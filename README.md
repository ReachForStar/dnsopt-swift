# DNS延迟测试工具

基于 **Rust + Tauri 2** 的现代化桌面应用：批量测试 DNS 服务器延迟，并在 Windows 上一键应用最优或指定 DNS、恢复自动获取、刷新本地 DNS 缓存。

DNS 查询由 [hickory-resolver](https://crates.io/crates/hickory-resolver) 直连 UDP 完成，**不捆绑 dig.exe 与任何运行时 DLL**，发布物是一个几 MB 的独立 exe，同时提供 CLI 模式。

## ✨ 功能特性

- 🚀 **批量并行测试** - 所有服务器同时测试，20+ 个 DNS 通常 3~5 秒出结果
- 📊 **延迟等级** - 结果按延迟排序，标注 极佳(<50ms) / 良好(<100ms) / 一般(<200ms) / 较慢
- ⚡ **自动选型** - 测试完成后自动选中最快两个作为首选/辅助，一键应用到所选网卡
- 🎯 **手动选择** - 任选首选/辅助 DNS 应用到所选网卡
- 🔄 **快速恢复** - 一键恢复为自动获取 DNS（DHCP）
- 🧹 **缓存清理** - 刷新本地 DNS 缓存
- 📁 **导入导出** - 导入/导出 DNS 列表，内置 23 个常用 DNS 模板
- 🖥️ **CLI 模式** - 命令行直接测试/设置，适合脚本与远程机器
- 🔐 **按需提权** - 仅"应用/恢复 DNS"触发 UAC 管理员确认，测试无需管理员

## 🖥️ 平台支持

- **Windows 10/11**：✅ 完整支持
- **其他平台**：当前版本面向 Windows（DNS 设置部分依赖 Windows 命令）

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

打包 NSIS 安装程序（可选）：`cargo install tauri-cli --version 2` 后执行 `cargo tauri build`。

### 测试

```bat
cd src-tauri
cargo test
```

12 项测试：引擎单元测试（延迟解析、排序、参数校验）＋ IPC 契约测试（mock runtime 真实 invoke 往返，覆盖命令名、参数名与真实网络调用）。

### CLI 用法

```
adapters                          列出网络接口（名称/状态/ifIndex）
test [IP ...]                     并行测试 DNS 延迟（默认 223.5.5.5 8.8.8.8 114.114.114.114）
set <ifIndex> <主DNS> [辅DNS]     应用静态 DNS（触发 UAC 确认）
auto <ifIndex>                    恢复自动获取 DNS（触发 UAC 确认）
flush                             刷新本地 DNS 缓存
help                              显示帮助
```

示例：

```bat
dns-test-tool.exe adapters
dns-test-tool.exe test 223.5.5.5 8.8.8.8
dns-test-tool.exe set 16 223.5.5.5 223.6.6.6
dns-test-tool.exe auto 16
```

## 🔑 权限说明

- **DNS 测试**：无需管理员
- **应用/恢复 DNS**：弹出 UAC 确认框，点"是"即可（每次操作单独确认，也可右键 exe 以管理员身份运行免确认）
- **刷新缓存**：无需管理员

## 🏗️ 项目结构

```
src-tauri/            Rust 后端（Tauri 2）
├── src/
│   ├── main.rs       入口：无参数启动 GUI，带参数进入 CLI
│   ├── lib.rs        Tauri 命令层（7 个命令，camelCase 注册）
│   ├── dns/mod.rs    测试引擎：hickory UDP 直连、3 次采样取平均、并行测试
│   ├── net/windows.rs 网卡枚举、DNS 设置（UAC 提权）、缓存刷新
│   └── cli.rs        CLI 模式
├── tests/ipc.rs      IPC 契约回归测试（mock runtime 真实 invoke 往返）
├── build.rs          Tauri 构建脚本（资源/清单，含测试目标清单注入）
├── capabilities/     Tauri 权限配置
└── tauri.conf.json   窗口与打包配置
frontend/             前端（原生 HTML/CSS/JS，无构建步骤）
├── index.html        界面结构
├── style.css         样式
├── app.js            交互逻辑（内置常用 DNS 模板、主副 DNS 自动选最快两个）
└── tauri.js          Tauri 内部 API 极简封装
```

## 📄 许可证

Apache License 2.0，见 [LICENSE](LICENSE)。

## 🤝 联系方式

- 问题与建议：请在仓库提交 Issue（推荐）
- 邮箱：1926885268@qq.com
