package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class ExampleGameTests {
    private ExampleGameTests() {
    }

    @GameTest(template = "empty")
    public static void airIsAir(GameTestHelper helper) {
        // The GameTest structure block occupies relative (0,0,0); the template
        // itself starts at (1,1,1).  The floor is at relative y=1.
        helper.assertBlockPresent(Blocks.AIR, 1, 2, 1);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void stoneFloorExists(GameTestHelper helper) {
        helper.assertBlockPresent(Blocks.STONE, 1, 1, 1);
        // The structure block reserves relative (0,0,0), so a 5x5 template is
        // clipped to relative 1..4 inside a 5x5 test area.
        helper.assertBlockPresent(Blocks.STONE, 4, 1, 4);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canPlaceBlock(GameTestHelper helper) {
        helper.setBlock(2, 2, 2, Blocks.DIAMOND_BLOCK);
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, 2, 2, 2);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargerClampsEnergy(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModRegistry.CHARGER.get());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockEntity be = helper.getLevel().getBlockEntity(pos);
        if (!(be instanceof ChargerBlockEntity charger)) {
            helper.assertTrue(false, "charger block entity missing");
            return;
        }
        charger.setEnergy(-5);
        helper.assertTrue(charger.getEnergy() == 0, "negative energy should clamp to 0");
        charger.setEnergy(20000);
        helper.assertTrue(charger.getEnergy() == 10000, "energy should clamp to 10000");
        charger.setEnergy(5000);
        helper.assertTrue(charger.getEnergy() == 5000, "energy should be 5000");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargerComparator(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModRegistry.CHARGER.get());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockEntity be = helper.getLevel().getBlockEntity(pos);
        if (!(be instanceof ChargerBlockEntity charger)) {
            helper.assertTrue(false, "charger block entity missing");
            return;
        }
        charger.setEnergy(5000);
        BlockState state = helper.getLevel().getBlockState(pos);
        int signal = state.getBlock().getAnalogOutputSignal(state, helper.getLevel(), pos);
        helper.assertTrue(signal == 7, "comparator signal should be 7, got " + signal);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargerNbtRoundTrip(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModRegistry.CHARGER.get());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockEntity be = helper.getLevel().getBlockEntity(pos);
        if (!(be instanceof ChargerBlockEntity charger)) {
            helper.assertTrue(false, "charger block entity missing");
            return;
        }
        charger.setEnergy(1234);
        CompoundTag tag = charger.saveWithoutMetadata();
        helper.assertTrue(tag.getInt("Energy") == 1234, "NBT should contain Energy=1234");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargedItemEnergy(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModRegistry.CHARGED_ITEM.get());
        ChargedItem.setEnergy(stack, 500);
        helper.assertTrue(ChargedItem.getEnergy(stack) == 500, "item energy should be 500");
        ChargedItem.setEnergy(stack, 20000);
        helper.assertTrue(ChargedItem.getEnergy(stack) == 10000, "item energy should clamp");
        helper.succeed();
    }
}
