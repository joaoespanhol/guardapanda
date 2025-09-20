package net.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.function.Predicate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.Collection;

import java.io.IOException;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.File;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;

import com.google.gson.JsonSyntaxException;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import com.google.common.collect.Lists;

@Mod.EventBusSubscriber
public class ClearlagCommand {
    private static final Map<String, Integer> entityCounts = new ConcurrentHashMap<>();
    private static boolean haltEnabled = false;
    private static JsonObject config;
    private static final Set<String> protectedEntities = new HashSet<>();
    private static final Set<String> protectedItems = new HashSet<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static long lastGCTime = 0;
    private static final long GC_COOLDOWN = 30000; // 30 seconds cooldown
    private static final Map<String, Long> moduleCooldowns = new ConcurrentHashMap<>();
    private static final Map<String, Double> tickTimes = new ConcurrentHashMap<>();
    private static final List<PerformanceSample> performanceSamples = new ArrayList<>();
    private static long lastSampleTime = 0;
    
    // Chunk loader tracking
    private static final Map<Long, ChunkLoaderInfo> activeChunkLoaders = new ConcurrentHashMap<>();
    private static boolean chunkLoaderControlEnabled = false;
    private static final ScheduledExecutorService chunkLoaderScanner = Executors.newScheduledThreadPool(1);
    
    // Performance monitoring
    private static class PerformanceSample {
        public long time;
        public double tps;
        public long memoryUsed;
        public long entitiesCount;
        
        public PerformanceSample(long time, double tps, long memoryUsed, long entitiesCount) {
            this.time = time;
            this.tps = tps;
            this.memoryUsed = memoryUsed;
            this.entitiesCount = entitiesCount;
        }
    }
    
    // Classe auxiliar para armazenar dados das coordenadas
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
    
    // Classe para representar chunk loaders
    private static class ChunkLoaderInfo {
        public final long chunkPos;
        public final String dimension;
        public final String sourceType;
        public final BlockPos position;
        public final long loadTime;
        
        public ChunkLoaderInfo(long chunkPos, String dimension, String sourceType, BlockPos position) {
            this.chunkPos = chunkPos;
            this.dimension = dimension;
            this.sourceType = sourceType;
            this.position = position;
            this.loadTime = System.currentTimeMillis();
        }
        
        public int getChunkX() {
            return (int) (chunkPos & 0xFFFFFFFFL);
        }
        
        public int getChunkZ() {
            return (int) ((chunkPos >> 32) & 0xFFFFFFFFL);
        }
        
        public int getBlockX() {
            return position != null ? position.getX() : getChunkX() * 16 + 8;
        }
        
        public int getBlockY() {
            return position != null ? position.getY() : 64;
        }
        
        public int getBlockZ() {
            return position != null ? position.getZ() : getChunkZ() * 16 + 8;
        }
        
        @Override
        public String toString() {
            return String.format("Chunk: %d,%d | Dim: %s | Source: %s | Pos: %s", 
                getChunkX(), getChunkZ(),
                dimension, sourceType, position != null ? position.toString() : "N/A");
        }
    }
    
    // Module system
    private static final Map<String, LagFixerModule> modules = new ConcurrentHashMap<>();
    
    private interface LagFixerModule {
        String getName();
        String getDescription();
        boolean isEnabled();
        void setEnabled(boolean enabled);
        void tick(ServerLevel world);
        default void onCommand(CommandSourceStack source, String[] args) {}
    }
    
    // Implementação de módulos
    private static class MobAiReducerModule implements LagFixerModule {
        private boolean enabled = true;
        
        @Override
        public String getName() { return "MobAiReducer"; }
        
        @Override
        public String getDescription() { return "Optimizes mob AI to reduce server load"; }
        
        @Override
        public boolean isEnabled() { return enabled; }
        
        @Override
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        @Override
        public void tick(ServerLevel world) {
            if (!enabled) return;
            
            for (Entity entity : world.getEntities().getAll()) {
                if (entity instanceof Mob mob) {
                    // Simplificar AI quando longe de jogadores
                    boolean hasNearbyPlayers = false;
                    for (Player player : world.players()) {
                        if (player.distanceToSqr(mob) < 1024) { // 32 blocks
                            hasNearbyPlayers = true;
                            break;
                        }
                    }
                    
                    if (!hasNearbyPlayers) {
                        // Reduzir atividade de mobs longe de jogadores
                        mob.setNoAi(true);
                    } else {
                        mob.setNoAi(false);
                    }
                }
            }
        }
    }
    
    private static class WorldCleanerModule implements LagFixerModule {
        private boolean enabled = true;
        
        @Override
        public String getName() { return "WorldCleaner"; }
        
        @Override
        public String getDescription() { return "Cleans up items and entities automatically"; }
        
        @Override
        public boolean isEnabled() { return enabled; }
        
        @Override
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        @Override
        public void tick(ServerLevel world) {
            if (!enabled) return;
            
            // Limpeza automática de itens
            int clearedItems = clearItems(world);
            if (clearedItems > 50) {
                world.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§6[WorldCleaner] §fCleared §c" + clearedItems + " §fitems"), false);
            }
        }
    }
    
    private static class ChunkLoaderControllerModule implements LagFixerModule {
        private boolean enabled = false;
        
        @Override
        public String getName() { return "ChunkLoaderController"; }
        
        @Override
        public String getDescription() { return "Controls and monitors chunk loaders to reduce lag"; }
        
        @Override
        public boolean isEnabled() { return enabled; }
        
        @Override
        public void setEnabled(boolean enabled) { 
            this.enabled = enabled;
            chunkLoaderControlEnabled = enabled;
            
            if (enabled) {
                startChunkLoaderScanning();
            } else {
                stopChunkLoaderScanning();
            }
        }
        
        @Override
        public void tick(ServerLevel world) {
            if (!enabled) return;
            
            // Verificar e limitar chunk loaders se necessário
            if (activeChunkLoaders.size() > 100) { // Limite arbitrário
                removeOldestChunkLoaders(activeChunkLoaders.size() - 100);
            }
        }
        
        @Override
        public void onCommand(CommandSourceStack source, String[] args) {
            if (args.length >= 1) {
                switch (args[0]) {
                    case "list":
                        listChunkLoaders(source);
                        break;
                    case "clear":
                        clearAllChunkLoaders(source);
                        break;
                    case "stats":
                        showChunkLoaderStats(source);
                        break;
                    default:
                        source.sendSuccess(() -> Component.literal("§cUso: /lagfixer chunkloaders <list|clear|stats>"), false);
                }
            }
        }
    }

