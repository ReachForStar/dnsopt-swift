mod cli;
mod dns;
mod net;

pub use cli::{is_cli_mode, run as run_cli};

/// 启动 Tauri GUI 应用
#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .invoke_handler(tauri::generate_handler![
            cmd_test_dns,
            cmd_list_adapters,
            cmd_apply_dns,
            cmd_reset_dns,
            cmd_flush_cache,
            cmd_import_dns,
            cmd_export_dns,
        ])
        .run(tauri::generate_context!())
        .expect("启动 Tauri 应用失败");
}

#[tauri::command]
async fn cmd_test_dns(servers: Vec<String>) -> Result<Vec<dns::TestResult>, String> {
    dns::test_multiple(&servers).await
}

#[tauri::command]
fn cmd_list_adapters() -> Result<Vec<net::Adapter>, String> {
    net::list_adapters()
}

#[tauri::command]
fn cmd_apply_dns(if_index: u32, primary: String, secondary: Option<String>) -> Result<String, String> {
    net::apply_dns(if_index, &primary, secondary.as_deref())
}

#[tauri::command]
fn cmd_reset_dns(if_index: u32) -> Result<String, String> {
    net::reset_dns(if_index)
}

#[tauri::command]
fn cmd_flush_cache() -> Result<String, String> {
    net::flush_cache()
}

#[tauri::command]
fn cmd_import_dns(path: String) -> Result<String, String> {
    std::fs::read_to_string(&path).map_err(|e| format!("读取文件失败: {e}"))
}

#[tauri::command]
fn cmd_export_dns(path: String, content: String) -> Result<String, String> {
    std::fs::write(&path, &content).map_err(|e| format!("写入文件失败: {e}"))?;
    Ok(format!("已导出: {path}"))
}
