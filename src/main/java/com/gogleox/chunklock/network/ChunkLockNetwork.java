package com.gogleox.chunklock.network;

import com.gogleox.chunklock.ChunkLockMod;
import com.gogleox.chunklock.claim.ClaimData;
import com.gogleox.chunklock.claim.ClaimManager;
import com.gogleox.chunklock.map.ClaimMapEntry;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ChunkLockNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final ClaimManager CLAIM_MANAGER = new ClaimManager();

    private static int packetId;
    private static SimpleChannel channel;

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(ChunkLockMod.MOD_ID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        channel.registerMessage(
                packetId++,
                ClaimMapSyncPacket.class,
                ClaimMapSyncPacket::encode,
                ClaimMapSyncPacket::decode,
                ClaimMapSyncPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static void syncTo(ServerPlayer player) {
        if (channel == null) {
            return;
        }

        ServerLevel level = player.getServer().getLevel(Level.OVERWORLD);

        if (level == null) {
            return;
        }

        List<ClaimMapEntry> entries = CLAIM_MANAGER.getAllClaims(level).stream()
                .map(claim -> toMapEntry(claim, player))
                .toList();

        channel.send(PacketDistributor.PLAYER.with(() -> player), new ClaimMapSyncPacket(entries));
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    private static ClaimMapEntry toMapEntry(ClaimData claim, ServerPlayer player) {
        return new ClaimMapEntry(
                claim.dimension(),
                claim.chunkX(),
                claim.chunkZ(),
                claim.ownerId(),
                claim.ownerLastKnownName(),
                claim.ownerId().equals(player.getUUID())
        );
    }

    private ChunkLockNetwork() {
    }
}
