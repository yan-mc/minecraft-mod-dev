package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ChargerBlock extends Block implements EntityBlock {
    public ChargerBlock() {
        super(Properties.of().strength(3.0F).requiresCorrectToolForDrops());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ChargerBlockEntity charger)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(ModRegistry.CHARGED_ITEM.get())) {
            if (!level.isClientSide) {
                int itemEnergy = ChargedItem.getEnergy(stack);
                int space = ChargerBlockEntity.MAX_ENERGY - charger.getEnergy();
                int transfer = Math.min(100, Math.min(itemEnergy, space));
                if (transfer > 0) {
                    charger.setEnergy(charger.getEnergy() + transfer);
                    ChargedItem.setEnergy(stack, itemEnergy - transfer);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal(
                    "Charger energy: " + charger.getEnergy()
                            + "/" + ChargerBlockEntity.MAX_ENERGY), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChargerBlockEntity charger) {
            return charger.getComparatorOutput();
        }
        return 0;
    }
}
