package com.mcdev.probe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class ProbeLogs {
    private static final int MAX = 1000;
    private static final Deque<JsonObject> LINES = new ConcurrentLinkedDeque<>();
    private static volatile boolean installed;

    private ProbeLogs() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        Logger root = (Logger) LogManager.getRootLogger();
        root.addAppender(new RingAppender());
    }

    public static JsonArray tail(int n) {
        int limit = Math.max(1, Math.min(n, MAX));
        List<JsonObject> all = new ArrayList<>(LINES);
        int from = Math.max(0, all.size() - limit);
        JsonArray arr = new JsonArray();
        for (int i = from; i < all.size(); i++) {
            arr.add(all.get(i));
        }
        return arr;
    }

    private static final class RingAppender extends AbstractAppender {
        RingAppender() {
            super("McdevProbe", null, null, false, new Property[0]);
            start();
        }

        @Override
        public void append(LogEvent event) {
            JsonObject o = new JsonObject();
            o.addProperty("time", event.getTimeMillis());
            o.addProperty("level", event.getLevel().name());
            o.addProperty("logger", event.getLoggerName());
            o.addProperty("thread", event.getThreadName());
            o.addProperty("message", event.getMessage().getFormattedMessage());
            if (event.getThrown() != null) {
                StringWriter sw = new StringWriter();
                event.getThrown().printStackTrace(new PrintWriter(sw));
                o.addProperty("throwable", sw.toString());
            }
            LINES.addLast(o);
            while (LINES.size() > MAX) {
                LINES.pollFirst();
            }
        }
    }
}
