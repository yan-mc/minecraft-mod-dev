package com.example.examplemod;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ExampleMod.MODID)
public final class ExampleMod {
    public static final String MODID = "examplemod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public ExampleMod() {
        LOGGER.info("ExampleMod constructed");
    }
}
