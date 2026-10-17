package com.mcdev.probe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * v0 run recorder: writes a JSON snapshot under .mcdev/runs/<id>/.
 * The DSH host later imports the snapshot into SQLite.
 */
public final class RunRecorder {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private static Path runDir;
    private static String runId;
    private static long startedAt;
    private static MinecraftServer mc;

    private RunRecorder() {
    }

    public static synchronized void start(MinecraftServer server) {
        mc = server;
        runId = FMT.format(Instant.now());
        startedAt = System.currentTimeMillis();
        runDir = Paths.get("").toAbsolutePath()
                .resolve(".mcdev").resolve("runs").resolve(runId);
        try {
            Files.createDirectories(runDir);
        } catch (IOException e) {
            McdevProbe.LOGGER.warn("Could not create run dir", e);
        }
        McdevProbe.LOGGER.info("Mcdev run {} at {}", runId, runDir);
    }

    public static synchronized JsonObject dump(int n) {
        JsonObject root = new JsonObject();
        root.addProperty("runId", runId);
        root.addProperty("startedAt", startedAt);
        root.addProperty("tick", mc.getTickCount());
        root.addProperty("gameTime", mc.overworld().getGameTime());
        root.addProperty("frozen", TickControl.isFrozen());
        root.add("logs", ProbeLogs.tail(n));
        Path out = runDir.resolve("snapshot-" + System.currentTimeMillis() + ".json");
        try {
            Files.writeString(out, GSON.toJson(root), StandardCharsets.UTF_8);
            root.addProperty("path", out.toString());
        } catch (IOException e) {
            root.addProperty("path", "write failed: " + e.getMessage());
        }
        return root;
    }

    public static synchronized void finish() {
        if (runDir == null) {
            return;
        }
        try {
            Files.writeString(runDir.resolve("finished.txt"),
                    Instant.now().toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
