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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.util.*;

@Mod.EventBusSubscriber
public class LimititensCommand {

    private static final Map<UUID, Map<String, Integer>> playerLimits = new HashMap<>();
    private static final Map<UUID, Map<BlockPos, String>> placedBlocks = new HashMap<>();
    private static final File saveFile = new File("config/guardapanda/limits.json");
    private static final File blocksFile = new File("config/guardapanda/placed_blocks.json");

    static {
        loadLimits();
        loadPlacedBlocks();
        MinecraftForge.EVENT_BUS.register(LimititensCommand.class);
    }

    public static Map<UUID, Map<String, Integer>> getPlayerLimits() {
        return playerLimits;
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        
        dispatcher.register(
            Commands.literal("limititens")
                .then(Commands.literal("set")
                    .then(Commands.argument("quantidade", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            try {
                                return setLimit(ctx);
                            } catch (CommandSyntaxException e) {
                                ctx.getSource().sendFailure(Component.literal("Erro ao executar comando"));
                                return 0;
                            }
                        })))
                .then(Commands.literal("remove")
                    .executes(ctx -> {
                        try {
                            return removeLimit(ctx);
                        } catch (CommandSyntaxException e) {
                            ctx.getSource().sendFailure(Component.literal("Erro ao executar comando"));
                            return 0;
                        }
                    }))
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        try {
                            return listLimits(ctx);
                        } catch (CommandSyntaxException e) {
                            ctx.getSource().sendFailure(Component.literal("Erro ao executar comando"));
                            return 0;
                        }
                    }))
                .then(Commands.literal("gui")
                    .executes(ctx -> {
                        try {
                            return openGui(ctx);
                        } catch (CommandSyntaxException e) {
                            ctx.getSource().sendFailure(Component.literal("Erro ao executar comando"));
                            return 0;
                        }
                    }))
                .then(Commands.literal("check")
                    .executes(ctx -> {
                        try {
                            return checkLimit(ctx);
                        } catch (CommandSyntaxException e) {
                            ctx.getSource().sendFailure(Component.literal("Erro ao executar comando"));
                            return 0;
                        }
                    }))
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
        
        playerLimits.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
            .put(itemId, limit);
        saveLimits();
        
        ctx.getSource().sendSuccess(() -> 
            Component.literal("§aLimite definido: §e" + heldItem.getDisplayName().getString() + 
            " §f→ §b" + limit), false);
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
        
        if (playerLimits.getOrDefault(player.getUUID(), Collections.emptyMap())
            .remove(itemId) != null) {
            saveLimits();
            ctx.getSource().sendSuccess(() -> 
                Component.literal("§aLimite removido: §e" + heldItem.getDisplayName().getString()), false);
        } else {
            ctx.getSource().sendFailure(Component.literal("§cNenhum limite encontrado para este item"));
        }
        return 1;
    }

    private static int listLimits(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Map<String, Integer> limits = playerLimits.getOrDefault(player.getUUID(), new HashMap<>());
        
        if (limits.isEmpty()) {
            ctx.getSource().sendSuccess(() -> 
                Component.literal("§eVocê não tem limites definidos"), false);
        } else {
            ctx.getSource().sendSuccess(() -> 
                Component.literal("§6Seus limites de itens:"), false);
            limits.forEach((id, limit) -> {
                ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));
                String name = stack.isEmpty() ? id : stack.getDisplayName().getString();
                player.sendSystemMessage(Component.literal("§7- §e" + name + " §f→ §b" + limit));
            });
        }
        return 1;
    }

    private static int openGui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            openClientGui(player);
        } else {
            Map<String, Integer> limits = playerLimits.getOrDefault(player.getUUID(), new HashMap<>());
            
            if (limits.isEmpty()) {
                player.sendSystemMessage(Component.literal("§eVocê não tem limites definidos"));
            } else {
                player.sendSystemMessage(Component.literal("§6Seus limites de itens:"));
                limits.forEach((id, limit) -> {
                    ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));
                    String name = stack.isEmpty() ? id : stack.getDisplayName().getString();
                    player.sendSystemMessage(Component.literal("§7- §e" + name + " §f→ §b" + limit));
                });
            }
        }
        return 1;
    }

    @OnlyIn(Dist.CLIENT)
    private static void openClientGui(Player player) {
        try {
            Class<?> clientClass = Class.forName("net.guardapanda.command.LimititensCommandClient");
            clientClass.getMethod("openGui", Player.class).invoke(null, player);
        } catch (Exception e) {
            e.printStackTrace();
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
        Integer limit = playerLimits.getOrDefault(player.getUUID(), Collections.emptyMap()).get(itemId);
        
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
        player.sendSystemMessage(Component.literal("§7- Limite: §b" + limit));
        
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
        if (player != null && placedBlocks.containsKey(player.getUUID())) {
            placedBlocks.get(player.getUUID()).remove(event.getPos());
            savePlacedBlocks();
        }
    }

    public static boolean checkItemLimit(Player player, ItemStack stack) {
        if (player == null || stack.isEmpty()) return true;
        
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        Integer limit = playerLimits.getOrDefault(player.getUUID(), Collections.emptyMap())
            .get(itemId);
        
        if (limit != null) {
            if (stack.getItem() instanceof BlockItem) {
                int worldCount = countBlocksInWorld(player, itemId);
                if (worldCount >= limit) {
                    player.displayClientMessage(
                        Component.literal("§cVocê atingiu o limite máximo de " + limit + " blocos colocados!"), true);
                    return false;
                }
            } else {
                int inventoryCount = countItemsInInventory(player, itemId);
                if (inventoryCount >= limit) {
                    player.displayClientMessage(
                        Component.literal("§cVocê atingiu o limite máximo de " + limit + " itens no inventário!"), true);
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

    private static void saveLimits() {
        try {
            if (!saveFile.getParentFile().exists()) {
                saveFile.getParentFile().mkdirs();
            }

            JsonObject root = new JsonObject();
            playerLimits.forEach((uuid, limits) -> {
                JsonObject playerData = new JsonObject();
                limits.forEach(playerData::addProperty);
                root.add(uuid.toString(), playerData);
            });

            try (FileWriter writer = new FileWriter(saveFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadLimits() {
        if (!saveFile.exists()) return;

        try (FileReader reader = new FileReader(saveFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.entrySet().forEach(entry -> {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject limitsJson = entry.getValue().getAsJsonObject();
                
                Map<String, Integer> limits = new HashMap<>();
                limitsJson.entrySet().forEach(e -> 
                    limits.put(e.getKey(), e.getValue().getAsInt()));
                
                playerLimits.put(uuid, limits);
            });
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