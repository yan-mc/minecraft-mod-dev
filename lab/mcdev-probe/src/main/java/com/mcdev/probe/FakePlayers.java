package com.mcdev.probe;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FakePlayers {
    private static final Map<String, FakePlayer> PLAYERS = new ConcurrentHashMap<>();

    private FakePlayers() {
    }

    public static String spawn(ServerLevel level, String name, BlockPos pos) {
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
        FakePlayer player = FakePlayerFactory.get(level, profile);
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        PLAYERS.put(name, player);
        return "spawned fake player " + name + " at " + pos;
    }

    public static String useOn(ServerLevel level, String name, BlockPos pos) {
        FakePlayer player = PLAYERS.get(name);
        if (player == null) {
            return "no fake player " + name;
        }
        ItemStack stack = player.getMainHandItem();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult result = player.gameMode.useItemOn(
                player, level, stack, InteractionHand.MAIN_HAND, hit);
        return "useOn " + pos + " -> " + result;
    }

    public static String attackNearest(ServerLevel level, String name) {
        FakePlayer player = PLAYERS.get(name);
        if (player == null) {
            return "no fake player " + name;
        }
        AABB box = player.getBoundingBox().inflate(6.0D);
        List<LivingEntity> targets = level.getEntitiesOfClass(
                LivingEntity.class, box, e -> e != player && e.isAlive());
        if (targets.isEmpty()) {
            return "no target within 6 blocks";
        }
        LivingEntity target = targets.get(0);
        player.attack(target);
        player.swing(InteractionHand.MAIN_HAND);
        return "attacked " + target.getName().getString();
    }
}
