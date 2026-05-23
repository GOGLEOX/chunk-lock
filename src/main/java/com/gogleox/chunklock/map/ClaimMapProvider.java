package com.gogleox.chunklock.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;

public final class ClaimMapProvider {
    private static List<ClaimMapEntry> clientClaims = List.of();
    private static Map<ClaimChunkKey, ClaimMapEntry> clientClaimsByChunk = Map.of();

    public static Collection<ClaimMapEntry> getVisibleClaims() {
        return Collections.unmodifiableList(clientClaims);
    }

    public static Collection<ClaimMapEntry> getClaimsOwnedByCurrentPlayer() {
        return clientClaims.stream()
                .filter(ClaimMapEntry::ownedByCurrentPlayer)
                .toList();
    }

    public static Collection<ClaimMapEntry> getVisibleClaims(ResourceLocation dimension) {
        return clientClaims.stream()
                .filter(claim -> claim.dimension().equals(dimension))
                .toList();
    }

    public static Optional<ClaimMapEntry> getClaimAt(ResourceLocation dimension, int chunkX, int chunkZ) {
        return Optional.ofNullable(clientClaimsByChunk.get(new ClaimChunkKey(dimension, chunkX, chunkZ)));
    }

    public static void replaceClientClaims(Collection<ClaimMapEntry> claims) {
        List<ClaimMapEntry> copiedClaims = List.copyOf(new ArrayList<>(claims));
        Map<ClaimChunkKey, ClaimMapEntry> claimsByChunk = new HashMap<>(copiedClaims.size());

        for (ClaimMapEntry claim : copiedClaims) {
            claimsByChunk.put(new ClaimChunkKey(claim.dimension(), claim.chunkX(), claim.chunkZ()), claim);
        }

        clientClaims = copiedClaims;
        clientClaimsByChunk = Map.copyOf(claimsByChunk);
        refreshJourneyMap();
    }

    public static void clearClientClaims() {
        clientClaims = List.of();
        clientClaimsByChunk = Map.of();
        refreshJourneyMap();
    }

    private static void refreshJourneyMap() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClaimMapProvider::refreshJourneyMapClient);
    }

    private static void refreshJourneyMapClient() {
        if (ModList.get().isLoaded("journeymap")) {
            try {
                Class.forName("com.gogleox.chunklock.compat.journeymap.JourneyMapCompat")
                        .getMethod("refreshOverlays")
                        .invoke(null);
            } catch (ReflectiveOperationException ignored) {
                // JourneyMap classes are optional and client-only.
            }
        }
    }

    private ClaimMapProvider() {
    }

    private record ClaimChunkKey(ResourceLocation dimension, int chunkX, int chunkZ) {
    }
}
