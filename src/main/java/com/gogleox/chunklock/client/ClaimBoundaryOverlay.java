package com.gogleox.chunklock.client;

import com.gogleox.chunklock.ChunkLockMod;
import com.gogleox.chunklock.show.ClaimBoundaryState;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChunkLockMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimBoundaryOverlay {
    private static final double CHUNK_SIZE = 16.0D;
    private static final float RED = 0.20F;
    private static final float GREEN = 0.85F;
    private static final float BLUE = 1.00F;
    private static final float ALPHA = 1.00F;

    private ClaimBoundaryOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClaimBoundaryState.ActiveBoundary boundary = ClaimBoundaryState.activeBoundary();

        if (minecraft.level == null || boundary == null) {
            return;
        }

        ClaimBoundaryState.activate(minecraft.level.getGameTime());
        boundary = ClaimBoundaryState.activeBoundary();

        if (minecraft.level.getGameTime() >= boundary.expiresAtGameTime()) {
            ClaimBoundaryState.clear();
            return;
        }

        ResourceLocation currentDimension = minecraft.level.dimension().location();
        if (!currentDimension.equals(boundary.dimension())) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        double minY = minecraft.level.getMinBuildHeight();
        double maxY = minecraft.level.getMaxBuildHeight();
        VertexConsumer lines = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        for (ClaimBoundaryState.BoundarySegment segment : boundary.segments()) {
            AABB face = createFace(segment, minY, maxY)
                    .move(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
            LevelRenderer.renderLineBox(event.getPoseStack(), lines, face, RED, GREEN, BLUE, ALPHA);
        }

        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClaimBoundaryState.clear();
    }

    private static AABB createFace(ClaimBoundaryState.BoundarySegment segment, double minY, double maxY) {
        return new AABB(
                Math.min(segment.startX(), segment.endX()),
                minY,
                Math.min(segment.startZ(), segment.endZ()),
                Math.max(segment.startX(), segment.endX()),
                maxY,
                Math.max(segment.startZ(), segment.endZ())
        );
    }
}
