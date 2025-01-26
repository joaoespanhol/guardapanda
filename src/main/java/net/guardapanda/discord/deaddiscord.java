package net.guardapanda.discord;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class deaddiscord {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1330270131145932821/A5t6rsXCAQhEqzCl-LrQx468Zax5ffzyORLNTaKd8u5Z-9j6rbHgA-1DwqTISgSWxHUR";

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            System.out.println("Evento de morte disparado para: " + player.getName().getString());

            String playerName = player.getName().getString();
            String deathCause = event.getSource().getMsgId(); // Obtem a causa da morte
            String killerName = null;

            if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                killerName = killer.getName().getString(); // Nome do jogador que matou
            } else if (event.getSource().getEntity() != null) {
                killerName = event.getSource().getEntity().getDisplayName().getString(); // Nome da entidade que matou
            }

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            String formattedDate = now.format(formatter);

            StringBuilder deathMessage = new StringBuilder();
            deathMessage.append("**Jogador que morreu:** ").append(playerName).append("\n");
            if (killerName != null) {
                deathMessage.append("**Jogador que matou:** ").append(killerName).append("\n");
            }
            deathMessage.append("**Entidade matou:** ").append(deathCause).append("\n");
            deathMessage.append("**Motivo da morte:** ").append(deathCause).append("\n");
            deathMessage.append("**Data e hora da morte:** ").append(formattedDate);

            sendDeathWebhook(deathMessage.toString());
        }
    }

    private static void sendDeathWebhook(String messageContent) {
        try {
            System.out.println("Tentando enviar a mensagem para o Discord...");

            HttpURLConnection connection = (HttpURLConnection) new URL(WEBHOOK_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            String jsonPayload = String.format("{\"content\": \"%s\"}", messageContent);
            System.out.println("Payload JSON: " + jsonPayload);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Código de resposta HTTP: " + responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                System.out.println("Erro ao enviar mensagem para o Discord. Código de resposta: " + responseCode);
            } else {
                System.out.println("Mensagem enviada com sucesso para o Discord!");
            }
        } catch (IOException e) {
            System.out.println("Erro ao enviar o webhook para o Discord");
            e.printStackTrace();
        }
    }
}

