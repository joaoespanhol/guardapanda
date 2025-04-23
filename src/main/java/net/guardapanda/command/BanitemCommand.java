package net.guardapanda.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber
public class BanitemCommand {
    private static final Logger LOGGER = LogManager.getLogger("BanitemCommand");
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static class BanConfig {
        public Map<String, WorldBans> blacklist = new ConcurrentHashMap<>();
        public BanConfig() {
            blacklist.put("world", new WorldBans());
        }
    }

    private static class WorldBans {
        public Map<String, ItemBans> dimensions = new ConcurrentHashMap<>();
    }

    private static class ItemBans {
        public Map<String, FlagList> items = new ConcurrentHashMap<>();
    }

    private static class FlagList {
        public Map<String, Object> flags = new ConcurrentHashMap<>();
    }

    private static volatile BanConfig config = new BanConfig();
    private static volatile Map<String, Map<String, Long>> temporaryPermissions = new ConcurrentHashMap<>();

    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("guardapanda/banitems.json");
    private static final Path PERMISSIONS_PATH = FMLPaths.CONFIGDIR.get().resolve("guardapanda/banitems_permissions.json");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final List<String> ALL_FLAGS = Arrays.asList(
        "hold", "use", "craft", "wear", "own", "drop", "pickup", "place",
        "break", "attack", "interact", "consume", "enchant", "rename", "glide",
        "smith", "mend", "fill", "unfill", "dispense", "armorstand_place",
        "armorstand_take", "book_edit", "brew", "hanging_place", "sweeping_edge",
        "entity_drop", "inventory_click", "transfer", "delete", "open", "all"
    );

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        LOGGER.info("Loading BanitemCommand configuration...");
        loadAllConfigs();
        INITIALIZED.set(true);
    }

    private static void loadAllConfigs() {
        try {
            Future<?> loadTask = IO_EXECUTOR.submit(() -> {
                config = loadConfig(CONFIG_PATH, BanConfig.class, new BanConfig());
                Map<String, Map<String, Long>> loadedPermissions = loadConfig(PERMISSIONS_PATH,
                        new TypeToken<Map<String, Map<String, Long>>>(){}.getType(),
                        new HashMap<>());
                temporaryPermissions = loadedPermissions != null ? 
                    new ConcurrentHashMap<>(loadedPermissions) : 
                    new ConcurrentHashMap<>();
            });
            loadTask.get(10, TimeUnit.SECONDS);
            LOGGER.info("BanitemCommand loaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to load configs, using defaults", e);
            config = new BanConfig();
            temporaryPermissions = new ConcurrentHashMap<>();
        }
    }

    private static <T> T loadConfig(Path path, Type type, T defaultValue) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(defaultValue));
                return defaultValue;
            }
            String json = Files.readString(path);
            return json != null && !json.isEmpty() ? GSON.fromJson(json, type) : defaultValue;
        } catch (IOException e) {
            LOGGER.error("Error loading config from " + path, e);
            return defaultValue;
        }
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("banitems")
            .then(Commands.literal("add")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("flag", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        ALL_FLAGS.forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .then(Commands.argument("message", MessageArgument.message())
                        .executes(ctx -> banItemInHand(
                            ctx,
                            StringArgumentType.getString(ctx, "flag"),
                            MessageArgument.getMessage(ctx, "message").getString()
                        ))
                    )
                    .executes(ctx -> banItemInHand(
                        ctx,
                        StringArgumentType.getString(ctx, "flag"), 
                        null
                    ))
                )
            )
            .then(Commands.literal("remove")
                .requires(source -> source.hasPermission(2))
                .executes(BanitemCommand::removeItemInHand)
                .then(Commands.argument("item", StringArgumentType.string())
                    .executes(ctx -> removeBannedItem(
                        ctx, 
                        StringArgumentType.getString(ctx, "item")
                    ))
                )
            )
            .then(Commands.literal("list")
                .executes(BanitemCommand::listBannedItems)
            )
            .then(Commands.literal("flags")
                .requires(source -> source.hasPermission(2))
                .executes(BanitemCommand::listAllFlags)
            )
            .then(Commands.literal("reload")
                .requires(source -> source.hasPermission(2))
                .executes(BanitemCommand::reloadConfig)
            )
            .then(Commands.literal("perm")
                .requires(source -> source.hasPermission(2))
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
            .then(Commands.literal("clean")
                .requires(source -> source.hasPermission(2))
                .executes(BanitemCommand::cleanBannedItems)
            )
        );
    }

    private static int banItemInHand(CommandContext<CommandSourceStack> ctx, String flag, String message) throws CommandSyntaxException {
        Player player = ctx.getSource().getPlayerOrException();
        ItemStack itemInHand = player.getMainHandItem();
        
        if (itemInHand.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cVocê não está segurando nenhum item!"));
            return 0;
        }
        
        String itemId = ForgeRegistries.ITEMS.getKey(itemInHand.getItem()).toString();
        String worldName = player.level().dimension().location().toString();
        
        if (!ALL_FLAGS.contains(flag.toLowerCase()) && !flag.equalsIgnoreCase("all")) {
            ctx.getSource().sendFailure(Component.literal("§cFlag inválida! Use /banitems flags para ver todas."));
            return 0;
        }
        
        getOrCreateWorldBans(worldName).items
            .computeIfAbsent(itemId, k -> new FlagList())
            .flags.put(flag.toLowerCase(), createFlagData(message));
        
        saveConfigAsync();
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("§aItem §e%s §abanido com a flag §6%s§a%s",
                itemId,
                flag,
                message != null ? "! Mensagem: §f" + message : "!")
        ), true);
        
        return 1;
    }

    private static Map<String, Object> createFlagData(String message) {
        Map<String, Object> data = new HashMap<>();
        if (message != null && !message.isEmpty()) {
            data.put("message", message);
            data.put("timestamp", System.currentTimeMillis());
        }
        return data;
    }

    private static void saveConfigAsync() {
        IO_EXECUTOR.execute(() -> {
            try {
                Files.writeString(CONFIG_PATH, GSON.toJson(config));
            } catch (IOException e) {
                LOGGER.error("Falha ao salvar configuração", e);
            }
        });
    }

    private static int removeItemInHand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player player = ctx.getSource().getPlayerOrException();
        ItemStack itemInHand = player.getMainHandItem();
        
        if (itemInHand.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cVocê não está segurando nenhum item!"));
            return 0;
        }
        
        String itemId = ForgeRegistries.ITEMS.getKey(itemInHand.getItem()).toString();
        return removeBannedItem(ctx, itemId);
    }

    private static int removeBannedItem(CommandContext<CommandSourceStack> ctx, String itemId) {
        String worldName = ctx.getSource().getLevel().dimension().location().toString();
        ItemBans dimensionBans = config.blacklist.get("world").dimensions.get(worldName);
        
        if (dimensionBans == null || !dimensionBans.items.containsKey(itemId)) {
            ctx.getSource().sendFailure(Component.literal("§cItem §e" + itemId + " §cnão está banido!"));
            return 0;
        }
        
        dimensionBans.items.remove(itemId);
        saveConfigAsync();
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            "§aItem §e" + itemId + " §aremovido da lista de banidos no mundo §b" + worldName + "§a!"
        ), true);
        
        return 1;
    }

    private static int listBannedItems(CommandContext<CommandSourceStack> ctx) {
        String worldName = ctx.getSource().getLevel().dimension().location().toString();
        ItemBans dimensionBans = config.blacklist.get("world").dimensions.get(worldName);
        
        if (dimensionBans == null || dimensionBans.items.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§eNenhum item banido no mundo §b" + worldName + "§e."), false);
            return 1;
        }
        
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Itens Banidos no mundo §b" + worldName + "§6 ==="), false);
        
        dimensionBans.items.forEach((itemId, flags) -> {
            StringBuilder flagsText = new StringBuilder();
            flags.flags.forEach((flag, data) -> {
                flagsText.append("§e").append(flag);
                if (data instanceof Map) {
                    Object message = ((Map<?,?>)data).get("message");
                    Object timestamp = ((Map<?,?>)data).get("timestamp");
                    if (message != null) {
                        flagsText.append(" (§7").append(message).append("§e)");
                    }
                    if (timestamp instanceof Long) {
                        flagsText.append(" [§8").append(DATE_FORMAT.format(new Date((Long)timestamp))).append("§e]");
                    }
                }
                flagsText.append(", ");
            });
            
            if (flagsText.length() > 0) {
                flagsText.setLength(flagsText.length() - 2);
            }
            
            ctx.getSource().sendSuccess(() -> Component.literal(
                "§e- " + itemId + " §7(Flags: " + flagsText + "§7)"
            ), false);
        });
        
        return 1;
    }

    private static int listAllFlags(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("§6=== Flags Disponíveis ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§e" + String.join(", ", ALL_FLAGS)), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Use §a/banitems add <flag> [mensagem] §7para banir um item"), false);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        loadAllConfigs();
        ctx.getSource().sendSuccess(() -> Component.literal("§aConfiguração recarregada!"), true);
        return 1;
    }

    private static int cleanBannedItems(CommandContext<CommandSourceStack> ctx) {
        final int[] count = {0};
        for (WorldBans worldBans : config.blacklist.values()) {
            for (ItemBans dimensionBans : worldBans.dimensions.values()) {
                count[0] += dimensionBans.items.size();
                dimensionBans.items.clear();
            }
        }
        saveConfigAsync();
        ctx.getSource().sendSuccess(() -> Component.literal("§aTodos os itens banidos (" + count[0] + ") foram removidos!"), true);
        return 1;
    }

    private static int giveTemporaryPermission(CommandContext<CommandSourceStack> ctx, ServerPlayer player, String timeStr) 
            throws CommandSyntaxException {
        Player sender = ctx.getSource().getPlayerOrException();
        ItemStack itemInHand = sender.getMainHandItem();
        
        if (itemInHand.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cVocê não está segurando nenhum item!"));
            return 0;
        }
        
        String itemId = ForgeRegistries.ITEMS.getKey(itemInHand.getItem()).toString();
        
        if (!isItemBannedInAnyWorld(itemId)) {
            ctx.getSource().sendFailure(Component.literal("§cEste item não está banido em nenhum mundo!"));
            return 0;
        }
        
        long duration;
        try {
            duration = timeStr.equalsIgnoreCase("sempre") 
                ? Long.MAX_VALUE 
                : parseTime(timeStr);
        } catch (NumberFormatException e) {
            ctx.getSource().sendFailure(Component.literal("§cFormato de tempo inválido! Use: 1d, 2h, 30m ou 'sempre'"));
            return 0;
        }
        
        long expirationTime = System.currentTimeMillis() + duration;
        temporaryPermissions
            .computeIfAbsent(itemId, k -> new HashMap<>())
            .put(player.getGameProfile().getName(), expirationTime);
        
        savePermissionsAsync();
        
        ctx.getSource().sendSuccess(() -> Component.literal(
            String.format("§aPermissão concedida para §e%s §ausar §6%s §aaté: §e%s",
                player.getName().getString(),
                itemId,
                duration == Long.MAX_VALUE ? "SEMPRE" : formatTime(expirationTime))
        ), true);
        
        return 1;
    }

    private static long parseTime(String timeStr) {
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
        return DATE_FORMAT.format(new Date(timestamp));
    }

    private static boolean isItemBannedInAnyWorld(String itemId) {
        return config.blacklist.get("world").dimensions.values().stream()
            .anyMatch(dimension -> dimension.items.containsKey(itemId));
    }

    private static void savePermissionsAsync() {
        IO_EXECUTOR.execute(() -> {
            try {
                if (temporaryPermissions != null) {
                    Files.writeString(PERMISSIONS_PATH, GSON.toJson(temporaryPermissions));
                }
            } catch (IOException e) {
                LOGGER.error("Falha ao salvar permissões", e);
            }
        });
    }

    private static ItemBans getOrCreateWorldBans(String worldName) {
        return config.blacklist.get("world").dimensions
            .computeIfAbsent(worldName, k -> new ItemBans());
    }

    // ========== EVENT HANDLERS ==========
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            ItemStack stack = event.getPlacedBlock().getBlock().asItem().getDefaultInstance();
            checkAndCancel(player, stack, "place", event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        checkAndCancel(event.getEntity(), event.getItemStack(), "use", event);
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockState state = event.getLevel().getBlockState(event.getPos());
        ItemStack heldItem = event.getItemStack();
        
        String blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
        if (isBanned(player, new ItemStack(state.getBlock()), "interact")) {
            event.setCanceled(true);
            String message = getBanMessage(blockId, "interact", 
                player.level().dimension().location().toString());
            player.displayClientMessage(Component.literal(
                !message.isEmpty() ? message : "§cVocê não pode interagir com este bloco!"),
                true
            );
            return;
        }
        
        checkAndCancel(player, heldItem, "use", event);
        if (event.isCanceled()) return;
        
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && isBanned(player, stack, "open")) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("§cEste container contém itens banidos!"), true);
                    return;
                }
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        checkAndCancel(event.getEntity(), event.getItemStack(), "break", event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        checkAndCancel(event.getEntity(), event.getItemStack(), "attack", event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.isEmpty() && isBanned(player, mainHand, "attack")) {
                event.setCanceled(true);
                String message = getBanMessage(ForgeRegistries.ITEMS.getKey(mainHand.getItem()).toString(), 
                    "attack", player.level().dimension().location().toString());
                player.displayClientMessage(Component.literal(
                    !message.isEmpty() ? message : "§cVocê não pode atacar com este item!"),
                    true
                );
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof Player) {
            Player player = (Player) event.getSource().getEntity();
            checkAndCancel(player, player.getMainHandItem(), "attack", event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player) {
            Player player = (Player) event.getSource().getEntity();
            checkAndCancel(player, player.getMainHandItem(), "attack", event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        checkAndCancel(event.getEntity(), event.getItemStack(), "interact", event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAnvilUpdate(net.minecraftforge.event.AnvilUpdateEvent event) {
        if (!event.getLeft().isEmpty() && isBanned(null, event.getLeft(), "rename")) {
            event.setOutput(ItemStack.EMPTY);
            event.setCanceled(true);
            if (event.getPlayer() != null) {
                String message = getBanMessage(
                    ForgeRegistries.ITEMS.getKey(event.getLeft().getItem()).toString(), 
                    "rename",
                    event.getPlayer().level().dimension().location().toString()
                );
                event.getPlayer().displayClientMessage(
                    Component.literal(!message.isEmpty() ? message : "§cVocê não pode renomear este item!"),
                    true
                );
            }
        }
    }

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onAnvilResult(net.minecraftforge.event.entity.player.AnvilRepairEvent event) {
	    if (isBanned(event.getEntity(), event.getOutput(), "rename")) {
	        event.setCanceled(true);
	        String message = getBanMessage(
	            ForgeRegistries.ITEMS.getKey(event.getOutput().getItem()).toString(), 
	            "rename",
	            event.getEntity().level().dimension().location().toString()
	        );
	        event.getEntity().displayClientMessage(
	            Component.literal(!message.isEmpty() ? message : "§cVocê não pode renomear este item!"),
	            true
	        );
	    }
	}
	
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedStack = event.getEntity().getItem();
    
        if (isBanned(player, droppedStack, "drop")) {
            event.setCanceled(true);
            if (!player.getInventory().add(droppedStack)) {
                player.drop(droppedStack, false);
            }
            String message = getBanMessage(ForgeRegistries.ITEMS.getKey(droppedStack.getItem()).toString(), "drop", 
                player.level().dimension().location().toString());
            player.displayClientMessage(
                Component.literal(!message.isEmpty() ? message : "§cVocê não pode dropar este item!"), 
                true
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        checkAndCancel(event.getEntity(), event.getItem().getItem(), "pickup", event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemCrafted(net.minecraftforge.event.entity.player.PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        ItemStack result = event.getCrafting();

        if (isBanned(player, result, "craft")) {
            player.getInventory().removeItem(result);

            if (player.containerMenu != null) {
                for (Slot slot : player.containerMenu.slots) {
                    if (slot instanceof ResultSlot && slot.hasItem()) {
                        slot.set(ItemStack.EMPTY);
                        break;
                    }
                }
                player.containerMenu.setCarried(ItemStack.EMPTY);
            }

            for (int i = 0; i < event.getInventory().getContainerSize(); i++) {
                ItemStack ingredient = event.getInventory().getItem(i);
                if (!ingredient.isEmpty()) {
                    player.getInventory().add(ingredient.copy());
                }
            }

            String message = getBanMessage(ForgeRegistries.ITEMS.getKey(result.getItem()).toString(), "craft",
                player.level().dimension().location().toString());
            player.displayClientMessage(
                Component.literal(!message.isEmpty() ? message : "§cVocê não pode craftar este item!"),
                true
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSmithing(net.minecraftforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event) {
        checkAndCancel(event.getEntity(), event.getSmelting(), "smith", event);
    }
    
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemConsume(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().getItem().isEdible() || 
            event.getItemStack().getItem() instanceof net.minecraft.world.item.PotionItem ||
            event.getItemStack().getItem() instanceof net.minecraft.world.item.MilkBucketItem) {
            checkAndCancel(event.getEntity(), event.getItemStack(), "consume", event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBucketFill(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        
        if (stack.getItem() instanceof BucketItem bucket) {
            // Check for fill action (empty bucket)
            if (bucket.getFluid() == Fluids.EMPTY) {
                if (event.getLevel().getFluidState(event.getPos()).isSource() && 
                    isBanned(player, stack, "fill")) {
                    event.setCanceled(true);
                    String message = getBanMessage(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(), 
                        "fill", player.level().dimension().location().toString());
                    player.displayClientMessage(
                        Component.literal(!message.isEmpty() ? message : "§cVocê não pode encher este balde!"),
                        true
                    );
                    return;
                }
            }
            // Check for unfill action (full bucket)
            else if (isBanned(player, stack, "unfill")) {
                event.setCanceled(true);
                String message = getBanMessage(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(), 
                    "unfill", player.level().dimension().location().toString());
                player.displayClientMessage(
                    Component.literal(!message.isEmpty() ? message : "§cVocê não pode esvaziar este balde!"),
                    true
                );
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDispenserActivate(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().getBlock() instanceof net.minecraft.world.level.block.DispenserBlock) {
            Level level = (Level) event.getLevel();
            BlockPos pos = event.getPos();
            
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.DispenserBlockEntity dispenser) {
                for (int i = 0; i < dispenser.getContainerSize(); i++) {
                    ItemStack stack = dispenser.getItem(i);
                    if (!stack.isEmpty()) {
                        checkAndCancel(null, stack, "dispense", event);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onArmorStandPlace(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget().getType().toString().equals("armor_stand")) {
            checkAndCancel(event.getEntity(), event.getItemStack(), "armorstand_place", event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onArmorStandTake(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget().getType().toString().equals("armor_stand") && 
            event.getItemStack().isEmpty()) {
            checkAndCancel(event.getEntity(), event.getItemStack(), "armorstand_take", event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBookEdit(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof WritableBookItem || stack.getItem() instanceof WrittenBookItem) {
            if (isBanned(event.getEntity(), stack, "book_edit")) {
                event.setCanceled(true);
                event.getEntity().closeContainer();
                String message = getBanMessage(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(), 
                    "book_edit", event.getEntity().level().dimension().location().toString());
                event.getEntity().displayClientMessage(
                    Component.literal(!message.isEmpty() ? message : "§cVocê não pode editar este livro!"),
                    true
                );
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHangingPlace(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem().toString().contains("painting") || 
            event.getItemStack().getItem().toString().contains("item_frame")) {
            checkAndCancel(event.getEntity(), event.getItemStack(), "hanging_place", event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSweepingEdgeDamage(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player) {
            Player player = (Player) event.getSource().getEntity();
            ItemStack weapon = player.getMainHandItem();
            if (weapon.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE) > 0 &&
                isBanned(player, weapon, "sweeping_edge")) {
                event.setAmount(0);
                event.setCanceled(true);
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        event.getDrops().removeIf(itemEntity -> {
            ItemStack stack = itemEntity.getItem();
            return isBanned(null, stack, "entity_drop");
        });
    }

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onContainerClick(net.minecraftforge.event.entity.player.PlayerEvent event) {
	    if (event instanceof net.minecraftforge.event.entity.player.PlayerContainerEvent) {
	        net.minecraftforge.event.entity.player.PlayerContainerEvent containerEvent = 
	            (net.minecraftforge.event.entity.player.PlayerContainerEvent) event;
	        
	        // Check all slots in the container
	        if (containerEvent.getContainer() instanceof Container container) {
	            for (int i = 0; i < container.getContainerSize(); i++) {
	                ItemStack stack = container.getItem(i);
	                if (!stack.isEmpty() && isBanned(containerEvent.getEntity(), stack, "inventory_click")) {
	                    containerEvent.setCanceled(true);
	                    String message = getBanMessage(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(), 
	                        "inventory_click", containerEvent.getEntity().level().dimension().location().toString());
	                    containerEvent.getEntity().displayClientMessage(
	                        Component.literal(!message.isEmpty() ? message : "§cVocê não pode clicar neste item!"),
	                        true
	                    );
	                    return;
	                }
	            }
	        }
	    }
	}
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemTransfer(net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent event) {
        checkAndCancel(event.getEntity(), event.getStack(), "transfer", event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHopperTransfer(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().getBlock() instanceof net.minecraft.world.level.block.HopperBlock) {
            BlockPos pos = event.getPos();
            if (event.getLevel().getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hopper) {
                for (int i = 0; i < hopper.getContainerSize(); i++) {
                    ItemStack stack = hopper.getItem(i);
                    if (!stack.isEmpty() && isBanned(null, stack, "transfer")) {
                        hopper.setItem(i, ItemStack.EMPTY);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        AbstractContainerMenu menu = event.getContainer();
        
        if (menu instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty() && isBanned(player, stack, "open")) {
                    event.setCanceled(true);
                    player.closeContainer();
                    String message = getBanMessage(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(), 
                        "open", player.level().dimension().location().toString());
                    player.displayClientMessage(
                        Component.literal(!message.isEmpty() ? message : "§cEste container contém itens banidos!"),
                        true
                    );
                    return;
                }
            }
        }
        
        ItemStack heldItem = player.getMainHandItem();
        if (!heldItem.isEmpty() && isBanned(player, heldItem, "interact")) {
            event.setCanceled(true);
            player.closeContainer();
            String message = getBanMessage(ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString(), 
                "interact", player.level().dimension().location().toString());
            player.displayClientMessage(
                Component.literal(!message.isEmpty() ? message : "§cVocê não pode interagir com containers usando este item!"),
                true
            );
        }
    }

    // ========== UTILITY METHODS ==========
    private static void checkAndCancel(Player player, ItemStack item, String flag, Object event) {
        if (!INITIALIZED.get() || item.isEmpty() || (player != null && player.hasPermissions(2))) {
            return;
        }
        
        if (isBanned(player, item, "delete")) {
            if (event instanceof net.minecraftforge.eventbus.api.Event) {
                ((net.minecraftforge.eventbus.api.Event) event).setCanceled(true);
            }
            removeBannedItemCompletely(player, item);
            return;
        }
        
        if (isBanned(player, item, flag)) {
            if (event instanceof net.minecraftforge.eventbus.api.Event) {
                ((net.minecraftforge.eventbus.api.Event) event).setCanceled(true);
            }
            if (player != null) {
                String message = getBanMessage(ForgeRegistries.ITEMS.getKey(item.getItem()).toString(), flag, 
                    player.level().dimension().location().toString());
                player.displayClientMessage(Component.literal(
                    !message.isEmpty() ? message : "§cEsta ação está bloqueada para este item!"
                ), true);
            }
        }
    }

    private static boolean isBanned(Player player, ItemStack stack, String flag) {
        if (!INITIALIZED.get() || stack.isEmpty() || (player != null && player.hasPermissions(2))) {
            return false;
        }
        
        if (config == null || config.blacklist == null) {
            return false;
        }
        
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        String blockId = stack.getItem() instanceof BlockItem ? 
            ForgeRegistries.BLOCKS.getKey(((BlockItem)stack.getItem()).getBlock()).toString() : 
            null;
        
        String worldName = player != null ? player.level().dimension().location().toString() : "global";
        
        if (player != null && hasTemporaryPermission(player, itemId)) {
            return false;
        }
        
        if (isItemBannedInWorld(itemId, worldName, flag)) {
            return true;
        }
        
        if (blockId != null && isItemBannedInWorld(blockId, worldName, flag)) {
            return true;
        }
        
        return false;
    }
    
    private static boolean isItemBannedInWorld(String itemId, String worldName, String flag) {
        WorldBans worldBans = config.blacklist.get("world");
        if (worldBans == null) return false;
        
        ItemBans dimensionBans = worldBans.dimensions.get(worldName);
        if (dimensionBans != null && dimensionBans.items.containsKey(itemId)) {
            FlagList flags = dimensionBans.items.get(itemId);
            return flags != null && (flags.flags.containsKey("all") || flags.flags.containsKey(flag));
        }
        return false;
    }

    private static boolean hasTemporaryPermission(Player player, String itemId) {
        if (temporaryPermissions == null) {
            return false;
        }
        
        Map<String, Long> permissions = temporaryPermissions.get(itemId);
        if (permissions == null) {
            return false;
        }
        
        Long expiration = permissions.get(player.getGameProfile().getName());
        if (expiration == null) {
            return false;
        }
        
        if (expiration < System.currentTimeMillis() && expiration != Long.MAX_VALUE) {
            permissions.remove(player.getGameProfile().getName());
            return false;
        }
        
        return true;
    }

    private static String getBanMessage(String itemId, String flag, String worldName) {
        ItemBans worldBans = config.blacklist.get("world").dimensions.get(worldName);
        if (worldBans != null && worldBans.items.containsKey(itemId)) {
            FlagList flags = worldBans.items.get(itemId);
            Object flagData = flags.flags.getOrDefault(flag, flags.flags.get("all"));
            if (flagData instanceof Map) {
                Object message = ((Map<?,?>)flagData).get("message");
                if (message != null) {
                    return "§c" + message.toString();
                }
            }
        }
        
        ItemBans globalBans = config.blacklist.get("world").dimensions.get("global");
        if (globalBans != null && globalBans.items.containsKey(itemId)) {
            FlagList flags = globalBans.items.get(itemId);
            Object flagData = flags.flags.getOrDefault(flag, flags.flags.get("all"));
            if (flagData instanceof Map) {
                Object message = ((Map<?,?>)flagData).get("message");
                if (message != null) {
                    return "§c" + message.toString();
                }
            }
        }
        
        return "";
    }

    private static void handleBannedItem(Player player, ItemStack bannedItem, String flag) {
        removeBannedItemCompletely(player, bannedItem);
        String message = getBanMessage(ForgeRegistries.ITEMS.getKey(bannedItem.getItem()).toString(), flag, 
            player.level().dimension().location().toString());
        player.displayClientMessage(
            Component.literal(!message.isEmpty() ? message : "§cVocê não pode usar este item (" + flag + ")!"),
            true
        );
    }

    private static void removeBannedItemCompletely(Player player, ItemStack bannedItem) {
        Item itemToRemove = bannedItem.getItem();
        removeFromPlayerInventory(player, itemToRemove);
        
        if (player.containerMenu instanceof Container) {
            removeFromContainer((Container)player.containerMenu, itemToRemove);
        }
    }

    private static void removeFromPlayerInventory(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == item) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static void removeFromContainer(Container container, Item item) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).getItem() == item) {
                container.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}