package net.guardapanda.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.common.EventBusSubscriber;

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

@EventBusSubscriber
public class LoginCommand {

    // Configurações do sistema
    private static final int MAX_LOGIN_ATTEMPTS = 3;
    private static final int OP_LEVEL_REQUIRED = 4;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Arquivos de configuração
    private static final File CONFIG_DIR = new File(FMLPaths.CONFIGDIR.get().toFile(), "guardapanda/login");
    private static final File MESSAGES_DIR = new File(CONFIG_DIR, "messages");
    private static final File playerDataFile = new File(CONFIG_DIR, "players.json");
    private static final File configFile = new File(CONFIG_DIR, "config.json");
    private static final File ipLimitsFile = new File(CONFIG_DIR, "ip_limits.json");
    private static final File ipRegistryFile = new File(CONFIG_DIR, "ip_registry.json");
    
    // Classes de dados
    private static class SessionData {
        boolean isLoggedIn = false;
        int loginAttempts = 0;
    }

    private static class PlayerData {
        String username;
        String password;
        String lastIp;
        
        PlayerData(String username, String password, String lastIp) {
            this.username = username;
            this.password = password;
            this.lastIp = lastIp;
        }
    }

    private static class PlayerState {
        Vec3 position;
        Vec2 rotation;
        net.minecraft.world.level.GameType originalGameMode;
        
        PlayerState(ServerPlayer player) {
            // Se o jogador está morto/esperando respawn, usamos o ponto de respawn (cama ou spawn global)
            try {
                if (player.isDeadOrDying()) {
                    ServerLevel level = player.serverLevel();
                    BlockPos respawnPos = player.getRespawnPosition();
                    float angle = player.getRespawnAngle();

                    if (respawnPos != null) {
                        this.position = Vec3.atBottomCenterOf(respawnPos);
                        this.rotation = new Vec2(angle, 0);
                    } else {
                        BlockPos worldSpawn = level.getSharedSpawnPos();
                        this.position = Vec3.atBottomCenterOf(worldSpawn);
                        this.rotation = new Vec2(0, 0);
                    }
                } else {
                    // Se está vivo, congela na posição atual
                    this.position = player.position();
                    this.rotation = player.getRotationVector();
                }
            } catch (Throwable t) {
                // Em caso de qualquer problema, fallback para a posição atual
                this.position = player.position();
                this.rotation = player.getRotationVector();
            }

            this.originalGameMode = player.gameMode.getGameModeForPlayer();
        }
    }

    private static class SystemConfig {
        String language = "pt";
        int accountsPerIp = 1;
    }
    
    // Dados em memória
    private static final Map<UUID, PlayerData> registeredPlayers = new HashMap<>();
    private static final Map<UUID, SessionData> activeSessions = new HashMap<>();
    private static final Map<UUID, PlayerState> frozenPlayers = new HashMap<>();
    private static final Map<String, List<UUID>> ipToPlayers = new HashMap<>();
    private static Map<String, Integer> customIpLimits = new HashMap<>();
    private static SystemConfig config = new SystemConfig();
    private static Map<String, String> translations = new HashMap<>();

    /* ========== INICIALIZAÇÃO ========== */
    @SubscribeEvent
    public static void onServerStart(ServerStartingEvent event) {
        initializeFiles();
        loadAllData();
    }

