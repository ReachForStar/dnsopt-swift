//! DNS 延迟测试引擎：UDP 直发（hickory-proto，可校验应答源）与 DoH（hickory-resolver）双通道
use hickory_proto::op::{Message, MessageType, OpCode, Query, ResponseCode};
use hickory_proto::rr::{Name, RData, RecordType};
use hickory_resolver::config::{LookupIpStrategy, NameServerConfig, ResolverConfig, ResolverOpts};
use hickory_resolver::net::runtime::TokioRuntimeProvider;
use hickory_resolver::TokioResolver;
use serde::{Deserialize, Serialize};
use std::net::{IpAddr, SocketAddr};
use std::time::{Duration, Instant};

/// 测试域名（尾部点号表示 FQDN，避免附加搜索域导致多轮查询）
pub const TEST_DOMAIN: &str = "www.baidu.com.";

/// 默认采样轮数与上限（每轮 = 每服务器各查一次）
pub const DEFAULT_ROUNDS: usize = 3;
pub const MAX_ROUNDS: usize = 50;

/// 单次查询超时：需大于常见公网 DNS 的 RTT，慢但可达的服务器不应被误判为失败
const QUERY_TIMEOUT: Duration = Duration::from_millis(1500);

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TestResult {
    pub server: String,
    /// 平均延迟（毫秒）
    pub latency_ms: u64,
    /// 最小/最大采样延迟（失败时为 0）
    pub latency_min: u64,
    pub latency_max: u64,
    pub success: bool,
    pub error: Option<String>,
    /// 疑似污染/劫持原因（None = 正常）
    pub suspect: Option<String>,
    /// 应答 A 记录集合（仅内部用于多数对比，不序列化给前端）
    #[serde(skip)]
    pub answer_ips: Vec<IpAddr>,
}

impl TestResult {
    /// 延迟等级（阈值与 Java 版 DnsTestResult 一致）
    pub fn grade(&self) -> &'static str {
        if !self.success {
            return "失败";
        }
        match self.latency_ms {
            0..=49 => "极佳",
            50..=99 => "良好",
            100..=199 => "一般",
            _ => "较慢",
        }
    }
}

/// 校验 IPv4 地址（点分四段、每段 0-255、不允许前导空白以外的杂质）
pub fn is_valid_ipv4(s: &str) -> bool {
    let s = s.trim();
    let parts: Vec<&str> = s.split('.').collect();
    parts.len() == 4
        && parts.iter().all(|p| {
            !p.is_empty()
                && p.len() <= 3
                && p.bytes().all(|b| b.is_ascii_digit())
                && p.parse::<u16>().map_or(false, |v| v <= 255)
        })
}

/// 用指定服务器执行一次 A 记录查询，返回耗时（毫秒）
struct QueryOutcome {
    latency_ms: u64,
    /// 实际应答源 IP（DoH 通道无法获取，为 None）
    source_ip: Option<IpAddr>,
    /// 应答中的 A 记录（用于多数对比）
    answer_ips: Vec<IpAddr>,
}

/// 校验服务器输入：IPv4 或 DoH URL（https://host[:port][/path]）
pub fn is_valid_server(s: &str) -> bool {
    let s = s.trim();
    is_valid_ipv4(s) || s.starts_with("https://") && !s.contains(' ') && s.len() > 8
}

fn parse_server(s: &str) -> Result<String, String> {
    let t = s.trim();
    if is_valid_ipv4(t) {
        return Ok(t.to_string());
    }
    if t.starts_with("https://") && !t.contains(' ') {
        return Ok(t.to_string());
    }
    Err(format!("不支持的服务器地址（仅支持 IPv4 或 https DoH URL）: {t}"))
}

