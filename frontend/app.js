"use strict";

// 与 Java 版一致的常用 DNS 列表
const COMMON_DNS = [
  "223.5.5.5", "223.6.6.6",        // 阿里云
  "114.114.114.114", "114.114.115.115", // 114
  "119.29.29.29", "182.254.116.116", // 腾讯
  "180.76.76.76",                  // 百度
  "1.2.4.8", "210.2.4.8",          // sDNS
  "101.226.4.6", "218.30.118.6",   // 电信
  "123.125.81.6", "140.207.198.6", // 联通
  "121.4.4.201", "121.4.4.40",     // 移动
  "8.8.8.8", "8.8.4.4",            // Google
  "1.1.1.1", "1.0.0.1",            // Cloudflare
  "208.67.222.222", "208.67.220.220", // OpenDNS
  "9.9.9.9", "149.112.112.112",    // Quad9
];

const $ = (id) => document.getElementById(id);
const input = $("dns-input");
const adapterSelect = $("adapter-select");
const primarySelect = $("primary-select");
const secondarySelect = $("secondary-select");
const statusText = $("status-text");
const progress = $("progress");
const resultTbody = document.querySelector("#result-table tbody");

let adapters = [];      // { name, status, ifIndex, connected }
let results = [];       // { server, latencyMs, success, error }
let busy = false;

// ---------- 状态 ----------

function setStatus(msg, cls) {
  statusText.textContent = msg;
  statusText.className = cls || "";
}

function setBusy(state) {
  busy = state;
  progress.classList.toggle("hidden", !state);
  ["btn-test", "btn-apply-selected", "btn-restore", "btn-flush"].forEach(
    (id) => ($(id).disabled = state)
  );
}

// ---------- 输入解析 ----------

const IPV4_RE = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/;

function isValidIpv4(s) {
  const m = IPV4_RE.exec(s);
  if (!m) return false;
  return m.slice(1).every((p) => Number(p) <= 255);
}

function parseInput() {
  const lines = input.value.split(/\r?\n/);
  const valid = [];
  const invalid = [];
  for (const raw of lines) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    (isValidIpv4(line) ? valid : invalid).push(line);
  }
  return { valid, invalid };
}

// ---------- 网络接口 ----------

async function loadAdapters(keepName) {
  setStatus("正在加载网络接口…", "busy");
  try {
    adapters = await DnsTauri.invoke("listAdapters");
  } catch (e) {
    setStatus("加载网络接口失败: " + e, "err");
    alert("加载网络接口失败:\n" + e);
    return;
  }
  adapterSelect.innerHTML = "";
  for (const a of adapters) {
    const opt = document.createElement("option");
    opt.value = a.ifIndex;
    opt.textContent = a.name + (a.connected ? " (已连接)" : " (未连接)");
    adapterSelect.appendChild(opt);
  }
  if (keepName) {
    const match = adapters.find((a) => a.name === keepName);
    if (match) adapterSelect.value = String(match.ifIndex);
  }
  setStatus(
    adapters.length ? "就绪 - 找到 " + adapters.length + " 个网络接口" : "就绪 - 未找到网络接口",
    adapters.length ? "ok" : "err"
  );
}

function selectedAdapter() {
  return adapters.find((a) => a.ifIndex === Number(adapterSelect.value)) || null;
}

// ---------- 结果渲染 ----------

const GRADE_BADGE = {
  极佳: "badge-excellent",
  良好: "badge-good",
  一般: "badge-fair",
  较慢: "badge-slow",
};

