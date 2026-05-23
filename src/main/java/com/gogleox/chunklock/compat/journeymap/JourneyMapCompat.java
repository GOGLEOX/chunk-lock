package com.gogleox.chunklock.compat.journeymap;

import com.gogleox.chunklock.ChunkLockMod;
import com.gogleox.chunklock.config.ChunkLockConfig;
import com.gogleox.chunklock.map.ClaimMapEntry;
import com.gogleox.chunklock.map.ClaimMapProvider;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import net.minecraft.resources.ResourceLocation;
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

        int displayIndex = 0;
        for (ClaimRegion region : buildRegions(ClaimMapProvider.getVisibleClaims())) {
            try {
                api.show(createOverlay(region, displayIndex++));
            } catch (Exception exception) {
                ChunkLockMod.LOGGER.warn("Unable to show JourneyMap claim overlay for {}", region.ownerName(), exception);
            }
        }
    }

    private void removeOverlays() {
        if (api != null) {
            api.removeAll(ChunkLockMod.MOD_ID, DisplayType.Polygon);
        }
    }

    private PolygonOverlay createOverlay(ClaimRegion region, int displayIndex) {
        int color = region.ownedByCurrentPlayer() ? OWNER_COLOR : OTHER_COLOR;
        String ownerText = "Claimed by: " + region.ownerName();

        ShapeProperties properties = new ShapeProperties()
                .setStrokeColor(color)
                .setFillColor(color)
                .setStrokeOpacity(0.75F)
                .setFillOpacity(0.18F)
                .setStrokeWidth(1.0F);

        PolygonOverlay overlay = new PolygonOverlay(
                ChunkLockMod.MOD_ID,
                displayId(region, displayIndex),
                ResourceKey.create(Registries.DIMENSION, region.dimension()),
                properties,
                new MapPolygon(region.outline())
        );
        overlay.setOverlayGroupName(OVERLAY_GROUP);
        overlay.setTitle(ownerText);
        overlay.setDisplayOrder(100);

        if (ChunkLockConfig.SHOW_CLAIM_OWNER_NAMES.get()) {
            overlay.setLabel(ownerText);
        }

        return overlay;
    }

    private static List<ClaimRegion> buildRegions(Collection<ClaimMapEntry> claims) {
        Map<RegionKey, Set<Cell>> cellsByOwner = new HashMap<>();

        for (ClaimMapEntry claim : claims) {
            if (!claim.ownedByCurrentPlayer() && !ChunkLockConfig.SHOW_OTHER_PLAYER_CLAIMS.get()) {
                continue;
            }

            RegionKey key = new RegionKey(claim.dimension(), claim.ownerId(), claim.ownerLastKnownName(), claim.ownedByCurrentPlayer());
            cellsByOwner.computeIfAbsent(key, ignored -> new HashSet<>()).add(new Cell(claim.chunkX(), claim.chunkZ()));
        }

        List<ClaimRegion> regions = new ArrayList<>();

        for (Map.Entry<RegionKey, Set<Cell>> entry : cellsByOwner.entrySet()) {
            Set<Cell> remaining = new HashSet<>(entry.getValue());

            while (!remaining.isEmpty()) {
                Cell start = remaining.iterator().next();
                Set<Cell> component = collectConnectedCells(start, remaining);
                Optional<List<BlockPos>> outline = buildOutline(component);
                outline.ifPresent(points -> regions.add(new ClaimRegion(
                        entry.getKey().dimension(),
                        entry.getKey().ownerId(),
                        entry.getKey().ownerName(),
                        entry.getKey().ownedByCurrentPlayer(),
                        points
                )));
            }
        }

        regions.sort(Comparator
                .comparing((ClaimRegion region) -> region.dimension().toString())
                .thenComparing(ClaimRegion::ownerName)
                .thenComparingInt(region -> region.outline().get(0).getX())
                .thenComparingInt(region -> region.outline().get(0).getZ()));
        return regions;
    }

    private static Set<Cell> collectConnectedCells(Cell start, Set<Cell> remaining) {
        Set<Cell> component = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(start);
        remaining.remove(start);

        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            component.add(cell);

            for (Cell neighbor : cell.neighbors()) {
                if (remaining.remove(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return component;
    }

    private static Optional<List<BlockPos>> buildOutline(Set<Cell> cells) {
        Map<Corner, List<Corner>> edges = new HashMap<>();

        for (Cell cell : cells) {
            int x = cell.x();
            int z = cell.z();

            if (!cells.contains(new Cell(x, z - 1))) {
                addEdge(edges, new Corner(x, z), new Corner(x + 1, z));
            }
            if (!cells.contains(new Cell(x + 1, z))) {
                addEdge(edges, new Corner(x + 1, z), new Corner(x + 1, z + 1));
            }
            if (!cells.contains(new Cell(x, z + 1))) {
                addEdge(edges, new Corner(x + 1, z + 1), new Corner(x, z + 1));
            }
            if (!cells.contains(new Cell(x - 1, z))) {
                addEdge(edges, new Corner(x, z + 1), new Corner(x, z));
            }
        }

        Corner start = edges.keySet().stream()
                .min(Comparator.comparingInt(Corner::z).thenComparingInt(Corner::x))
                .orElse(null);

        if (start == null) {
            return Optional.empty();
        }

        List<BlockPos> points = new ArrayList<>();
        Corner current = start;
        Corner previous = null;
        int guard = edges.values().stream().mapToInt(List::size).sum() + 2;

        while (guard-- > 0) {
            points.add(current.toBlockPos());
            List<Corner> nextCorners = edges.getOrDefault(current, List.of());
            Corner next = chooseNextCorner(nextCorners, previous);

            if (next == null) {
                return Optional.empty();
            }

            previous = current;
            current = next;

            if (current.equals(start)) {
                break;
            }
        }

        if (!current.equals(start) || points.size() < 4) {
            return Optional.empty();
        }

        return Optional.of(simplifyCollinear(points));
    }

    private static void addEdge(Map<Corner, List<Corner>> edges, Corner from, Corner to) {
        edges.computeIfAbsent(from, ignored -> new ArrayList<>()).add(to);
    }

    private static Corner chooseNextCorner(List<Corner> corners, Corner previous) {
        if (corners.isEmpty()) {
            return null;
        }

        if (corners.size() == 1) {
            return corners.get(0);
        }

        for (Corner corner : corners) {
            if (!corner.equals(previous)) {
                return corner;
            }
        }

        return corners.get(0);
    }

    private static List<BlockPos> simplifyCollinear(List<BlockPos> points) {
        List<BlockPos> simplified = new ArrayList<>();

        for (int index = 0; index < points.size(); index++) {
            BlockPos previous = points.get((index - 1 + points.size()) % points.size());
            BlockPos current = points.get(index);
            BlockPos next = points.get((index + 1) % points.size());

            boolean sameX = previous.getX() == current.getX() && current.getX() == next.getX();
            boolean sameZ = previous.getZ() == current.getZ() && current.getZ() == next.getZ();

            if (!sameX && !sameZ) {
                simplified.add(current);
            }
        }

        return simplified.size() >= 4 ? simplified : points;
    }

    private static String displayId(ClaimRegion region, int displayIndex) {
        return region.dimension().toString().replace(':', '_')
                + "_" + region.ownerId()
                + "_" + displayIndex;
    }

    private record RegionKey(ResourceLocation dimension, UUID ownerId, String ownerName, boolean ownedByCurrentPlayer) {
        private RegionKey {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(ownerName, "ownerName");
        }
    }

    private record ClaimRegion(ResourceLocation dimension, UUID ownerId, String ownerName, boolean ownedByCurrentPlayer, List<BlockPos> outline) {
    }

    private record Cell(int x, int z) {
        private List<Cell> neighbors() {
            return List.of(
                    new Cell(x + 1, z),
                    new Cell(x - 1, z),
                    new Cell(x, z + 1),
                    new Cell(x, z - 1)
            );
        }
    }

    private record Corner(int x, int z) {
        private BlockPos toBlockPos() {
            return new BlockPos(x << 4, 64, z << 4);
        }
    }
}
