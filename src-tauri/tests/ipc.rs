// IPC 层端到端测试：走 Tauri mock runtime 的真实 invoke 往返，
// 验证命令名（camelCase）与参数名契约 + 真实网络/系统调用。
// 必须作为集成测试（tests/ 目标）：build.rs 只为测试目标补嵌 comctl32 v6 清单，
// 库内单元测试的二进制拿不到清单，链接 GUI 栈后会加载失败（0xc0000139）。

use dns_test_tool::{dns, net, test_app};
use tauri::test::{get_ipc_response, INVOKE_KEY};
use tauri::webview::InvokeRequest;
use tauri::WebviewWindowBuilder;

type MockApp = tauri::App<tauri::test::MockRuntime>;
type MockWebview = tauri::WebviewWindow<tauri::test::MockRuntime>;

// mock 应用的 webview 标签全局唯一，每个测试建一次后复用
fn make_webview(app: &MockApp) -> MockWebview {
    WebviewWindowBuilder::new(app, "main", Default::default())
        .build()
        .expect("创建 mock webview 失败")
}

fn invoke(
    webview: &MockWebview,
    cmd: &str,
    body: serde_json::Value,
) -> Result<tauri::ipc::InvokeResponseBody, serde_json::Value> {
    get_ipc_response(
        webview,
        InvokeRequest {
            cmd: cmd.to_string(),
            callback: tauri::ipc::CallbackFn(0),
            error: tauri::ipc::CallbackFn(1),
            url: "http://tauri.localhost".parse().unwrap(),
            body: tauri::ipc::InvokeBody::Json(body),
            headers: Default::default(),
            invoke_key: INVOKE_KEY.to_string(),
        },
    )
}

/// 回归测试：命令名必须按 camelCase 注册（GUI 曾报 "Command list_adapters not found"）
#[test]
fn command_names_are_camelcase() {
    let app = test_app();
    let webview = make_webview(&app);

    // camelCase 可解析且真实返回接口列表
    let body = invoke(&webview, "listAdapters", serde_json::json!({})).expect("listAdapters 应可解析");
    let adapters: Vec<net::Adapter> = body.deserialize().expect("反序列化适配器列表失败");
    assert!(!adapters.is_empty(), "本机应至少有一个网络接口");
    assert!(adapters.iter().any(|a| a.is_connected()));

    // snake_case 不可解析，且错误信息能定位到命令名（错误体是纯字符串）
    let err = invoke(&webview, "list_adapters", serde_json::json!({})).expect_err("snake_case 不应被注册");
    let msg: String = serde_json::from_value(err).unwrap_or_default();
    assert!(
        msg.contains("list_adapters") && msg.contains("not found"),
        "实际错误: {msg}"
    );
}

