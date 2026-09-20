# 操作日志

## [2026-09-20] Rust 重写 v3.0.0

- 决策：技术栈从 Java Swing 转为 Rust/Tauri 2（见 decisions/rust-rewrite-stack.md）
- 新增：`src-tauri/`（hickory DNS 引擎、Windows 网络管理、CLI 模式）与 `frontend/`（原生 HTML/CSS/JS）
- 验证：8 个单元测试通过；release 构建成功；CLI 实测 6 服务器 4.9s 出结果；UAC 应用/恢复 DNS 端到端成功（WLAN ifIndex 16，223.5.5.5/223.6.6.6 设置后恢复 DHCP）；GUI 进程启动无崩溃
- 踩坑沉淀：PowerShell 引号/EncodedCommand、Get-NetAdapter 状态本地化、GUI 控制台挂接、hickory 0.26 with_options（见 queries/powershell-uac-and-gotchas.md）
- 修复：.gitignore 合并冲突；README 更新为 Rust 版说明
- 待用户确认：Java legacy 代码移除、96MB standalone.exe 与 dependency-reduced-pom.xml 移出跟踪、`%SystemDrive%` 垃圾目录删除
