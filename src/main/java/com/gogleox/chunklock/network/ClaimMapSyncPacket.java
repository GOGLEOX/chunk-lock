package com.gogleox.chunklock.network;

import com.gogleox.chunklock.map.ClaimMapEntry;
import com.gogleox.chunklock.map.ClaimMapProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

public record ClaimMapSyncPacket(List<ClaimMapEntry> claims) {
    private static final int MAX_OWNER_NAME_LENGTH = 64;
    private static final int MAX_SYNCED_CLAIMS = 10_000;

    public static void encode(ClaimMapSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.claims.size());

        for (ClaimMapEntry claim : packet.claims) {
            buffer.writeResourceLocation(claim.dimension());
            buffer.writeInt(claim.chunkX());
            buffer.writeInt(claim.chunkZ());
            buffer.writeUUID(claim.ownerId());
            buffer.writeUtf(claim.ownerLastKnownName(), MAX_OWNER_NAME_LENGTH);
            buffer.writeBoolean(claim.ownedByCurrentPlayer());
        }
    }

    public static ClaimMapSyncPacket decode(FriendlyByteBuf buffer) {
        int claimCount = Math.min(buffer.readVarInt(), MAX_SYNCED_CLAIMS);
        List<ClaimMapEntry> claims = new ArrayList<>(claimCount);

        for (int index = 0; index < claimCount; index++) {
            ResourceLocation dimension = buffer.readResourceLocation();
            int chunkX = buffer.readInt();
            int chunkZ = buffer.readInt();
            java.util.UUID ownerId = buffer.readUUID();
            String ownerLastKnownName = buffer.readUtf(MAX_OWNER_NAME_LENGTH);
            boolean ownedByCurrentPlayer = buffer.readBoolean();

            claims.add(new ClaimMapEntry(dimension, chunkX, chunkZ, ownerId, ownerLastKnownName, ownedByCurrentPlayer));
        }

        return new ClaimMapSyncPacket(claims);
    }

    public static void handle(ClaimMapSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClaimMapProvider.replaceClientClaims(packet.claims));
        context.setPacketHandled(true);
    }
}
