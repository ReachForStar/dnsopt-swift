---
title: PowerShell 提权与 Rust 构建的坑（Windows）
type: query
tags: [踩坑, powershell, uac, rust, windows, hickory]
created: 2026-09-20
updated: 2026-09-20
sources: []
status: active
---

# PowerShell 提权与 Rust 构建的坑（Windows）

## 问题 1：PowerShell 命令引号转义

**现象**：Java 版用 `\"` 给 PowerShell 参数加引号，`\"` 在 PowerShell 里不是转义符（转义符是反引号），参数会被拆坏。

**解法**：
- 参数含特殊字符时用**单引号**包裹（PowerShell 单引号字符串内只有 `''` 一种转义）；
- 整个脚本再用 `-EncodedCommand`（UTF-16LE + Base64，**无 BOM**）传输，外层零引号问题。

```text
外层: powershell -NoProfile -ExecutionPolicy Bypass -EncodedCommand <b64>
内层脚本: $script='Set-DnsClientServerAddress -InterfaceIndex 16 -ServerAddresses ''223.5.5.5'',''223.6.6.6''
          $p = Start-Process powershell.exe -Verb RunAs -Wait -PassThru -ArgumentList @('-NoProfile','-Command',$script)
          exit $p.ExitCode
```

透传 `$p.ExitCode` 可区分成功/失败；用户取消 UAC 时 stderr 含 `canceled by the user` / `用户取消了操作`。

## 问题 2：Get-NetAdapter 的 Status 按 PowerShell 版本本地化不同

**现象**：同一台中文系统上，pwsh 7 输出 `Status: "Up"`（英文），5.1 输出中文"已连接"。只匹配"已连接/Connected"会把已连接网卡判为未连接。

**解法**：状态判定兼容三套文案：`已连接` / `Connected` / `Up` 为已连接；`Disconnected` / `已断开` 为未连接。注意 `Disconnected` 含子串 `connected`，匹配顺序要先排除。

## 问题 3：GUI（windows_subsystem）下 CLI 没有控制台

**现象**：release 构建加 `#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]` 后，CLI 模式的 `println!` 全部丢失；`std::io::set_stdout` 在该工具链未稳定，不可用。

**解法**：CLI 启动时 `AllocConsole()`，然后 `GetStdHandle(STD_OUTPUT_HANDLE/STD_ERROR_HANDLE)` 拿到控制台句柄，用 `File::from_raw_handle` 直接 `write_all` 字节（句柄所有权泄漏可接受，进程即将退出）。

## 问题 4：hickory-resolver 版本线重编号 + 选项不生效

**现象**：
- rsproxy 镜像上 hickory-resolver 只有 0.1.0 与 0.24~0.26 线（旧 0.9x 线不可用），训练记忆里的 0.99 API 全部失效；
- 0.26 中 `TokioResolver` 是 `Resolver<TokioRuntimeProvider>` 的别名，构造要走 `builder_with_config(config, provider).with_options(opts).build()`；
- 只 `builder_with_config` 不 `.with_options()` 时，`timeout`/`attempts` 全部按默认值（5s/2 次重试）生效——死服务器 3 个样本实测 45s；加上 `with_options` 后降到 4.9s。

**解法**：写 hickory 代码前先下载对应版本的 `.crate` 源码核对 API（`https://rsproxy.cn/api/v1/crates/<name>/<ver>/download`，注意 307 要 `-L` 跟随）；延迟测试务必显式设置 `timeout=1500ms`、`attempts=0`、`Ipv4Only`。

## 问题 5：SocketAddr 不能解析裸 IP

**现象**：`"223.5.5.5".parse::<SocketAddr>()` 报 `invalid socket address syntax`（SocketAddr 需要端口）。

**解法**：DNS 服务器解析用 `IpAddr`，协议与端口由 hickory 处理。

## 涉及模块

[dns-test-tool（Rust/Tauri 版）](../entities/dns-test-tool-rust.md)：`net/windows.rs`（问题 1、2、3）、`dns/mod.rs`（问题 4、5）。

## 复发预防

- 新增 PowerShell 交互一律走 EncodedCommand + 单引号 + ifIndex 数字参数；
- 状态判定新增文案时同步更新 `is_connected` 的测试用例（覆盖 Up/Disconnected/已连接/已断开）;
- 升级 hickory 大版本前重新核对本页问题 4 的 API 结论。
