package com.mcdev.probe;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(McdevProbe.MODID)
public final class McdevProbe {
    public static final String MODID = "mcdev_probe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    public static final int DEFAULT_PORT = 39001;
    public static final int DEFAULT_CLIENT_PORT = 39002;
    public static final String DEFAULT_TOKEN = "mcdev";

    public McdevProbe() {
        ProbeLogs.install();
        MinecraftForge.EVENT_BUS.register(this);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        }
        LOGGER.info("Mcdev Probe constructed on {}", FMLEnvironment.dist);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            int port = Integer.getInteger("mcdev.probe.client.port", DEFAULT_CLIENT_PORT);
            String token = System.getProperty("mcdev.probe.token", DEFAULT_TOKEN);
            ClientProbeServer.start(port, token);
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ProbeCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            return;
        }
        RunRecorder.start(event.getServer());
        int port = Integer.getInteger("mcdev.probe.port", DEFAULT_PORT);
        String token = System.getProperty("mcdev.probe.token", DEFAULT_TOKEN);
        ProbeServer.start(event.getServer(), port, token);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            return;
        }
        RunRecorder.finish();
        ProbeServer.stop();
    }
}
