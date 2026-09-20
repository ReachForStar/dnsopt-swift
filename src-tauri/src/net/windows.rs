//! Windows 网络管理：网卡枚举、DNS 设置（UAC 提权）、缓存刷新
use base64::Engine;
use serde::{Deserialize, Serialize};
use std::process::{Command, Stdio};

// CREATE_NO_WINDOW：避免弹出黑色控制台窗口
#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x08000000;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Adapter {
    #[serde(rename = "name", alias = "Name")]
    pub name: String,
    #[serde(rename = "status", alias = "Status")]
    pub status: String,
    #[serde(rename = "ifIndex")]
    pub if_index: u32,
    /// 连接状态由 Rust 侧统一判定后序列化，前端不再自行解析状态文案
    /// （PowerShell 7 的状态是 Up/Disconnected，中文文案匹配会误判为未连接）
    pub connected: bool,
}

/// Get-NetAdapter JSON 的原始形状（无 connected 字段）
#[derive(Debug, Deserialize)]
struct RawAdapter {
    #[serde(alias = "Name")]
    name: String,
    #[serde(alias = "Status")]
    status: String,
    #[serde(alias = "ifIndex")]
    if_index: u32,
}

/// 系统 DNS 解析缓存条目（ipconfig /displaydns 提取）
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CacheEntry {
    pub name: String,
    pub address: String,
}

/// 读取系统 DNS 解析缓存（无需管理员）
pub fn get_dns_cache() -> Result<Vec<CacheEntry>, String> {
    let mut cmd = std::process::Command::new("ipconfig");
    cmd.arg("/displaydns").stdout(Stdio::piped()).stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    let output = cmd.output().map_err(|e| format!("执行 ipconfig 失败: {e}"))?;
    let text = String::from_utf8_lossy(&output.stdout);
    Ok(parse_dns_cache_output(&text))
}

/// 解析 displaydns 输出（逐行状态机，兼容中英文字段名）
pub fn parse_dns_cache_output(out: &str) -> Vec<CacheEntry> {
    let mut entries = Vec::new();
    let mut current: Option<String> = None;
    for line in out.lines() {
        let t = line.trim();
        if let Some(name) = displaydns_values(t, &["记录名称", "Record Name"]) {
            current = Some(name);
        } else if let Some(ip) = displaydns_values(t, &["IPv4 地址", "IPv4 Address"]) {
            if let Some(name) = current.take() {
                entries.push(CacheEntry { name, address: ip });
            }
        }
    }
    entries
}

/// 取 displaydns 行中字段后的值（如 "记录名称 . . . : xxx" → "xxx"）
fn displaydns_values(line: &str, keys: &[&str]) -> Option<String> {
    for key in keys {
        if let Some(pos) = line.find(key) {
            let rest = &line[pos + key.len()..];
            if let Some(colon) = rest.find(':') {
                let v = rest[colon + 1..].trim().to_string();
                if !v.is_empty() {
                    return Some(v);
                }
            }
        }
    }
    None
}

impl Adapter {
    fn from_raw(r: RawAdapter) -> Self {
        let connected = Self::status_connected(&r.status);
        Self {
            name: r.name,
            status: r.status,
            if_index: r.if_index,
            connected,
        }
    }

    /// 兼容中文系统（已连接/已断开）、英文（Connected/Disconnected）与 pwsh 7（Up）的状态文案
    fn status_connected(status: &str) -> bool {
        let s = status.to_lowercase();
        if s.contains("已连接") || s == "up" {
            return true;
        }
        if s.contains("disconnected") || s.contains("已断开") {
            return false;
        }
        s.contains("connected")
    }

    pub fn is_connected(&self) -> bool {
        self.connected
    }
}

/// 定位 PowerShell：优先 pwsh（PowerShell 7，状态输出更稳定），缺失时回退系统自带的 5.1
fn find_powershell() -> String {
    let path_var = std::env::var("PATH").unwrap_or_default();
    for dir in path_var.split(';') {
        if dir.is_empty() {
            continue;
        }
        let candidate = std::path::Path::new(dir).join("pwsh.exe");
        if candidate.is_file() {
            return candidate.to_string_lossy().into_owned();
        }
    }
    // 标准安装位置（未加入 PATH 时）
    let standard = std::path::Path::new(r"C:\Program Files\PowerShell\7\pwsh.exe");
    if standard.is_file() {
        return standard.to_string_lossy().into_owned();
    }
    "powershell".to_string()
}

