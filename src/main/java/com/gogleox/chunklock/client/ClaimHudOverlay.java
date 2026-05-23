package com.gogleox.chunklock.client;

import com.gogleox.chunklock.ChunkLockMod;
import com.gogleox.chunklock.config.ChunkLockClientConfig;
import com.gogleox.chunklock.map.ClaimMapEntry;
import com.gogleox.chunklock.map.ClaimMapProvider;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChunkLockMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimHudOverlay {
    private static final int HUD_COLOR = 0xFFFFFF;
    private static final int DEFAULT_HUD_Y = 49;
    private static HudState lastObservedState;
    private static String visibleText = "";
    private static long hideAtGameTime = Long.MIN_VALUE;

    private ClaimHudOverlay() {
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())
                || !ChunkLockClientConfig.ENABLE_CLAIM_HUD.get()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null
                || minecraft.options.hideGui
                || minecraft.options.renderDebug
                || minecraft.screen != null
                || minecraft.getOverlay() != null) {
            return;
        }

        updateVisibleState(player);

        if (visibleText.isBlank() || player.level().getGameTime() >= hideAtGameTime) {
            return;
        }

        int x = event.getWindow().getGuiScaledWidth() / 2;
        int y = event.getWindow().getGuiScaledHeight() - DEFAULT_HUD_Y + ChunkLockClientConfig.HUD_Y_OFFSET.get();
        event.getGuiGraphics().drawCenteredString(minecraft.font, visibleText, x, y, HUD_COLOR);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClaimMapProvider.clearClientClaims();
        lastObservedState = null;
        visibleText = "";
        hideAtGameTime = Long.MIN_VALUE;
    }

    private static void updateVisibleState(LocalPlayer player) {
        HudState currentState = resolveHudState(player);

        if (currentState.equals(lastObservedState)) {
            return;
        }

        lastObservedState = currentState;
        visibleText = currentState.text();
        hideAtGameTime = player.level().getGameTime() + ChunkLockClientConfig.CLAIM_HUD_DISPLAY_TICKS.get();
    }

    private static HudState resolveHudState(LocalPlayer player) {
        ResourceLocation dimension = player.level().dimension().location();
        Optional<ClaimMapEntry> claim = ClaimMapProvider.getClaimAt(dimension, player.chunkPosition().x, player.chunkPosition().z);

        if (claim.isPresent()) {
            ClaimMapEntry currentClaim = claim.get();
            return new HudState(
                    HudStateKind.CLAIMED,
                    dimension,
                    currentClaim.ownerId().toString(),
                    ChunkLockClientConfig.CLAIMED_TEXT.get().replace("{owner}", currentClaim.ownerLastKnownName())
            );
        }

        if (!ChunkLockClientConfig.SHOW_WILDERNESS_TEXT.get()) {
            return new HudState(HudStateKind.WILDERNESS, dimension, "", "");
        }

        return new HudState(HudStateKind.WILDERNESS, dimension, "", ChunkLockClientConfig.WILDERNESS_TEXT.get());
    }

    private record HudState(HudStateKind kind, ResourceLocation dimension, String ownerKey, String text) {
    }

    private enum HudStateKind {
        WILDERNESS,
        CLAIMED
    }
}
