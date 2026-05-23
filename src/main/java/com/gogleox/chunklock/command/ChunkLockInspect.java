package com.gogleox.chunklock.command;

import com.gogleox.chunklock.claim.ClaimData;
import com.gogleox.chunklock.claim.ClaimManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class ChunkLockInspect {
    private static final ClaimManager CLAIM_MANAGER = new ClaimManager();
    private static final DateTimeFormatter CLAIM_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private static final Set<UUID> INSPECTING_PLAYERS = new HashSet<>();
    private static final Map<UUID, InspectedChunk> LAST_REPORTED_CHUNKS = new HashMap<>();

    private ChunkLockInspect() {
    }

    public static boolean toggle(ServerPlayer player) {
        UUID playerId = player.getUUID();

        if (INSPECTING_PLAYERS.remove(playerId)) {
            LAST_REPORTED_CHUNKS.remove(playerId);
            player.sendSystemMessage(Component.translatable("command.chunklock.inspect.disabled"));
            return false;
        }

        INSPECTING_PLAYERS.add(playerId);
        reportChunk(player, player.serverLevel(), player.chunkPosition(), true);
        player.sendSystemMessage(Component.translatable("command.chunklock.inspect.enabled"));
        return true;
    }

    public static void clear(ServerPlayer player) {
        UUID playerId = player.getUUID();
        INSPECTING_PLAYERS.remove(playerId);
        LAST_REPORTED_CHUNKS.remove(playerId);
    }

    public static void reportCurrentChunkIfChanged(ServerPlayer player) {
        reportChunk(player, player.serverLevel(), player.chunkPosition(), false);
    }

    public static void reportChunk(ServerPlayer player, ServerLevel level, ChunkPos chunkPos, boolean force) {
        if (!INSPECTING_PLAYERS.contains(player.getUUID())) {
            return;
        }

        InspectedChunk inspectedChunk = InspectedChunk.from(level.dimension().location(), chunkPos);
        if (!force && inspectedChunk.equals(LAST_REPORTED_CHUNKS.get(player.getUUID()))) {
            return;
        }

        LAST_REPORTED_CHUNKS.put(player.getUUID(), inspectedChunk);
        player.sendSystemMessage(buildInspectionMessage(CLAIM_MANAGER.getClaim(level, chunkPos), level, chunkPos));
    }

    private static Component buildInspectionMessage(ClaimData claim, ServerLevel level, ChunkPos chunkPos) {
        ResourceLocation dimensionId = level.dimension().location();

        if (claim == null) {
            return Component.translatable(
                    "command.chunklock.inspect.report.wilderness",
                    chunkPos.x,
                    chunkPos.z,
                    dimensionId
            );
        }

        return Component.translatable(
                "command.chunklock.inspect.report.claimed",
                claim.ownerLastKnownName(),
                chunkPos.x,
                chunkPos.z,
                dimensionId,
                formatClaimDate(claim.createdTimestamp())
        );
    }

    private static String formatClaimDate(long createdTimestamp) {
        if (createdTimestamp <= 0L) {
            return "unknown";
        }

        return CLAIM_DATE_FORMATTER.format(Instant.ofEpochMilli(createdTimestamp));
    }

    private record InspectedChunk(ResourceLocation dimension, int chunkX, int chunkZ) {
        private static InspectedChunk from(ResourceLocation dimension, ChunkPos chunkPos) {
            return new InspectedChunk(dimension, chunkPos.x, chunkPos.z);
        }
    }
}
