package com.gogleox.chunklock.client;

import com.gogleox.chunklock.ChunkLockMod;
import com.gogleox.chunklock.claim.ClaimToolUsage;
import com.gogleox.chunklock.config.ChunkLockConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ChunkLockMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClaimToolTooltipHandler {
    private ClaimToolTooltipHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (!ClaimToolUsage.isEnabled()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!ChunkLockConfig.isConfiguredClaimItem(stack)) {
            return;
        }

        int remainingUses = ClaimToolUsage.getDisplayedRemainingUses(stack);
        if (remainingUses < 0) {
            return;
        }

        event.getToolTip().add(Component.translatable(
                "tooltip.chunklock.claim_tool_uses",
                remainingUses,
                ClaimToolUsage.getConfiguredMaxUses()
        ).withStyle(ChatFormatting.GRAY));
    }
}