    private static void initializeFiles() {
        try {
            if (!CONFIG_DIR.exists() && !CONFIG_DIR.mkdirs()) {
                throw new IOException("Failed to create config directory");
            }
            
            if (!MESSAGES_DIR.exists()) {
                MESSAGES_DIR.mkdirs();
                createDefaultLanguageFiles();
            }
            
            if (!playerDataFile.exists()) {
                FileUtils.writeStringToFile(playerDataFile, "{}", StandardCharsets.UTF_8);
            }
            
            if (!configFile.exists()) {
                FileUtils.writeStringToFile(configFile, GSON.toJson(new SystemConfig()), StandardCharsets.UTF_8);
            }
            
            if (!ipLimitsFile.exists()) {
                FileUtils.writeStringToFile(ipLimitsFile, "{}", StandardCharsets.UTF_8);
            }
            
            if (!ipRegistryFile.exists()) {
                FileUtils.writeStringToFile(ipRegistryFile, "{}", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.err.println("Failed to initialize login system files:");
            e.printStackTrace();
        }
    }

    private static void createDefaultLanguageFiles() throws IOException {
        // pt.json
        Map<String, String> ptMessages = new HashMap<>();
        ptMessages.put("login_success", "§aLogin efetuado com sucesso!");
        ptMessages.put("register_success", "§aRegistro efetuado com sucesso!");
        ptMessages.put("login_required", "§cFaça login primeiro usando /login <senha>!");
        ptMessages.put("wrong_password", "§cSenha incorreta! Tentativas restantes: %d");
        ptMessages.put("max_attempts", "§cMuitas tentativas de login falhas!");
        ptMessages.put("not_registered", "§cVocê não está registrado. Use /register <senha>");
        ptMessages.put("already_logged", "§aVocê já está logado!");
        ptMessages.put("session_error", "§cErro de sessão. Reconecte ao servidor.");
        ptMessages.put("register_usage", "§eUse /register <senha> para criar uma conta!");
        ptMessages.put("login_usage", "§eUse /login <senha> para jogar!");
        ptMessages.put("min_password", "§cA senha deve ter no mínimo 4 caracteres!");
        ptMessages.put("already_registered", "§cVocê já está registrado!");
        ptMessages.put("ip_limit_reached", "§cLimite de %d conta(s) por IP (%s) atingido!");
        ptMessages.put("config_reloaded", "§aConfigurações recarregadas com sucesso!");
        ptMessages.put("ip_already_used", "§cEste IP já está sendo usado por outra conta!");
        ptMessages.put("unfreeze_success", "§aJogador %s descongelado!");
        ptMessages.put("force_login_success", "§aUm administrador liberou seu acesso!");
        ptMessages.put("force_login_admin_success", "§aLogin forçado para %s");
        ptMessages.put("ip_limit_set", "§aLimite de %d contas definido para o IP %s");
        ptMessages.put("register_kick_message", "§aFoi efectuado o registo com sucesso, deve efectuar login agora!");
        
        FileUtils.writeStringToFile(new File(MESSAGES_DIR, "pt.json"), GSON.toJson(ptMessages), StandardCharsets.UTF_8);
        
        // en.json
        Map<String, String> enMessages = new HashMap<>();
        enMessages.put("login_success", "§aLogged in successfully!");
        enMessages.put("register_success", "§aRegistered successfully!");
        enMessages.put("login_required", "§cPlease login first using /login <password>!");
        enMessages.put("wrong_password", "§cWrong password! Remaining attempts: %d");
        enMessages.put("max_attempts", "§cToo many failed login attempts!");
        enMessages.put("not_registered", "§cYou are not registered. Use /register <password>");
        enMessages.put("already_logged", "§aYou are already logged in!");
        enMessages.put("session_error", "§cSession error. Please reconnect.");
        enMessages.put("register_usage", "§eUse /register <password> to create an account!");
        enMessages.put("login_usage", "§eUse /login <password> to play!");
        enMessages.put("min_password", "§cPassword must be at least 4 characters!");
        enMessages.put("already_registered", "§cYou are already registered!");
        enMessages.put("ip_limit_reached", "§cLimit of %d account(s) per IP (%s) reached!");
        enMessages.put("config_reloaded", "§aConfigurations reloaded successfully!");
        enMessages.put("ip_already_used", "§cThis IP is already used by another account!");
        enMessages.put("unfreeze_success", "§aPlayer %s unfrozen!");
        enMessages.put("force_login_success", "§aAn administrator has granted you access!");
        enMessages.put("force_login_admin_success", "§aForced login for %s");
        enMessages.put("ip_limit_set", "§aLimit of %d accounts set for IP %s");
        enMessages.put("register_kick_message", "§aRegistration successful, please login now!");
        
        FileUtils.writeStringToFile(new File(MESSAGES_DIR, "en.json"), GSON.toJson(enMessages), StandardCharsets.UTF_8);
    }

    private static void loadAllData() {
        registeredPlayers.putAll(loadPlayerData());
        config = loadConfig();
        loadCustomIpLimits();
        loadIpRegistry();
        loadMessages(config.language);
    }

    private static void loadMessages(String language) {
        File langFile = new File(MESSAGES_DIR, language + ".json");
        try {
            String json = FileUtils.readFileToString(langFile, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            translations = GSON.fromJson(json, type);
        } catch (IOException e) {
            System.err.println("Error loading language file:");
            e.printStackTrace();
        }
    }

    private static void loadCustomIpLimits() {
        try {
            String json = FileUtils.readFileToString(ipLimitsFile, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            customIpLimits = GSON.fromJson(json, type);
        } catch (IOException e) {
            System.err.println("Error loading IP limits:");
            e.printStackTrace();
        }
    }

    private static void loadIpRegistry() {
        try {
            String json = FileUtils.readFileToString(ipRegistryFile, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
            Map<String, List<String>> tempMap = GSON.fromJson(json, type);
            
            ipToPlayers.clear();
            for (Map.Entry<String, List<String>> entry : tempMap.entrySet()) {
                List<UUID> uuids = new ArrayList<>();
                for (String uuidStr : entry.getValue()) {
                    uuids.add(UUID.fromString(uuidStr));
                }
                ipToPlayers.put(entry.getKey(), uuids);
            }
        } catch (IOException e) {
            System.err.println("Error loading IP registry:");
            e.printStackTrace();
        }
    }

    private static Map<UUID, PlayerData> loadPlayerData() {
        try {
            String json = FileUtils.readFileToString(playerDataFile, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<UUID, PlayerData>>(){}.getType();
            return GSON.fromJson(json, type);
        } catch (IOException e) {
            System.err.println("Error loading player data:");
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    private static SystemConfig loadConfig() {
        try {
            String json = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
            return GSON.fromJson(json, SystemConfig.class);
        } catch (IOException e) {
            System.err.println("Error loading config:");
            e.printStackTrace();
            return new SystemConfig();
        }
    }

    private static void savePlayerData() {
        try {
            FileUtils.writeStringToFile(playerDataFile, GSON.toJson(registeredPlayers), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error saving player data:");
            e.printStackTrace();
        }
    }

    private static void saveIpRegistry() {
        try {
            Map<String, List<String>> toSave = new HashMap<>();
            for (Map.Entry<String, List<UUID>> entry : ipToPlayers.entrySet()) {
                List<String> uuids = new ArrayList<>();
                for (UUID uuid : entry.getValue()) {
                    uuids.add(uuid.toString());
                }
                toSave.put(entry.getKey(), uuids);
            }
            
            FileUtils.writeStringToFile(ipRegistryFile, GSON.toJson(toSave), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error saving IP registry:");
            e.printStackTrace();
        }
    }

    private static void saveCustomIpLimits() {
        try {
            FileUtils.writeStringToFile(ipLimitsFile, GSON.toJson(customIpLimits), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error saving IP limits:");
            e.printStackTrace();
        }
    }

    /* ========== REGISTRO DE COMANDOS ========== */
    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent event) {
        // Comando de login
        event.getDispatcher().register(Commands.literal("login")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("password", MessageArgument.message())
                .executes(ctx -> handleLogin(
                    ctx.getSource().getPlayerOrException(),
                    MessageArgument.getMessage(ctx, "password").getString()
                ))
            )
        );
        
        // Comando de registro
        event.getDispatcher().register(Commands.literal("register")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("password", MessageArgument.message())
                .executes(ctx -> handleRegister(
                    ctx.getSource().getPlayerOrException(),
                    MessageArgument.getMessage(ctx, "password").getString()
                ))
            )
        );
        
        // Comandos administrativos
        registerAdminCommands(event);
    }

    private static void registerAdminCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("auth")
            .requires(source -> source.hasPermission(OP_LEVEL_REQUIRED))
            .then(Commands.literal("unfreeze")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        unfreezePlayer(target);
                        ctx.getSource().sendSuccess(() -> 
                            Component.literal(getMessage("unfreeze_success", target.getName())), false);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("forcelogin")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        UUID targetUuid = target.getUUID();
                        
                        if (!registeredPlayers.containsKey(targetUuid)) {
                            ctx.getSource().sendFailure(Component.literal(getMessage("not_registered")));
                            return 0;
                        }
                        
                        SessionData session = activeSessions.get(targetUuid);
                        if (session != null) {
                            session.isLoggedIn = true;
                            unfreezePlayer(target);
                            sendMessage(target, getMessage("force_login_success"));
                            ctx.getSource().sendSuccess(() -> 
                                Component.literal(getMessage("force_login_admin_success", target.getName())), false);
                            return 1;
                        }
                        return 0;
                    })
                )
            )
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    loadAllData();
                    ctx.getSource().sendSuccess(() -> 
                        Component.literal(getMessage("config_reloaded")), false);
                    return 1;
                })
            )
                .then(Commands.literal("setiplimit")
                    .then(Commands.argument("limit", MessageArgument.message())
                        .executes(ctx -> {
                            String limitStr = MessageArgument.getMessage(ctx, "limit").getString();
                            String ip = ctx.getSource().getPlayerOrException().getIpAddress();
                            
                            try {
                                int limit = Integer.parseInt(limitStr);
                                if (limit < 1) {
                                    ctx.getSource().sendFailure(Component.literal("Limit must be at least 1"));
                                    return 0;
                                }
                                
                                customIpLimits.put(ip, limit);
                                saveCustomIpLimits();
                                ctx.getSource().sendSuccess(() -> 
                                    Component.literal(getMessage("ip_limit_set", limit, ip)), false);
                                return 1;
                            } catch (NumberFormatException e) {
                                ctx.getSource().sendFailure(Component.literal("Invalid number format"));
                                return 0;
                            }
                        })
                    )
                )
            )
        );
    }

    /* ========== LÓGICA PRINCIPAL ========== */
    private static int handleLogin(ServerPlayer player, String password) {
        UUID uuid = player.getUUID();
        SessionData session = activeSessions.get(uuid);
        
        if (session == null) {
            sendMessage(player, getMessage("session_error"));
            return 0;
        }

        if (session.isLoggedIn) {
            sendMessage(player, getMessage("already_logged"));
            return 0;
        }

        PlayerData account = registeredPlayers.get(uuid);
        if (account == null) {
            sendMessage(player, getMessage("not_registered"));
            return 0;
        }

        if (!account.password.equals(password)) {
            session.loginAttempts++;
            
            if (session.loginAttempts >= MAX_LOGIN_ATTEMPTS) {
                player.connection.disconnect(Component.literal(getMessage("max_attempts")));
                return 0;
            }
            
            sendMessage(player, getMessage("wrong_password", MAX_LOGIN_ATTEMPTS - session.loginAttempts));
            return 0;
        }

        // Atualiza o IP do jogador
        String currentIp = player.getIpAddress();
        if (!currentIp.equals(account.lastIp)) {
            // Remove do IP antigo
            if (ipToPlayers.containsKey(account.lastIp)) {
                ipToPlayers.get(account.lastIp).remove(uuid);
                if (ipToPlayers.get(account.lastIp).isEmpty()) {
                    ipToPlayers.remove(account.lastIp);
                }
            }
            
            // Adiciona ao novo IP
            ipToPlayers.computeIfAbsent(currentIp, k -> new ArrayList<>()).add(uuid);
            account.lastIp = currentIp;
            savePlayerData();
            saveIpRegistry();
        }

        // Login bem-sucedido
        session.isLoggedIn = true;
        session.loginAttempts = 0;
        unfreezePlayer(player);
        sendMessage(player, getMessage("login_success"));
        return 1;
    }

    private static int handleRegister(ServerPlayer player, String password) {
        UUID uuid = player.getUUID();
        String ip = player.getIpAddress();
        
        if (registeredPlayers.containsKey(uuid)) {
            sendMessage(player, getMessage("already_registered"));
            return 0;
        }

        if (password.length() < 4) {
            sendMessage(player, getMessage("min_password"));
            return 0;
        }

        int limit = customIpLimits.getOrDefault(ip, config.accountsPerIp);
        int accountsOnIp = ipToPlayers.getOrDefault(ip, Collections.emptyList()).size();
        
        if (accountsOnIp >= limit) {
            sendMessage(player, getMessage("ip_limit_reached", limit, ip));
            return 0;
        }

        PlayerData newAccount = new PlayerData(player.getGameProfile().getName(), password, ip);
        registeredPlayers.put(uuid, newAccount);
        
        ipToPlayers.computeIfAbsent(ip, k -> new ArrayList<>()).add(uuid);
        
        savePlayerData();
        saveIpRegistry();
        
        SessionData session = activeSessions.get(uuid);
        if (session != null) {
            session.isLoggedIn = true;
        }
        
        unfreezePlayer(player);
        
        // Kick o jogador com a mensagem de sucesso
        player.connection.disconnect(Component.literal(getMessage("register_kick_message")));
        
        return 1;
    }

    /* ========== MANIPULAÇÃO DE EVENTOS ========== */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        UUID uuid = player.getUUID();
        String ip = player.getIpAddress();
        
        activeSessions.put(uuid, new SessionData());
        
        if (registeredPlayers.containsKey(uuid)) {
            PlayerData account = registeredPlayers.get(uuid);
            if (!ip.equals(account.lastIp)) {
                if (ipToPlayers.containsKey(account.lastIp)) {
                    ipToPlayers.get(account.lastIp).remove(uuid);
                    if (ipToPlayers.get(account.lastIp).isEmpty()) {
                        ipToPlayers.remove(account.lastIp);
                    }
                }
                
                ipToPlayers.computeIfAbsent(ip, k -> new ArrayList<>()).add(uuid);
                account.lastIp = ip;
                savePlayerData();
                saveIpRegistry();
            }
            
            freezePlayer(player);
            sendMessage(player, getMessage("login_usage"));
        } else {
            freezePlayer(player);
            sendMessage(player, getMessage("register_usage"));
        }
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        activeSessions.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        UUID uuid = player.getUUID();
        if (!isLoggedIn(uuid) && frozenPlayers.containsKey(uuid)) {
            PlayerState state = frozenPlayers.get(uuid);
            player.connection.teleport(state.position.x, state.position.y, state.position.z, 
                                     player.getYRot(), player.getXRot());
            
            if (player.gameMode.getGameModeForPlayer() != net.minecraft.world.level.GameType.SURVIVAL) {
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            }
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!isLoggedIn(event.getPlayer().getUUID())) {
            event.setCanceled(true);
            returnItemToPlayer((ServerPlayer) event.getPlayer(), event.getEntity().getItem());
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isLoggedIn(event.getEntity().getUUID())) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isLoggedIn(event.getEntity().getUUID())) {
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isLoggedIn(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (!isLoggedIn(event.getPlayer().getUUID())) {
            event.setCanceled(true);
            sendMessage(event.getPlayer(), getMessage("login_required"));
        }
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player) {
            String command = "";
            if (!event.getParseResults().getContext().getNodes().isEmpty()) {
                command = event.getParseResults().getContext().getNodes().get(0).getNode().getName();
            }
            
            if (!isLoggedIn(player.getUUID()) && !command.equals("login") && !command.equals("register")) {
                event.setCanceled(true);
                sendMessage(player, getMessage("login_required"));
            }
        }
    }

    /* ========== FUNÇÕES AUXILIARES ========== */
    private static String getMessage(String key, Object... args) {
        String message = translations.getOrDefault(key, "§cMessage not found: " + key);
        return String.format(message, args);
    }

    private static void sendMessage(Player player, String message) {
        player.displayClientMessage(Component.literal(message), false);
    }

    private static boolean isLoggedIn(UUID uuid) {
        SessionData session = activeSessions.get(uuid);
        return session != null && session.isLoggedIn;
    }

    private static void freezePlayer(ServerPlayer player) {
        frozenPlayers.put(player.getUUID(), new PlayerState(player));
        
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAbilities().invulnerable = true;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.getAbilities().setWalkingSpeed(0);
        player.getAbilities().mayBuild = false;
        player.onUpdateAbilities();
    }

    private static void unfreezePlayer(ServerPlayer player) {
        PlayerState state = frozenPlayers.remove(player.getUUID());
        if (state != null) {
            player.setGameMode(state.originalGameMode);
            
            boolean isCreative = state.originalGameMode == net.minecraft.world.level.GameType.CREATIVE;
            player.getAbilities().invulnerable = isCreative;
            player.getAbilities().mayfly = isCreative;
            player.getAbilities().flying = false;
            player.getAbilities().setWalkingSpeed(0.1F);
            player.getAbilities().mayBuild = true;
            player.onUpdateAbilities();
        }
    }

    private static void returnItemToPlayer(ServerPlayer player, ItemStack item) {
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
    }
}