fn powershell(args: &[String]) -> Result<String, String> {
    let mut cmd = Command::new(find_powershell());
    cmd.args(args).stdout(Stdio::piped()).stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    let output = cmd.output().map_err(|e| format!("启动 PowerShell 失败: {e}"))?;
    Ok(String::from_utf8_lossy(&output.stdout).into_owned())
}

/// 枚举网络接口（Get-NetAdapter 输出 JSON，ifIndex 与语言无关，后续按索引设置 DNS）
pub fn list_adapters() -> Result<Vec<Adapter>, String> {
    let script = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; \
                  Get-NetAdapter | Select-Object Name,Status,ifIndex | ConvertTo-Json -Compress";
    let out = powershell(&["-NoProfile".into(), "-Command".into(), script.to_string()])?;
    let out = out.trim();
    if out.is_empty() {
        return Err("Get-NetAdapter 无输出，请确认系统已安装网络组件".into());
    }
    let raws: Vec<RawAdapter> =
        serde_json::from_str(out).map_err(|e| format!("解析网络接口列表失败: {e}"))?;
    Ok(raws.into_iter().map(Adapter::from_raw).collect())
}

/// 单引号内转义（PowerShell 单引号字符串以 '' 转义 '）
fn ps_quote(s: &str) -> String {
    s.replace('\'', "''")
}

/// UTF-16LE + Base64（-EncodedCommand 的编码要求，无 BOM）
fn utf16le_b64(s: &str) -> String {
    let bytes: Vec<u8> = s.encode_utf16().flat_map(|u| u.to_le_bytes()).collect();
    base64::engine::general_purpose::STANDARD.encode(bytes)
}

/// 以管理员权限运行一条 PowerShell 命令（触发 UAC 确认框），返回成功信息或失败原因
fn run_elevated(command_line: &str) -> Result<String, String> {
    let outer = format!(
        "$script = '{}'\n$p = Start-Process powershell.exe -Verb RunAs -Wait -PassThru -ArgumentList @('-NoProfile','-Command',$script)\nexit $p.ExitCode",
        ps_quote(command_line)
    );
    let encoded = utf16le_b64(&outer);

    let mut cmd = Command::new(find_powershell());
    cmd.args(["-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", &encoded])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    let output = cmd.output().map_err(|e| format!("启动提权进程失败: {e}"))?;
    let code = output.status.code().unwrap_or(-1);
    let stderr = String::from_utf8_lossy(&output.stderr);

    if code == 0 {
        Ok("操作成功".into())
    } else if stderr.contains("canceled by the user")
        || stderr.contains("The operation was cancelled")
        || stderr.contains("用户取消了操作")
    {
        Err("用户取消了管理员权限确认".into())
    } else {
        let detail = stderr.lines().find(|l| !l.trim().is_empty()).unwrap_or("未知错误").to_string();
        Err(format!("操作失败（退出码 {code}）: {detail}"))
    }
}

/// 为指定接口设置静态 DNS
pub fn apply_dns(if_index: u32, primary: &str, secondary: Option<&str>) -> Result<String, String> {
    if !crate::dns::is_valid_ipv4(primary) {
        return Err(format!("首选 DNS 不是有效地址: {primary}"));
    }
    let command_line = match secondary {
        Some(s) => {
            if !crate::dns::is_valid_ipv4(s) {
                return Err(format!("辅助 DNS 不是有效地址: {s}"));
            }
            format!(
                "Set-DnsClientServerAddress -InterfaceIndex {if_index} -ServerAddresses '{}','{}'",
                ps_quote(primary),
                ps_quote(s)
            )
        }
        None => format!(
            "Set-DnsClientServerAddress -InterfaceIndex {if_index} -ServerAddresses '{}'",
            ps_quote(primary)
        ),
    };
    run_elevated(&command_line)
}

