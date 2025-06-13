package net.guardapanda.command;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CoresCommand {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString(); // Extrai o texto do Component

        // Formata a mensagem com cores
        Component formattedMessage = formatMessage(player.getDisplayName().getString(), message);

        // Cancela o evento original para evitar a mensagem sem formatação
        event.setCanceled(true);

        // Envia a mensagem formatada para todos os jogadores
        player.getServer().getPlayerList().broadcastSystemMessage(formattedMessage, false);
    }

    private static Component formatMessage(String playerName, String message) {
        // Formata o nome do jogador e a mensagem
        MutableComponent formattedMessage = Component.literal("");

        // Adiciona o nome do jogador
        formattedMessage.append(Component.literal(playerName).withStyle(style -> style.withColor(0xFFFFFF)));

        // Adiciona a mensagem formatada
        formattedMessage.append(Component.literal(": "));

        // Aplica a formatação de cores na mensagem
        if (message.contains("&")) {
            String[] parts = message.split("&");
            for (String part : parts) {
                if (part.isEmpty()) continue;

                char code = part.charAt(0);
                String text = part.substring(1);

                switch (code) {
                    case '0' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0x000000))); // Preto
                    case '1' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0x0000AA))); // Azul Escuro
                    case '2' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0x00AA00))); // Verde Escuro
                    case '3' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0x00AAAA))); // Ciano Escuro
                    case '4' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0xAA0000))); // Vermelho Escuro
                    case '5' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0xAA00AA))); // Roxo
                    case '6' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0xFFAA00))); // Laranja
                    case '7' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0xAAAAAA))); // Cinza
                    case '8' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0x555555))); // Cinza Escuro
                    case '9' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0x5555FF))); // Azul Claro
                    case 'a' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0x55FF55))); // Verde Claro
                    case 'b' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0x55FFFF))); // Ciano Claro
                    case 'c' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0xFF5555))); // Vermelho Claro
                    case 'd' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0xFF55FF))); // Rosa
                    case 'e' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0xFFFF55))); // Amarelo
                    case 'f' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0xFFFFFF))); // Branco
                    case 'k' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withObfuscated(true))); // Texto obfuscado
                    case 'l' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withBold(true))); // Negrito
                    case 'm' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withStrikethrough(true))); // Tachado
                    case 'n' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withUnderlined(true))); // Sublinhado
                    case 'o' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withItalic(true))); // Itálico
                    case 'r' -> formattedMessage.append(Component.literal(text).withStyle(style -> style.withColor(0xFFFFFF))); // Resetar formatação
                    default -> formattedMessage.append(Component.literal("&" + part)); // Código inválido
                }
            }
        } else {
            formattedMessage.append(Component.literal(message));
        }

        return formattedMessage;
    }
}