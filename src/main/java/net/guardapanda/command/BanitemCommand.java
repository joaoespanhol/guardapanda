package net.guardapanda.command;


import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Container;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.TickEvent;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Arrays;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraft.commands.Commands;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Arrays;

@Mod.EventBusSubscriber
public class BanitemCommand {

    private static class BanConfig {
        public Map<String, WorldBans> blacklist = new LinkedHashMap<>();

        public BanConfig() {
            blacklist.put("world", new WorldBans());
        }
    }

    private static class WorldBans {
        public Map<String, ItemBans> dimensions = new LinkedHashMap<>();
    }

    private static class ItemBans {
        public Map<String, FlagList> items = new LinkedHashMap<>();
    }

    private static class FlagList {
        public Map<String, Object> flags = new LinkedHashMap<>();
    }

    private static BanConfig config = new BanConfig();
    private static final Map<String, Map<String, Long>> temporaryPermissions = new HashMap<>();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("guardapanda/banitem.json");
    private static final Path PERMISSIONS_PATH = FMLPaths.CONFIGDIR.get().resolve("guardapanda/banitem_permissions.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    
    private static final List<String> ALL_FLAGS = Arrays.asList(
        "hold", "use", "craft", "wear", "own", "drop", "pickup", "place",
        "break", "attack", "interact", "consume", "enchant", "rename", "glide",
        "smith", "mend", "fill", "unfill", "dispense", "armorstand_place",
        "armorstand_take", "book_edit", "brew", "hanging_place", "sweeping_edge",
        "entity_drop", "inventory_click", "transfer", "delete"
    );

    static {
        loadConfig();
        loadPermissions();
    }

    private static void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            saveConfig();
            return;
        }

