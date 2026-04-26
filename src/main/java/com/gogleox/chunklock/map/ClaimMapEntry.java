package com.gogleox.chunklock.map;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public record ClaimMapEntry(
        ResourceLocation dimension,
        int chunkX,
        int chunkZ,
        UUID ownerId,
        String ownerLastKnownName,
        boolean ownedByCurrentPlayer
) {
}
