package com.gogleox.chunklock.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ChunkLockClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_CLAIM_HUD;
    public static final ForgeConfigSpec.BooleanValue SHOW_WILDERNESS_TEXT;
    public static final ForgeConfigSpec.ConfigValue<String> WILDERNESS_TEXT;
    public static final ForgeConfigSpec.ConfigValue<String> CLAIMED_TEXT;
    public static final ForgeConfigSpec.IntValue CLAIM_HUD_DISPLAY_TICKS;
    public static final ForgeConfigSpec.IntValue HUD_Y_OFFSET;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("hud");

        ENABLE_CLAIM_HUD = builder
                .comment("If true, ChunkLock renders claim status text above the hotbar.")
                .define("enable_claim_hud", true);

        SHOW_WILDERNESS_TEXT = builder
                .comment("If true, ChunkLock shows the wilderness label while standing in an unclaimed chunk.")
                .define("show_wilderness_text", true);

        WILDERNESS_TEXT = builder
                .comment("Text shown for unclaimed chunks.")
                .define("wilderness_text", "Wilderness");

        CLAIMED_TEXT = builder
                .comment("Text shown for claimed chunks. Use {owner} as the owner-name placeholder.")
                .define("claimed_text", "Claimed by {owner}");

        CLAIM_HUD_DISPLAY_TICKS = builder
                .comment("How long the claim HUD stays visible after the displayed claim state changes.")
                .defineInRange("claim_hud_display_ticks", 60, 1, 20 * 60);

        HUD_Y_OFFSET = builder
                .comment("Vertical offset for the claim HUD, relative to its default position above the hotbar.")
                .defineInRange("hud_y_offset", 0, -200, 200);

        builder.pop();

        SPEC = builder.build();
    }

    private ChunkLockClientConfig() {
    }
}
