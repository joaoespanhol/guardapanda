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
    private static final Map<String, Integer> entityCounts = new HashMap<>();
    private static boolean haltEnabled = false;
    private static JsonObject config;
    private static final Set<String> protectedEntities = new HashSet<>();
    private static final Set<String> protectedItems = new HashSet<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

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
                    .executes(context -> clearArea(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))))
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
                .executes(context -> listTopEntitiesAndItems(context.getSource())))
            .then(Commands.literal("whitelist")
                .then(Commands.literal("add")
                    .executes(context -> addHeldItemToWhitelist(context.getSource())))
                .then(Commands.literal("remove")
                    .executes(context -> removeHeldItemFromWhitelist(context.getSource())))
                .then(Commands.literal("list")
                    .executes(context -> listWhitelistedItems(context.getSource())))));

        scheduleAutoClear();
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
        System.gc();
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
}