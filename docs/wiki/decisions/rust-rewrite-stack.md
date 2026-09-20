---
title: 技术栈转为 Rust（Tauri 2 + hickory-resolver）
type: decision
tags: [架构, rust, tauri, 选型]
created: 2026-09-20
updated: 2026-09-20
sources: []
status: active
---

# 决策：技术栈转为 Rust（Tauri 2 + hickory-resolver）

## 背景

原 v2.0.0 是 Java Swing + Maven 项目，痛点：

1. 捆绑 BIND 的 dig.exe 与 12 个 DLL（约 20MB+ 资源），启动时还要解压到临时目录；
2. 打包链条重（Maven + shade + launch4j/jpackage/GraalVM 五种 profile），96MB 的 standalone exe 直接进仓库；
3. dig 的等待超时（500ms）小于 dig 自身超时（1000ms），慢但可达的 DNS 被误判失败；
4. 测试串行执行，23 个 DNS 要几十秒。

用户要求"技术转为 Rust"，目标是等功能的 Rust 重写。

## 备选方案

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| **Tauri 2**（Rust 后端 + WebView 前端，原生 HTML/CSS/JS） | 生态最大、UI 现代化成本低、exe 几 MB、前端零构建 | UI 部分是 Web 技术而非纯 Rust；依赖 WebView2（Win10/11 自带） | ✅ 选用 |
| Slint | 纯 Rust、声明式 | 表格/表单组件生态较薄，主题定制成本更高 | 备选 |
| iced | 纯 Rust、即时模式 | 即时模式下表格交互代码量大 | 备选 |
| 保留 dig 进程做 DNS 测试 | 与旧版一致 | 捆绑体积大、进程调度开销、超时语义难对齐 | ❌ 弃用，改 hickory 直连 UDP |
| 应用/恢复 DNS 走 netsh | 旧版方案 | 接口别名按语言本地化，参数拼接易错 | ❌ 弃用，改 PowerShell `Set-DnsClientServerAddress -InterfaceIndex`（数字索引与语言无关） |

## 决策

- GUI：**Tauri 2**（`tauri 2.11.x` + `tauri-plugin-dialog 2.x`），前端为无构建步骤的静态 HTML/CSS/JS，通过 `window.__TAURI_INTERNALS__.invoke` 调用 Rust 命令。
- DNS 测试：**hickory-resolver 0.26** 直连 UDP 查询 A 记录，每服务器 3 次采样取平均，`timeout=1500ms`、`attempts=0`、`Ipv4Only`；全部服务器并行（tokio spawn）。
- Windows 管理：`Get-NetAdapter | ConvertTo-Json` 枚举网卡（取 ifIndex）；应用/恢复用 `Set-DnsClientServerAddress -InterfaceIndex` 经 UAC 提权执行；刷新缓存用 `ipconfig /flushdns`（无需提权）。
- 提权机制：外层 PowerShell 以 `-EncodedCommand`（UTF-16LE+Base64，免一切引号问题）运行 `Start-Process ... -Verb RunAs -Wait -PassThru`，透传内层退出码区分"成功/失败/用户取消"。
- 新增 CLI 模式（test/adapters/set/auto/flush），release 下 `windows_subsystem` 隐藏控制台，CLI 时 `AllocConsole` + 直接写控制台句柄。
- 发布物：单个 `dns-test-tool.exe`（cargo release，strip + lto thin），不再需要 JRE/dig。

## 理由

- Tauri 是 Rust 桌面事实标准，UI 现代化（表格/弹窗/进度条）成本最低，且本工具 100% 的核心逻辑（DNS 测试、系统设置）都在 Rust 侧。
- hickory 直连 UDP 消除了 dig 捆绑与超时语义错配两个根因，测量值就是真实往返时间。
- 数字 ifIndex 做设置入口，规避了旧版"接口名按语言本地化 + 引号转义"的脆弱解析。
- CLI 模式让核心功能可无界面验证（CI/远程场景），也让本次重写的验证不依赖视觉手段。

## 后果

- 仓库内并存 Rust 版（`src-tauri/` + `frontend/`）与 Java legacy 版（根目录），待用户确认后再移除 Java。
- 前端依赖 `__TAURI_INTERNALS__` 内部 API（无 npm 构建），Tauri 大版本升级时需核对。
- 应用/恢复 DNS 每次操作弹一次 UAC（用户可整体以管理员运行规避）。
- 构建依赖 MSVC 工具链（本机 VS2022 BuildTools 已满足）；全局 cargo 配置使用 rsproxy 镜像。