        try {
            String json = Files.readString(CONFIG_PATH);
            config = GSON.fromJson(json, BanConfig.class);
            
            if (config == null) {
                config = new BanConfig();
            } else {
                if (config.blacklist == null) {
                    config.blacklist = new LinkedHashMap<>();
                }
                if (config.blacklist.get("world") == null) {
                    config.blacklist.put("world", new WorldBans());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            config = new BanConfig();
        }
    }

    private static void saveConfig() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String json = GSON.toJson(config);
            Files.writeString(CONFIG_PATH, json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadPermissions() {
        if (!Files.exists(PERMISSIONS_PATH)) {
            temporaryPermissions.clear();
            return;
        }

        try {
            String json = Files.readString(PERMISSIONS_PATH);
            Type type = new TypeToken<Map<String, Map<String, Long>>>(){}.getType();
            Map<String, Map<String, Long>> loaded = GSON.fromJson(json, type);
            
            temporaryPermissions.clear();
            if (loaded != null) {
                temporaryPermissions.putAll(loaded);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void savePermissions() {
        try {
            Files.createDirectories(PERMISSIONS_PATH.getParent());
            Files.writeString(PERMISSIONS_PATH, GSON.toJson(temporaryPermissions));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ItemBans getOrCreateWorldBans(String worldName) {
        WorldBans worldBans = config.blacklist.get("world");
        if (worldBans == null) {
            worldBans = new WorldBans();
            config.blacklist.put("world", worldBans);
        }
        return worldBans.dimensions.computeIfAbsent(worldName, k -> new ItemBans());
    }

    private static FlagList getOrCreateItemBans(ItemBans worldBans, String itemId) {
        return worldBans.items.computeIfAbsent(itemId, k -> new FlagList());
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("banitem")
                .requires(source -> source.hasPermission(2))
                
                .then(Commands.literal("add")
                    .then(Commands.argument("flag", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            builder.suggest("ALL");
                            ALL_FLAGS.forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String flag = StringArgumentType.getString(ctx, "flag");
                                String message = StringArgumentType.getString(ctx, "message");
                                if (message.startsWith("\"") && message.endsWith("\"")) {
                                    message = message.substring(1, message.length() - 1);
                                }
                                return banItemInHand(ctx, flag, message);
                            })
                        )
                        .executes(ctx -> banItemInHand(ctx, StringArgumentType.getString(ctx, "flag"), null))
                    )
                )
                .then(Commands.literal("remove")
                    .executes(ctx -> removeItemInHand(ctx))
                    .then(Commands.argument("item", StringArgumentType.string())
                        .executes(ctx -> removeBannedItem(ctx, StringArgumentType.getString(ctx, "item")))
                )
                .then(Commands.literal("list")
                    .executes(ctx -> listBannedItems(ctx))
                )
                .then(Commands.literal("flags")
                    .executes(ctx -> listAllFlags(ctx))
                )
                .then(Commands.literal("reload")
                    .executes(ctx -> reloadConfig(ctx))
                )
                .then(Commands.literal("perm")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("tempo", StringArgumentType.string())
                            .executes(ctx -> giveTemporaryPermission(
                                ctx,
                                EntityArgument.getPlayer(ctx, "player"),
                                StringArgumentType.getString(ctx, "tempo")
                            ))
                        )
                    )
                )
        ));
    }

    private static int banItemInHand(CommandContext<CommandSourceStack> ctx, String flag, String message) throws CommandSyntaxException {
        Player player = ctx.getSource().getPlayerOrException();
        ItemStack itemInHand = player.getMainHandItem();
        
        if (itemInHand.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cVocê não está segurando nenhum item!"));
            return 0;
        }
        
        String itemId = itemInHand.getItem().toString();
        String worldName = player.level().dimension().location().toString();
        String finalFlag = flag.toLowerCase();
        
        if (!ALL_FLAGS.contains(finalFlag) && !flag.equalsIgnoreCase("ALL")) {
            ctx.getSource().sendFailure(Component.literal("§cFlag inválida! Use /banitem flags para ver todas as flags disponíveis."));
            return 0;
        }
        
        ItemBans worldBans = getOrCreateWorldBans(worldName);
        FlagList itemFlags = getOrCreateItemBans(worldBans, itemId);
        
        Map<String, Object> flagData = new LinkedHashMap<>();
        if (message != null && !message.isEmpty()) {
            flagData.put("message", message);
        }
        
        if (flag.equalsIgnoreCase("ALL")) {
            for (String f : ALL_FLAGS) {
                if (!f.equals("delete")) {
                    itemFlags.flags.put(f, flagData);
                }
            }
            ctx.getSource().sendSuccess(() -> 
                Component.literal("§aItem §e" + itemId + " §abanido com §6TODAS§a as flags" + 
                    (message != null ? "§a! Mensagem: §f" + message : "§a!")), 
                true
            );
        } else {
            itemFlags.flags.put(finalFlag, flagData);
            ctx.getSource().sendSuccess(() -> 
                Component.literal("§aItem §e" + itemId + " §abanido com a flag §6" + finalFlag + 
                    (message != null ? "§a! Mensagem: §f" + message : "§a!")), 
                true
            );
        }
        
        saveConfig();
        return 1;
    }

    private static int giveTemporaryPermission(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String tempoStr) throws CommandSyntaxException {
        Player sender = ctx.getSource().getPlayerOrException();
        ItemStack itemInHand = sender.getMainHandItem();
        
        if (itemInHand.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cVocê não está segurando nenhum item!"));
            return 0;
        }
        
        String itemId = itemInHand.getItem().toString();
        
        if (!isItemBannedInAnyWorld(itemId)) {
            ctx.getSource().sendFailure(Component.literal("§cEste item não está banido em nenhum mundo!"));
            return 0;
        }
        
        long duration;
        try {
            if (tempoStr.equalsIgnoreCase("sempre")) {
                duration = Long.MAX_VALUE;
            } else {
                duration = parseTime(tempoStr);
            }
        } catch (NumberFormatException e) {
            ctx.getSource().sendFailure(Component.literal("§cFormato de tempo inválido! Use: 1d, 2h, 30m ou 'sempre'"));
            return 0;
        }
        
        long expirationTime = System.currentTimeMillis() + duration;
        
        temporaryPermissions
            .computeIfAbsent(itemId, k -> new HashMap<>())
            .put(player.getGameProfile().getName(), expirationTime);
        
        savePermissions();
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§aPermissão concedida para §e" + player.getName().getString() + 
            "§a usar §6" + itemId + 
            "§a até: §e" + (duration == Long.MAX_VALUE ? "SEMPRE" : formatTime(expirationTime))
        ), true);
        
        return 1;
    }

