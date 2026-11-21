# EnderCore

> 控制端 **beta.V4.0** · 模组 **3.2.1** · Minecraft 1.20.4 / Fabric

Minecraft Java 1.20.4 Fabric 模组 — 通过 HTTP 外部控制游戏，由 LLM（大语言模型）作为大脑驱动角色自主完成建造、交互、交易、战斗等复杂任务。

## 功能特性

- **LLM 大脑**：输入自然语言目标（如"在原地搭一个 6×6×6 立方体"），LLM 自动拆解为元指令序列执行
- **游戏内悬浮窗**：左上角常驻显示 AI 状态、当前动作和账户剩余 Token；深度思考文本输出到聊天框
- **快捷键**：
  - `O` — 在游戏内弹出目标输入框，直接设定 LLM 任务
  - `N` — 切换后台托管模式：直接释放鼠标光标（不挂界面，不挡 AI 移动）并强制游戏不暂停，可切窗挂后台让 AI 自主运行；再按一次退出托管并锁回鼠标
  - `K` — 打开模组配置界面（接口地址 / API Key / 模型 / 决策间隔）
- **40+ 元指令**：移动、视角、物品、方块、容器、生物、交易、合成、战斗等
- **分层决策骨架**（类自动驾驶，渐进落地）：
  - 感知层 `perception.py`：威胁/可交互实体/界面槽位归一化，保留短时位置历史
  - 预测层 `prediction.py`：实体轨迹外推、接近/逃离/游荡意图、接触时间 ETA
  - 行为层 `behavior.py`：本地状态机输出 FIGHT / AVOID / FOLLOW / INTERACT / NAVIGATE / STUCK 意图
  - 寻路层 `pathfinding.py`：局部栅格 A* 绕障，输出步点与 walk / jump / pillar / bridge 原语
  - 闭环层 `watchdog.py`：卡死自愈、导航超时重规划、掉落自救、到达停车
  - 任务层 `llm_brain.py`：LLM 只负责粗计划与目标拆解，不进实时控制环；本地安全兜底优先于 LLM 动作
  - 待调：寻路阈值与栅格范围、watchdog 卡死/超时参数按真实场景日志调优
- **已验证流程**：
  - 追踪生物（实时跟随移动中的实体）
  - 村民/流浪商人完整交易流程
  - 工作台合成（木板 → 床 → 放置 → 睡觉）
  - 放置/挖掘方块、背包操作

## 技术栈

- Minecraft 1.20.4 / Fabric Loader 0.15.11 / Fabric API 0.97.2
- Gradle 8.8 / JDK 17
- Python 3.12（LLM 控制端）
- WebSocket 长连接（端口 25600，主传输），HTTP REST API（端口 25599，兜底/健康检查）

## 快速开始

### 1. 安装模组

将 `endercore-3.2.1.jar` 放入 `.minecraft/mods/` 文件夹（需要已安装 Fabric API）。

### 2. 安装 Python 依赖

```bash
pip install -r requirements.txt
```

LLM 接入参数无需在 Python 端配置文件里填写。进入世界后按 **K** 键（或命令 `/endercore gui`）打开模组配置界面，填写：

- **LLM 接口地址**（Base URL）
- **LLM API Key**（仅保存在本机 `endercore/config.json`，不打包进模组、不随仓库分发）
- **LLM 模型名**
- **决策间隔（秒）**

保存后由模组通过 WebSocket 状态帧下发给 Python 控制端。

### 3. 启动

1. 启动 Minecraft 并进入世界
2. 运行 Python 控制端：

```bash
python main_gui.py
```

3. 在游戏内按 `O` 键输入目标，AI 自动开始执行

### 4. 后台托管（让 AI 无人值守运行）

进入世界、AI 开始执行后，按 `N` 进入后台托管模式：

