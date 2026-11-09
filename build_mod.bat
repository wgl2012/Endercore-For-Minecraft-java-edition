@echo off
chcp 65001 >nul
title EnderCore 模组编译脚本

echo ========================================
echo   EnderCore Mod 一键编译
echo ========================================
echo.

:: 检查 JDK
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Java，请先安装 JDK 17
    echo 下载地址: https://adoptium.net/temurin/releases/?version=17
    pause
    exit /b 1
)

:: 进入 mod 目录
cd /d "%~dp0mod"

echo [1/3] 检测 Gradle...
if not exist "gradlew.bat" (
    echo [!] 未找到 gradlew，正在生成...
    call gradle wrapper --gradle-version 8.5
)

echo.
echo [2/3] 开始编译（第一次会下载依赖，需要几分钟）...
call gradlew.bat build --no-daemon

if %errorlevel% neq 0 (
    echo.
    echo [错误] 编译失败！请检查上面的错误信息
    pause
    exit /b 1
)

echo.
echo [3/3] 编译完成！
echo.
echo 模组文件位置:
echo   %~dp0mod\build\libs\endercore-2.0.0.jar
echo.
echo 使用方法:
echo   把这个 jar 文件复制到 .minecraft\mods\ 文件夹
echo   确保已安装 Fabric Loader 0.15.x 和 Fabric API
echo.
pause
