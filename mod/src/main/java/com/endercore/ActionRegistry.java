package com.endercore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * 动作模块注册表
 * 从 endercore/models/ 目录加载 JSON 动作，支持步骤序列组合
 */
public class ActionRegistry {
    private static final Map<String, JsonObject> customActions = new HashMap<>();

    public static void reload() {
        customActions.clear();
        File dir = Config.getModelsDir();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;
        for (File f : files) {
            try (FileReader r = new FileReader(f)) {
                JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
                if (obj.has("name") && obj.has("steps")) {
                    String name = obj.get("name").getAsString();
                    customActions.put(name, obj);
                    EnderCoreMod.LOGGER.info("已加载动作模块: " + name);
                }
            } catch (Exception e) {
                EnderCoreMod.LOGGER.warn("动作模块加载失败: " + f.getName(), e);
            }
        }
    }

    public static boolean has(String action) {
        return customActions.containsKey(action);
    }

    /**
     * 执行自定义动作：按顺序执行 steps 里的每个子动作
     */
    public static boolean execute(String action, JsonObject params) {
        JsonObject def = customActions.get(action);
        if (def == null || !def.has("steps")) return false;

        JsonArray steps = def.getAsJsonArray("steps");
        boolean allOk = true;
        for (JsonElement step : steps) {
            JsonObject stepObj = step.getAsJsonObject();
            String subAction = stepObj.get("action").getAsString();
            JsonObject subParams = stepObj.has("params") && stepObj.get("params").isJsonObject()
                    ? stepObj.getAsJsonObject("params")
                    : new JsonObject();

            // 合并外部传入的 params（覆盖默认值）
            for (Map.Entry<String, JsonElement> e : params.entrySet()) {
                subParams.add(e.getKey(), e.getValue().deepCopy());
            }

            boolean ok = GameDataCollector.executeActionBlocking(subAction, subParams);
            if (!ok) allOk = false;
        }
        return allOk;
    }
}
