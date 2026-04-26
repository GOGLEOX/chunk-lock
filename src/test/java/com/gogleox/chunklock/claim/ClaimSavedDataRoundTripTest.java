package com.gogleox.chunklock.claim;

import com.gogleox.chunklock.map.ClaimMapEntry;
import com.gogleox.chunklock.map.ClaimMapProvider;
import com.gogleox.chunklock.network.ClaimMapSyncPacket;
import io.netty.buffer.Unpooled;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

public final class ClaimSavedDataRoundTripTest {
    private ClaimSavedDataRoundTripTest() {
    }

    public static void main(String[] args) {
        UUID ownerId = UUID.fromString("8f8a7f80-0c5b-4c49-9a42-7d42fd69c172");
        ChunkPos sharedPosition = new ChunkPos(12, -4);

        ClaimSavedData original = new ClaimSavedData();
        putClaim(original, ownerId, "Dev", new ResourceLocation("minecraft", "overworld"), sharedPosition);
        putClaim(original, ownerId, "Dev", new ResourceLocation("minecraft", "the_nether"), sharedPosition);
        putClaim(original, ownerId, "Dev", new ResourceLocation("minecraft", "the_end"), sharedPosition);

        CompoundTag savedTag = original.save(new CompoundTag());
        ClaimSavedData loaded = ClaimSavedData.load(savedTag);

        require(loaded.size() == 3, "Expected three claims after reload");
        require(loaded.isClaimed(new ResourceLocation("minecraft", "overworld"), sharedPosition), "Missing Overworld claim");
        require(loaded.isClaimed(new ResourceLocation("minecraft", "the_nether"), sharedPosition), "Missing Nether claim");
        require(loaded.isClaimed(new ResourceLocation("minecraft", "the_end"), sharedPosition), "Missing End claim");
        require(loaded.getClaimCount(ownerId) == 3, "Expected all claims to belong to the owner");

        Collection<ClaimData> playerClaims = loaded.getClaimsForPlayer(ownerId);
        require(playerClaims.size() == 3, "Expected player claim query to return three claims");

        int removed = loaded.removeAllClaimsForPlayer(ownerId);
        require(removed == 3, "Expected all player claims to be removed");
        require(loaded.size() == 0, "Expected no claims after removal");

        validateMapSyncPacket(ownerId);

        System.out.println("Claim saved-data round-trip validation passed.");
    }

    private static void putClaim(ClaimSavedData data, UUID ownerId, String ownerName, ResourceLocation dimension, ChunkPos pos) {
        boolean stored = data.putClaim(new ClaimData(ownerId, ownerName, dimension, pos.x, pos.z, 1_775_000_000_000L));
        require(stored, "Expected claim to be stored for " + dimension);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void validateMapSyncPacket(UUID ownerId) {
        ClaimMapEntry entry = new ClaimMapEntry(
                new ResourceLocation("minecraft", "overworld"),
                12,
                -4,
                ownerId,
                "Dev",
                true
        );
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        ClaimMapSyncPacket.encode(new ClaimMapSyncPacket(List.of(entry)), buffer);
        ClaimMapSyncPacket decoded = ClaimMapSyncPacket.decode(buffer);

        require(decoded.claims().size() == 1, "Expected one decoded map claim");
        ClaimMapProvider.replaceClientClaims(decoded.claims());
        require(ClaimMapProvider.getVisibleClaims().size() == 1, "Expected one visible client claim");
        require(ClaimMapProvider.getClaimsOwnedByCurrentPlayer().size() == 1, "Expected one owned client claim");
    }
}
