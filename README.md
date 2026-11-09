# EnderCore

Minecraft Java 1.20.4 Fabric 模组 — 通过 HTTP 外部控制游戏，由 LLM（大语言模型）作为大脑驱动角色自主完成建造、交互、交易、战斗等复杂任务。

## 功能特性

- **LLM 大脑**：输入自然语言目标（如"在原地搭一个 6×6×6 立方体"），LLM 自动拆解为元指令序列执行
- **游戏内悬浮窗**：左上角常驻显示 AI 思考过程、当前动作、状态和目标
- **快捷键**：
  - `O` — 在游戏内弹出目标输入框，直接设定 LLM 任务
  - `P` — 释放/锁定鼠标光标（操作 UI 时不转视角）
- **40+ 元指令**：移动、视角、物品、方块、容器、生物、交易、合成、战斗等
- **已验证流程**：
  - 追踪生物（实时跟随移动中的实体）
  - 村民/流浪商人完整交易流程
  - 工作台合成（木板 → 床 → 放置 → 睡觉）
  - 放置/挖掘方块、背包操作

## 技术栈

- Minecraft 1.20.4 / Fabric Loader 0.15.11 / Fabric API 0.97.2
- Gradle 8.8 / JDK 17
- Python 3.12（LLM 控制端）
- HTTP REST API（端口 25599）

## 快速开始

### 1. 安装模组

将 `endercore-2.0.0.jar` 放入 `.minecraft/mods/` 文件夹（需要已安装 Fabric API）。

### 2. 配置 Python 端

```bash
pip install -r requirements.txt
```

编辑 `config.json`：

```json
{
  "llm_base_url": "https://api.ginka.cloud/v1",
  "llm_api_key": "你的API密钥",
  "llm_model": "deepseek-v4-flash",
  "llm_decision_interval_sec": 2.5
}
```

### 3. 启动

1. 启动 Minecraft 并进入世界
2. 运行 Python 控制端：

```bash
python main_gui.py
```

3. 在游戏内按 `O` 键输入目标，AI 自动开始执行

## HTTP API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/ping` | GET | 健康检查 |
| `/api/state` | GET | 获取玩家位置、血量、背包、附近生物、目标 |
| `/api/blocks` | GET | 获取附近方块数据 |
| `/api/action` | POST | 执行元指令 |
| `/api/hud` | POST | 推送思考/动作/状态到游戏内悬浮窗 |

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
│       ├── ModApiServer.java      # HTTP 服务器
│       ├── GameDataCollector.java # 游戏状态采集
│       ├── PlayerController.java  # 玩家动作执行
│       ├── ActionRegistry.java   # 元指令注册
│       ├── AiHud.java            # 游戏内悬浮窗
│       ├── GoalScreen.java       # O 键目标输入界面
│       └── FreeMouseScreen.java  # P 键鼠标释放
├── ai_controller.py              # AI 控制主循环
├── llm_brain.py                  # LLM 调用与提示词
├── mod_bridge.py                  # HTTP 通信桥
├── main_gui.py                   # Python 命令行入口
├── config.json                   # LLM 配置
└── requirements.txt              # Python 依赖
```

## 从源码构建

```bash
cd mod
./gradlew build
# 产物在 mod/build/libs/endercore-2.0.0.jar
```

## 许可证

[MIT](LICENSE)