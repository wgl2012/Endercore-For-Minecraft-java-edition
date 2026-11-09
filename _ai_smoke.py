import time
from mod_bridge import bridge
from ai_controller import ai

def log(m): print("[AI]", m, flush=True)
ai.log_callback = log
if not bridge.test_connection():
    print("NO MOD"); raise SystemExit
bridge.start_polling(0.2)
time.sleep(0.5)
b = bridge.get_player_info()
print("BEFORE x=%.1f z=%.1f hp=%s" % (b.get("x",0), b.get("z",0), b.get("health")))
ai.set_mode("llm")
ai.start()
time.sleep(10)
ai.stop()
a = bridge.get_player_info()
print("AFTER  x=%.1f z=%.1f" % (a.get("x",0), a.get("z",0)))
