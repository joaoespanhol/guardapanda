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
import net.minecraftforge.event.ServerChatEvent;

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

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        loadConfig();
    }

    private static void loadConfig() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                config = new Gson().fromJson(new FileReader(CONFIG_PATH.toFile()), JsonObject.class);
            } else {
                config = new JsonObject();

                JsonObject webhooks = new JsonObject();
                webhooks.addProperty("death", "https://discord.com/api/webhooks/SEU_WEBHOOK_MORTES");
                webhooks.addProperty("join_leave", "https://discord.com/api/webhooks/SEU_WEBHOOK_ENTRADA_SAIDA");
                webhooks.addProperty("bans_kicks", "https://discord.com/api/webhooks/SEU_WEBHOOK_BANIMENTOS_KICKS");
                webhooks.addProperty("chat", "https://discord.com/api/webhooks/SEU_WEBHOOK_CHAT");
                config.add("webhooks", webhooks);

                JsonObject commands = new JsonObject();
                commands.addProperty("kick", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_KICK");
                commands.addProperty("ban", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_BAN");
                commands.addProperty("op", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_OP");
                commands.addProperty("deop", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_DEOP");
                config.add("commands", commands);

                Files.createDirectories(CONFIG_PATH.getParent());

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String json = gson.toJson(config);

                Files.writeString(CONFIG_PATH, json);
            }
        } catch (IOException e) {
            System.out.println("[ERRO] Erro ao carregar ou criar o arquivo de configuração:");
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            String playerName = player.getName().getString();
            String deathReason = event.getSource().getMsgId();
            String killerName = "N/A";

            if (event.getSource().getEntity() instanceof LivingEntity) {
                killerName = ((LivingEntity) event.getSource().getEntity()).getName().getString();
            }

            String timestamp = DATE_FORMAT.format(new Date());

            sendDeathToDiscord(playerName, killerName, deathReason, timestamp);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String playerName = player.getName().getString();
            String timestamp = DATE_FORMAT.format(new Date());

            sendJoinLeaveToDiscord(playerName, "entrou", timestamp);
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            String playerName = player.getName().getString();
            String timestamp = DATE_FORMAT.format(new Date());

            sendJoinLeaveToDiscord(playerName, "saiu", timestamp);
        }
    }

    @SubscribeEvent
    public static void onCommandExecution(CommandEvent event) {
        String command = event.getParseResults().getReader().getString();
        var source = event.getParseResults().getContext().getSource();

        String executorName = "Console";
        if (source.getEntity() instanceof ServerPlayer player) {
            executorName = player.getDisplayName().getString();
        }

        String commandName = command.split(" ")[0].replace("/", "");

        if (config != null && config.has("commands")) {
            JsonObject commands = config.getAsJsonObject("commands");

            if (commands.has(commandName)) {
                String webhookUrl = commands.get(commandName).getAsString();

                String message = String.format(
                    "**Comando executado:**\n" +
                    "> **Executado por:** %s\n" +
                    "> **Comando:** %s",
                    executorName, command
                );

                sendToDiscord(webhookUrl, message);
            }
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        String playerName = event.getPlayer().getName().getString();
        String message = event.getMessage().getString();
        String timestamp = DATE_FORMAT.format(new Date());

        sendChatToDiscord(playerName, message, timestamp);
    }

    private static void sendDeathToDiscord(String playerName, String killerName, String deathReason, String timestamp) {
        if (config != null && config.has("webhooks")) {
            JsonObject webhooks = config.getAsJsonObject("webhooks");

            if (webhooks.has("death")) {
                String webhookUrl = webhooks.get("death").getAsString();

                String message = String.format(
                    "**Jogador que morreu:** %s\n" +
                    "**Entidade matou:** %s\n" +
                    "**Motivo da morte:** %s\n" +
                    "**Data e hora da morte:** %s",
                    playerName, killerName, deathReason, timestamp
                );

                sendToDiscord(webhookUrl, message);
            }
        }
    }

    private static void sendJoinLeaveToDiscord(String playerName, String action, String timestamp) {
        if (config != null && config.has("webhooks")) {
            JsonObject webhooks = config.getAsJsonObject("webhooks");

            if (webhooks.has("join_leave")) {
                String webhookUrl = webhooks.get("join_leave").getAsString();

                String message = String.format(
                    "**Jogador %s:** %s\n" +
                    "**Data e hora:** %s",
                    action, playerName, timestamp
                );

                sendToDiscord(webhookUrl, message);
            }
        }
    }

    private static void sendChatToDiscord(String playerName, String message, String timestamp) {
        if (config != null && config.has("webhooks")) {
            JsonObject webhooks = config.getAsJsonObject("webhooks");

            if (webhooks.has("chat")) {
                String webhookUrl = webhooks.get("chat").getAsString();

                String formattedMessage = String.format(
                    "**Jogador:** %s\n" +
                    "**Mensagem:** %s\n" +
                    "**Data e hora:** %s",
                    playerName, message, timestamp
                );

                sendToDiscord(webhookUrl, formattedMessage);
            }
        }
    }

    private static void sendToDiscord(String webhookUrl, String message) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            String jsonPayload = String.format("{\"content\": \"%s\"}", message.replace("\n", "\\n"));

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                try (var errorStream = connection.getErrorStream()) {
                    if (errorStream != null) {
                        String errorResponse = new String(errorStream.readAllBytes(), "utf-8");
                        System.out.println("[ERRO] Resposta de erro do Discord: " + errorResponse);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[ERRO] Erro ao enviar webhook para o Discord:");
            e.printStackTrace();
        }
    }
}