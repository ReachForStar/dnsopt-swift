# DNS延迟测试工具

一款基于 Java Swing 的现代化桌面应用，用于批量测试 DNS 服务器延迟，并在 Windows 系统上一键应用最优或指定 DNS 设置，支持恢复自动获取与刷新本地 DNS 缓存。

## ✨ 功能特性

- 🚀 **批量测试** - 快速测试多个 DNS 服务器延迟，结果按延迟自动排序
- ⚡ **一键应用** - 自动应用测试得到的最优 DNS 到指定网卡
- 🎯 **手动选择** - 支持手动选择并应用任意两条测试成功的 DNS
- 🔄 **快速恢复** - 一键恢复为自动获取 DNS（DHCP）
- 🧹 **缓存清理** - 刷新本地 DNS 缓存
- 📁 **导入导出** - 导入/导出 DNS 列表，加载常用 DNS 模板
- 🎨 **现代界面** - 使用 FlatLaf 现代化 UI，内置中文字体统一显示
- 🔧 **多种打包** - 支持 JAR、EXE、独立应用、原生可执行文件等多种分发方式

## 🖥️ 平台支持

- **Windows 10/11**：✅ 完整支持（测试、应用/恢复 DNS、刷新缓存）
- **其他平台**：⚠️ 主要面向 Windows；如仅运行测试功能，可能需要自行适配系统命令

## 📋 运行环境

### 基本要求
- **操作系统**：Windows 10/11
- **权限要求**：管理员权限（用于应用/恢复/刷新 DNS）
- **Java**：JDK 17 或更高（对于需要 JRE 的版本）

### 开发环境
- **Java**：JDK 17+
- **Maven**：3.6+
- **GraalVM**：22.0+（仅原生可执行文件需要）

## 🔑 管理员权限说明

DNS 设置变更需要管理员权限：

- **EXE 方式**：右键可执行文件 → "以管理员身份运行"
- **JAR 方式**：以管理员身份打开命令提示符，再执行 `java -jar` 命令
- **无管理员权限**：DNS 测试功能正常，但无法应用 DNS 设置

## 🚀 快速开始

### 方式一：使用构建脚本（推荐）

运行项目根目录下的构建脚本：

```batch
build.bat
```

选择构建选项：
- `1` - 快速开发构建（排除 dig 工具，速度最快）
- `2` - 轻量级 EXE（排除 dig 工具，需要 JRE）
- `3` - 完整功能 EXE（包含 dig 工具，需要 JRE）
- `4` - 独立应用（包含 JRE，无需安装 Java）
- `5` - 原生可执行文件（GraalVM，启动最快）
- `6` - 清理构建缓存
- `7` - 测试应用程序

### 方式二：手动构建

#### 1. 快速开发构建
```bash
mvn clean package -Pdev
```
**生成文件**：`target/dns-test-tool.jar`  
**运行命令**：`java -jar target/dns-test-tool.jar`

#### 2. 轻量级 EXE
```bash
mvn clean package -Plightweight-exe
```
**生成文件**：`target/DNS延迟测试工具_轻量版.exe`  
**特点**：体积较小，需要系统安装 Java 17+

#### 3. 完整功能 EXE
```bash
mvn clean package -Pfull,windows-exe
```
**生成文件**：`target/DNS延迟测试工具_launch4j.exe`  
**特点**：包含 dig 工具，需要系统安装 Java 17+

#### 4. 独立应用（推荐分发）
```bash
mvn clean package -Pfull,standalone
```
**生成文件**：
- `target/dist/DNS延迟测试工具/`（完整应用目录）
- `target/dist/DNS延迟测试工具-standalone.zip`（分发压缩包）

**特点**：包含完整 JRE，任何 Windows 电脑都能运行

#### 5. 原生可执行文件
```bash
mvn clean package -Pnative
```
**生成文件**：`target/dns-test-tool.exe`  
**特点**：启动速度最快，无需 Java 环境，但构建需要 GraalVM

## 📦 分发建议

| 分发场景 | 推荐版本 | 优点 | 缺点 |
|---------|---------|------|------|
| **普通用户** | 独立应用 | 无需安装 Java，开箱即用 | 体积较大 |
| **技术用户** | 轻量级 EXE | 体积小，启动快 | 需要 Java 环境 |
| **极致性能** | 原生可执行文件 | 启动最快，无依赖 | 构建复杂 |
| **开发测试** | JAR 文件 | 构建最快，方便调试 | 需要命令行运行 |

## 🛠️ 开发指南

### 项目结构
```
src/main/java/org/example/dns/
├── DnsTestApplication.java    # 主应用入口
├── DnsTestGUI.java           # 主界面
├── DnsServer.java            # DNS 服务器模型
├── DnsTestEngine.java        # 测试引擎
├── NetworkUtils.java         # 网络工具类
├── PingUtils.java            # Ping 工具类
└── FileUtils.java            # 文件工具类
```

### 资源文件
- `src/main/resources/icon.ico` - 应用图标
- `src/main/resources/font/` - 内置字体文件
- `src/main/resources/dig_path/` - dig 工具集合（Windows）

### 构建配置
- **默认 profile**：`dev`（快速开发，排除大文件）
- **完整 profile**：`full`（包含所有资源）
- **打包 profiles**：
  - `lightweight-exe` - Launch4j 轻量级 EXE
  - `windows-exe` - Launch4j 完整 EXE
  - `standalone` - jpackage 独立应用
  - `native` - GraalVM 原生镜像

## 🎯 使用技巧

### 1. DNS 测试优化
- 建议测试 8-15 个常用 DNS 服务器
- 测试前先刷新本地 DNS 缓存以获得准确结果
- 可通过导入功能快速加载预设的 DNS 列表

### 2. 网卡选择
- 程序会自动检测可用网卡
- 选择当前正在使用的网卡进行 DNS 设置
- 有线和无线网卡需要分别设置

### 3. 故障排除
- 如果无法应用 DNS 设置，请确认以管理员权限运行
- 测试失败可能是网络连接问题或 DNS 服务器不可达
- 恢复 DNS 设置后建议重启网络适配器

## 📄 许可证

本项目基于 Apache License 2.0 开源发布。您可以在遵循许可条款的前提下自由使用、修改与分发本软件；分发时需保留原始版权与许可声明。完整条款见仓库根目录的 LICENSE 文件，或访问：
- LICENSE 文件：LICENSE
- 在线版本：http://www.apache.org/licenses/LICENSE-2.0
## 🤝 联系方式
问题与建议：请在本仓库提交 Issue（推荐）。
- QQ：1926885268
- 邮箱：1926885268@qq.com
## 📝 更新日志

### v2.0.0
- ✨ 全新的现代化界面设计
- 🚀 多种打包和分发选项
- ⚡ 优化的构建脚本和流程
- 🎨 内置中文字体支持
- 🔧 完善的 Maven 配置和 profiles
- 📦 支持 GraalVM 原生镜像
- 🛠️ 改进的错误处理和用户反馈
