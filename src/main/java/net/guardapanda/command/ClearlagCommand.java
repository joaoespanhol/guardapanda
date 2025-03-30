package net.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
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
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.function.Predicate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;

import java.io.IOException;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.File;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import com.google.common.collect.Lists;

@Mod.EventBusSubscriber
public class ClearlagCommand {
    private static final Map<String, Integer> entityCounts = new HashMap<>();
    private static boolean haltEnabled = false;
    private static JsonObject config;
    private static final Set<String> protectedEntities = new HashSet<>();
    private static final Set<String> protectedItems = new HashSet<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        loadConfig();

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
                    .executes(context -> clearArea(context.getSource(), IntegerArgumentType.getInteger(context, "radius"))))
            .then(Commands.literal("admin")
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
                .executes(context -> listTopEntitiesAndItems(context.getSource())))));

        scheduleAutoClear();
    }

    private static void loadConfig() {
        File configFile = new File("config/guardapanda/clearlag_config.json");
        if (!configFile.exists()) {
            generateDefaultConfig();
        }

        try (FileReader reader = new FileReader(configFile)) {
            config = JsonParser.parseReader(reader).getAsJsonObject();
            
            if (config.has("protected_entities")) {
                JsonArray protectedEntitiesArray = config.getAsJsonArray("protected_entities");
                protectedEntitiesArray.forEach(element -> protectedEntities.add(element.getAsString()));
            }
            
            if (config.has("protected_items")) {
                JsonArray protectedItemsArray = config.getAsJsonArray("protected_items");
                protectedItemsArray.forEach(element -> protectedItems.add(element.getAsString()));
            }
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
            messages.addProperty("warning_250s", "&4&l[ClearLag] &cAviso: Itens e mobs serão removidos em &7250 segundos!");
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
            defaultConfig.add("messages", messages);

            JsonObject autoRemoval = new JsonObject();
            autoRemoval.addProperty("enabled", true);
            autoRemoval.addProperty("interval", 300);
            defaultConfig.add("auto_removal", autoRemoval);

            JsonArray protectedEntitiesArray = new JsonArray();
            protectedEntitiesArray.add("minecraft:player");
            protectedEntitiesArray.add("minecraft:armor_stand");
            defaultConfig.add("protected_entities", protectedEntitiesArray);

            JsonArray protectedItemsArray = new JsonArray();
            protectedItemsArray.add("minecraft:diamond");
            protectedItemsArray.add("minecraft:nether_star");
            defaultConfig.add("protected_items", protectedItemsArray);

            try (FileWriter writer = new FileWriter(configFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(defaultConfig, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getMessage(String key) {
        String message = config.getAsJsonObject("messages").get(key).getAsString();
        return message.replace('&', '§');
    }

  

	private static void scheduleAutoClear() {
	    if (!config.getAsJsonObject("auto_removal").get("enabled").getAsBoolean()) {
	        return;
	    }
	
	    int interval = config.getAsJsonObject("auto_removal").get("interval").getAsInt();
	    scheduler.scheduleAtFixedRate(() -> {
	        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
	        if (server == null || server.isStopped()) return;
	
	        // Send initial warning with the configured interval
	        String initialMessage = getInitialWarningMessage(interval);
	        server.getPlayerList().broadcastSystemMessage(Component.literal(initialMessage), false);
	
	        // Schedule intermediate warnings
	        scheduleWarning(server, interval - 60, "warning_60s");
	        scheduleWarning(server, interval - 30, "warning_30s");
	        scheduleWarning(server, interval - 3, "warning_3s");
	        scheduleWarning(server, interval - 2, "warning_2s");
	        scheduleWarning(server, interval - 1, "warning_1s");
	
	        // Schedule the actual clearing
	        scheduler.schedule(() -> {
	            server.executeIfPossible(() -> {
	                if (server.isStopped()) return;
	                
	                int totalItems = 0;
	                int totalMobs = 0;
	                
	                for (ServerLevel world : server.getAllLevels()) {
	                    if (world != null && !world.isClientSide()) {
	                        totalItems += clearItems(world);
	                        totalMobs += killMobs(world);
	                    }
	                }
	                
	                String resultMsg = String.format(getMessage("cleared_entities"), totalItems, totalMobs);
	                server.getPlayerList().broadcastSystemMessage(Component.literal(resultMsg), false);
	            });
	        }, interval, TimeUnit.SECONDS);
	    }, interval, interval, TimeUnit.SECONDS);
	}
	
	private static String getInitialWarningMessage(int interval) {
	    return String.format("§4§l[ClearLag] §cAviso: Limpeza automática ocorrerá em §7%d §csegundos!", interval);
	}
	
	private static void scheduleWarning(MinecraftServer server, int delay, String messageKey) {
	    if (delay <= 0) return; // Skip if the delay has already passed
	    
	    scheduler.schedule(() -> {
	        server.executeIfPossible(() -> {
	            if (!server.isStopped()) {
	                String message = getMessage(messageKey);
	                server.getPlayerList().broadcastSystemMessage(Component.literal(message), false);
	            }
	        });
	    }, delay, TimeUnit.SECONDS);
	}


    private static void scheduleWarning(ServerLevel world, int time, String messageKey) {
        scheduler.schedule(() -> broadcastMessage(world, getMessage(messageKey)), time, TimeUnit.SECONDS);
    }


	private static void broadcastMessage(ServerLevel world, String message) {
	    if (world == null || world.isClientSide()) return;
	    
	    MinecraftServer server = world.getServer();
	    if (server == null || server.isStopped()) return;
	
	    // Converte a mensagem com cores e formatação
	    Component text = Component.literal(message.replace('&', '§'));
	    
	    // Usa o sistema de broadcast seguro do Minecraft
	    server.getPlayerList().broadcastSystemMessage(text, false);
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
        List<Entity> entities = Lists.newArrayList(world.getEntities().getAll());
        int killedMobs = 0;
        for (Entity entity : entities) {
            if (isNonPassiveMob(entity) && !isProtectedEntity(entity)) {
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

        return switch (mob.getType().getCategory()) {
            case CREATURE, AMBIENT, WATER_AMBIENT -> false;
            default -> true;
        };
    }

    private static int clearEntities(CommandSourceStack source) {
        ServerLevel world = source.getLevel();
        List<Entity> entities = new ArrayList<>();
        world.getEntities().getAll().forEach(entities::add);
        int removedEntities = 0;
        entityCounts.clear();
        for (Entity entity : entities) {
            if (shouldRemoveEntity(entity)) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                removedEntities++;
                String entityType = entity.getClass().getSimpleName();
                entityCounts.put(entityType, entityCounts.getOrDefault(entityType, 0) + 1);
            }
        }
        sendFeedback(source, removedEntities);
        return removedEntities;
    }

    private static boolean shouldRemoveEntity(Entity entity) {
        if (entity instanceof Player || entity instanceof ServerPlayer) {
            return false;
        }
        
        if (entity instanceof ItemEntity) {
            return !isProtectedItem((ItemEntity) entity);
        }
        
        if (entity instanceof LivingEntity) {
            return !isProtectedEntity(entity) && isNonPassiveMob(entity);
        }
        
        return entity instanceof ExperienceOrb || 
               entity instanceof PrimedTnt || 
               entity instanceof Projectile || 
               entity instanceof Boat || 
               entity instanceof Minecart || 
               entity instanceof Painting || 
               entity instanceof ItemFrame;
    }

    private static void sendFeedback(CommandSourceStack source, int removedEntities) {
        source.sendSuccess(() -> Component.literal(String.format(getMessage("feedback_message"), removedEntities)), true);
        for (Map.Entry<String, Integer> entry : entityCounts.entrySet()) {
            source.sendSuccess(() -> Component.literal(String.format(getMessage("entity_removed_message"), entry.getValue(), entry.getKey())), true);
        }
        if (source.getLevel() instanceof ServerLevel) {
            ServerLevel world = (ServerLevel) source.getLevel();
            world.getPlayers(player -> true).forEach(player -> 
                player.sendSystemMessage(Component.literal(String.format(getMessage("broadcast_message"), removedEntities))));
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
	    
	    final double finalTps = tps; // Create final copies
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
	    List<Entity> entities = Lists.newArrayList(world.getEntities().getAll());
	    int killedMobs = 0;
	
	    for (Entity entity : entities) {
	        if (isNonPassiveMob(entity) && !isProtectedEntity(entity)) {
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
	    List<Entity> entities = new ArrayList<>();
	    world.getEntities().getAll().forEach(entities::add);
	    int removedEntities = 0;
	    for (Entity entity : entities) {
	        if (player.distanceToSqr(entity) <= radius * radius && shouldRemoveEntity(entity)) {
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
        List<Entity> entities = new ArrayList<>();
        world.getEntities().getAll().forEach(entities::add);
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
        List<Entity> entities = new ArrayList<>();
        world.getEntities().getAll().forEach(entities::add);

        Map<String, Integer> entityCounts = new HashMap<>();
        Map<String, Integer> itemCounts = new HashMap<>();

        for (Entity entity : entities) {
            if (entity instanceof ItemEntity) {
                ItemStack itemStack = ((ItemEntity) entity).getItem();
                String itemName = itemStack.getItem().getDescription().getString();
                itemCounts.put(itemName, itemCounts.getOrDefault(itemName, 0) + itemStack.getCount());
            } else if (!(entity instanceof Player)) {
                String entityName = entity.getType().getDescription().getString();
                entityCounts.put(entityName, entityCounts.getOrDefault(entityName, 0) + 1);
            }
        }

        List<Map.Entry<String, Integer>> sortedEntities = new ArrayList<>(entityCounts.entrySet());
        sortedEntities.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));

        List<Map.Entry<String, Integer>> sortedItems = new ArrayList<>(itemCounts.entrySet());
        sortedItems.sort((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()));

        source.sendSuccess(() -> Component.literal(getMessage("top_coordinates_header")), true);
        for (int i = 0; i < Math.min(10, sortedEntities.size()); i++) {
            Map.Entry<String, Integer> entry = sortedEntities.get(i);
            source.sendSuccess(() -> Component.literal(String.format(getMessage("top_coordinates_entry"), entry.getKey(), entry.getValue())), true);
        }
        for (int i = 0; i < Math.min(10, sortedItems.size()); i++) {
            Map.Entry<String, Integer> entry = sortedItems.get(i);
            source.sendSuccess(() -> Component.literal(String.format(getMessage("top_coordinates_entry"), entry.getKey(), entry.getValue())), true);
        }

        return 1;
    }
}