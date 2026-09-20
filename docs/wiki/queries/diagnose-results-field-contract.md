---
title: diagnose 结果字段契约（missing field jitterMs）
type: query
tags: [tauri, serde, 契约, 前端, 踩坑]
created: 2026-09-20
updated: 2026-09-20
sources: []
status: active
---

# diagnose 结果字段契约（missing field jitterMs）

## 问题

GUI 点「生成报告」弹出 `生成报告失败: invalid args 'results' for command '"diagnose": missing field jitterMs'`，
报告完全无法导出。

## 根因

`frontend/app.js` 的 `generateReport()` 把 `results` 手写映射成 7 个字段
（`server/latencyMs/latencyMin/latencyMax/success/error/suspect`）后传给 `diagnose`，
而 Rust 侧 `dns::TestResult` 还有 `jitter_ms`、`loss_rate` 两个字段。
`TestResult` 派生 `Deserialize`，缺失字段且无 `#[serde(default)]` 时反序列化直接报错，
Tauri 把该错误作为 invoke 的 reject 抛回前端——错误文案里的 `"diagnose"` 带引号正是 Tauri 的参数错误格式。

注意 `testDns` 返回的结果对象本身就含 `jitterMs`、`lossRate`（`answers` 因 `#[serde(skip)]` 不序列化），
前端的手写映射纯属多余，且是失效源头。

## 修复

1. `frontend/app.js`：直接把 `testDns` 的结果数组透传给 `diagnose`，不手写字段映射。
2. `src-tauri/src/dns/mod.rs`：给 `TestResult` 的统计字段加 `#[serde(default)]`
   （`latency_ms / latency_min / latency_max / success / jitter_ms / loss_rate`），
   只有 `server` 仍为必填。前端字段不全时报告仍能生成，缺 `server` 才报错。

## 回归测试（tests/ipc.rs::diagnose_with_results）

- `testDns` 结果 `serde_json::to_value` 后原样回传 `diagnose` → 报告含「测试结果」与被测服务器地址。
- 只给 `{"server": "1.1.1.1"}` 的极简结果 → 报告仍生成（验证 `#[serde(default)]` 生效）。
- 只给 `{"latencyMs": 10}`（缺 `server`）→ 报错文本含 `server`（不静默丢弃）。

## 复发预防

- 前端传结构化结果给 Rust 时**不要手写字段映射**，直接透传命令返回值；
  必须裁剪时以 Rust 结构体为准逐字段核对。
- 面向「只用于渲染报告」的入参结构体，统计字段用 `#[serde(default)]` 提高容错；
  业务必填字段（如 `server`）保持必填，并用测试断言其报错路径。
- `invalid args ... for command` 文案即 Tauri 反序列化失败，先核对前端 JSON 字段名/齐全度，再查后端。

## 涉及模块

- `frontend/app.js`（`generateReport`）
- `src-tauri/src/dns/mod.rs`（`TestResult`）
- `src-tauri/tests/ipc.rs`（`diagnose_with_results`）

## 关联页面

- [Tauri 命令名契约与测试二进制清单缺失](tauri-command-naming-and-test-binary-manifest.md)
- [dns-test-tool（Rust/Tauri 版）](../entities/dns-test-tool-rust.md)
