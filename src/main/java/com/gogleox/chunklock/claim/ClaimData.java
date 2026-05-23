package com.gogleox.chunklock.claim;

import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class ClaimData {
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final String OWNER_NAME_TAG = "OwnerLastKnownName";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String CHUNK_X_TAG = "ChunkX";
    private static final String CHUNK_Z_TAG = "ChunkZ";
    private static final String CREATED_AT_TAG = "CreatedAt";
    private static final String TRUSTED_PLAYERS_TAG = "TrustedPlayers";
    private static final String BLOCKED_PLAYERS_TAG = "BlockedPlayers";
    private static final String ACCESS_ID_TAG = "Id";
    private static final String ACCESS_NAME_TAG = "Name";

    private final UUID ownerId;
    private final String ownerLastKnownName;
    private final ResourceLocation dimension;
    private final int chunkX;
    private final int chunkZ;
    private final long createdTimestamp;
    private final Map<UUID, String> trustedPlayers;
    private final Map<UUID, String> blockedPlayers;

    public ClaimData(
            UUID ownerId,
            String ownerLastKnownName,
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            long createdTimestamp
    ) {
        this(ownerId, ownerLastKnownName, dimension, chunkX, chunkZ, createdTimestamp, Map.of(), Map.of());
    }

    public ClaimData(
            UUID ownerId,
            String ownerLastKnownName,
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            long createdTimestamp,
            Map<UUID, String> trustedPlayers,
            Map<UUID, String> blockedPlayers
    ) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ownerLastKnownName = Objects.requireNonNull(ownerLastKnownName, "ownerLastKnownName");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.createdTimestamp = createdTimestamp;
        this.trustedPlayers = new LinkedHashMap<>(Objects.requireNonNull(trustedPlayers, "trustedPlayers"));
        this.blockedPlayers = new LinkedHashMap<>(Objects.requireNonNull(blockedPlayers, "blockedPlayers"));
    }

    public static ClaimData load(CompoundTag tag) {
        return new ClaimData(
                tag.getUUID(OWNER_ID_TAG),
                tag.getString(OWNER_NAME_TAG),
                new ResourceLocation(tag.getString(DIMENSION_TAG)),
                tag.getInt(CHUNK_X_TAG),
                tag.getInt(CHUNK_Z_TAG),
                tag.getLong(CREATED_AT_TAG),
                loadAccessList(tag.getList(TRUSTED_PLAYERS_TAG, Tag.TAG_COMPOUND)),
                loadAccessList(tag.getList(BLOCKED_PLAYERS_TAG, Tag.TAG_COMPOUND))
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(OWNER_ID_TAG, ownerId);
        tag.putString(OWNER_NAME_TAG, ownerLastKnownName);
        tag.putString(DIMENSION_TAG, dimension.toString());
        tag.putInt(CHUNK_X_TAG, chunkX);
        tag.putInt(CHUNK_Z_TAG, chunkZ);
        tag.putLong(CREATED_AT_TAG, createdTimestamp);
        tag.put(TRUSTED_PLAYERS_TAG, saveAccessList(trustedPlayers));
        tag.put(BLOCKED_PLAYERS_TAG, saveAccessList(blockedPlayers));
        return tag;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerLastKnownName() {
        return ownerLastKnownName;
    }

    public ResourceLocation dimension() {
        return dimension;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    public long createdTimestamp() {
        return createdTimestamp;
    }

    public Map<UUID, String> trustedPlayers() {
        return Map.copyOf(trustedPlayers);
    }

    public Map<UUID, String> blockedPlayers() {
        return Map.copyOf(blockedPlayers);
    }

    public boolean isTrusted(UUID playerId) {
        return trustedPlayers.containsKey(playerId);
    }

    public boolean isBlocked(UUID playerId) {
        return blockedPlayers.containsKey(playerId);
    }

    public ClaimData withTrustedPlayer(UUID playerId, String playerName) {
        Map<UUID, String> trusted = new LinkedHashMap<>(trustedPlayers);
        Map<UUID, String> blocked = new LinkedHashMap<>(blockedPlayers);
        trusted.put(playerId, playerName);
        blocked.remove(playerId);
        return copyWith(trusted, blocked);
    }

    public ClaimData withoutTrustedPlayer(UUID playerId) {
        Map<UUID, String> trusted = new LinkedHashMap<>(trustedPlayers);
        trusted.remove(playerId);
        return copyWith(trusted, blockedPlayers);
    }

    public ClaimData withBlockedPlayer(UUID playerId, String playerName) {
        Map<UUID, String> trusted = new LinkedHashMap<>(trustedPlayers);
        Map<UUID, String> blocked = new LinkedHashMap<>(blockedPlayers);
        blocked.put(playerId, playerName);
        trusted.remove(playerId);
        return copyWith(trusted, blocked);
    }

    public ClaimData withoutBlockedPlayer(UUID playerId) {
        Map<UUID, String> blocked = new LinkedHashMap<>(blockedPlayers);
        blocked.remove(playerId);
        return copyWith(trustedPlayers, blocked);
    }

    public ClaimData withOwner(UUID newOwnerId, String newOwnerName) {
        Map<UUID, String> trusted = new LinkedHashMap<>(trustedPlayers);
        Map<UUID, String> blocked = new LinkedHashMap<>(blockedPlayers);
        trusted.remove(newOwnerId);
        blocked.remove(newOwnerId);
        return new ClaimData(newOwnerId, newOwnerName, dimension, chunkX, chunkZ, createdTimestamp, trusted, blocked);
    }

    private ClaimData copyWith(Map<UUID, String> trusted, Map<UUID, String> blocked) {
        return new ClaimData(ownerId, ownerLastKnownName, dimension, chunkX, chunkZ, createdTimestamp, trusted, blocked);
    }

    private static Map<UUID, String> loadAccessList(ListTag tags) {
        Map<UUID, String> players = new LinkedHashMap<>();

        for (int index = 0; index < tags.size(); index++) {
            CompoundTag entry = tags.getCompound(index);
            if (entry.hasUUID(ACCESS_ID_TAG)) {
                players.put(entry.getUUID(ACCESS_ID_TAG), entry.getString(ACCESS_NAME_TAG));
            }
        }

        return players;
    }

    private static ListTag saveAccessList(Map<UUID, String> players) {
        ListTag tags = new ListTag();

        for (Map.Entry<UUID, String> player : players.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(ACCESS_ID_TAG, player.getKey());
            entry.putString(ACCESS_NAME_TAG, player.getValue());
            tags.add(entry);
        }

        return tags;
    }
}
