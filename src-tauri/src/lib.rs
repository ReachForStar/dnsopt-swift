mod cli;
pub mod dns;
pub mod net;

pub use cli::{is_cli_mode, run as run_cli};

/// 启动 Tauri GUI 应用
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![
            test_dns,
            list_adapters,
            apply_dns,
            reset_dns,
            flush_cache,
            get_dns_cache,
            import_dns,
            export_dns,
        ])
        .run(tauri::generate_context!())
        .expect("启动 Tauri 应用失败");
}

/// IPC 契约测试入口：用与 GUI 相同的命令集构建 mock runtime 应用
/// （供 tests/ipc.rs 集成测试做真实 invoke 往返）
pub fn test_app() -> tauri::App<tauri::test::MockRuntime> {
    tauri::test::mock_builder()
        .invoke_handler(tauri::generate_handler![
            test_dns,
            list_adapters,
            apply_dns,
            reset_dns,
            flush_cache,
            get_dns_cache,
            import_dns,
            export_dns,
        ])
        .build(tauri::test::mock_context(tauri::test::noop_assets()))
        .expect("构建 mock 应用失败")
}

#[tauri::command(rename = "testDns")]
async fn test_dns(servers: Vec<String>, rounds: Option<u32>) -> Result<Vec<dns::TestResult>, String> {
    // 旧前端不传 rounds 时保持默认 3 轮（向后兼容）
    let rounds = rounds.unwrap_or(dns::DEFAULT_ROUNDS as u32).clamp(1, dns::MAX_ROUNDS as u32);
    dns::test_multiple(&servers, rounds as usize).await
}

#[tauri::command(rename = "listAdapters")]
fn list_adapters() -> Result<Vec<net::Adapter>, String> {
    net::list_adapters()
}

#[tauri::command(rename = "applyDns")]
fn apply_dns(if_index: u32, primary: String, secondary: Option<String>) -> Result<String, String> {
    net::apply_dns(if_index, &primary, secondary.as_deref())
}

#[tauri::command(rename = "resetDns")]
fn reset_dns(if_index: u32) -> Result<String, String> {
    net::reset_dns(if_index)
}

#[tauri::command(rename = "flushCache")]
fn flush_cache() -> Result<String, String> {
    net::flush_cache()
}

#[tauri::command(rename = "getDnsCache")]
fn get_dns_cache() -> Result<Vec<net::CacheEntry>, String> {
    net::get_dns_cache()
}

#[tauri::command(rename = "importDns")]
fn import_dns(path: String) -> Result<String, String> {
    std::fs::read_to_string(&path).map_err(|e| format!("读取文件失败: {e}"))
}

#[tauri::command(rename = "exportDns")]
fn export_dns(path: String, content: String) -> Result<String, String> {
    std::fs::write(&path, &content).map_err(|e| format!("写入文件失败: {e}"))?;
    Ok(format!("已导出: {path}"))
}
