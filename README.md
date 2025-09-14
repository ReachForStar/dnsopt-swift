# DNS延迟测试工具

一款基于 Java Swing 的桌面应用，用于批量测试 DNS 服务器延迟，并在 Windows 系统上一键应用最优或指定 DNS 设置，支持恢复自动获取与刷新本地 DNS 缓存。

## 功能特性
- 批量测试 DNS 服务器延迟，结果按延迟自动排序
- 一键应用测试得到的最优 DNS 到指定网卡
- 支持手动选择并应用任意两条测试成功的 DNS
- 一键恢复为自动获取 DNS（DHCP）
- 刷新本地 DNS 缓存
- 导入/导出 DNS 列表，加载常用 DNS 模板
- 现代化 UI（FlatLaf），中文界面，内置中文字体以统一显示

## 运行环境
- 操作系统：Windows 10/11（应用/恢复/刷新 DNS 需管理员权限）
- Java：JDK 8 或更高
- Maven：3.6+（用于构建）

## 构建与运行
项目已配置 Maven Shade 插件，默认打包为可运行的 Fat JAR。Windows 下可选生成 EXE。

- 打包 JAR
```bash
mvn -DskipTests package
```
产物位置：`target/dns-test-tool.jar`

- 运行 JAR
```bash
java -jar target/dns-test-tool.jar
```

- 直接以 Maven 运行（可选）
```bash
mvn -q exec:java
```

- 生成 Windows 可执行文件（EXE）
```bash
mvn -P windows-exe -DskipTests package
```
产物位置：`target/DNS延迟测试工具.exe`

## 使用指南
1. 在左侧文本框中输入待测 DNS 地址，每行一条（可点击“加载常用”快速填充）
2. 顶部选择要应用 DNS 的网络接口
3. 点击“开始测试”，等待结果
4. 可选择：
   - “应用最优DNS”：将排序第 1 的 DNS 作为主 DNS，并自动选择次优作为备用
   - “应用选定DNS”：从下拉框手动选择主/辅 DNS
5. 其他：
   - “恢复自动DNS”：将接口恢复为 DHCP 自动获取
   - “刷新DNS缓存”：清空本地 DNS 缓存
   - “导入/导出”：保存或读取 DNS 列表为 txt 文件

提示：应用/恢复/刷新操作需要以管理员身份运行程序。

## 字体与显示
- 全局 UI 字体：`SourceHanSansSC-Regular-2.otf`（内置于 `src/main/resources/font/`）
- 为保持一致性，界面已移除所有表情符号（emoji）

## 依赖
- [dnsjava](https://github.com/dnsjava/dnsjava)（本地 lib 目录提供 jar）
- [SLF4J API](https://www.slf4j.org/)（本地 lib 目录提供 jar）
- [FlatLaf](https://www.formdev.com/flatlaf/)（Maven 依赖）
- [emoji-java](https://github.com/vdurmont/emoji-java)（仅保留解析能力，界面不再显示 emoji）

构建时由 Maven Shade 插件打包为 Fat JAR，无需手动拷贝依赖。

## 目录结构（节选）
```
src/
  main/
    java/org/example/dns/    # 源码
    resources/
      font/                  # 字体（含 SourceHanSansSC-Regular-2.otf）
      dig_path/              # Windows 附带工具与依赖
pom.xml                      # Maven 配置（含 shade、exec、launch4j profile）
```

## 常见问题
- 无管理员权限导致“应用 DNS”或“恢复自动 DNS”失败：
  以管理员身份运行程序或 EXE
- 运行后界面中文显示异常：
  确认 `SourceHanSansSC-Regular-2.otf` 已随资源打包（默认已包含）
- 未发现网络接口或接口列表为空：
  确认网卡已启用且系统网络状态正常

## 许可
本项目未显式声明许可证。如需商用或二次分发，请先与作者确认。

## 致谢
- dnsjava
- FlatLaf
- emoji-java

