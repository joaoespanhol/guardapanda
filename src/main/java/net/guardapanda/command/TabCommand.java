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
    private static final int UPDATE_INTERVAL = 25;

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

            // Atualiza a TabList a cada "UPDATE_INTERVAL" ticks
            if (updateTickCounter >= UPDATE_INTERVAL) {
                loadTabListComponents(); // Recarrega as frases do arquivo de configuração
                updateTabListForAllPlayers();
                hideVanishedAndSpectatorPlayers();
                updateTickCounter = 0; // Reinicia o contador
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

            // Verifica se o jogador está saindo do modo Spectator
            if (event.getCurrentGameMode() == GameType.SPECTATOR) {
                // Se o jogador estava no modo Spectator e está mudando para outro modo, atualiza a visibilidade
                updatePlayerVisibility(player);
            }

            // Verifica se o jogador está entrando no modo Spectator
            if (event.getNewGameMode() == GameType.SPECTATOR) {
                // Se o jogador está entrando no modo Spectator, oculta-o da TabList
                hidePlayerFromTabList(player);
            }
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
                    // Remove o jogador da TabList dos outros jogadores
                    hidePlayerFromTabList(player);
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

    private static void updatePlayerVisibility(ServerPlayer player) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayer otherPlayer : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                if (!otherPlayer.getUUID().equals(player.getUUID())) {
                    // Remove o jogador da TabList dos outros jogadores
                    otherPlayer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
                    // Adiciona o jogador de volta à TabList dos outros jogadores
                    otherPlayer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
                }
            }
        }
    }

    private static void setVanish(ServerPlayer player, boolean state) {
        // Define o estado de vanish no jogador
        player.getPersistentData().putBoolean("vanished", state);

        // Atualiza a visibilidade do jogador na TabList
        if (state) {
            // Oculta o jogador da TabList
            hidePlayerFromTabList(player);
            player.sendSystemMessage(Component.literal("§aVocê está agora invisível."));
        } else {
            // Mostra o jogador na TabList
            updatePlayerVisibility(player);
            player.sendSystemMessage(Component.literal("§aVocê está agora visível."));
        }
    }
}