package com.example.examplemod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = ExampleMod.MODID, bus = EventBusSubscriber.Bus.FORGE)
public final class ChargerCommand {
    private ChargerCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("charger")
                .then(Commands.literal("info")
                        .executes(context -> info(context.getSource(), null))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> info(context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"))))));
    }

    private static int info(CommandSourceStack source, @Nullable BlockPos pos) {
        ServerLevel level = source.getLevel();
        if (pos == null) {
            ServerPlayer player;
            try {
                player = source.getPlayerOrException();
            } catch (CommandSyntaxException exception) {
                source.sendFailure(Component.literal(
                        "Run /charger info as a player, or pass a block position."));
                return 0;
            }
            HitResult hit = player.pick(5.0D, 0.0F, false);
            if (hit.getType() != HitResult.Type.BLOCK) {
                source.sendFailure(Component.literal("Look at a charger first."));
                return 0;
            }
            pos = ((BlockHitResult) hit).getBlockPos();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ChargerBlockEntity charger) {
            BlockPos target = pos;
            source.sendSuccess(() -> Component.literal(
                    "Charger energy: " + charger.getEnergy()
                            + "/" + ChargerBlockEntity.MAX_ENERGY
                            + " at " + target.toShortString()), false);
            return 1;
        }
        BlockPos target = pos;
        source.sendFailure(Component.literal("Not a charger at " + target.toShortString()));
        return 0;
    }
}
