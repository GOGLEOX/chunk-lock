package com.gogleox.chunklock.config;

import com.gogleox.chunklock.ChunkLockMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;

public final class ChunkLockConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_CLAIMS_PER_PLAYER;
    public static final ForgeConfigSpec.ConfigValue<String> CLAIM_ITEM;
    public static final ForgeConfigSpec.BooleanValue CLAIM_TOOL_USES_ENABLED;
    public static final ForgeConfigSpec.IntValue CLAIM_TOOL_MAX_USES;
    public static final ForgeConfigSpec.BooleanValue CLAIM_TOOL_BREAKS_WHEN_EMPTY;
    public static final ForgeConfigSpec.ConfigValue<String> CLAIM_TOOL_FAIL_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue CLAIM_TOOL_LOW_USES_WARNING_ENABLED;
    public static final ForgeConfigSpec.IntValue CLAIM_TOOL_LOW_USES_THRESHOLD;
    public static final ForgeConfigSpec.BooleanValue CLAIM_TOOL_REPAIR_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> CLAIM_TOOL_REPAIR_ITEM;
    public static final ForgeConfigSpec.IntValue CLAIM_TOOL_REPAIR_USES_PER_ITEM;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CREATIVE_BYPASS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CLAIMING_IN_OVERWORLD;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CLAIMING_IN_NETHER;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CLAIMING_IN_END;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_BREAK_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_PLACE_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTAINER_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BUCKET_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue PROTECT_AGAINST_TNT;
    public static final ForgeConfigSpec.BooleanValue PROTECT_AGAINST_CREEPERS;
    public static final ForgeConfigSpec.BooleanValue PROTECT_AGAINST_WITHER;
    public static final ForgeConfigSpec.BooleanValue PROTECT_AGAINST_OTHER_EXPLOSIONS;
    public static final ForgeConfigSpec.IntValue CLAIM_SHOW_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue CLAIM_SHOW_MAX_CHUNKS;
    public static final ForgeConfigSpec.IntValue DENIAL_MESSAGE_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_JOURNEYMAP_OVERLAY;
    public static final ForgeConfigSpec.BooleanValue SHOW_OTHER_PLAYER_CLAIMS;
    public static final ForgeConfigSpec.BooleanValue SHOW_CLAIM_OWNER_NAMES;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");

        MAX_CLAIMS_PER_PLAYER = builder
                .comment("Maximum number of chunks each player may claim. Set to 0 to disable new player claims.")
                .defineInRange("max_claims_per_player", 25, 0, 1_000_000);

        CLAIM_ITEM = builder
                .comment("Item used for sneak-right-click chunk claiming. Invalid IDs fall back to minecraft:map.")
                .define("claim_item", "minecraft:map");

        CLAIM_TOOL_USES_ENABLED = builder
                .comment("If true, ChunkLock claim-tool actions consume limited uses on the configured claim item.")
                .define("claim_tool_uses_enabled", true);

        CLAIM_TOOL_MAX_USES = builder
                .comment("Maximum number of ChunkLock uses per claim tool item stack when limited uses are enabled.")
                .defineInRange("claim_tool_max_uses", 5, 1, 10_000);

        CLAIM_TOOL_BREAKS_WHEN_EMPTY = builder
                .comment("If true, the claim tool breaks or is removed when its ChunkLock uses reach zero.")
                .define("claim_tool_breaks_when_empty", true);

        CLAIM_TOOL_FAIL_MESSAGE = builder
                .comment("Message shown when a ChunkLock claim tool has no remaining uses.")
                .define("claim_tool_fail_message", "This claim tool is worn out.");

        CLAIM_TOOL_LOW_USES_WARNING_ENABLED = builder
                .comment("If true, ChunkLock shows an actionbar warning after claim-tool use or repair when remaining uses are low.")
                .define("claim_tool_low_uses_warning_enabled", true);

        CLAIM_TOOL_LOW_USES_THRESHOLD = builder
                .comment("Remaining-use threshold at or below which ChunkLock shows a low-uses warning.")
                .defineInRange("claim_tool_low_uses_threshold", 8, 0, 10_000);

        CLAIM_TOOL_REPAIR_ENABLED = builder
                .comment("If true, configured repair items can restore ChunkLock claim-tool uses.")
                .define("claim_tool_repair_enabled", true);

        CLAIM_TOOL_REPAIR_ITEM = builder
                .comment("Item used in the offhand to repair ChunkLock claim tools.")
                .define("claim_tool_repair_item", "minecraft:paper");

        CLAIM_TOOL_REPAIR_USES_PER_ITEM = builder
                .comment("How many ChunkLock uses each repair item restores.")
                .defineInRange("claim_tool_repair_uses_per_item", 8, 1, 10_000);

        ALLOW_CREATIVE_BYPASS = builder
                .comment("If true, creative-mode players may bypass claim protection. Permission-based bypass is deferred.")
                .define("allow_creative_bypass", false);

        ALLOW_CLAIMING_IN_OVERWORLD = builder
                .comment("If true, players may claim chunks in the Overworld.")
                .define("allow_claiming_in_overworld", true);

        ALLOW_CLAIMING_IN_NETHER = builder
                .comment("If true, players may claim chunks in the Nether.")
                .define("allow_claiming_in_nether", false);

        ALLOW_CLAIMING_IN_END = builder
                .comment("If true, players may claim chunks in the End.")
                .define("allow_claiming_in_end", false);

        ENABLE_BLOCK_BREAK_PROTECTION = builder
                .comment("If true, non-owners cannot break blocks inside claimed chunks.")
                .define("enable_block_break_protection", true);

        ENABLE_BLOCK_PLACE_PROTECTION = builder
                .comment("If true, non-owners cannot place blocks inside claimed chunks.")
                .define("enable_block_place_protection", true);

        ENABLE_CONTAINER_PROTECTION = builder
                .comment("If true, non-owners cannot interact with protected block entities inside claimed chunks.")
                .define("enable_container_protection", true);

        ENABLE_BUCKET_PROTECTION = builder
                .comment("If true, non-owners cannot place or pick up bucket fluids inside claimed chunks.")
                .define("enable_bucket_protection", true);

        PROTECT_AGAINST_TNT = builder
                .comment("If true, TNT explosions cannot break blocks inside claimed chunks.")
                .define("protect_against_tnt", true);

        PROTECT_AGAINST_CREEPERS = builder
                .comment("If true, creeper explosions cannot break blocks inside claimed chunks.")
                .define("protect_against_creepers", true);

        PROTECT_AGAINST_WITHER = builder
                .comment("If true, wither explosions cannot break blocks inside claimed chunks.")
                .define("protect_against_wither", true);

        PROTECT_AGAINST_OTHER_EXPLOSIONS = builder
                .comment("If true, other explosion sources cannot break blocks inside claimed chunks.")
                .define("protect_against_other_explosions", true);

        CLAIM_SHOW_DURATION_TICKS = builder
                .comment("How long /chunklock show displays the current chunk boundary to the player, in ticks.")
                .defineInRange("claim_show_duration_ticks", 300, 20, 20 * 60);

        CLAIM_SHOW_MAX_CHUNKS = builder
                .comment("Maximum number of connected claimed chunks scanned by /chunklock show.")
                .defineInRange("claim_show_max_chunks", 256, 1, 4_096);

        DENIAL_MESSAGE_COOLDOWN_TICKS = builder
                .comment("Cooldown, in ticks, before another claim protection denial message is sent to the same player.")
                .defineInRange("denial_message_cooldown_ticks", 40, 0, 20 * 60);

        ENABLE_JOURNEYMAP_OVERLAY = builder
                .comment("If true, ChunkLock displays claim overlays in JourneyMap when JourneyMap is installed.")
                .define("enable_journeymap_overlay", true);

        SHOW_OTHER_PLAYER_CLAIMS = builder
                .comment("If true, JourneyMap overlays include claims owned by other players.")
                .define("show_other_player_claims", true);

        SHOW_CLAIM_OWNER_NAMES = builder
                .comment("If true, JourneyMap claim overlays include owner names in labels and tooltips.")
                .define("show_claim_owner_names", true);

        builder.pop();

        SPEC = builder.build();
    }

    public static Item resolveClaimItem() {
        return resolveConfiguredItem(CLAIM_ITEM.get(), "claim item", Items.MAP, "minecraft:map");
    }

    public static Item resolveClaimItemQuiet() {
        return resolveConfiguredItemQuiet(CLAIM_ITEM.get(), Items.MAP);
    }

    public static Item resolveClaimToolRepairItem() {
        return resolveConfiguredItem(CLAIM_TOOL_REPAIR_ITEM.get(), "claim tool repair item", Items.PAPER, "minecraft:paper");
    }

    public static boolean isConfiguredClaimItem(ItemStack stack) {
        return !stack.isEmpty() && stack.is(resolveClaimItemQuiet());
    }

    public static boolean isClaimingAllowedIn(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            return ALLOW_CLAIMING_IN_OVERWORLD.get();
        }

        if (level.dimension() == Level.NETHER) {
            return ALLOW_CLAIMING_IN_NETHER.get();
        }

        if (level.dimension() == Level.END) {
            return ALLOW_CLAIMING_IN_END.get();
        }

        return true;
    }

    private ChunkLockConfig() {
    }

    private static Item resolveConfiguredItem(String configuredItem, String label, Item fallbackItem, String fallbackId) {
        ResourceLocation itemId = ResourceLocation.tryParse(configuredItem);

        if (itemId == null) {
            ChunkLockMod.LOGGER.warn("Invalid ChunkLock {} '{}'; falling back to {}", label, configuredItem, fallbackId);
            return fallbackItem;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);

        if (item == Items.AIR) {
            ChunkLockMod.LOGGER.warn("Unknown ChunkLock {} '{}'; falling back to {}", label, configuredItem, fallbackId);
            return fallbackItem;
        }

        return item;
    }

    private static Item resolveConfiguredItemQuiet(String configuredItem, Item fallbackItem) {
        ResourceLocation itemId = ResourceLocation.tryParse(configuredItem);

        if (itemId == null) {
            return fallbackItem;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item == Items.AIR ? fallbackItem : item;
    }
}