function renderResults() {
  resultTbody.innerHTML = "";
  primarySelect.innerHTML = "";
  secondarySelect.innerHTML = "";
  const none = document.createElement("option");
  none.value = "";
  none.textContent = "无";
  secondarySelect.appendChild(none);

  for (const r of results) {
    const tr = document.createElement("tr");

    const tdServer = document.createElement("td");
    tdServer.textContent = r.server;

    const tdLat = document.createElement("td");
    tdLat.className = "latency";
    tdLat.textContent = r.success ? String(r.latencyMs) : "N/A";

    const tdStatus = document.createElement("td");
    if (r.success) {
      const grade = r.latencyMs <= 49 ? "极佳" : r.latencyMs <= 99 ? "良好" : r.latencyMs <= 199 ? "一般" : "较慢";
      const badge = document.createElement("span");
      badge.className = "badge " + GRADE_BADGE[grade];
      badge.textContent = grade;
      tdStatus.appendChild(badge);
    } else {
      const badge = document.createElement("span");
      badge.className = "badge badge-fail";
      badge.textContent = "失败";
      tdStatus.appendChild(badge);
      const err = document.createElement("div");
      err.className = "err-text";
      err.textContent = r.error || "";
      tdStatus.appendChild(err);
    }

    tr.append(tdServer, tdLat, tdStatus);
    resultTbody.appendChild(tr);

    if (r.success) {
      const label = r.server + " (" + r.latencyMs + "ms)";
      for (const sel of [primarySelect, secondarySelect]) {
        const opt = document.createElement("option");
        opt.value = r.server;
        opt.textContent = label;
        sel.appendChild(opt);
      }
    }
  }

  // 自动选择最快的两个（主副不同时）
  const ok = results.filter((r) => r.success);
  if (ok.length > 0) {
    primarySelect.value = ok[0].server;
    if (ok.length > 1) secondarySelect.value = ok[1].server;
  }

  const okCount = ok.length;
  $("result-summary").textContent = results.length
    ? "成功 " + okCount + " / " + results.length +
      (okCount ? "，最快: " + ok[0].server + " (" + ok[0].latencyMs + "ms)" : "")
    : "";
}

// ---------- 测试 ----------

async function runTest() {
  if (busy) return;
  const { valid, invalid } = parseInput();
  if (invalid.length > 0) {
    alert("以下条目不是有效的 IPv4 地址，已跳过:\n" + invalid.join("\n"));
  }
  if (valid.length === 0) {
    alert("没有找到有效的 DNS 服务器");
    return;
  }

  setBusy(true);
  setStatus("正在并行测试 " + valid.length + " 个 DNS 服务器…", "busy");
  const started = Date.now();

  try {
    results = await DnsTauri.invoke("testDns", { servers: valid });
    renderResults();
    const ok = results.filter((r) => r.success);
    const secs = ((Date.now() - started) / 1000).toFixed(1);
    if (ok.length > 0) {
      setStatus("测试完成（" + secs + "s）- 最快 DNS: " + ok[0].server + " (" + ok[0].latencyMs + "ms)", "ok");
    } else {
      setStatus("测试完成（" + secs + "s）- 没有可用的 DNS 服务器", "err");
    }
  } catch (e) {
    setStatus("DNS 测试失败: " + e, "err");
    alert("DNS 测试失败:\n" + e);
  } finally {
    setBusy(false);
  }
}

// ---------- 应用 / 恢复 / 刷新 ----------

async function applyDns(primary, secondary, title) {
  const adapter = selectedAdapter();
  if (!adapter) {
    alert("请先选择一个网络接口");
    return;
  }
  const msg =
    "确定要" + title + "吗？\n\n" +
    "接口: " + adapter.name + "\n" +
    "首选DNS: " + primary + "\n" +
    "辅助DNS: " + (secondary || "无") +
    "\n\n（将弹出 UAC 管理员权限确认框）";
  if (!confirm(msg)) return;

  setBusy(true);
  setStatus("正在应用 DNS 设置（等待管理员确认）…", "busy");
  try {
    const msg2 = await DnsTauri.invoke("applyDns", {
      ifIndex: adapter.ifIndex,
      primary,
      secondary: secondary || null,
    });
    setStatus("DNS 设置已成功应用到 \"" + adapter.name + "\"", "ok");
    alert(msg2 + "\n接口: " + adapter.name + "\n首选: " + primary + "\n辅助: " + (secondary || "无"));
  } catch (e) {
    setStatus("DNS 设置应用失败: " + e, "err");
    alert("DNS 设置应用失败:\n" + e);
  } finally {
    setBusy(false);
  }
}

