@echo off
chcp 65001
echo ==========================================
echo DNS延迟测试工具 - 快速构建脚本
echo ==========================================
echo.

echo 可用的构建选项：
echo 1. 快速开发构建 (排除dig工具，速度最快)
echo 2. 轻量级EXE (排除dig工具，需要JRE)
echo 3. 完整功能EXE (包含dig工具，需要JRE)
echo 4. 独立应用 (包含JRE，无需安装Java - 已修复路径循环问题)
echo 5. 原生可执行文件 (GraalVM)
echo 6. 清理构建缓存
echo 7. 测试应用程序
echo.

set /p choice=请选择构建选项 (1-7):

if "%choice%"=="1" (
    echo 执行快速开发构建...
    mvn clean package -Pdev
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo ✅ 构建成功！
        echo 生成文件: target\dns-test-tool.jar
        echo 运行命令: java -jar target\dns-test-tool.jar
    ) else (
        echo ❌ 构建失败！
    )
) else if "%choice%"=="2" (
    echo 执行轻量级EXE构建...
    mvn clean package -Plightweight-exe
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo ✅ 构建成功！
        echo 生成文件: target\DNS延迟测试工具_轻量版.exe
        echo 注意: 需要安装Java 17或更高版本
    ) else (
        echo ❌ 构建失败！
    )
) else if "%choice%"=="3" (
    echo 执行完整功能EXE构建...
    mvn clean package -Pfull,windows-exe
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo ✅ 构建成功！
        echo 生成文件: target\DNS延迟测试工具_launch4j.exe
        echo 注意: 需要安装Java 17或更高版本，包含dig工具
    ) else (
        echo ❌ 构建失败！
    )
) else if "%choice%"=="4" (
    echo 执行独立应用构建（已修复路径循环问题）...
    mvn clean package -Pfull,standalone
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo ✅ 构建成功！
        echo 应用程序位置: target\dist\DNS延迟测试工具\
        echo 压缩包位置: target\dist\DNS延迟测试工具-standalone.zip
        echo 注意: 包含完整JRE，可独立运行，无需安装Java
        echo.
        echo 📁 正在打开应用程序目录...
        start "" "target\dist\DNS延迟测试工具"
    ) else (
        echo ❌ 构建失败！
    )
) else if "%choice%"=="5" (
    echo 执行原生可执行文件构建...
    echo 注意: 需要安装GraalVM
    mvn clean package -Pnative
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo ✅ 构建成功！
        echo 生成文件: target\DNS延迟测试工具.exe
        echo 注意: 原生可执行文件，启动速度最快
    ) else (
        echo ❌ 构建失败！
    )
) else if "%choice%"=="6" (
    echo 清理构建缓存...
    mvn clean
    if exist target rmdir /s /q target 2>nul
    echo ✅ 缓存已清理
) else if "%choice%"=="7" (
    echo 测试应用程序...
    if exist "target\dns-test-tool.jar" (
        echo 运行JAR版本...
        java -jar target\dns-test-tool.jar
    ) else if exist "target\DNS延迟测试工具_轻量版.exe" (
        echo 运行轻量版EXE...
        start "" "target\DNS延迟测试工具_轻量版.exe"
    ) else if exist "target\DNS延迟测试工具_launch4j.exe" (
        echo 运行完整版EXE...
        start "" "target\DNS延迟测试工具_launch4j.exe"
    ) else if exist "target\dist\DNS延迟测试工具\DNS延迟测试工具.exe" (
        echo 运行独立应用...
        start "" "target\dist\DNS延迟测试工具\DNS延迟测试工具.exe"
    ) else (
        echo ❌ 未找到可执行文件，请先执行构建
    )
) else (
    echo ❌ 无效选项，请重新运行脚本
)

echo.
echo 构建完成！按任意键退出...
pause >nul
