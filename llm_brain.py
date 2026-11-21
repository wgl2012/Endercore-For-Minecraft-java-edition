# -*- coding: utf-8 -*-
"""
LLM 大脑：把游戏状态发给大模型，让它决策下一个动作。
支持目标驱动：用户可以通过 set_goal 告诉 LLM 要做什么。

v3.1 改动：维护客户端侧持久对话历史（system + 最近 N 轮 user/assistant），
每轮把上一步动作结果回喂，避免相邻决策互相矛盾；目标切换时可清空历史。
"""
import json
import time
import requests

from mod_bridge import bridge

# 默认配置；实际值由 mod 端通过状态帧下发，refresh_config 时覆盖
_cfg = {
    "llm_base_url": "https://api.ginka.cloud/v1",
    "llm_api_key": "",
    "llm_model": "deepseek-v4-flash",
    "llm_decision_interval_sec": 2.5,
}


def refresh_config():
    """从 mod 状态快照读取 LLM 配置（配置统一在 mod 端维护）。"""
    try:
        cfg = bridge.snapshot().get("config")
        if isinstance(cfg, dict):
            for k in _cfg:
                if k in cfg and cfg[k] is not None:
                    _cfg[k] = cfg[k]
    except Exception:
        pass


def _api_base() -> str:
    """归一化 OpenAI 兼容地址：用户只填域名时自动补 /v1。"""
    base = (_cfg["llm_base_url"] or "").rstrip("/")
    if base and not base.endswith("/v1"):
        base = base + "/v1"
    return base


def fetch_balance(force: bool = False):
    """查询账户剩余额度（new-api / one-api 风格 /api/user/self）。失败返回 None。"""
    refresh_config()
    if not force and time.time() - _balance_cache["ts"] < _BALANCE_TTL:
        return _balance_cache["value"]

    value = None
    key = _cfg["llm_api_key"]
    base = _cfg["llm_base_url"].rstrip("/")
    root = base[:-3] if base.endswith("/v1") else base
    if key:
        try:
            r = requests.get(
                root + "/api/user/self",
                headers={"Authorization": f"Bearer {key}"},
                timeout=8,
            )
            if r.ok:
                data = r.json()
                node = data.get("data") if isinstance(data.get("data"), dict) else data
                if isinstance(node, dict):
                    for k in ("quota", "remain_quota", "remainBalance", "balance"):
                        if node.get(k) is not None:
                            value = node[k]
                            break
        except Exception:
            value = None

    _balance_cache["value"] = value
    _balance_cache["ts"] = time.time()
    return value

# 会话历史（不含 system，system 每次请求单独放最前）；不设轮数/长度上限
_history = []

# 账户剩余额度缓存（秒），避免每轮决策都请求余额接口
_balance_cache = {"value": None, "ts": 0.0}
_BALANCE_TTL = 60.0

# 最近一次决策失败原因（供控制器打印诊断，成功时清空）
last_error = None

