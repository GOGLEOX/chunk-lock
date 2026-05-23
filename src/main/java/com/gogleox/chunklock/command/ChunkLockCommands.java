package com.gogleox.chunklock.command;

import com.gogleox.chunklock.claim.ClaimData;
import com.gogleox.chunklock.claim.ClaimManager;
import com.mojang.authlib.GameProfile;
import com.gogleox.chunklock.config.ChunkLockConfig;
import com.gogleox.chunklock.network.ChunkLockNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ChunkLockCommands {
    private static final ClaimManager CLAIM_MANAGER = new ClaimManager();

    private ChunkLockCommands() {
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(literal("chunklock")
                .then(literal("claim")
                        .executes(context -> claimCurrentChunk(context.getSource())))
                .then(literal("unclaim")
                        .executes(context -> unclaimCurrentChunk(context.getSource())))
                .then(literal("info")
                        .executes(context -> showCurrentChunkInfo(context.getSource())))
                .then(literal("inspect")
                        .requires(ChunkLockCommands::isAdmin)
                        .executes(context -> toggleInspect(context.getSource())))
                .then(literal("show")
                        .executes(context -> showCurrentChunkBoundary(context.getSource())))
                .then(literal("trust")
                        .then(playerNameArgument()
                                .executes(context -> trustPlayer(context.getSource(), playerName(context)))))
                .then(literal("untrust")
                        .then(playerNameArgument()
                                .executes(context -> untrustPlayer(context.getSource(), playerName(context)))))
                .then(literal("trusted")
                        .executes(context -> listTrustedPlayers(context.getSource())))
                .then(literal("list")
                        .executes(context -> listOwnClaims(context.getSource())))
                .then(literal("whitelist")
                        .then(literal("add")
                                .then(playerNameArgument()
                                        .executes(context -> trustPlayer(context.getSource(), playerName(context)))))
                        .then(literal("remove")
                                .then(playerNameArgument()
                                        .executes(context -> untrustPlayer(context.getSource(), playerName(context)))))
                        .then(literal("list")
                                .executes(context -> listAccess(context.getSource(), true))))
                .then(literal("blacklist")
                        .then(literal("add")
                                .then(playerNameArgument()
                                        .executes(context -> blockPlayer(context.getSource(), playerName(context)))))
                        .then(literal("remove")
                                .then(playerNameArgument()
                                        .executes(context -> unblockPlayer(context.getSource(), playerName(context)))))
                        .then(literal("list")
                                .executes(context -> listAccess(context.getSource(), false))))
                .then(literal("admin")
                        .requires(ChunkLockCommands::isAdmin)
                        .then(literal("claim")
                                .executes(context -> adminClaimCurrentChunk(context.getSource()))
                                .then(playerNameArgument()
                                        .executes(context -> adminClaimCurrentChunkFor(context.getSource(), playerName(context)))))
                        .then(literal("unclaim")
                                .executes(context -> adminUnclaimCurrentChunk(context.getSource())))
                        .then(literal("transfer")
                                .then(playerNameArgument()
                                        .executes(context -> adminTransferCurrentChunk(context.getSource(), playerName(context)))))
                        .then(literal("info")
                                .executes(context -> showCurrentChunkInfo(context.getSource())))
                        .then(literal("listall")
                                .executes(context -> listAllClaims(context.getSource())))
                        .then(literal("listplayer")
                                .then(playerNameArgument()
                                        .executes(context -> listPlayerClaims(context.getSource(), playerName(context)))))
                        .then(literal("clearplayer")
                                .then(playerNameArgument()
                                        .executes(context -> clearPlayerClaims(context.getSource(), playerName(context)))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> playerNameArgument() {
        return com.mojang.brigadier.builder.RequiredArgumentBuilder.argument("player", StringArgumentType.word());
    }

    private static String playerName(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "player");
    }

    private static int claimCurrentChunk(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos pos = player.chunkPosition();
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        if (claim == null) {
            ClaimManager.ClaimResult result = CLAIM_MANAGER.claimChunkWithResult(player, level, pos);

            if (result == ClaimManager.ClaimResult.CLAIMED) {
                source.sendSuccess(() -> message("message.chunklock.claimed"), false);
                return 1;
            }

            source.sendFailure(messageForClaimResult(result));
            return 0;
        }

        if (claim.ownerId().equals(player.getUUID())) {
            source.sendFailure(message("command.chunklock.already_own"));
            return 0;
        }

        source.sendFailure(message("message.chunklock.already_claimed_by", claim.ownerLastKnownName()));
        return 0;
    }

    private static int unclaimCurrentChunk(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos pos = player.chunkPosition();
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        if (claim == null) {
            source.sendFailure(message("command.chunklock.not_claimed"));
            return 0;
        }

        if (!claim.ownerId().equals(player.getUUID())) {
            source.sendFailure(message("message.chunklock.already_claimed_by", claim.ownerLastKnownName()));
            return 0;
        }

        if (CLAIM_MANAGER.unclaimChunk(player, level, pos)) {
            source.sendSuccess(() -> message("message.chunklock.unclaimed"), false);
            return 1;
        }

        source.sendFailure(message("command.chunklock.unable_to_unclaim"));
        return 0;
    }

    private static int trustPlayer(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        return updateCurrentClaimAccess(source, targetName, true, true);
    }

    private static int untrustPlayer(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        return updateCurrentClaimAccess(source, targetName, true, false);
    }

    private static int listTrustedPlayers(CommandSourceStack source) throws CommandSyntaxException {
        return listAccess(source, true);
    }

    private static int blockPlayer(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        return updateCurrentClaimAccess(source, targetName, false, true);
    }

    private static int unblockPlayer(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        return updateCurrentClaimAccess(source, targetName, false, false);
    }

    private static int updateCurrentClaimAccess(CommandSourceStack source, String targetName, boolean whitelist, boolean add) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerPlayer target = findOnlinePlayer(source, targetName);

        if (target == null) {
            source.sendFailure(message("command.chunklock.player_not_found", targetName));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        ChunkPos pos = player.chunkPosition();
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        if (claim == null) {
            source.sendFailure(message("command.chunklock.not_claimed"));
            return 0;
        }

        if (!claim.ownerId().equals(player.getUUID()) && !isAdmin(source)) {
            source.sendFailure(message("command.chunklock.not_owner"));
            return 0;
        }

        if (claim.ownerId().equals(target.getUUID())) {
            source.sendFailure(message("command.chunklock.access.owner"));
            return 0;
        }

        boolean updated = CLAIM_MANAGER.updateClaimAccess(level, pos, currentClaim -> {
            UUID targetId = target.getUUID();
            String name = target.getGameProfile().getName();

            if (whitelist && add) {
                return currentClaim.withTrustedPlayer(targetId, name);
            }

            if (whitelist) {
                return currentClaim.withoutTrustedPlayer(targetId);
            }

            if (add) {
                return currentClaim.withBlockedPlayer(targetId, name);
            }

            return currentClaim.withoutBlockedPlayer(targetId);
        });

        if (!updated) {
            source.sendFailure(message("command.chunklock.access.failed"));
            return 0;
        }

        String key = whitelist
                ? (add ? "command.chunklock.trust.added" : "command.chunklock.trust.removed")
                : (add ? "command.chunklock.blacklist.added" : "command.chunklock.blacklist.removed");
        source.sendSuccess(() -> message(key, target.getGameProfile().getName()), false);
        return 1;
    }

    private static int listAccess(CommandSourceStack source, boolean whitelist) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ClaimData claim = CLAIM_MANAGER.getClaim(player.serverLevel(), player.chunkPosition());

        if (claim == null) {
            source.sendFailure(message("command.chunklock.not_claimed"));
            return 0;
        }

        if (!claim.ownerId().equals(player.getUUID()) && !isAdmin(source)) {
            source.sendFailure(message("command.chunklock.not_owner"));
            return 0;
        }

        Map<UUID, String> players = whitelist ? claim.trustedPlayers() : claim.blockedPlayers();
        source.sendSuccess(() -> message(whitelist ? "command.chunklock.trust.title" : "command.chunklock.blacklist.title", players.size()), false);

        if (players.isEmpty()) {
            source.sendSuccess(() -> message("command.chunklock.list.empty"), false);
            return 0;
        }

        players.values().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(name -> source.sendSuccess(() -> message("command.chunklock.access.entry", name), false));
        return players.size();
    }

    private static int showCurrentChunkInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos pos = player.chunkPosition();
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        source.sendSuccess(() -> message("command.chunklock.info.title"), false);
        source.sendSuccess(() -> message("command.chunklock.info.status", claim == null ? "unclaimed" : "claimed"), false);

        if (claim != null) {
            source.sendSuccess(() -> message("command.chunklock.info.owner", claim.ownerLastKnownName()), false);
            source.sendSuccess(() -> message("command.chunklock.info.access", claim.trustedPlayers().size(), claim.blockedPlayers().size()), false);
        }

        source.sendSuccess(() -> message("command.chunklock.info.dimension", level.dimension().location()), false);
        source.sendSuccess(() -> message("command.chunklock.info.chunk", pos.x, pos.z), false);
        return claim == null ? 0 : 1;
    }

    private static int listOwnClaims(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Collection<ClaimData> claims = CLAIM_MANAGER.getClaimsForPlayer(player.getUUID());
        sendClaimList(source, "command.chunklock.list.own", claims);
        return claims.size();
    }

    private static int toggleInspect(CommandSourceStack source) throws CommandSyntaxException {
        ChunkLockInspect.toggle(source.getPlayerOrException());
        return 1;
    }

    private static int showCurrentChunkBoundary(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ChunkLockNetwork.showChunkBoundary(
                player,
                ChunkLockConfig.CLAIM_SHOW_DURATION_TICKS.get(),
                ChunkLockConfig.CLAIM_SHOW_MAX_CHUNKS.get()
        );
        source.sendSuccess(() -> message("command.chunklock.show"), false);
        return 1;
    }

    private static int adminClaimCurrentChunk(CommandSourceStack source) throws CommandSyntaxException {
        return adminClaimCurrentChunkFor(source, source.getPlayerOrException().getGameProfile().getName());
    }

    private static int adminClaimCurrentChunkFor(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        ServerPlayer target = findOnlinePlayer(source, targetName);

        if (target == null) {
            source.sendFailure(message("command.chunklock.player_not_found", targetName));
            return 0;
        }

        ClaimData existing = CLAIM_MANAGER.getClaim(actor.serverLevel(), actor.chunkPosition());

        if (existing != null) {
            source.sendFailure(message("message.chunklock.already_claimed_by", existing.ownerLastKnownName()));
            return 0;
        }

        ClaimManager.ClaimResult result = CLAIM_MANAGER.claimChunkWithResult(target, actor.serverLevel(), actor.chunkPosition());
        if (result == ClaimManager.ClaimResult.CLAIMED) {
            source.sendSuccess(() -> message("command.chunklock.admin.claimed", target.getGameProfile().getName()), true);
            return 1;
        }

        source.sendFailure(messageForClaimResult(result));
        return 0;
    }

    private static int adminUnclaimCurrentChunk(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos pos = player.chunkPosition();
        ClaimData claim = CLAIM_MANAGER.getClaim(level, pos);

        if (claim == null) {
            source.sendFailure(message("command.chunklock.not_claimed"));
            return 0;
        }

        if (CLAIM_MANAGER.removeClaim(level, pos)) {
            source.sendSuccess(() -> message("command.chunklock.admin.unclaimed", claim.ownerLastKnownName()), true);
            return 1;
        }

        source.sendFailure(message("command.chunklock.unable_to_unclaim"));
        return 0;
    }

    private static int adminTransferCurrentChunk(CommandSourceStack source, String targetName) throws CommandSyntaxException {
        ServerPlayer actor = source.getPlayerOrException();
        ServerPlayer target = findOnlinePlayer(source, targetName);

        if (target == null) {
            source.sendFailure(message("command.chunklock.player_not_found", targetName));
            return 0;
        }

        if (CLAIM_MANAGER.transferClaim(actor.serverLevel(), actor.chunkPosition(), target)) {
            source.sendSuccess(() -> message("command.chunklock.admin.transferred", target.getGameProfile().getName()), true);
            return 1;
        }

        source.sendFailure(message("command.chunklock.not_claimed"));
        return 0;
    }

    private static int listAllClaims(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Collection<ClaimData> claims = CLAIM_MANAGER.getAllClaims(player.serverLevel());
        sendClaimList(source, "command.chunklock.list.all", claims);
        return claims.size();
    }

    private static int listPlayerClaims(CommandSourceStack source, String targetName) {
        ServerPlayer target = findOnlinePlayer(source, targetName);

        if (target == null) {
            source.sendFailure(message("command.chunklock.player_not_found", targetName));
            return 0;
        }

        Collection<ClaimData> claims = CLAIM_MANAGER.getClaimsForPlayer(target.getUUID());
        source.sendSuccess(() -> message("command.chunklock.admin.listplayer", target.getGameProfile().getName()), true);
        sendClaimList(source, "command.chunklock.list.player", claims);
        return claims.size();
    }

    private static int clearPlayerClaims(CommandSourceStack source, String targetName) {
        ServerPlayer target = findOnlinePlayer(source, targetName);

        if (target == null) {
            source.sendFailure(message("command.chunklock.player_not_found", targetName));
            return 0;
        }

        int removed = CLAIM_MANAGER.removeAllClaimsForPlayer(target.getUUID());
        source.sendSuccess(() -> message("command.chunklock.admin.clearplayer", removed, target.getGameProfile().getName()), true);
        return removed;
    }

    private static void sendClaimList(CommandSourceStack source, String titleKey, Collection<ClaimData> claims) {
        source.sendSuccess(() -> message("command.chunklock.list.title", message(titleKey), claims.size()), false);

        if (claims.isEmpty()) {
            source.sendSuccess(() -> message("command.chunklock.list.empty"), false);
            return;
        }

        claims.stream()
                .sorted(Comparator
                        .comparing((ClaimData claim) -> claim.dimension().toString())
                        .thenComparingInt(ClaimData::chunkX)
                        .thenComparingInt(ClaimData::chunkZ))
                .forEach(claim -> source.sendSuccess(
                        () -> message("command.chunklock.list.entry", claim.dimension(), claim.chunkX(), claim.chunkZ(), claim.ownerLastKnownName()),
                        false
                ));
    }

    private static ServerPlayer findOnlinePlayer(CommandSourceStack source, String name) {
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(name)) {
                return player;
            }
        }

        return null;
    }

    public static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return true;
        }

        GameProfile profile = player.getGameProfile();
        return source.getServer().getPlayerList().isOp(profile);
    }

    private static Component messageForClaimResult(ClaimManager.ClaimResult result) {
        return switch (result) {
            case CLAIMED -> message("message.chunklock.claimed");
            case ALREADY_CLAIMED -> message("message.chunklock.already_claimed");
            case CLAIM_LIMIT_REACHED -> message("message.chunklock.claim_limit_reached");
            case DIMENSION_DISABLED -> message("message.chunklock.dimension_disabled");
        };
    }

    private static Component message(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
