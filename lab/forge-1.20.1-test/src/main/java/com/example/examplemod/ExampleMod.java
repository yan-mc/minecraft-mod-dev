package com.example.examplemod;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ExampleMod.MODID)
public final class ExampleMod {
    public static final String MODID = "examplemod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public ExampleMod(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ModRegistry.register(bus);
        LOGGER.info("ExampleMod constructed");
    }
}
