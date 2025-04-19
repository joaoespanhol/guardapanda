package net.guardapanda.command;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.TicketType;

import net.minecraft.core.SectionPos;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import java.io.IOException;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.File;
import java.lang.reflect.Field;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.google.gson.JsonSyntaxException;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import com.google.common.collect.Lists;
import net.minecraft.world.level.block.entity.HopperBlockEntity;


@Mod.EventBusSubscriber
public class ClearlagCommand {

    // Configurações existentes
    private static final Map<String, Integer> entityCounts = new HashMap<>();
    private static boolean haltEnabled = false;
    private static JsonObject config;
    private static final Set<String> protectedEntities = new HashSet<>();
    private static final Set<String> protectedItems = new HashSet<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static final Set<BlockPos> chunkLoaderPositions = new HashSet<>();

    // Sistema de controle de chunks
    private static final Set<ChunkPos> forcedChunks = new HashSet<>();
    private static boolean chunkLoaderControlEnabled = true;
    private static int maxChunksPerPlayer = 25;
    private static int inactiveChunkTimeout = 30;

    // Novos sistemas
    private static boolean chunkLimiterEnabled = true;
    private static int globalChunkLimit = 100;
    private static boolean createNewChunks = false;
    
    private static boolean hopperLimiterEnabled = true;
    private static int hopperTransferLimit = 6;
    private static int hopperCheckInterval = 20;
    private static final Map<ChunkPos, Integer> hopperTransfers = new HashMap<>();
    
    private static boolean speedLimiterEnabled = true;
    private static double moveMaxSpeed = 0.5;
    private static double flyMaxSpeed = 1.0;

    // Classe auxiliar para coordenadas
    private static class CoordinateData {
        public int x, y, z;
        public int entityCount = 0;
        public int itemCount = 0;

        public CoordinateData(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }


@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		System.out.println("[ClearLag] Registering commands..."); // Debug log
	    loadConfig();
	
	    Predicate<CommandSourceStack> requiresOp = source -> source.hasPermission(2);
	
