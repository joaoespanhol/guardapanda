
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStartedEvent; // Novo evento adicionado
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;

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
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WebhookCommand {

    private static final Path CONFIG_PATH = Paths.get("config", "guardapanda_webhooks.json");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private static JsonObject config;
    private static final Map<BlockPos, SignData> signContents = new HashMap<>(); // Armazena o conteúdo das placas e o nome do jogador

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        loadConfig();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        String timestamp = DATE_FORMAT.format(new Date());
        sendServerStatusToDiscord("ligado", timestamp);
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
                webhooks.addProperty("sign", "https://discord.com/api/webhooks/SEU_WEBHOOK_PLACAS");
                webhooks.addProperty("server_status", "https://discord.com/api/webhooks/SEU_WEBHOOK_STATUS_SERVIDOR"); // Novo webhook adicionado
                config.add("webhooks", webhooks);

                JsonObject commands = new JsonObject();
                commands.addProperty("kick", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_KICK");
                commands.addProperty("ban", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_BAN");
                commands.addProperty("op", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_OP");
                commands.addProperty("deop", "https://discord.com/api/webhooks/SEU_WEBHOOK_COMANDOS_DEOP");
                config.add("commands", commands);

                Files.createDirectories(CONFIG_PATH.getParent());
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(CONFIG_PATH, gson.toJson(config));
            }
        } catch (IOException e) {
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
                String message = String.format("**Comando executado:**\n> **Executado por:** %s\n> **Comando:** %s", executorName, command);
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

    @SubscribeEvent
    public static void onSignPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().getBlock() instanceof SignBlock) {
            if (event.getEntity() instanceof ServerPlayer player) {
                BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
                if (blockEntity instanceof SignBlockEntity signBlockEntity) {
                    // Armazena a posição da placa e o nome do jogador para verificação posterior
                    signContents.put(event.getPos(), new SignData(player.getName().getString(), ""));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Verifica o conteúdo das placas periodicamente
            for (Map.Entry<BlockPos, SignData> entry : signContents.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockEntity blockEntity = event.getServer().overworld().getBlockEntity(pos);
                if (blockEntity instanceof SignBlockEntity signBlockEntity) {
                    String[] signText = new String[4];
                    for (int i = 0; i < 4; i++) {
                        signText[i] = signBlockEntity.getFrontText().getMessage(i, false).getString();
                    }

                    String currentContent = String.join("\n", signText);
                    String previousContent = entry.getValue().getContent();

                    // Se o conteúdo da placa mudou, envia para o Discord
                    if (!currentContent.equals(previousContent)) {
                        signContents.put(pos, new SignData(entry.getValue().getPlayerName(), currentContent)); // Atualiza o conteúdo armazenado
                        if (!currentContent.trim().isEmpty()) { // Evita enviar placas vazias
                            String timestamp = DATE_FORMAT.format(new Date());
                            sendSignToDiscord(entry.getValue().getPlayerName(), pos.getX(), pos.getY(), pos.getZ(), currentContent, timestamp);
                        }
                    }
                }
            }
        }
    }

    private static void sendDeathToDiscord(String playerName, String killerName, String deathReason, String timestamp) {
        sendToWebhook("death", String.format("**Jogador que morreu:** %s\n**Entidade matou:** %s\n**Motivo da morte:** %s\n**Data e hora:** %s", playerName, killerName, deathReason, timestamp));
    }

    private static void sendJoinLeaveToDiscord(String playerName, String action, String timestamp) {
        sendToWebhook("join_leave", String.format("**Jogador %s:** %s\n**Data e hora:** %s", action, playerName, timestamp));
    }

    private static void sendChatToDiscord(String playerName, String message, String timestamp) {
        sendToWebhook("chat", String.format("**Jogador:** %s\n**Mensagem:** %s\n**Data e hora:** %s", playerName, message, timestamp));
    }

    private static void sendSignToDiscord(String playerName, int x, int y, int z, String signContent, String timestamp) {
        sendToWebhook("sign", String.format("**Jogador:** %s\n**Localização:** %d, %d, %d\n**Conteúdo da placa:**\n%s\n**Data e hora:** %s", playerName, x, y, z, signContent, timestamp));
    }

    private static void sendServerStatusToDiscord(String status, String timestamp) {
        sendToWebhook("server_status", String.format("**Servidor %s:**\n**Data e hora:** %s", status, timestamp));
    }

    private static void sendToWebhook(String key, String message) {
        if (config != null && config.has("webhooks")) {
            JsonObject webhooks = config.getAsJsonObject("webhooks");

            if (webhooks.has(key)) {
                sendToDiscord(webhooks.get(key).getAsString(), message);
            }
        }
    }

    private static void sendToDiscord(String webhookUrl, String message) {
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            JsonObject json = new JsonObject();
            json.addProperty("content", message);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.toString().getBytes("utf-8"));
            }

            connection.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Classe auxiliar para armazenar o nome do jogador e o conteúdo da placa
    private static class SignData {
        private final String playerName;
        private final String content;

        public SignData(String playerName, String content) {
            this.playerName = playerName;
            this.content = content;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getContent() {
            return content;
        }
    }
}