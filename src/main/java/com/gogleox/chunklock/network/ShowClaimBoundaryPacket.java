package com.gogleox.chunklock.network;

import com.gogleox.chunklock.show.ClaimBoundaryState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

public record ShowClaimBoundaryPacket(ResourceLocation dimension, List<ClaimBoundaryState.BoundarySegment> segments, int durationTicks) {
    public static void encode(ShowClaimBoundaryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(packet.dimension);
        buffer.writeVarInt(packet.segments.size());
        for (ClaimBoundaryState.BoundarySegment segment : packet.segments) {
            buffer.writeInt(segment.startX());
            buffer.writeInt(segment.startZ());
            buffer.writeInt(segment.endX());
            buffer.writeInt(segment.endZ());
        }
        buffer.writeVarInt(packet.durationTicks);
    }

    public static ShowClaimBoundaryPacket decode(FriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        int segmentCount = buffer.readVarInt();
        List<ClaimBoundaryState.BoundarySegment> segments = new ArrayList<>(segmentCount);

        for (int index = 0; index < segmentCount; index++) {
            segments.add(new ClaimBoundaryState.BoundarySegment(
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readInt()
            ));
        }

        return new ShowClaimBoundaryPacket(
                dimension,
                List.copyOf(segments),
                buffer.readVarInt()
        );
    }

    public static void handle(ShowClaimBoundaryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClaimBoundaryState.showPending(
                new ClaimBoundaryState.PendingBoundary(packet.dimension, packet.segments, packet.durationTicks)
        ));
        context.setPacketHandled(true);
    }
}
