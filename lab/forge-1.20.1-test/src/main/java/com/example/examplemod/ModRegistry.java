package com.example.examplemod;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ExampleMod.MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ExampleMod.MODID);

    public static final RegistryObject<Block> CHARGER =
            BLOCKS.register("charger", ChargerBlock::new);
    public static final RegistryObject<Item> CHARGER_ITEM =
            ITEMS.register("charger", () -> new BlockItem(CHARGER.get(), new Item.Properties()));
    public static final RegistryObject<Item> CHARGED_ITEM =
            ITEMS.register("charged_item", () -> new ChargedItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<ChargerBlockEntity>> CHARGER_BE =
            BLOCK_ENTITIES.register("charger",
                    () -> BlockEntityType.Builder.of(ChargerBlockEntity::new, CHARGER.get()).build(null));

    private ModRegistry() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}