    static {
        // Registrar módulos
        modules.put("mobaireducer", new MobAiReducerModule());
        modules.put("worldcleaner", new WorldCleanerModule());
        modules.put("chunkloadercontroller", new ChunkLoaderControllerModule());
        // Adicione mais módulos aqui conforme necessário
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        loadConfig();
        MinecraftForge.EVENT_BUS.register(ClearlagCommand.class);

        Predicate<CommandSourceStack> requiresOp = source -> source.hasPermission(2);

        event.getDispatcher().register(Commands.literal("Lagg").requires(requiresOp)
            .then(Commands.literal("clear")
                .executes(context -> clearEntities(context.getSource())))
            .then(Commands.literal("check")
                .executes(context -> checkWorldInfo(context.getSource())))
            .then(Commands.literal("reload")
                .executes(context -> reloadConfig(context.getSource())))
            .then(Commands.literal("killmobs")
                .executes(context -> killMobs(context.getSource())))
            .then(Commands.literal("area")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                    .executes(context -> clearArea(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
            .then(Commands.literal("admin")
                .then(Commands.literal("modules")
                    .executes(context -> listModules(context.getSource()))
                    .then(Commands.argument("module", StringArgumentType.string())
                        .then(Commands.argument("enable", BoolArgumentType.bool())
                            .executes(context -> toggleModule(context.getSource(), 
                                StringArgumentType.getString(context, "module"), 
                                BoolArgumentType.getBool(context, "enable"))))))
                .executes(context -> manageModules(context.getSource())))
            .then(Commands.literal("gc")
                .executes(context -> forceGarbageCollection(context.getSource())))
            .then(Commands.literal("halt")
                .executes(context -> toggleHalt(context.getSource())))
            .then(Commands.literal("profile")
                .executes(context -> viewPerformance(context.getSource())))
            .then(Commands.literal("samplememory")
                .then(Commands.argument("time", IntegerArgumentType.integer(1))
                    .executes(context -> sampleMemory(context.getSource(), IntegerArgumentType.getInteger(context, "time")))))
            .then(Commands.literal("sampleticks")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                    .executes(context -> sampleTicks(context.getSource(), IntegerArgumentType.getInteger(context, "ticks")))))
            .then(Commands.literal("tps")
                .executes(context -> checkTPS(context.getSource())))
            .then(Commands.literal("ping")
                .executes(context -> calculatePing(context.getSource())))
            .then(Commands.literal("performance")
                .executes(context -> viewPerformance(context.getSource())))
            .then(Commands.literal("memory")
                .executes(context -> viewMemory(context.getSource())))
            .then(Commands.literal("free")
                .executes(context -> freeMemory(context.getSource())))
            .then(Commands.literal("lista")
                .executes(context -> listTopEntitiesAndItems(context.getSource())))
            .then(Commands.literal("whitelist")
                .then(Commands.literal("add")
                    .executes(context -> addHeldItemToWhitelist(context.getSource())))
                .then(Commands.literal("remove")
                    .executes(context -> removeHeldItemFromWhitelist(context.getSource())))
                .then(Commands.literal("list")
                    .executes(context -> listWhitelistedItems(context.getSource()))))
            .then(Commands.literal("chunkstats")
                .executes(context -> showChunkStats(context.getSource())))
            .then(Commands.literal("entitystats")
                .executes(context -> showEntityStats(context.getSource())))
            .then(Commands.literal("lagspike")
                .executes(context -> detectLagSpikes(context.getSource())))
            .then(Commands.literal("diagnose")
                .executes(context -> diagnoseLag(context.getSource())))
            .then(Commands.literal("chunkloaders")
                .then(Commands.literal("list")
                    .executes(context -> listChunkLoaders(context.getSource())))
                .then(Commands.literal("clear")
                    .executes(context -> clearAllChunkLoaders(context.getSource())))
                .then(Commands.literal("stats")
                    .executes(context -> showChunkLoaderStats(context.getSource()))))
            .then(Commands.literal("tp")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                    .then(Commands.argument("y", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(context -> teleportToCoordinates(context.getSource(), 
                                IntegerArgumentType.getInteger(context, "x"),
                                IntegerArgumentType.getInteger(context, "y"),
                                IntegerArgumentType.getInteger(context, "z"))))))));

        scheduleAutoClear();
        schedulePerformanceMonitoring();
    }
    
    private static void schedulePerformanceMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || server.isStopped()) return;
            
            // Coletar métricas de performance
            double tps = 1000.0 / Math.max(50, server.getAverageTickTime());
            tps = Math.min(20.0, tps);
            
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            
            // Contar entidades totais
            long totalEntities = 0;
            for (ServerLevel world : server.getAllLevels()) {
                totalEntities += world.getEntities().getAll().size();
            }
            
            // Armazenar amostra
            performanceSamples.add(new PerformanceSample(
                System.currentTimeMillis(), tps, usedMemory, totalEntities));
            