/// UDP 直发一次 A 记录查询（可拿到应答源地址，用于劫持检测）
async fn query_udp(server_ip: &IpAddr) -> Result<QueryOutcome, String> {
    let name = Name::from_ascii(TEST_DOMAIN).map_err(|e| format!("域名构造失败: {e}"))?;
    let id = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.subsec_millis() as u16)
        .unwrap_or(0);
    let mut message = Message::new(id, MessageType::Query, OpCode::Query);
    message.metadata.recursion_desired = true;
    message.queries.push(Query::query(name, RecordType::A));
    let payload = message.to_vec().map_err(|e| format!("查询序列化失败: {e}"))?;

    let sock = tokio::net::UdpSocket::bind("0.0.0.0:0")
        .await
        .map_err(|e| format!("UDP 绑定失败: {e}"))?;
    let target: SocketAddr = (*server_ip, 53).into();
    sock.connect(target).await.map_err(|e| format!("UDP 连接失败: {e}"))?;

    let start = Instant::now();
    sock.send(&payload).await.map_err(|e| format!("发送查询失败: {e}"))?;
    let mut buf = vec![0u8; 4096];
    let (len, source) = tokio::time::timeout(QUERY_TIMEOUT, sock.recv_from(&mut buf))
        .await
        .map_err(|_| "request timed out".to_string())?
        .map_err(|e| format!("接收应答失败: {e}"))?;
    let latency_ms = start.elapsed().as_millis() as u64;

    let response = Message::from_vec(&buf[..len]).map_err(|e| format!("解析应答失败: {e}"))?;
    if response.metadata.response_code != ResponseCode::NoError {
        return Err(format!("应答码异常: {:?}", response.metadata.response_code));
    }
    let answer_ips: Vec<IpAddr> = response
        .answers
        .iter()
        .filter_map(|r| match &r.data {
            RData::A(a) => Some(IpAddr::V4(a.0)),
            _ => None,
        })
        .collect();
    if answer_ips.is_empty() {
        return Err("响应中没有地址记录".into());
    }
    Ok(QueryOutcome {
        latency_ms,
        source_ip: Some(source.ip()),
        answer_ips,
    })
}

/// DoH 通道一次 A 记录查询（HTTPS 加密，无法校验应答源）
async fn query_doh(url: &str) -> Result<QueryOutcome, String> {
    let parsed = url::Url::parse(url).map_err(|e| format!("DoH URL 无效: {e}"))?;
    let host = parsed
        .host_str()
        .ok_or_else(|| "DoH URL 缺少主机名".to_string())?;
    let path = parsed.path();
    let path = if path.is_empty() || path == "/" { "/dns-query" } else { path };
    let port = parsed.port().unwrap_or(443);
    // 0.26 的 DoH 构造要求连接端点为 IP：先用系统解析器取 IP（仅连接端点，
    // 查询本身走加密通道，不经过本地 DNS，不影响测量语义）
    let endpoint = tokio::net::lookup_host((host, port))
        .await
        .map_err(|e| format!("DoH 主机名解析失败: {e}"))?
        .next()
        .ok_or_else(|| format!("DoH 主机名 {host} 无解析结果"))?;
    let ns = NameServerConfig::https(endpoint.ip(), host.into(), Some(path.into()));
    let config = ResolverConfig::from_name_servers(vec![ns]);
    let mut opts = ResolverOpts::default();
    opts.timeout = QUERY_TIMEOUT;
    opts.attempts = 0; // 不重试：延迟测试要的是单次往返
    opts.ip_strategy = LookupIpStrategy::Ipv4Only;

    let resolver = TokioResolver::builder_with_config(config, TokioRuntimeProvider::default())
        .with_options(opts)
        .build()
        .map_err(|e| format!("创建解析器失败: {e}"))?;

    let start = Instant::now();
    let lookup = resolver
        .lookup_ip(TEST_DOMAIN)
        .await
        .map_err(|e| e.to_string())?;
    let latency_ms = start.elapsed().as_millis() as u64;
    let answer_ips: Vec<IpAddr> = lookup.iter().collect();
    if answer_ips.is_empty() {
        return Err("响应中没有地址记录".into());
    }
    Ok(QueryOutcome {
        latency_ms,
        source_ip: None,
        answer_ips,
    })
}

/// 按服务器类型分派一次查询
async fn query_once(server: &str) -> Result<QueryOutcome, String> {
    let t = server.trim();
    if let Ok(ip) = t.parse::<IpAddr>() {
        query_udp(&ip).await
    } else {
        query_doh(t).await
    }
}

