package net.guardapanda.command;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@Mod.EventBusSubscriber
public class SystemCommand {

    private static final HashMap<UUID, PlayerLogin> playerLoginMap = new HashMap<>();
    private static final File CONFIG_DIR = new File(FMLPaths.CONFIGDIR.get().toFile(), "guardapanda");
    private static final File REGISTERED_PLAYERS = new File(CONFIG_DIR, "registered-players.json");
    private static final File SYSTEM_LOGIN_CONFIG = new File(CONFIG_DIR, "systemLogin.json");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static JsonArray jsonArray = new JsonArray();

    // Configurações padrão
    private static String loginMessage = "§aBem-vindo ao servidor! Faça o login usando /login <senha>.";
    private static String registerMessage = "§aUse /register <senha> para se registrar.";
    private static String changePasswordMessage = "§aSenha alterada com sucesso!";
    private static String changePasswordStaffMessage = "§aSenha do jogador alterada com sucesso!";
    private static String unregisterMessage = "§aRegistro do jogador removido com sucesso!";
    private static String forceLoginMessage = "§aLogin forçado realizado com sucesso!";
    private static String lastLoginMessage = "§aÚltimo login de %s: %s";
    private static String recentLoginsMessage = "§aÚltimos logins:\n%s";
    private static String unregisterKickMessage = "§cVocê foi desregistrado. Por favor, registre-se novamente.";