/// 恢复指定接口为自动获取 DNS（DHCP）
pub fn reset_dns(if_index: u32) -> Result<String, String> {
    run_elevated(&format!("Set-DnsClientServerAddress -InterfaceIndex {if_index} -ResetServerAddresses"))
}

/// 刷新本地 DNS 缓存（普通权限即可，无需提权）
pub fn flush_cache() -> Result<String, String> {
    let mut cmd = Command::new("ipconfig");
    cmd.arg("/flushdns").stdout(Stdio::piped()).stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }
    let output = cmd.output().map_err(|e| format!("执行 ipconfig 失败: {e}"))?;
    if output.status.success() {
        Ok("DNS 缓存已刷新".into())
    } else {
        Err("DNS 缓存刷新失败".into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ps_quote_escapes_single_quotes() {
        assert_eq!(ps_quote("以太网"), "以太网");
        assert_eq!(ps_quote("a'b"), "a''b");
        assert_eq!(ps_quote("以太网 2"), "以太网 2");
    }

    #[test]
    fn utf16le_b64_roundtrip() {
        // "ab" → 0x61 0x00 0x62 0x00 → base64 "YQBiAA=="
        assert_eq!(utf16le_b64("ab"), "YQBiAA==");
        // 中文：电 = U+7535 → LE 字节 0x35 0x75
        let v = utf16le_b64("电");
        let decoded = base64::engine::general_purpose::STANDARD.decode(&v).unwrap();
        assert_eq!(decoded, vec![0x35, 0x75]);
    }

    #[test]
    fn adapter_status_connected_localized() {
        // 中文系统（5.1 输出）、英文（Connected）、pwsh 7（Up）
        assert!(Adapter::status_connected("已连接"));
        assert!(Adapter::status_connected("Connected"));
        assert!(Adapter::status_connected("Up"));
        // 注意：Disconnected 不能误判为已连接
        assert!(!Adapter::status_connected("Disconnected"));
        assert!(!Adapter::status_connected("已断开"));
    }

    #[test]
    fn parse_dns_cache_zh() {
        let out = "Windows IP Configuration\n\n\
                   记录名称 . . . . . . . : www.baidu.com\n\
                   记录类型 . . . . . . . : 5\n\
                   生存时间 . . . . . . . : 600\n\
                   IPv4 地址 . . . . . . . : 110.242.68.66\n\n\
                   记录名称 . . . . . . . : example.com\n\
                   IPv4 地址 . . . . . . . : 93.184.216.34\n";
        let entries = parse_dns_cache_output(out);
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].name, "www.baidu.com");
        assert_eq!(entries[0].address, "110.242.68.66");
        assert_eq!(entries[1].name, "example.com");
        assert_eq!(entries[1].address, "93.184.216.34");
    }

    #[test]
    fn parse_dns_cache_en() {
        let out = "Record Name . . . . . . . : www.example.org\n\
                   Record Type . . . . . . . : 5\n\
                   IPv4 Address . . . . . . . : 192.0.2.7\n";
        let entries = parse_dns_cache_output(out);
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].name, "www.example.org");
        assert_eq!(entries[0].address, "192.0.2.7");
    }

    #[test]
    fn parse_dns_cache_empty() {
        assert!(parse_dns_cache_output("").is_empty());
        // 无记录名称的孤立 IPv4 行不应产生条目
        assert!(parse_dns_cache_output("IPv4 Address . . . . . . . : 1.2.3.4").is_empty());
    }

    #[test]
    fn parse_adapter_json() {
        // PowerShell Select-Object 输出的属性名首字母大写；pwsh 7 状态为 Up/Disconnected
        let json = r#"{"Name":"WLAN","Status":"Up","ifIndex":12}"#;
        let raw: RawAdapter = serde_json::from_str(json).unwrap();
        let a = Adapter::from_raw(raw);
        assert_eq!(a.name, "WLAN");
        assert_eq!(a.if_index, 12);
        assert!(a.connected);

        let json = r#"{"Name":"以太网","Status":"Disconnected","ifIndex":19}"#;
        let raw: RawAdapter = serde_json::from_str(json).unwrap();
        let a = Adapter::from_raw(raw);
        assert!(!a.connected);
    }
}
