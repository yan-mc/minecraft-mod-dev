package com.mcdev.probe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ProbeServer {
    private static final Gson GSON = new Gson();
    private static HttpServer server;
    private static ExecutorService executor;
    private static MinecraftServer mc;
    private static String token = "mcdev";

    private ProbeServer() {
    }

    public static synchronized void start(MinecraftServer serverIn, int port, String tokenIn) {
        if (server != null) {
            return;
        }
        mc = serverIn;
        token = tokenIn == null ? "" : tokenIn;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/status", ProbeServer::handleStatus);
            server.createContext("/log/tail", ProbeServer::handleLogTail);
            server.createContext("/command", ProbeServer::handleCommand);
            server.createContext("/world/block", ProbeServer::handleWorldBlock);
            server.createContext("/run/dump", ProbeServer::handleRunDump);
            server.createContext("/screenshot", ProbeServer::handleNotClient);
            server.createContext("/ui/snapshot", ProbeServer::handleNotClient);
            server.createContext("/ui/click", ProbeServer::handleNotClient);
            executor = Executors.newFixedThreadPool(2);
            server.setExecutor(executor);
            server.start();
            McdevProbe.LOGGER.info("Mcdev Probe listening on 127.0.0.1:{}", port);
        } catch (IOException e) {
            McdevProbe.LOGGER.error("Failed to start Mcdev Probe", e);
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static void handleStatus(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("side", "server");
        data.put("tick", mc.getTickCount());
        data.put("gameTime", mc.overworld().getGameTime());
        data.put("dayTime", mc.overworld().getDayTime());
        data.put("players", mc.getPlayerList().getPlayerCount());
        data.put("maxPlayers", mc.getPlayerList().getMaxPlayers());
        data.put("level", mc.getWorldData().getLevelName());
        data.put("version", SharedConstants.getCurrentVersion().getName());
        data.put("frozen", TickControl.isFrozen());
        data.put("stepBudget", TickControl.getStepBudget());
        ok(ex, data);
    }

    private static void handleLogTail(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        int n = intQuery(ex, "n", 100);
        JsonObject data = new JsonObject();
        data.add("lines", ProbeLogs.tail(n));
        ok(ex, data);
    }

    private static void handleCommand(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            error(ex, 405, "POST required");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        final String cmd;
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            cmd = obj.get("cmd").getAsString();
        } catch (Exception e) {
            error(ex, 400, "invalid json: " + e.getMessage());
            return;
        }
        CompletableFuture<Integer> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(mc.getCommands()
                        .performPrefixedCommand(mc.createCommandSourceStack(), cmd));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            int result = future.get(10, TimeUnit.SECONDS);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cmd", cmd);
            data.put("result", result);
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "command failed: " + e.getMessage());
        }
    }

    private static void handleWorldBlock(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        int x = intQuery(ex, "x", 0);
        int y = intQuery(ex, "y", 64);
        int z = intQuery(ex, "z", 0);
        String dim = strQuery(ex, "dim", "minecraft:overworld");
        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                ResourceKey<Level> key = ResourceKey.create(
                        Registries.DIMENSION, new ResourceLocation(dim));
                ServerLevel level = mc.getLevel(key);
                if (level == null) {
                    level = mc.overworld();
                }
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                BlockEntity be = level.getBlockEntity(pos);
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("dim", level.dimension().location().toString());
                data.put("pos", x + "," + y + "," + z);
                data.put("block", String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock())));
                data.put("state", state.toString());
                data.put("analog", state.getAnalogOutputSignal(level, pos));
                data.put("blockEntity", be == null ? null : be.saveWithoutMetadata().toString());
                future.complete(data);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            ok(ex, future.get(10, TimeUnit.SECONDS));
        } catch (Exception e) {
            error(ex, 500, "world query failed: " + e.getMessage());
        }
    }

    private static void handleRunDump(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        int n = intQuery(ex, "n", 500);
        ok(ex, RunRecorder.dump(n));
    }

    private static void handleNotClient(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        error(ex, 501, "client-only endpoint; this probe is running on the server");
    }

    private static boolean authorized(HttpExchange ex) throws IOException {
        if (token.isEmpty()) {
            return true;
        }
        String got = ex.getRequestHeaders().getFirst("X-Mcdev-Token");
        if (token.equals(got)) {
            return true;
        }
        error(ex, 401, "invalid token");
        return false;
    }

    private static void ok(HttpExchange ex, Object data) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("ok", true);
        root.add("data", GSON.toJsonTree(data));
        send(ex, 200, root);
    }

    private static void error(HttpExchange ex, int code, String message) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("ok", false);
        JsonObject err = new JsonObject();
        err.addProperty("message", message);
        root.add("error", err);
        send(ex, code, root);
    }

    private static void send(HttpExchange ex, int code, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static int intQuery(HttpExchange ex, String key, int def) {
        String raw = strQuery(ex, key, null);
        if (raw == null) {
            return def;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String strQuery(HttpExchange ex, String key, String def) {
        String query = ex.getRequestURI().getQuery();
        if (query == null || query.isEmpty()) {
            return def;
        }
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
            if (k.equals(key)) {
                return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return def;
    }
}
