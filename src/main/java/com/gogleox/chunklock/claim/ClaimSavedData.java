package com.gogleox.chunklock.claim;

import com.gogleox.chunklock.ChunkLockMod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

public final class ClaimSavedData extends SavedData {
    public static final String DATA_NAME = ChunkLockMod.MOD_ID + "_claims";

    private static final String CLAIMS_TAG = "Claims";

    private final Map<ClaimKey, ClaimData> claims = new LinkedHashMap<>();

    public static ClaimSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                ClaimSavedData::load,
                ClaimSavedData::new,
                DATA_NAME
        );
    }

    public static ClaimSavedData load(CompoundTag tag) {
        ClaimSavedData data = new ClaimSavedData();
        ListTag claimTags = tag.getList(CLAIMS_TAG, Tag.TAG_COMPOUND);

        for (int index = 0; index < claimTags.size(); index++) {
            ClaimData claim = ClaimData.load(claimTags.getCompound(index));
            data.claims.put(ClaimKey.from(claim), claim);
        }

        ChunkLockMod.LOGGER.info("Loaded {} ChunkLock claims from saved data", data.claims.size());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag claimTags = new ListTag();

        for (ClaimData claim : claims.values()) {
            claimTags.add(claim.save());
        }

        tag.put(CLAIMS_TAG, claimTags);
        ChunkLockMod.LOGGER.info("Saved {} ChunkLock claims to saved data", claims.size());
        return tag;
    }

    public ClaimData getClaim(ResourceLocation dimension, ChunkPos pos) {
        return claims.get(ClaimKey.from(dimension, pos));
    }

    public boolean isClaimed(ResourceLocation dimension, ChunkPos pos) {
        return claims.containsKey(ClaimKey.from(dimension, pos));
    }

    public boolean putClaim(ClaimData claim) {
        ClaimKey key = ClaimKey.from(claim);

        if (claims.containsKey(key)) {
            return false;
        }

        claims.put(key, claim);
        setDirty();
        ChunkLockMod.LOGGER.debug("Stored claim for {} at chunk {}, {}", claim.dimension(), claim.chunkX(), claim.chunkZ());
        return true;
    }

    public boolean replaceClaim(ClaimData claim) {
        ClaimKey key = ClaimKey.from(claim);

        if (!claims.containsKey(key)) {
            return false;
        }

        claims.put(key, claim);
        setDirty();
        ChunkLockMod.LOGGER.debug("Updated claim for {} at chunk {}, {}", claim.dimension(), claim.chunkX(), claim.chunkZ());
        return true;
    }

    public boolean removeClaim(ResourceLocation dimension, ChunkPos pos) {
        ClaimData removed = claims.remove(ClaimKey.from(dimension, pos));

        if (removed == null) {
            return false;
        }

        setDirty();
        ChunkLockMod.LOGGER.debug("Removed claim for {} at chunk {}, {}", dimension, pos.x, pos.z);
        return true;
    }

    public Collection<ClaimData> getClaimsForPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        List<ClaimData> playerClaims = claims.values().stream()
                .filter(claim -> claim.ownerId().equals(playerId))
                .collect(Collectors.toCollection(ArrayList::new));
        return Collections.unmodifiableList(playerClaims);
    }

    public Collection<ClaimData> getAllClaims() {
        return Collections.unmodifiableCollection(new ArrayList<>(claims.values()));
    }

    public Collection<ClaimData> getClaimsInRange(ResourceLocation dimension, ChunkPos center, int radius) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(center, "center");

        List<ClaimData> nearbyClaims = claims.values().stream()
                .filter(claim -> claim.dimension().equals(dimension))
                .filter(claim -> Math.abs(claim.chunkX() - center.x) <= radius)
                .filter(claim -> Math.abs(claim.chunkZ() - center.z) <= radius)
                .collect(Collectors.toCollection(ArrayList::new));
        return Collections.unmodifiableList(nearbyClaims);
    }

    public int getClaimCount(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        int count = 0;

        for (ClaimData claim : claims.values()) {
            if (claim.ownerId().equals(playerId)) {
                count++;
            }
        }

        return count;
    }

    public int removeAllClaimsForPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        int initialSize = claims.size();
        claims.entrySet().removeIf(entry -> entry.getValue().ownerId().equals(playerId));
        int removed = initialSize - claims.size();

        if (removed > 0) {
            setDirty();
            ChunkLockMod.LOGGER.debug("Removed {} ChunkLock claims for player {}", removed, playerId);
        }

        return removed;
    }

    public int size() {
        return claims.size();
    }

    private record ClaimKey(ResourceLocation dimension, int chunkX, int chunkZ) {
        private ClaimKey {
            Objects.requireNonNull(dimension, "dimension");
        }

        private static ClaimKey from(ClaimData claim) {
            return new ClaimKey(claim.dimension(), claim.chunkX(), claim.chunkZ());
        }

        private static ClaimKey from(ResourceLocation dimension, ChunkPos pos) {
            return new ClaimKey(dimension, pos.x, pos.z);
        }
    }
}
