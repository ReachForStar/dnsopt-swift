# DNS延迟测试工具

[![CI](https://github.com/ReachForStar/dnsopt-swift/actions/workflows/ci.yml/badge.svg)](https://github.com/ReachForStar/dnsopt-swift/actions/workflows/ci.yml)
[![Release](https://github.com/ReachForStar/dnsopt-swift/actions/workflows/release.yml/badge.svg)](https://github.com/ReachForStar/dnsopt-swift/actions/workflows/release.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

基于 **Rust + Tauri 2** 的桌面应用：批量测试 DNS 服务器延迟，并在 Windows 上一键应用最优或指定 DNS、恢复自动获取、刷新本地 DNS 缓存，附带诊断报告与时序监控。

DNS 查询由 [hickory-resolver](https://crates.io/crates/hickory-resolver) 直连 UDP 完成，**不捆绑 dig.exe 与任何运行时 DLL**，发布物是几 MB 的独立 exe，同时提供 CLI 模式。

## ⬇️ 下载安装

从 [Releases](https://github.com/ReachForStar/dnsopt-swift/releases) 下载最新的 NSIS 安装包 `DNS延迟测试工具_x.y.z_x64-setup.exe`（当前版本 **v3.0.0**），双击安装即可。

也可以不使用安装包，直接从源码构建独立的绿色版 exe（见下文「构建」）。

## ✨ 功能特性

- 🚀 **批量并行测试** - 所有服务器同时测试，20+ 个 DNS 通常 3~5 秒出结果
- 🔁 **采样与抖动** - 采样轮数 1/3/5/10/20，输出 min/avg/max、抖动（标准差）与丢包率，结果按延迟排序并标注等级（极佳 ≤49ms / 良好 ≤99ms / 一般 ≤199ms / 较慢）
- 🖥️ **多域名 / 多查询类型** - 测试域名可填多个（逗号分隔，最多 10 个），查询类型支持 A / AAAA / MX
- 🛡️ **污染与劫持检测** - 同一域名在多个服务器上的应答做多数对比，少数派标记为「疑似污染/劫持」；加密 **DoH（HTTPS）** 服务器参与对比（内置阿里云 DoH 与 doh.pub）
- ⚡ **自动选型** - 只从「极佳且无可疑标记」的结果里自动选最快两个作为首选/辅助，一键应用到所选网卡
- 🎯 **手动选择** - 任选首选/辅助 DNS 应用到所选网卡
- 🔄 **快速恢复** - 一键恢复为自动获取 DNS（DHCP）
- 📈 **时序监控** - 按 5 秒~5 分钟间隔循环测试，折线图展示各服务器延迟随时间变化（保留最近 120 个采样点）
- 📄 **诊断报告** - 一键导出文本报告：系统信息、各网卡状态与当前 DNS、DNS 缓存摘要、本次测试结果
- 🧹 **缓存与查询** - 刷新本地 DNS 缓存；查看系统 DNS 解析缓存；查看所选接口当前配置的 DNS
- 📁 **导入导出** - 导入/导出 DNS 列表，内置 33 个常用 DNS 模板（含 2 个 DoH）
- 🖥️ **CLI 模式** - 命令行直接测试/设置/导出报告，适合脚本与远程机器
- 🔐 **按需提权** - 仅「应用/恢复 DNS」触发 UAC 管理员确认，测试无需管理员

## 🖥️ 平台支持

- **Windows 10/11**：✅ 完整支持（DNS 设置依赖 Windows 命令）
- **其他平台**：暂不支持（DNS 应用/缓存命令为 Windows 实现）

## 🚀 快速开始

### 运行（已构建）

双击或命令行执行 `src-tauri/target/release/dns-test-tool.exe`：

- 无参数 → 图形界面
- 带参数 → CLI 模式

### 构建

要求：[rustup](https://rustup.rs)（MSVC 工具链）+ Visual Studio Build Tools（C++ 组件）；运行时需 WebView2（Windows 10/11 系统自带）。

```bat
cd src-tauri
cargo build --release
```

生成 `src-tauri/target/release/dns-test-tool.exe`（独立可执行文件，无需安装任何运行时）。

打包 NSIS 安装程序（可选）：安装 tauri CLI 后执行打包（`tauri.conf.json` 已把 `bundle.targets` 设为 `nsis`）。

```bat
cargo install tauri-cli --version ^2 --locked
cd src-tauri
cargo tauri build
```

产物位于 `src-tauri/target/release/bundle/nsis/`。

### 测试

```bat
cd src-tauri
cargo test
```

28 项测试：15 项引擎/系统交互单元测试（延迟解析、排序、等级阈值、污染判定、参数校验、PowerShell 输出解析）＋ 13 项 `tests/ipc.rs` IPC 契约测试（Tauri mock runtime 走真实 invoke 往返，覆盖命令名/参数名契约与真实系统、网络调用）。

### GUI 使用流程

1. 左上角选择要设置的**网络接口**（显示连接状态，点「刷新」重新枚举，点「查看DNS」看该接口当前配置）
2. 「DNS服务器配置」区每行输入一个 DNS 地址（`#` 开头为注释），或点「加载常用」带入 33 个常用模板
3. 按需调整**采样次数**、**查询类型**与**测试域名**，点「开始测试」：结果按延迟排序并标注等级与疑似污染，自动选中最快且无可疑的两个作为首选/辅助
4. 需要长期观察时点「监控」：设定间隔与采样域名，折线图实时展示延迟变化，点「停止并关闭」结束
5. 「DNS设置」区可手动调整首选/辅助 → 点「应用选定DNS」（弹 UAC 确认）
6. 随时可用「恢复自动DNS」切回 DHCP、「刷新DNS缓存」清理本地缓存、「查看缓存」查看解析缓存、「生成报告」导出诊断文本

### CLI 用法

```
adapters                          列出网络接口（名称/状态/ifIndex）
test [IP ...]                     并行测试 DNS 延迟（默认 223.5.5.5 8.8.8.8 114.114.114.114）
set <ifIndex> <主DNS> [辅DNS]     应用静态 DNS（触发 UAC 确认）
auto <ifIndex>                    恢复自动获取 DNS（触发 UAC 确认）
flush                             刷新本地 DNS 缓存
cache                             查看系统 DNS 解析缓存
dns <ifIndex>                     查看接口当前配置的 DNS 服务器
diagnose [输出文件]               生成诊断报告（省略文件名则打印到控制台）
help                              显示帮助
```

示例：

```bat
dns-test-tool.exe adapters
dns-test-tool.exe test 223.5.5.5 8.8.8.8
dns-test-tool.exe dns 16
dns-test-tool.exe set 16 223.5.5.5 223.6.6.6
dns-test-tool.exe auto 16
dns-test-tool.exe diagnose report.txt
```

## 🔑 权限说明

- **DNS 测试 / 缓存查询 / 诊断报告**：无需管理员
- **应用/恢复 DNS**：弹出 UAC 确认框，点「是」即可（每次操作单独确认，也可右键 exe 以管理员身份运行免确认）
- **刷新缓存**：无需管理员

## 🏗️ 项目结构

```
src-tauri/            Rust 后端（Tauri 2）
├── src/
│   ├── main.rs       入口：无参数启动 GUI，带参数进入 CLI
│   ├── lib.rs        Tauri 命令层（10 个命令，显式 camelCase 注册）
│   ├── dns/mod.rs    测试引擎：hickory UDP/DoH 直连、多次采样、多域名与污染判定
│   ├── net/windows.rs 网卡枚举、当前 DNS 查询、DNS 设置（UAC 提权）、缓存读取/刷新、诊断报告
│   └── cli.rs        CLI 模式
├── tests/ipc.rs      IPC 契约回归测试（mock runtime 真实 invoke 往返）
├── build.rs          Tauri 构建脚本（含测试目标 comctl32 v6 清单注入）
├── capabilities/     Tauri 权限配置
└── tauri.conf.json   窗口与打包配置（NSIS）
frontend/             前端（原生 HTML/CSS/JS，无构建步骤）
├── index.html        界面结构（含监控、缓存、接口 DNS 三个模态框）
├── style.css         样式
├── app.js            交互逻辑（DNS 模板、自动选型、监控折线图、报告导出）
└── tauri.js          Tauri 内部 API 极简封装
.github/workflows/    ci.yml（测试 + 安装包构建验证）、release.yml（tag 触发发布）
```

## ❓ 常见问题

- **界面空白或打不开**：确认已安装 WebView2 Runtime（Windows 11 与较新 Windows 10 自带；旧版 Win10 可在「设置 → 可选更新」中安装）。
- **想去掉 UAC 确认框**：右键 exe 选择「以管理员身份运行」，之后的应用/恢复操作不再弹确认。
- **验证 DNS 是否生效**：`ipconfig /all` 查看对应网卡的首选/备用 DNS；或 `nslookup www.baidu.com 223.5.5.5` 验证指定服务器可用。
- **CLI 在 GUI 模式下没有输出**：带参数启动即为 CLI 模式，无参数启动图形界面；`help` 查看全部用法。
- **结果被标记「疑似污染/劫持」**：同一域名在不同服务器上的应答地址不一致，少数派被标记；若多数服务器本身被劫持，判定会指向多数派，可结合 DoH 结果与 `nslookup` 人工复核。

## 🛠️ 开发与发布

- **CI**：push 到 `main` 与任意 PR 触发 `cargo test`；push 到 `main` 时额外构建 NSIS 安装包并作为 artifact 上传（PR 不构建安装包）。
- **发布**：同步修改 `src-tauri/Cargo.toml` 与 `src-tauri/tauri.conf.json` 的版本号 → 推送 `main` 并确认 CI 通过 → 打 tag 并推送。

```bat
git tag v3.0.0
git push origin v3.0.0
```

tag 匹配 `v*` 时由 `release.yml`（`tauri-action`）自动构建安装包并创建 GitHub Release。

## 📝 版本历史

- **v3.0.0**（2026-09）：Rust + Tauri 2 完整重写；hickory 直连 UDP 替代 dig 捆绑；新增 CLI 模式与 IPC 契约测试；新增多域名/多查询类型、抖动与丢包统计、DoH 通道与污染检测、时序监控、诊断报告；修复诊断报告字段缺失与 CI 工作流
- **v2.0.0** 及以前：Java Swing 版本，已随 v3.0.0 从仓库移除

## 📄 许可证

Apache License 2.0，见 [LICENSE](LICENSE)。

## 🤝 联系方式

- 问题与建议：请在仓库提交 Issue（推荐）
- 邮箱：1926885268@qq.com