    private static boolean isItemBannedInAnyWorld(String itemId) {
        WorldBans worldBans = config.blacklist.get("world");
        if (worldBans == null) return false;
        
        for (ItemBans dimensionBans : worldBans.dimensions.values()) {
            if (dimensionBans.items.containsKey(itemId)) {
                return true;
            }
        }
        return false;
    }

    private static long parseTime(String timeStr) throws NumberFormatException {
        char unit = timeStr.charAt(timeStr.length() - 1);
        long number = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
        
        return switch (unit) {
            case 's' -> number * 1000;
            case 'm' -> number * 60 * 1000;
            case 'h' -> number * 60 * 60 * 1000;
            case 'd' -> number * 24 * 60 * 60 * 1000;
            default -> throw new NumberFormatException("Unidade de tempo inválida");
        };
    }

    private static String formatTime(long timestamp) {
        if (timestamp == Long.MAX_VALUE) return "SEMPRE";
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    private static int removeItemInHand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = ctx.getSource().getPlayerOrException();
        ItemStack itemInHand = player.getMainHandItem();
        
        if (itemInHand.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cVocê não está segurando nenhum item!"));
            return 0;
        }
        
        String itemId = itemInHand.getItem().toString();
        return removeBannedItem(ctx, itemId);
    }

    private static int removeBannedItem(CommandContext<CommandSourceStack> ctx, String itemId) {
        boolean removed = false;
        String worldName = ctx.getSource().getLevel().dimension().location().toString();
        
        WorldBans worldBans = config.blacklist.get("world");
        if (worldBans != null) {
            ItemBans dimensionBans = worldBans.dimensions.get(worldName);
            if (dimensionBans != null && dimensionBans.items.containsKey(itemId)) {
                dimensionBans.items.remove(itemId);
                removed = true;
            }
        }
        
        if (!removed) {
            ctx.getSource().sendFailure(Component.literal("§cItem §e" + itemId + " §cnão está banido neste mundo!"));
            return 0;
        }
        
        saveConfig();
        
        ctx.getSource().sendSuccess(() -> Component.literal("§aItem §e" + itemId + " §aremovido da lista de banidos no mundo §b" + worldName + "§a!"), true);
        return 1;
    }

