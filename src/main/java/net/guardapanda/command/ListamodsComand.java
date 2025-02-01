package net.mcreator.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

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
                    List<String> nomesMods = mods.stream()
                        .map(IModInfo::getDisplayName)
                        .collect(Collectors.toList());

                    // Dividir a lista em várias mensagens menores
                    List<String> mensagens = dividirMensagem(nomesMods, 256);

                    // Enviar as mensagens ao jogador
                    source.sendSuccess(() -> Component.literal("Mods instalados por " + jogador.getDisplayName().getString() + ":"), false);
                    for (String mensagem : mensagens) {
                        source.sendSuccess(() -> Component.literal(mensagem), false);
                    }

                    return 1;
                })
            )
        );
    }

    private static List<String> dividirMensagem(List<String> nomesMods, int limite) {
        List<String> mensagens = new ArrayList<>();
        StringBuilder mensagemAtual = new StringBuilder();

        for (String mod : nomesMods) {
            if (mensagemAtual.length() + mod.length() + 2 > limite) { // +2 por causa da vírgula e espaço
                mensagens.add(mensagemAtual.toString());
                mensagemAtual.setLength(0);
            }
            if (!mensagemAtual.isEmpty()) {
                mensagemAtual.append(", ");
            }
            mensagemAtual.append(mod);
        }

        if (!mensagemAtual.isEmpty()) {
            mensagens.add(mensagemAtual.toString());
        }

        return mensagens;
    }
}