	    event.getDispatcher().register(Commands.literal("Lagg").requires(requiresOp)
	        // Comandos existentes
	        .then(Commands.literal("clear").executes(context -> clearEntities(context.getSource())))
	        .then(Commands.literal("check").executes(context -> checkWorldInfo(context.getSource())))
	        .then(Commands.literal("reload").executes(context -> reloadConfig(context.getSource())))
	        .then(Commands.literal("killmobs").executes(context -> killMobs(context.getSource())))
	        .then(Commands.literal("area").then(Commands.argument("radius", IntegerArgumentType.integer(1))
	            .executes(context -> clearArea(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
	        .then(Commands.literal("gc").executes(context -> forceGarbageCollection(context.getSource())))
	        .then(Commands.literal("halt").executes(context -> toggleHalt(context.getSource())))
	        .then(Commands.literal("profile").executes(context -> viewPerformance(context.getSource())))
	        .then(Commands.literal("samplememory").then(Commands.argument("time", IntegerArgumentType.integer(1))
	            .executes(context -> sampleMemory(context.getSource(), IntegerArgumentType.getInteger(context, "time")))))
	        .then(Commands.literal("sampleticks").then(Commands.argument("ticks", IntegerArgumentType.integer(1))
	            .executes(context -> sampleTicks(context.getSource(), IntegerArgumentType.getInteger(context, "ticks")))))
	        .then(Commands.literal("tps").executes(context -> checkTPS(context.getSource())))
	        .then(Commands.literal("ping").executes(context -> calculatePing(context.getSource())))
	        .then(Commands.literal("performance").executes(context -> viewPerformance(context.getSource())))
	        .then(Commands.literal("memory").executes(context -> viewMemory(context.getSource())))
	        .then(Commands.literal("free").executes(context -> freeMemory(context.getSource())))
	        .then(Commands.literal("lista").executes(context -> listTopEntitiesAndItems(context.getSource())))
	
	        // Comandos de whitelist
	        .then(Commands.literal("whitelist")
	            .then(Commands.literal("add").executes(context -> addHeldItemToWhitelist(context.getSource())))
	            .then(Commands.literal("remove").executes(context -> removeHeldItemFromWhitelist(context.getSource())))
	            .then(Commands.literal("list").executes(context -> listWhitelistedItems(context.getSource()))))
	
	        // Comandos de chunk control
	        .then(Commands.literal("chunkcontrol")
	            .then(Commands.literal("enable").executes(context -> enableChunkControl(context.getSource())))
	            .then(Commands.literal("disable").executes(context -> disableChunkControl(context.getSource())))
	            .then(Commands.literal("status").executes(context -> chunkControlStatus(context.getSource())))
	            .then(Commands.literal("list").executes(context -> listForcedChunks(context.getSource())))
	            .then(Commands.literal("unloadall").executes(context -> unloadAllForcedChunks(context.getSource())))
	            .then(Commands.literal("limit").then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
	                .executes(context -> setChunkLimit(context.getSource(), IntegerArgumentType.getInteger(context, "limit")))))
	            .then(Commands.literal("info").executes(context -> showChunkInfo(context.getSource()))))
	
	        // Novos comandos para limitadores
	        .then(Commands.literal("chunklimiter")
	            .then(Commands.literal("enable").executes(context -> {
	                chunkLimiterEnabled = true;
	                saveConfig();
	                context.getSource().sendSuccess(() -> Component.literal("Chunk limiter enabled"), false);
	                return 1;
	            }))
	            .then(Commands.literal("disable").executes(context -> {
	                chunkLimiterEnabled = false;
	                saveConfig();
	                context.getSource().sendSuccess(() -> Component.literal("Chunk limiter disabled"), false);
	                return 1;
	            }))
	            .then(Commands.literal("setlimit").then(Commands.argument("limit", IntegerArgumentType.integer(1))
	                .executes(context -> {
	                    globalChunkLimit = IntegerArgumentType.getInteger(context, "limit");
	                    saveConfig();
	                    context.getSource().sendSuccess(() -> Component.literal("Global chunk limit set to " + globalChunkLimit), false);
	                    return 1;
	                })))
	            .then(Commands.literal("setnewchunks").then(Commands.argument("allow", BoolArgumentType.bool())
	                .executes(context -> {
	                    createNewChunks = BoolArgumentType.getBool(context, "allow");
	                    saveConfig();
	                    context.getSource().sendSuccess(() -> Component.literal("New chunk generation set to " + createNewChunks), false);
	                    return 1;
	                }))))
	
	        // Comandos de hopper limiter
	        .then(Commands.literal("hopperlimiter")
	            .then(Commands.literal("enable").executes(context -> {
	                hopperLimiterEnabled = true;
	                saveConfig();
	                context.getSource().sendSuccess(() -> Component.literal("Hopper limiter enabled"), false);
	                return 1;
	            }))
	            .then(Commands.literal("disable").executes(context -> {
	                hopperLimiterEnabled = false;
	                saveConfig();
	                context.getSource().sendSuccess(() -> Component.literal("Hopper limiter disabled"), false);
	                return 1;
	            }))
	            .then(Commands.literal("setlimit").then(Commands.argument("limit", IntegerArgumentType.integer(1))
	                .executes(context -> {
	                    hopperTransferLimit = IntegerArgumentType.getInteger(context, "limit");
	                    saveConfig();
	                    context.getSource().sendSuccess(() -> Component.literal("Hopper transfer limit set to " + hopperTransferLimit), false);
	                    return 1;
	                })))
	            .then(Commands.literal("setinterval").then(Commands.argument("ticks", IntegerArgumentType.integer(1))
	                .executes(context -> {
	                    hopperCheckInterval = IntegerArgumentType.getInteger(context, "ticks");
	                    saveConfig();
	                    context.getSource().sendSuccess(() -> Component.literal("Hopper check interval set to " + hopperCheckInterval + " ticks"), false);
	                    return 1;
	                }))))
	
	        // Comandos de speed limiter
	        .then(Commands.literal("speedlimiter")
	            .then(Commands.literal("enable").executes(context -> {
	                speedLimiterEnabled = true;
	                saveConfig();
	                context.getSource().sendSuccess(() -> Component.literal("Speed limiter enabled"), false);
	                return 1;
	            }))
	            .then(Commands.literal("disable").executes(context -> {
	                speedLimiterEnabled = false;
	                saveConfig();
	                context.getSource().sendSuccess(() -> Component.literal("Speed limiter disabled"), false);
	                return 1;
	            }))
	            .then(Commands.literal("setspeed")
	                .then(Commands.argument("move", DoubleArgumentType.doubleArg(0.1, 10.0))
	                    .then(Commands.argument("fly", DoubleArgumentType.doubleArg(0.1, 10.0))
	                        .executes(context -> {
	                            moveMaxSpeed = DoubleArgumentType.getDouble(context, "move");
	                            flyMaxSpeed = DoubleArgumentType.getDouble(context, "fly");
	                            saveConfig();
	                            context.getSource().sendSuccess(() -> Component.literal(
	                                String.format("Speed limits set to Move: %.1f, Fly: %.1f", moveMaxSpeed, flyMaxSpeed)), false);
	                            return 1;
	                        })))
	            )));
	
	    scheduleAutoClear();
	}

private static void loadConfig() {
    File configDir = new File("config/guardapanda");
    File configFile = new File(configDir, "clearlag_config.json");
    
    // Verificar se o arquivo de configuração existe, se não, gerar um novo
    if (!configFile.exists()) {
        System.out.println("[ClearLag] Config file not found, generating default...");
        generateDefaultConfig();
    }

    // Tentar carregar o arquivo de configuração
    try (FileReader reader = new FileReader(configFile)) {
        JsonElement parsed = JsonParser.parseReader(reader);
        config = parsed.getAsJsonObject();
        
        // Carregar entidades protegidas
        protectedEntities.clear();
        if (config.has("protected_entities")) {
            JsonArray protectedEntitiesArray = config.getAsJsonArray("protected_entities");
            protectedEntitiesArray.forEach(element -> {
                if (element.isJsonPrimitive()) {
                    protectedEntities.add(element.getAsString());
                }
            });
        }
        
        // Carregar itens protegidos
        protectedItems.clear();
        if (config.has("protected_items")) {
            JsonArray protectedItemsArray = config.getAsJsonArray("protected_items");
            protectedItemsArray.forEach(element -> {
                if (element.isJsonPrimitive()) {
                    protectedItems.add(element.getAsString());
                }
            });
        }

        // Carregar configurações de controle de chunks
        if (config.has("chunk_control_enabled")) {
            chunkLoaderControlEnabled = config.get("chunk_control_enabled").getAsBoolean();
        }
        
        if (config.has("max_chunks_per_player")) {
            maxChunksPerPlayer = config.get("max_chunks_per_player").getAsInt();
        }
        
        if (config.has("inactive_chunk_timeout_minutes")) {
            inactiveChunkTimeout = config.get("inactive_chunk_timeout_minutes").getAsInt();
        }

        // Carregar configurações do limitador de chunks
        if (config.has("chunk_limiter")) {
            JsonObject chunkLimiter = config.getAsJsonObject("chunk_limiter");
            chunkLimiterEnabled = chunkLimiter.get("enabled").getAsBoolean();
            globalChunkLimit = chunkLimiter.get("limit").getAsInt();
            createNewChunks = chunkLimiter.get("create_new_chunks").getAsBoolean();
        }

        // Carregar configurações do limitador de hoppers
        if (config.has("hopper_limiter")) {
            JsonObject hopperLimiter = config.getAsJsonObject("hopper_limiter");
            hopperLimiterEnabled = hopperLimiter.get("enabled").getAsBoolean();
            hopperTransferLimit = hopperLimiter.get("transfer_limit").getAsInt();
            hopperCheckInterval = hopperLimiter.get("check_interval").getAsInt();
        }

        // Carregar configurações do limitador de velocidade
        if (config.has("speed_limiter")) {
            JsonObject speedLimiter = config.getAsJsonObject("speed_limiter");
            speedLimiterEnabled = speedLimiter.get("enabled").getAsBoolean();
            moveMaxSpeed = speedLimiter.get("move_max_speed").getAsDouble();
            flyMaxSpeed = speedLimiter.get("fly_max_speed").getAsDouble();
        }

        System.out.println("[ClearLag] Config loaded successfully from: " + configFile.getAbsolutePath());
    } catch (IOException e) {
        System.err.println("[ClearLag] IO Error loading config file:");
        e.printStackTrace();
        generateDefaultConfig();
    } catch (JsonSyntaxException e) {
        System.err.println("[ClearLag] Invalid JSON syntax in config file:");
        e.printStackTrace();
        generateDefaultConfig();
    } catch (Exception e) {
        System.err.println("[ClearLag] Unexpected error loading config:");
        e.printStackTrace();
        generateDefaultConfig();
    }
}

private static void generateDefaultConfig() {
    File configDir = new File("config/guardapanda");
    File configFile = new File(configDir, "clearlag_config.json");
    
    try {
        // Criar diretório se não existir
        if (!configDir.exists() && !configDir.mkdirs()) {
            System.err.println("[ClearLag] Failed to create config directory!");
            return;
        }

        // Criar objeto de configuração padrão
        JsonObject defaultConfig = new JsonObject();

        // Configurações gerais
        JsonObject settings = new JsonObject();
        settings.addProperty("language", "English");
        settings.addProperty("auto-update", true);
        settings.addProperty("enable-api", true);
        settings.addProperty("use-internal-tps", true);
        defaultConfig.add("settings", settings);

        // Mensagens
        JsonObject messages = new JsonObject();
        messages.addProperty("top_coordinates_header", "&6&lTop 10 coordinates with most entities and items:");
        messages.addProperty("top_coordinates_entry", "&7- &e%s &7- Entities: &c%d &7- Items: &c%d");
        messages.addProperty("warning_60s", "&4&l[ClearLag] &cWarning: Items and mobs will be removed in &760 seconds!");
        messages.addProperty("warning_30s", "&4&l[ClearLag] &cWarning: Items and mobs will be removed in &730 seconds!");
        messages.addProperty("warning_10s", "&4&l[ClearLag] &cWarning: Items and mobs will be removed in &710 seconds!");
        messages.addProperty("warning_5s", "&4&l[ClearLag] &cWarning: Items and mobs will be removed in &75 seconds!");
        messages.addProperty("warning_1s", "&4&l[ClearLag] &cWarning: Items and mobs will be removed in &71 second!");
        messages.addProperty("cleared_entities", "&a&l[ClearLag] &fRemoved &c%d &fitems and &c%d &fmobs.");
        messages.addProperty("auto_clear_enabled", "&a&l[ClearLag] &fAuto clear enabled. Interval: &c%d seconds.");
        messages.addProperty("auto_clear_disabled", "&a&l[ClearLag] &fAuto clear disabled.");
        messages.addProperty("forced_gc", "&a&l[ClearLag] &fForced garbage collection.");
        messages.addProperty("config_reloaded", "&a&l[ClearLag] &fConfig reloaded.");
        messages.addProperty("world_info", "&a&l[ClearLag] &fWorld has &c%d &fentities.");
        messages.addProperty("max_memory", "&aMax Memory: &f%d MB");
        messages.addProperty("allocated_memory", "&aAllocated Memory: &f%d MB");
        messages.addProperty("free_memory", "&aFree Memory: &f%d MB");
        messages.addProperty("feedback_message", "&a&l[ClearLag] &fRemoved &c%d &fentities.");
        messages.addProperty("entity_removed_message", "&a&l[ClearLag] &fRemoved &c%d &fentities of type &c%s.");
        messages.addProperty("broadcast_message", "&a&l[ClearLag] &fRemoved &c%d &fentities.");
        messages.addProperty("tps_message", "&a&l[ClearLag] &fCurrent TPS: &c%.2f");
        messages.addProperty("average_tick_message", "&a&l[ClearLag] &fAverage time of last &c%d &fticks: &c%.2f ms");
        messages.addProperty("halt_enabled_message", "&a&l[ClearLag] &fHALT mode enabled.");
        messages.addProperty("halt_disabled_message", "&a&l[ClearLag] &fHALT mode disabled.");
        messages.addProperty("kill_mobs_message", "&a&l[ClearLag] &fKilled &c%d &fmobs.");
        messages.addProperty("clear_area_message", "&a&l[ClearLag] &fRemoved &c%d &fentities in &c%d &fblocks radius.");
        messages.addProperty("player_only_command", "&cThis command can only be executed by a player.");
        messages.addProperty("gc_message", "&a&l[ClearLag] &fForced garbage collection performed.");
        messages.addProperty("reload_config_message", "&a&l[ClearLag] &fConfig reloaded successfully.");
        messages.addProperty("no_modules_message", "&cNo modules found in config.");
        messages.addProperty("module_status_message", "&a&l[ClearLag] &fModule status:");
        messages.addProperty("module_entry_message", "&a&l[ClearLag] &fModule &c%s &f- &c%s");
        messages.addProperty("toggle_module_message", "&a&l[ClearLag] &fUse /clearlag admin <module> to toggle modules.");
        messages.addProperty("ping_message", "&a&l[ClearLag] &fYour ping: &c%d ms");
        messages.addProperty("whitelist_add_success", "&aItem &e%s &aadded to whitelist!");
        messages.addProperty("whitelist_remove_success", "&aItem &e%s &aremoved from whitelist!");
        messages.addProperty("whitelist_item_not_found", "&cItem &e%s ¬is not in whitelist!");
        messages.addProperty("whitelist_empty", "&aNo items in whitelist!");
        messages.addProperty("whitelist_header", "&6&lWhitelisted items:");
        messages.addProperty("whitelist_entry", "&7- &e%s");
        messages.addProperty("hold_item_message", "&cYou need to hold an item!");
        messages.addProperty("chunk_control_enabled", "&aChunk Loader control enabled");
        messages.addProperty("chunk_control_disabled", "&cChunk Loader control disabled");
        messages.addProperty("chunk_control_status", "&6Chunk Control Status: %s");
        messages.addProperty("chunk_control_list_header", "&6Active forced chunks:");
        messages.addProperty("chunk_control_list_entry", "&7- X: &e%d &7Z: &e%d");
        messages.addProperty("chunk_control_unload_all", "&aAll forced chunks (%d) unloaded");
        messages.addProperty("chunk_control_none", "&eNo active forced chunks");
        messages.addProperty("chunk_limit_set", "&aPlayer chunk limit set to: &e%d");
        messages.addProperty("chunk_info_header", "&6=== Chunk Info ===");
        messages.addProperty("chunk_info_active", "&aActive chunks: &e%d");
        messages.addProperty("chunk_info_limit", "&aPer player limit: &e%d");
        messages.addProperty("chunk_info_timeout", "&aInactivity timeout: &e%d minutes");
        messages.addProperty("chunk_info_player_entry", "&7- &e%s&7: &c%d chunks");
        defaultConfig.add("messages", messages);

        // Configurações de remoção automática
        JsonObject autoRemoval = new JsonObject();
        autoRemoval.addProperty("enabled", true);
        autoRemoval.addProperty("interval", 300);
        autoRemoval.addProperty("clear_entities", true);
        autoRemoval.addProperty("clear_items", true);
        autoRemoval.addProperty("clear_passive_mobs", false);
        defaultConfig.add("auto_removal", autoRemoval);

        // Entidades protegidas
        JsonArray protectedEntitiesArray = new JsonArray();
        protectedEntitiesArray.add("minecraft:player");
        protectedEntitiesArray.add("minecraft:armor_stand");
        defaultConfig.add("protected_entities", protectedEntitiesArray);

        // Itens protegidos
        JsonArray protectedItemsArray = new JsonArray();
        protectedItemsArray.add("minecraft:diamond");
        protectedItemsArray.add("minecraft:nether_star");
        defaultConfig.add("protected_items", protectedItemsArray);

        // Controle de chunks
        defaultConfig.addProperty("chunk_control_enabled", true);
        defaultConfig.addProperty("max_chunks_per_player", 25);
        defaultConfig.addProperty("inactive_chunk_timeout_minutes", 30);

        // Limitador de chunks
        JsonObject chunkLimiter = new JsonObject();
        chunkLimiter.addProperty("enabled", true);
        chunkLimiter.addProperty("limit", 100);
        chunkLimiter.addProperty("create_new_chunks", false);
        defaultConfig.add("chunk_limiter", chunkLimiter);

        // Limitador de hoppers
        JsonObject hopperLimiter = new JsonObject();
        hopperLimiter.addProperty("enabled", true);
        hopperLimiter.addProperty("transfer_limit", 6);
        hopperLimiter.addProperty("check_interval", 20);
        defaultConfig.add("hopper_limiter", hopperLimiter);

        // Limitador de velocidade
        JsonObject speedLimiter = new JsonObject();
        speedLimiter.addProperty("enabled", true);
        speedLimiter.addProperty("move_max_speed", 0.5);
        speedLimiter.addProperty("fly_max_speed", 1.0);
        defaultConfig.add("speed_limiter", speedLimiter);

        // Escrever o arquivo
        try (FileWriter writer = new FileWriter(configFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(defaultConfig, writer);
            System.out.println("[ClearLag] Default config file generated at: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[ClearLag] Failed to write default config file:");
            e.printStackTrace();
        }
    } catch (Exception e) {
        System.err.println("[ClearLag] Error generating default config:");
        e.printStackTrace();
    }
}


    private static void saveConfig() {
        File configFile = new File("config/guardapanda/clearlag_config.json");
        try {
            configFile.getParentFile().mkdirs();
            
            try (FileWriter writer = new FileWriter(configFile)) {
                config.addProperty("chunk_control_enabled", chunkLoaderControlEnabled);
                config.addProperty("max_chunks_per_player", maxChunksPerPlayer);
                config.addProperty("inactive_chunk_timeout_minutes", inactiveChunkTimeout);
                
                JsonObject chunkLimiter = new JsonObject();
                chunkLimiter.addProperty("enabled", chunkLimiterEnabled);
                chunkLimiter.addProperty("limit", globalChunkLimit);
                chunkLimiter.addProperty("create_new_chunks", createNewChunks);
                config.add("chunk_limiter", chunkLimiter);

                JsonObject hopperLimiter = new JsonObject();
                hopperLimiter.addProperty("enabled", hopperLimiterEnabled);
                hopperLimiter.addProperty("transfer_limit", hopperTransferLimit);
                hopperLimiter.addProperty("check_interval", hopperCheckInterval);
                config.add("hopper_limiter", hopperLimiter);

                JsonObject speedLimiter = new JsonObject();
                speedLimiter.addProperty("enabled", speedLimiterEnabled);
                speedLimiter.addProperty("move_max_speed", moveMaxSpeed);
                speedLimiter.addProperty("fly_max_speed", flyMaxSpeed);
                config.add("speed_limiter", speedLimiter);
                
                JsonArray protectedEntitiesArray = new JsonArray();
                protectedEntities.forEach(protectedEntitiesArray::add);
                config.add("protected_entities", protectedEntitiesArray);
                
                JsonArray protectedItemsArray = new JsonArray();
                protectedItems.forEach(protectedItemsArray::add);
                config.add("protected_items", protectedItemsArray);
                
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(config, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static String getMessage(String key) {
        if (config == null || !config.has("messages")) {
            return "§cConfiguration error";
        }
        
        JsonObject messages = config.getAsJsonObject("messages");
        if (messages == null || !messages.has(key)) {
            return "§cMessage not found: " + key;
        }
        
        return messages.get(key).getAsString().replace('&', '§');
    }

    private static void scheduleAutoClear() {
        if (config == null || !config.has("auto_removal")) {
            return;
        }

        JsonObject autoRemoval = config.getAsJsonObject("auto_removal");
        if (autoRemoval == null || !autoRemoval.has("enabled") || !autoRemoval.get("enabled").getAsBoolean()) {
            return;
        }

        boolean clearEntities = autoRemoval.has("clear_entities") && autoRemoval.get("clear_entities").getAsBoolean();
        boolean clearItems = autoRemoval.has("clear_items") && autoRemoval.get("clear_items").getAsBoolean();
        int interval = autoRemoval.has("interval") ? autoRemoval.get("interval").getAsInt() : 300;

        if (!clearEntities && !clearItems) {
            return;
        }

        scheduler.scheduleAtFixedRate(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || server.isStopped()) return;

            // Update forced chunks
            for (ServerLevel world : server.getAllLevels()) {
                detectChunkLoaders(world);
                checkForcedChunks(world);
            }

            String warningMessage = "§4§l[ClearLag] §cWarning: " + 
                (clearItems && clearEntities ? "Items and mobs will be removed in " :
                 clearItems ? "Items will be removed in " : "Mobs will be removed in ") + 
                "§7" + interval + " §cseconds!";
            
            server.getPlayerList().broadcastSystemMessage(Component.literal(warningMessage), false);

            scheduleWarning(server, interval - 60, buildWarningMessage(clearItems, clearEntities, 60));
            scheduleWarning(server, interval - 30, buildWarningMessage(clearItems, clearEntities, 30));
            scheduleWarning(server, interval - 10, buildWarningMessage(clearItems, clearEntities, 10));
            scheduleWarning(server, interval - 5, buildWarningMessage(clearItems, clearEntities, 5));
            scheduleWarning(server, interval - 1, buildWarningMessage(clearItems, clearEntities, 1));

            scheduler.schedule(() -> {
                server.executeIfPossible(() -> {
                    if (server.isStopped()) return;
                    
                    int totalItems = 0;
                    int totalMobs = 0;
                    
                    for (ServerLevel world : server.getAllLevels()) {
                        if (world != null && !world.isClientSide()) {
                            if (clearItems) {
                                totalItems += clearItems(world);
                            }
                            if (clearEntities) {
                                totalMobs += killMobs(world);
                            }
                        }
                    }
                    
                    String resultMessage = "§a§l[ClearLag] §f" + 
                        (clearItems && clearEntities ? String.format("Removed §c%d §fitems and §c%d §fmobs.", totalItems, totalMobs) :
                         clearItems ? String.format("Removed §c%d §fitems.", totalItems) :
                         String.format("Removed §c%d §fmobs.", totalMobs));
                    
                    server.getPlayerList().broadcastSystemMessage(Component.literal(resultMessage), false);
                });
            }, interval, TimeUnit.SECONDS);
        }, interval, interval, TimeUnit.SECONDS);

        // Schedule chunk checks every 30 seconds
        scheduler.scheduleAtFixedRate(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || server.isStopped()) return;

            for (ServerLevel world : server.getAllLevels()) {
                detectChunkLoaders(world);
                checkForcedChunks(world);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private static String buildWarningMessage(boolean clearItems, boolean clearEntities, int seconds) {
        return "§4§l[ClearLag] §cWarning: " + 
            (clearItems && clearEntities ? "Items and mobs will be removed in " :
             clearItems ? "Items will be removed in " : "Mobs will be removed in ") + 
            "§7" + seconds + " §c" + (seconds == 1 ? "second!" : "seconds!");
    }

    private static void scheduleWarning(MinecraftServer server, int delay, String message) {
        if (delay <= 0) return;
        
        scheduler.schedule(() -> {
            server.executeIfPossible(() -> {
                if (!server.isStopped()) {
                    server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
                }
            });
        }, delay, TimeUnit.SECONDS);
    }

    private static int clearItems(ServerLevel world) {
        List<Entity> entities = Lists.newArrayList(world.getEntities().getAll());
        int removedItems = 0;
        for (Entity entity : entities) {
            if (entity instanceof ItemEntity && !isProtectedItem((ItemEntity) entity)) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                removedItems++;
            }
        }
        return removedItems;
    }

    private static boolean isProtectedItem(ItemEntity itemEntity) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
        return protectedItems.contains(itemId.toString());
    }

    private static int killMobs(ServerLevel world) {
        boolean clearPassive = config.getAsJsonObject("auto_removal").get("clear_passive_mobs").getAsBoolean();
        List<Entity> entities = Lists.newArrayList(world.getEntities().getAll());
        int killedMobs = 0;

        for (Entity entity : entities) {
            if ((clearPassive || isNonPassiveMob(entity)) && !isProtectedEntity(entity)) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                killedMobs++;
            }
        }
        return killedMobs;
    }

    private static boolean isProtectedEntity(Entity entity) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return protectedEntities.contains(entityId.toString());
    }

    private static boolean isNonPassiveMob(Entity entity) {
        if (!(entity instanceof Mob mob) || entity.hasCustomName()) {
            return false;
        }

        if (config.getAsJsonObject("auto_removal").get("clear_passive_mobs").getAsBoolean()) {
            return true;
        }

        return switch (mob.getType().getCategory()) {
            case CREATURE, AMBIENT, WATER_AMBIENT -> false;
            default -> true;
        };
    }

    private static int clearEntities(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        JsonObject autoRemoval = config.getAsJsonObject("auto_removal");
        boolean clearEntities = autoRemoval.get("clear_entities").getAsBoolean();
        boolean clearItems = autoRemoval.get("clear_items").getAsBoolean();

        List<Entity> entities = Lists.newArrayList(world.getEntities().getAll());
        int removedEntities = 0;
        entityCounts.clear();
        
        for (Entity entity : entities) {
            if (shouldRemoveEntity(entity, clearItems, clearEntities)) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                removedEntities++;
                String entityType = entity.getClass().getSimpleName();
                entityCounts.put(entityType, entityCounts.getOrDefault(entityType, 0) + 1);
            }
        }
        
        sendFeedback(source, removedEntities, clearItems, clearEntities);
        return removedEntities;
    }

    private static boolean shouldRemoveEntity(Entity entity, boolean clearItems, boolean clearEntities) {
        if (entity instanceof Player || entity instanceof ServerPlayer) {
            return false;
        }
        
        if (entity instanceof ItemEntity) {
            return clearItems && !isProtectedItem((ItemEntity) entity);
        }
        
        if (entity instanceof LivingEntity) {
            return clearEntities && !isProtectedEntity(entity) && isNonPassiveMob(entity);
        }
        
        return (clearItems || clearEntities) && 
               (entity instanceof ExperienceOrb || 
                entity instanceof PrimedTnt || 
                entity instanceof Projectile || 
                entity instanceof Boat || 
                entity instanceof Minecart || 
                entity instanceof Painting || 
                entity instanceof ItemFrame);
    }

    private static void sendFeedback(CommandSourceStack source, int removedEntities, boolean clearItems, boolean clearEntities) {
        String feedback = "§a§l[ClearLag] §f" + 
            (clearItems && clearEntities ? String.format("Removed §c%d §fentities.", removedEntities) :
             clearItems ? String.format("Removed §c%d §fitems.", removedEntities) :
             String.format("Removed §c%d §fmobs.", removedEntities));
        
        source.sendSuccess(() -> Component.literal(feedback), true);
        
        for (Map.Entry<String, Integer> entry : entityCounts.entrySet()) {
            source.sendSuccess(() -> Component.literal(
                String.format("§a§l[ClearLag] §fRemoved §c%d §fentities of type §c%s.", entry.getValue(), entry.getKey())), true);
        }
        
        if (source.getLevel() instanceof ServerLevel) {
            ServerLevel world = (ServerLevel) source.getLevel();
            world.getPlayers(player -> true).forEach(player -> 
                player.sendSystemMessage(Component.literal(feedback)));
        }
    }

    private static int calculatePing(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer)) {
            source.sendFailure(Component.literal(getMessage("player_only_command")));
            return 0;
        }
        ServerPlayer player = (ServerPlayer) source.getEntity();
        int ping = player.latency;
        source.sendSuccess(() -> Component.literal(String.format(getMessage("ping_message"), ping)), true);
        return ping;
    }

