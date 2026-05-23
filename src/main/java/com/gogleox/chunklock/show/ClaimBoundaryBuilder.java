package com.gogleox.chunklock.show;

import com.gogleox.chunklock.claim.ClaimData;
import com.gogleox.chunklock.claim.ClaimManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class ClaimBoundaryBuilder {
    private static final int CHUNK_SIZE = 16;
    private static final ClaimManager CLAIM_MANAGER = new ClaimManager();

    private ClaimBoundaryBuilder() {
    }

    public static ClaimBoundaryState.PendingBoundary build(ServerLevel level, ChunkPos origin, int durationTicks, int maxChunks) {
        ClaimData originClaim = CLAIM_MANAGER.getClaim(level, origin);
        ResourceLocation dimension = level.dimension().location();

        if (originClaim == null) {
            return new ClaimBoundaryState.PendingBoundary(
                    dimension,
                    List.of(segment(origin.x * CHUNK_SIZE, origin.z * CHUNK_SIZE, (origin.x + 1) * CHUNK_SIZE, origin.z * CHUNK_SIZE),
                            segment((origin.x + 1) * CHUNK_SIZE, origin.z * CHUNK_SIZE, (origin.x + 1) * CHUNK_SIZE, (origin.z + 1) * CHUNK_SIZE),
                            segment((origin.x + 1) * CHUNK_SIZE, (origin.z + 1) * CHUNK_SIZE, origin.x * CHUNK_SIZE, (origin.z + 1) * CHUNK_SIZE),
                            segment(origin.x * CHUNK_SIZE, (origin.z + 1) * CHUNK_SIZE, origin.x * CHUNK_SIZE, origin.z * CHUNK_SIZE)),
                    durationTicks
            );
        }

        Set<ChunkPos> connectedChunks = collectConnectedChunks(level, origin, originClaim.ownerId(), maxChunks);
        List<ClaimBoundaryState.BoundarySegment> segments = new ArrayList<>();

        for (ChunkPos chunk : connectedChunks) {
            if (!connectedChunks.contains(new ChunkPos(chunk.x, chunk.z - 1))) {
                segments.add(segment(chunk.x * CHUNK_SIZE, chunk.z * CHUNK_SIZE, (chunk.x + 1) * CHUNK_SIZE, chunk.z * CHUNK_SIZE));
            }
            if (!connectedChunks.contains(new ChunkPos(chunk.x + 1, chunk.z))) {
                segments.add(segment((chunk.x + 1) * CHUNK_SIZE, chunk.z * CHUNK_SIZE, (chunk.x + 1) * CHUNK_SIZE, (chunk.z + 1) * CHUNK_SIZE));
            }
            if (!connectedChunks.contains(new ChunkPos(chunk.x, chunk.z + 1))) {
                segments.add(segment((chunk.x + 1) * CHUNK_SIZE, (chunk.z + 1) * CHUNK_SIZE, chunk.x * CHUNK_SIZE, (chunk.z + 1) * CHUNK_SIZE));
            }
            if (!connectedChunks.contains(new ChunkPos(chunk.x - 1, chunk.z))) {
                segments.add(segment(chunk.x * CHUNK_SIZE, (chunk.z + 1) * CHUNK_SIZE, chunk.x * CHUNK_SIZE, chunk.z * CHUNK_SIZE));
            }
        }

        return new ClaimBoundaryState.PendingBoundary(dimension, List.copyOf(segments), durationTicks);
    }

    private static Set<ChunkPos> collectConnectedChunks(ServerLevel level, ChunkPos origin, UUID ownerId, int maxChunks) {
        Set<ChunkPos> connected = new HashSet<>();
        Set<ChunkPos> queued = new HashSet<>();
        ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
        queue.add(origin);
        queued.add(origin);

        while (!queue.isEmpty() && connected.size() < maxChunks) {
            ChunkPos current = queue.removeFirst();

            if (!connected.add(current)) {
                continue;
            }

            for (ChunkPos neighbor : neighbors(current)) {
                if (queued.contains(neighbor)) {
                    continue;
                }

                ClaimData claim = CLAIM_MANAGER.getClaim(level, neighbor);
                if (claim != null && claim.ownerId().equals(ownerId)) {
                    queue.addLast(neighbor);
                    queued.add(neighbor);
                }
            }
        }

        return connected;
    }

    private static List<ChunkPos> neighbors(ChunkPos chunkPos) {
        return List.of(
                new ChunkPos(chunkPos.x, chunkPos.z - 1),
                new ChunkPos(chunkPos.x + 1, chunkPos.z),
                new ChunkPos(chunkPos.x, chunkPos.z + 1),
                new ChunkPos(chunkPos.x - 1, chunkPos.z)
        );
    }

    private static ClaimBoundaryState.BoundarySegment segment(int startX, int startZ, int endX, int endZ) {
        return new ClaimBoundaryState.BoundarySegment(startX, startZ, endX, endZ);
    }
}
