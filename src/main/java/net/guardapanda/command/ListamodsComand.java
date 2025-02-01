package net.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component; // Substitua TextComponent por Component
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber
public class ListamodsCommand {
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("listamods")
            .then(Commands.argument("jogador", EntityArgument.player())
                .executes(context -> {
                    ServerPlayer jogador = EntityArgument.getPlayer(context, "jogador");
                    CommandSourceStack source = context.getSource();
                    
                    // Obter a lista de mods
                    Collection<IModInfo> mods = ModList.get().getMods();
                    
                    // Converter a lista de mods para uma string
                    String listaMods = mods.stream()
                        .map(IModInfo::getDisplayName)
                        .collect(Collectors.joining(", "));
                    
                    // Enviar a lista de mods para o jogador que executou o comando
                    source.sendSuccess(() -> Component.literal("Mods instalados por " + jogador.getDisplayName().getString() + ": " + listaMods), false);
                    
                    return 1;
                })
            )
        );
    }
}