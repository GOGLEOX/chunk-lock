package com.gogleox.chunklock.claim;

import com.gogleox.chunklock.config.ChunkLockConfig;
import com.gogleox.chunklock.command.ChunkLockInspect;
import com.gogleox.chunklock.network.ChunkLockNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.Container;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ClaimEvents {
    private static final ClaimManager CLAIM_MANAGER = new ClaimManager();
    private static final Map<UUID, Long> LAST_DENIAL_MESSAGE_TICKS = new HashMap<>();
    private static final Map<UUID, PlayerChunkView> LAST_SYNCED_CHUNKS = new HashMap<>();

    private ClaimEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChunkLockNetwork.syncTo(player);
            rememberChunk(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChunkLockNetwork.syncTo(player);
            rememberChunk(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_DENIAL_MESSAGE_TICKS.remove(event.getEntity().getUUID());
        LAST_SYNCED_CHUNKS.remove(event.getEntity().getUUID());

        if (event.getEntity() instanceof ServerPlayer player) {
            ChunkLockInspect.clear(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide()
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        PlayerChunkView currentView = PlayerChunkView.from(player);
        PlayerChunkView lastView = LAST_SYNCED_CHUNKS.get(player.getUUID());

        if (!currentView.equals(lastView)) {
            ChunkLockNetwork.syncTo(player);
            LAST_SYNCED_CHUNKS.put(player.getUUID(), currentView);
        }

        ChunkLockInspect.reportCurrentChunkIfChanged(player);
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
                ChunkLockInspect.reportChunk(serverPlayer, serverLevel, new ChunkPos(event.getPos()), true);
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
            serverPlayer.sendSystemMessage(message("message.chunklock.help", ChatFormatting.GRAY));
        }

        ChunkLockInspect.reportChunk(serverPlayer, serverLevel, new ChunkPos(event.getPos()), true);
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
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        ChunkLockInspect.reportChunk(player, level, new ChunkPos(event.getPos()), true);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ChunkLockConfig.ENABLE_BLOCK_BREAK_PROTECTION.get()) {
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
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        Explosion explosion = event.getExplosion();
        if (!shouldProtectExplosion(explosion)) {
            return;
        }

        event.getAffectedBlocks().removeIf(pos -> CLAIM_MANAGER.isClaimed(serverLevel, new ChunkPos(pos)));
    }

    private static void handleClaimToolUse(PlayerInteractEvent.RightClickBlock event, ServerPlayer serverPlayer, ServerLevel serverLevel) {
        ItemStack toolStack = serverPlayer.getMainHandItem();
        ItemStack repairStack = serverPlayer.getOffhandItem();

        if (ClaimToolUsage.shouldAttemptRepair(repairStack)) {
            ClaimToolUsage.RepairResult repairResult = ClaimToolUsage.tryRepair(serverPlayer, toolStack, repairStack);
            if (repairResult == ClaimToolUsage.RepairResult.REPAIRED
                    || repairResult == ClaimToolUsage.RepairResult.ALREADY_FULLY_REPAIRED) {
                return;
            }
        }

        if (!ClaimToolUsage.canUse(serverPlayer, toolStack)) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(event.getPos());
        ClaimData claim = CLAIM_MANAGER.getClaim(serverLevel, chunkPos);

        if (claim == null) {
            ClaimManager.ClaimResult result = CLAIM_MANAGER.claimChunkWithResult(serverPlayer, serverLevel, chunkPos);
            serverPlayer.sendSystemMessage(messageForClaimResult(result));

            if (result == ClaimManager.ClaimResult.CLAIMED) {
                ClaimToolUsage.consumeUse(serverPlayer, toolStack, event.getHand());
            }
            return;
        }

        if (claim.ownerId().equals(serverPlayer.getUUID())) {
            if (CLAIM_MANAGER.unclaimChunk(serverPlayer, serverLevel, chunkPos)) {
                serverPlayer.sendSystemMessage(message("message.chunklock.unclaimed", ChatFormatting.YELLOW));
                ClaimToolUsage.consumeUse(serverPlayer, toolStack, event.getHand());
            }
            return;
        }

        serverPlayer.sendSystemMessage(message("message.chunklock.already_claimed_by", ChatFormatting.RED, claim.ownerLastKnownName()));
        ClaimToolUsage.consumeUse(serverPlayer, toolStack, event.getHand());
    }

    private static boolean isProtectedFrom(ServerPlayer player, ServerLevel level, ChunkPos pos) {
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        if (claim == null || CLAIM_MANAGER.canAccess(player, level, pos)) {
            return false;
        }

        return !canBypassProtection(player);
    }

    private static boolean canBypassProtection(ServerPlayer player) {
        return ChunkLockConfig.ALLOW_CREATIVE_BYPASS.get() && player.isCreative();
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
        sendCooldownMessage(player, message("message.chunklock.protection_denied", ChatFormatting.RED));
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
        return ChunkLockConfig.isConfiguredClaimItem(stack);
    }

    private static boolean shouldProtectExplosion(Explosion explosion) {
        Entity source = explosion.getExploder();

        if (source instanceof PrimedTnt) {
            return ChunkLockConfig.PROTECT_AGAINST_TNT.get();
        }

        if (source instanceof Creeper) {
            return ChunkLockConfig.PROTECT_AGAINST_CREEPERS.get();
        }

        if (source instanceof WitherBoss || source instanceof WitherSkull) {
            return ChunkLockConfig.PROTECT_AGAINST_WITHER.get();
        }

        return ChunkLockConfig.PROTECT_AGAINST_OTHER_EXPLOSIONS.get();
    }

    private static Component messageForClaimResult(ClaimManager.ClaimResult result) {
        return switch (result) {
            case CLAIMED -> message("message.chunklock.claimed", ChatFormatting.GREEN);
            case CLAIM_LIMIT_REACHED -> message("message.chunklock.claim_limit_reached", ChatFormatting.RED);
            case DIMENSION_DISABLED -> message("message.chunklock.dimension_disabled", ChatFormatting.RED);
            case ALREADY_CLAIMED -> message("message.chunklock.unable_to_claim", ChatFormatting.RED);
        };
    }

    private static Component message(String key, ChatFormatting formatting, Object... args) {
        return Component.translatable(key, args).withStyle(formatting);
    }

    private static void rememberChunk(ServerPlayer player) {
        LAST_SYNCED_CHUNKS.put(player.getUUID(), PlayerChunkView.from(player));
    }

    private record PlayerChunkView(ResourceLocation dimension, int chunkX, int chunkZ) {
        private static PlayerChunkView from(ServerPlayer player) {
            return new PlayerChunkView(player.serverLevel().dimension().location(), player.chunkPosition().x, player.chunkPosition().z);
        }
    }
}