/// testDns：参数名 servers + 真实网络结果校验
#[test]
fn test_dns_ipc_contract_and_real_network() {
    let app = test_app();
    let webview = make_webview(&app);

    let body = invoke(
        &webview,
        "testDns",
        serde_json::json!({ "servers": ["223.5.5.5", "8.8.8.8", "999.1.1.1", "192.0.2.1"] }),
    )
    .expect("testDns 应可解析");
    let results: Vec<dns::TestResult> = body.deserialize().expect("反序列化测试结果失败");
    assert_eq!(results.len(), 4);

    let invalid = results.iter().find(|r| r.server == "999.1.1.1").unwrap();
    assert!(!invalid.success && invalid.latency_ms == 0);
    let unreachable = results.iter().find(|r| r.server == "192.0.2.1").unwrap();
    // 192.0.2.1 是否可达取决于网络环境（公司内网/CI 云网络对 TEST-NET 的处置不同），
    // 只断言结果形态自洽：成功必有延迟，失败必有错误信息
    if unreachable.success {
        assert!(unreachable.latency_ms > 0);
    } else {
        assert!(unreachable.error.is_some());
    }
    let aliyun = results.iter().find(|r| r.server == "223.5.5.5").unwrap();
    assert!(aliyun.success, "223.5.5.5 应可达: {:?}", aliyun.error);

    // 成功项在前、延迟升序
    let success: Vec<u64> = results.iter().filter(|r| r.success).map(|r| r.latency_ms).collect();
    let mut sorted = success.clone();
    sorted.sort();
    assert_eq!(success, sorted);

    // 参数名错误（server 单数）应报参数错误而非静默成功
    let err = invoke(&webview, "testDns", serde_json::json!({ "server": ["223.5.5.5"] }))
        .expect_err("错误参数名应失败");
    let _ = err.to_string();

    // rounds 参数：1 轮时单次采样，min/avg/max 必然相等
    let body = invoke(
        &webview,
        "testDns",
        serde_json::json!({ "servers": ["223.5.5.5"], "rounds": 1 }),
    )
    .expect("testDns 带 rounds 应可解析");
    let one: Vec<dns::TestResult> = body.deserialize().unwrap();
    let r = one.iter().find(|x| x.server == "223.5.5.5").unwrap();
    assert!(r.success);
    assert_eq!(r.latency_min, r.latency_ms);
    assert_eq!(r.latency_ms, r.latency_max);
}

/// getDnsCache：真实系统调用，返回结构化的缓存条目
#[test]
fn get_dns_cache_command() {
    let app = test_app();
    let webview = make_webview(&app);
    let body = invoke(&webview, "getDnsCache", serde_json::json!({})).expect("getDnsCache 应成功");
    let entries: Vec<net::CacheEntry> = body.deserialize().unwrap();
    // 开发机缓存通常非空；为空时（刚重启/刚 flush）只验证结构可用
    for e in &entries {
        assert!(!e.name.trim().is_empty());
        assert!(!e.address.trim().is_empty());
    }
}

/// applyDns/resetDns：参数名契约（不触发 UAC，缺参应在派发层报错）
#[test]
fn dns_commands_argument_contract() {
    let app = test_app();
    let webview = make_webview(&app);
    let err = invoke(&webview, "applyDns", serde_json::json!({})).expect_err("缺 ifIndex 应报错");
    let s = err.to_string();
    assert!(s.contains("ifIndex") || s.contains("if_index"), "实际错误: {s}");

    let err = invoke(&webview, "resetDns", serde_json::json!({})).expect_err("缺 ifIndex 应报错");
    let s = err.to_string();
    assert!(s.contains("ifIndex") || s.contains("if_index"), "实际错误: {s}");
}

/// flushCache / importDns / exportDns：真实系统调用与文件往返
#[test]
fn flush_and_file_commands() {
    let app = test_app();
    let webview = make_webview(&app);

    let body = invoke(&webview, "flushCache", serde_json::json!({})).expect("flushCache 应成功");
    let msg: String = body.deserialize().unwrap();
    assert!(msg.contains("刷新"));

    let dir = std::env::temp_dir().join("dns-tool-ipc-test");
    std::fs::create_dir_all(&dir).unwrap();
    let file = dir.join("dns_list.txt");
    std::fs::write(&file, "223.5.5.5\n8.8.8.8\n").unwrap();
    let path = file.to_string_lossy().to_string();

    let body = invoke(&webview, "importDns", serde_json::json!({ "path": path })).expect("importDns 应成功");
    let content: String = body.deserialize().unwrap();
    assert_eq!(content.trim(), "223.5.5.5\n8.8.8.8");

    let body = invoke(&webview, "exportDns", serde_json::json!({ "path": path, "content": "1.1.1.1\n" }))
        .expect("exportDns 应成功");
    let msg: String = body.deserialize().unwrap();
    assert!(msg.contains("已导出"));
    assert_eq!(std::fs::read_to_string(&file).unwrap(), "1.1.1.1\n");

    let _ = std::fs::remove_dir_all(&dir); // 用完即删
}
