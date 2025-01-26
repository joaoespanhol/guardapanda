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
                .then(Commands.argument("cor", StringArgumentType.word())
                    .executes(context -> {
                        String cor = StringArgumentType.getString(context, "cor");
                        CommandSourceStack source = context.getSource();
                        if (source.getEntity() instanceof ServerPlayer player) {
                            return setPlayerChatColor(player, cor);
                        }
                        source.sendFailure(Component.literal("Este comando só pode ser usado por jogadores."));
                        return 0;
                    })
                )
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
        Component coloredMessage = applyColorCodes(originalMessage.getString());
        event.setMessage(coloredMessage);
    }

    private static Component applyColorCodes(String message) {
        String processedMessage = substituteSectionSign(message);

        // Usa MutableComponent para permitir mutações (adição de texto)
        MutableComponent result = Component.empty();

        // Divide a mensagem com base nos códigos de cor §
        String[] parts = processedMessage.split("§", -1); // Usa -1 para preservar partes vazias
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

    private static int setPlayerChatColor(ServerPlayer player, String cor) {
        TextColor textColor;
        try {
            // Suporte para cores hexadecimais no formato "#RRGGBB"
            if (cor.startsWith("#")) {
                textColor = TextColor.parseColor(cor);
            } else {
                // Suporte para cores legadas usando nomes, códigos ou formato &
                if (cor.startsWith("&")) {
                    cor = cor.substring(1); // Remove o caractere &
                }
                textColor = getLegacyColor(cor);
                if (textColor == null) throw new IllegalArgumentException();
            }

            player.sendSystemMessage(Component.literal("Cor definida com sucesso: ").setStyle(Style.EMPTY.withColor(textColor)));
            return 1; // Sucesso
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Cor inválida. Use uma cor hexadecimal (#RRGGBB), &<código> ou um nome válido."));
            return 0; // Falha
        }
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

    private static TextColor getLegacyColor(String cor) {
        switch (cor.toLowerCase()) {
            case "0": case "preto": return TextColor.fromRgb(0x000000);
            case "1": case "azul escuro": return TextColor.fromRgb(0x0000AA);
            case "2": case "verde": return TextColor.fromRgb(0x00AA00);
            case "3": case "aqua escuro": return TextColor.fromRgb(0x00AAAA);
            case "4": case "vermelho": return TextColor.fromRgb(0xAA0000);
            case "5": case "roxo": return TextColor.fromRgb(0xAA00AA);
            case "6": case "ouro": return TextColor.fromRgb(0xFFAA00);
            case "7": case "cinza": return TextColor.fromRgb(0xAAAAAA);
            case "8": case "cinza escuro": return TextColor.fromRgb(0x555555);
            case "9": case "azul": return TextColor.fromRgb(0x5555FF);
            case "a": case "verde claro": return TextColor.fromRgb(0x55FF55);
            case "b": case "aqua": return TextColor.fromRgb(0x55FFFF);
            case "c": case "vermelho claro": return TextColor.fromRgb(0xFF5555);
            case "d": case "magenta": return TextColor.fromRgb(0xFF55FF);
            case "e": case "amarelo": return TextColor.fromRgb(0xFFFF55);
            case "f": case "branco": return TextColor.fromRgb(0xFFFFFF);
            default: return null;
        }
    }

    private static String substituteSectionSign(String s) {
        return s.replaceAll("&", "§");
    }
}
