# EnderCore v2.0 模组版

通过 Fabric 模组直接读取 Minecraft 游戏内部数据，无需 YOLO 屏幕识别，CPU 占用极低。

## 项目结构

```
EnderCore/
├── main_gui.py           # Python 主程序 GUI
├── mod_bridge.py         # 和模组通信的 HTTP 客户端
├── ai_controller.py      # AI 决策逻辑
├── crafting_planner.py   # 合成规划器（保留）
├── crafting_recipes.json # 合成配方数据
├── requirements.txt      # Python 依赖
├── mod/                  # Fabric 模组源代码
│   └── src/main/
│       ├── java/com/endercore/
│       │   ├── EnderCoreMod.java      # 模组主类
│       │   ├── GameDataCollector.java # 游戏数据采集
│       │   └── HttpServer.java       # HTTP API 服务器
│       └── resources/
│           └── fabric.mod.json        # 模组元数据
├── legacy_yolo_backup/  # 旧版 YOLO 文件备份
└── build.bat / install.bat
```

## 快速开始

### 第一步：安装 Python 依赖
```powershell
cd C:\Users\吴广陵\Desktop\EnderCore
pip install -r requirements.txt
```

### 第二步：构建 Fabric 模组
1. 安装 JDK 17 和 IntelliJ IDEA
2. 用 Fabric Loom 创建项目（参考 https://fabricmc.net/develop/）
3. 把 `mod/src/` 里的文件复制到你的 Fabric 项目
4. 运行 `gradlew build`，把 `build/libs/endercore-2.0.0.jar` 放到 `.minecraft/mods/` 文件夹

### 第三步：启动
1. 启动 Minecraft（Fabric 1.20.4），进入世界
2. 运行 Python 主程序：
   ```powershell
   python main_gui.py
   ```
3. 点击「重新连接」，显示 ✅ 已连接就 OK 了

## API 接口说明

模组端 HTTP 服务器跑在 `http://127.0.0.1:25599`：

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/ping` | GET | 健康检查 |
| `/api/state` | GET | 获取完整游戏状态（玩家+附近怪物） |
| `/api/action` | POST | 下发动作指令 |

### 动作指令类型
- `swing` — 挥手攻击
- `stop_move` — 停止移动
- `move_to` — 移动到坐标 `{x, z}`
- `look_at` — 看向坐标 `{x, y, z}`

## 作者
- 双赦
- 抖音：@94907869429
