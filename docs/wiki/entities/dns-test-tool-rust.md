---
title: dns-test-tool（Rust/Tauri 版）
type: entity
tags: [模块, rust, tauri]
created: 2026-09-20
updated: 2026-09-20
sources: []
status: active
---

# dns-test-tool（Rust/Tauri 版）

DNS 延迟测试工具 v3.0.0 的 Rust 实现，位于仓库根目录 `src-tauri/`（后端）与 `frontend/`（前端）。

## 职责

批量测量 DNS 服务器延迟并管理 Windows 网卡 DNS 设置。GUI（无参数启动）与 CLI（带参数启动）两个入口共用同一套核心模块。

## 模块结构

| 文件 | 职责 | 关键点 |
| --- | --- | --- |
| `src-tauri/src/main.rs` | 入口分发 | 无参数→GUI；CLI 模式在 release（`windows_subsystem`）下 `AllocConsole` 挂控制台 |
| `src-tauri/src/lib.rs` | Tauri 命令层 | 7 个命令均带 `rename = "camelCase 名"`（默认注册名是函数名原样，详见 [Tauri 命令名契约](../queries/tauri-command-naming-and-test-binary-manifest.md)）；`test_app()` 供集成测试构建 mock 应用；文件导入导出直接在 Rust 侧做 |
| `src-tauri/src/dns/mod.rs` | 测试引擎 | hickory UDP 直连，测试域名 `www.baidu.com.`（尾点 FQDN），3 次采样均值；等级阈值 极佳≤49 / 良好≤99 / 一般≤199 / 较慢≥200（ms）；`sort_results`：成功在前、延迟升序 |
| `src-tauri/src/net/windows.rs` | 系统交互 | `Get-NetAdapter` JSON 枚举（ifIndex 为设置入口）；连接状态由 Rust 侧判定后以 `connected` 布尔字段序列化（前端不解析状态文案，pwsh 7 的 Up 文案曾致误判）；UAC 提权用 EncodedCommand；`find_powershell` 优先 pwsh、回退 5.1 |
| `src-tauri/src/cli.rs` | CLI | test / adapters / set / auto / flush / help |
| `frontend/` | 界面 | 原生 HTML/CSS/JS；`tauri.js` 封装 `__TAURI_INTERNALS__.invoke`；内置常用模板 23 个，测试后主副 DNS 自动选最快两个 |

## 上下游依赖

- crates：`tauri 2.11`（test feature）、`tauri-plugin-dialog 2.x`、`hickory-resolver 0.26`、`tokio 1`、`serde`、`base64 0.22`、`windows-sys 0.59`；build 依赖 `embed-resource 3`（测试目标清单注入）
- 系统：Windows 10/11、WebView2（系统自带）、PowerShell（pwsh 7 优先，回退 5.1）
- 网络：DNS 服务器 UDP/53；测试域名 `www.baidu.com`

## 重要变更记录

- 2026-09-20：v3.0.0 由 Java 版完整重写为 Rust/Tauri；dig 方案替换为 hickory 直连；新增 CLI 模式（详见 [Rust 重写决策](../decisions/rust-rewrite-stack.md)）。
- 2026-09-20：修复命令名契约（rename 显式 camelCase）与 GUI 连接状态误判（connected 字段）；新增 tests/ipc.rs 契约回归测试与 build.rs 测试目标清单注入（详见 [Tauri 命令名与测试二进制清单](../queries/tauri-command-naming-and-test-binary-manifest.md)）。
- 2026-09-20：Java 版（pom.xml、src/、build.bat、lib/、standalone.exe、image/）全部移除，仓库只保留 Rust/Tauri 版；GUI 删除与"应用选定DNS"重复的"应用最优DNS"按钮。
