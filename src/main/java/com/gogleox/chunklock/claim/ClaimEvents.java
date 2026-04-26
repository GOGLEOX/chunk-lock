package com.gogleox.chunklock.claim;

import com.gogleox.chunklock.config.ChunkLockConfig;
import com.gogleox.chunklock.network.ChunkLockNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ClaimEvents {
    private static final ClaimManager CLAIM_MANAGER = new ClaimManager();
    private static final Map<UUID, Long> LAST_DENIAL_MESSAGE_TICKS = new HashMap<>();

    private static final Component CLAIMED_MESSAGE = Component.literal("Chunk claimed.").withStyle(ChatFormatting.GREEN);
    private static final Component UNCLAIMED_MESSAGE = Component.literal("Chunk unclaimed.").withStyle(ChatFormatting.YELLOW);
    private static final Component HELP_MESSAGE = Component.literal("Hold a map and sneak-right-click to claim or unclaim a chunk.")
            .withStyle(ChatFormatting.GRAY);
    private static final Component DENIED_MESSAGE = Component.literal("This chunk is claimed.").withStyle(ChatFormatting.RED);
    private static final Component CLAIM_LIMIT_MESSAGE = Component.literal("You have reached your claim limit.").withStyle(ChatFormatting.RED);
    private static final Component DIMENSION_DISABLED_MESSAGE = Component.literal("Claiming is disabled in this dimension.").withStyle(ChatFormatting.RED);
    private static final Component FTB_DISABLED_MESSAGE = Component.literal("ChunkLock is disabled because FTB Chunks is loaded.").withStyle(ChatFormatting.RED);

    private ClaimEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChunkLockNetwork.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChunkLockNetwork.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        ItemStack mainHandItem = player.getMainHandItem();

        if (player.isShiftKeyDown() && isClaimItem(mainHandItem)) {
            cancelClaimItemUse(event);

            if (player instanceof ServerPlayer serverPlayer && event.getLevel() instanceof ServerLevel serverLevel) {
                handleClaimToolUse(event, serverPlayer, serverLevel);
            }

            return;
        }

        if (event.getLevel().isClientSide() || !(player instanceof ServerPlayer serverPlayer) || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (ChunkLockConfig.ENABLE_BUCKET_PROTECTION.get() && mainHandItem.getItem() instanceof BucketItem) {
            BlockPos adjacentPos = event.getFace() == null ? event.getPos() : event.getPos().relative(event.getFace());
            if (isProtectedFrom(serverPlayer, serverLevel, new ChunkPos(event.getPos()))
                    || isProtectedFrom(serverPlayer, serverLevel, new ChunkPos(adjacentPos))) {
                denyRightClick(event, serverPlayer);
                return;
            }
        }

        if (ChunkLockConfig.ENABLE_CONTAINER_PROTECTION.get() && isProtectedInteractionTarget(serverLevel, event.getPos())) {
            ChunkPos chunkPos = new ChunkPos(event.getPos());
            if (isProtectedFrom(serverPlayer, serverLevel, chunkPos)) {
                denyRightClick(event, serverPlayer);
                return;
            }
        }

        if (serverPlayer.isShiftKeyDown() && !isClaimItem(mainHandItem)) {
            serverPlayer.sendSystemMessage(HELP_MESSAGE);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity().isShiftKeyDown()
                && isClaimItem(event.getEntity().getMainHandItem())) {
            cancelClaimItemUse(event);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ChunkLockConfig.ENABLE_BLOCK_BREAK_PROTECTION.get() || ChunkLockConfig.isDisabledByFtbChunks()) {
            return;
        }

        Player player = event.getPlayer();
        LevelAccessor level = event.getLevel();

        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (isProtectedFrom(serverPlayer, serverLevel, new ChunkPos(event.getPos()))) {
            event.setCanceled(true);
            sendDenialMessage(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!ChunkLockConfig.ENABLE_BLOCK_PLACE_PROTECTION.get()
                || ChunkLockConfig.isDisabledByFtbChunks()
                || !(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (isProtectedFrom(serverPlayer, serverLevel, new ChunkPos(event.getPos()))) {
            event.setCanceled(true);
            sendDenialMessage(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onBucketFill(FillBucketEvent event) {
        if (!ChunkLockConfig.ENABLE_BUCKET_PROTECTION.get()
                || ChunkLockConfig.isDisabledByFtbChunks()
                || event.getLevel().isClientSide()) {
            return;
        }

        Player player = event.getEntity();

        if (!(player instanceof ServerPlayer serverPlayer) || !(event.getLevel() instanceof ServerLevel serverLevel)
                || !(event.getTarget() instanceof BlockHitResult blockHitResult)
                || event.getTarget().getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos targetPos = blockHitResult.getBlockPos();

        if (isProtectedFrom(serverPlayer, serverLevel, new ChunkPos(targetPos))) {
            event.setCanceled(true);
            sendDenialMessage(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!ChunkLockConfig.ENABLE_EXPLOSION_PROTECTION.get()
                || ChunkLockConfig.isDisabledByFtbChunks()
                || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        event.getAffectedBlocks().removeIf(pos -> CLAIM_MANAGER.isClaimed(serverLevel, new ChunkPos(pos)));
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (!ChunkLockConfig.ENABLE_MOB_GRIEFING_PROTECTION.get()
                || ChunkLockConfig.isDisabledByFtbChunks()
                || !(event.getEntity().level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (CLAIM_MANAGER.isClaimed(serverLevel, event.getEntity().chunkPosition())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!ChunkLockConfig.ENABLE_MOB_GRIEFING_PROTECTION.get()
                || ChunkLockConfig.isDisabledByFtbChunks()
                || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        Entity entity = event.getEntity();

        if (entity instanceof Player) {
            return;
        }

        if (CLAIM_MANAGER.isClaimed(serverLevel, new ChunkPos(event.getPos()))) {
            event.setCanceled(true);
        }
    }

    private static void handleClaimToolUse(PlayerInteractEvent.RightClickBlock event, ServerPlayer serverPlayer, ServerLevel serverLevel) {
        ChunkPos chunkPos = new ChunkPos(event.getPos());
        ClaimData claim = CLAIM_MANAGER.getClaim(serverLevel, chunkPos);

        if (claim == null) {
            serverPlayer.sendSystemMessage(messageForClaimResult(CLAIM_MANAGER.claimChunkWithResult(serverPlayer, serverLevel, chunkPos)));
            return;
        }

        if (claim.ownerId().equals(serverPlayer.getUUID())) {
            if (CLAIM_MANAGER.unclaimChunk(serverPlayer, serverLevel, chunkPos)) {
                serverPlayer.sendSystemMessage(UNCLAIMED_MESSAGE);
            }
            return;
        }

        serverPlayer.sendSystemMessage(Component.literal("This chunk is already claimed by " + claim.ownerLastKnownName() + ".")
                .withStyle(ChatFormatting.RED));
    }

    private static boolean isProtectedFrom(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        if (ChunkLockConfig.isDisabledByFtbChunks()) {
            return false;
        }

        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        if (claim == null || claim.ownerId().equals(player.getUUID())) {
            return false;
        }

        return !canBypassProtection(player);
    }

    private static boolean canBypassProtection(ServerPlayer player) {
        return ChunkLockConfig.ALLOW_OPERATOR_BYPASS.get() && player.hasPermissions(2);
    }

    private static boolean isProtectedInteractionTarget(ServerLevel level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();

        if (block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.BARREL
                || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER
                || block == Blocks.HOPPER
                || block == Blocks.DROPPER
                || block == Blocks.DISPENSER
                || block == Blocks.BREWING_STAND
                || block == Blocks.LECTERN
                || block instanceof ShulkerBoxBlock) {
            return true;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container
                || blockEntity != null && blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    private static void denyRightClick(PlayerInteractEvent.RightClickBlock event, ServerPlayer player) {
        event.setCanceled(true);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        sendDenialMessage(player);
    }

    private static void cancelClaimItemUse(PlayerInteractEvent event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (event instanceof PlayerInteractEvent.RightClickBlock rightClickBlock) {
            rightClickBlock.setUseBlock(Event.Result.DENY);
            rightClickBlock.setUseItem(Event.Result.DENY);
        }
    }

    private static void sendDenialMessage(ServerPlayer player) {
        sendCooldownMessage(player, DENIED_MESSAGE);
    }

    private static void sendCooldownMessage(ServerPlayer player, Component message) {
        long currentTick = player.serverLevel().getGameTime();
        long cooldownTicks = ChunkLockConfig.DENIAL_MESSAGE_COOLDOWN_TICKS.get();
        long lastMessageTick = LAST_DENIAL_MESSAGE_TICKS.getOrDefault(player.getUUID(), Long.MIN_VALUE);

        if (cooldownTicks > 0 && currentTick - lastMessageTick < cooldownTicks) {
            return;
        }

        LAST_DENIAL_MESSAGE_TICKS.put(player.getUUID(), currentTick);
        player.sendSystemMessage(message);
    }

    private static boolean isClaimItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ChunkLockConfig.resolveClaimItem());
    }

    private static Component messageForClaimResult(ClaimManager.ClaimResult result) {
        return switch (result) {
            case CLAIMED -> CLAIMED_MESSAGE;
            case CLAIM_LIMIT_REACHED -> CLAIM_LIMIT_MESSAGE;
            case DIMENSION_DISABLED -> DIMENSION_DISABLED_MESSAGE;
            case DISABLED_BY_FTB_CHUNKS -> FTB_DISABLED_MESSAGE;
            case ALREADY_CLAIMED -> Component.literal("Unable to claim this chunk.").withStyle(ChatFormatting.RED);
        };
    }
}
