# 知识库索引

> 本文件由 wiki 维护者（Code Agent）更新；页面按类型分组。

## entities（模块/系统）

- [dns-test-tool（Rust/Tauri 版）](entities/dns-test-tool-rust.md) — v3.0.0 模块结构：测试引擎、系统交互、CLI、前端
- [GitHub Actions 工作流（CI / Release）](entities/github-actions-workflows.md) — CI 测试矩阵与 tag 触发发布链、tauri-action 打包 NSIS、发布流程

## decisions（技术决策 ADR）

- [技术栈转为 Rust（Tauri 2 + hickory-resolver）](decisions/rust-rewrite-stack.md) — 2026-09-20 选型：GUI 框架、DNS 引擎、提权机制

## queries（排查/分析）

- [PowerShell 提权与 Rust 构建的坑（Windows）](queries/powershell-uac-and-gotchas.md) — 引号/EncodedCommand、Get-NetAdapter 本地化、GUI 控制台、hickory 0.26 API
- [Tauri 命令名契约与测试二进制清单缺失（0xc0000139）](queries/tauri-command-naming-and-test-binary-manifest.md) — rename/rename_all 真实语义、comctl32 v6 清单只嵌入 bin、IPC 契约测试方法
- [diagnose 结果字段契约（missing field jitterMs）](queries/diagnose-results-field-contract.md) — 前端手写字段映射漏统计字段、serde default 容错与回归测试
