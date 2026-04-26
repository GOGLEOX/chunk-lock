package com.gogleox.chunklock.map;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;

public final class ClaimMapProvider {
    private static List<ClaimMapEntry> clientClaims = List.of();

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

    public static void replaceClientClaims(Collection<ClaimMapEntry> claims) {
        clientClaims = List.copyOf(new ArrayList<>(claims));
        refreshJourneyMap();
    }

    public static void clearClientClaims() {
        clientClaims = List.of();
        refreshJourneyMap();
    }

    private static void refreshJourneyMap() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClaimMapProvider::refreshJourneyMapClient);
    }

    private static void refreshJourneyMapClient() {
        if (ModList.get().isLoaded("journeymap")) {
            com.gogleox.chunklock.compat.journeymap.JourneyMapCompat.refreshOverlays();
        }
    }

    private ClaimMapProvider() {
    }
}
