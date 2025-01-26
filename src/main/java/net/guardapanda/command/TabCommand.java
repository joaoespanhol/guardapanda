
package net.guardapanda.command;


import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks; // Usando ServerLifecycleHooks diretamente
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.chat.Component;

@Mod.EventBusSubscriber
public class TabCommand {
    private static Component header;
    private static Component footer;

    // Contador para controlar o intervalo da atualização
    private static int updateTickCounter = 0;
    private static final int UPDATE_INTERVAL = 50;

    static {
        // Inicialização do cabeçalho e rodapé com valores padrão
        header = Component.literal("\u00a76HSMP\n\u00a7e>> Welcome <<");
        footer = Component.literal("\u00a7bJogadores Onlines: \u00a70\n" +
                                   "\u00a7bVisit Discord: \u00a79https://discord.gg/gYnf4rZUHK");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // Incrementa o contador a cada tick
        if (event.phase == TickEvent.Phase.END) {
            updateTickCounter++;

            // Atualiza a TabList a cada "UPDATE_INTERVAL" ticks
            if (updateTickCounter >= UPDATE_INTERVAL) {
                updateTabListComponents();
                updateTabListForAllPlayers();
                updateTickCounter = 0; // Reinicia o contador
            }
        }
    }

    private static void updateTabListComponents() {
        // Cabeçalho da TabList
        header = Component.literal("\u00a76HSMP\n\u00a7e>> Bem-vindo <<");

        // Rodapé da TabList
        int onlinePlayers = getOnlinePlayerCount();  // Obtém a contagem de jogadores online
        int staffCount = getStaffCount();  // Obtém a contagem de jogadores staff

        footer = Component.literal("\u00a7bJogador Online: \u00a7a" + onlinePlayers + "\n" + 
                                   "\u00a7bDiscord Link: \u00a79https://discord.gg/gYnf4rZUHK");
    }

    private static void updateTabListForAllPlayers() {
        // Envia o cabeçalho e rodapé atualizados para todos os jogadores online
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                player.connection.send(new ClientboundTabListPacket(header, footer));
            }
        }
    }

    private static int getOnlinePlayerCount() {
        // Obtém o número de jogadores online usando a instância do servidor
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().size();
        }
        return 0; // Retorna 0 se o servidor ainda não estiver disponível
    }

    private static int getStaffCount() {
        // Obtém o número de jogadores com permissão de staff
        int staffCount = 0;
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                if (player.hasPermissions(4)) { // Assumindo que 4 é o nível de permissão de staff
                    staffCount++;
                }
            }
        }
        return staffCount;
    }
}