---
title: Tauri 命令名契约与测试二进制清单缺失（0xc0000139）
type: query
tags: [tauri, rust, windows, 测试, 踩坑]
created: 2026-09-20
updated: 2026-09-20
sources: []
status: active
---

# Tauri 命令名契约与测试二进制清单缺失（0xc0000139）

## 问题 1：GUI 报 "Command list_adapters not found"

**现象**：Rust/Tauri 版 v3.0.0 的 GUI 点击"刷新接口"等功能时报
`Command list_adapters not found`，前端 invoke 全部失败。

**排查过程中的错误结论（记录以免重蹈）**：曾判断"Tauri 命令注册名默认
camelCase"，据此把 Rust 函数从 `cmd_list_adapters` 改名为 `list_adapters`、
前端调用改为 camelCase（`listAdapters`）。该结论是错的——改名后命令反而
完全找不到（IPC 测试实测 `Command listAdapters not found`）。

**真实机制**（tauri-macros 2.6.3 源码确认）：

- 命令注册名 = **函数名原样**（`#[tauri::command]` 宏展开的
  `__tauri_command_name_*` 宏在无 `rename` 时返回 `stringify!(函数名)`），
  即默认 **snake_case**，不自动转 camelCase。
- `rename = "listAdapters"`：改**命令名**（仅命令名）。
- `rename_all = "camelCase"`：只改**参数名**，不改命令名。
- 参数名**默认就是 camelCase**（`ArgumentCase::Camel` 为宏默认值），
  即 `if_index` 参数在前端应传 `ifIndex`，无需任何配置。

**修复**：`src-tauri/src/lib.rs` 每个命令显式
`#[tauri::command(rename = "testDns")]` 等 7 个，注册名与前端
`frontend/app.js` 的 camelCase 调用（`listAdapters / testDns / applyDns /
resetDns / flushCache / importDns / exportDns`）对齐。

**防回归**：`src-tauri/tests/ipc.rs` 回归测试断言 camelCase 可解析、
snake_case 报 "not found"。

## 问题 2：测试二进制 0xc0000139（STATUS_ENTRYPOINT_NOT_FOUND）

**现象**：`cargo test` 时测试可执行文件无法加载（cargo 报 0xc0000139，直接
运行报 127）；同一 crate 的 release GUI 可执行文件正常。

**根因链**：

1. tauri-build 通过 `tauri-winres` → `embed_resource::compile()` 把应用清单
   （内含 comctl32 **v6** 依赖 `Microsoft.Windows.Common-Controls 6.0.0.0`）
   编译为资源，但 `compile()` 只输出 `cargo:rustc-link-arg-bins=...`——
   **清单只嵌入 bin 目标，测试目标拿不到**。
2. 测试二进制（链接 `tauri` test feature 的 mock runtime 后）会把 GUI 栈
   代码链进来，导入 `comctl32.dll` 的 `TaskDialogIndirect`——该入口**仅
   comctl32 v6 导出**，System32 的 v5 没有。
3. 测试二进制没有清单 → 加载器绑定 System32 的 comctl32 v5 → 入口缺失 →
   进程加载即失败（0xc0000139）。

**排除过的假设**（均为误导）：本机 UCRT/VCRUNTIME 过旧、F 盘文件系统
问题、sccache 缓存损坏、debug/release 代码生成差异——在 C 盘全新目录
构建、最小 crate 对照、逐个核对 214 个导入入口后均不成立。

**修复**（`src-tauri/build.rs`）：

```rust
fn main() {
    tauri_build::build();
    // 为测试目标补嵌同一份资源（含 comctl32 v6 清单）
    #[cfg(windows)]
    {
        let rc = std::path::Path::new(&std::env::var("OUT_DIR").expect("OUT_DIR 未设置"))
            .join("resource.rc");
        let _ = embed_resource::compile_for_tests(rc, embed_resource::NONE);
    }
}
```

配套约束：

- `cargo:rustc-link-arg-tests` 只对 **tests/ 集成测试目标**生效；lib 内
  `#[cfg(test)]` 单元测试的二进制拿不到该资源。本项目 IPC 契约测试因此放在
  `tests/ipc.rs`（lib 内单测不链 GUI 栈、不导入 comctl32，可继续留在 lib）。
- `embed-resource = "3"` 需加入 `[target.'cfg(windows)'.build-dependencies]`。
- 若资源同时被 bin 与测试目标链接会产生重复 RT_MANIFEST 资源（LNK1251），
  故不能改用 `compile_for_everything`，必须走 `compile_for_tests` 分目标注入。

## IPC 契约测试方法（tests/ipc.rs 要点）

- `tauri::test::mock_builder().invoke_handler(generate_handler![...])
  .build(mock_context(noop_assets()))` 构建真实 IPC 往返（非 mock 返回值）。
- `mock_context(noop_assets())` 不创建初始窗口（`windows: Vec::new()`）；
  webview 标签在同一 app 内唯一，`WebviewWindowBuilder` 建一次后复用
  （重复建 "main" 报 `WebviewLabelAlreadyExists`）。
- 测试入口 `pub fn test_app()` 放在 lib.rs（集成测试是独立 crate，
  `#[cfg(test)]` 对其不生效，且私有命令函数不可见，必须在 lib 内构建
  handler 后整体暴露）。
- 命令未注册的错误体是**纯字符串**（`"Command xxx not found"`），不是带
  `message` 字段的 JSON 对象；参数错误的错误体含参数名（camelCase 形式）。

## 涉及模块

- `src-tauri/src/lib.rs`（命令 rename、test_app）
- `src-tauri/build.rs`（测试目标清单注入）
- `src-tauri/tests/ipc.rs`（4 个契约回归测试）
- `frontend/app.js`（camelCase invoke 调用方）

## 复发预防

- 新增 Tauri 命令：必须带 `rename = "camelCase 名"`，并在 `tests/ipc.rs`
  补一条 invoke 往返断言；前端调用名与 rename 值逐字一致。
- 测试二进制加载失败先查清单：`mt /inputresource:<exe>;#1 /out:x.manifest`
  提取 RT_MANIFEST，rc=31 即无清单。
