//! DNS 延迟测试引擎：hickory-resolver 直连 UDP 查询，无外部进程依赖
use hickory_resolver::config::{LookupIpStrategy, NameServerConfig, ResolverConfig, ResolverOpts};
use hickory_resolver::net::runtime::TokioRuntimeProvider;
use hickory_resolver::TokioResolver;
use serde::{Deserialize, Serialize};
use std::time::{Duration, Instant};

/// 测试域名（尾部点号表示 FQDN，避免附加搜索域导致多轮查询）
pub const TEST_DOMAIN: &str = "www.baidu.com.";

/// 每个 DNS 服务器采样次数，取平均值
const SAMPLES: usize = 3;

/// 单次查询超时：需大于常见公网 DNS 的 RTT，慢但可达的服务器不应被误判为失败
const QUERY_TIMEOUT: Duration = Duration::from_millis(1500);

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TestResult {
    pub server: String,
    pub latency_ms: u64,
    pub success: bool,
    pub error: Option<String>,
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
async fn query_once(server: &str) -> Result<u64, String> {
    let ip: std::net::IpAddr = server.trim().parse().map_err(|e| format!("地址解析失败: {e}"))?;
    let config = ResolverConfig::from_name_servers(vec![NameServerConfig::udp(ip)]);
    let mut opts = ResolverOpts::default();
    opts.timeout = QUERY_TIMEOUT;
    opts.attempts = 0; // 不重试：延迟测试要的是单次往返，重试会污染测量值
    opts.ip_strategy = LookupIpStrategy::Ipv4Only; // 只查 A 记录，与 dig A 语义一致

    let resolver = TokioResolver::builder_with_config(config, TokioRuntimeProvider::default())
        .with_options(opts)
        .build()
        .map_err(|e| format!("创建解析器失败: {e}"))?;

    let start = Instant::now();
    let lookup = resolver
        .lookup_ip(TEST_DOMAIN)
        .await
        .map_err(|e| e.to_string())?;
    let _first = lookup.iter().next().ok_or("响应中没有地址记录")?;
    Ok(start.elapsed().as_millis() as u64)
}

/// 测试单个 DNS 服务器：采样 3 次，全部失败才算失败
pub async fn test_single(server: &str) -> TestResult {
    if !is_valid_ipv4(server) {
        return TestResult {
            server: server.to_string(),
            latency_ms: 0,
            success: false,
            error: Some("不是有效的 IPv4 地址".into()),
        };
    }

    let mut latencies: Vec<u64> = Vec::new();
    let mut last_error: Option<String> = None;
    for _ in 0..SAMPLES {
        match query_once(server).await {
            Ok(ms) => latencies.push(ms),
            Err(e) => last_error = Some(e),
        }
    }

    if latencies.is_empty() {
        TestResult {
            server: server.to_string(),
            latency_ms: 0,
            success: false,
            error: Some(last_error.unwrap_or_else(|| "DNS 查询失败".into())),
        }
    } else {
        let avg = latencies.iter().sum::<u64>() / latencies.len() as u64;
        TestResult {
            server: server.to_string(),
            latency_ms: avg,
            success: true,
            error: None,
        }
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
pub async fn test_multiple(servers: &[String]) -> Result<Vec<TestResult>, String> {
    if servers.is_empty() {
        return Err("没有要测试的 DNS 服务器".into());
    }

    let mut handles = Vec::new();
    for s in servers {
        let server = s.clone();
        handles.push(tokio::spawn(async move { test_single(&server).await }));
    }

    let mut results = Vec::with_capacity(handles.len());
    for h in handles {
        match h.await {
            Ok(r) => results.push(r),
            Err(e) => results.push(TestResult {
                server: "unknown".into(),
                latency_ms: 0,
                success: false,
                error: Some(format!("任务异常: {e}")),
            }),
        }
    }

    sort_results(&mut results);
    Ok(results)
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
            success: true,
            error: None,
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
            success: false,
            error: Some("x".into()),
        };
        assert_eq!(fail.grade(), "失败");
    }

    fn r(server: &str, ms: u64, success: bool) -> TestResult {
        TestResult {
            server: server.into(),
            latency_ms: ms,
            success,
            error: None,
        }
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
        assert!(test_multiple(&[]).await.is_err());
    }
}