    // Novas configurações
    private static boolean enableJoinMessage = true; // Ativar/desativar mensagem de entrada
    private static boolean enableLeaveMessage = true; // Ativar/desativar mensagem de saída
    private static String joinMessage = "§aBem-vindo ao servidor, %s!"; // Mensagem de entrada
    private static String leaveMessage = "§cO jogador %s saiu do servidor."; // Mensagem de saída
    private static String reloadMessage = "§aConfigurações recarregadas com sucesso!"; // Mensagem de recarregamento

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        // Comando /register <senha>
        event.getDispatcher().register(Commands.literal("register")
                .then(Commands.argument("password", MessageArgument.message())
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String password = MessageArgument.getMessage(ctx, "password").getString();
                    String username = player.getGameProfile().getName();
                    String ip = player.getIpAddress();

                    PlayerLogin playerLogin = getPlayerLogin(player);

                    if (isPlayerRegistered(username)) {
                        ctx.getSource().sendFailure(Component.literal("§cVocê já está registrado! Use /login para entrar."));
                        return 1;
                    }

                    String uuid = player.getUUID().toString();
                    savePlayer(uuid, username, password, ip);
                    playerLogin.setLoggedIn(true);
                    player.setInvulnerable(false);
                    resetPlayerAttributes(player); // Restaura os atributos do jogador
                    ctx.getSource().sendSuccess(() -> Component.literal("§aRegistro concluído com sucesso!"), false);
                    return 1;
                })));

        // Comando /login <senha>
        event.getDispatcher().register(Commands.literal("login")
                .then(Commands.argument("password", MessageArgument.message())
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String password = MessageArgument.getMessage(ctx, "password").getString();
                    String username = player.getGameProfile().getName();

                    PlayerLogin playerLogin = getPlayerLogin(player);

                    if (!isPlayerRegistered(username)) {
                        ctx.getSource().sendFailure(Component.literal("§cVocê não está registrado! Use /register para se registrar."));
                    } else if (isCorrectPassword(username, password)) {
                        playerLogin.setLoggedIn(true);
                        ctx.getSource().sendSuccess(() -> Component.literal("§aLogin realizado com sucesso!"), false);
                        player.setInvulnerable(false);
                        resetPlayerAttributes(player); // Restaura os atributos do jogador
                    } else {
                        ctx.getSource().sendFailure(Component.literal("§cSenha incorreta!"));
                    }
                    return 1;
                })));

        // Comando /changepassword <nova senha>
        event.getDispatcher().register(Commands.literal("changepassword")
                .then(Commands.argument("newPassword", MessageArgument.message())
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String newPassword = MessageArgument.getMessage(ctx, "newPassword").getString();
                    String username = player.getGameProfile().getName();

                    if (!isPlayerRegistered(username)) {
                        ctx.getSource().sendFailure(Component.literal("§cVocê não está registrado!"));
                        return 1;
                    }

                    changePassword(username, newPassword);
                    ctx.getSource().sendSuccess(() -> Component.literal(changePasswordMessage), false);
                    return 1;
                })));

        // Comando /changepasswordStaff <nome do jogador> <nova senha>
        event.getDispatcher().register(Commands.literal("changepasswordStaff")
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP
                .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("newPassword", MessageArgument.message())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    String newPassword = MessageArgument.getMessage(ctx, "newPassword").getString();
                    String username = target.getGameProfile().getName();

                    if (!isPlayerRegistered(username)) {
                        ctx.getSource().sendFailure(Component.literal("§cJogador não está registrado!"));
                        return 1;
                    }

                    changePassword(username, newPassword);
                    ctx.getSource().sendSuccess(() -> Component.literal(changePasswordStaffMessage), false);
                    return 1;
                }))));

        // Comando /unregister <nome do jogador>
        event.getDispatcher().register(Commands.literal("unregister")
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP
                .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    String username = target.getGameProfile().getName();

                    if (!isPlayerRegistered(username)) {
                        ctx.getSource().sendFailure(Component.literal("§cJogador não está registrado!"));
                        return 1;
                    }

                    unregisterPlayer(username); // Remove o registro do jogador
                    target.connection.disconnect(Component.literal(unregisterKickMessage)); // Expulsa o jogador
                    ctx.getSource().sendSuccess(() -> Component.literal(unregisterMessage), false);
                    return 1;
                })));

        // Comando /forcelogin <nick>
        event.getDispatcher().register(Commands.literal("forcelogin")
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP
                .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    PlayerLogin playerLogin = getPlayerLogin(target);
                    playerLogin.setLoggedIn(true);
                    target.setInvulnerable(false);
                    resetPlayerAttributes(target); // Restaura os atributos do jogador
                    ctx.getSource().sendSuccess(() -> Component.literal(forceLoginMessage), false);
                    return 1;
                })));

        // Comando /lastlogin <nick>
        event.getDispatcher().register(Commands.literal("lastlogin")
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP
                .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    PlayerLogin playerLogin = getPlayerLogin(target);
                    String lastLoginDate = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss").format(new Date(playerLogin.getLastLoginTime()));
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(lastLoginMessage, target.getGameProfile().getName(), lastLoginDate)), false);
                    return 1;
                })));

        // Comando /system recent
        event.getDispatcher().register(Commands.literal("system")
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP
                .then(Commands.literal("recent")
                .executes(ctx -> {
                    StringBuilder recentLogins = new StringBuilder();
                    for (Map.Entry<UUID, PlayerLogin> entry : playerLoginMap.entrySet()) {
                        ServerPlayer player = entry.getValue().getPlayer();
                        String lastLoginDate = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm:ss").format(new Date(entry.getValue().getLastLoginTime()));
                        recentLogins.append(player.getGameProfile().getName()).append(": ").append(lastLoginDate).append("\n");
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(recentLoginsMessage, recentLogins.toString())), false);
                    return 1;
                })));

        // Comando /system reload
        event.getDispatcher().register(Commands.literal("system")
                .requires(source -> source.hasPermission(2)) // Requer permissão de OP
                .then(Commands.literal("reload")
                .executes(ctx -> {
                    loadConfig(); // Recarrega as configurações
                    ctx.getSource().sendSuccess(() -> Component.literal(reloadMessage), false);
                    return 1;
                })));
    }

    private static PlayerLogin getPlayerLogin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (playerLoginMap.containsKey(uuid)) {
            return playerLoginMap.get(uuid);
        }
        PlayerLogin newPlayer = new PlayerLogin(player);
        playerLoginMap.put(uuid, newPlayer);
        return newPlayer;
    }

    private static boolean isPlayerRegistered(String username) {
        return findPlayerObject(username) != null;
    }

    private static boolean isCorrectPassword(String username, String password) {
        JsonObject playerObject = findPlayerObject(username);
        return playerObject != null && playerObject.get("password").getAsString().equals(encryptPassword(password));
    }

    private static JsonObject findPlayerObject(String username) {
        if (jsonArray.size() == 0) {
            return null;
        }
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject playerObject = jsonArray.get(i).getAsJsonObject();
            if (playerObject.get("name").getAsString().equals(username)) {
                return playerObject;
            }
        }
        return null;
    }

    private static void savePlayer(String uuid, String username, String password, String ip) {
        // Cria a pasta config/guardapanda se não existir
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("uuid", uuid);
        jsonObject.addProperty("name", username);
        jsonObject.addProperty("password", encryptPassword(password)); // Criptografa a senha
        jsonObject.addProperty("ip", ip); // Armazena o IP do jogador
        jsonArray.add(jsonObject);

        // Salva as alterações no arquivo
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(REGISTERED_PLAYERS, StandardCharsets.UTF_8))) {
            bufferedWriter.write(gson.toJson(jsonArray));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void changePassword(String username, String newPassword) {
        JsonObject playerObject = findPlayerObject(username);
        if (playerObject != null) {
            playerObject.addProperty("password", encryptPassword(newPassword)); // Criptografa a nova senha
            saveRegisteredPlayers();
        }
    }

    private static void unregisterPlayer(String username) {
        JsonObject playerObject = findPlayerObject(username);
        if (playerObject != null) {
            jsonArray.remove(playerObject);
            saveRegisteredPlayers();
        }
    }

    private static void saveRegisteredPlayers() {
        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(REGISTERED_PLAYERS, StandardCharsets.UTF_8))) {
            bufferedWriter.write(gson.toJson(jsonArray));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void readRegisteredPlayers() {
        if (!REGISTERED_PLAYERS.exists()) {
            return;
        }
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(REGISTERED_PLAYERS, StandardCharsets.UTF_8))) {
            jsonArray = gson.fromJson(bufferedReader, JsonArray.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig() {
        if (!SYSTEM_LOGIN_CONFIG.exists()) {
            // Cria o arquivo de configuração com valores padrão
            JsonObject config = new JsonObject();
            config.addProperty("loginMessage", "§aBem-vindo ao servidor! Faça o login usando /login <senha>.");
            config.addProperty("registerMessage", "§aUse /register <senha> para se registrar.");
            config.addProperty("changePasswordMessage", "§aSenha alterada com sucesso!");
            config.addProperty("changePasswordStaffMessage", "§aSenha do jogador alterada com sucesso!");
            config.addProperty("unregisterMessage", "§aRegistro do jogador removido com sucesso!");
            config.addProperty("forceLoginMessage", "§aLogin forçado realizado com sucesso!");
            config.addProperty("lastLoginMessage", "§aÚltimo login de %s: %s");
            config.addProperty("recentLoginsMessage", "§aÚltimos logins:\n%s");
            config.addProperty("unregisterKickMessage", "§cVocê foi desregistrado. Por favor, registre-se novamente.");

            // Novas configurações
            config.addProperty("enableJoinMessage", true);
            config.addProperty("enableLeaveMessage", true);
            config.addProperty("joinMessage", "§aBem-vindo ao servidor, %s!");
            config.addProperty("leaveMessage", "§cO jogador %s saiu do servidor.");
            config.addProperty("reloadMessage", "§aConfigurações recarregadas com sucesso!");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(SYSTEM_LOGIN_CONFIG, StandardCharsets.UTF_8))) {
                writer.write(gson.toJson(config));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Carrega as configurações do arquivo
            try (BufferedReader reader = new BufferedReader(new FileReader(SYSTEM_LOGIN_CONFIG, StandardCharsets.UTF_8))) {
                JsonObject config = gson.fromJson(reader, JsonObject.class);
                loginMessage = config.get("loginMessage").getAsString();
                registerMessage = config.get("registerMessage").getAsString();
                changePasswordMessage = config.get("changePasswordMessage").getAsString();
                changePasswordStaffMessage = config.get("changePasswordStaffMessage").getAsString();
                unregisterMessage = config.get("unregisterMessage").getAsString();
                forceLoginMessage = config.get("forceLoginMessage").getAsString();
                lastLoginMessage = config.get("lastLoginMessage").getAsString();
                recentLoginsMessage = config.get("recentLoginsMessage").getAsString();
                unregisterKickMessage = config.get("unregisterKickMessage").getAsString();

                // Novas configurações
                enableJoinMessage = config.get("enableJoinMessage").getAsBoolean();
                enableLeaveMessage = config.get("enableLeaveMessage").getAsBoolean();
                joinMessage = config.get("joinMessage").getAsString();
                leaveMessage = config.get("leaveMessage").getAsString();
                reloadMessage = config.get("reloadMessage").getAsString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {
        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            readRegisteredPlayers();
            loadConfig(); // Carrega as configurações ao iniciar o servidor
        }
    }

    public static class PlayerLogin {
        private final ServerPlayer player;
        private boolean loggedIn;
        private long lastLoginTime; // Tempo do último login
        private Vec3 initialPosition; // Armazena a posição inicial do jogador

        public PlayerLogin(ServerPlayer player) {
            this.player = player;
            this.player.setInvulnerable(true); // Define o jogador como invulnerável ao entrar
            this.loggedIn = false;
            this.lastLoginTime = System.currentTimeMillis(); // Registra o tempo do último login
            this.initialPosition = player.position(); // Salva a posição inicial
            setPlayerAttributes(player, 0, 0); // Define velocidade e força do pulo como 0
        }

        public void setLoggedIn(boolean loggedIn) {
            this.loggedIn = loggedIn;
            if (loggedIn) {
                this.lastLoginTime = System.currentTimeMillis(); // Atualiza o tempo do último login
            }
        }

        public boolean isLoggedIn() {
            return loggedIn;
        }

        public ServerPlayer getPlayer() {
            return player;
        }

        public long getLastLoginTime() {
            return lastLoginTime;
        }

        public Vec3 getInitialPosition() {
            return initialPosition;
        }
    }

    // Eventos do jogador
    @Mod.EventBusSubscriber
    public static class PlayerEvents {

        @SubscribeEvent
        public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) event.getEntity();
                PlayerLogin playerLogin = getPlayerLogin(player);
                playerLogin.setLoggedIn(false); // Reseta o estado de login ao entrar no servidor
                setPlayerAttributes(player, 0, 0); // Define velocidade e força do pulo como 0

                // Verifica se o jogador já está registrado
                String username = player.getGameProfile().getName();
                if (isPlayerRegistered(username)) {
                    // Se o jogador já está registrado, envia a mensagem de login
                    player.sendSystemMessage(Component.literal(loginMessage));
                } else {
                    // Se o jogador não está registrado, envia a mensagem de registro
                    player.sendSystemMessage(Component.literal(registerMessage));
                }

                // Mensagem de boas-vindas personalizada
                if (enableJoinMessage) {
                    String formattedMessage = String.format(joinMessage, player.getGameProfile().getName());
                    player.sendSystemMessage(Component.literal(formattedMessage));
                }
            }
        }

        @SubscribeEvent
        public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) event.getEntity();
                PlayerLogin playerLogin = getPlayerLogin(player);

                // Mensagem de saída personalizada
                if (enableLeaveMessage) {
                    String formattedMessage = String.format(leaveMessage, player.getGameProfile().getName());
                    for (ServerPlayer onlinePlayer : player.getServer().getPlayerList().getPlayers()) {
                        onlinePlayer.sendSystemMessage(Component.literal(formattedMessage));
                    }
                }

                // Remove o jogador do mapa de logins para evitar problemas de estado
                playerLoginMap.remove(player.getUUID());
            }
        }

        @SubscribeEvent
        public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.player instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) event.player;
                PlayerLogin playerLogin = getPlayerLogin(player);
                if (!playerLogin.isLoggedIn()) {
                    // Teleporta o jogador de volta para a posição inicial
                    Vec3 initialPosition = playerLogin.getInitialPosition();
                    player.teleportTo(initialPosition.x, initialPosition.y, initialPosition.z);

                    // Impede o movimento
                    player.setDeltaMovement(0, 0, 0); // Define a velocidade do jogador como zero
                    player.setOnGround(true); // Força o jogador a ficar no chão
                }
            }
        }

        @SubscribeEvent
        public static void onPlayerInteract(PlayerInteractEvent event) {
            if (event.getEntity() instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) event.getEntity();
                PlayerLogin playerLogin = getPlayerLogin(player);

                // Se o jogador não estiver logado, cancela a interação com itens
                if (!playerLogin.isLoggedIn()) {
                    event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent
        public static void onItemToss(ItemTossEvent event) {
            if (event.getPlayer() instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) event.getPlayer();
                PlayerLogin playerLogin = getPlayerLogin(player);

                // Se o jogador não estiver logado, cancela o drop de itens
                if (!playerLogin.isLoggedIn()) {
                    event.setCanceled(true);

                    // Adiciona o item de volta ao inventário do jogador
                    ItemEntity itemEntity = event.getEntity();
                    ItemStack itemStack = itemEntity.getItem();
                    player.getInventory().add(itemStack);
                }
            }
        }

        @SubscribeEvent
        public static void onLivingHurt(LivingHurtEvent event) {
            if (event.getEntity() instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) event.getEntity();
                PlayerLogin playerLogin = getPlayerLogin(player);

                // Se o jogador não estiver logado, cancela o dano e o knockback
                if (!playerLogin.isLoggedIn()) {
                    event.setCanceled(true); // Cancela o dano

                    // Congela o jogador na posição atual
                    Vec3 currentPos = player.position();
                    player.teleportTo(currentPos.x, currentPos.y, currentPos.z);

                    // Impede o movimento
                    player.setDeltaMovement(0, 0, 0); // Define a velocidade do jogador como zero
                    player.setOnGround(true); // Força o jogador a ficar no chão
                }
            }
        }

        @SubscribeEvent
        public static void onLivingDamage(LivingDamageEvent event) {
            if (event.getEntity() instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) event.getEntity();
                PlayerLogin playerLogin = getPlayerLogin(player);

                // Se o jogador não estiver logado, cancela o dano
                if (!playerLogin.isLoggedIn()) {
                    event.setCanceled(true); // Cancela o dano
                }
            }
        }

        @SubscribeEvent
        public static void onServerChat(ServerChatEvent event) {
            ServerPlayer player = event.getPlayer();
            PlayerLogin playerLogin = getPlayerLogin(player);

            // Se o jogador não estiver logado, cancela a mensagem no chat
            if (!playerLogin.isLoggedIn()) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("§cVocê precisa fazer login para falar no chat."));
            }
        }

        @SubscribeEvent
        public static void onCommand(CommandEvent event) {
            if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer) {
                ServerPlayer player = (ServerPlayer) event.getParseResults().getContext().getSource().getEntity();
                PlayerLogin playerLogin = getPlayerLogin(player);

                // Obtém o comando que está sendo executado
                String command = event.getParseResults().getReader().getString();

                // Se o jogador não estiver logado, cancela o comando (exceto /login e /register)
                if (!playerLogin.isLoggedIn() && !command.startsWith("login") && !command.startsWith("register")) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("§cVocê precisa fazer login para usar comandos."));
                }
            }
        }
    }

    // Método para definir os atributos do jogador (velocidade e força do pulo)
    private static void setPlayerAttributes(ServerPlayer player, double speed, double jumpStrength) {
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            movementSpeed.setBaseValue(speed);
        }
    }

    // Método para restaurar os atributos do jogador ao padrão
    private static void resetPlayerAttributes(ServerPlayer player) {
        setPlayerAttributes(player, 0.1, 0.42); // Valores padrão de velocidade e força do pulo no Minecraft
    }

    // Método para criptografar a senha usando SHA-256
    private static String encryptPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}