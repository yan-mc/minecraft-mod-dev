package com.mcdev.probe;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

/**
 * Client-side probe: UI tree, mouse clicks, screenshots.
 * Runs on the client's main (render) thread for all game access.
 */
public final class ClientProbeServer {
    private static final Gson GSON = new Gson();
    private static HttpServer server;
    private static ExecutorService executor;
    private static String token = "mcdev";

    private ClientProbeServer() {
    }

    public static synchronized void start(int port, String tokenIn) {
        if (server != null) {
            return;
        }
        token = tokenIn == null ? "" : tokenIn;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/status", ClientProbeServer::handleStatus);
            server.createContext("/log/tail", ClientProbeServer::handleLogTail);
            server.createContext("/ui/snapshot", ClientProbeServer::handleUiSnapshot);
            server.createContext("/ui/click", ClientProbeServer::handleUiClick);
            server.createContext("/ui/close", ClientProbeServer::handleUiClose);
            server.createContext("/command", ClientProbeServer::handleCommand);
            server.createContext("/player/use", ClientProbeServer::handlePlayerUse);
            server.createContext("/player/use_item", ClientProbeServer::handlePlayerUseItem);
            server.createContext("/player/break", ClientProbeServer::handlePlayerBreak);
            server.createContext("/item/mainhand", ClientProbeServer::handleMainhandItem);
            server.createContext("/screenshot", ClientProbeServer::handleScreenshot);
            server.createContext("/quit", ClientProbeServer::handleQuit);
            executor = Executors.newFixedThreadPool(2);
            server.setExecutor(executor);
            server.start();
            McdevProbe.LOGGER.info("Mcdev Client Probe listening on 127.0.0.1:{}", port);
        } catch (IOException e) {
            McdevProbe.LOGGER.error("Failed to start Mcdev Client Probe", e);
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
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("side", "client");
                d.put("screen", mc.screen == null ? null : mc.screen.getClass().getName());
                d.put("title", mc.screen == null ? null : mc.screen.getTitle().getString());
                d.put("fps", mc.getFps());
                d.put("width", mc.getWindow().getGuiScaledWidth());
                d.put("height", mc.getWindow().getGuiScaledHeight());
                d.put("player", mc.player == null ? null : mc.player.getName().getString());
                d.put("dimension", mc.level == null ? null
                        : mc.level.dimension().location().toString());
                return d;
            });
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "status failed: " + e.getMessage());
        }
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

    private static void handleUiSnapshot(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = mc.screen;
                Map<String, Object> d = new LinkedHashMap<>();
                if (screen == null) {
                    d.put("screen", null);
                    return d;
                }
                d.put("screen", screen.getClass().getName());
                d.put("title", screen.getTitle().getString());
                List<Map<String, Object>> widgets = new ArrayList<>();
                for (GuiEventListener listener : screen.children()) {
                    if (listener instanceof AbstractWidget w) {
                        Map<String, Object> o = new LinkedHashMap<>();
                        o.put("class", w.getClass().getName());
                        o.put("x", w.getX());
                        o.put("y", w.getY());
                        o.put("width", w.getWidth());
                        o.put("height", w.getHeight());
                        o.put("message", w.getMessage().getString());
                        o.put("active", w.isActive());
                        o.put("visible", w.visible);
                        widgets.add(o);
                    }
                }
                d.put("widgets", widgets);
                if (screen instanceof AbstractContainerScreen<?> c) {
                    List<Map<String, Object>> slots = new ArrayList<>();
                    for (Slot s : c.getMenu().slots) {
                        Map<String, Object> o = new LinkedHashMap<>();
                        o.put("index", s.index);
                        o.put("x", s.x);
                        o.put("y", s.y);
                        o.put("screenX", c.getGuiLeft() + s.x + 8);
                        o.put("screenY", c.getGuiTop() + s.y + 8);
                        o.put("item", s.getItem().isEmpty() ? null
                                : s.getItem().getHoverName().getString());
                        o.put("count", s.getItem().getCount());
                        slots.add(o);
                    }
                    d.put("slots", slots);
                    d.put("guiLeft", c.getGuiLeft());
                    d.put("guiTop", c.getGuiTop());
                }
                return d;
            });
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "ui snapshot failed: " + e.getMessage());
        }
    }

    private static void handleUiClick(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            error(ex, 405, "POST required");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        final int x;
        final int y;
        final int button;
        final int slot;
        final boolean shift;
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            x = obj.has("x") ? obj.get("x").getAsInt() : 0;
            y = obj.has("y") ? obj.get("y").getAsInt() : 0;
            button = obj.has("button") ? obj.get("button").getAsInt() : 0;
            slot = obj.has("slot") ? obj.get("slot").getAsInt() : -1;
            shift = obj.has("shift") && obj.get("shift").getAsBoolean();
        } catch (Exception e) {
            error(ex, 400, "invalid json: " + e.getMessage());
            return;
        }
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                Screen screen = mc.screen;
                Map<String, Object> d = new LinkedHashMap<>();
                if (screen == null) {
                    d.put("clicked", false);
                    d.put("reason", "no screen");
                    return d;
                }
                if (slot >= 0 && screen instanceof AbstractContainerScreen<?> c) {
                    if (slot >= c.getMenu().slots.size()) {
                        d.put("clicked", false);
                        d.put("reason", "slot out of range");
                        return d;
                    }
                    Slot s = c.getMenu().slots.get(slot);
                    int sx = c.getGuiLeft() + s.x + 8;
                    int sy = c.getGuiTop() + s.y + 8;
                    if (shift) {
                        mc.gameMode.handleInventoryMouseClick(
                                c.getMenu().containerId, slot, button,
                                ClickType.QUICK_MOVE, mc.player);
                    } else {
                        screen.mouseClicked(sx, sy, button);
                        screen.mouseReleased(sx, sy, button);
                    }
                    d.put("clicked", true);
                    d.put("slot", slot);
                    d.put("x", sx);
                    d.put("y", sy);
                    d.put("shift", shift);
                    return d;
                }
                screen.mouseClicked(x, y, button);
                screen.mouseReleased(x, y, button);
                d.put("clicked", true);
                d.put("x", x);
                d.put("y", y);
                d.put("button", button);
                return d;
            });
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "ui click failed: " + e.getMessage());
        }
    }

    private static void handleUiClose(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            error(ex, 405, "POST required");
            return;
        }
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                Map<String, Object> d = new LinkedHashMap<>();
                if (mc.player == null) {
                    d.put("closed", false);
                    d.put("reason", "no player");
                    return d;
                }
                mc.player.closeContainer();
                mc.setScreen(null);
                d.put("closed", true);
                d.put("screen", mc.screen == null ? null : mc.screen.getClass().getName());
                return d;
            });
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "ui close failed: " + e.getMessage());
        }
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
        String cmd;
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            cmd = obj.get("cmd").getAsString();
        } catch (Exception e) {
            error(ex, 400, "invalid json: " + e.getMessage());
            return;
        }
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        final String command = cmd;
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                Map<String, Object> d = new LinkedHashMap<>();
                if (mc.player == null) {
                    d.put("sent", false);
                    d.put("reason", "no player");
                    return d;
                }
                mc.player.connection.sendCommand(command);
                d.put("sent", true);
                d.put("cmd", command);
                return d;
            });
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "command failed: " + e.getMessage());
        }
    }

    private static void handlePlayerUse(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            error(ex, 405, "POST required");
            return;
        }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        final int x;
        final int y;
        final int z;
        String faceName = "up";
        try {
            JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
            x = obj.get("x").getAsInt();
            y = obj.get("y").getAsInt();
            z = obj.get("z").getAsInt();
            if (obj.has("face")) {
                faceName = obj.get("face").getAsString();
            }
        } catch (Exception e) {
            error(ex, 400, "invalid json: " + e.getMessage());
            return;
        }
        Direction parsed = Direction.byName(faceName);
        final Direction face = parsed == null ? Direction.UP : parsed;
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                Map<String, Object> d = new LinkedHashMap<>();
                if (mc.player == null) {
                    d.put("used", false);
                    d.put("reason", "no player");
                    return d;
                }
                BlockPos pos = new BlockPos(x, y, z);
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(pos), face, pos, false);
                InteractionResult result = mc.gameMode.useItemOn(
                        mc.player, InteractionHand.MAIN_HAND, hit);
                d.put("used", true);
                d.put("result", result.toString());
                d.put("pos", x + "," + y + "," + z);
                d.put("face", face.getName());
                d.put("screen", mc.screen == null ? null : mc.screen.getClass().getName());
                return d;
            });
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "use failed: " + e.getMessage());
        }
    }

    private static void handlePlayerUseItem(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            error(ex, 405, "POST required");
            return;
        }
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                Map<String, Object> d = new LinkedHashMap<>();
                if (mc.player == null) {
                    d.put("used", false);
                    d.put("reason", "no player");
                    return d;
                }
                InteractionResult result = mc.gameMode.useItem(
                        mc.player, InteractionHand.MAIN_HAND);
                d.put("used", true);
                d.put("result", result.toString());
                return d;
            });
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "use item failed: " + e.getMessage());
        }
    }

    private static void handlePlayerBreak(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        int x = intQuery(ex, "x", 0);
        int y = intQuery(ex, "y", 64);
        int z = intQuery(ex, "z", 0);
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                Map<String, Object> d = new LinkedHashMap<>();
                if (mc.player == null || mc.level == null || mc.gameMode == null) {
                    d.put("ok", false);
                    d.put("error", "player not in world");
                    return d;
                }
                BlockPos pos = new BlockPos(x, y, z);
                d.put("pos", x + "," + y + "," + z);
                d.put("before", String.valueOf(ForgeRegistries.BLOCKS.getKey(mc.level.getBlockState(pos).getBlock())));
                d.put("started", mc.gameMode.startDestroyBlock(pos, Direction.UP));
                boolean continued = false;
                for (int i = 0; i < 10; i++) {
                    continued = mc.gameMode.continueDestroyBlock(pos, Direction.UP);
                }
                mc.gameMode.stopDestroyBlock();
                d.put("continued", continued);
                d.put("after", String.valueOf(ForgeRegistries.BLOCKS.getKey(mc.level.getBlockState(pos).getBlock())));
                return d;
            });
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "player break failed: " + e.getMessage());
        }
    }

    private static void handleMainhandItem(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                Map<String, Object> d = new LinkedHashMap<>();
                if (mc.player == null) {
                    d.put("found", false);
                    return d;
                }
                ItemStack stack = mc.player.getMainHandItem();
                d.put("found", true);
                d.put("item", stack.isEmpty() ? null
                        : String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem())));
                d.put("nbt", stack.getTag() == null ? null : stack.getTag().toString());
                d.put("count", stack.getCount());
                d.put("barVisible", stack.isBarVisible());
                d.put("barWidth", stack.getBarWidth());
                d.put("barColor", stack.getBarColor());
                return d;
            });
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "mainhand item query failed: " + e.getMessage());
        }
    }

    private static void handleScreenshot(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        try {
            Map<String, Object> data = onClient(() -> {
                Minecraft mc = Minecraft.getInstance();
                String name = "mcdev-" + System.currentTimeMillis() + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> {
                });
                java.nio.file.Path path = new File(
                        new File(mc.gameDirectory, "screenshots"), name)
                        .toPath().toAbsolutePath().normalize();
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("path", path.toString());
                d.put("screen", mc.screen == null ? null : mc.screen.getClass().getName());
                d.put("width", mc.getWindow().getWidth());
                d.put("height", mc.getWindow().getHeight());
                return d;
            });
            java.nio.file.Path path = java.nio.file.Path.of((String) data.get("path"));
            for (int i = 0; i < 40 && !java.nio.file.Files.isRegularFile(path); i++) {
                Thread.sleep(50);
            }
            data.put("exists", java.nio.file.Files.isRegularFile(path));
            data.put("size", java.nio.file.Files.isRegularFile(path)
                    ? java.nio.file.Files.size(path) : 0L);
            ok(ex, data);
        } catch (Exception e) {
            error(ex, 500, "screenshot failed: " + e.getMessage());
        }
    }

    private static void handleQuit(HttpExchange ex) throws IOException {
        if (!authorized(ex)) {
            return;
        }
        ok(ex, Map.of("quitting", true));
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }

    private static <T> T onClient(Callable<T> task) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                future.complete(task.call());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(10, TimeUnit.SECONDS);
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
