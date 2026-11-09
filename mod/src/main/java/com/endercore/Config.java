package com.endercore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;

/**
 * EnderCore 配置管理
 */
public class Config {
    public static class Data {
        public int http_port = 25599;
        public String http_host = "127.0.0.1";
        public int state_tick_ms = 500;
    }

    private static Data data = new Data();
    private static File configFile;

    public static void init() {
        Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("endercore");
        dir.toFile().mkdirs();
        configFile = dir.resolve("config.json").toFile();
        load();

        Path modelsDir = dir.resolve("models");
        modelsDir.toFile().mkdirs();
        seedPresetModels(modelsDir.toFile());
    }

    public static Data get() { return data; }

    public static void load() {
        if (!configFile.exists()) { save(); return; }
        try (FileReader r = new FileReader(configFile)) {
            data = new Gson().fromJson(r, Data.class);
        } catch (Exception e) {
            data = new Data();
        }
    }

    public static void save() {
        try (FileWriter w = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(data, w);
        } catch (Exception e) {
            EnderCoreMod.LOGGER.error("配置保存失败", e);
        }
    }

    private static void seedPresetModels(File modelsDir) {
        String[][] presets = {
            {"move_to", "走到指定坐标，自动转向", "{\"x\":0,\"z\":0}"},
            {"move_rough", "粗略走到指定坐标", "{\"x\":0,\"z\":0}"},
            {"stop_move", "停止移动", "{}"},
            {"pillar_up", "原地向上搭柱", "{\"target_y\":100}"},
            {"bridge_forward", "向前搭桥", "{\"distance\":5}"},
            {"place_block_at", "在指定坐标放方块", "{\"x\":0,\"y\":0,\"z\":0}"},
            {"break_block", "挖指定坐标的方块", "{\"x\":0,\"y\":0,\"z\":0}"},
            {"select_slot", "选择物品栏格子", "{\"slot\":0}"},
            {"use_item", "使用手中物品", "{}"},
            {"look_at", "看向指定坐标", "{\"x\":0,\"y\":0,\"z\":0}"},
            {"attack_nearest", "攻击最近的生物", "{}"},
        };

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        for (String[] p : presets) {
            File f = new File(modelsDir, p[0] + ".json");
            if (f.exists()) continue;
            try (FileWriter w = new FileWriter(f)) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("name", p[0]);
                m.put("description", p[1]);
                m.put("default_params", gson.fromJson(p[2], Object.class));
                gson.toJson(m, w);
            } catch (Exception e) {
                EnderCoreMod.LOGGER.warn("预设动作写入失败: " + p[0], e);
            }
        }

        // 示例：带 steps 的自定义动作
        File example = new File(modelsDir, "example_build_wall.json");
        if (!example.exists()) {
            try (FileWriter w = new FileWriter(example)) {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("name", "build_wall");
                m.put("description", "示例：建一面3格长的墙（自定义动作）");
                java.util.List<Object> steps = new java.util.ArrayList<>();
                int[][] coords = {{100,64,100},{101,64,100},{102,64,100}};
                for (int[] c : coords) {
                    java.util.Map<String, Object> step = new java.util.LinkedHashMap<>();
                    step.put("action", "place_block_at");
                    java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
                    params.put("x", c[0]); params.put("y", c[1]); params.put("z", c[2]);
                    step.put("params", params);
                    steps.add(step);
                }
                m.put("steps", steps);
                gson.toJson(m, w);
            } catch (Exception e) {
                EnderCoreMod.LOGGER.warn("示例动作写入失败", e);
            }
        }
    }

    public static File getModelsDir() {
        return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameDir().resolve("endercore/models").toFile();
    }
}
