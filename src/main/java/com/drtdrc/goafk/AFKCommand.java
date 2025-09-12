package com.drtdrc.goafk;

import com.drtdrc.goafk.storage.AFKAnchorsState;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.text.Text.literal;

public final class AFKCommand {
    private AFKCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            CommandManager.literal("afk").requires(src -> src.getEntity() instanceof ServerPlayerEntity).executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                boolean nowAFK = AFKManager.goAFKAndKick(player);
                if (!nowAFK) {
                    context.getSource().sendFeedback(() -> Text.literal("Called /afk"), true);
                }
                return 1;
            })
        );
        // Admin subcommands (/afk anchor ...)
        dispatcher.register(CommandManager.literal("afk")
                .then(CommandManager.literal("anchor")
                        .requires(src -> src.hasPermissionLevel(src.getServer().getOpPermissionLevel())) // ops
                        // /afk anchor add [x y z] [for <player>] | [forUuid <uuid>]
                        .then(CommandManager.literal("add")
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    var world = src.getWorld();
                                    BlockPos pos = BlockPos.ofFloored(src.getPosition());
                                    boolean ok = AFKManager.addAdminAnchor(world, pos, null);
                                    src.sendFeedback(() -> literal(ok ? "Anchor added at your feet" : "Anchor already exists here"), true);
                                    return ok ? 1 : 0;
                                })
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                                            boolean ok = AFKManager.addAdminAnchor(src.getWorld(), pos, null);
                                            src.sendFeedback(() -> literal(ok ? "Anchor added" : "Anchor already exists here"), true);
                                            return ok ? 1 : 0;
                                        })
//                                        .then(CommandManager.literal("for")
//                                                .then(CommandManager.argument("player", EntityArgumentType.player())
//                                                        .executes(ctx -> {
//                                                            var src = ctx.getSource();
//                                                            BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
//                                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
//                                                            boolean ok = AFKManager.addAdminAnchor(src.getWorld(), pos, target.getUuid());
//                                                            src.sendFeedback(() -> Text.literal(ok ? ("Anchor added for " + target.getName().getString())
//                                                                    : "Anchor already exists here"), true);
//                                                            return ok ? 1 : 0;
//                                                        })
//                                                )
//                                        )
//                                        .then(CommandManager.literal("forUuid")
//                                                .then(CommandManager.argument("uuid", StringArgumentType.word())
//                                                        .executes(ctx -> {
//                                                            var src = ctx.getSource();
//                                                            BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
//                                                            String s = StringArgumentType.getString(ctx, "uuid");
//                                                            UUID uuid;
//                                                            try { uuid = UUID.fromString(s); }
//                                                            catch (IllegalArgumentException ex) {
//                                                                src.sendError(literal("Invalid UUID: " + s));
//                                                                return 0;
//                                                            }
//                                                            boolean ok = AFKManager.addAdminAnchor(src.getWorld(), pos, uuid);
//                                                            src.sendFeedback(() -> literal(ok ? "Anchor added for " + uuid : "Anchor already exists here"), true);
//                                                            return ok ? 1 : 0;
//                                                        })
//                                                )
//                                        )
                                )
                        )
                        // /afk anchor remove [x y z]
                        .then(CommandManager.literal("remove")
                                .executes(ctx -> {
                                    var src = ctx.getSource();
                                    var world = src.getWorld();
                                    BlockPos pos = BlockPos.ofFloored(src.getPosition());
                                    boolean ok = AFKManager.removeAdminAnchor(world, pos);
                                    if (ok) src.sendFeedback(() -> literal("Anchor removed"), true);
                                    else    src.sendError(literal("No anchor at this position"));
                                    return ok ? 1 : 0;
                                })
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .suggests(ANCHOR_POS_SUGGESTIONS)
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                                            boolean ok = AFKManager.removeAdminAnchor(src.getWorld(), pos);
                                            if (ok) src.sendFeedback(() -> literal("Anchor removed"), true);
                                            else    src.sendError(literal("No anchor at this position"));
                                            return ok ? 1 : 0;
                                        })
                                )
                        )
                )
        );
    }

    private static final SuggestionProvider<ServerCommandSource> ANCHOR_POS_SUGGESTIONS = (ctx, builder) -> {
        ServerWorld world = ctx.getSource().getWorld();
        var state = AFKAnchorsState.get(world);
        for (AFKAnchorsState.Entry e : state.getAllEntries()) {
            var p = e.pos();
            String suggestion = p.getX() + " " + p.getY() + " " + p.getZ();
            Text tooltip = (e.owner() == null)
                    ? Text.literal("unowned")
                    : Text.literal("owner: " + e.owner());
            builder.suggest(suggestion, tooltip);
        }
        return builder.buildFuture();
    };

    public static @Nullable String resolvePlayerName(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        return Objects.requireNonNull(server.getUserCache())
                .getByUuid(uuid)
                .map(GameProfile::getName)
                .orElse(null);
    }

    // Suggest player names = owners in state + currently online
    private static final SuggestionProvider<ServerCommandSource> PLAYER_NAME_SUGGESTIONS = (ctx, builder) -> {
        Set<String> names = new HashSet<>();
        MinecraftServer server = ctx.getSource().getServer();
        // owners in state
        for (var e : AFKAnchorsState.get(ctx.getSource().getWorld()).getAllEntries()) {
            names.add(resolvePlayerName(server, e.owner()));
        }
        // online players
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            names.add(p.getGameProfile().getName());
        }
        names.forEach(builder::suggest);
        return builder.buildFuture();
    };

}