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
  // 区域运营商（电信/Chinanet，用户提供）
  "111.170.166.6", "113.96.17.165", "183.2.141.97",
  "61.151.230.52", "14.215.166.64", "221.231.139.97",
  "14.215.166.106", "183.2.141.242",
  // 加密 DoH（HTTPS 通道，参与污染对比但不做应答源校验）
  "https://dns.alidns.com/dns-query",     // 阿里云 DoH
  "https://doh.pub/dns-query",            // 公共解析 DoH
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

// 支持的服务器：IPv4 或 https DoH URL
function isServer(s) {
  return isValidIpv4(s) || (s.startsWith("https://") && !s.includes(" ") && s.length > 8);
}

function parseInput() {
  const lines = input.value.split(/\r?\n/);
  const valid = [];
  const invalid = [];
  for (const raw of lines) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    (isServer(line) ? valid : invalid).push(line);
  }
  return { valid, invalid };
}

// 域名输入：逗号/分号/换行分隔，去空去重，上限 10 个
function parseDomains() {
  const parts = $("domain-input").value
    .split(/[,;\r\n]+/)
    .map((s) => s.trim())
    .filter(Boolean);
  const uniq = [...new Set(parts)];
  return { domains: uniq.slice(0, 10), tooMany: uniq.length > 10 };
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
    if (r.success) {
      // 条形图宽度按本轮最大延迟归一，颜色随等级
      const maxLat = Math.max(...results.filter((x) => x.success).map((x) => x.latencyMax), 1);
      const bar = document.createElement("div");
      bar.className = "latency-bar-track";
      const fill = document.createElement("div");
      fill.className = "latency-bar";
      fill.style.width = Math.max(2, (r.latencyMax / maxLat) * 100) + "%";
      bar.appendChild(fill);
      tdLat.appendChild(bar);
      const txt = document.createElement("span");
      txt.className = "latency-text";
      txt.textContent = r.latencyMin + " / " + r.latencyMs + " / " + r.latencyMax;
      tdLat.appendChild(txt);
    } else {
      tdLat.textContent = "N/A";
    }

    const tdJitter = document.createElement("td");
    tdJitter.textContent = r.success ? String(r.jitterMs) : "N/A";

    const tdLoss = document.createElement("td");
    tdLoss.textContent = r.success ? Math.round(r.lossRate * 100) + "%" : "100%";

    const tdStatus = document.createElement("td");
    if (r.success) {
      const grade = r.latencyMs <= 49 ? "极佳" : r.latencyMs <= 99 ? "良好" : r.latencyMs <= 199 ? "一般" : "较慢";
      const badge = document.createElement("span");
      badge.className = "badge " + GRADE_BADGE[grade];
      badge.textContent = grade;
      tdStatus.appendChild(badge);
      if (r.suspect) {
        const warn = document.createElement("span");
        warn.className = "badge badge-suspect";
        warn.textContent = "⚠ 可疑";
        warn.title = r.suspect;
        tdStatus.appendChild(warn);
        const why = document.createElement("div");
        why.className = "err-text";
        why.textContent = r.suspect;
        tdStatus.appendChild(why);
      }
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

    tr.append(tdServer, tdLat, tdJitter, tdLoss, tdStatus);
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

  // 自动选择主/副：只从「极佳（≤49ms）且无可疑标记」中按最快顺序选，避免把被污染/劫持的 DNS 自动应用
  const eligible = results.filter((r) => r.success && !r.suspect && r.latencyMs <= 49);
  if (eligible.length > 0) {
    primarySelect.value = eligible[0].server;
    if (eligible.length > 1) secondarySelect.value = eligible[1].server;
  }

  const ok = results.filter((r) => r.success);
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
    alert("以下条目不是有效的 DNS 服务器（IPv4 或 https DoH URL），已跳过:\n" + invalid.join("\n"));
  }
  if (valid.length === 0) {
    alert("没有找到有效的 DNS 服务器");
    return;
  }
  const { domains, tooMany } = parseDomains();
  if (tooMany) alert("域名超过 10 个，只测试前 10 个");
  if (domains.length === 0) {
    alert("请填写测试域名");
    return;
  }

  setBusy(true);
  const rounds = Number($("rounds-select").value);
  setStatus("正在并行测试 " + valid.length + " 个 DNS 服务器（每服务器 " + rounds + " 轮 × " + domains.length + " 个域名）…", "busy");
  const started = Date.now();

  try {
    results = await DnsTauri.invoke("testDns", { servers: valid, rounds, domains, qtype: $("qtype-select").value });
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

// ---------- 时序监控 ----------

let monitorTimer = null;
let monitorPoints = []; // [{ t, values: {server: ms} }]
const MONITOR_MAX_POINTS = 120;
const MONITOR_COLORS = ["#0d6efd", "#198754", "#fd7e14", "#d63384", "#6f42c1", "#0dcaf0", "#dc3545", "#6610f2"];

function monitorTick() {
  if (busy) return; // 上一轮未完成时跳过，避免请求堆积
  const { valid } = parseInput();
  const { domains } = parseDomains();
  if (valid.length === 0 || domains.length === 0) {
    stopMonitor();
    return;
  }
  DnsTauri.invoke("testDns", { servers: valid, rounds: 1, domains, qtype: $("qtype-select").value })
    .then((res) => {
      const values = {};
      for (const r of res) if (r.success) values[r.server] = r.latencyMs;
      monitorPoints.push({ t: Date.now(), values });
      if (monitorPoints.length > MONITOR_MAX_POINTS) monitorPoints.shift();
      drawMonitorChart();
    })
    .catch(() => {});
}

function startMonitor() {
  if (monitorTimer) return;
  $("btn-monitor").textContent = "监控中";
  $("monitor-modal").classList.remove("hidden");
  monitorPoints = [];
  drawMonitorChart();
  monitorTick();
  monitorTimer = setInterval(monitorTick, Number($("monitor-interval").value));
}

function stopMonitor() {
  if (!monitorTimer) return;
  clearInterval(monitorTimer);
  monitorTimer = null;
  $("btn-monitor").textContent = "监控";
}

function drawMonitorChart() {
  const canvas = $("monitor-chart");
  const ctx = canvas.getContext("2d");
  const W = canvas.width;
  const H = canvas.height;
  const pad = { l: 44, r: 12, t: 24, b: 24 };
  ctx.clearRect(0, 0, W, H);
  if (monitorPoints.length < 2) {
    ctx.fillStyle = "#8a94a6";
    ctx.font = "12px sans-serif";
    ctx.fillText("等待采样数据…", W / 2 - 40, H / 2);
    return;
  }
  const servers = [...new Set(monitorPoints.flatMap((p) => Object.keys(p.values)))];
  let maxMs = 10;
  for (const p of monitorPoints) {
    for (const v of Object.values(p.values)) maxMs = Math.max(maxMs, v);
  }
  maxMs = Math.ceil(maxMs / 50) * 50;
  const pw = W - pad.l - pad.r;
  const ph = H - pad.t - pad.b;
  // 坐标轴与网格
  ctx.strokeStyle = "#d8dee4";
  ctx.fillStyle = "#5c6675";
  ctx.font = "11px sans-serif";
  for (let i = 0; i <= 4; i++) {
    const y = pad.t + ph - (ph * i) / 4;
    ctx.beginPath();
    ctx.moveTo(pad.l, y);
    ctx.lineTo(W - pad.r, y);
    ctx.stroke();
    ctx.fillText(String(Math.round((maxMs * i) / 4)), 8, y + 4);
  }
  // 各服务器折线
  servers.forEach((server, si) => {
    const color = MONITOR_COLORS[si % MONITOR_COLORS.length];
    ctx.strokeStyle = color;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    let started = false;
    monitorPoints.forEach((p, i) => {
      const v = p.values[server];
      if (v == null) return;
      const x = pad.l + (pw * i) / (monitorPoints.length - 1);
      const y = pad.t + ph - (ph * v) / maxMs;
      if (!started) {
        ctx.moveTo(x, y);
        started = true;
      } else {
        ctx.lineTo(x, y);
      }
    });
    ctx.stroke();
    // 图例
    ctx.fillStyle = color;
    const ly = 12 + 13 * (si % 3);
    const lx = 8 + 170 * Math.floor(si / 3);
    ctx.fillRect(lx, ly - 8, 10, 3);
    const label = server.length > 20 ? server.slice(0, 20) + "…" : server;
    ctx.fillText(label, lx + 14, ly - 4);
  });
}

// ---------- 诊断报告 ----------

async function generateReport() {
  setBusy(true);
  setStatus("正在生成诊断报告…", "busy");
  try {
    // 有近期测试结果时一并写入报告（直接透传 testDns 结果，勿手写字段映射：
    // 漏字段会让 Rust 侧反序列化失败，报告生成整体报错）
    const report = await DnsTauri.invoke("diagnose", {
      results: results.length ? results : null,
    });
    const stamp = new Date().toISOString().replace(/[:T]/g, "-").slice(0, 19);
    const path = await DnsTauri.invoke("plugin:dialog|save", {
      options: {
        title: "保存诊断报告",
        defaultPath: `dns-diagnose-${stamp}.txt`,
        filters: [{ name: "文本", extensions: ["txt"] }],
      },
    });
    if (!path) {
      setStatus("已取消保存", "info");
      return;
    }
    await DnsTauri.invoke("exportDns", { path, content: report });
    setStatus("诊断报告已保存: " + path, "ok");
  } catch (e) {
    setStatus("生成报告失败: " + e, "err");
    alert("生成报告失败:\n" + e);
  } finally {
    setBusy(false);
  }
}

// ---------- 查看缓存 ----------

// 查看所选接口当前配置的 DNS 服务器
async function showAdapterDns() {
  const adapter = selectedAdapter();
  if (!adapter) {
    alert("请先选择网络接口");
    return;
  }
  setBusy(true);
  setStatus("正在读取 " + adapter.name + " 的 DNS 配置…", "busy");
  try {
    const ips = await DnsTauri.invoke("getDnsServers", { ifIndex: adapter.ifIndex });
    $("dns-modal-title").textContent = adapter.name + "（ifIndex " + adapter.ifIndex + "）当前 DNS";
    const tbody = document.querySelector("#dns-server-table tbody");
    tbody.innerHTML = "";
    if (!ips.length) {
      const tr = document.createElement("tr");
      const td = document.createElement("td");
      td.textContent = "未配置 DNS（自动获取/DHCP）";
      tr.appendChild(td);
      tbody.appendChild(tr);
    } else {
      ips.forEach((ip, i) => {
        const tr = document.createElement("tr");
        const td = document.createElement("td");
        td.textContent = ip + (i === 0 ? "（首选）" : "（辅助）");
        tr.appendChild(td);
        tbody.appendChild(tr);
      });
    }
    $("dns-modal").classList.remove("hidden");
    setStatus("已读取 " + adapter.name + " 的 " + ips.length + " 个 DNS 服务器", "ok");
  } catch (e) {
    setStatus("读取接口 DNS 失败: " + e, "err");
    alert("读取接口 DNS 失败:\n" + e);
  } finally {
    setBusy(false);
  }
}

async function showCache() {
  setBusy(true);
  setStatus("正在读取 DNS 解析缓存…", "busy");
  try {
    const entries = await DnsTauri.invoke("getDnsCache");
    const tbody = document.querySelector("#cache-table tbody");
    tbody.innerHTML = "";
    if (!entries.length) {
      const tr = document.createElement("tr");
      const td = document.createElement("td");
      td.colSpan = 2;
      td.textContent = "缓存为空";
      tr.appendChild(td);
      tbody.appendChild(tr);
    } else {
      for (const e of entries) {
        const tr = document.createElement("tr");
        const tdName = document.createElement("td");
        tdName.textContent = e.name;
        const tdAddr = document.createElement("td");
        tdAddr.textContent = e.address;
        tr.append(tdName, tdAddr);
        tbody.appendChild(tr);
      }
    }
    $("cache-modal").classList.remove("hidden");
    setStatus("已读取 " + entries.length + " 条缓存记录", "ok");
  } catch (e) {
    setStatus("读取缓存失败: " + e, "err");
    alert("读取缓存失败:\n" + e);
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
  $("btn-cache").addEventListener("click", showCache);
  $("btn-cache-close").addEventListener("click", () => $("cache-modal").classList.add("hidden"));
  $("btn-adapter-dns").addEventListener("click", showAdapterDns);
  $("btn-dns-modal-close").addEventListener("click", () => $("dns-modal").classList.add("hidden"));
  $("btn-report").addEventListener("click", generateReport);
  $("btn-monitor").addEventListener("click", () => (monitorTimer ? stopMonitor() : startMonitor()));
  $("btn-monitor-close").addEventListener("click", () => {
    stopMonitor();
    $("monitor-modal").classList.add("hidden");
  });
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
