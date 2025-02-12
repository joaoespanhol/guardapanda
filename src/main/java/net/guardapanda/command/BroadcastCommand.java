
package net.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.google.gson.Gson;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mod.EventBusSubscriber
public class BroadcastCommand {
    // Configurações carregadas do arquivo JSON
    private static CommandConfig config;

    static {
        config = loadConfig(); // Carrega as configurações ao inicializar a classe
    }

    // Classe interna para armazenar as configurações
    private static class CommandConfig {
        private String commandSL = "SL";
        private String prefixSL = "&a[SL]"; // Verde
        private String commandHSMP = "HSMP";
        private String prefixHSMP = "&3[HSMP]"; // Azul escuro
        private String commandEntidade = "Entidade";
        private String prefixEntidade = "&c[Entidade]"; // Vermelho

        // Getters e Setters
        public String getCommandSL() {
            return commandSL;
        }

        public void setCommandSL(String commandSL) {
            this.commandSL = commandSL;
        }

        public String getPrefixSL() {
            return prefixSL;
        }

        public void setPrefixSL(String prefixSL) {
            this.prefixSL = prefixSL;
        }

        public String getCommandHSMP() {
            return commandHSMP;
        }

        public void setCommandHSMP(String commandHSMP) {
            this.commandHSMP = commandHSMP;
        }

        public String getPrefixHSMP() {
            return prefixHSMP;
        }

        public void setPrefixHSMP(String prefixHSMP) {
            this.prefixHSMP = prefixHSMP;
        }

        public String getCommandEntidade() {
            return commandEntidade;
        }

        public void setCommandEntidade(String commandEntidade) {
            this.commandEntidade = commandEntidade;
        }

        public String getPrefixEntidade() {
            return prefixEntidade;
        }

        public void setPrefixEntidade(String prefixEntidade) {
            this.prefixEntidade = prefixEntidade;
        }
    }

    // Método para carregar as configurações do arquivo JSON
    private static CommandConfig loadConfig() {
        Gson gson = new Gson();
        String configDir = "config";
        String configFile = configDir + "/broadcastConfig.json";

        try {
            // Verifica se o diretório de configuração existe
            Path configDirPath = Paths.get(configDir);
            if (!Files.exists(configDirPath)) {
                Files.createDirectories(configDirPath);
            }

            // Verifica se o arquivo de configuração existe
            Path configFilePath = Paths.get(configFile);
            if (!Files.exists(configFilePath)) {
                // Cria um arquivo de configuração padrão
                CommandConfig defaultConfig = new CommandConfig();
                saveConfig(defaultConfig, gson, configFile);
                return defaultConfig;
            }

            // Carrega o arquivo de configuração
            try (FileReader reader = new FileReader(configFile)) {
                return gson.fromJson(reader, CommandConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new CommandConfig(); // Retorna configuração padrão em caso de erro
        }
    }

    // Método para salvar as configurações no arquivo JSON
    private static void saveConfig(CommandConfig config, Gson gson, String configFile) {
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Registro dos comandos
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal(config.getCommandSL()) // Comando personalizado
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        broadcastMessage(context.getSource(), config.getPrefixSL() + " " + message);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        );

        event.getDispatcher().register(
            Commands.literal(config.getCommandHSMP()) // Comando personalizado
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        broadcastMessage(context.getSource(), config.getPrefixHSMP() + " " + message);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        );

        event.getDispatcher().register(
            Commands.literal(config.getCommandEntidade()) // Comando personalizado
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> {
                        String message = StringArgumentType.getString(context, "message");
                        broadcastMessage(context.getSource(), config.getPrefixEntidade() + " " + message);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        );
    }

    // Método para enviar uma mensagem broadcast com suporte a cores
    private static void broadcastMessage(CommandSourceStack source, String message) {
        // Substitui '&' por '§' para permitir cores e formatações
        String formattedMessage = message.replace('&', '§');
        Component textMessage = Component.literal(formattedMessage);
        source.getServer().getPlayerList().getPlayers().forEach(player -> {
            player.sendSystemMessage(textMessage);
        });
    }
}