    private static int listBannedItems(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getSource().getLevel().dimension().location().toString();
        
        WorldBans worldBans = config.blacklist.get("world");
        if (worldBans == null || worldBans.dimensions.get(worldName) == null || 
            worldBans.dimensions.get(worldName).items.isEmpty()) {
            
            ctx.getSource().sendSuccess(() -> Component.literal("§eNenhum item banido no mundo §b" + worldName + "§e."), false);
            return 1;
        }
        
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Itens Banidos no mundo §b" + worldName + "§6 ==="), false);
        worldBans.dimensions.get(worldName).items.forEach((itemId, flags) -> {
            StringBuilder flagsText = new StringBuilder();
            flags.flags.forEach((flag, data) -> {
                flagsText.append("§e").append(flag);
                if (data instanceof Map) {
                    Map<?, ?> dataMap = (Map<?, ?>) data;
                    if (dataMap.containsKey("message")) {
                        flagsText.append(" (§7").append(dataMap.get("message")).append("§e)");
                    }
                }
                flagsText.append(", ");
            });
            if (flagsText.length() > 0) {
                flagsText.setLength(flagsText.length() - 2);
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§e- " + itemId + " §7(Flags: " + flagsText.toString() + "§7)"), false);
        });
        
        return 1;
    }

    private static int listAllFlags(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Flags Disponíveis ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e" + String.join(", ", ALL_FLAGS)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Use §a/banitem add <flag> [mensagem] §7para banir um item"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Exemplo: §a/banitem add pickup \"Não pode pegar este item!\""), false);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        loadConfig();
        loadPermissions();
        ctx.getSource().sendSuccess(() -> Component.literal("§aConfiguração recarregada com sucesso!"), true);
        return 1;
    }

    private static boolean hasPermission(Player player, String itemId) {
        if (!temporaryPermissions.containsKey(itemId)) {
            return false;
        }
        
        Map<String, Long> itemPermissions = temporaryPermissions.get(itemId);
        String playerName = player.getGameProfile().getName();
        
        if (!itemPermissions.containsKey(playerName)) {
            return false;
        }
        
        long expirationTime = itemPermissions.get(playerName);
        
        if (expirationTime < System.currentTimeMillis() && expirationTime != Long.MAX_VALUE) {
            itemPermissions.remove(playerName);
            savePermissions();
            return false;
        }
        
        return true;
    }

    private static boolean isBanned(Player player, ItemStack stack, String flag) {
        if (stack.isEmpty() || (player != null && player.hasPermissions(2))) {
            return false;
        }
        
        String itemId = stack.getItem().toString();
        
        if (player != null && hasPermission(player, itemId)) {
            return false;
        }
        
        String worldName = player != null ? player.level().dimension().location().toString() : "global";
        
        WorldBans worldBans = config.blacklist.get("world");
        if (worldBans == null) return false;
        
        if (worldBans.dimensions.containsKey("global")) {
            FlagList globalFlags = worldBans.dimensions.get("global").items.get(itemId);
            if (globalFlags != null && (globalFlags.flags.containsKey(flag) || globalFlags.flags.containsKey("*"))) {
                return true;
            }
        }
        
        if (player != null) {
            ItemBans dimensionBans = worldBans.dimensions.get(worldName);
            if (dimensionBans != null) {
                FlagList itemFlags = dimensionBans.items.get(itemId);
                if (itemFlags != null && (itemFlags.flags.containsKey(flag) || itemFlags.flags.containsKey("*"))) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private static void checkAndCancel(Player player, ItemStack item, String flag, Object event) {
        if (item.isEmpty() || (player != null && player.hasPermissions(2))) {
            return;
        }
        
        if (isBanned(player, item, "delete")) {
            if (event instanceof net.minecraftforge.eventbus.api.Event) {
                ((net.minecraftforge.eventbus.api.Event) event).setCanceled(true);
            }
            removeBannedItemCompletely(player, item, event);
            return;
        }
        
        if (isBanned(player, item, flag)) {
            if (event instanceof net.minecraftforge.eventbus.api.Event) {
                ((net.minecraftforge.eventbus.api.Event) event).setCanceled(true);
            }
            if (player != null) {
                String message = getBanMessage(item.getItem().toString(), flag, 
                    player.level().dimension().location().toString());
                if (!message.isEmpty()) {
                    player.displayClientMessage(Component.literal(message), true);
                } else {
                    player.displayClientMessage(Component.literal("§cEsta ação está bloqueada para este item!"), true);
                }
            }
        }
    }

    private static String getBanMessage(String itemId, String flag, String worldName) {
        WorldBans worldBans = config.blacklist.get("world");
        if (worldBans == null) return "";
        
        if (worldBans.dimensions.containsKey(worldName)) {
            ItemBans dimensionBans = worldBans.dimensions.get(worldName);
            if (dimensionBans != null && dimensionBans.items.containsKey(itemId)) {
                FlagList itemFlags = dimensionBans.items.get(itemId);
                if (itemFlags != null) {
                    Object flagData = itemFlags.flags.get(flag);
                    if (flagData instanceof Map) {
                        Map<?, ?> dataMap = (Map<?, ?>) flagData;
                        if (dataMap.containsKey("message")) {
                            return "§c" + dataMap.get("message").toString();
                        }
                    }
                }
            }
        }
        
        if (worldBans.dimensions.containsKey("global")) {
            FlagList globalFlags = worldBans.dimensions.get("global").items.get(itemId);
            if (globalFlags != null) {
                Object flagData = globalFlags.flags.get(flag);
                if (flagData instanceof Map) {
                    Map<?, ?> dataMap = (Map<?, ?>) flagData;
                    if (dataMap.containsKey("message")) {
                        return "§c" + dataMap.get("message").toString();
                    }
                }
            }
        }
        
        return "";
    }

    private static void removeBannedItemCompletely(Player player, ItemStack bannedItem, Object event) {
        Item itemToRemove = bannedItem.getItem();
        
        if (player != null) {
            removeFromPlayerInventory(player, itemToRemove);
            player.displayClientMessage(Component.literal("§cEste item foi removido do jogo!"), true);
        }
        
        if (event instanceof BlockEvent) {
            BlockEvent blockEvent = (BlockEvent) event;
            Level level = (Level) blockEvent.getLevel();
            BlockPos pos = blockEvent.getPos();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            
            if (blockEntity instanceof Container container) {
                removeFromContainer(container, itemToRemove);
            }
        }
        
        if (event instanceof ItemTossEvent) {
            ItemEntity itemEntity = ((ItemTossEvent) event).getEntity();
            itemEntity.discard();
        }
    }
    
    private static void removeFromPlayerInventory(Player player, Item itemToRemove) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == itemToRemove) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack handStack = player.getItemInHand(hand);
            if (handStack.getItem() == itemToRemove) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
        }
    }
    
    private static void removeFromContainer(Container container, Item itemToRemove) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() == itemToRemove) {
                container.setItem(i, ItemStack.EMPTY);
            }
        }
    }
    
    private static void handleBannedItem(Player player, ItemStack bannedItem, String flag) {
        removeBannedItemCompletely(player, bannedItem, null);
        String message = getBanMessage(bannedItem.getItem().toString(), flag, 
            player.level().dimension().location().toString());
        player.displayClientMessage(
            Component.literal(!message.isEmpty() ? message : "§cVocê não pode usar este item (" + flag + ")!"),
            true
        );
    }

    // ========== EVENT HANDLERS ==========
    
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        checkAndCancel(event.getEntity(), event.getItemStack(), "use", event);
    }
    
