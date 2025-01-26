
package net.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;

@Mod.EventBusSubscriber
public class BroadcastCommand {
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("SL") // Comando /SL
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        broadcastMessage(context.getSource(), "[SL] " + message);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        );

        event.getDispatcher().register(
            Commands.literal("HSMP") // Comando /HSMP
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        broadcastMessage(context.getSource(), "[HSMP] " + message);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        );

        event.getDispatcher().register(
            Commands.literal("Entidade") // Comando /Entidade
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        broadcastMessage(context.getSource(), "[Entidade] " + message);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        );
    }

    private static void broadcastMessage(CommandSourceStack source, String message) {
        Component textMessage = Component.literal(message);
        source.getServer().getPlayerList().getPlayers().forEach(player -> {
            player.sendSystemMessage(textMessage);
        });
    }
}