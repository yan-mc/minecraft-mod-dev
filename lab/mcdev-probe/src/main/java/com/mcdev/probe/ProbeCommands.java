package com.mcdev.probe;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

public final class ProbeCommands {
    private ProbeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("mcdev");
        root.then(Commands.literal("status").executes(ProbeCommands::status));
        root.then(Commands.literal("freeze").then(
                Commands.argument("value", BoolArgumentType.bool()).executes(ProbeCommands::freeze)));
        root.then(Commands.literal("step").then(
                Commands.argument("ticks", IntegerArgumentType.integer(1, 10000))
                        .executes(ProbeCommands::step)));
        root.then(fakeplayer());
        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> fakeplayer() {
        return Commands.literal("fakeplayer")
                .then(Commands.literal("spawn").then(
                        Commands.argument("name", StringArgumentType.word())
                                .executes(ProbeCommands::spawn)))
                .then(Commands.literal("use").then(
                        Commands.argument("name", StringArgumentType.word()).then(
                                Commands.argument("x", IntegerArgumentType.integer()).then(
                                        Commands.argument("y", IntegerArgumentType.integer()).then(
                                                Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(ProbeCommands::use))))))
                .then(Commands.literal("attack").then(
                        Commands.argument("name", StringArgumentType.word())
                                .executes(ProbeCommands::attack)));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal(
                "tick=" + src.getServer().getTickCount()
                        + " players=" + src.getServer().getPlayerList().getPlayerCount()
                        + " frozen=" + TickControl.isFrozen()
                        + " step=" + TickControl.getStepBudget()), false);
        return 1;
    }

    private static int freeze(CommandContext<CommandSourceStack> ctx) {
        boolean value = BoolArgumentType.getBool(ctx, "value");
        CommandSourceStack src = ctx.getSource();
        TickControl.setFrozen(value);
        ServerLevel level = src.getServer().overworld();
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING)
                .set(value ? 0 : 3, src.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT)
                .set(!value, src.getServer());
        src.sendSuccess(() -> Component.literal("frozen=" + value), false);
        return 1;
    }

    private static int step(CommandContext<CommandSourceStack> ctx) {
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        TickControl.step(ticks);
        ctx.getSource().sendSuccess(() -> Component.literal("step=" + ticks), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack src = ctx.getSource();
        Vec3 p = src.getPosition();
        BlockPos pos = new BlockPos(
                (int) Math.floor(p.x), (int) Math.floor(p.y), (int) Math.floor(p.z));
        String result = FakePlayers.spawn(src.getLevel(), name, pos);
        src.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int use(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        int x = IntegerArgumentType.getInteger(ctx, "x");
        int y = IntegerArgumentType.getInteger(ctx, "y");
        int z = IntegerArgumentType.getInteger(ctx, "z");
        String result = FakePlayers.useOn(ctx.getSource().getLevel(), name, new BlockPos(x, y, z));
        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int attack(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String result = FakePlayers.attackNearest(ctx.getSource().getLevel(), name);
        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}
