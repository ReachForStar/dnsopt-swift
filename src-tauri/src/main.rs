// GUI 发布构建隐藏控制台窗口；CLI 模式在 main 中按需挂接控制台
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();

    if dns_test_tool::is_cli_mode(&args) {
        attach_console_if_needed();
        std::process::exit(dns_test_tool::run_cli(&args));
    }

    dns_test_tool::run();
}

// release 下 windows_subsystem 没有控制台，CLI 模式挂接一个用于输出
#[cfg(all(windows, not(debug_assertions)))]
fn attach_console_if_needed() {
    unsafe {
        windows_sys::Win32::System::Console::AllocConsole();
    }
}

#[cfg(not(all(windows, not(debug_assertions))))]
fn attach_console_if_needed() {}