/// 测试单个 DNS 服务器：采样 rounds 次，至少一次成功才算成功
pub async fn test_single(server: &str, rounds: usize) -> TestResult {
    let server = match parse_server(server) {
        Ok(s) => s,
        Err(e) => {
            return TestResult {
                server: server.to_string(),
                latency_ms: 0,
                latency_min: 0,
                latency_max: 0,
                success: false,
                error: Some(e),
                suspect: None,
                answer_ips: Vec::new(),
            };
        }
    };

    let mut latencies: Vec<u64> = Vec::new();
    let mut last_error: Option<String> = None;
    let mut source_ip: Option<IpAddr> = None;
    let mut answer_ips: Vec<IpAddr> = Vec::new();
    for _ in 0..rounds {
        match query_once(&server).await {
            Ok(o) => {
                latencies.push(o.latency_ms);
                source_ip = o.source_ip;
                answer_ips = o.answer_ips;
            }
            Err(e) => last_error = Some(e),
        }
    }

    if latencies.is_empty() {
        return TestResult {
            server,
            latency_ms: 0,
            latency_min: 0,
            latency_max: 0,
            success: false,
            error: Some(last_error.unwrap_or_else(|| "DNS 查询失败".into())),
            suspect: None,
            answer_ips: Vec::new(),
        };
    }

    let avg = latencies.iter().sum::<u64>() / latencies.len() as u64;
    // UDP 通道应答源与服务器不一致 → 基本可判定劫持（响应被中间节点伪造/代发）
    let hijack = match &source_ip {
        Some(src) => {
            let expected: IpAddr = server.trim().parse().ok().unwrap_or(*src);
            if *src != expected {
                Some(format!("应答源 {src} 与服务器 {} 不一致（疑似劫持）", expected))
            } else {
                None
            }
        }
        None => None,
    };

    TestResult {
        server,
        latency_ms: avg,
        latency_min: latencies.iter().min().copied().unwrap_or(0),
        latency_max: latencies.iter().max().copied().unwrap_or(0),
        success: true,
        error: None,
        suspect: hijack,
        answer_ips,
    }
}

/// 按「成功在前、延迟升序、同延迟按服务器名」排序
pub fn sort_results(results: &mut Vec<TestResult>) {
    results.sort_by(|a, b| {
        match (a.success, b.success) {
            (true, false) => std::cmp::Ordering::Less,
            (false, true) => std::cmp::Ordering::Greater,
            _ => a.latency_ms
                .cmp(&b.latency_ms)
                .then_with(|| a.server.cmp(&b.server)),
        }
    });
}

/// 并行测试多个 DNS 服务器，按「成功在前、延迟升序」返回
pub async fn test_multiple(servers: &[String], rounds: usize) -> Result<Vec<TestResult>, String> {
    if servers.is_empty() {
        return Err("没有要测试的 DNS 服务器".into());
    }
    let rounds = rounds.clamp(1, MAX_ROUNDS);

    let mut handles = Vec::new();
    for s in servers {
        let server = s.clone();
        handles.push(tokio::spawn(async move { test_single(&server, rounds).await }));
    }

    let mut results = Vec::with_capacity(handles.len());
    for h in handles {
        match h.await {
            Ok(r) => results.push(r),
            Err(e) => results.push(TestResult {
                server: "unknown".into(),
                latency_ms: 0,
                latency_min: 0,
                latency_max: 0,
                success: false,
                error: Some(format!("任务异常: {e}")),
                suspect: None,
                answer_ips: Vec::new(),
            }),
        }
    }

    sort_results(&mut results);
    flag_suspect_majority(&mut results);
    Ok(results)
}

