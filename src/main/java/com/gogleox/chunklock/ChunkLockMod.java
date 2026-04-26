package com.gogleox.chunklock;

import com.gogleox.chunklock.claim.ClaimEvents;
import com.gogleox.chunklock.command.ChunkLockCommands;
import com.gogleox.chunklock.config.ChunkLockConfig;
import com.gogleox.chunklock.item.ChunkLockItems;
import com.gogleox.chunklock.network.ChunkLockNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(ChunkLockMod.MOD_ID)
public final class ChunkLockMod {
    public static final String MOD_ID = "chunklock";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChunkLockMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ChunkLockItems.register(modEventBus);
        ChunkLockNetwork.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ChunkLockConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(ClaimEvents.class);
        MinecraftForge.EVENT_BUS.register(ChunkLockCommands.class);

        LOGGER.info("ChunkLock foundation initialized");
    }
}
