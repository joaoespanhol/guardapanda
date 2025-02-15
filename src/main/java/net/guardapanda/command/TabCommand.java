package net.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.world.level.GameType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Mod.EventBusSubscriber
public class TabCommand {
    private static Component header;
    private static Component footer;

    // Contador para controlar o intervalo da atualização
    private static int updateTickCounter = 0;
    private static final int UPDATE_INTERVAL = 50;

    // Contador personalizado para atualizações rápidas
    private static int customUpdateDelay = 0;
    private static final int CUSTOM_UPDATE_INTERVAL = 10; // 10 ticks = 0.5 segundos

    // Caminho do arquivo de configuração
    private static final String CONFIG_FILE_PATH = "config/tablist_config.txt";

    static {
        // Inicializa o arquivo de configuração com valores padrão, se necessário
        createDefaultConfigFileIfNotExists();
        // Carrega as frases do arquivo de configuração
        loadTabListComponents();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // Incrementa o contador a cada tick
        if (event.phase == TickEvent.Phase.END) {
            updateTickCounter++;
            customUpdateDelay++;

            // Atualiza a TabList a cada "UPDATE_INTERVAL" ticks
            if (updateTickCounter >= UPDATE_INTERVAL) {
                loadTabListComponents(); // Recarrega as frases do arquivo de configuração
                updateTabListForAllPlayers();
                hideVanishedAndSpectatorPlayers();
                updateTickCounter = 0; // Reinicia o contador
            }

            // Atualiza a visibilidade dos jogadores a cada "CUSTOM_UPDATE_INTERVAL" ticks
            if (customUpdateDelay >= CUSTOM_UPDATE_INTERVAL) {
                hideVanishedAndSpectatorPlayers();
                customUpdateDelay = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("vanish")
                .then(Commands.argument("state", BoolArgumentType.bool())
                    .executes(context -> {
                        boolean state = BoolArgumentType.getBool(context, "state");
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        setVanish(player, state);
                        return 1;
                    })
                )
        );
    }

    @SubscribeEvent
    public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            // Força a atualização da visibilidade do jogador imediatamente
            updatePlayerVisibility(player);
            hideVanishedAndSpectatorPlayers();
        }
    }

    private static void createDefaultConfigFileIfNotExists() {
        File configFile = new File(CONFIG_FILE_PATH);
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs(); // Cria o diretório "config" se não existir
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write("# Configuração da TabList\n");
                    writer.write("# Edite as frases abaixo para personalizar o cabeçalho e rodapé da TabList.\n");
                    writer.write("# Use \\n para quebras de linha.\n");
                    writer.write("header=§6HSMP\\n§e>> Bem-vindo <<\n");
                    writer.write("footer=§bJogadores Online: §a%online_players%\\n§bStaff Online: §c%staff_count%\\n§bDiscord Link: §9https://discord.gg/gYnf4rZUHK\n");
                }
                System.out.println("[INFO] Arquivo de configuração criado: " + CONFIG_FILE_PATH);
            } catch (IOException e) {
                System.err.println("[ERRO] Falha ao criar o arquivo de configuração: " + CONFIG_FILE_PATH);
                e.printStackTrace();
            }
        }
    }

    private static void loadTabListComponents() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(CONFIG_FILE_PATH));
            String headerText = "";
            String footerText = "";

            for (String line : lines) {
                if (line.startsWith("header=")) {
                    headerText = line.substring(7).replace("\\n", "\n");
                } else if (line.startsWith("footer=")) {
                    footerText = line.substring(7).replace("\\n", "\n");
                }
            }

            // Substitui placeholders dinâmicos
            int onlinePlayers = getOnlinePlayerCount();
            int staffCount = getStaffCount();
            footerText = footerText.replace("%online_players%", String.valueOf(onlinePlayers))
                                   .replace("%staff_count%", String.valueOf(staffCount));

            // Converte para Component
            header = Component.literal(headerText);
            footer = Component.literal(footerText);

            System.out.println("[INFO] Frases da TabList carregadas com sucesso.");
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao ler o arquivo de configuração: " + CONFIG_FILE_PATH);
            e.printStackTrace();
        }
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
        // Obtém o número de jogadores online, excluindo jogadores em vanish ou spectator
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            return (int) ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().stream()
                    .filter(player -> !isVanished(player) && !player.isSpectator())
                    .count();
        }
        return 0; // Retorna 0 se o servidor ainda não estiver disponível
    }

    private static int getStaffCount() {
        // Obtém o número de jogadores com permissão de staff, excluindo jogadores em vanish ou spectator
        int staffCount = 0;
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                if (player.hasPermissions(4) && !isVanished(player) && !player.isSpectator()) {
                    staffCount++;
                }
            }
        }
        return staffCount;
    }

    private static boolean isVanished(ServerPlayer player) {
        // Verifica se o jogador está em vanish
        return player.getPersistentData().getBoolean("vanished");
    }

    private static void hideVanishedAndSpectatorPlayers() {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                if (isVanished(player) || player.isSpectator()) {
                    // Remove o jogador da TabList e do mundo
                    hidePlayerFromTabList(player);
                    hidePlayerInWorld(player);
                } else {
                    // Atualiza a visibilidade do jogador na TabList e no mundo se não estiver em Vanish ou Spectator
                    updatePlayerVisibility(player);
                }
            }
        }
    }

    private static void hidePlayerFromTabList(ServerPlayer player) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayer otherPlayer : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                if (!otherPlayer.getUUID().equals(player.getUUID())) {
                    otherPlayer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
                }
            }
        }
    }

    private static void hidePlayerInWorld(ServerPlayer player) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayer otherPlayer : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                if (!otherPlayer.getUUID().equals(player.getUUID())) {
                    // Remove o jogador da lista de entidades visíveis para outros jogadores
                    otherPlayer.connection.send(new ClientboundRemoveEntitiesPacket(player.getId()));
                }
            }
        }
    }

    private static void updatePlayerVisibility(ServerPlayer player) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayer otherPlayer : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                if (!otherPlayer.getUUID().equals(player.getUUID())) {
                    // Atualiza a visibilidade do jogador para outros jogadores
                    otherPlayer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
                    otherPlayer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
                    otherPlayer.connection.send(new ClientboundAddPlayerPacket(player));
                }
            }
        }
    }

    private static void setVanish(ServerPlayer player, boolean state) {
        // Define o estado de vanish no jogador
        player.getPersistentData().putBoolean("vanished", state);

        // Atualiza a visibilidade do jogador na TabList e no mundo
        if (state) {
            // Oculta o jogador da TabList e do mundo
            hidePlayerFromTabList(player);
            hidePlayerInWorld(player);
            player.setGameMode(GameType.SPECTATOR); // Muda para modo Espectador
            player.sendSystemMessage(Component.literal("§aVocê está agora invisível."));
        } else {
            // Mostra o jogador na TabList e no mundo
            updatePlayerVisibility(player);
            player.setGameMode(GameType.SURVIVAL); // Volta ao modo Survival
            player.sendSystemMessage(Component.literal("§aVocê está agora visível."));
        }
    }
}