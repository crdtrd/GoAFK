package com.drtdrc.goafk;

import com.drtdrc.goafk.storage.AFKAnchorsState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
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
                                            boolean ok = AFKManager.addAnchor(src.getWorld(), pos, "");
                                            src.sendFeedback(() -> literal(ok ? "Anchor added" : "Anchor already exists here"), true);
                                            return ok ? 1 : 0;
                                        })
                                )
                        )
                        // /afk anchor remove [x y z]
                        .then(CommandManager.literal("remove")
                                .then(CommandManager.literal("pos")
                                        .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                                .suggests(POS_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    var src = ctx.getSource();
                                                    BlockPos pos = BlockPosArgumentType.getLoadedBlockPos(ctx, "pos");
                                                    boolean ok = AFKManager.removeAnchor(src.getWorld(), pos, "");
                                                    if (ok) src.sendFeedback(() -> literal("Anchor removed"), true);
                                                    else    src.sendError(literal("No anchor at this position"));
                                                    return ok ? 1 : 0;
                                                })
                                        )
                                )
                                .then(CommandManager.literal("name")
                                        .then(CommandManager.argument("name", StringArgumentType.word())
                                                .suggests(NAME_SUGGESTIONS)
                                                .executes(ctx -> {
                                                    var src = ctx.getSource();
                                                    var world = src.getWorld();
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    boolean ok = AFKManager.removeAnchor(world, null, name);
                                                    if (ok) src.sendFeedback(() -> Text.literal("Removed anchors named " + name), true);
                                                    else src.sendError(Text.literal("No anchors named " + name + " in this world"));
                                                    return ok ? 1 : 0;
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
                                                AFKManager.removeAnchor(world, p, "");
                                            }
                                            src.sendFeedback(() -> Text.literal("All anchors removed"), true);
                                            return 1;
                                        })
                                )
                        )
                )
        );
    }

    private static final SuggestionProvider<ServerCommandSource> POS_SUGGESTIONS = (ctx, builder) -> {
        ServerWorld world = ctx.getSource().getWorld();
        var state = AFKAnchorsState.get(world);
        for (AFKAnchorsState.AFKAnchor e : state.getAllEntries()) {
            var p = e.pos();
            String suggestion = p.getX() + " " + p.getY() + " " + p.getZ();
            Text tooltip = Objects.equals(e.name(), null)
                    ? Text.literal("unowned")
                    : Text.literal("name: " + e.name());
            builder.suggest(suggestion, tooltip);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> NAME_SUGGESTIONS = (ctx, builder) -> {
        ServerWorld world = ctx.getSource().getWorld();

        for (var e : AFKAnchorsState.get(world).getAllEntries()) {
            String owner = e.name();
            if (owner != null && !owner.isBlank()) {
                builder.suggest(owner);
            }
        }
        return builder.buildFuture();
    };

}