package net.guardapanda.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.ServerChatEvent; // Novo import

import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WebhookCommand {

    private static final Path CONFIG_PATH = Paths.get("config", "guardapanda_webhooks.json");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private static JsonObject config;

    // Carrega a configuração ao iniciar o servidor
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        loadConfig();
    }

    // Carrega o arquivo de configuração
    private static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                config = new Gson().fromJson(new FileReader(CONFIG_PATH.toFile()), JsonObject.class);
                System.out.println("[DEBUG] Configuração carregada com sucesso.");
                System.out.println("[DEBUG] Conteúdo do arquivo de configuração: " + config);
            } else {
                // Cria um arquivo de configuração padrão
                config = new JsonObject();

                JsonObject webhooks = new JsonObject();
                webhooks.addProperty("death", "https://discord.com/api/webhooks/SEU_WEBHOOK_MORTES");
                webhooks.addProperty("join_leave", "https://discord.com/api/webhooks/SEU_WEBHOOK_ENTRADA_SAIDA");
                webhooks.addProperty("bans_kicks", "https://discord.com/api/webhooks/SEU_WEBHOOK_BANIMENTOS_KICKS");
                webhooks.addProperty("chat", "https://discord.com/api/webhooks/SEU_WEBHOOK_CHAT"); // Novo webhook para o chat
                config.add("webhooks", webhooks);

                JsonObject commands = new JsonObject();
                commands.addProperty("kick", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_KICK");
                commands.addProperty("ban", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_BAN");
                commands.addProperty("op", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_OP");
                commands.addProperty("deop", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_DEOP");
                config.add("commands", commands);

                Files.createDirectories(CONFIG_PATH.getParent());

                // Usar Gson com formatação bonita (pretty printing)
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(config);

                // Salvar o JSON formatado no arquivo
                Files.writeString(CONFIG_PATH, json);
                System.out.println("[DEBUG] Arquivo de configuração criado com sucesso.");
            }
        } catch (IOException e) {
            System.out.println("[ERRO] Erro ao carregar ou criar o arquivo de configuração:");
            e.printStackTrace();
        }
    }

    // Evento de morte
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            String playerName = player.getName().getString();
            String deathReason = event.getSource().getMsgId(); // Motivo da morte
            String killerName = "N/A";

            if (event.getSource().getEntity() instanceof LivingEntity) {
                killerName = ((LivingEntity) event.getSource().getEntity()).getName().getString();
            }

            String timestamp = DATE_FORMAT.format(new Date());

            // Envia a mensagem para o Discord
            sendDeathToDiscord(playerName, killerName, deathReason, timestamp);
        }
    }

    // Evento de entrada de jogador
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String playerName = player.getName().getString();
            String timestamp = DATE_FORMAT.format(new Date());

            // Envia a mensagem para o Discord
            sendJoinLeaveToDiscord(playerName, "entrou", timestamp);
        }
    }

    // Evento de saída de jogador
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String playerName = player.getName().getString();
            String timestamp = DATE_FORMAT.format(new Date());

            // Envia a mensagem para o Discord
            sendJoinLeaveToDiscord(playerName, "saiu", timestamp);
        }
    }

    // Evento de comando
    @SubscribeEvent
    public static void onCommandExecution(CommandEvent event) {
        System.out.println("[DEBUG] Evento de comando capturado.");

        // Obtém o comando completo (incluindo argumentos)
        String command = event.getParseResults().getReader().getString();

        // Obtém a fonte do comando (quem executou o comando)
        var source = event.getParseResults().getContext().getSource();

        // Verifica se o comando foi executado por um jogador
        String executorName = "Console"; // Assume que foi executado pelo console
        if (source.getEntity() instanceof ServerPlayer player) {
            executorName = player.getDisplayName().getString();
        }

        // Extrai o nome do comando (remove o "/" e os argumentos)
        String commandName = command.split(" ")[0].replace("/", "");
        System.out.println("[DEBUG] Comando detectado: " + commandName);

        // Verifica se o comando está na lista de comandos configurados
        if (config != null && config.has("commands")) {
            JsonObject commands = config.getAsJsonObject("commands");

            if (commands.has(commandName)) {
                String webhookUrl = commands.get(commandName).getAsString();

                // Mensagem a ser enviada para o Discord
                String message = String.format(
                    "**Comando executado:**\n" +
                    "> **Executado por:** %s\n" +
                    "> **Comando:** %s",
                    executorName, command
                );

                // Envia a mensagem para o Discord
                sendToDiscord(webhookUrl, message);
            } else {
                System.out.println("[DEBUG] Comando não configurado: " + commandName);
            }
        } else {
            System.out.println("[ERRO] Arquivo de configuração não carregado ou chave 'commands' não encontrada.");
        }
    }

    // Evento de mensagem no chat
    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String playerName = event.getPlayer().getName().getString();
        String message = event.getMessage().getString(); // Converte Component para String
        String timestamp = DATE_FORMAT.format(new Date());

        // Envia a mensagem do chat para o Discord
        sendChatToDiscord(playerName, message, timestamp);
    }

    // Envia mensagem de morte para o Discord
    private static void sendDeathToDiscord(String playerName, String killerName, String deathReason, String timestamp) {
        if (config != null && config.has("webhooks")) {
            JsonObject webhooks = config.getAsJsonObject("webhooks");

            if (webhooks.has("death")) {
                String webhookUrl = webhooks.get("death").getAsString();

                // Formata a mensagem
                String message = String.format(
                    "**Jogador que morreu:** %s\n" +
                    "**Entidade matou:** %s\n" +
                    "**Motivo da morte:** %s\n" +
                    "**Data e hora da morte:** %s",
                    playerName, killerName, deathReason, timestamp
                );

                // Envia a mensagem para o Discord
                sendToDiscord(webhookUrl, message);
            } else {
                System.out.println("[ERRO] Chave 'death' não encontrada no arquivo de configuração.");
            }
        } else {
            System.out.println("[ERRO] Arquivo de configuração não carregado ou chave 'webhooks' não encontrada.");
        }
    }

    // Envia mensagem de entrada/saída para o Discord
    private static void sendJoinLeaveToDiscord(String playerName, String action, String timestamp) {
        if (config != null && config.has("webhooks")) {
            JsonObject webhooks = config.getAsJsonObject("webhooks");

            if (webhooks.has("join_leave")) {
                String webhookUrl = webhooks.get("join_leave").getAsString();

                // Formata a mensagem
                String message = String.format(
                    "**Jogador %s:** %s\n" +
                    "**Data e hora:** %s",
                    action, playerName, timestamp
                );

                // Envia a mensagem para o Discord
                sendToDiscord(webhookUrl, message);
            } else {
                System.out.println("[ERRO] Chave 'join_leave' não encontrada no arquivo de configuração.");
            }
        } else {
            System.out.println("[ERRO] Arquivo de configuração não carregado ou chave 'webhooks' não encontrada.");
        }
    }

    // Envia mensagem do chat para o Discord
    private static void sendChatToDiscord(String playerName, String message, String timestamp) {
        if (config != null && config.has("webhooks")) {
            JsonObject webhooks = config.getAsJsonObject("webhooks");

            if (webhooks.has("chat")) {
                String webhookUrl = webhooks.get("chat").getAsString();

                // Formata a mensagem
                String formattedMessage = String.format(
                    "**Jogador:** %s\n" +
                    "**Mensagem:** %s\n" +
                    "**Data e hora:** %s",
                    playerName, message, timestamp
                );

                // Envia a mensagem para o Discord
                sendToDiscord(webhookUrl, formattedMessage);
            } else {
                System.out.println("[ERRO] Chave 'chat' não encontrada no arquivo de configuração.");
            }
        } else {
            System.out.println("[ERRO] Arquivo de configuração não carregado ou chave 'webhooks' não encontrada.");
        }
    }

    // Envia mensagem para o Discord via webhook
    private static void sendToDiscord(String webhookUrl, String message) {
        System.out.println("[DEBUG] Tentando enviar mensagem para o Discord: " + webhookUrl);

        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            // Formata a mensagem para o payload JSON
            String jsonPayload = String.format("{\"content\": \"%s\"}", message.replace("\n", "\\n"));
            System.out.println("[DEBUG] Payload JSON: " + jsonPayload);

            // Envia o payload JSON para o Discord
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Verifica o código de resposta HTTP
            int responseCode = connection.getResponseCode();
            System.out.println("[DEBUG] Código de resposta HTTP: " + responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                System.out.println("[ERRO] Erro ao enviar mensagem para o Discord. Código: " + responseCode);
                // Lê a resposta de erro, se houver
                try (var errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        String errorResponse = new String(errorStream.readAllBytes(), "utf-8");
                        System.out.println("[ERRO] Resposta de erro do Discord: " + errorResponse);
                    }
                }
            } else {
                System.out.println("[DEBUG] Mensagem enviada com sucesso para o Discord.");
            }
        } catch (IOException e) {
            System.out.println("[ERRO] Erro ao enviar webhook para o Discord:");
            e.printStackTrace();
        }
    }
}