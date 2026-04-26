package com.gogleox.chunklock.claim;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public final class ClaimData {
    private static final String OWNER_ID_TAG = "OwnerId";
    private static final String OWNER_NAME_TAG = "OwnerLastKnownName";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String CHUNK_X_TAG = "ChunkX";
    private static final String CHUNK_Z_TAG = "ChunkZ";
    private static final String CREATED_AT_TAG = "CreatedAt";

    private final UUID ownerId;
    private final String ownerLastKnownName;
    private final ResourceLocation dimension;
    private final int chunkX;
    private final int chunkZ;
    private final long createdTimestamp;

    public ClaimData(
            UUID ownerId,
            String ownerLastKnownName,
            ResourceLocation dimension,
            int chunkX,
            int chunkZ,
            long createdTimestamp
    ) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ownerLastKnownName = Objects.requireNonNull(ownerLastKnownName, "ownerLastKnownName");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.createdTimestamp = createdTimestamp;
    }

    public static ClaimData load(CompoundTag tag) {
        return new ClaimData(
                tag.getUUID(OWNER_ID_TAG),
                tag.getString(OWNER_NAME_TAG),
                new ResourceLocation(tag.getString(DIMENSION_TAG)),
                tag.getInt(CHUNK_X_TAG),
                tag.getInt(CHUNK_Z_TAG),
                tag.getLong(CREATED_AT_TAG)
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
}