- 鼠标光标直接释放（不打开任何界面，因此不会清零 AI 的移动输入）；
- 强制关闭原版「失焦自动暂停」(`pauseOnLostFocus`)，切到其他窗口超过 500ms 也不会弹出暂停菜单；
- Mixin 强制 `isPaused()=false`，托管期间世界与客户端 tick 不会被暂停界面卡住；
- 悬浮窗 `Host` 行显示「后台托管中」，聊天框会提示进入/退出；再按 `N` 退出并锁回鼠标。

注意：**不要把窗口最小化到任务栏**（Windows/显卡可能挂起主线程导致动作堆积）。需要离开时用 Alt-Tab 切走，或把窗口拖到屏幕边缘外 / 第二块屏幕，保持窗口未最小化。

## HTTP API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/ping` | GET | 健康检查 |
| `/api/state` | GET | 获取玩家位置、血量、背包、附近生物、目标 |
| `/api/blocks` | GET | 获取附近方块数据 |
| `/api/action` | POST | 执行元指令 |
| `/api/hud` | POST | 推送动作/状态/剩余额度到悬浮窗（thought 转聊天框） |
| `/api/chat` | POST | 推送文本到游戏内聊天框 |

## 元指令列表

### 移动
- `go_to_block` — 移动到指定坐标
- `go_to_entity` — 追踪指定 ID 的生物（实时跟随）
- `walk` / `jump` / `sprint` / `sneak` / `stop_move`

### 视角
- `look_at` / `turn_left` / `turn_right` / `look_up` / `look_down`

### 物品
- `select_slot` — 选择快捷栏槽位
- `swap_hands` — 交换主副手
- `drop_item` — 丢弃物品

### 方块
- `place_block_at` — 在指定位置放置方块
- `mine_block` — 挖掘方块

### 容器/合成
- `click_slot` — 点击容器界面槽位
- `shift_click_slot` — Shift 点击移动物品
- `close_container` — 关闭界面

### 生物/交易
- `interact_entity` — 右键交互实体（打开交易界面）
- `attack_entity` — 攻击实体
- `select_trade` — 选择交易项

## 项目结构

```
EnderCore/
├── mod/                          # Fabric 模组源码
│   └── src/main/java/com/endercore/
│       ├── EnderCoreMod.java      # 主入口、快捷键注册
│       ├── ModApiServer.java      # HTTP 兜底服务器
│       ├── ModWsServer.java        # WebSocket 服务端（主传输）
│       ├── Config.java             # 本地配置读写（密钥不打包）
│       ├── ConfigScreen.java      # K 键游戏内配置界面
│       ├── GameDataCollector.java # 游戏状态采集
│       ├── PlayerController.java  # 玩家动作执行
│       ├── ActionRegistry.java   # 元指令注册
│       ├── AiHud.java            # 游戏内悬浮窗
│       ├── GoalScreen.java       # O 键目标输入界面
│       ├── HostMode.java         # N 键后台托管状态（释放光标 + 保活标记）
│       └── mixin/
│           └── MinecraftClientMixin.java # 托管期间强制不暂停
├── ai_controller.py              # AI 控制主循环（串联感知/预测/行为/寻路/闭环/LLM/执行）
├── perception.py                 # 感知层：状态归一化 + 短时历史（威胁/交互实体/界面）
├── prediction.py                  # 预测层：实体轨迹外推 + 意图/接近时间 ETA
├── behavior.py                   # 行为状态机：FIGHT/AVOID/FOLLOW/INTERACT/NAVIGATE/STUCK
├── pathfinding.py                 # 寻路层：局部 A* 绕障步点 + 搭桥/搭柱原语
├── watchdog.py                    # 闭环层：卡死自愈/超时重规划/掉落自救/到达停车
├── llm_brain.py                  # LLM 任务层调用与提示词
├── mod_bridge.py                 # WebSocket / HTTP 通信桥
├── main_gui.py                   # Python 命令行入口（纯日志）
└── requirements.txt              # Python 依赖
```

## 从源码构建

```bash
cd mod
./gradlew build
# 产物在 mod/build/libs/endercore-3.2.1.jar
```

## 许可证

[MIT](LICENSE)