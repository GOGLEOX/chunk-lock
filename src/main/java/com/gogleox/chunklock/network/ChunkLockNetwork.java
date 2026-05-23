package com.gogleox.chunklock.network;

import com.gogleox.chunklock.ChunkLockMod;
import com.gogleox.chunklock.claim.ClaimData;
import com.gogleox.chunklock.claim.ClaimManager;
import com.gogleox.chunklock.map.ClaimMapEntry;
import com.gogleox.chunklock.show.ClaimBoundaryBuilder;
import com.gogleox.chunklock.show.ClaimBoundaryState;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ChunkLockNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final int CLAIM_SYNC_RADIUS = 1;
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

        channel.registerMessage(
                packetId++,
                ShowClaimBoundaryPacket.class,
                ShowClaimBoundaryPacket::encode,
                ShowClaimBoundaryPacket::decode,
                ShowClaimBoundaryPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static void syncTo(ServerPlayer player) {
        if (channel == null) {
            return;
        }

        List<ClaimMapEntry> entries = CLAIM_MANAGER.getClaimsInRange(player.serverLevel(), player.chunkPosition(), CLAIM_SYNC_RADIUS).stream()
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

    public static void syncAffectingChunk(MinecraftServer server, ResourceLocation dimension, ChunkPos chunkPos) {
        if (server == null || channel == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.serverLevel().dimension().location().equals(dimension)) {
                continue;
            }

            ChunkPos playerChunk = player.chunkPosition();
            if (Math.abs(playerChunk.x - chunkPos.x) <= CLAIM_SYNC_RADIUS
                    && Math.abs(playerChunk.z - chunkPos.z) <= CLAIM_SYNC_RADIUS) {
                syncTo(player);
            }
        }
    }

    public static void showChunkBoundary(ServerPlayer player, int durationTicks, int maxChunks) {
        if (channel == null) {
            return;
        }

        ClaimBoundaryState.PendingBoundary boundary = ClaimBoundaryBuilder.build(player.serverLevel(), player.chunkPosition(), durationTicks, maxChunks);
        channel.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ShowClaimBoundaryPacket(boundary.dimension(), boundary.segments(), boundary.durationTicks())
        );
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
