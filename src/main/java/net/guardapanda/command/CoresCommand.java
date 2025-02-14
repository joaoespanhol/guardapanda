package net.guardapanda.command;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

@Mod.EventBusSubscriber
public class CoresCommand {

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("cores")
                .then(Commands.literal("lista")
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        return showColorList(source);
                    })
                )
        );
    }

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent event) {
        Component originalMessage = event.getMessage();
        String rawMessage = originalMessage.getString(); // Obtém a mensagem como string
        Component coloredMessage = applyColorCodes(rawMessage); // Aplica os códigos de cor
        event.setMessage(coloredMessage); // Define a mensagem com as cores aplicadas
    }

    private static Component applyColorCodes(String message) {
        // Substitui & por § (código de cor do Minecraft)
        message = message.replaceAll("&", "§");

        // Usa MutableComponent para construir a mensagem com cores
        MutableComponent result = Component.empty();

        // Divide a mensagem com base nos códigos de cor §
        String[] parts = message.split("§", -1); // Usa -1 para preservar partes vazias
        boolean firstPart = true;

        for (String part : parts) {
            if (firstPart) {
                // A primeira parte não tem um código de cor associado
                result.append(Component.literal(part));
                firstPart = false;
                continue;
            }

            if (part.isEmpty()) continue;

            char code = part.charAt(0); // O código da cor ou formatação
            String text = part.substring(1); // O texto após o código

            TextColor color = getLegacyColor(String.valueOf(code));
            Style style = (color != null) ? Style.EMPTY.withColor(color) : Style.EMPTY;

            // Adiciona o texto com o estilo ao MutableComponent
            result.append(Component.literal(text).setStyle(style));
        }

        return result; // Retorna o componente final com as cores aplicadas
    }

    private static int showColorList(CommandSourceStack source) {
        String[] colors = {
            "&0 - Preto",
            "&1 - Azul Escuro",
            "&2 - Verde",
            "&3 - Aqua Escuro",
            "&4 - Vermelho",
            "&5 - Roxo",
            "&6 - Ouro",
            "&7 - Cinza",
            "&8 - Cinza Escuro",
            "&9 - Azul",
            "&a - Verde Claro",
            "&b - Aqua",
            "&c - Vermelho Claro",
            "&d - Magenta",
            "&e - Amarelo",
            "&f - Branco"
        };

        for (String color : colors) {
            source.sendSuccess(() -> Component.literal(color), false);
        }

        return 1; // Sucesso
    }

    private static TextColor getLegacyColor(String code) {
        switch (code.toLowerCase()) {
            case "0": return TextColor.fromRgb(0x000000); // Preto
            case "1": return TextColor.fromRgb(0x0000AA); // Azul Escuro
            case "2": return TextColor.fromRgb(0x00AA00); // Verde
            case "3": return TextColor.fromRgb(0x00AAAA); // Aqua Escuro
            case "4": return TextColor.fromRgb(0xAA0000); // Vermelho
            case "5": return TextColor.fromRgb(0xAA00AA); // Roxo
            case "6": return TextColor.fromRgb(0xFFAA00); // Ouro
            case "7": return TextColor.fromRgb(0xAAAAAA); // Cinza
            case "8": return TextColor.fromRgb(0x555555); // Cinza Escuro
            case "9": return TextColor.fromRgb(0x5555FF); // Azul
            case "a": return TextColor.fromRgb(0x55FF55); // Verde Claro
            case "b": return TextColor.fromRgb(0x55FFFF); // Aqua
            case "c": return TextColor.fromRgb(0xFF5555); // Vermelho Claro
            case "d": return TextColor.fromRgb(0xFF55FF); // Magenta
            case "e": return TextColor.fromRgb(0xFFFF55); // Amarelo
            case "f": return TextColor.fromRgb(0xFFFFFF); // Branco
            default: return null; // Código inválido
        }
    }
}