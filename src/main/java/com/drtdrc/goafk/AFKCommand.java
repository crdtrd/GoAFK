package com.drtdrc.goafk;

import com.drtdrc.goafk.storage.AFKAnchorsState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

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
                                    boolean ok = AFKManager.addAnchor(world, pos, null);
                                    src.sendFeedback(() -> literal(ok ? "Anchor added at your feet" : "Anchor already exists here"), true);
                                    return ok ? 1 : 0;
                                })
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                                            boolean ok = AFKManager.addAnchor(src.getWorld(), pos, null);
                                            src.sendFeedback(() -> literal(ok ? "Anchor added" : "Anchor already exists here"), true);
                                            return ok ? 1 : 0;
                                        })
                                )
                        )
                        // /afk anchor remove [x y z]
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.literal("pos")
                                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                                .suggests(ANCHOR_POS_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    var src = ctx.getSource();
                                                    BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                                                    boolean ok = AFKManager.removeAnchor(src.getWorld(), pos);
                                                    if (ok) src.sendFeedback(() -> literal("Anchor removed"), true);
                                                    else    src.sendError(literal("No anchor at this position"));
                                                    return ok ? 1 : 0;
                                                })
                                        )
                                )
                                .then(CommandManager.literal("player")
                                        .then(CommandManager.argument("name", StringArgumentType.word())
                                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    var src = ctx.getSource();
                                                    var world = src.getWorld();
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    List<BlockPos> anchorPos = AFKAnchorsState.get(world).removeAllByOwner(name);
                                                    if (anchorPos.isEmpty()) {
                                                        src.sendError(Text.literal("No anchors found for " + name + " in this world"));
                                                        return 0;
                                                    }
                                                    for (BlockPos p : anchorPos) {
                                                        AFKManager.removeTicketsAround(world, p, AFKManager.computeRadius(src.getServer()));
                                                    }
                                                    src.sendFeedback(() -> Text.literal("Removed anchor for " + name), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(CommandManager.literal("all")
                                        .executes(ctx -> {
                                            var src = ctx.getSource();
                                            var world = src.getWorld();
                                            @NotNull @Unmodifiable List<BlockPos> pos = AFKManager.getAnchorPositions(world);
                                            if (pos.isEmpty()) {
                                                src.sendFeedback(() -> Text.literal("No anchors removed"), true);
                                                return 0;
                                            }
                                            for (BlockPos p : pos) {
                                                AFKManager.removeAnchor(world, p);
                                            }
                                            src.sendFeedback(() -> Text.literal("All anchors removed"), true);
                                            return 1;
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
            Text tooltip = Objects.equals(e.owner(), null)
                    ? Text.literal("unowned")
                    : Text.literal("owner: " + e.owner());
            builder.suggest(suggestion, tooltip);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> PLAYER_NAME_SUGGESTIONS = (ctx, builder) -> {
        Set<String> names = new HashSet<>();
        MinecraftServer server = ctx.getSource().getServer();
        ServerWorld world = ctx.getSource().getWorld();

        // owners in state
        for (var e : AFKAnchorsState.get(world).getAllEntries()) {
            String owner = e.owner();
            if (owner != null && !owner.isBlank()) {
                names.add(owner);
            }
        }

        // online players
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            String n = p.getGameProfile().getName();
            if (n != null && !n.isBlank()) {
                names.add(n);
            }
        }

        for (String n : names) builder.suggest(n);
        return builder.buildFuture();
    };

}