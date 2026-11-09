@echo off
chcp 65001 > nul
echo ==============================================
echo EnderCore v1.0.0 依赖一键安装脚本
echo ==============================================
echo 系统环境检查...
python --version > nul 2>&1
if errorlevel 1 (
    echo 错误：未找到Python，请先安装Python 3.10.11并添加到PATH
    pause
    exit /b 1
)

echo 正在升级pip...
pip install --upgrade pip -i https://pypi.tuna.tsinghua.edu.cn/simple

echo 正在安装核心依赖包...
pip install -i https://pypi.tuna.tsinghua.edu.cn/simple ^
pytesseract ^
mss ^
pillow ^
psutil ^
keyboard ^
mouse ^
numpy ^
opencv-python ^
ultralytics ^
tkintertable ^
pyinstaller ^
torch ^
torchvision ^
torchaudio ^
stable-baselines3[extra] ^
gymnasium ^
easyocr ^
paddlepaddle ^
paddleocr

echo ==============================================
echo 依赖安装完成！
echo 请按任意键退出...
echo ==============================================
pause > nul
