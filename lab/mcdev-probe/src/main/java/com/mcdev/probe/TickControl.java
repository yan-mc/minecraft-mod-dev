package com.mcdev.probe;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Tick-freeze state.  The ServerLevel mixin cancels the level tick while
 * frozen; a step asks each level for N more ticks.
 */
public final class TickControl {
    private static volatile boolean frozen;
    private static int stepTicks;
    private static final Map<ResourceKey<Level>, Integer> BUDGETS = new HashMap<>();

    private TickControl() {
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static synchronized int getStepBudget() {
        return BUDGETS.getOrDefault(Level.OVERWORLD, stepTicks);
    }

    public static synchronized void setFrozen(boolean value) {
        frozen = value;
        if (!value) {
            stepTicks = 0;
            BUDGETS.clear();
        }
    }

    public static synchronized void step(int ticks) {
        stepTicks = Math.max(0, ticks);
        BUDGETS.clear();
        frozen = true;
    }

    public static synchronized boolean shouldCancelTick(ServerLevel level) {
        if (!frozen) {
            return false;
        }
        ResourceKey<Level> key = level.dimension();
        int remaining = BUDGETS.computeIfAbsent(key, k -> stepTicks);
        if (remaining > 0) {
            BUDGETS.put(key, remaining - 1);
            return false;
        }
        return true;
    }
}
