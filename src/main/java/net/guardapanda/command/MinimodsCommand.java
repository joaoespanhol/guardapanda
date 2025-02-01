package net.guardapanda.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mod.EventBusSubscriber
@Mod("guardapanda")
public class MinimodsCommand {
    private static String customMotd = "§aBem-vindo ao servidor! §6Divirta-se!";
    private static final Path CONFIG_PATH = Paths.get("config", "guardapanda_motd.txt");

    // Evento que carrega o MOTD antes do servidor iniciar completamente
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        loadMotdFromFile();
        MinecraftServer server = event.getServer();
        server.setMotd(customMotd); // Define o MOTD na inicialização
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("motd")
            .then(Commands.literal("set")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> setMotd(context.getSource(), StringArgumentType.getString(context, "message")))))
            .then(Commands.literal("get")
                .executes(context -> getMotd(context.getSource()))));
    }

    private static int setMotd(CommandSourceStack source, String message) {
        customMotd = message.replace("&", "§"); // Converte cores
        source.sendSuccess(() -> Component.literal("§aMOTD atualizado para: §r" + customMotd), true);
        saveMotdToFile();
        
        // Atualiza o MOTD do servidor imediatamente
        if (source.getServer() != null) {
            source.getServer().setMotd(customMotd);
        }

        return 1;
    }

    private static int getMotd(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§aMOTD atual: §r" + customMotd), false);
        return 1;
    }

    private static void saveMotdToFile() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, customMotd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadMotdFromFile() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                customMotd = Files.readString(CONFIG_PATH);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
