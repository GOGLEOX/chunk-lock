package com.gogleox.chunklock.claim;

import com.gogleox.chunklock.ChunkLockMod;
import com.gogleox.chunklock.config.ChunkLockConfig;
import com.gogleox.chunklock.network.ChunkLockNetwork;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public final class ClaimManager {
    public enum ClaimResult {
        CLAIMED,
        ALREADY_CLAIMED,
        CLAIM_LIMIT_REACHED,
        DIMENSION_DISABLED,
        DISABLED_BY_FTB_CHUNKS
    }

    public ClaimData getClaim(ServerLevel level, ChunkPos pos) {
        return savedData(level).getClaim(dimensionId(level), pos);
    }

    public boolean isClaimed(ServerLevel level, ChunkPos pos) {
        return savedData(level).isClaimed(dimensionId(level), pos);
    }

    public boolean isOwner(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        ClaimData claim = getClaim(level, pos);
        return claim != null && claim.ownerId().equals(player.getUUID());
    }

    public boolean claimChunk(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        return claimChunkWithResult(player, level, pos) == ClaimResult.CLAIMED;
    }

    public ClaimResult claimChunkWithResult(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        if (ChunkLockConfig.isDisabledByFtbChunks()) {
            return ClaimResult.DISABLED_BY_FTB_CHUNKS;
        }

        if (!ChunkLockConfig.isClaimingAllowedIn(level)) {
            return ClaimResult.DIMENSION_DISABLED;
        }

        ClaimSavedData claims = savedData(level);
        ResourceLocation dimensionId = dimensionId(level);

        if (claims.isClaimed(dimensionId, pos)) {
            return ClaimResult.ALREADY_CLAIMED;
        }

        if (claims.getClaimCount(player.getUUID()) >= ChunkLockConfig.MAX_CLAIMS_PER_PLAYER.get()) {
            return ClaimResult.CLAIM_LIMIT_REACHED;
        }

        ClaimData claim = new ClaimData(
                player.getUUID(),
                player.getGameProfile().getName(),
                dimensionId,
                pos.x,
                pos.z,
                System.currentTimeMillis()
        );

        boolean stored = claims.putClaim(claim);
        ChunkLockMod.LOGGER.debug(
                "Claim request for {} at chunk {}, {} by {} stored={}",
                dimensionId,
                pos.x,
                pos.z,
                player.getGameProfile().getName(),
                stored
        );
        if (stored) {
            ChunkLockNetwork.syncAll(level.getServer());
            return ClaimResult.CLAIMED;
        }

        return ClaimResult.ALREADY_CLAIMED;
    }

    public boolean unclaimChunk(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        if (!isOwner(player, level, pos)) {
            return false;
        }

        boolean removed = savedData(level).removeClaim(dimensionId(level), pos);
        ChunkLockMod.LOGGER.debug(
                "Unclaim request for {} at chunk {}, {} by {} removed={}",
                dimensionId(level),
                pos.x,
                pos.z,
                player.getGameProfile().getName(),
                removed
        );
        if (removed) {
            ChunkLockNetwork.syncAll(level.getServer());
        }

        return removed;
    }

    public boolean removeClaim(ServerLevel level, ChunkPos pos) {
        boolean removed = savedData(level).removeClaim(dimensionId(level), pos);
        ChunkLockMod.LOGGER.debug(
                "Admin claim removal for {} at chunk {}, {} removed={}",
                dimensionId(level),
                pos.x,
                pos.z,
                removed
        );
        if (removed) {
            ChunkLockNetwork.syncAll(level.getServer());
        }

        return removed;
    }

    public Collection<ClaimData> getClaimsForPlayer(UUID playerId) {
        return savedData().getClaimsForPlayer(playerId);
    }

    public Collection<ClaimData> getAllClaims(ServerLevel level) {
        return savedData(level).getAllClaims();
    }

    public int getClaimCount(UUID playerId) {
        return savedData().getClaimCount(playerId);
    }

    public int removeAllClaimsForPlayer(UUID playerId) {
        int removed = savedData().removeAllClaimsForPlayer(playerId);

        if (removed > 0 && net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            ChunkLockNetwork.syncAll(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        }

        return removed;
    }

    private ClaimSavedData savedData(ServerLevel level) {
        return ClaimSavedData.get(level);
    }

    private ClaimSavedData savedData() {
        ServerLevel level = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
        return savedData(level);
    }

    private ResourceLocation dimensionId(ServerLevel level) {
        return level.dimension().location();
    }
}
