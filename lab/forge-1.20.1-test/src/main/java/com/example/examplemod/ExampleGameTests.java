package com.example.examplemod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
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
}
