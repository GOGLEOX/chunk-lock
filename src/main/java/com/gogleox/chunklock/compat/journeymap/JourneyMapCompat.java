package com.gogleox.chunklock.compat.journeymap;

import com.gogleox.chunklock.ChunkLockMod;
import com.gogleox.chunklock.config.ChunkLockConfig;
import com.gogleox.chunklock.map.ClaimMapEntry;
import com.gogleox.chunklock.map.ClaimMapProvider;
import java.util.EnumSet;
import journeymap.client.api.ClientPlugin;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.display.PolygonOverlay;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.model.MapPolygon;
import journeymap.client.api.model.ShapeProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

@OnlyIn(Dist.CLIENT)
@ClientPlugin
public final class JourneyMapCompat implements IClientPlugin {
    private static final String OVERLAY_GROUP = "ChunkLock Claims";
    private static final int OWNER_COLOR = 0x22aa44;
    private static final int OTHER_COLOR = 0xcc3333;

    private static JourneyMapCompat instance;

    private IClientAPI api;

    public JourneyMapCompat() {
        instance = this;
    }

    @Override
    public void initialize(IClientAPI jmAPI) {
        this.api = jmAPI;
        this.api.subscribe(getModId(), EnumSet.of(ClientEvent.Type.MAPPING_STARTED, ClientEvent.Type.MAPPING_STOPPED));
        ChunkLockMod.LOGGER.info("JourneyMap integration initialized for ChunkLock");
    }

    @Override
    public String getModId() {
        return ChunkLockMod.MOD_ID;
    }

    @Override
    public void onEvent(ClientEvent event) {
        if (event.type == ClientEvent.Type.MAPPING_STOPPED) {
            removeOverlays();
            return;
        }

        if (event.type == ClientEvent.Type.MAPPING_STARTED) {
            refreshOverlays();
        }
    }

    public static void refreshOverlays() {
        if (instance == null || instance.api == null || !isEnabled()) {
            return;
        }

        instance.rebuildOverlays();
    }

    private static boolean isEnabled() {
        return ModList.get().isLoaded("journeymap") && ChunkLockConfig.ENABLE_JOURNEYMAP_OVERLAY.get();
    }

    private void rebuildOverlays() {
        if (!api.playerAccepts(ChunkLockMod.MOD_ID, DisplayType.Polygon)) {
            return;
        }

        removeOverlays();

        for (ClaimMapEntry claim : ClaimMapProvider.getVisibleClaims()) {
            if (!claim.ownedByCurrentPlayer() && !ChunkLockConfig.SHOW_OTHER_PLAYER_CLAIMS.get()) {
                continue;
            }

            try {
                api.show(createOverlay(claim));
            } catch (Exception exception) {
                ChunkLockMod.LOGGER.warn("Unable to show JourneyMap claim overlay for chunk {}, {}", claim.chunkX(), claim.chunkZ(), exception);
            }
        }
    }

    private void removeOverlays() {
        if (api != null) {
            api.removeAll(ChunkLockMod.MOD_ID, DisplayType.Polygon);
        }
    }

    private PolygonOverlay createOverlay(ClaimMapEntry claim) {
        int minX = claim.chunkX() << 4;
        int minZ = claim.chunkZ() << 4;
        int maxX = minX + 16;
        int maxZ = minZ + 16;
        int color = claim.ownedByCurrentPlayer() ? OWNER_COLOR : OTHER_COLOR;
        String ownerText = "Claimed by: " + claim.ownerLastKnownName();

        ShapeProperties properties = new ShapeProperties()
                .setStrokeColor(color)
                .setFillColor(color)
                .setStrokeOpacity(0.9F)
                .setFillOpacity(0.25F)
                .setStrokeWidth(2.0F);

        MapPolygon polygon = new MapPolygon(
                new BlockPos(maxX, 64, maxZ),
                new BlockPos(maxX, 64, minZ),
                new BlockPos(minX, 64, minZ),
                new BlockPos(minX, 64, maxZ)
        );

        PolygonOverlay overlay = new PolygonOverlay(
                ChunkLockMod.MOD_ID,
                displayId(claim),
                ResourceKey.create(Registries.DIMENSION, claim.dimension()),
                properties,
                polygon
        );
        overlay.setOverlayGroupName(OVERLAY_GROUP);
        overlay.setTitle(ownerText);
        overlay.setDisplayOrder(900);

        if (ChunkLockConfig.SHOW_CLAIM_OWNER_NAMES.get()) {
            overlay.setLabel(ownerText);
        }

        return overlay;
    }

    private static String displayId(ClaimMapEntry claim) {
        return claim.dimension().toString().replace(':', '_') + "_" + claim.chunkX() + "_" + claim.chunkZ();
    }
}