/// 应答多数对比：成功服务器 ≥ 2 时，应答集与多数派不同且频率更低的标记为疑似污染
fn flag_suspect_majority(results: &mut [TestResult]) {
    use std::collections::BTreeMap;
    let ok_count = results.iter().filter(|r| r.success).count();
    if ok_count < 2 {
        return;
    }
    let mut counts: BTreeMap<Vec<std::net::IpAddr>, usize> = BTreeMap::new();
    for r in results.iter().filter(|r| r.success) {
        *counts.entry(r.answer_ips.clone()).or_insert(0) += 1;
    }
    let (majority_set, majority_n) = match counts.iter().max_by_key(|(_, c)| *c) {
        Some(m) => m,
        None => return,
    };
    for r in results.iter_mut().filter(|r| r.success) {
        if r.suspect.is_none() && r.answer_ips != *majority_set && counts[&r.answer_ips] < *majority_n {
            r.suspect = Some("解析结果与其他多数服务器不一致（疑似污染）".into());
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ipv4_validation() {
        assert!(is_valid_ipv4("223.5.5.5"));
        assert!(is_valid_ipv4("8.8.8.8"));
        assert!(is_valid_ipv4(" 1.1.1.1 "));
        assert!(is_valid_ipv4("1.2.3.04")); // 允许前导零，与 Java 版行为一致
        assert!(!is_valid_ipv4(""));
        assert!(!is_valid_ipv4("223.5.5"));
        assert!(!is_valid_ipv4("256.1.1.1"));
        assert!(!is_valid_ipv4("1.2.3.4.5"));
        assert!(!is_valid_ipv4("a.b.c.d"));
        assert!(!is_valid_ipv4("1.2.3.-4"));
        assert!(!is_valid_ipv4("1.2.3.4x"));
    }

    #[test]
    fn grade_thresholds() {
        let ok = |ms: u64| TestResult {
            server: "1.1.1.1".into(),
            latency_ms: ms,
            latency_min: ms,
            latency_max: ms,
            success: true,
            error: None,
            suspect: None,
            answer_ips: Vec::new(),
        };
        assert_eq!(ok(0).grade(), "极佳");
        assert_eq!(ok(49).grade(), "极佳");
        assert_eq!(ok(50).grade(), "良好");
        assert_eq!(ok(100).grade(), "一般");
        assert_eq!(ok(200).grade(), "较慢");
        assert_eq!(ok(999).grade(), "较慢");
        let fail = TestResult {
            server: "1.1.1.1".into(),
            latency_ms: 0,
            latency_min: 0,
            latency_max: 0,
            success: false,
            error: Some("x".into()),
            suspect: None,
            answer_ips: Vec::new(),
        };
        assert_eq!(fail.grade(), "失败");
    }

    fn r(server: &str, ms: u64, success: bool) -> TestResult {
        TestResult {
            server: server.into(),
            latency_ms: ms,
            latency_min: ms,
            latency_max: ms,
            success,
            error: None,
            suspect: None,
            answer_ips: Vec::new(),
        }
    }

    #[test]
    fn suspect_majority_flags_minority_answers() {
        let ip_a = "1.1.1.1".parse::<std::net::IpAddr>().unwrap();
        let ip_b = "2.2.2.2".parse::<std::net::IpAddr>().unwrap();
        let with_answer = |server: &str, ip: std::net::IpAddr| TestResult {
            server: server.into(),
            latency_ms: 20,
            latency_min: 20,
            latency_max: 20,
            success: true,
            error: None,
            suspect: None,
            answer_ips: vec![ip],
        };
        // 3 个服务器回答 ip_a，1 个回答 ip_b → 少数派被标记
        let mut results = vec![
            with_answer("s1", ip_a),
            with_answer("s2", ip_a),
            with_answer("s3", ip_a),
            with_answer("s4", ip_b),
        ];
        flag_suspect_majority(&mut results);
        assert!(results[0].suspect.is_none());
        assert!(results[3].suspect.is_some());
        assert!(results[3].suspect.as_ref().unwrap().contains("不一致"));

        // 2:2 平票 → 不标记（避免误报）
        let mut tie = vec![with_answer("s1", ip_a), with_answer("s2", ip_b)];
        flag_suspect_majority(&mut tie);
        assert!(tie[0].suspect.is_none() && tie[1].suspect.is_none());
    }

    #[test]
    fn server_input_validation() {
        assert!(is_valid_server("223.5.5.5"));
        assert!(is_valid_server("https://dns.alidns.com/dns-query"));
        assert!(is_valid_server("https://1.1.1.1/dns-query"));
        assert!(!is_valid_server("999.1.1.1"));
        assert!(!is_valid_server("http://insecure.example.com"));
        assert!(!is_valid_server("https://bad url.example"));
        assert!(!is_valid_server(""));
    }

    #[test]
    fn sort_order_success_before_failure_then_latency() {
        let mut results = vec![r("c", 0, false), r("a", 80, true), r("b", 30, true), r("d", 80, true)];
        sort_results(&mut results);
        let order: Vec<&str> = results.iter().map(|x| x.server.as_str()).collect();
        assert_eq!(order, vec!["b", "a", "d", "c"]);
    }

    #[tokio::test]
    async fn test_multiple_empty_input() {
        assert!(test_multiple(&[], DEFAULT_ROUNDS).await.is_err());
    }
}
