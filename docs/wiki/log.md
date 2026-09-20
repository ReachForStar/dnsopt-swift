# 操作日志

## [2026-09-20] Rust 重写 v3.0.0

- 决策：技术栈从 Java Swing 转为 Rust/Tauri 2（见 decisions/rust-rewrite-stack.md）
- 新增：`src-tauri/`（hickory DNS 引擎、Windows 网络管理、CLI 模式）与 `frontend/`（原生 HTML/CSS/JS）
- 验证：8 个单元测试通过；release 构建成功；CLI 实测 6 服务器 4.9s 出结果；UAC 应用/恢复 DNS 端到端成功（WLAN ifIndex 16，223.5.5.5/223.6.6.6 设置后恢复 DHCP）；GUI 进程启动无崩溃
- 踩坑沉淀：PowerShell 引号/EncodedCommand、Get-NetAdapter 状态本地化、GUI 控制台挂接、hickory 0.26 with_options（见 queries/powershell-uac-and-gotchas.md）
- 修复：.gitignore 合并冲突；README 更新为 Rust 版说明
- 待用户确认：Java legacy 代码移除、96MB standalone.exe 与 dependency-reduced-pom.xml 移出跟踪、`%SystemDrive%` 垃圾目录删除

## [2026-09-20] 命令名契约修复 + 全功能测试

- 修复：Tauri 命令显式 `rename = "camelCase 名"`（默认注册名是函数名原样 snake_case；此前"默认 camelCase"的判断有误）
- 修复：GUI 连接状态误判——pwsh 7 状态文案 Up 前端匹配不到，改为 Rust 侧判定并以 `connected` 字段序列化，前端直接消费
- 排查：测试二进制 0xc0000139 根因是 comctl32 v6 清单只嵌入 bin 目标（embed_resource::compile 仅输出 link-arg-bins）；build.rs 用 compile_for_tests 为测试目标补嵌
- 新增：tests/ipc.rs 4 个 IPC 契约回归测试（mock runtime 真实 invoke 往返）
- 验证：cargo test 12 项全通过；CLI adapters/test/flush 实测正常；release 重建后 GUI 启动正常

## [2026-09-20] 移除 Java 版，仓库只保留 Rust/Tauri 版

- 用户确认：删除 Java 遗留——`src/`（70 文件）、`pom.xml`、`build.bat`、`dependency-reduced-pom.xml`、96MB standalone.exe、`image/`、`lib/`（2 jar）、`target/`（Maven 产物）、`%SystemDrive%/`（垃圾目录）
- `.gitignore` 重写：去掉 Java/Maven/IDE 规则，保留 `src-tauri/target/`、`.qmd/`、`tmp/`、`.vscode/`、`.DS_Store`
- README 同步更新（去 legacy 章节、补测试小节与项目结构）
- GUI：删除与"应用选定DNS"重复的"应用最优DNS"按钮（测试后主副下拉已自动选最快两个）
- 验证：cargo test 12 项全通过；用户 GUI 走查全部功能通过