SYSTEM_PROMPT = """你是《我的世界》Java版 1.20.4 的生存 AI 大脑。你会收到游戏状态 JSON（玩家坐标/血量/食物/背包/附近生物/可交互方块/昼夜），以及用户给的【目标】。

工作方式：
1. 先想清楚：当前位置离目标还有多远？有什么障碍？
2. 输出一个 JSON：{"thought":"你为什么这么做","action":{...}}
3. action 必须是下面列表中的一个。每次只输出一个动作。
4. 你能看到最近若干轮对话记录（上一步做了什么、成功还是失败）。请沿用上一步的思路继续推进，保持计划一致，不要无故反复更改方向或后退。

=== 移动类 ===
{"action":"move_to","params":{"x":数字,"z":数字}} 精确走到坐标（0.2格精度，用于重要位置）
{"action":"move_rough","params":{"x":数字,"z":数字}} 粗略疾跑（远距离赶路，1格精度）
{"action":"stop_move"} 立即停止移动
{"action":"go_to_entity","params":{"id":数字}} 自动追踪指定生物（持续走到它身边，适合追商人/村民/动物）
{"action":"jump"} 跳一下
{"action":"sneak","params":{"on":true/false}} 潜行
{"action":"sprint","params":{"on":true/false}} 疾跑开关
{"action":"fly","params":{"on":true/false}} 飞行开关（需要创造模式）
{"action":"forward","params":{"ticks":数字}} 向前走N tick（约0.5秒/2tick）
{"action":"back","params":{"ticks":数字}} 向后走
{"action":"left","params":{"ticks":数字}} 向左走
{"action":"right","params":{"ticks":数字}} 向右走
{"action":"bridge_forward"} 向前搭桥（过峡谷时连续用，每次放一块）
{"action":"pillar_up","params":{"target_y":数字}} 原地搭柱向上爬，到目标高度自动停
{"action":"swim_up"} 在水里上浮自救

=== 视角类 ===
{"action":"look_yaw_pitch","params":{"yaw":数字,"pitch":数字}} 设置朝向（yaw: 0=南,90=西,180=北,270=东; pitch: -90=上,90=下）
{"action":"look_at","params":{"x":数字,"y":数字,"z":数字}} 看向某坐标
{"action":"look_delta","params":{"yaw":数字,"pitch":数字}} 相对转视角
{"action":"camera_perspective","params":{"mode":"first_person/third_person/third_person_back"}} 切换视角

=== 物品类 ===
{"action":"hotbar","params":{"slot":数字}} 切换手持物品（0-8）
{"action":"swap_hands"} 交换主副手物品
{"action":"use_offhand"} 右键使用副手物品
{"action":"use_item"} 右键使用/放置主手物品
{"action":"drop_item"} 扔掉当前手持物品
{"action":"drop_stack","params":{"slot":数字}} 扔掉指定快捷栏整组

=== 方块交互类 ===
{"action":"place_block_at","params":{"x":数字,"y":数字,"z":数字}} 在指定坐标放方块
{"action":"break_block","params":{"x":数字,"y":数字,"z":数字}} 挖掉附近方块
{"action":"right_click_block","params":{"x":数字,"y":数字,"z":数字}} 右键方块（打开工作台/熔炉/门/床等）
{"action":"start_breaking","params":{"x":数字,"y":数字,"z":数字}} 开始持续挖
{"action":"stop_breaking"} 停止挖

=== 容器/界面操作类 ===
{"action":"open_block","params":{"x":数字,"y":数字,"z":数字}} 打开功能方块界面（工作台/箱子/熔炉等）
{"action":"click_slot","params":{"slot":数字}} 点击界面槽位（拿起/放下物品）
{"action":"shift_click_slot","params":{"slot":数字}} Shift+点击槽位（快速移动物品到另一背包）
{"action":"click_slot_hotbar","params":{"slot":数字,"hotbar":数字}} 把快捷栏hotbar(0-8)的物品放到界面slot
{"action":"drop_cursor"} 扔出光标上的物品
{"action":"close_screen"} 关闭当前界面

=== 生物交互类 ===
{"action":"interact_entity","params":{"id":数字}} 右键交互实体（打开村民/流浪商人交易界面）
{"action":"interact_entity_at","params":{"id":数字}} 右键交互实体（带位置参数版本）
{"action":"ride_entity","params":{"id":数字}} 骑上实体（马/猪/矿车/船）
{"action":"dismount"} 下坐骑

=== 交易类 ===
{"action":"select_trade","params":{"index":数字}} 选择交易列表第N项（从0开始）

=== 战斗类 ===
{"action":"attack","params":{"ticks":数字}} 攻击（挥手打）
{"action":"attack_entity","params":{"id":数字}} 攻击指定实体
{"action":"attack_nearest"} 攻击最近的敌对生物

=== 其他 ===
{"action":"chat","params":{"message":"文字"}} 发送聊天消息
{"action":"set_gamemode","params":{"mode":"survival/creative"}} 切换游戏模式
{"action":"time_set","params":{"value":"day/night"}} 设置时间
{"action":"weather","params":{"value":"clear/rain/thunder"}} 设置天气
{"action":"idle"} 原地不动

=== 关键规则 ===
1. **交互距离限制**：你只能操作距离玩家 4.5 格以内的方块和实体。远距离时必须先 move_to/go_to_entity 走近。
2. **视线遮挡（重要）**：附近生物字段里 `visible=false` 且带 `blocker`（遮挡方块 id）时，说明距离虽近但中间隔着墙/门/栅栏/玻璃，直接 interact_entity 会失败。此时不要反复交互：先绕路走到实体所在的一侧；若是实心墙，需寻路绕行或挖通。`visible=true` 才可以直接交互。通行障碍区分：方块 `kind=door/fence_gate/trapdoor` 是可开合门/栅栏门/活板门，先 right_click_block（或 open_block）开门再穿过；`kind=fence/bars`（栅栏/铁栏杆）不能开合，必须绕行，不要硬穿。
3. **村民可交易性（重要）**：村民记录带 `profession`（职业）、`baby`、`tradable` 与 `nbt`。只有 `tradable=true`（已转职、成年）的村民右键才会打开交易界面；`tradable=false`（职业为 none、幼年村民）右键不会有反应，不要反复对它 interact_entity，应换一个已转职的成年村民。
4. **交易流程**：go_to_entity 追踪村民→interact_entity 打开交易界面→select_trade 选交易项→click_slot_hotbar 把绿宝石放入输入槽(slot 0)→shift_click_slot slot 2 取结果商品→close_screen。
5. **合成流程**：open_block 打开工作台→click_slot_hotbar 把材料放入合成格→shift_click_slot slot 0 取合成结果→close_screen。
6. **搭建时**：每次只放一个方块，放够得着的，再 move_to 走到下一个位置继续。不要站在远处硬放。
7. 有目标时不要 idle！必须执行一个动作推进目标。
8. 界面槽位编号：工作台 slot 0=结果, 1-9=合成格, 37-45=快捷栏; 商人 slot 0=输入1, 1=输入2, 2=结果, 30-38=快捷栏。

输出格式示例：
{"thought":"需要追踪村民打开交易","action":{"action":"go_to_entity","params":{"id":17}}}
只输出这一个 JSON，不要解释，不要 markdown。"""