    private static int freeMemory(CommandSourceStack source) {
        System.gc();
        long freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        source.sendSuccess(() -> Component.literal(String.format(getMessage("free_memory"), freeMemory)), true);
        return 1;
    }

    private static int checkTPS(CommandSourceStack source) {
	    MinecraftServer server = source.getServer();
	    double tps = 1000.0 / Math.max(50, server.getAverageTickTime());
	    tps = Math.min(20.0, tps);
	    final double finalTps = tps; // Create final copy
	    source.sendSuccess(() -> Component.literal(String.format(getMessage("tps_message"), finalTps)), true);
	    return (int) tps;
	}

    private static int sampleMemory(CommandSourceStack source, int time) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long allocatedMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        source.sendSuccess(() -> Component.literal(String.format(getMessage("max_memory"), maxMemory)), true);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("allocated_memory"), allocatedMemory)), true);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("free_memory"), freeMemory)), true);
        return 1;
    }

    private static int sampleTicks(CommandSourceStack source, int ticks) {
        MinecraftServer server = source.getServer();
        long[] tickTimes = server.tickTimes;
        long totalTime = 0;
        int count = 0;
        for (int i = 0; i < ticks && i < tickTimes.length; i++) {
            totalTime += tickTimes[i];
            count++;
        }
        double averageTickTime = (double) totalTime / count / 1_000_000;
        source.sendSuccess(() -> Component.literal(String.format(getMessage("average_tick_message"), ticks, averageTickTime)), true);
        return 1;
    }

    private static int viewMemory(CommandSourceStack source) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long allocatedMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        source.sendSuccess(() -> Component.literal(String.format(getMessage("max_memory"), maxMemory)), true);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("allocated_memory"), allocatedMemory)), true);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("free_memory"), freeMemory)), true);
        return 1;
    }
	    
	private static int viewPerformance(CommandSourceStack source) {
	    MinecraftServer server = source.getServer();
	    double tps = 1000.0 / Math.max(50, server.getAverageTickTime());
	    tps = Math.min(20.0, tps);
	    Runtime runtime = Runtime.getRuntime();
	    long maxMemory = runtime.maxMemory() / 1024 / 1024;
	    long allocatedMemory = runtime.totalMemory() / 1024 / 1024;
	    long freeMemory = runtime.freeMemory() / 1024 / 1024;
	    
	    // Create final copies for lambda
	    final double finalTps = tps;
	    final long finalMaxMemory = maxMemory;
	    final long finalAllocatedMemory = allocatedMemory;
	    final long finalFreeMemory = freeMemory;
	    
	    source.sendSuccess(() -> Component.literal(String.format(getMessage("tps_message"), finalTps)), true);
	    source.sendSuccess(() -> Component.literal(String.format(getMessage("max_memory"), finalMaxMemory)), true);
	    source.sendSuccess(() -> Component.literal(String.format(getMessage("allocated_memory"), finalAllocatedMemory)), true);
	    source.sendSuccess(() -> Component.literal(String.format(getMessage("free_memory"), finalFreeMemory)), true);
	    return 1;
	}

    private static int toggleHalt(CommandSourceStack source) {
        haltEnabled = !haltEnabled;
        if (haltEnabled) {
            source.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, source.getServer());
            source.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false, source.getServer());
            source.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, source.getServer());
            source.sendSuccess(() -> Component.literal(getMessage("halt_enabled_message")), true);
        } else {
            source.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true, source.getServer());
            source.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(true, source.getServer());
            source.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true, source.getServer());
            source.sendSuccess(() -> Component.literal(getMessage("halt_disabled_message")), true);
        }
        return 1;
    }
    
	private static int killMobs(CommandSourceStack source) {
	    ServerLevel world = source.getLevel();
	    boolean clearPassive = config.getAsJsonObject("auto_removal").get("clear_passive_mobs").getAsBoolean();
	    List<Entity> entities = Lists.newArrayList(world.getEntities().getAll());
	    int killedMobs = 0;
	
	    for (Entity entity : entities) {
	        if ((clearPassive || isNonPassiveMob(entity)) && !isProtectedEntity(entity)) {
	            entity.remove(Entity.RemovalReason.DISCARDED);
	            killedMobs++;
	        }
	    }
	
	    final int finalKilledMobs = killedMobs; // Create final copy
	    source.sendSuccess(() -> Component.literal(String.format(getMessage("kill_mobs_message"), finalKilledMobs)), true);
	    return killedMobs;
	}
	
	private static int clearArea(CommandSourceStack source, int radius) {
	    ServerLevel world = source.getLevel();
	    Entity player = source.getEntity();
	    List<Entity> entities = Lists.newArrayList(world.getEntities().getAll());
	    int removedEntities = 0;
	    for (Entity entity : entities) {
	        if (player.distanceToSqr(entity) <= radius * radius && shouldRemoveEntity(entity, true, true)) {
	            entity.remove(Entity.RemovalReason.DISCARDED);
	            removedEntities++;
	        }
	    }
	
	    final int finalRemovedEntities = removedEntities; // Create final copies
	    final int finalRadius = radius;
	    source.sendSuccess(() -> Component.literal(String.format(getMessage("clear_area_message"), finalRemovedEntities, finalRadius)), true);
	    return removedEntities;
	}

    private static int forceGarbageCollection(CommandSourceStack source) {
        System.gc();
        source.sendSuccess(() -> Component.literal(getMessage("gc_message")), true);
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        loadConfig();
        source.sendSuccess(() -> Component.literal(getMessage("reload_config_message")), true);
        return 1;
    }

    private static int checkWorldInfo(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        List<Entity> entities = Lists.newArrayList(world.getEntities().getAll());
        int entityCount = entities.size();
        source.sendSuccess(() -> Component.literal(String.format(getMessage("world_info"), entityCount)), true);
        return entityCount;
    }

    private static int manageModules(CommandSourceStack source) {
        JsonObject modules = config.getAsJsonObject("modules");
        if (modules == null) {
            source.sendFailure(Component.literal(getMessage("no_modules_message")));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(getMessage("module_status_message")), true);
        for (Map.Entry<String, JsonElement> entry : modules.entrySet()) {
            String moduleName = entry.getKey();
            boolean enabled = entry.getValue().getAsBoolean();
            source.sendSuccess(() -> Component.literal(String.format(getMessage("module_entry_message"), moduleName, enabled ? "Enabled" : "Disabled")), true);
        }
        source.sendSuccess(() -> Component.literal(getMessage("toggle_module_message")), true);
        return 1;
    }

	private static int listTopEntitiesAndItems(CommandSourceStack source) {
	    ServerLevel world = source.getLevel();
	    Map<String, CoordinateData> coordinateMap = new HashMap<>();
	
	    for (Entity entity : world.getEntities().getAll()) {
	        int x = (int) entity.getX();
	        int y = (int) entity.getY();
	        int z = (int) entity.getZ();
	        String coordKey = x + ";" + y + ";" + z;
	
	        CoordinateData data = coordinateMap.computeIfAbsent(coordKey, k -> new CoordinateData(x, y, z));
	
	        if (entity instanceof ItemEntity) {
	            data.itemCount += ((ItemEntity) entity).getItem().getCount();
	        } else if (!(entity instanceof Player)) {
	            data.entityCount++;
	        }
	    }
	
	    List<CoordinateData> sortedCoordinates = new ArrayList<>(coordinateMap.values());
	    sortedCoordinates.sort((a, b) -> Integer.compare(b.entityCount + b.itemCount, a.entityCount + a.itemCount));
	
	    source.sendSuccess(() -> Component.literal("§6§lTop 10 Coordinates with most entities and items:"), false);
	
	    // Create a final list to use in the lambda
	    final List<CoordinateData> finalSortedCoordinates = sortedCoordinates;
	    final int count = Math.min(10, sortedCoordinates.size());
	    
	    for (int i = 0; i < count; i++) {
	        final int index = i; // Create final copy of loop variable
	        source.sendSuccess(() -> {
	            CoordinateData data = finalSortedCoordinates.get(index);
	            return Component.literal(String.format(
	                "§e%d. §7X: §b%d §7Y: §b%d §7Z: §b%d §7- Entities: §c%d §7- Items: §c%d",
	                index + 1, data.x, data.y, data.z, data.entityCount, data.itemCount
	            ));
	        }, false);
	    }
	
	    return 1;
	}

    private static int addHeldItemToWhitelist(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(getMessage("player_only_command")));
            return 0;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            source.sendFailure(Component.literal(getMessage("hold_item_message")));
            return 0;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(heldItem.getItem());
        protectedItems.add(itemId.toString());

        JsonArray protectedItemsArray = new JsonArray();
        protectedItems.forEach(protectedItemsArray::add);
        config.add("protected_items", protectedItemsArray);
        saveConfig();

        source.sendSuccess(() -> Component.literal(String.format(getMessage("whitelist_add_success"), itemId)), true);
        return 1;
    }

    private static int removeHeldItemFromWhitelist(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(getMessage("player_only_command")));
            return 0;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            source.sendFailure(Component.literal(getMessage("hold_item_message")));
            return 0;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(heldItem.getItem());
        if (protectedItems.remove(itemId.toString())) {
            JsonArray protectedItemsArray = new JsonArray();
            protectedItems.forEach(protectedItemsArray::add);
            config.add("protected_items", protectedItemsArray);
            saveConfig();

            source.sendSuccess(() -> Component.literal(String.format(getMessage("whitelist_remove_success"), itemId)), true);
        } else {
            source.sendFailure(Component.literal(String.format(getMessage("whitelist_item_not_found"), itemId)));
        }
        return 1;
    }

    private static int listWhitelistedItems(CommandSourceStack source) {
        if (protectedItems.isEmpty()) {
            source.sendSuccess(() -> Component.literal(getMessage("whitelist_empty")), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(getMessage("whitelist_header")), false);
        protectedItems.forEach(item -> 
            source.sendSuccess(() -> Component.literal(String.format(getMessage("whitelist_entry"), item)), false)
        );
        return 1;
    }

    private static int enableChunkControl(CommandSourceStack source) {
        chunkLoaderControlEnabled = true;
        saveConfig();
        source.sendSuccess(() -> Component.literal(getMessage("chunk_control_enabled")), true);
        return 1;
    }

    private static int disableChunkControl(CommandSourceStack source) {
        chunkLoaderControlEnabled = false;
        saveConfig();
        source.sendSuccess(() -> Component.literal(getMessage("chunk_control_disabled")), true);
        return 1;
    }

    private static int chunkControlStatus(CommandSourceStack source) {
        String status = chunkLoaderControlEnabled ? "§aEnabled" : "§cDisabled";
        source.sendSuccess(() -> Component.literal(String.format(getMessage("chunk_control_status"), status)), true);
        source.sendSuccess(() -> Component.literal(String.format("§6Active forced chunks: §e%d", forcedChunks.size())), true);
        return 1;
    }

    private static int listForcedChunks(CommandSourceStack source) {
        if (forcedChunks.isEmpty()) {
            source.sendSuccess(() -> Component.literal(getMessage("chunk_control_none")), true);
            return 1;
        }
        
        source.sendSuccess(() -> Component.literal(getMessage("chunk_control_list_header")), true);
        forcedChunks.forEach(chunkPos -> 
            source.sendSuccess(() -> Component.literal(
                String.format(getMessage("chunk_control_list_entry"), chunkPos.x, chunkPos.z)
            ), true)
        );
        return 1;
    }
    
    private static int unloadAllForcedChunks(CommandSourceStack source) {
        int count = forcedChunks.size();
        MinecraftServer server = source.getServer();
        
        for (ServerLevel world : server.getAllLevels()) {
            for (ChunkPos chunkPos : forcedChunks) {
                world.getChunkSource().updateChunkForced(chunkPos, false);
            }
        }
        
        forcedChunks.clear();
        source.sendSuccess(() -> Component.literal(String.format(getMessage("chunk_control_unload_all"), count)), true);
        return 1;
    }
    
    private static int setChunkLimit(CommandSourceStack source, int limit) {
        maxChunksPerPlayer = limit;
        config.addProperty("max_chunks_per_player", limit);
        saveConfig();
        source.sendSuccess(() -> Component.literal(String.format(getMessage("chunk_limit_set"), limit)), true);
        return 1;
    }
    
    private static int showChunkInfo(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(getMessage("chunk_info_header")), false);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("chunk_info_active"), forcedChunks.size())), false);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("chunk_info_limit"), maxChunksPerPlayer)), false);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("chunk_info_timeout"), inactiveChunkTimeout)), false);
        return 1;
    }
    
    private static void detectChunkLoaders(ServerLevel world) {
        if (!chunkLoaderControlEnabled) return;
        
        for (ServerPlayer player : world.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    ChunkPos chunkPos = new ChunkPos(playerChunk.x + x, playerChunk.z + z);
                    
                    if (!forcedChunks.contains(chunkPos)) {
                        long playerChunkCount = forcedChunks.stream()
                            .filter(cp -> isPlayerKeepingChunk(cp, player.getUUID()))
                            .count();
                            
                        if (playerChunkCount >= maxChunksPerPlayer) {
                            continue;
                        }
                        
                        world.getChunkSource().updateChunkForced(chunkPos, true);
                        forcedChunks.add(chunkPos);
                    }
                }
            }
        }
    }
    
    private static boolean isPlayerKeepingChunk(ChunkPos chunkPos, UUID playerId) {
        // Simplified implementation
        return true;
    }
    
    private static boolean isKnownChunkLoader(BlockEntity blockEntity) {
        if (blockEntity == null) return false;
        ResourceLocation blockId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return blockId != null && (blockId.toString().equals("ftbchunks:chunk_loader") ||
               blockId.toString().equals("chickenchunks:chunk_loader") ||
               blockId.toString().matches(".*chunkloader.*"));
    }
    
    private static void checkForcedChunks(ServerLevel world) {
        Set<ChunkPos> chunksToKeep = new HashSet<>();

        // Player chunks
        for (ServerPlayer player : world.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    chunksToKeep.add(new ChunkPos(playerChunk.x + x, playerChunk.z + z));
                }
            }
        }

        // Chunk loader blocks
        for (BlockPos pos : chunkLoaderPositions) {
            if (world.isLoaded(pos)) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity != null && isKnownChunkLoader(blockEntity)) {
                    chunksToKeep.add(new ChunkPos(pos));
                }
            }
        }

        // Update forced chunks
        Iterator<ChunkPos> iterator = forcedChunks.iterator();
        while (iterator.hasNext()) {
            ChunkPos chunkPos = iterator.next();
            if (!chunksToKeep.contains(chunkPos)) {
                world.getChunkSource().updateChunkForced(chunkPos, false);
                iterator.remove();
            }
        }

        // Add new forced chunks
        for (ChunkPos chunkPos : chunksToKeep) {
            if (!forcedChunks.contains(chunkPos)) {
                world.getChunkSource().updateChunkForced(chunkPos, true);
                forcedChunks.add(chunkPos);
            }
        }
    }

    // Event listeners for new systems

	@Mod.EventBusSubscriber
	public static class ChunkLimiterEvents {
		private static final Set<ChunkPos> alreadyLoadedChunks = new HashSet<>();

		@SubscribeEvent
		public static void onChunkLoad(ChunkEvent.Load event) {
			if (!chunkLimiterEnabled || !(event.getLevel() instanceof ServerLevel level)) return;

			LevelChunk chunk = (LevelChunk) event.getChunk();
			ChunkPos chunkPos = chunk.getPos();

			boolean isNewChunk = !alreadyLoadedChunks.contains(chunkPos);
			alreadyLoadedChunks.add(chunkPos);

			if ((!createNewChunks && isNewChunk) || countLoadedChunks() >= globalChunkLimit) {
				level.getChunkSource().removeRegionTicket(TicketType.PLAYER, chunkPos, 31, chunkPos);

				if (countLoadedChunks() >= globalChunkLimit) {
					// Alternative approach using the public API
					try {
						ChunkMap chunkMap = level.getChunkSource().chunkMap;
						
						// Get all chunks that are currently loaded
						Iterable<ChunkHolder> chunks = getLoadedChunks(chunkMap);
						
						for (ChunkHolder holder : chunks) {
							if (holder != null) {
								LevelChunk loadedChunk = holder.getTickingChunk();
								if (loadedChunk != null && !loadedChunk.getPos().equals(chunkPos)) {
									level.getChunkSource().removeRegionTicket(
										TicketType.PLAYER, 
										loadedChunk.getPos(), 
										31, 
										loadedChunk.getPos()
									);
									if (countLoadedChunks() < globalChunkLimit) {
										break;
									}
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}

// Helper method to get loaded chunks without using protected methods
    private static Iterable<ChunkHolder> getLoadedChunks(ChunkMap chunkMap) {
        try {
            // Try to use the public API first
            if (chunkMap instanceof Iterable) {
                return (Iterable<ChunkHolder>) chunkMap;
            }
            
            // Fallback to reflection if needed
            Field visibleChunksField = ChunkMap.class.getDeclaredField("visibleChunkMap");
            visibleChunksField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Long, ChunkHolder> visibleChunks = (Map<Long, ChunkHolder>) visibleChunksField.get(chunkMap);
            return visibleChunks.values();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }


    private static int countLoadedChunks() {
        int count = 0;
        for (ServerLevel level : ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
            count += level.getChunkSource().chunkMap.size();
        }
        return count;
    }
}

    @Mod.EventBusSubscriber
    public static class HopperLimiterEvents {
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % hopperCheckInterval == 0) {
                hopperTransfers.entrySet().removeIf(entry -> entry.getValue() <= 0);
                hopperTransfers.replaceAll((k, v) -> 0);
            }
        }
    }

    @Mod.EventBusSubscriber
    public static class SpeedLimiterEvents {
        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent event) {
            if (!speedLimiterEnabled || event.phase != TickEvent.Phase.END) return;

            if (!(event.player instanceof ServerPlayer player)) return;
            if (player.isSpectator()) return;

            double dx = player.getX() - player.xo;
            double dz = player.getZ() - player.zo;
            double distanceSq = dx * dx + dz * dz;

            double maxSpeed = player.getAbilities().flying ? flyMaxSpeed : moveMaxSpeed;
            double maxSpeedSq = maxSpeed * maxSpeed;

            if (distanceSq > maxSpeedSq) {
                player.connection.teleport(player.xo, player.getY(), player.zo, player.getYRot(), player.getXRot());
            }
        }
    }
} // Esta chave fecha a classe ClearlagCommand