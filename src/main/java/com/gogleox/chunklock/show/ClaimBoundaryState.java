package com.gogleox.chunklock.show;

import net.minecraft.resources.ResourceLocation;
import java.util.List;

public final class ClaimBoundaryState {
    private static ActiveBoundary activeBoundary;

    private ClaimBoundaryState() {
    }

    public static void showPending(PendingBoundary pendingBoundary) {
        activeBoundary = new ActiveBoundary(pendingBoundary.dimension(), pendingBoundary.segments(), pendingBoundary.durationTicks(), -1L);
    }

    public static void activate(long currentGameTime) {
        if (activeBoundary == null || activeBoundary.expiresAtGameTime() >= 0L) {
            return;
        }

        activeBoundary = new ActiveBoundary(
                activeBoundary.dimension(),
                activeBoundary.segments(),
                activeBoundary.durationTicks(),
                currentGameTime + activeBoundary.durationTicks()
        );
    }

    public static ActiveBoundary activeBoundary() {
        return activeBoundary;
    }

    public static void clear() {
        activeBoundary = null;
    }

    public record PendingBoundary(ResourceLocation dimension, List<BoundarySegment> segments, int durationTicks) {
    }

    public record ActiveBoundary(ResourceLocation dimension, List<BoundarySegment> segments, int durationTicks, long expiresAtGameTime) {
    }

    public record BoundarySegment(int startX, int startZ, int endX, int endZ) {
    }
}
