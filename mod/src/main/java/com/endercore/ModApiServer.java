package com.endercore;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 模组内置 HTTP 服务器
 * 供外部 Python 程序查询游戏数据、下发动作指令
 *
 * 注意：类名不能叫 HttpServer，否则会和 com.sun.net.httpserver.HttpServer 冲突（编译错误）。
 */
public class ModApiServer {
    private final String host;
    private final int port;
    private HttpServer server;
    private ExecutorService executor;

    public ModApiServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);

        // 健康检查
        server.createContext("/api/ping", exchange ->
                respond(exchange, 200, "{\"status\":\"ok\",\"mod\":\"EnderCore\"}"));

        // 获取游戏状态
        server.createContext("/api/state", exchange -> {
            JsonObject state = GameDataCollector.getGameStateBlocking();
            respond(exchange, 200, state.toString());
        });

        // 周围方块扫描
        server.createContext("/api/blocks", exchange -> {
            JsonObject data = GameDataCollector.scanBlocksBlocking();
            respond(exchange, 200, data.toString());
        });

        // 执行动作
        server.createContext("/api/action", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            boolean success;
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject req = JsonParser.parseString(body).getAsJsonObject();
                String action = req.get("action").getAsString();
                JsonObject params = req.has("params") && req.get("params").isJsonObject()
                        ? req.getAsJsonObject("params")
                        : new JsonObject();
                success = GameDataCollector.executeActionBlocking(action, params);
            } catch (Exception e) {
                respond(exchange, 400, "{\"error\":\"bad request\",\"detail\":\"" + escape(e.toString()) + "\"}");
                return;
            }

            respond(exchange, 200, "{\"success\":" + success + "}");
        });

        // 更新 AI 悬浮窗
        server.createContext("/api/hud", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject req = JsonParser.parseString(body).getAsJsonObject();
                if (req.has("thought")) AiHud.setThought(req.get("thought").getAsString());
                if (req.has("action")) AiHud.setAction(req.get("action").getAsString());
                if (req.has("status")) AiHud.setStatus(req.get("status").getAsString());
            } catch (Exception e) {
                respond(exchange, 400, "{\"error\":\"bad request\"}");
                return;
            }
            respond(exchange, 200, "{\"success\":true}");
        });
        executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
