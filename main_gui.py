# -*- coding: utf-8 -*-
"""EnderCore 控制端 - 纯命令行日志输出。
配置由 mod 端下发，目标在游戏内按 O 键输入，本程序不提供任何交互命令。
"""
APP_VERSION = "beta.V4.0"
import time
from mod_bridge import bridge
from ai_controller import ai


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


ai.log_callback = log


def main():
    log(f"EnderCore 控制端 {APP_VERSION} 启动")
    log("正在连接模组...")
    if not bridge.test_connection():
        log("未检测到模组，请先启动 Minecraft")
        return

    bridge.start_polling(interval=0.5)
    log("已连接，AI 自动运行")
    log("游戏内：O=输入目标，N=后台托管（释放鼠标+不暂停）；Ctrl+C 退出")

    ai.start()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        ai.stop()
        bridge.stop_polling()
        log("已停止")


if __name__ == "__main__":
    main()