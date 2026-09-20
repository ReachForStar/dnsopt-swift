fn main() {
    tauri_build::build();

    // tauri-build 只把资源（含 comctl32 v6 清单）嵌入 bin 目标（link-arg-bins）；
    // 测试二进制经 tauri test feature 链接 GUI 栈后会导入 comctl32 的
    // TaskDialogIndirect（仅 v6 导出），无清单时加载器绑定 System32 的 v5，
    // 进程加载即失败（0xc0000139），这里为测试目标补嵌同一份资源
    #[cfg(windows)]
    {
        let rc = std::path::Path::new(&std::env::var("OUT_DIR").expect("OUT_DIR 未设置"))
            .join("resource.rc");
        let _ = embed_resource::compile_for_tests(rc, embed_resource::NONE);
    }
}