function applySelected() {
  if (!primarySelect.value) {
    alert("请先进行 DNS 测试");
    return;
  }
  applyDns(primarySelect.value, secondarySelect.value || null, "应用选定的 DNS 到所选网络接口");
}

async function restoreAuto() {
  const adapter = selectedAdapter();
  if (!adapter) {
    alert("请先选择一个网络接口");
    return;
  }
  if (!confirm("确定要将 \"" + adapter.name + "\" 恢复为自动获取 DNS（DHCP）吗？\n\n（将弹出 UAC 管理员权限确认框）")) return;

  setBusy(true);
  setStatus("正在恢复自动 DNS 设置（等待管理员确认）…", "busy");
  try {
    await DnsTauri.invoke("resetDns", { ifIndex: adapter.ifIndex });
    setStatus("已恢复 \"" + adapter.name + "\" 为自动获取 DNS", "ok");
    alert("已成功恢复为自动获取 DNS 设置");
  } catch (e) {
    setStatus("恢复自动 DNS 失败: " + e, "err");
    alert("恢复自动 DNS 失败:\n" + e);
  } finally {
    setBusy(false);
  }
}

async function flushCache() {
  if (!confirm("确定要刷新本地 DNS 缓存吗？\n这将清除缓存并强制重新解析 DNS 信息。")) return;

  setBusy(true);
  setStatus("正在刷新 DNS 缓存…", "busy");
  try {
    await DnsTauri.invoke("flushCache");
    setStatus("DNS 缓存已成功刷新", "ok");
  } catch (e) {
    setStatus("DNS 缓存刷新失败: " + e, "err");
    alert("DNS 缓存刷新失败:\n" + e);
  } finally {
    setBusy(false);
  }
}

// ---------- 导入 / 导出 ----------

async function importDns() {
  try {
    const path = await DnsTauri.invoke("plugin:dialog|open", {
      options: {
        title: "导入 DNS 配置",
        filters: [{ name: "文本文件", extensions: ["txt"] }],
      },
    });
    if (!path) return;
    const text = await DnsTauri.invoke("importDns", { path });
    input.value = text;
    setStatus("已导入 DNS 配置: " + path.split(/[\\/]/).pop(), "ok");
  } catch (e) {
    alert("导入失败:\n" + e);
  }
}

async function exportDns() {
  const content = input.value.trim();
  if (!content) {
    alert("没有可导出的 DNS 配置");
    return;
  }
  try {
    const path = await DnsTauri.invoke("plugin:dialog|save", {
      options: {
        title: "导出 DNS 配置",
        defaultPath: "dns_config.txt",
        filters: [{ name: "文本文件", extensions: ["txt"] }],
      },
    });
    if (!path) return;
    await DnsTauri.invoke("exportDns", { path, content });
    setStatus("已导出 DNS 配置: " + path.split(/[\\/]/).pop(), "ok");
  } catch (e) {
    alert("导出失败:\n" + e);
  }
}

// ---------- 事件绑定 ----------

document.addEventListener("DOMContentLoaded", () => {
  input.value = COMMON_DNS.join("\n");

  $("btn-test").addEventListener("click", runTest);
  $("btn-apply-selected").addEventListener("click", applySelected);
  $("btn-restore").addEventListener("click", restoreAuto);
  $("btn-flush").addEventListener("click", flushCache);
  $("btn-common").addEventListener("click", () => { input.value = COMMON_DNS.join("\n"); });
  $("btn-clear").addEventListener("click", () => { input.value = ""; });
  $("btn-import").addEventListener("click", importDns);
  $("btn-export").addEventListener("click", exportDns);
  $("btn-refresh-adapter").addEventListener("click", () => {
    const keep = selectedAdapter() ? selectedAdapter().name : null;
    loadAdapters(keep);
  });

  if (DnsTauri.available) {
    loadAdapters();
  } else {
    setStatus("未在 Tauri 环境中运行，仅显示界面", "err");
  }
});
