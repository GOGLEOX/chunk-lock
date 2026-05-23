package com.gogleox.chunklock.claim;

import com.gogleox.chunklock.config.ChunkLockConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class ClaimToolUsage {
    private static final String REMAINING_USES_TAG = "ChunkLockRemainingUses";

    private ClaimToolUsage() {
    }

    public static boolean isEnabled() {
        return ChunkLockConfig.CLAIM_TOOL_USES_ENABLED.get();
    }

    public static boolean canUse(ServerPlayer player, ItemStack stack) {
        if (!isEnabled()) {
            return true;
        }

        if (requiresSingleTrackedItem(stack)) {
            player.sendSystemMessage(Component.translatable("message.chunklock.claim_tool_single_item"));
            return false;
        }

        if (getRemainingUses(stack) > 0) {
            return true;
        }

        player.sendSystemMessage(Component.literal(ChunkLockConfig.CLAIM_TOOL_FAIL_MESSAGE.get()));

        if (ChunkLockConfig.CLAIM_TOOL_BREAKS_WHEN_EMPTY.get()) {
            removeIfDepleted(stack);
        }

        return false;
    }

    public static void consumeUse(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (!isEnabled() || stack.isEmpty()) {
            return;
        }

        int remainingUses = Math.max(0, getRemainingUses(stack) - 1);
        setRemainingUses(stack, remainingUses);

        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
        }

        if (remainingUses <= 0 && ChunkLockConfig.CLAIM_TOOL_BREAKS_WHEN_EMPTY.get()) {
            removeIfDepleted(stack);
        }

        sendLowUsesWarningIfNeeded(player, stack);
    }

    public static boolean shouldAttemptRepair(ItemStack repairStack) {
        return isEnabled()
                && ChunkLockConfig.CLAIM_TOOL_REPAIR_ENABLED.get()
                && !repairStack.isEmpty()
                && repairStack.is(ChunkLockConfig.resolveClaimToolRepairItem());
    }

    public static RepairResult tryRepair(ServerPlayer player, ItemStack toolStack, ItemStack repairStack) {
        if (!isEnabled() || !ChunkLockConfig.CLAIM_TOOL_REPAIR_ENABLED.get()) {
            return RepairResult.NOT_APPLICABLE;
        }

        if (toolStack.isEmpty() || repairStack.isEmpty()) {
            return RepairResult.NOT_APPLICABLE;
        }

        if (requiresSingleTrackedItem(toolStack)) {
            player.sendSystemMessage(Component.translatable("message.chunklock.claim_tool_single_item"));
            return RepairResult.REQUIRES_SINGLE_ITEM;
        }

        if (getRemainingUses(toolStack) >= getMaxUses()) {
            player.sendSystemMessage(Component.translatable("message.chunklock.claim_tool_repair_full"));
            return RepairResult.ALREADY_FULLY_REPAIRED;
        }

        if (!repairStack.is(ChunkLockConfig.resolveClaimToolRepairItem())) {
            return RepairResult.NO_VALID_REPAIR_ITEM;
        }

        int missingUses = getMaxUses() - getRemainingUses(toolStack);
        int usesPerItem = ChunkLockConfig.CLAIM_TOOL_REPAIR_USES_PER_ITEM.get();
        int neededItems = (int) Math.ceil((double) missingUses / usesPerItem);
        int itemsToConsume = Math.min(neededItems, repairStack.getCount());
        int restoredUses = Math.min(missingUses, itemsToConsume * usesPerItem);

        if (restoredUses <= 0) {
            return RepairResult.NO_VALID_REPAIR_ITEM;
        }

        setRemainingUses(toolStack, getRemainingUses(toolStack) + restoredUses);

        if (toolStack.isDamageableItem()) {
            toolStack.setDamageValue(Math.max(0, toolStack.getDamageValue() - restoredUses));
        }

        repairStack.shrink(itemsToConsume);
        player.sendSystemMessage(Component.translatable(
                "message.chunklock.claim_tool_repaired",
                getRemainingUses(toolStack),
                getMaxUses()
        ));
        sendLowUsesWarningIfNeeded(player, toolStack);
        return RepairResult.REPAIRED;
    }

    public static int getDisplayedRemainingUses(ItemStack stack) {
        if (!isEnabled() || stack.isEmpty()) {
            return -1;
        }

        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(REMAINING_USES_TAG)) {
            return Math.max(0, tag.getInt(REMAINING_USES_TAG));
        }

        return getMaxUses();
    }

    public static int getConfiguredMaxUses() {
        return getMaxUses();
    }

    private static int getRemainingUses(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        if (!tag.contains(REMAINING_USES_TAG)) {
            int maxUses = getMaxUses();
            tag.putInt(REMAINING_USES_TAG, maxUses);
            return maxUses;
        }

        return Math.max(0, tag.getInt(REMAINING_USES_TAG));
    }

    private static void setRemainingUses(ItemStack stack, int remainingUses) {
        stack.getOrCreateTag().putInt(REMAINING_USES_TAG, remainingUses);
    }

    private static int getMaxUses() {
        return ChunkLockConfig.CLAIM_TOOL_MAX_USES.get();
    }

    private static void sendLowUsesWarningIfNeeded(ServerPlayer player, ItemStack stack) {
        if (!ChunkLockConfig.CLAIM_TOOL_LOW_USES_WARNING_ENABLED.get() || stack.isEmpty()) {
            return;
        }

        int remainingUses = getDisplayedRemainingUses(stack);
        if (remainingUses < 0 || remainingUses > ChunkLockConfig.CLAIM_TOOL_LOW_USES_THRESHOLD.get()) {
            return;
        }

        player.displayClientMessage(
                Component.translatable("message.chunklock.claim_tool_low_uses", remainingUses, getMaxUses()),
                true
        );
    }

    private static boolean requiresSingleTrackedItem(ItemStack stack) {
        return !stack.isEmpty()
                && !stack.isDamageableItem()
                && stack.getCount() > 1;
    }

    private static void removeIfDepleted(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.shrink(1);
        }
    }

    public enum RepairResult {
        NOT_APPLICABLE,
        REPAIRED,
        ALREADY_FULLY_REPAIRED,
        NO_VALID_REPAIR_ITEM,
        REQUIRES_SINGLE_ITEM
    }
}
