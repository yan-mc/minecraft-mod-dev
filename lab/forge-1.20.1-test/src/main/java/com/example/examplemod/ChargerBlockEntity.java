package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ChargerBlockEntity extends BlockEntity implements Container, ContainerData {
    public static final int MAX_ENERGY = 10000;
    public static final int SLOT = 0;
    public static final int DATA_ENERGY = 0;
    public static final int DATA_MAX = 1;

    private int energy;
    private ItemStack item = ItemStack.EMPTY;

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
        if (!item.isEmpty()) {
            tag.put("Item", item.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        energy = Math.max(0, Math.min(MAX_ENERGY, tag.getInt("Energy")));
        item = tag.contains("Item") ? ItemStack.of(tag.getCompound("Item")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Energy", energy);
        if (!item.isEmpty()) {
            tag.put("Item", item.save(new CompoundTag()));
        }
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return item.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return item;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = item.split(amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = item;
        item = ItemStack.EMPTY;
        return result;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        item = stack;
        if (item.getCount() > getMaxStackSize()) {
            item.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && player.distanceToSqr(worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        item = ItemStack.EMPTY;
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int get(int index) {
        return index == DATA_ENERGY ? energy : MAX_ENERGY;
    }

    @Override
    public void set(int index, int value) {
        if (index == DATA_ENERGY) {
            setEnergy(value);
        }
    }

    @Override
    public int getCount() {
        return 2;
    }
}
