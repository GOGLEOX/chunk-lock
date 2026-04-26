package com.gogleox.chunklock.command;

import com.gogleox.chunklock.claim.ClaimData;
import com.gogleox.chunklock.claim.ClaimManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.Comparator;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ChunkLockCommands {
    private static final ClaimManager CLAIM_MANAGER = new ClaimManager();

    private ChunkLockCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("chunklock")
                .then(Commands.literal("claim")
                        .executes(context -> claimCurrentChunk(context.getSource())))
                .then(Commands.literal("unclaim")
                        .executes(context -> unclaimCurrentChunk(context.getSource())))
                .then(Commands.literal("info")
                        .executes(context -> showCurrentChunkInfo(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> listOwnClaims(context.getSource())))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> reloadConfig(context.getSource())))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("unclaim")
                                .executes(context -> adminUnclaimCurrentChunk(context.getSource())))
                        .then(Commands.literal("clearplayer")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> adminClearPlayer(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "player")
                                        ))))
                        .then(Commands.literal("listplayer")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(context -> adminListPlayer(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "player")
                                        ))))));
    }

    private static int claimCurrentChunk(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos pos = player.chunkPosition();
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        if (claim == null) {
            ClaimManager.ClaimResult result = CLAIM_MANAGER.claimChunkWithResult(player, level, pos);

            if (result == ClaimManager.ClaimResult.CLAIMED) {
                source.sendSuccess(() -> Component.literal("Chunk claimed."), false);
                return 1;
            }

            source.sendFailure(messageForClaimResult(result));
            return 0;
        }

        if (claim.ownerId().equals(player.getUUID())) {
            source.sendFailure(Component.literal("You already own this chunk."));
            return 0;
        }

        source.sendFailure(Component.literal("This chunk is already claimed by " + claim.ownerLastKnownName() + "."));
        return 0;
    }

    private static int unclaimCurrentChunk(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos pos = player.chunkPosition();
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        if (claim == null) {
            source.sendFailure(Component.literal("This chunk is not claimed."));
            return 0;
        }

        if (!claim.ownerId().equals(player.getUUID())) {
            source.sendFailure(Component.literal("This chunk is already claimed by " + claim.ownerLastKnownName() + "."));
            return 0;
        }

        if (CLAIM_MANAGER.unclaimChunk(player, level, pos)) {
            source.sendSuccess(() -> Component.literal("Chunk unclaimed."), false);
            return 1;
        }

        source.sendFailure(Component.literal("Unable to unclaim this chunk."));
        return 0;
    }

    private static int showCurrentChunkInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos pos = player.chunkPosition();
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        source.sendSuccess(() -> Component.literal("ChunkLock info").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Status: " + (claim == null ? "unclaimed" : "claimed")), false);

        if (claim != null) {
            source.sendSuccess(() -> Component.literal("Owner: " + claim.ownerLastKnownName()), false);
        }

        source.sendSuccess(() -> Component.literal("Dimension: " + level.dimension().location()), false);
        source.sendSuccess(() -> Component.literal("Chunk: " + pos.x + ", " + pos.z), false);
        return claim == null ? 0 : 1;
    }

    private static int listOwnClaims(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Collection<ClaimData> claims = CLAIM_MANAGER.getClaimsForPlayer(player.getUUID());
        sendClaimList(source, "Your claimed chunks", claims);
        return claims.size();
    }

    private static int reloadConfig(CommandSourceStack source) {
        ConfigTracker.INSTANCE.loadConfigs(ModConfig.Type.COMMON, FMLPaths.CONFIGDIR.get());
        source.sendSuccess(() -> Component.literal("ChunkLock config reloaded."), true);
        return 1;
    }

    private static int adminUnclaimCurrentChunk(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos pos = player.chunkPosition();
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        if (claim == null) {
            source.sendFailure(Component.literal("This chunk is not claimed."));
            return 0;
        }

        if (CLAIM_MANAGER.removeClaim(level, pos)) {
            source.sendSuccess(() -> Component.literal("Removed claim owned by " + claim.ownerLastKnownName() + "."), true);
            return 1;
        }

        source.sendFailure(Component.literal("Unable to remove this claim."));
        return 0;
    }

    private static int adminClearPlayer(CommandSourceStack source, Collection<GameProfile> profiles) {
        int removed = 0;

        for (GameProfile profile : profiles) {
            if (profile.getId() == null) {
                source.sendFailure(Component.literal("Could not resolve UUID for " + profile.getName() + "."));
                continue;
            }

            removed += CLAIM_MANAGER.removeAllClaimsForPlayer(profile.getId());
        }

        int removedCount = removed;
        source.sendSuccess(() -> Component.literal("Removed " + removedCount + " claim(s)."), true);
        return removed;
    }

    private static int adminListPlayer(CommandSourceStack source, Collection<GameProfile> profiles) {
        int total = 0;

        for (GameProfile profile : profiles) {
            if (profile.getId() == null) {
                source.sendFailure(Component.literal("Could not resolve UUID for " + profile.getName() + "."));
                continue;
            }

            Collection<ClaimData> claims = CLAIM_MANAGER.getClaimsForPlayer(profile.getId());
            sendClaimList(source, "Claimed chunks for " + profile.getName(), claims);
            total += claims.size();
        }

        return total;
    }

    private static void sendClaimList(CommandSourceStack source, String title, Collection<ClaimData> claims) {
        source.sendSuccess(() -> Component.literal(title + " (" + claims.size() + ")").withStyle(ChatFormatting.GOLD), false);

        if (claims.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No claims found."), false);
            return;
        }

        claims.stream()
                .sorted(Comparator
                        .comparing((ClaimData claim) -> claim.dimension().toString())
                        .thenComparingInt(ClaimData::chunkX)
                        .thenComparingInt(ClaimData::chunkZ))
                .forEach(claim -> source.sendSuccess(
                        () -> Component.literal("- " + claim.dimension() + " chunk " + claim.chunkX() + ", " + claim.chunkZ()),
                        false
                ));
    }

    private static Component messageForClaimResult(ClaimManager.ClaimResult result) {
        return switch (result) {
            case CLAIMED -> Component.literal("Chunk claimed.");
            case ALREADY_CLAIMED -> Component.literal("This chunk is already claimed.");
            case CLAIM_LIMIT_REACHED -> Component.literal("You have reached your claim limit.");
            case DIMENSION_DISABLED -> Component.literal("Claiming is disabled in this dimension.");
            case DISABLED_BY_FTB_CHUNKS -> Component.literal("ChunkLock is disabled because FTB Chunks is loaded.");
        };
    }
}
