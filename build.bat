@echo off
chcp 65001 > nul
echo ==============================================
echo EnderCore v1.0.0 一键打包脚本
echo ==============================================

:: 检查pyinstaller
pyinstaller --version > nul 2>&1
if errorlevel 1 (
    echo 安装pyinstaller...
    pip install pyinstaller -i https://pypi.tuna.tsinghua.edu.cn/simple
)

:: 打包命令
echo 开始打包EXE文件...
pyinstaller -F -w ^
-i logo.ico ^
--name "EnderCore" ^
--add-data "EnderCore.dll;." ^
--add-data "yolov8m.pt;." ^
--add-data "minecraft_yolo_best.pt;." ^
--add-data "minecraft_yolo_best.int8.engine;." ^
--add-data "crafting_recipes.json;." ^
--add-data "yolo_minecraft_config.json;." ^
--clean ^
main_gui.py

:: 检查打包结果
if exist "dist\EnderCore.exe" (
    echo ==============================================
    echo ✅ 打包成功！
    echo 生成文件：dist\EnderCore.exe
    echo 文件大小：%~z1 dist\EnderCore.exe bytes
    echo ==============================================
) else (
    echo ==============================================
    echo ❌ 打包失败！
    echo ==============================================
)

echo 请按任意键退出...
pause > nul