def reset_history():
    """清空会话历史（目标切换、重启 AI 时调用）。"""
    _history.clear()


def decide(state: dict) -> dict:
    """根据游戏状态，返回动作字典。失败时抛异常后返回 idle。"""
    refresh_config()
    user_content = json.dumps(state, ensure_ascii=False)
    _history.append({"role": "user", "content": user_content})
    user_idx = len(_history) - 1

    payload = {
        "model": _cfg["llm_model"],
        "messages": [{"role": "system", "content": SYSTEM_PROMPT}] + list(_history),
        "temperature": 0.3,
        "max_tokens": 5000,
    }

    last_err = None
    last_error = None
    for attempt in range(3):
        try:
            r = requests.post(
                _api_base() + "/chat/completions",
                headers={"Authorization": f"Bearer {_cfg['llm_api_key']}"},
                json=payload,
                timeout=20,
            )
            r.raise_for_status()
            ctype = r.headers.get("content-type") or ""
            if "application/json" not in ctype:
                raise RuntimeError(f"接口返回非 JSON（HTTP {r.status_code}，{ctype}）: {r.text[:100]!r}")
            resp = r.json()
            content = (resp["choices"][0]["message"]["content"] or "").strip()
            usage = resp.get("usage")
        except Exception as e:
            last_err = e
            time.sleep(1)
            continue

        if not content:
            last_err = ValueError("空响应")
            time.sleep(1)
            continue

        if content.startswith("```"):
            content = content.strip("`")
            if content.lower().startswith("json"):
                content = content[4:]
        i = content.find("{")
        j = content.rfind("}")
        if i < 0 or j < 0:
            last_err = ValueError(f"模型返回不是 JSON: {content[:100]}")
            time.sleep(1)
            continue
        try:
            parsed = json.loads(content[i:j + 1])
        except json.JSONDecodeError as e:
            last_err = e
            time.sleep(1)
            continue

        # 成功：把本轮助手回复记入历史，保持上下文连续
        _history.append({"role": "assistant", "content": json.dumps(parsed, ensure_ascii=False)})
        parsed["usage"] = usage
        last_error = None
        return parsed

    # 全部失败：撤掉刚追加的 user 消息，避免上下文错位，返回 idle
    if len(_history) > user_idx:
        del _history[user_idx:]
    last_error = last_err
    return {"action": "idle", "fallback": True,
            "reason": str(last_err)[:120] if last_err else "unknown"}