            // Manter apenas as últimas 100 amostras
            if (performanceSamples.size() > 100) {
                performanceSamples.remove(0);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private static void loadConfig() {
        File configFile = new File("config/guardapanda/clearlag_config.json");
        if (!configFile.exists()) {
            generateDefaultConfig();
            config = new JsonObject();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            config = parsed.getAsJsonObject();
            
            protectedEntities.clear();
            if (config.has("protected_entities")) {
                JsonArray protectedEntitiesArray = config.getAsJsonArray("protected_entities");
                protectedEntitiesArray.forEach(element -> {
                    if (element.isJsonPrimitive()) {
                        protectedEntities.add(element.getAsString());
                    }
                });
            }
            
            protectedItems.clear();
            if (config.has("protected_items")) {
                JsonArray protectedItemsArray = config.getAsJsonArray("protected_items");
                protectedItemsArray.forEach(element -> {
                    if (element.isJsonPrimitive()) {
                        protectedItems.add(element.getAsString());
                    }
                });
            }
            
            // Carregar configurações de módulos
            if (config.has("modules")) {
                JsonObject modulesConfig = config.getAsJsonObject("modules");
                for (Map.Entry<String, JsonElement> entry : modulesConfig.entrySet()) {
                    String moduleName = entry.getKey();
                    boolean enabled = entry.getValue().getAsBoolean();
                    
                    if (modules.containsKey(moduleName)) {
                        modules.get(moduleName).setEnabled(enabled);
                    }
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("[ClearLag] Erro ao carregar configuração:");
            e.printStackTrace();
            config = new JsonObject();
        }
    }

    private static void saveConfig() {
        File configFile = new File("config/guardapanda/clearlag_config.json");
        try (FileWriter writer = new FileWriter(configFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void generateDefaultConfig() {
        File configFile = new File("config/guardapanda/clearlag_config.json");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();

            JsonObject defaultConfig = new JsonObject();

            JsonObject settings = new JsonObject();
            settings.addProperty("language", "English");
            settings.addProperty("auto-update", true);
            settings.addProperty("enable-api", true);
            settings.addProperty("use-internal-tps", true);
            defaultConfig.add("settings", settings);

            JsonObject messages = new JsonObject();
            messages.addProperty("top_coordinates_header", "&6&lTop 10 coordenadas com mais entidades e itens:");
            messages.addProperty("top_coordinates_entry", "&7- &e%s &7- Entidades: &c%d &7- Itens: &c%d");
            messages.addProperty("warning_60s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &760 segundos!");
            messages.addProperty("warning_30s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &730 segundos!");
            messages.addProperty("warning_20s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &720 segundos!");
            messages.addProperty("warning_15s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &715 segundos!");
            messages.addProperty("warning_10s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &710 segundos!");
            messages.addProperty("warning_5s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &75 segundos!");
            messages.addProperty("warning_4s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &74 segundos!");
            messages.addProperty("warning_3s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &73 segundos!");
            messages.addProperty("warning_2s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &72 segundos!");
            messages.addProperty("warning_1s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &71 segundo!");
            messages.addProperty("cleared_entities", "&a&l[ClearLag] &fForam removidos &c%d &fitens e &c%d &fmobs.");
            messages.addProperty("auto_clear_enabled", "&a&l[ClearLag] &fLimpeza automática ativada. Intervalo: &c%d segundos.");
            messages.addProperty("auto_clear_disabled", "&a&l[ClearLag] &fLimpeza automática desativada.");
            messages.addProperty("forced_gc", "&a&l[ClearLag] &fForced garbage collection.");
            messages.addProperty("config_reloaded", "&a&l[ClearLag] &fConfiguração recarregada.");
            messages.addProperty("world_info", "&a&l[ClearLag] &fO mundo tem &c%d &fentidades.");
            messages.addProperty("invalid_activity_type", "&cInvalid activity type. Use 'mobspawn' or 'chunkload'.");
            messages.addProperty("max_memory", "&aMax Memory: &f%d MB");
            messages.addProperty("allocated_memory", "&aAllocated Memory: &f%d MB");
            messages.addProperty("free_memory", "&aFree Memory: &f%d MB");
            messages.addProperty("feedback_message", "&a&l[ClearLag] &fForam removidas &c%d &fentidades.");
            messages.addProperty("entity_removed_message", "&a&l[ClearLag] &fRemovidos &c%d &fentidades do tipo &c%s.");
            messages.addProperty("broadcast_message", "&a&l[ClearLag] &fForam removidas &c%d &fentidades.");
            messages.addProperty("tps_message", "&a&l[ClearLag] &fTPS atual: &c%.2f");
            messages.addProperty("average_tick_message", "&a&l[ClearLag] &fTempo médio dos últimos &c%d &fticks: &c%.2f ms");
            messages.addProperty("profile_activity_message", "&a&l[ClearLag] &fPerfil de atividade &c%s &fpor &c%d &fsegundos concluído em &c%d ms.");
            messages.addProperty("halt_enabled_message", "&a&l[ClearLag] &fModo HALT ativado.");
            messages.addProperty("halt_disabled_message", "&a&l[ClearLag] &fModo HALT desativado.");
            messages.addProperty("kill_mobs_message", "&a&l[ClearLag] &fForam mortos &c%d &fmobs.");
            messages.addProperty("clear_area_message", "&a&l[ClearLag] &fForam removidas &c%d &fentidades em um raio de &c%d &fblocos.");
            messages.addProperty("teleport_message", "&a&l[ClearLag] &fTeleportado para o chunk &c(%d, %d).");
            messages.addProperty("player_only_command", "&cEste comando só pode ser executado por um jogador.");
            messages.addProperty("gc_message", "&a&l[ClearLag] &fColeta de lixo forçada realizada.");
            messages.addProperty("reload_config_message", "&a&l[ClearLag] &fConfiguração recarregada com sucesso.");
            messages.addProperty("no_modules_message", "&cNenhum módulo encontrado na configuração.");
            messages.addProperty("module_status_message", "&a&l[ClearLag] &fStatus dos módulos:");
            messages.addProperty("module_entry_message", "&a&l[ClearLag] &fMódulo &c%s &f- &c%s");
            messages.addProperty("toggle_module_message", "&a&l[ClearLag] &fUse /Lagg admin <módulo> para ativar/desativar módulos.");
            messages.addProperty("ping_message", "&a&l[ClearLag] &fSeu ping: &c%d ms");
            messages.addProperty("whitelist_add_success", "&aItem &e%s &aadicionado à whitelist!");
            messages.addProperty("whitelist_remove_success", "&aItem &e%s &aremovido da whitelist!");
            messages.addProperty("whitelist_item_not_found", "&cO item &e%s &cnão está na whitelist!");
            messages.addProperty("whitelist_empty", "&aNenhum item na whitelist!");
            messages.addProperty("whitelist_header", "&6&lItens na whitelist:");
            messages.addProperty("whitelist_entry", "&7- &e%s");
            messages.addProperty("hold_item_message", "&cVocê precisa segurar um item na mão!");
            messages.addProperty("chunk_stats_header", "&6&lEstatísticas de Chunks:");
            messages.addProperty("chunk_stats_entry", "&7- &e%s: &c%d chunks");
            messages.addProperty("entity_stats_header", "&6&lEstatísticas de Entidades:");
            messages.addProperty("entity_stats_entry", "&7- &e%s: &c%d entidades");
            messages.addProperty("lag_spike_detected", "&4&l[ClearLag] &cLag spike detectado! TPS: %.2f");
            messages.addProperty("no_lag_spikes", "&a&l[ClearLag] &fNenhum lag spike significativo detectado.");
            messages.addProperty("chunkloader_header", "&6&lChunk Loaders Ativos:");
            messages.addProperty("chunkloader_entry", "&e%d. §7Chunk: §b%d,%d §7| Dim: §b%s §7| Tipo: §b%s §7| Pos: §b%s");
            messages.addProperty("chunkloader_cleared", "&aRemovidos §c%d §achunk loaders.");
            messages.addProperty("chunkloader_stats_header", "&6&lEstatísticas de Chunk Loaders:");
            messages.addProperty("chunkloader_stats_total", "§7Total: §c%d");
            messages.addProperty("chunkloader_stats_dim", "§7- §e%s§7: §c%d");
            messages.addProperty("chunkloader_stats_type", "§7- §e%s§7: §c%d");
            messages.addProperty("teleport_success", "&aTeleportado para §e%d, %d, %d");
            messages.addProperty("teleport_fail", "&cNão foi possível teleportar para §e%d, %d, %d");
            defaultConfig.add("messages", messages);

            JsonObject autoRemoval = new JsonObject();
            autoRemoval.addProperty("enabled", true);
            autoRemoval.addProperty("interval", 300);
            autoRemoval.addProperty("clear_entities", true);
            autoRemoval.addProperty("clear_items", true);
            autoRemoval.addProperty("clear_passive_mobs", false);
            defaultConfig.add("auto_removal", autoRemoval);

            JsonArray protectedEntitiesArray = new JsonArray();
            protectedEntitiesArray.add("minecraft:player");
            protectedEntitiesArray.add("minecraft:armor_stand");
            defaultConfig.add("protected_entities", protectedEntitiesArray);

            JsonArray protectedItemsArray = new JsonArray();
            protectedItemsArray.add("minecraft:diamond");
            protectedItemsArray.add("minecraft:nether_star");
            defaultConfig.add("protected_items", protectedItemsArray);
            
            // Configurações padrão para módulos
            JsonObject modulesConfig = new JsonObject();
            for (String moduleName : modules.keySet()) {
                modulesConfig.addProperty(moduleName, true);
            }
            defaultConfig.add("modules", modulesConfig);

            try (FileWriter writer = new FileWriter(configFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(defaultConfig, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        
        JsonElement messageElement = messages.get(key);
        if (messageElement == null || !messageElement.isJsonPrimitive()) {
            return "§cInvalid message: " + key;
        }
        
        return messageElement.getAsString().replace('&', '§');
    }

    private static void scheduleAutoClear() {
        if (config == null || !config.has("auto_removal")) {
            return;
        }

        JsonObject autoRemoval = config.getAsJsonObject("auto_removal");
        if (autoRemoval == null || 
            !autoRemoval.has("enabled") || 
            !autoRemoval.get("enabled").getAsBoolean()) {
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

            StringBuilder warningMessage = new StringBuilder();
            warningMessage.append("§4§l[ClearLag] §cAviso: ");
            
            if (clearItems && clearEntities) {
                warningMessage.append("Itens e mobs serão removidos em ");
            } else if (clearItems) {
                warningMessage.append("Itens serão removidos em ");
            } else if (clearEntities) {
                warningMessage.append("Mobs serão removidos em ");
            }
            
            warningMessage.append("§7").append(interval).append(" §csegundos!");
            
            server.getPlayerList().broadcastSystemMessage(Component.literal(warningMessage.toString()), false);

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
                    
                    StringBuilder resultMessage = new StringBuilder();
                    resultMessage.append("§a§l[ClearLag] §f");
                    
                    if (clearItems && clearEntities) {
                        resultMessage.append(String.format("Foram removidos §c%d §fitens e §c%d §fmobs.", totalItems, totalMobs));
                    } else if (clearItems) {
                        resultMessage.append(String.format("Foram removidos §c%d §fitens.", totalItems));
                    } else if (clearEntities) {
                        resultMessage.append(String.format("Foram removidos §c%d §fmobs.", totalMobs));
                    }
                    
                    server.getPlayerList().broadcastSystemMessage(Component.literal(resultMessage.toString()), false);
                });
            }, interval, TimeUnit.SECONDS);
        }, interval, interval, TimeUnit.SECONDS);
    }

    private static String buildWarningMessage(boolean clearItems, boolean clearEntities, int seconds) {
        StringBuilder message = new StringBuilder();
        message.append("§4§l[ClearLag] §cAviso: ");
        
        if (clearItems && clearEntities) {
            message.append("Itens e mobs serão removidos em ");
        } else if (clearItems) {
            message.append("Itens serão removidos em ");
        } else if (clearEntities) {
            message.append("Mobs serão removidos em ");
        }
        
        message.append("§7").append(seconds).append(" §c");
        message.append(seconds == 1 ? "segundo!" : "segundos!");
        
        return message.toString();
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
        List<Entity> entities = new ArrayList<>();
        world.getEntities().getAll().forEach(entities::add);
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

        // Se a configuração clear_passive_mobs estiver ativa, matar todos os mobs
        if (config.getAsJsonObject("auto_removal").get("clear_passive_mobs").getAsBoolean()) {
            return true;
        }

        // Caso contrário, manter o comportamento original
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

        List<Entity> entities = new ArrayList<>();
        world.getEntities().getAll().forEach(entities::add);
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
        StringBuilder feedback = new StringBuilder();
        feedback.append("§a§l[ClearLag] §f");
        
        if (clearItems && clearEntities) {
            feedback.append(String.format("Foram removidas §c%d §fentidades.", removedEntities));
        } else if (clearItems) {
            feedback.append(String.format("Foram removidos §c%d §fitens.", removedEntities));
        } else if (clearEntities) {
            feedback.append(String.format("Foram removidos §c%d §fmobs.", removedEntities));
        }
        
        source.sendSuccess(() -> Component.literal(feedback.toString()), true);
        
        for (Map.Entry<String, Integer> entry : entityCounts.entrySet()) {
            source.sendSuccess(() -> Component.literal(String.format("§a§l[ClearLag] §fRemovidos §c%d §fentidades do tipo §c%s.", entry.getValue(), entry.getKey())), true);
        }
        
        if (source.getLevel() instanceof ServerLevel) {
            ServerLevel world = (ServerLevel) source.getLevel();
            world.getPlayers(player -> true).forEach(player -> 
                player.sendSystemMessage(Component.literal(feedback.toString())));
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
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastGCTime < GC_COOLDOWN) {
            source.sendFailure(Component.literal("§cAguarde " + ((GC_COOLDOWN - (currentTime - lastGCTime)) / 1000) + " segundos antes de executar GC novamente."));
            return 0;
        }
        
        System.gc();
        lastGCTime = currentTime;
        long freeMemory = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        source.sendSuccess(() -> Component.literal(String.format(getMessage("free_memory"), freeMemory)), true);
        return 1;
    }

    private static int checkTPS(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        double tps = 1000.0 / Math.max(50, server.getAverageTickTime());
        tps = Math.min(20.0, tps);
        final double finalTps = tps;
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
        
        final double finalTps = tps;
        final long finalMaxMemory = maxMemory;
        final long finalAllocatedMemory = allocatedMemory;
        final long finalFreeMemory = freeMemory;
        
        source.sendSuccess(() -> Component.literal(String.format(getMessage("tps_message"), finalTps)), true);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("max_memory"), finalMaxMemory)), true);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("allocated_memory"), finalAllocatedMemory)), true);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("free_memory"), finalFreeMemory)), true);
        
        // Mostrar histórico de performance
        if (!performanceSamples.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§6§lHistórico de Performance:"), false);
            int samplesToShow = Math.min(5, performanceSamples.size());
            for (int i = performanceSamples.size() - samplesToShow; i < performanceSamples.size(); i++) {
                PerformanceSample sample = performanceSamples.get(i);
                source.sendSuccess(() -> Component.literal(String.format(
                    "§7- TPS: §c%.2f §7| Mem: §c%dMB §7| Ent: §c%d", 
                    sample.tps, sample.memoryUsed, sample.entitiesCount)), false);
            }
        }
        
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
    
        final int finalKilledMobs = killedMobs;
        source.sendSuccess(() -> Component.literal(String.format(getMessage("kill_mobs_message"), finalKilledMobs)), true);
        return killedMobs;
    }
    
    private static int clearArea(CommandSourceStack source, int radius) {
        ServerLevel world = source.getLevel();
        Entity player = source.getEntity();
        List<Entity> entities = new ArrayList<>();
        world.getEntities().getAll().forEach(entities::add);
        int removedEntities = 0;
        for (Entity entity : entities) {
            if (player.distanceToSqr(entity) <= radius * radius && shouldRemoveEntity(entity, true, true)) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                removedEntities++;
            }
        }
    
        final int finalRemovedEntities = removedEntities;
        final int finalRadius = radius;
        source.sendSuccess(() -> Component.literal(String.format(getMessage("clear_area_message"), finalRemovedEntities, finalRadius)), true);
        return removedEntities;
    }

    private static int forceGarbageCollection(CommandSourceStack source) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastGCTime < GC_COOLDOWN) {
            source.sendFailure(Component.literal("§cAguarde " + ((GC_COOLDOWN - (currentTime - lastGCTime)) / 1000) + " segundos antes de executar GC novamente."));
            return 0;
        }
        
        System.gc();
        lastGCTime = currentTime;
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
        List<Entity> entities = new ArrayList<>();
        world.getEntities().getAll().forEach(entities::add);
        int entityCount = entities.size();
        source.sendSuccess(() -> Component.literal(String.format(getMessage("world_info"), entityCount)), true);
        return entityCount;
    }

    private static int manageModules(CommandSourceStack source) {
        JsonObject modulesConfig = config.getAsJsonObject("modules");
        if (modulesConfig == null) {
            source.sendFailure(Component.literal(getMessage("no_modules_message")));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(getMessage("module_status_message")), true);
        for (Map.Entry<String, JsonElement> entry : modulesConfig.entrySet()) {
            String moduleName = entry.getKey();
            boolean enabled = entry.getValue().getAsBoolean();
            source.sendSuccess(() -> Component.literal(String.format(getMessage("module_entry_message"), moduleName, enabled ? "Enabled" : "Disabled")), true);
        }
        source.sendSuccess(() -> Component.literal(getMessage("toggle_module_message")), true);
        return 1;
    }
    
    private static int listModules(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§lMódulos do LagFixer:"), false);
        for (LagFixerModule module : modules.values()) {
            source.sendSuccess(() -> Component.literal(String.format(
                "§7- §e%s: §7%s §f- %s", 
                module.getName(), 
                module.isEnabled() ? "§aAtivado" : "§cDesativado",
                module.getDescription())), false);
        }
        return 1;
    }
    
    private static int toggleModule(CommandSourceStack source, String moduleName, boolean enable) {
        if (!modules.containsKey(moduleName)) {
            source.sendFailure(Component.literal("§cMódulo não encontrado: " + moduleName));
            return 0;
        }
        
        modules.get(moduleName).setEnabled(enable);
        
        // Atualizar configuração
        JsonObject modulesConfig = config.getAsJsonObject("modules");
        if (modulesConfig == null) {
            modulesConfig = new JsonObject();
            config.add("modules", modulesConfig);
        }
        modulesConfig.addProperty(moduleName, enable);
        saveConfig();
        
        source.sendSuccess(() -> Component.literal("§aMódulo " + moduleName + " " + (enable ? "ativado" : "desativado")), true);
        return 1;
    }

    private static int listTopEntitiesAndItems(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        Map<String, CoordinateData> coordinateMap = new HashMap<>();
    
        // Agrupar entidades e itens por coordenadas
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
    
        // Converter para lista e ordenar pelo total de entidades + itens
        List<CoordinateData> sortedCoordinates = new ArrayList<>(coordinateMap.values());
        sortedCoordinates.sort((a, b) -> {
            int totalA = a.entityCount + a.itemCount;
            int totalB = b.entityCount + b.itemCount;
            return Integer.compare(totalB, totalA); // Ordem decrescente
        });
    
        // Enviar cabeçalho
        source.sendSuccess(() -> Component.literal("§6§lTop 10 Coordenadas com mais entidades e itens:"), false);
    
        // Mostrar top 10 - Criando variáveis finais para uso no lambda
        for (int i = 0; i < Math.min(10, sortedCoordinates.size()); i++) {
            final int index = i + 1; // Variável final para o índice
            final CoordinateData data = sortedCoordinates.get(i); // Variável final para os dados
            
            source.sendSuccess(() -> Component.literal(String.format(
                "§e%d. §7X: §b%d §7Y: §b%d §7Z: §b%d §7- Entidades: §c%d §7- Itens: §c%d",
                index, data.x, data.y, data.z, data.entityCount, data.itemCount
            )), false);
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

        // Atualizar a configuração
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
            // Atualizar a configuração
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
    
    // Novos comandos de diagnóstico
    private static int showChunkStats(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        Map<String, Integer> chunkCounts = new HashMap<>();
        
        for (ServerLevel level : source.getServer().getAllLevels()) {
            String dimensionName = level.dimension().location().toString();
            int loadedChunks = level.getChunkSource().getLoadedChunksCount();
            chunkCounts.put(dimensionName, loadedChunks);
        }
        
        source.sendSuccess(() -> Component.literal(getMessage("chunk_stats_header")), false);
        for (Map.Entry<String, Integer> entry : chunkCounts.entrySet()) {
            source.sendSuccess(() -> Component.literal(String.format(
                getMessage("chunk_stats_entry"), entry.getKey(), entry.getValue())), false);
        }
        
        return 1;
    }
    
    private static int showEntityStats(CommandSourceStack source) {
        Map<String, Integer> entityStats = new HashMap<>();
        
        for (ServerLevel world : source.getServer().getAllLevels()) {
            for (Entity entity : world.getEntities().getAll()) {
                String entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                entityStats.put(entityType, entityStats.getOrDefault(entityType, 0) + 1);
            }
        }
        
        // Ordenar por quantidade (decrescente)
        List<Map.Entry<String, Integer>> sortedStats = new ArrayList<>(entityStats.entrySet());
        sortedStats.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        source.sendSuccess(() -> Component.literal(getMessage("entity_stats_header")), false);
        for (int i = 0; i < Math.min(10, sortedStats.size()); i++) {
            Map.Entry<String, Integer> entry = sortedStats.get(i);
            source.sendSuccess(() -> Component.literal(String.format(
                getMessage("entity_stats_entry"), entry.getKey(), entry.getValue())), false);
        }
        
        return 1;
    }
    
    private static int detectLagSpikes(CommandSourceStack source) {
        if (performanceSamples.size() < 10) {
            source.sendSuccess(() -> Component.literal("§aColetando dados de performance..."), false);
            return 0;
        }
        
        // Verificar se há lag spikes significativos (TPS < 15)
        boolean foundLagSpike = false;
        for (int i = Math.max(0, performanceSamples.size() - 10); i < performanceSamples.size(); i++) {
            PerformanceSample sample = performanceSamples.get(i);
            if (sample.tps < 15.0) {
                source.sendSuccess(() -> Component.literal(String.format(
                    getMessage("lag_spike_detected"), sample.tps)), false);
                foundLagSpike = true;
            }
        }
        
        if (!foundLagSpike) {
            source.sendSuccess(() -> Component.literal(getMessage("no_lag_spikes")), false);
        }
        
        return 1;
    }
    
    // Métodos para chunk loaders
    private static void startChunkLoaderScanning() {
        chunkLoaderScanner.scheduleAtFixedRate(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || server.isStopped()) return;
            
            try {
                scanForChunkLoaders(server);
            } catch (Exception e) {
                System.err.println("Erro ao escanear chunk loaders: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS); // Escanear a cada 30 segundos
    }

    private static void stopChunkLoaderScanning() {
        chunkLoaderScanner.shutdown();
        activeChunkLoaders.clear();
    }

    private static void scanForChunkLoaders(MinecraftServer server) {
        // Limpar lista anterior
        activeChunkLoaders.clear();
        
        for (ServerLevel world : server.getAllLevels()) {
            String dimensionName = world.dimension().location().toString();
            
            // 1. Verificar chunks forçados pelo vanilla (forceload)
            ForcedChunksSavedData forcedChunksData = world.getDataStorage().get(ForcedChunksSavedData::load, "chunks");
            if (forcedChunksData != null) {
                try {
                    // Acessar a lista de chunks forçados via reflection (pode variar entre versões)
                    CompoundTag nbt = forcedChunksData.save(new CompoundTag());
                    if (nbt.contains("ForcedChunks")) {
                        long[] forcedChunks = nbt.getLongArray("ForcedChunks");
                        for (long chunkPos : forcedChunks) {
                            ChunkLoaderInfo info = new ChunkLoaderInfo(chunkPos, dimensionName, "Forceload", null);
                            activeChunkLoaders.put(chunkPos, info);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao ler chunks forçados: " + e.getMessage());
                }
            }
            
            // 2. Verificar chunk loaders de mods (exemplo genérico)
            for (BlockEntity blockEntity : world.blockEntityList) {
                if (isChunkLoaderBlockEntity(blockEntity)) {
                    BlockPos pos = blockEntity.getBlockPos();
                    long chunkPos = LevelChunk.asLong(pos.getX() >> 4, pos.getZ() >> 4);
                    
                    String modName = "Unknown";
                    try {
                        modName = blockEntity.getClass().getSimpleName();
                    } catch (Exception e) {}
                    
                    ChunkLoaderInfo info = new ChunkLoaderInfo(chunkPos, dimensionName, "Mod: " + modName, pos);
                    activeChunkLoaders.put(chunkPos, info);
                }
            }
            
            // 3. Verificar entidades que mantêm chunks carregados
            for (Entity entity : world.getEntities().getAll()) {
                if (isEntityChunkLoader(entity)) {
                    BlockPos pos = entity.blockPosition();
                    long chunkPos = LevelChunk.asLong(pos.getX() >> 4, pos.getZ() >> 4);
                    
                    ChunkLoaderInfo info = new ChunkLoaderInfo(chunkPos, dimensionName, 
                        "Entity: " + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), pos);
                    activeChunkLoaders.put(chunkPos, info);
                }
            }
        }
    }

    // Método para detectar block entities que são chunk loaders
    private static boolean isChunkLoaderBlockEntity(BlockEntity blockEntity) {
        if (blockEntity == null) return false;
        
        String className = blockEntity.getClass().getName().toLowerCase();
        String blockName = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).toString().toLowerCase();
        
        // Verificar por nomes comuns de chunk loaders em mods
        return className.contains("chunk") && className.contains("loader") ||
               blockName.contains("chunk") && blockName.contains("loader") ||
               className.contains("anchor") || blockName.contains("anchor") ||
               className.contains("loader") || blockName.contains("loader");
    }

    // Método para detectar entidades que mantêm chunks carregados
    private static boolean isEntityChunkLoader(Entity entity) {
        if (entity == null) return false;
        
        // Algumas entidades podem manter chunks carregados, como alguns mobs de mods
        // ou entidades especiais
        return false; // Implementação específica depende dos mods instalados
    }

    // Método para remover os chunk loaders mais antigos
    private static void removeOldestChunkLoaders(int count) {
        List<ChunkLoaderInfo> sortedLoaders = new ArrayList<>(activeChunkLoaders.values());
        sortedLoaders.sort(Comparator.comparingLong(loader -> loader.loadTime));
        
        for (int i = 0; i < Math.min(count, sortedLoaders.size()); i++) {
            ChunkLoaderInfo loader = sortedLoaders.get(i);
            activeChunkLoaders.remove(loader.chunkPos);
            
            // Tentar desativar o chunk loader no mundo
            try {
                ServerLevel world = getWorldForDimension(loader.dimension);
                if (world != null) {
                    // Para chunks forçados do vanilla
                    if (loader.sourceType.equals("Forceload")) {
                        world.setChunkForced(loader.getChunkX(), loader.getChunkZ(), false);
                    }
                    // Para chunk loaders de mods (precisaria de implementação específica)
                    else if (loader.position != null) {
                        // Tentar quebrar o bloco ou desativar a entidade
                        world.destroyBlock(loader.position, true);
                    }
                }
            } catch (Exception e) {
                System.err.println("Erro ao remover chunk loader: " + e.getMessage());
            }
        }
    }

    // Método auxiliar para obter mundo por nome de dimensão
    private static ServerLevel getWorldForDimension(String dimensionName) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        
        for (ServerLevel world : server.getAllLevels()) {
            if (world.dimension().location().toString().equals(dimensionName)) {
                return world;
            }
        }
        return null;
    }

    // Métodos de comando para chunk loaders
    private static int listChunkLoaders(CommandSourceStack source) {
        if (activeChunkLoaders.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§aNenhum chunk loader ativo encontrado."), false);
            return 1;
        }
        
        source.sendSuccess(() -> Component.literal(getMessage("chunkloader_header")), false);
        
        int count = 1;
        for (ChunkLoaderInfo loader : activeChunkLoaders.values()) {
            String positionStr = loader.position != null ? 
                loader.position.getX() + ", " + loader.position.getY() + ", " + loader.position.getZ() : "N/A";
            
            source.sendSuccess(() -> Component.literal(String.format(
                getMessage("chunkloader_entry"), 
                count, loader.getChunkX(), loader.getChunkZ(), 
                loader.dimension, loader.sourceType, positionStr)), false);
            count++;
            
            // Limitar a exibição para não sobrecarregar o chat
            if (count > 50) {
                source.sendSuccess(() -> Component.literal("§7... e mais " + (activeChunkLoaders.size() - 50) + " chunk loaders"), false);
                break;
            }
        }
        
        return 1;
    }

    private static int clearAllChunkLoaders(CommandSourceStack source) {
        int removedCount = activeChunkLoaders.size();
        
        // Criar cópia para evitar ConcurrentModificationException
        List<ChunkLoaderInfo> loadersToRemove = new ArrayList<>(activeChunkLoaders.values());
        
        for (ChunkLoaderInfo loader : loadersToRemove) {
            try {
                ServerLevel world = getWorldForDimension(loader.dimension);
                if (world != null) {
                    if (loader.sourceType.equals("Forceload")) {
                        world.setChunkForced(loader.getChunkX(), loader.getChunkZ(), false);
                    } else if (loader.position != null) {
                        world.destroyBlock(loader.position, true);
                    }
                }
            } catch (Exception e) {
                System.err.println("Erro ao remover chunk loader: " + e.getMessage());
            }
        }
        
        activeChunkLoaders.clear();
        source.sendSuccess(() -> Component.literal(String.format(getMessage("chunkloader_cleared"), removedCount)), true);
        return removedCount;
    }

    private static int showChunkLoaderStats(CommandSourceStack source) {
        Map<String, Integer> statsByDimension = new HashMap<>();
        Map<String, Integer> statsByType = new HashMap<>();
        
        for (ChunkLoaderInfo loader : activeChunkLoaders.values()) {
            // Estatísticas por dimensão
            statsByDimension.put(loader.dimension, statsByDimension.getOrDefault(loader.dimension, 0) + 1);
            
            // Estatísticas por tipo
            String type = loader.sourceType.split(":")[0]; // Pegar apenas o tipo principal
            statsByType.put(type, statsByType.getOrDefault(type, 0) + 1);
        }
        
        source.sendSuccess(() -> Component.literal(getMessage("chunkloader_stats_header")), false);
        source.sendSuccess(() -> Component.literal(String.format(getMessage("chunkloader_stats_total"), activeChunkLoaders.size())), false);
        
        source.sendSuccess(() -> Component.literal("§7Por dimensão:"), false);
        for (Map.Entry<String, Integer> entry : statsByDimension.entrySet()) {
            source.sendSuccess(() -> Component.literal(String.format(getMessage("chunkloader_stats_dim"), entry.getKey(), entry.getValue())), false);
        }
        
        source.sendSuccess(() -> Component.literal("§7Por tipo:"), false);
        for (Map.Entry<String, Integer> entry : statsByType.entrySet()) {
            source.sendSuccess(() -> Component.literal(String.format(getMessage("chunkloader_stats_type"), entry.getKey(), entry.getValue())), false);
        }
        
        return 1;
    }
    
    // Método para teleportar para coordenadas específicas
    private static int teleportToCoordinates(CommandSourceStack source, int x, int y, int z) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(getMessage("player_only_command")));
            return 0;
        }
        
        try {
            // Encontrar um local seguro para teleportar (evitar bloquear dentro de blocos)
            ServerLevel world = (ServerLevel) player.level();
            BlockPos targetPos = new BlockPos(x, y, z);
            
            // Verificar se a posição é segura
            if (!isSafeLocation(world, targetPos)) {
                // Tentar encontrar uma posição segura próxima
                targetPos = findSafeLocation(world, targetPos);
                if (targetPos == null) {
                    source.sendFailure(Component.literal(String.format(getMessage("teleport_fail"), x, y, z)));
                    return 0;
                }
            }
            
            // Teleportar o jogador
            player.teleportTo(world, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 
                             player.getYRot(), player.getXRot());
            
            source.sendSuccess(() -> Component.literal(String.format(getMessage("teleport_success"), 
                targetPos.getX(), targetPos.getY(), targetPos.getZ())), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal(String.format(getMessage("teleport_fail"), x, y, z)));
            return 0;
        }
    }
    
    // Verificar se uma localização é segura para teleportar
    private static boolean isSafeLocation(ServerLevel world, BlockPos pos) {
        // Verificar se o bloco abaixo é sólido
        if (!world.getBlockState(pos.below()).isSolid()) {
            return false;
        }
        
        // Verificar se o local atual não é um bloco sólido
        if (world.getBlockState(pos).isSolid()) {
            return false;
        }
        
        // Verificar se o local acima não é um bloco sólido
        if (world.getBlockState(pos.above()).isSolid()) {
            return false;
        }
        
        return true;
    }
    
    // Encontrar uma localização segura próxima
    private static BlockPos findSafeLocation(ServerLevel world, BlockPos center) {
        // Procurar em um raio de 5 blocos
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = -5; y <= 5; y++) {
                    BlockPos testPos = center.offset(x, y, z);
                    if (isSafeLocation(world, testPos)) {
                        return testPos;
                    }
                }
            }
        }
        
        return null;
    }
    
    // Diagnóstico de lag
    private static int diagnoseLag(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        
        // 1. Verificar TPS
        double tps = 1000.0 / Math.max(50, server.getAverageTickTime());
        tps = Math.min(20.0, tps);
        
        source.sendSuccess(() -> Component.literal("§6§lDiagnóstico de Lag:"), false);
        source.sendSuccess(() -> Component.literal("§7TPS: " + (tps > 18 ? "§a" : tps > 15 ? "§e" : "§c") + String.format("%.2f", tps)), false);
        
        // 2. Verificar uso de memória
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        source.sendSuccess(() -> Component.literal("§7Memória: §c" + usedMemory + "MB§7/§c" + maxMemory + "MB"), false);
        
        // 3. Contar entidades totais
        long totalEntities = 0;
        Map<String, Integer> entitiesByType = new HashMap<>();
        
        for (ServerLevel world : server.getAllLevels()) {
            for (Entity entity : world.getEntities().getAll()) {
                totalEntities++;
                String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                entitiesByType.put(type, entitiesByType.getOrDefault(type, 0) + 1);
            }
        }
        
        source.sendSuccess(() -> Component.literal("§7Total de entidades: §c" + totalEntities), false);
        
        // 4. Mostrar as entidades mais comuns (possíveis causas de lag)
        if (!entitiesByType.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Top entidades (possíveis causas de lag):"), false);
            
            List<Map.Entry<String, Integer>> sortedEntities = new ArrayList<>(entitiesByType.entrySet());
            sortedEntities.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            
            for (int i = 0; i < Math.min(5, sortedEntities.size()); i++) {
                Map.Entry<String, Integer> entry = sortedEntities.get(i);
                source.sendSuccess(() -> Component.literal("§7- §e" + entry.getKey() + "§7: §c" + entry.getValue()), false);
            }
        }
        
        // 5. Verificar chunk loaders
        source.sendSuccess(() -> Component.literal("§7Chunk loaders ativos: §c" + activeChunkLoaders.size()), false);
        
        // 6. Verificar chunks carregados
        Map<String, Integer> chunksByDimension = new HashMap<>();
        for (ServerLevel world : server.getAllLevels()) {
            String dimName = world.dimension().location().toString();
            int chunkCount = world.getChunkSource().getLoadedChunksCount();
            chunksByDimension.put(dimName, chunkCount);
        }
        
        source.sendSuccess(() -> Component.literal("§7Chunks carregados:"), false);
        for (Map.Entry<String, Integer> entry : chunksByDimension.entrySet()) {
            source.sendSuccess(() -> Component.literal("§7- §e" + entry.getKey() + "§7: §c" + entry.getValue()), false);
        }
        
        // 7. Recomendações baseadas na análise
        source.sendSuccess(() -> Component.literal("§6§lRecomendações:"), false);
        
        if (tps < 15) {
            source.sendSuccess(() -> Component.literal("§c- Lag severo detectado! Considere:"), false);
            source.sendSuccess(() -> Component.literal("§c  - Limpar entidades com /Lagg clear"), false);
            source.sendSuccess(() -> Component.literal("§c  - Verificar chunk loaders com /Lagg chunkloaders list"), false);
            source.sendSuccess(() -> Component.literal("§c  - Reiniciar o servidor se necessário"), false);
        } else if (tps < 18) {
            source.sendSuccess(() -> Component.literal("§e- Lag moderado detectado. Considere:"), false);
            source.sendSuccess(() -> Component.literal("§e  - Monitorar entidades com /Lagg entitystats"), false);
            source.sendSuccess(() -> Component.literal("§e  - Verificar chunks carregados com /Lagg chunkstats"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§a- Desempenho bom. Continue monitorando."), false);
        }
        
        if (usedMemory > maxMemory * 0.8) {
            source.sendSuccess(() -> Component.literal("§c- Uso de memória alto! Considere:"), false);
            source.sendSuccess(() -> Component.literal("§c  - Limpar memória com /Lagg free"), false);
            source.sendSuccess(() -> Component.literal("§c  - Aumentar a memória alocada para o servidor"), false);
        }
        
        if (totalEntities > 5000) {
            source.sendSuccess(() -> Component.literal("§c- Muitas entidades! Considere:"), false);
            source.sendSuccess(() -> Component.literal("§c  - Limpar entidades com /Lagg clear"), false);
            source.sendSuccess(() -> Component.literal("§c  - Ajustar limites de spawn de mobs"), false);
        }
        
        return 1;
    }
    
    // Event handler para tick dos módulos
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null && !server.isStopped()) {
                for (ServerLevel world : server.getAllLevels()) {
                    for (LagFixerModule module : modules.values()) {
                        module.tick(world);
                    }
                }
            }
        }
    }
}
