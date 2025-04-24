package net.guardapanda.command;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.SimpleContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class LimititensCommand {
    private static final Map<String, Integer> globalItemLimits = new HashMap<>();
    private static final Map<UUID, Map<BlockPos, String>> placedBlocks = new HashMap<>();
    private static final File limitsFile = new File("config/guardapanda/global_limits.json");
    private static final File blocksFile = new File("config/guardapanda/placed_blocks.json");

    static {
        loadGlobalLimits();
        loadPlacedBlocks();
        MinecraftForge.EVENT_BUS.register(LimititensCommand.class);
    }

    public static Map<String, Integer> getGlobalItemLimits() {
        return globalItemLimits;
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(
            Commands.literal("limititens")
                .then(Commands.literal("set")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                        .executes(ctx -> setLimit(ctx))))
                .then(Commands.literal("remove")
                    .requires(source -> source.hasPermission(2))
                    .executes(ctx -> removeLimit(ctx)))
                .then(Commands.literal("list")
                    .executes(ctx -> listLimits(ctx)))
                .then(Commands.literal("gui")
                    .requires(source -> source.hasPermission(2))
                    .executes(ctx -> openGui(ctx)))
                .then(Commands.literal("check")
                    .executes(ctx -> checkLimit(ctx)))
        );
    }
    
    private static int setLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cSegure um item na mão principal!"));
            return 0;
        }
        
        int limit = IntegerArgumentType.getInteger(ctx, "quantidade");
        String itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString();
        globalItemLimits.put(itemId, limit);
        saveGlobalLimits();
        
        ctx.getSource().sendSuccess(() -> 
            Component.literal("§aLimite global definido: §e" + heldItem.getDisplayName().getString() + 
            " §f→ §b" + limit), true);
        return 1;
    }

    private static int removeLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cSegure um item na mão principal!"));
            return 0;
        }
        
        String itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString();
        if (globalItemLimits.remove(itemId) != null) {
            saveGlobalLimits();
            ctx.getSource().sendSuccess(() -> 
                Component.literal("§aLimite global removido: §e" + heldItem.getDisplayName().getString()), true);
            return 1;
        }
        
        ctx.getSource().sendFailure(Component.literal("§cNenhum limite encontrado para este item"));
        return 0;
    }

    private static int listLimits(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        
        if (globalItemLimits.isEmpty()) {
            ctx.getSource().sendSuccess(() -> 
                Component.literal("§eNão há limites globais definidos"), false);
            return 1;
        }
        
        ctx.getSource().sendSuccess(() -> 
            Component.literal("§6Limites globais de itens:"), false);
            
        globalItemLimits.forEach((id, limit) -> {
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));
            String name = stack.isEmpty() ? id : stack.getDisplayName().getString();
            player.sendSystemMessage(Component.literal("§7- §e" + name + " §f→ §b" + limit));
        });
        
        return 1;
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        
        if (globalItemLimits.isEmpty()) {
            player.sendSystemMessage(Component.literal("§eNão há limites globais definidos"));
            return 0;
        }
        
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("Limites Globais de Itens");
            }

            @Override
            public AbstractContainerMenu createMenu(int windowId, Inventory playerInventory, Player player) {
                return new ReadOnlyContainerMenu(windowId, playerInventory);
            }
        });
        return 1;
    }

    public static class ReadOnlyContainerMenu extends AbstractContainerMenu {
        private static final int SLOTS_PER_ROW = 9;
        private static final int VISIBLE_SLOT_ROWS = 5;
        private static final int ITEMS_PER_PAGE = SLOTS_PER_ROW * VISIBLE_SLOT_ROWS - 2; // Reserve 2 slots for arrows
        private int currentPage = 0;
        
        public ReadOnlyContainerMenu(int id, Inventory playerInventory) {
            super(MenuType.GENERIC_9x5, id);
            updateSlots();
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public int getMaxPages() {
            return Math.max(1, (int) Math.ceil((double) globalItemLimits.size() / ITEMS_PER_PAGE));
        }

        private void updateSlots() {
            this.slots.clear();
            
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(globalItemLimits.entrySet());
            int startIndex = currentPage * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, entries.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                Map.Entry<String, Integer> entry = entries.get(i);
                ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.getKey())));
                if (!stack.isEmpty()) {
                    int slotIndex = i - startIndex;
                    int row = slotIndex / (SLOTS_PER_ROW - 1);
                    int col = slotIndex % (SLOTS_PER_ROW - 1);
                    
                    if (row == VISIBLE_SLOT_ROWS - 1 && col >= SLOTS_PER_ROW - 3) {
                        col = SLOTS_PER_ROW - 3;
                    }
                    
                    this.addSlot(new ReadOnlySlot(stack, 8 + col * 18, 18 + row * 18));
                }
            }
            
            if (getMaxPages() > 1) {
                // Previous arrow slot (second to last slot)
                this.addSlot(new ArrowSlot(true, 8 + (SLOTS_PER_ROW - 2) * 18, 18 + (VISIBLE_SLOT_ROWS - 1) * 18));
                // Next arrow slot (last slot)
                this.addSlot(new ArrowSlot(false, 8 + (SLOTS_PER_ROW - 1) * 18, 18 + (VISIBLE_SLOT_ROWS - 1) * 18));
            }
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            if (slotId >= 0 && slotId < this.slots.size()) {
                Slot slot = this.slots.get(slotId);
                if (slot instanceof ArrowSlot) {
                    ArrowSlot arrowSlot = (ArrowSlot) slot;
                    if (arrowSlot.isPrevious) {
                        prevPage();
                    } else {
                        nextPage();
                    }
                    return;
                }
            }
            super.clicked(slotId, button, clickType, player);
        }

        public void nextPage() {
            if (currentPage < getMaxPages() - 1) {
                currentPage++;
                updateSlots();
                broadcastChanges();
            }
        }

        public void prevPage() {
            if (currentPage > 0) {
                currentPage--;
                updateSlots();
                broadcastChanges();
            }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        public static class ArrowSlot extends Slot {
            private final boolean isPrevious;
            
            public ArrowSlot(boolean isPrevious, int x, int y) {
                super(new SimpleContainer(1), 0, x, y);
                this.isPrevious = isPrevious;
                ((SimpleContainer)this.container).setItem(0, 
                    isPrevious ? new ItemStack(Items.ARROW) : new ItemStack(Items.SPECTRAL_ARROW));
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }
        }
    }

    public static class ReadOnlySlot extends Slot {
        private final ItemStack displayStack;

        public ReadOnlySlot(ItemStack stack, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
            this.displayStack = stack;
            ((SimpleContainer)this.container).setItem(0, stack.copy());
        }

        @Override
        public ItemStack getItem() {
            return displayStack.copy();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public void set(ItemStack stack) {
            // Prevent any changes
        }
    }

    private static int checkLimit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("§cSegure um item na mão principal!"));
            return 0;
        }
        
        String itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString();
        Integer limit = globalItemLimits.get(itemId);
        
        if (limit == null) {
            ctx.getSource().sendFailure(Component.literal("§cNenhum limite definido para este item"));
            return 0;
        }
        
        int inventoryCount = countItemsInInventory(player, itemId);
        int worldCount = 0;
        List<String> blockLocations = new ArrayList<>();
        
        if (heldItem.getItem() instanceof BlockItem) {
            worldCount = countBlocksInWorld(player, itemId);
            blockLocations = getBlockLocations(player, itemId);
        }
        
        player.sendSystemMessage(Component.literal("§6Informações do item:"));
        player.sendSystemMessage(Component.literal("§7- Item: §e" + heldItem.getDisplayName().getString()));
        player.sendSystemMessage(Component.literal("§7- Limite global: §b" + limit));
        
        if (heldItem.getItem() instanceof BlockItem) {
            player.sendSystemMessage(Component.literal("§7- Blocos colocados: §a" + worldCount + "§7/§b" + limit));
            if (!blockLocations.isEmpty()) {
                player.sendSystemMessage(Component.literal("§7- Localizações:"));
                for (String loc : blockLocations) {
                    player.sendSystemMessage(Component.literal("§7  - " + loc));
                }
            }
        } else {
            player.sendSystemMessage(Component.literal("§7- No inventário: §a" + inventoryCount + "§7/§b" + limit));
        }
        
        return 1;
    }

    private static List<String> getBlockLocations(Player player, String itemId) {
        List<String> locations = new ArrayList<>();
        if (!placedBlocks.containsKey(player.getUUID())) return locations;
        
        for (Map.Entry<BlockPos, String> entry : placedBlocks.get(player.getUUID()).entrySet()) {
            if (entry.getValue().equals(itemId)) {
                BlockPos pos = entry.getKey();
                locations.add(String.format("X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return locations;
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!checkItemLimit(event.getEntity(), event.getItem().getItem())) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public static void onItemCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!checkItemLimit(event.getEntity(), event.getCrafting())) {
            event.getEntity().getInventory().placeItemBackInInventory(event.getCrafting());
        }
    }
    
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!checkItemLimit(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            ItemStack stack = new ItemStack(event.getPlacedBlock().getBlock());
            String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            
            if (!globalItemLimits.containsKey(itemId)) return;
            
            if (!checkItemLimit(player, stack)) {
                event.setCanceled(true);
                return;
            }
            
            placedBlocks.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(event.getPos(), itemId);
            savePlacedBlocks();
        }
    }
    
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        BlockPos pos = event.getPos();
        
        for (Map.Entry<UUID, Map<BlockPos, String>> entry : placedBlocks.entrySet()) {
            if (entry.getValue().containsKey(pos)) {
                entry.getValue().remove(pos);
                savePlacedBlocks();
                break;
            }
        }
    }

    public static boolean checkItemLimit(Player player, ItemStack stack) {
        if (player == null || stack.isEmpty()) return true;
        
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        Integer limit = globalItemLimits.get(itemId);
        
        if (limit != null) {
            if (stack.getItem() instanceof BlockItem) {
                int worldCount = countBlocksInWorld(player, itemId);
                if (worldCount >= limit) {
                    player.displayClientMessage(
                        Component.literal("§cLimite global atingido: máximo de " + limit + " blocos colocados!"), true);
                    return false;
                }
            } else {
                int inventoryCount = countItemsInInventory(player, itemId);
                if (inventoryCount >= limit) {
                    player.displayClientMessage(
                        Component.literal("§cLimite global atingido: máximo de " + limit + " itens no inventário!"), true);
                    return false;
                }
            }
        }
        return true;
    }

    private static int countItemsInInventory(Player player, String itemId) {
        int count = 0;
        for (ItemStack invStack : player.getInventory().items) {
            if (!invStack.isEmpty() && 
                ForgeRegistries.ITEMS.getKey(invStack.getItem()).toString().equals(itemId)) {
                count += invStack.getCount();
            }
        }
        for (ItemStack armorStack : player.getInventory().armor) {
            if (!armorStack.isEmpty() && 
                ForgeRegistries.ITEMS.getKey(armorStack.getItem()).toString().equals(itemId)) {
                count += armorStack.getCount();
            }
        }
        ItemStack offhand = player.getInventory().offhand.get(0);
        if (!offhand.isEmpty() && 
            ForgeRegistries.ITEMS.getKey(offhand.getItem()).toString().equals(itemId)) {
            count += offhand.getCount();
        }
        return count;
    }

    private static int countBlocksInWorld(Player player, String itemId) {
        if (!placedBlocks.containsKey(player.getUUID())) return 0;
        
        int count = 0;
        for (String blockId : placedBlocks.get(player.getUUID()).values()) {
            if (blockId.equals(itemId)) {
                count++;
            }
        }
        return count;
    }

    private static void saveGlobalLimits() {
        try {
            if (!limitsFile.getParentFile().exists()) {
                limitsFile.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();
            globalItemLimits.forEach(root::addProperty);

            try (FileWriter writer = new FileWriter(limitsFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadGlobalLimits() {
        if (!limitsFile.exists()) return;

        try (FileReader reader = new FileReader(limitsFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.entrySet().forEach(entry -> 
                globalItemLimits.put(entry.getKey(), entry.getValue().getAsInt()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void savePlacedBlocks() {
        try {
            if (!blocksFile.getParentFile().exists()) {
                blocksFile.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();
            placedBlocks.forEach((uuid, blockMap) -> {
                JsonArray posArray = new JsonArray();
                blockMap.forEach((pos, itemId) -> {
                    JsonObject posObj = new JsonObject();
                    posObj.addProperty("x", pos.getX());
                    posObj.addProperty("y", pos.getY());
                    posObj.addProperty("z", pos.getZ());
                    posObj.addProperty("itemId", itemId);
                    posArray.add(posObj);
                });
                root.add(uuid.toString(), posArray);
            });

            try (FileWriter writer = new FileWriter(blocksFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadPlacedBlocks() {
        if (!blocksFile.exists()) return;

        try (FileReader reader = new FileReader(blocksFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.entrySet().forEach(entry -> {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonArray posArray = entry.getValue().getAsJsonArray();
                
                Map<BlockPos, String> blockMap = new HashMap<>();
                posArray.forEach(element -> {
                    JsonObject posObj = element.getAsJsonObject();
                    BlockPos pos = new BlockPos(
                        posObj.get("x").getAsInt(),
                        posObj.get("y").getAsInt(),
                        posObj.get("z").getAsInt()
                    );
                    String itemId = posObj.get("itemId").getAsString();
                    blockMap.put(pos, itemId);
                });
                
                placedBlocks.put(uuid, blockMap);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}