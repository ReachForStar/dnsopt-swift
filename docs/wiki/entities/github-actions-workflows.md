---
title: GitHub Actions 工作流（CI / Release）
type: entity
tags: [ci, github-actions, 发布, 构建]
created: 2026-09-20
updated: 2026-09-20
sources: []
status: active
---

# GitHub Actions 工作流（CI / Release）

仓库两个工作流：`.github/workflows/ci.yml`（测试）与 `.github/workflows/release.yml`（打包发布）。
远端仓库为 `ReachForStar/dnsopt-swift`（旧地址 `xyx1926885268/dnsopt-swift` 由 GitHub 重定向，
本地 `origin` 仍写旧地址，push 时打印 "This repository moved" 提示但可用）。

## CI（ci.yml）

| 项 | 值 |
| --- | --- |
| 触发 | push 到 `main`、任意 pull_request |
| 运行环境 | `windows-latest`（系统交互代码 Get-NetAdapter/UAC/displaydns 仅 Windows 可测） |
| 超时 | 25 分钟 |
| 工具链 | `dtolnay/rust-toolchain@stable` |
| 缓存 | `Swatinem/rust-cache@v2`，`workspaces: src-tauri` |
| 命令 | `cargo test`（工作目录 `src-tauri`，实测 1m53s） |

一次 CI 覆盖 15 个库内单元测试 + 13 个 `tests/ipc.rs` 集成测试（IPC 契约 + 真实系统调用）。
集成测试含真实网络用例（`223.5.5.5`、`8.8.8.8`、DoH `https://dns.alidns.com/dns-query`），
CI 需公网可达；`192.0.2.1` 一类不可达目标的断言只校验结果形态自洽，不写死成败。

### build-installer job

push 到 `main` 时额外跑 `build-installer`：用 `tauri-action`（不带 tag）执行 `tauri build`，
再用 `actions/upload-artifact@v4` 把 `src-tauri/target/release/bundle/nsis/*.exe`
传为 artifact `dns-test-tool-nsis`（`if-no-files-found: error`，产物缺失即失败）。
目的在于发布前就能发现打包问题，不必等到打 tag；PR 不跑该 job（`if: github.event_name == 'push'`），
超时 40 分钟，实测 6m10s（首次无缓存时更久），artifact `dns-test-tool-nsis` 约 3.4 MB。

## Release（release.yml）

| 项 | 值 |
| --- | --- |
| 触发 | push tag `v*` |
| 运行环境 | `windows-latest`，超时 60 分钟 |
| 权限 | `permissions: contents: write`（创建 release 与上传资产需要） |
| 构建与上传 | `tauri-apps/tauri-action@v0`：`tagName: ${{ github.ref_name }}`、`releaseName: DNS延迟测试工具 <tag>`、`releaseDraft: false`、`releaseBody`（安装说明 + README/变更记录链接） |
| 产物 | NSIS 安装包 `src-tauri/target/release/bundle/nsis/*.exe`（targets 在 `tauri.conf.json` 的 `bundle.targets` 指定） |

首次验证：tag `v3.0.0` → run 35524785071 的 build job 7m25s 成功，Release 资产 `DNS._3.0.0_x64-setup.exe`（3.4 MB）。

### NSIS 产物文件名

安装包文件名由 `productName` 经 ASCII 过滤生成，而本项目 `productName` 是中文「DNS延迟测试工具」，
故实际资产名变成 `DNS._3.0.0_x64-setup.exe`（中文全被丢弃，只剩 `DNS.` 与版本后缀）。
`tauri.conf.json` 没有重命名安装包的配置项（已查 config schema v2 的 `NsisConfig`，只有图标/语言/压缩/钩子等），
所以在 README 与 Release 说明里按实际名（`*-setup.exe`）描述，不假装它是可读名称。

`tauri-action` 取代了早期的「`cargo install tauri-cli` → `cargo tauri build` → `softprops/action-gh-release`」
三段手工步骤：它自带头 tauri CLI 获取与 bundle 产物上传，避免每次发布从源码编译 tauri-cli。
不带 `tagName`/`releaseId` 时它只构建、不碰 Release，因此 CI 的 `build-installer` 复用了同一个 action。

## 发布流程

1. 同步改版本号：`src-tauri/Cargo.toml` 的 `version` 与 `src-tauri/tauri.conf.json` 的 `version`（含窗口标题里的版本字样）。
2. 提交并推送 `main`，确认 CI 变绿。
3. `git tag v3.0.0 && git push origin v3.0.0` 触发 Release；用 `gh run watch` / `gh run view --log-failed` 跟踪。
4. 验证：`gh release view v3.0.0` 的 assets 含 `*.exe`。

## 历史坑（重要）

- 2026-09-20：两个工作流都写成 `dtolnay/rust-action@stable`——**该仓库不存在**，正确名是
  `dtolnay/rust-toolchain`。症状是所有 run 在 5–9 秒内失败于 "Set up job"，annotation 为
  `Unable to resolve action dtolnay/rust-action, repository not found`；日志层面看不到任何 cargo 输出，
  容易误以为是源码或测试问题。排查入口：`gh run view <id>` 的 ANNOTATIONS 段落，
  或 `gh run view <id> --log-failed`。

## 关联页面

- [dns-test-tool（Rust/Tauri 版）](dns-test-tool-rust.md)
- [Tauri 命令名契约与测试二进制清单缺失](../queries/tauri-command-naming-and-test-binary-manifest.md)
- [diagnose 结果字段契约（missing field jitterMs）](../queries/diagnose-results-field-contract.md)
