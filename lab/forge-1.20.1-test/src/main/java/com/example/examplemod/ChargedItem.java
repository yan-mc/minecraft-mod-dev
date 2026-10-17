package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ChargedItem extends Item {
    public static final int MAX_ENERGY = ChargerBlockEntity.MAX_ENERGY;
    public static final String ENERGY_TAG = "Energy";

    public ChargedItem(Properties properties) {
        super(properties);
    }

    public static int getEnergy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_ENERGY, tag.getInt(ENERGY_TAG)));
    }

    public static void setEnergy(ItemStack stack, int value) {
        int clamped = Math.max(0, Math.min(MAX_ENERGY, value));
        stack.getOrCreateTag().putInt(ENERGY_TAG, clamped);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int energy = getEnergy(stack);
            if (energy >= 100) {
                setEnergy(stack, energy - 100);
                player.displayClientMessage(Component.literal(
                        "Energy: " + getEnergy(stack) + "/" + MAX_ENERGY), true);
                return InteractionResultHolder.success(stack);
            }
            player.displayClientMessage(Component.literal("Not enough energy"), true);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Energy: " + getEnergy(stack) + "/" + MAX_ENERGY)
                .withStyle(ChatFormatting.GRAY));
    }
}