@SubscribeEvent
public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
    // Remove a verificação do crafting table aqui
    // Deixa o jogador abrir a mesa normalmente
    checkAndCancel(event.getEntity(), event.getItemStack(), "place", event);
}

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        checkAndCancel(event.getEntity(), event.getItemStack(), "break", event);
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player) {
            Player player = (Player) event.getSource().getEntity();
            checkAndCancel(player, player.getMainHandItem(), "attack", event);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        checkAndCancel(event.getEntity(), event.getItemStack(), "interact", event);
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        checkAndCancel(event.getEntity(), event.getOutput(), "rename", event);
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedStack = event.getEntity().getItem();
    
        if (isBanned(player, droppedStack, "drop")) {
            event.setCanceled(true);
            if (!player.getInventory().add(droppedStack)) {
                player.drop(droppedStack, false);
            }
            String message = getBanMessage(droppedStack.getItem().toString(), "drop", 
                player.level().dimension().location().toString());
            player.displayClientMessage(
                Component.literal(!message.isEmpty() ? message : "§cVocê não pode dropar este item!"), 
                true
            );
        }
    }

    @SubscribeEvent
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        checkAndCancel(event.getEntity(), event.getItem().getItem(), "pickup", event);
    }





@SubscribeEvent
public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
    Player player = event.getEntity();
    ItemStack result = event.getCrafting();

    if (isBanned(player, result, "craft")) {
        // Remove do inventário (caso tenha ido via shift + click)
        player.getInventory().removeItem(result);

        // Tenta limpar o slot de resultado
        if (player.containerMenu != null) {
            for (Slot slot : player.containerMenu.slots) {
                if (slot instanceof ResultSlot && slot.hasItem()) {
                    slot.set(ItemStack.EMPTY);
                    break;
                }
            }

            // Limpa o item do cursor (normal click)
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        // Devolve os ingredientes
        for (int i = 0; i < event.getInventory().getContainerSize(); i++) {
            ItemStack ingredient = event.getInventory().getItem(i);
            if (!ingredient.isEmpty()) {
                player.getInventory().add(ingredient.copy());
            }
        }

        // Mensagem de erro
        String message = getBanMessage(result.getItem().toString(), "craft",
            player.level().dimension().location().toString());
        player.displayClientMessage(
            Component.literal(!message.isEmpty() ? message : "§cVocê não pode craftar este item!"),
            true
        );
    }
}









