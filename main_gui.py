# -*- coding: utf-8 -*-
"""EnderCore v3.0 CLI - auto runs, goal set in-game with O key"""
import time
from mod_bridge import bridge
from ai_controller import ai

def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)

ai.log_callback = log

def main():
    log("EnderCore v3.0")
    log("Connecting to mod...")
    if not bridge.test_connection():
        log("No mod detected. Start MC first.")
        return
    bridge.start_polling(interval=0.5)
    log("Connected!")
    log("In-game: O = set goal, P = free mouse")
    ai.start()
    log("AI running. Type quit to exit.")
    while True:
        try:
            cmd = input("> ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            break
        if cmd == "quit" or cmd == "exit":
            break
        elif cmd == "status":
            d = bridge.snapshot()
            p = d.get("player", {})
            log(f"Player: {p.get('x',0):.1f} {p.get('y',0):.1f} {p.get('z',0):.1f} HP={p.get('health','?')}")
        elif cmd == "stop":
            ai.stop()
        elif cmd == "start":
            ai.start()
    ai.stop()
    log("Stopped.")

if __name__ == "__main__":
    main()