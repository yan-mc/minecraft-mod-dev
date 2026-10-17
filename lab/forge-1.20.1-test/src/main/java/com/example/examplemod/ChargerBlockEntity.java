package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ChargerBlockEntity extends BlockEntity {
    public static final int MAX_ENERGY = 10000;

    private int energy;

    public ChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CHARGER_BE.get(), pos, state);
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int value) {
        int clamped = Math.max(0, Math.min(MAX_ENERGY, value));
        if (clamped != energy) {
            energy = clamped;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public int getComparatorOutput() {
        return energy * 15 / MAX_ENERGY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Energy", energy);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = Math.max(0, Math.min(MAX_ENERGY, tag.getInt("Energy")));
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Energy", energy);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
