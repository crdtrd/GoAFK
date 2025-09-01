package com.drtdrc.goafk;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.text.Text.literal;

public final class AFKCommand {
    private AFKCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            CommandManager.literal("afk").requires(src -> src.getEntity() instanceof ServerPlayerEntity).executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                boolean nowAFK = AFKManager.toggleAfkAndKick(player);
                if (!nowAFK) {
                    context.getSource().sendFeedback(() -> Text.literal("Called /afk"), true);
                }
                return 1;
            })
        );
    }
}