//! CLI 模式：无 GUI 环境下测试/设置 DNS（带参数启动时进入）

const USAGE: &str = r#"DNS延迟测试工具 CLI 用法:
  (无参数)                 启动图形界面
  adapters                 列出网络接口（名称/状态/ifIndex）
  test [IP ...]            并行测试 DNS 延迟（默认 223.5.5.5 8.8.8.8 114.114.114.114）
  set <ifIndex> <主DNS> [辅DNS]   应用静态 DNS（触发 UAC 管理员确认）
  auto <ifIndex>           恢复自动获取 DNS（触发 UAC 管理员确认）
  flush                    刷新本地 DNS 缓存
  cache                    查看系统 DNS 解析缓存
  dns <ifIndex>            查看接口当前配置的 DNS 服务器
  diagnose [输出文件]       生成诊断报告（系统/网卡/当前DNS/缓存摘要）
  help                     显示本帮助"#;

pub fn is_cli_mode(args: &[String]) -> bool {
    matches!(
        args.first().map(String::as_str),
        Some("test")
            | Some("adapters")
            | Some("flush")
            | Some("set")
            | Some("auto")
            | Some("cache")
            | Some("dns")
            | Some("diagnose")
            | Some("help")
            | Some("--help")
            | Some("-h")
    )
}

/// 执行 CLI，返回进程退出码
pub fn run(args: &[String]) -> i32 {
    match run_inner(args) {
        Ok(output) => {
            emit(&output, false);
            0
        }
        Err(e) => {
            emit(&format!("错误: {e}"), true);
            1
        }
    }
}

/// 输出到控制台。GUI release 构建下 std 句柄无效，直接写控制台设备
fn emit(line: &str, is_error: bool) {
    #[cfg(all(windows, not(debug_assertions)))]
    {
        use std::io::Write;
        use std::os::windows::io::FromRawHandle;
        use windows_sys::Win32::System::Console::{
            GetStdHandle, STD_ERROR_HANDLE, STD_OUTPUT_HANDLE,
        };
        let h = unsafe {
            GetStdHandle(if is_error { STD_ERROR_HANDLE } else { STD_OUTPUT_HANDLE })
        };
        if !h.is_null() {
            let mut file = unsafe { std::fs::File::from_raw_handle(h as _) };
            let _ = file.write_all(line.as_bytes());
            let _ = file.write_all(b"\n");
            return;
        }
    }
    if is_error {
        eprintln!("{line}");
    } else {
        println!("{line}");
    }
}

fn run_inner(args: &[String]) -> Result<String, String> {
    let [cmd, rest @ ..] = args else {
        return Err("缺少命令，使用 help 查看用法".into());
    };
    match cmd.as_str() {
        "help" | "--help" | "-h" => Ok(USAGE.into()),
        "adapters" => {
            let adapters = crate::net::list_adapters()?;
            if adapters.is_empty() {
                return Ok("未找到网络接口".into());
            }
            let mut lines = vec!["名称\t状态\tifIndex".to_string()];
            for a in &adapters {
                lines.push(format!(
                    "{}\t{}\t{}",
                    a.name,
                    if a.is_connected() { "已连接" } else { "未连接" },
                    a.if_index
                ));
            }
            Ok(lines.join("\n"))
        }
        "test" => {
            let servers: Vec<String> = if rest.is_empty() {
                vec!["223.5.5.5".into(), "8.8.8.8".into(), "114.114.114.114".into()]
            } else {
                rest.to_vec()
            };
            let runtime = tokio::runtime::Runtime::new().map_err(|e| e.to_string())?;
            let domains = crate::dns::parse_domains(&[crate::dns::TEST_DOMAIN.trim_end_matches('.').to_string()])?;
            let results =
                runtime.block_on(crate::dns::test_multiple(&servers, crate::dns::DEFAULT_ROUNDS, &domains))?;
            let mut lines = vec!["DNS服务器\t延迟(ms)\t状态".to_string()];
            for r in &results {
                let status = if r.success {
                    r.grade().to_string()
                } else {
                    format!("失败: {}", r.error.as_deref().unwrap_or("未知原因"))
                };
                lines.push(format!(
                    "{}\t{}/{} (min/avg/max)\t{}",
                    r.server,
                    if r.success { r.latency_min.to_string() } else { "N/A".into() },
                    if r.success { r.latency_ms.to_string() } else { "N/A".into() },
                    status
                ));
            }
            Ok(lines.join("\n"))
        }
        "flush" => Ok(crate::net::flush_cache()?),
        "cache" => {
            let entries = crate::net::get_dns_cache()?;
            if entries.is_empty() {
                return Ok("DNS 解析缓存为空（可用 flush 刷新后重新解析）".into());
            }
            let mut lines = vec!["域名\tIPv4 地址".to_string()];
            for e in &entries {
                lines.push(format!("{}\t{}", e.name, e.address));
            }
            Ok(lines.join("\n"))
        }
        "dns" => {
            let [idx, ..] = rest else {
                return Err("用法: dns <ifIndex>".into());
            };
            let if_index: u32 = idx.parse().map_err(|e| format!("ifIndex 无效: {e}"))?;
            let ips = crate::net::get_dns_servers(if_index)?;
            if ips.is_empty() {
                return Ok("该接口未配置 DNS 服务器（自动获取/DHCP）".into());
            }
            Ok(ips.join("\n"))
        }
        "diagnose" => {
            let [file, ..] = rest else {
                return Ok(crate::net::diagnose(None));
            };
            let report = crate::net::diagnose(None);
            std::fs::write(file, &report).map_err(|e| format!("写入文件失败: {e}"))?;
            Ok(format!("诊断报告已保存到: {file}"))
        }
        "set" => {
            let [idx, dns1, rest2 @ ..] = rest else {
                return Err("用法: set <ifIndex> <首选DNS> [辅助DNS]".into());
            };
            let if_index: u32 = idx.parse().map_err(|e| format!("ifIndex 无效: {e}"))?;
            let secondary = rest2.first().map(String::as_str);
            Ok(crate::net::apply_dns(if_index, dns1, secondary)?)
        }
        "auto" => {
            let [idx, ..] = rest else {
                return Err("用法: auto <ifIndex>".into());
            };
            let if_index: u32 = idx.parse().map_err(|e| format!("ifIndex 无效: {e}"))?;
            Ok(crate::net::reset_dns(if_index)?)
        }
        other => Err(format!("未知命令: {other}，使用 help 查看用法")),
    }
}
