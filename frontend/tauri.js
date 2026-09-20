// Tauri v2 内部 API 的极简封装（无需 npm 构建）
// __TAURI_INTERNALS__ 由 Tauri 运行时注入；非 Tauri 环境（如直接用浏览器打开）下降级提示
(function () {
  "use strict";

  const internals = window.__TAURI_INTERNALS__;

  if (!internals) {
    window.DnsTauri = {
      available: false,
      invoke: async () => {
        throw new Error("当前页面未在 Tauri 应用中运行，请通过构建后的可执行文件启动");
      },
    };
    return;
  }

  window.DnsTauri = {
    available: true,
    invoke(cmd, args) {
      return internals.invoke(cmd, args || {});
    },
  };
})();