// Método adicional para prevenir transferência via shift+click
@SubscribeEvent
public static void onInventoryClick(PlayerEvent.ItemPickupEvent event) {
    if (isBanned(event.getEntity(), event.getStack(), "craft")) {
        // Simplesmente não fazemos nada - o item não será adicionado ao inventário
        event.getEntity().displayClientMessage(
            Component.literal("§cItem banido detectado!"),
            true
        );
    }
}






    @SubscribeEvent
    public static void onSmithing(ItemSmeltedEvent event) {
        checkAndCancel(event.getEntity(), event.getSmelting(), "smith", event);
    }
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            for (Player player : event.getServer().getPlayerList().getPlayers()) {
                ItemStack mainHand = player.getMainHandItem();
                if (!mainHand.isEmpty() && isBanned(player, mainHand, "hold")) {
                    handleBannedItem(player, mainHand, "hold");
                }
                
                for (ItemStack armor : player.getInventory().armor) {
                    if (!armor.isEmpty() && isBanned(player, armor, "wear")) {
                        handleBannedItem(player, armor, "wear");
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBucketFill(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack bucket = event.getItemStack();
        
        if (bucket.getItem() instanceof BucketItem && 
            !(bucket.getItem() instanceof MilkBucketItem) &&
            event.getLevel().getFluidState(event.getPos()).isSource()) {
            checkAndCancel(player, bucket, "fill", event);
        }
    }

    @SubscribeEvent
    public static void onBucketInteraction(PlayerInteractEvent.RightClickBlock event) {
        ItemStack item = event.getItemStack();
        Player player = event.getEntity();
        
        if (item.getItem() instanceof BucketItem && !(item.getItem() instanceof MilkBucketItem)) {
            if (event.getLevel().getFluidState(event.getPos()).isSource()) {
                checkAndCancel(player, item, "unfill", event);
            }
        }
    }

    @SubscribeEvent
    public static void onDispenserActivate(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().getBlock() instanceof DispenserBlock) {
            LevelAccessor level = event.getLevel();
            BlockPos pos = event.getPos();
            
            if (level.getBlockEntity(pos) instanceof DispenserBlockEntity dispenser) {
                for (int i = 0; i < dispenser.getContainerSize(); i++) {
                    ItemStack stack = dispenser.getItem(i);
                    if (!stack.isEmpty()) {
                        checkAndCancel(null, stack, "dispense", event);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onArmorStandPlace(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget().getType().toString().equals("armor_stand")) {
            checkAndCancel(event.getEntity(), event.getItemStack(), "armorstand_place", event);
        }
    }

    @SubscribeEvent
    public static void onArmorStandTake(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget().getType().toString().equals("armor_stand") && 
            event.getItemStack().isEmpty()) {
            checkAndCancel(event.getEntity(), event.getItemStack(), "armorstand_take", event);
        }
    }

    @SubscribeEvent
    public static void onBookEdit(PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().getItem().toString().contains("writable_book") || 
            event.getItemStack().getItem().toString().contains("written_book")) {
            checkAndCancel(event.getEntity(), event.getItemStack(), "book_edit", event);
        }
    }

    @SubscribeEvent
    public static void onHangingPlace(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem().toString().contains("painting") || 
            event.getItemStack().getItem().toString().contains("item_frame")) {
            checkAndCancel(event.getEntity(), event.getItemStack(), "hanging_place", event);
        }
    }

    @SubscribeEvent
    public static void onSweepingEdge(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player) {
            Player player = (Player) event.getSource().getEntity();
            ItemStack weapon = player.getMainHandItem();
            if (weapon.getEnchantmentTags().toString().contains("sweeping")) {
                checkAndCancel(player, weapon, "sweeping_edge", event);
            }
        }
    }
    
    @SubscribeEvent
    public static void onEntityDrops(LivingDropsEvent event) {
        event.getDrops().removeIf(itemEntity -> {
            ItemStack stack = itemEntity.getItem();
            return isBanned(null, stack, "entity_drop");
        });
    }

    @SubscribeEvent
    public static void onPlayerInteractWithContainer(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockState(event.getPos()).hasBlockEntity()) {
            ItemStack heldItem = event.getItemStack();
            if (!heldItem.isEmpty()) {
                checkAndCancel(event.getEntity(), heldItem, "inventory_click", event);
            }
        }
    }
    
    @SubscribeEvent
    public static void onItemTransfer(PlayerEvent.ItemPickupEvent event) {
        checkAndCancel(event.getEntity(), event.getStack(), "transfer", event);
    }

    @SubscribeEvent
    public static void onHopperTransfer(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().getBlock() instanceof HopperBlock) {
            BlockPos pos = event.getPos();
            if (event.getLevel().getBlockEntity(pos) instanceof HopperBlockEntity hopper) {
                for (int i = 0; i < hopper.getContainerSize(); i++) {
                    ItemStack stack = hopper.getItem(i);
                    if (!stack.isEmpty()) {
                        checkAndCancel(null, stack, "transfer", event);
                    }
                }
            }
        }
    }
}