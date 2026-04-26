package com.gogleox.chunklock.config;

import com.gogleox.chunklock.ChunkLockMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

public final class ChunkLockConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_CLAIMS_PER_PLAYER;
    public static final ForgeConfigSpec.ConfigValue<String> CLAIM_ITEM;
    public static final ForgeConfigSpec.BooleanValue ALLOW_OPERATOR_BYPASS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CLAIMING_IN_OVERWORLD;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CLAIMING_IN_NETHER;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CLAIMING_IN_END;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_BREAK_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BLOCK_PLACE_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_CONTAINER_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BUCKET_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_EXPLOSION_PROTECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_MOB_GRIEFING_PROTECTION;
    public static final ForgeConfigSpec.IntValue DENIAL_MESSAGE_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue DISABLE_IF_FTB_CHUNKS_LOADED;
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

        ALLOW_OPERATOR_BYPASS = builder
                .comment("If true, operators with permission level 2 or higher bypass claim protection.")
                .define("allow_operator_bypass", true);

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

        ENABLE_EXPLOSION_PROTECTION = builder
                .comment("If true, explosions cannot modify blocks inside claimed chunks.")
                .define("enable_explosion_protection", false);

        ENABLE_MOB_GRIEFING_PROTECTION = builder
                .comment("If true, mob griefing is denied while the mob is inside a claimed chunk.")
                .define("enable_mob_griefing_protection", false);

        DENIAL_MESSAGE_COOLDOWN_TICKS = builder
                .comment("Cooldown, in ticks, before another claim protection denial message is sent to the same player.")
                .defineInRange("denial_message_cooldown_ticks", 40, 0, 20 * 60);

        DISABLE_IF_FTB_CHUNKS_LOADED = builder
                .comment("If true, ChunkLock claiming and protection are disabled when FTB Chunks is loaded.")
                .define("disable_if_ftb_chunks_loaded", false);

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
        String configuredItem = CLAIM_ITEM.get();
        ResourceLocation itemId = ResourceLocation.tryParse(configuredItem);

        if (itemId == null) {
            ChunkLockMod.LOGGER.warn("Invalid ChunkLock claim item '{}'; falling back to minecraft:map", configuredItem);
            return Items.MAP;
        }

        Item item = BuiltInRegistries.ITEM.get(itemId);

        if (item == Items.AIR) {
            ChunkLockMod.LOGGER.warn("Unknown ChunkLock claim item '{}'; falling back to minecraft:map", configuredItem);
            return Items.MAP;
        }

        return item;
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

    public static boolean isDisabledByFtbChunks() {
        return DISABLE_IF_FTB_CHUNKS_LOADED.get() && ModList.get().isLoaded("ftbchunks");
    }

    private ChunkLockConfig() {
    }
}
