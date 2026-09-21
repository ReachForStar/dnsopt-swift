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

## [2026-09-20] 功能扩展第二轮批次 3

- 诊断报告：diagnose 命令（系统/网卡/各接口当前 DNS/缓存摘要/可选测试结果），CLI `diagnose [文件]`，前端「生成报告」导出 txt
- 时序监控：前端监控模式（间隔 5s–5min，循环单轮测试，canvas 折线图保留 120 点）
- 多域名测试：testDns 加 domains 参数（1..=10）；服务器延迟取各成功域名最优值；污染判定按域名独立多数对比
- 验证：cargo test 14 单测 + 10 IPC 全通过；CLI diagnose 实测输出完整

## [2026-09-20] 修复 CI/Release 工作流、修复诊断报告字段契约

- 根因：`ci.yml` / `release.yml` 都引用不存在的 action `dtolnay/rust-action`（正确名 `dtolnay/rust-toolchain`），run 在 5–9 秒内失败于 "Set up job"，annotation `Unable to resolve action ... repository not found`，日志层看不到任何 cargo 输出
- CI 改造：`windows-latest` + `dtolnay/rust-toolchain@stable` + `Swatinem/rust-cache@v2(workspaces: src-tauri)`；push main 追加 `build-installer` job（tauri-action 构建 NSIS + upload-artifact，PR 不跑）
- Release 改造：tag `v*` 触发，改用 `tauri-apps/tauri-action@v0` 构建并创建 Release（替代 `cargo install tauri-cli` + `softprops/action-gh-release`），加 `permissions: contents: write`
- 修复 GUI「生成报告」报 `invalid args 'results' ... missing field jitterMs`：前端改为透传 testDns 结果（原手写字段映射漏 jitterMs/lossRate），`TestResult` 统计字段加 `#[serde(default)]`，新增 `diagnose_with_results` IPC 回归测试
- 验证：CI run 35524075048（test）与 35524363087（test 1m53s + build-installer 6m10s，artifact 3.4MB）均 success；本地 cargo test 28 项（15 单测 + 13 IPC）全通过
- 文档：README 补下载安装、DoH/污染检测/时序监控/诊断报告、CLI 全命令、CI 与发布流程；仓库描述与 topics 更新为 Rust/Tauri 版
- 发布：tag `v3.0.0`（Release run 35524785071，build 7m25s）创建 Release v3.0.0，资产 `DNS._3.0.0_x64-setup.exe`（3.4 MB）；
  安装包文件名由中文 productName 经 ASCII 过滤而来（现无重命名配置项），Release 说明与 README 按实际名描述
- 仓库信息：description 改为 Rust/Tauri 版描述，topics 补 dns/dns-test/latency-test/tauri/rust/windows/doh/nsis
- 仓库地址：本地 `origin` 由 `xyx1926885268/dnsopt-swift.git` 改为 `git@github.com:ReachForStar/dnsopt-swift.git`（旧地址是 GitHub 重定向，此前每次 push 都提示 moved）
