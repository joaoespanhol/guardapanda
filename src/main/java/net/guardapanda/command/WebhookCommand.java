/*package net.guardapanda.command;

import net.minecraft.network.chat.Component;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WebhookCommand {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/1330270131145932821/A5t6rsXCAQhEqzCl-LrQx468Zax5ffzyORLNTaKd8u5Z-9j6rbHgA-1DwqTISgSWxHUR";

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ItemStack heldItem = event.getItemStack();

            // Verifica se o item é um livro escrito
            if (heldItem.getItem() instanceof WrittenBookItem) {
                System.out.println("Livro escrito detectado. Tentando extrair conteúdo...");

                // Obtém o conteúdo do livro
                String bookText = getBookText(heldItem);

                if (bookText != null && !bookText.isEmpty()) {
                    System.out.println("Conteúdo do livro: " + bookText);

                    // Envia o conteúdo para o Discord
                    sendBookToDiscord(player.getName().getString(), bookText);

                    // Confirma no chat do jogador
                    player.sendSystemMessage(Component.literal("O conteúdo do livro foi enviado para o Discord!"));
                } else {
                    System.out.println("O livro não contém texto.");
                }
            }
        }
    }

    private static String getBookText(ItemStack book) {
        // Extrai as páginas do livro do NBT
        if (book.hasTag() && book.getTag() != null) {
            ListTag pages = book.getTag().getList("pages", 8); // 8 = NBT String
            if (pages != null && !pages.isEmpty()) {
                StringBuilder bookContent = new StringBuilder();
                for (int i = 0; i < pages.size(); i++) {
                    bookContent.append(pages.getString(i)).append("\n");
                }
                return bookContent.toString();
            }
        }
        return null;
    }

    private static void sendBookToDiscord(String playerName, String bookText) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(WEBHOOK_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");

            String jsonPayload = String.format("{\"content\": \"Jogador %s escreveu um livro:\n%s\"}", playerName, bookText);
            System.out.println("Payload JSON gerado: " + jsonPayload);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Código de resposta do Discord: " + responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                System.out.println("Erro ao enviar mensagem para o Discord. Código: " + responseCode);
            } else {
                System.out.println("Mensagem enviada com sucesso para o Discord!");
            }
        } catch (IOException e) {
            System.out.println("Erro ao enviar webhook para o Discord:");
            e.printStackTrace();
        }
    }
}
*/