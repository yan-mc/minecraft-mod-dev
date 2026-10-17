package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

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

    @GameTest(template = "empty")
    public static void chargedItemBar(GameTestHelper helper) {
        ItemStack stack = new ItemStack(ModRegistry.CHARGED_ITEM.get());
        ChargedItem item = (ChargedItem) stack.getItem();

        ChargedItem.setEnergy(stack, 0);
        helper.assertTrue(!item.isBarVisible(stack), "bar should be hidden at energy 0");

        ChargedItem.setEnergy(stack, 10000);
        helper.assertTrue(!item.isBarVisible(stack), "bar should be hidden at energy 10000");

        ChargedItem.setEnergy(stack, 5000);
        helper.assertTrue(item.isBarVisible(stack), "bar should be visible at energy 5000");
        helper.assertTrue(item.getBarWidth(stack) == 6,
                "bar width should be 6 at energy 5000, got " + item.getBarWidth(stack));
        helper.assertTrue(item.getBarColor(stack) == 0x00FF00,
                "bar color should be green at energy 5000, got " + item.getBarColor(stack));

        ChargedItem.setEnergy(stack, 2000);
        helper.assertTrue(item.isBarVisible(stack), "bar should be visible at energy 2000");
        helper.assertTrue(item.getBarWidth(stack) == 2,
                "bar width should be 2 at energy 2000, got " + item.getBarWidth(stack));
        helper.assertTrue(item.getBarColor(stack) == 0xFFFF00,
                "bar color should be yellow at energy 2000, got " + item.getBarColor(stack));

        ChargedItem.setEnergy(stack, 500);
        helper.assertTrue(item.isBarVisible(stack), "bar should be visible at energy 500");
        helper.assertTrue(item.getBarWidth(stack) == 1,
                "bar width should be 1 at energy 500, got " + item.getBarWidth(stack));
        helper.assertTrue(item.getBarColor(stack) == 0xFF0000,
                "bar color should be red at energy 500, got " + item.getBarColor(stack));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargerRecipeExists(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation(ExampleMod.MODID, "charger"));
        helper.assertTrue(recipe.isPresent(), "charger recipe missing");
        ItemStack result = recipe.get().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModRegistry.CHARGER_ITEM.get()), "recipe result should be charger");
        helper.assertTrue(result.getCount() == 1, "recipe count should be 1");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargerCommandInfo(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModRegistry.CHARGER.get());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockEntity be = helper.getLevel().getBlockEntity(pos);
        if (!(be instanceof ChargerBlockEntity charger)) {
            helper.assertTrue(false, "charger block entity missing");
            return;
        }
        charger.setEnergy(777);
        var source = helper.getLevel().getServer().createCommandSourceStack();
        int result = helper.getLevel().getServer().getCommands().performPrefixedCommand(
                source, "charger info " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        helper.assertTrue(result == 1, "charger info command should return 1");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargerMenuShiftClickNoDuplication(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModRegistry.CHARGER.get());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockEntity be = helper.getLevel().getBlockEntity(pos);
        if (!(be instanceof ChargerBlockEntity charger)) {
            helper.assertTrue(false, "charger block entity missing");
            return;
        }
        charger.setItem(0, new ItemStack(ModRegistry.CHARGED_ITEM.get()));
        Player player = helper.makeMockPlayer();
        ChargerMenu menu = new ChargerMenu(1, player.getInventory(), charger, charger);
        menu.quickMoveStack(player, 0);

        int total = charger.getItem(0).getCount();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            total += player.getInventory().getItem(i).getCount();
        }
        helper.assertTrue(charger.getItem(0).isEmpty(), "charger slot should be empty");
        helper.assertTrue(total == 1, "total item count should stay 1, got " + total);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chargerLootDrop(GameTestHelper helper) {
        helper.setBlock(1, 1, 1, ModRegistry.CHARGER.get());
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState state = helper.getLevel().getBlockState(pos);
        BlockEntity be = helper.getLevel().getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(
                state, helper.getLevel(), pos, be, null, ItemStack.EMPTY);
        int count = 0;
        for (ItemStack stack : drops) {
            if (stack.is(ModRegistry.CHARGER_ITEM.get())) {
                count += stack.getCount();
            }
        }
        helper.assertTrue(count == 1, "charger should drop exactly 1, got " + count);
        helper.succeed();
    }
}
