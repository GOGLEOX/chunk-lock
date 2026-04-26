# ChunkLock

ChunkLock is a lightweight NeoForge 1.20.1 chunk claiming mod for survival servers.

Players can claim chunks, protect them from basic modification, and optionally expose synced claim data to client-side map integrations. The default claim item is `minecraft:map`.

Paper is intentionally not the default claim item because paper is reserved for the WorldNotes mod.

## What ChunkLock Does

- Lets players claim and unclaim chunks.
- Stores claims persistently in server world saved data.
- Protects claimed chunks from non-owner block breaking, block placing, container interaction, and bucket fluid interaction.
- Provides operator/admin commands.
- Supports configurable claim limits and dimension rules.
- Provides optional JourneyMap overlay support when JourneyMap is installed.

## What ChunkLock Does Not Do

- No teams.
- No GUI.
- No chunk loading.
- No economy.
- No permission plugin integration yet.
- No Xaero map overlay yet.
- No FTB Chunks dependency or migration layer.

## Claiming Chunks

Default item: `minecraft:map`

Sneak-right-click a block while holding the configured claim item:

- If the chunk is unclaimed, it is claimed for you.
- If you own the chunk, it is unclaimed.
- If someone else owns the chunk, the action is denied.

An optional item, `chunklock:claim_stake`, is included. Servers may switch to it with:

```toml
claim_item = "chunklock:claim_stake"
```

## Commands

Player commands:

- `/chunklock claim`
- `/chunklock unclaim`
- `/chunklock info`
- `/chunklock list`

Admin commands, permission level 2+:

- `/chunklock reload`
- `/chunklock admin unclaim`
- `/chunklock admin clearplayer <player>`
- `/chunklock admin listplayer <player>`

Admin player commands can target cached/offline profiles when Minecraft can resolve the profile.

## Config Options

Common config: `config/chunklock-common.toml`

```toml
max_claims_per_player = 25
claim_item = "minecraft:map"
allow_operator_bypass = true
allow_claiming_in_overworld = true
allow_claiming_in_nether = false
allow_claiming_in_end = false
enable_block_break_protection = true
enable_block_place_protection = true
enable_container_protection = true
enable_bucket_protection = true
enable_explosion_protection = false
enable_mob_griefing_protection = false
denial_message_cooldown_ticks = 40
disable_if_ftb_chunks_loaded = false
enable_journeymap_overlay = true
show_other_player_claims = true
show_claim_owner_names = true
```

Invalid claim item IDs fall back to `minecraft:map` and log a warning.

## Map Integration Status

JourneyMap support is optional. ChunkLock runs normally without JourneyMap installed.

When JourneyMap is installed on the client, claims can appear as chunk-boundary overlays:

- Green: claims owned by the current player.
- Red: claims owned by other players.
- Tooltip/label: `Claimed by: <owner>`.

No actual dependency on JourneyMap is required for dedicated servers.

Xaero integration is not implemented yet.

## Server Admin Notes

- Creative mode does not bypass protection by itself.
- Operators bypass only when `allow_operator_bypass = true`.
- Nether and End claiming are disabled by default.
- Explosion and mob griefing protections are disabled by default to preserve vanilla behavior unless explicitly enabled.
- If using FTB Chunks on the same server, consider `disable_if_ftb_chunks_loaded = true`.
- Paper remains available for WorldNotes because ChunkLock defaults to maps.

## Known Limitations

- No teams or shared ownership.
- No GUI management screen.
- No chunk loading.
- No economy or rent/upkeep.
- JourneyMap support is optional and client-side only.
- Xaero map overlays are not implemented.
- FTB Chunks compatibility is limited to optional self-disable behavior.

## Manual Test Checklist

- Fresh client launch.
- Fresh dedicated server launch.
- Claim with map.
- Unclaim with map.
- Confirm sneak-right-clicking with map does not create filled maps.
- Claim persistence after restart.
- Two-player protection test.
- Operator bypass test.
- Config reload test.
- JourneyMap installed test.
- Xaero installed test.
- FTB Chunks installed test.

## Attribution

ChunkLock is part of the Worldloom-adjacent tooling work by GOGLEO (GitHub: lefoxxy).
