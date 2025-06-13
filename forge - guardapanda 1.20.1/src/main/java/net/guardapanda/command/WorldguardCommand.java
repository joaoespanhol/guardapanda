package net.guardapanda.command;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldguardCommand {

    private static final Map<String, Region> protectedRegions = new HashMap<>();
    private static final Map<Player, BlockPos> firstPoint = new HashMap<>();
    private static final Map<Player, BlockPos> secondPoint = new HashMap<>();
    private static final File regionFile = new File("protected_regions.json");
    private static final File bannedItemsFile = new File("banned_items.json");
    private static final File itemBypassFile = new File("item_bypass.json");
    private static final Set<String> bannedModItems = new HashSet<>();
    private static final Set<String> detectedFakePlayers = new HashSet<>();
    private static final Set<String> bypassPlayers = new HashSet<>();
    private static final Set<String> bypassItems = new HashSet<>();

    // Mensagens padr�o
    private static final Component NO_PERMISSION_MSG = Component.literal("�cVoc� n�o tem permiss�o para isso nesta regi�o.");
    private static final Component REGION_PROTECTED_MSG = Component.literal("�cEsta �rea � protegida.");
    private static final Component ITEM_NOT_ALLOWED_MSG = Component.literal("�cEste item n�o pode ser usado em regi�es protegidas.");

    static {
        loadRegionsFromFile();
        loadBannedItems();
        loadItemBypass();
    }

    private static class Region {
        private BlockPos start;
        private BlockPos end;
        private Map<String, Boolean> flags;
        private String owner;
        private Map<String, Boolean> members;
        private Map<String, Boolean> memberFlags;
        private Set<String> blockedCommands;
        private String enterMessage;
        private String exitMessage;
        private Set<String> allowedEntities;
        private Set<String> allowedPlayers = new HashSet<>();
        
        public Region(BlockPos start, BlockPos end, Map<String, Boolean> flags, String owner) {
            this.start = start;
            this.end = end;
            this.flags = flags != null ? flags : new HashMap<>();
            this.owner = owner;
            this.members = new HashMap<>();
            this.memberFlags = new HashMap<>();
            this.blockedCommands = new HashSet<>();
            this.enterMessage = "";
            this.exitMessage = "";
            this.allowedEntities = new HashSet<>();
            this.allowedPlayers = new HashSet<>();
            
            initializeDefaultFlags();
        }

        private void initializeDefaultFlags() {
            // Protection flags
            flags.putIfAbsent("build", false);
            flags.putIfAbsent("destroy", false);
            
            // Interaction flags
            flags.putIfAbsent("interact", false);
            flags.putIfAbsent("container-access", false);
            flags.putIfAbsent("sign", false);
            flags.putIfAbsent("item-frame-rotation", false);
            flags.putIfAbsent("item-frame-remove", false);
            flags.putIfAbsent("item-frame-break", false);
            flags.putIfAbsent("use-anvil", false);
            flags.putIfAbsent("use", true);
            
            // Entity flags
            flags.putIfAbsent("mob-spawning", false);
            flags.putIfAbsent("animal-spawning", true);
            flags.putIfAbsent("mob-damage", false);
            flags.putIfAbsent("pvp", false);
            flags.putIfAbsent("damage-animals", false);
            flags.putIfAbsent("mob-drops", true);
            
            // Explosion and damage flags
            flags.putIfAbsent("creeper-explosion", false);
            flags.putIfAbsent("other-explosion", false);
            flags.putIfAbsent("tnt", false);
            flags.putIfAbsent("fire-spread", false);
            
            // Communication flags
            flags.putIfAbsent("send-chat", true);
            flags.putIfAbsent("receive-chat", true);
            
            // Movement flags
            flags.putIfAbsent("teleport", false);
            flags.putIfAbsent("entry", true);
            
            // Other flags
            flags.putIfAbsent("invincible", false);
            flags.putIfAbsent("item-pickup", true);
            flags.putIfAbsent("item-drop", true);
            flags.putIfAbsent("mod-interaction", false);
            
            // Member flags
            memberFlags.put("build", false);
            memberFlags.put("destroy", false);
            memberFlags.put("interact", false);
            memberFlags.put("sign", false);
            memberFlags.put("container-access", false);
            memberFlags.put("use", true);
        }

        public boolean isWithinRegion(BlockPos pos) {
            return pos.getX() >= Math.min(start.getX(), end.getX()) && pos.getX() <= Math.max(start.getX(), end.getX()) &&
                   pos.getY() >= Math.min(start.getY(), end.getY()) && pos.getY() <= Math.max(start.getY(), end.getY()) &&
                   pos.getZ() >= Math.min(start.getZ(), end.getZ()) && pos.getZ() <= Math.max(start.getZ(), end.getZ());
        }

        public boolean isOwner(String playerName) {
            return owner != null && owner.equalsIgnoreCase(playerName);
        }

        public boolean isMember(String playerName) {
            return members.containsKey(playerName.toLowerCase());
        }

        public void addMember(String playerName) {
            members.put(playerName.toLowerCase(), true);
        }

        public void removeMember(String playerName) {
            members.remove(playerName.toLowerCase());
        }

        public Map<String, Boolean> getFlags() {
            return flags;
        }
        
        public boolean hasMemberFlag(String playerName, String flag) {
            return isOwner(playerName) || (isMember(playerName) && memberFlags.getOrDefault(flag, false));
        }
        
        public void setMemberFlag(String flag, boolean value) {
            memberFlags.put(flag, value);
        }
        
        public void blockCommand(String command) {
            blockedCommands.add(command.toLowerCase());
        }
        
        public void unblockCommand(String command) {
            blockedCommands.remove(command.toLowerCase());
        }
        
        public boolean isCommandBlocked(String command) {
            return blockedCommands.contains(command.toLowerCase());
        }
        
        public void setEnterMessage(String message) {
            this.enterMessage = message != null ? message : "";
        }
        
        public void setExitMessage(String message) {
            this.exitMessage = message != null ? message : "";
        }
        
        public void allowEntity(String entityName) {
            if (entityName != null) {
                allowedEntities.add(entityName.toLowerCase());
            }
        }
        
        public void disallowEntity(String entityName) {
            if (entityName != null) {
                allowedEntities.remove(entityName.toLowerCase());
            }
        }
        
        public void allowPlayer(String playerName) {
            if (playerName != null) {
                allowedPlayers.add(playerName.toLowerCase());
            }
        }
        
        public void disallowPlayer(String playerName) {
            if (playerName != null) {
                allowedPlayers.remove(playerName.toLowerCase());
            }
        }
        
        public boolean isEntityAllowed(String entityName) {
            return entityName != null && 
                   (allowedEntities.isEmpty() || allowedEntities.contains(entityName.toLowerCase()));
        }
        
        public boolean isPlayerAllowed(String playerName) {
            return playerName != null && 
                   (allowedPlayers.isEmpty() || allowedPlayers.contains(playerName.toLowerCase()));
        }
    }

    private static boolean isRegionProtected(BlockPos pos) {
        return protectedRegions.values().stream().anyMatch(region -> region.isWithinRegion(pos));
    }

    private static Region getRegion(BlockPos pos) {
        for (Region region : protectedRegions.values()) {
            if (region.isWithinRegion(pos)) {
                return region;
            }
        }
        return null;
    }

    private static boolean isFlagEnabled(BlockPos pos, String flag, Player player) {
        if (player == null) {
            return false;
        }
        
        if (player.hasPermissions(2)) {
            return true;
        }
        
        if (bypassPlayers.contains(player.getName().getString().toLowerCase())) {
            return true;
        }
        
        Region region = getRegion(pos);
        if (region == null) {
            return true;
        }
        
        if (region.isOwner(player.getName().getString())) {
            return true;
        }
        
        if (!region.isPlayerAllowed(player.getName().getString())) {
            if (flag.equals("entry") || flag.equals("teleport")) {
                return false;
            }
            return false;
        }
        
        if (region.isMember(player.getName().getString())) {
            if (region.hasMemberFlag(player.getName().getString(), flag)) {
                return true;
            }
            return region.getFlags().getOrDefault(flag, false);
        }
        
        return region.getFlags().getOrDefault(flag, false);
    }

    private static void saveRegionsToFile() {
        try (FileWriter writer = new FileWriter(regionFile)) {
            Gson gson = new Gson();
            gson.toJson(protectedRegions, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static void loadRegionsFromFile() {
        if (regionFile.exists()) {
            try (FileReader reader = new FileReader(regionFile)) {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Region>>(){}.getType();
                Map<String, Region> loadedRegions = gson.fromJson(reader, type);
                
                protectedRegions.clear();
                for (Map.Entry<String, Region> entry : loadedRegions.entrySet()) {
                    Region region = entry.getValue();
                    if (region.members == null) region.members = new HashMap<>();
                    if (region.memberFlags == null) region.memberFlags = new HashMap<>();
                    if (region.blockedCommands == null) region.blockedCommands = new HashSet<>();
                    if (region.allowedEntities == null) region.allowedEntities = new HashSet<>();
                    if (region.allowedPlayers == null) region.allowedPlayers = new HashSet<>();
                    if (region.enterMessage == null) region.enterMessage = "";
                    if (region.exitMessage == null) region.exitMessage = "";
                    protectedRegions.put(entry.getKey(), region);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void loadBannedItems() {
        if (bannedItemsFile.exists()) {
            try (FileReader reader = new FileReader(bannedItemsFile)) {
                Gson gson = new Gson();
                Type type = new TypeToken<Set<String>>(){}.getType();
                Set<String> loadedItems = gson.fromJson(reader, type);
                bannedModItems.clear();
                bannedModItems.addAll(loadedItems);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            bannedModItems.addAll(Arrays.asList(
                "create:drill",
                "create:mechanical_drill",
                "create:mechanical_plough",
                "create:mechanical_harvester"
            ));
            saveBannedItems();
        }
    }

    private static void saveBannedItems() {
        try (FileWriter writer = new FileWriter(bannedItemsFile)) {
            Gson gson = new Gson();
            gson.toJson(bannedModItems, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadItemBypass() {
        if (itemBypassFile.exists()) {
            try (FileReader reader = new FileReader(itemBypassFile)) {
                Gson gson = new Gson();
                Type type = new TypeToken<Set<String>>(){}.getType();
                Set<String> loadedBypass = gson.fromJson(reader, type);
                bypassItems.clear();
                bypassItems.addAll(loadedBypass);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void saveItemBypass() {
        try (FileWriter writer = new FileWriter(itemBypassFile)) {
            Gson gson = new Gson();
            gson.toJson(bypassItems, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
	private static boolean hasItemBypass(ItemStack stack, Block block) {
	    if (stack == null || stack.isEmpty()) return false;
	    String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
	    boolean hasBypass = bypassItems.contains(itemId.toLowerCase());
	    
	    // Se n�o tem bypass, retorna falso
	    if (!hasBypass) return false;
	    
	    // Se tem bypass, verifica se o bloco � do mesmo tipo que o item
	    if (block != null && stack.getItem() instanceof BlockItem) {
	        Block itemBlock = ((BlockItem)stack.getItem()).getBlock();
	        return itemBlock == block;
	    }
	    
	    return false;
	}

    // Verifica se um BLOCK tem bypass
    private static boolean hasBlockBypass(Block block) {
        if (block == null) return false;
        String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();
        return bypassItems.contains(blockId.toLowerCase());
    }
    
	// Verifica se um item est� na lista de itens banidos
    private static boolean isBannedModItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        
        if (bypassItems.contains(itemId.toLowerCase())) {
            return false; // Se o item tem bypass, n�o est� banido
        }
        
        return bannedModItems.contains(itemId.toLowerCase());
    }
    
    @SubscribeEvent
    public static void onFakePlayerDetection(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof FakePlayer) {
            FakePlayer fakePlayer = (FakePlayer) event.getEntity();
            String fakePlayerName = fakePlayer.getName().getString();
            String fakePlayerClass = fakePlayer.getClass().getName();
            
            detectedFakePlayers.add(fakePlayerName + " (" + fakePlayerClass + ")");
        }
    }

	@SubscribeEvent
	public static void onPlayerInteract(PlayerInteractEvent event) {
	    Player player = event.getEntity();
	    ItemStack heldItem = player.getMainHandItem();
	    
	    // Verificar se o jogador est� segurando um machado de ouro
	    if (heldItem.getItem() == Items.GOLDEN_AXE && event.getHand() == InteractionHand.MAIN_HAND) {
	        // Verificar se foi um clique em um bloco
	        if (event instanceof PlayerInteractEvent.RightClickBlock || 
	            event instanceof PlayerInteractEvent.LeftClickBlock) {
	            
	            BlockPos clickedPos = event.getPos();
	            
	            // Primeiro ponto com clique esquerdo
	            if (event instanceof PlayerInteractEvent.LeftClickBlock) {
	                firstPoint.put(player, clickedPos);
	                player.sendSystemMessage(Component.literal("�aPrimeiro ponto definido em: " + 
	                    clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()));
	                event.setCanceled(true);
	            } 
	            // Segundo ponto com clique direito
	            else if (event instanceof PlayerInteractEvent.RightClickBlock) {
	                secondPoint.put(player, clickedPos);
	                player.sendSystemMessage(Component.literal("�aSegundo ponto definido em: " + 
	                    clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()));
	                event.setCanceled(true);
	            }
	        }
	    }
	}

    @SubscribeEvent
    public static void onPlayerInteractAir(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();
        
        if (heldItem.getItem() == Items.GOLDEN_AXE) {
            BlockPos pos1 = firstPoint.get(player);
            BlockPos pos2 = secondPoint.get(player);
            
            if (pos1 != null && pos2 != null) {
                player.sendSystemMessage(Component.literal("�6Pontos selecionados:"));
                player.sendSystemMessage(Component.literal("�6Ponto 1: " + pos1.getX() + ", " + pos1.getY() + ", " + pos1.getZ()));
                player.sendSystemMessage(Component.literal("�6Ponto 2: " + pos2.getX() + ", " + pos2.getY() + ", " + pos2.getZ()));
            } else if (pos1 != null) {
                player.sendSystemMessage(Component.literal("�6Apenas o primeiro ponto foi definido: " + 
                    pos1.getX() + ", " + pos1.getY() + ", " + pos1.getZ()));
            } else if (pos2 != null) {
                player.sendSystemMessage(Component.literal("�6Apenas o segundo ponto foi definido: " + 
                    pos2.getX() + ", " + pos2.getY() + ", " + pos2.getZ()));
            } else {
                player.sendSystemMessage(Component.literal("�cNenhum ponto selecionado. Clique em blocos com o machado de ouro para definir os pontos."));
            }
        }
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("panda")
            .then(Commands.literal("proteger")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .executes(context -> {
                        String regionName = StringArgumentType.getString(context, "regionName");
                        Player player = context.getSource().getPlayerOrException();

                        if (firstPoint.containsKey(player) && secondPoint.containsKey(player)) {
                            BlockPos start = firstPoint.get(player);
                            BlockPos end = secondPoint.get(player);

                            if (isRegionProtected(start) || isRegionProtected(end)) {
                                player.sendSystemMessage(Component.literal("�cJ� existe uma regi�o protegida nesta �rea!"));
                                return 0;
                            }

                            Map<String, Boolean> flags = new HashMap<>();
                            protectedRegions.put(regionName, new Region(start, end, flags, player.getName().getString()));
                            saveRegionsToFile();
                            player.sendSystemMessage(Component.literal("�aRegi�o '" + regionName + "' foi protegida com sucesso!"));
                            firstPoint.remove(player);
                            secondPoint.remove(player);
                        } else {
                            player.sendSystemMessage(Component.literal("�cSelecione dois pontos usando o machado de ouro:"));
                            player.sendSystemMessage(Component.literal("�7- Clique normal para definir o primeiro ponto"));
                            player.sendSystemMessage(Component.literal("�7- Clique enquanto segura Shift para definir o segundo ponto"));
                            player.sendSystemMessage(Component.literal("�7- Clique no ar para ver os pontos selecionados"));
                        }
                        return 1;
                    })
                )
            )
            .then(Commands.literal("remover")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .executes(context -> {
                        String regionName = StringArgumentType.getString(context, "regionName");
                        Player player = context.getSource().getPlayerOrException();

                        if (protectedRegions.containsKey(regionName)) {
                            Region region = protectedRegions.get(regionName);
                            if (region.isOwner(player.getName().getString())) {
                                protectedRegions.remove(regionName);
                                saveRegionsToFile();
                                player.sendSystemMessage(Component.literal("�aA regi�o '" + regionName + "' foi removida com sucesso."));
                            } else {
                                player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode remov�-la."));
                            }
                        } else {
                            player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                        }
                        return 1;
                    })
                )
            )
            .then(Commands.literal("adicionarMembro")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .executes(context -> {
                            String regionName = StringArgumentType.getString(context, "regionName");
                            String playerName = StringArgumentType.getString(context, "playerName");
                            Player player = context.getSource().getPlayerOrException();

                            if (protectedRegions.containsKey(regionName)) {
                                Region region = protectedRegions.get(regionName);
                                if (region.isOwner(player.getName().getString())) {
                                    region.addMember(playerName);
                                    saveRegionsToFile();
                                    player.sendSystemMessage(Component.literal("�aJogador '" + playerName + "' foi adicionado � regi�o '" + regionName + "'."));
                                } else {
                                    player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode adicionar membros."));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                            }
                            return 1;
                        })
                    )
                )
            )
            .then(Commands.literal("removerMembro")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .then(Commands.argument("playerName", StringArgumentType.word())
                        .executes(context -> {
                            String regionName = StringArgumentType.getString(context, "regionName");
                            String playerName = StringArgumentType.getString(context, "playerName");
                            Player player = context.getSource().getPlayerOrException();

                            if (protectedRegions.containsKey(regionName)) {
                                Region region = protectedRegions.get(regionName);
                                if (region.isOwner(player.getName().getString())) {
                                    region.removeMember(playerName);
                                    saveRegionsToFile();
                                    player.sendSystemMessage(Component.literal("�aJogador '" + playerName + "' foi removido da regi�o '" + regionName + "'."));
                                } else {
                                    player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode remover membros."));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                            }
                            return 1;
                        })
                    )
                )
            )
            .then(Commands.literal("modificarFlag")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .then(Commands.argument("flag", StringArgumentType.word())
                        .then(Commands.argument("valor", StringArgumentType.word())
                            .executes(context -> {
                                String regionName = StringArgumentType.getString(context, "regionName");
                                String flag = StringArgumentType.getString(context, "flag");
                                String valor = StringArgumentType.getString(context, "valor");
                                Player player = context.getSource().getPlayerOrException();

                                if (protectedRegions.containsKey(regionName)) {
                                    Region region = protectedRegions.get(regionName);
                                    if (region.isOwner(player.getName().getString())) {
                                        boolean flagValue = Boolean.parseBoolean(valor);
                                        region.getFlags().put(flag, flagValue);
                                        saveRegionsToFile();
                                        player.sendSystemMessage(Component.literal("�aFlag '" + flag + "' na regi�o '" + regionName + "' foi alterada para '" + flagValue + "'."));
                                    } else {
                                        player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode modificar flags."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                                }
                                return 1;
                            })
                        )
                    )
                )
            )
            .then(Commands.literal("adicionarFlagMembro")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .then(Commands.argument("flag", StringArgumentType.word())
                        .then(Commands.argument("valor", StringArgumentType.word())
                            .executes(context -> {
                                String regionName = StringArgumentType.getString(context, "regionName");
                                String flag = StringArgumentType.getString(context, "flag");
                                String valor = StringArgumentType.getString(context, "valor");
                                Player player = context.getSource().getPlayerOrException();

                                if (protectedRegions.containsKey(regionName)) {
                                    Region region = protectedRegions.get(regionName);
                                    if (region.isOwner(player.getName().getString())) {
                                        boolean flagValue = Boolean.parseBoolean(valor);
                                        region.setMemberFlag(flag, flagValue);
                                        saveRegionsToFile();
                                        player.sendSystemMessage(Component.literal("�aFlag de membro '" + flag + "' na regi�o '" + regionName + "' foi alterada para '" + flagValue + "'."));
                                    } else {
                                        player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode modificar flags de membros."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                                }
                                return 1;
                            })
                        )
                    )
                )
            )
            .then(Commands.literal("blockcommand")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .then(Commands.argument("command", StringArgumentType.greedyString())
                        .executes(context -> {
                            String regionName = StringArgumentType.getString(context, "regionName");
                            String command = StringArgumentType.getString(context, "command");
                            Player player = context.getSource().getPlayerOrException();

                            if (protectedRegions.containsKey(regionName)) {
                                Region region = protectedRegions.get(regionName);
                                if (region.isOwner(player.getName().getString())) {
                                    region.blockCommand(command);
                                    saveRegionsToFile();
                                    player.sendSystemMessage(Component.literal("�aComando '" + command + "' foi bloqueado na regi�o '" + regionName + "'."));
                                } else {
                                    player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode bloquear comandos."));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                            }
                            return 1;
                        })
                    )
                )
            )
            .then(Commands.literal("unblockcommand")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .then(Commands.argument("command", StringArgumentType.greedyString())
                        .executes(context -> {
                            String regionName = StringArgumentType.getString(context, "regionName");
                            String command = StringArgumentType.getString(context, "command");
                            Player player = context.getSource().getPlayerOrException();

                            if (protectedRegions.containsKey(regionName)) {
                                Region region = protectedRegions.get(regionName);
                                if (region.isOwner(player.getName().getString())) {
                                    region.unblockCommand(command);
                                    saveRegionsToFile();
                                    player.sendSystemMessage(Component.literal("�aComando '" + command + "' foi desbloqueado na regi�o '" + regionName + "'."));
                                } else {
                                    player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode desbloquear comandos."));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                            }
                            return 1;
                        })
                    )
                )
            )
            .then(Commands.literal("addMensagem")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .then(Commands.argument("tipo", StringArgumentType.word())
                        .then(Commands.argument("mensagem", StringArgumentType.greedyString())
                            .executes(context -> {
                                String regionName = StringArgumentType.getString(context, "regionName");
                                String tipo = StringArgumentType.getString(context, "tipo");
                                String mensagem = StringArgumentType.getString(context, "mensagem");
                                Player player = context.getSource().getPlayerOrException();

                                if (protectedRegions.containsKey(regionName)) {
                                    Region region = protectedRegions.get(regionName);
                                    if (region.isOwner(player.getName().getString())) {
                                        if (tipo.equalsIgnoreCase("entrada")) {
                                            region.setEnterMessage(mensagem);
                                            player.sendSystemMessage(Component.literal("�aMensagem de entrada da regi�o '" + regionName + "' definida para: " + mensagem));
                                        } else if (tipo.equalsIgnoreCase("saida")) {
                                            region.setExitMessage(mensagem);
                                            player.sendSystemMessage(Component.literal("�aMensagem de sa�da da regi�o '" + regionName + "' definida para: " + mensagem));
                                        } else {
                                            player.sendSystemMessage(Component.literal("�cTipo inv�lido. Use 'entrada' ou 'saida'."));
                                        }
                                        saveRegionsToFile();
                                    } else {
                                        player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode modificar mensagens."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                                }
                                return 1;
                            })
                        )
                    )
                )
            )
            .then(Commands.literal("entrance_land")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .then(Commands.argument("tipo", StringArgumentType.word())
                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                            .executes(context -> {
                                String regionName = StringArgumentType.getString(context, "regionName");
                                String tipo = StringArgumentType.getString(context, "tipo");
                                String nome = StringArgumentType.getString(context, "nome");
                                Player player = context.getSource().getPlayerOrException();

                                if (protectedRegions.containsKey(regionName)) {
                                    Region region = protectedRegions.get(regionName);
                                    if (region.isOwner(player.getName().getString())) {
                                        if (tipo.equalsIgnoreCase("player")) {
                                            region.allowPlayer(nome);
                                            player.sendSystemMessage(Component.literal("�aJogador '" + nome + "' foi permitido na regi�o '" + regionName + "'."));
                                        } else if (tipo.equalsIgnoreCase("entity")) {
                                            region.allowEntity(nome);
                                            player.sendSystemMessage(Component.literal("�aEntidade '" + nome + "' foi permitida na regi�o '" + regionName + "'."));
                                        } else {
                                            player.sendSystemMessage(Component.literal("�cTipo inv�lido. Use 'player' ou 'entity'."));
                                        }
                                        saveRegionsToFile();
                                    } else {
                                        player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode modificar permiss�es de entrada."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                                }
                                return 1;
                            })
                        )
                    )
                )
            )
            .then(Commands.literal("remove_entrance")
                .then(Commands.argument("regionName", StringArgumentType.word())
                    .then(Commands.argument("tipo", StringArgumentType.word())
                        .then(Commands.argument("nome", StringArgumentType.greedyString())
                            .executes(context -> {
                                String regionName = StringArgumentType.getString(context, "regionName");
                                String tipo = StringArgumentType.getString(context, "tipo");
                                String nome = StringArgumentType.getString(context, "nome");
                                Player player = context.getSource().getPlayerOrException();

                                if (protectedRegions.containsKey(regionName)) {
                                    Region region = protectedRegions.get(regionName);
                                    if (region.isOwner(player.getName().getString())) {
                                        if (tipo.equalsIgnoreCase("player")) {
                                            region.disallowPlayer(nome);
                                            player.sendSystemMessage(Component.literal("�aJogador '" + nome + "' foi removido da lista de permiss�es da regi�o '" + regionName + "'."));
                                        } else if (tipo.equalsIgnoreCase("entity")) {
                                            region.disallowEntity(nome);
                                            player.sendSystemMessage(Component.literal("�aEntidade '" + nome + "' foi removida da lista de permiss�es da regi�o '" + regionName + "'."));
                                        } else {
                                            player.sendSystemMessage(Component.literal("�cTipo inv�lido. Use 'player' ou 'entity'."));
                                        }
                                        saveRegionsToFile();
                                    } else {
                                        player.sendSystemMessage(Component.literal("�cVoc� n�o � o dono desta regi�o e n�o pode modificar permiss�es de entrada."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("�cA regi�o '" + regionName + "' n�o existe."));
                                }
                                return 1;
                            })
                        )
                    )
                )
            )
            .then(Commands.literal("flagsAtuais")
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    BlockPos playerPos = player.blockPosition();

                    for (Map.Entry<String, Region> entry : protectedRegions.entrySet()) {
                        String regionName = entry.getKey();
                        Region region = entry.getValue();

                        if (region.isWithinRegion(playerPos)) {
                            player.sendSystemMessage(Component.literal("�6Flags da regi�o '" + regionName + "':"));
                            for (Map.Entry<String, Boolean> flagEntry : region.getFlags().entrySet()) {
                                String flag = flagEntry.getKey();
                                boolean value = flagEntry.getValue();
                                player.sendSystemMessage(Component.literal("�7- " + flag + ": " + value));
                            }
                            
                            player.sendSystemMessage(Component.literal("�6Flags de membros:"));
                            for (Map.Entry<String, Boolean> flagEntry : region.memberFlags.entrySet()) {
                                String flag = flagEntry.getKey();
                                boolean value = flagEntry.getValue();
                                player.sendSystemMessage(Component.literal("�7- " + flag + ": " + value));
                            }
                            
                            if (!region.blockedCommands.isEmpty()) {
                                player.sendSystemMessage(Component.literal("�6Comandos bloqueados:"));
                                for (String cmd : region.blockedCommands) {
                                    player.sendSystemMessage(Component.literal("�7- " + cmd));
                                }
                            }
                            
                            if (region.enterMessage != null && !region.enterMessage.isEmpty()) {
                                player.sendSystemMessage(Component.literal("�6Mensagem de entrada: �7" + region.enterMessage));
                            }
                            if (region.exitMessage != null && !region.exitMessage.isEmpty()) {
                                player.sendSystemMessage(Component.literal("�6Mensagem de sa�da: �7" + region.exitMessage));
                            }
                            
                            if (!region.allowedEntities.isEmpty()) {
                                player.sendSystemMessage(Component.literal("�6Entidades permitidas:"));
                                for (String entity : region.allowedEntities) {
                                    player.sendSystemMessage(Component.literal("�7- " + entity));
                                }
                            }
                            
                            if (!region.allowedPlayers.isEmpty()) {
                                player.sendSystemMessage(Component.literal("�6Jogadores permitidos:"));
                                for (String pl : region.allowedPlayers) {
                                    player.sendSystemMessage(Component.literal("�7- " + pl));
                                }
                            }
                            return 1;
                        }
                    }

                    player.sendSystemMessage(Component.literal("�cVoc� n�o est� em uma regi�o protegida."));
                    return 0;
                })
            )
            .then(Commands.literal("listarFlags")
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();

                    List<String> flags = List.of(
                        "build", "destroy", "interact", "container-access", "sign", 
                        "item-frame-rotation", "item-frame-remove", "item-frame-break", 
                        "use-anvil", "use", "mob-spawning", "animal-spawning", "mob-damage", 
                        "pvp", "damage-animals", "mob-drops", "creeper-explosion", 
                        "other-explosion", "tnt", "fire-spread", "send-chat", "receive-chat", 
                        "teleport", "entry", "invincible", "item-pickup", "item-drop", 
                        "mod-interaction"
                    );

                    player.sendSystemMessage(Component.literal("�6Flags dispon�veis:"));
                    for (String flag : flags) {
                        player.sendSystemMessage(Component.literal("�7- " + flag));
                    }
                    
                    player.sendSystemMessage(Component.literal("\n�6Flags de membros dispon�veis:"));
                    player.sendSystemMessage(Component.literal("�7- build"));
                    player.sendSystemMessage(Component.literal("�7- destroy"));
                    player.sendSystemMessage(Component.literal("�7- interact"));
                    player.sendSystemMessage(Component.literal("�7- sign"));
                    player.sendSystemMessage(Component.literal("�7- container-access"));
                    player.sendSystemMessage(Component.literal("�7- use"));

                    return 1;
                })
            )
            .then(Commands.literal("banirItem")
                .then(Commands.argument("itemId", StringArgumentType.greedyString())
                    .executes(context -> {
                        String itemId = StringArgumentType.getString(context, "itemId");
                        Player player = context.getSource().getPlayerOrException();
                        
                        if (!itemId.contains(":")) {
                            player.sendSystemMessage(Component.literal("�cFormato inv�lido. Use modid:itemname (ex: create:drill)"));
                            return 0;
                        }
                        
                        bannedModItems.add(itemId.toLowerCase());
                        saveBannedItems();
                        player.sendSystemMessage(Component.literal("�aItem '" + itemId + "' foi banido de regi�es protegidas."));
                        return 1;
                    })
                )
            )
            .then(Commands.literal("permitirItem")
                .then(Commands.argument("itemId", StringArgumentType.greedyString())
                    .executes(context -> {
                        String itemId = StringArgumentType.getString(context, "itemId");
                        Player player = context.getSource().getPlayerOrException();
                        
                        if (bannedModItems.remove(itemId.toLowerCase())) {
                            saveBannedItems();
                            player.sendSystemMessage(Component.literal("�aItem '" + itemId + "' foi permitido em regi�es protegidas."));
                        } else {
                            player.sendSystemMessage(Component.literal("�cItem '" + itemId + "' n�o estava na lista de banidos."));
                        }
                        return 1;
                    })
                )
            )
            .then(Commands.literal("listarItensBanidos")
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    
                    if (bannedModItems.isEmpty()) {
                        player.sendSystemMessage(Component.literal("�aNenhum item est� banido no momento."));
                    } else {
                        player.sendSystemMessage(Component.literal("�6Itens banidos:"));
                        for (String item : bannedModItems) {
                            player.sendSystemMessage(Component.literal("�7- " + item));
                        }
                    }
                    return 1;
                })
            )
            .then(Commands.literal("listarFakePlayers")
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    
                    if (detectedFakePlayers.isEmpty()) {
                        player.sendSystemMessage(Component.literal("�aNenhum FakePlayer foi detectado ainda."));
                    } else {
                        player.sendSystemMessage(Component.literal("�6FakePlayers detectados:"));
                        for (String fakePlayer : detectedFakePlayers) {
                            player.sendSystemMessage(Component.literal("�7- " + fakePlayer));
                        }
                    }
                    return 1;
                })
            )
            .then(Commands.literal("bypass")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player").toLowerCase();
                        
                        if (bypassPlayers.contains(playerName)) {
                            bypassPlayers.remove(playerName);
                            context.getSource().sendSystemMessage(Component.literal("�aBypass removido para " + playerName));
                        } else {
                            bypassPlayers.add(playerName);
                            context.getSource().sendSystemMessage(Component.literal("�aBypass concedido para " + playerName));
                        }
                        return 1;
                    })
                )
            )
            .then(Commands.literal("bypassremove")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", StringArgumentType.word())
                    .executes(context -> {
                        String playerName = StringArgumentType.getString(context, "player").toLowerCase();
                        if (bypassPlayers.remove(playerName)) {
                            context.getSource().sendSystemMessage(Component.literal("�aRemovido bypass do jogador " + playerName));
                        } else {
                            context.getSource().sendSystemMessage(Component.literal("�cJogador " + playerName + " n�o estava na lista de bypass"));
                        }
                        return 1;
                    })
                )
            )
            .then(Commands.literal("bypassitemadd")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    ItemStack heldItem = player.getMainHandItem();
                    
                    if (heldItem.isEmpty()) {
                        player.sendSystemMessage(Component.literal("�cVoc� precisa segurar um item/bloco na m�o principal!"));
                        return 0;
                    }
                    
                    String itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString();
                    
                    if (bypassItems.add(itemId.toLowerCase())) {
                        saveItemBypass();
                        player.sendSystemMessage(Component.literal("�aItem/Bloco " + itemId + " adicionado ao bypass!"));
                    } else {
                        player.sendSystemMessage(Component.literal("�cEste item/bloco j� tinha bypass!"));
                    }
                    return 1;
                })
            )
            .then(Commands.literal("bypassitemremove")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    ItemStack heldItem = player.getMainHandItem();
                    
                    if (heldItem.isEmpty()) {
                        player.sendSystemMessage(Component.literal("�cVoc� precisa segurar um item/bloco na m�o principal!"));
                        return 0;
                    }
                    
                    String itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString();
                    
                    if (bypassItems.remove(itemId.toLowerCase())) {
                        saveItemBypass();
                        player.sendSystemMessage(Component.literal("�aItem/Bloco " + itemId + " removido do bypass!"));
                    } else {
                        player.sendSystemMessage(Component.literal("�cEste item/bloco n�o estava no bypass!"));
                    }
                    return 1;
                })
            )
            .then(Commands.literal("bypassitemlist")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    
                    if (bypassItems.isEmpty()) {
                        player.sendSystemMessage(Component.literal("�aNenhum item/bloco tem bypass no momento."));
                    } else {
                        player.sendSystemMessage(Component.literal("�6Itens/Blocos com bypass:"));
                        for (String item : bypassItems) {
                            player.sendSystemMessage(Component.literal("�7- " + item));
                        }
                    }
                    return 1;
                })
            )
        );
    }
	
	@SubscribeEvent
	public static void onBlockBreak(BlockEvent.BreakEvent event) {
	    Player player = event.getPlayer();
	    BlockPos pos = event.getPos();
	    BlockState state = event.getState();
	    Block block = state.getBlock();
	    ItemStack heldItem = player.getMainHandItem();
	
	    // Verifica se pode quebrar com base nas regras de bypass
	    if (canBreakWithBypass(heldItem, block)) {
	        return; // Permite quebrar
	    }
	
	    // Verifica��o para placas
	    if (state.getBlock() instanceof SignBlock || state.getBlock() instanceof WallSignBlock) {
	        if (isRegionProtected(pos) && !isFlagEnabled(pos, "sign", player)) {
	            event.setCanceled(true);
	            player.sendSystemMessage(NO_PERMISSION_MSG);
	            return;
	        }
	    }
	
	    // Verifica��o para FakePlayers (mods)
	    if (player instanceof FakePlayer) {
	        if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", null)) {
	            event.setCanceled(true);
	            return;
	        }
	    }
	    
	    // Verifica��o para itens banidos
	    if (isBannedModItem(player.getMainHandItem())) {
	        if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", player)) {
	            event.setCanceled(true);
	            player.sendSystemMessage(ITEM_NOT_ALLOWED_MSG);
	            return;
	        }
	    }
	    
	    // Verifica��o padr�o de prote��o de regi�o
	    if (isRegionProtected(pos) && !isFlagEnabled(pos, "destroy", player)) {
	        event.setCanceled(true);
	        player.sendSystemMessage(NO_PERMISSION_MSG);
	    }
	}
	
	// Nova fun��o auxiliar para verificar regras de bypass
	private static boolean canBreakWithBypass(ItemStack heldItem, Block block) {
	    // Se est� quebrando com a m�o vazia, verifica se o bloco foi colocado com bypass
	    if (heldItem.isEmpty()) {
	        return wasPlacedWithBypass(block);
	    }
	    
	    // Verifica se o item tem bypass
	    String itemId = ForgeRegistries.ITEMS.getKey(heldItem.getItem()).toString();
	    boolean itemHasBypass = bypassItems.contains(itemId.toLowerCase());
	    
	    // Se o item tem bypass, verifica se � do mesmo tipo que o bloco
	    if (itemHasBypass && heldItem.getItem() instanceof BlockItem) {
	        Block itemBlock = ((BlockItem)heldItem.getItem()).getBlock();
	        return itemBlock == block;
	    }
	    
	    // Se n�o tem bypass ou n�o � do mesmo tipo, verifica se o bloco foi colocado com bypass
	    return wasPlacedWithBypass(block);
	}
	
	// Verifica se o bloco foi colocado com um item que tem bypass
	private static boolean wasPlacedWithBypass(Block block) {
	    if (block == null) return false;
	    String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();
	    return bypassItems.contains(blockId.toLowerCase());
	}

 
@SubscribeEvent
public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
    if (!(event.getEntity() instanceof Player)) return;

    Player player = (Player) event.getEntity();
    BlockPos pos = event.getPos();
    BlockState state = event.getPlacedBlock();
    Block block = state.getBlock();
    ItemStack heldItem = player.getMainHandItem();

    // Se o jogador est� segurando um item com bypass E o bloco sendo colocado � do mesmo tipo
    if (hasItemBypass(heldItem, block)) {
        return; // Permite colocar apenas se for o mesmo tipo
    }

    if (state.getBlock() instanceof SignBlock || state.getBlock() instanceof WallSignBlock) {
        if (isRegionProtected(pos) && !isFlagEnabled(pos, "sign", player)) {
            event.setCanceled(true);
            player.sendSystemMessage(NO_PERMISSION_MSG);
            return;
        }
    }

    if (player instanceof FakePlayer) {
        if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", null)) {
            event.setCanceled(true);
            return;
        }
    }

    if (isBannedModItem(player.getMainHandItem())) {
        if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", player)) {
            event.setCanceled(true);
            player.sendSystemMessage(ITEM_NOT_ALLOWED_MSG);
            return;
        }
    }
    
    if (isRegionProtected(pos) && !isFlagEnabled(pos, "build", player)) {
        event.setCanceled(true);
        player.sendSystemMessage(NO_PERMISSION_MSG);
    }
}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        
        if (state.getBlock() instanceof BaseEntityBlock) {
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "container-access", player)) {
                event.setCanceled(true);
                player.sendSystemMessage(NO_PERMISSION_MSG);
                return;
            }
        }
        
        if (state.getBlock() instanceof AnvilBlock) {
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "use-anvil", player)) {
                event.setCanceled(true);
                player.sendSystemMessage(NO_PERMISSION_MSG);
                return;
            }
        }
    }

	@SubscribeEvent
	public static void onItemUse(PlayerInteractEvent.RightClickItem event) {
	    Player player = event.getEntity();
	    ItemStack heldItem = event.getItemStack();
	    
	    if (heldItem.getItem() == Items.GOLDEN_AXE && event.getHand() == InteractionHand.MAIN_HAND) {
	        BlockPos pos1 = firstPoint.get(player);
	        BlockPos pos2 = secondPoint.get(player);
	        
	        if (pos1 != null && pos2 != null) {
	            player.sendSystemMessage(Component.literal("�6Pontos selecionados:"));
	            player.sendSystemMessage(Component.literal("�6Ponto 1: " + pos1.getX() + ", " + pos1.getY() + ", " + pos1.getZ()));
	            player.sendSystemMessage(Component.literal("�6Ponto 2: " + pos2.getX() + ", " + pos2.getY() + ", " + pos2.getZ()));
	        } else if (pos1 != null) {
	            player.sendSystemMessage(Component.literal("�6Apenas o primeiro ponto foi definido: " + 
	                pos1.getX() + ", " + pos1.getY() + ", " + pos1.getZ()));
	        } else if (pos2 != null) {
	            player.sendSystemMessage(Component.literal("�6Apenas o segundo ponto foi definido: " + 
	                pos2.getX() + ", " + pos2.getY() + ", " + pos2.getZ()));
	        } else {
	            player.sendSystemMessage(Component.literal("�cNenhum ponto selecionado. Use:"));
	            player.sendSystemMessage(Component.literal("�7- Clique ESQUERDO em blocos para definir o primeiro ponto"));
	            player.sendSystemMessage(Component.literal("�7- Clique DIREITO em blocos para definir o segundo ponto"));
	            player.sendSystemMessage(Component.literal("�7- Clique DIREITO no ar para ver os pontos selecionados"));
	        }
	        event.setCanceled(true);
	    }
	}

    @SubscribeEvent
    public static void onItemFrameInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof ItemFrame) {
            Player player = event.getEntity();
            BlockPos pos = event.getTarget().blockPosition();
            
            if (isRegionProtected(pos)) {
                ItemFrame frame = (ItemFrame) event.getTarget();
                ItemStack heldItem = player.getItemInHand(event.getHand());
                
                if (!heldItem.isEmpty() && frame.getItem().isEmpty()) {
                    if (!isFlagEnabled(pos, "item-frame-rotation", player)) {
                        event.setCanceled(true);
                        player.sendSystemMessage(NO_PERMISSION_MSG);
                        return;
                    }
                }
                
                if (heldItem.isEmpty() && !frame.getItem().isEmpty()) {
                    if (!isFlagEnabled(pos, "item-frame-remove", player)) {
                        event.setCanceled(true);
                        player.sendSystemMessage(NO_PERMISSION_MSG);
                        return;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent event) {
        Entity source = event.getSource().getEntity();
        LivingEntity target = event.getEntity();
        BlockPos pos = target.blockPosition();
        
        if (isRegionProtected(pos)) {
            if (source instanceof Player && target instanceof Player) {
                if (!isFlagEnabled(pos, "pvp", (Player) source)) {
                    event.setCanceled(true);
                    source.sendSystemMessage(NO_PERMISSION_MSG);
                    return;
                }
            }
            
            if (target instanceof Animal && source instanceof Player) {
                if (!isFlagEnabled(pos, "damage-animals", (Player) source)) {
                    event.setCanceled(true);
                    source.sendSystemMessage(NO_PERMISSION_MSG);
                    return;
                }
            }
            
            if ((target instanceof Monster || target instanceof Ghast || target instanceof Slime) && source instanceof Player) {
                if (!isFlagEnabled(pos, "mob-damage", (Player) source)) {
                    event.setCanceled(true);
                    source.sendSystemMessage(NO_PERMISSION_MSG);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMobSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        
        LivingEntity entity = (LivingEntity) event.getEntity();
        BlockPos pos = entity.blockPosition();
        
        if (entity instanceof Player) return;
        
        if (entity instanceof Monster || entity instanceof Ghast || entity instanceof Slime) {
            if (isRegionProtected(pos)) {
                Region region = getRegion(pos);
                if (region != null && !region.getFlags().getOrDefault("mob-spawning", false)) {
                    event.setCanceled(true);
                    entity.discard();
                }
            }
        }
        
        if (entity instanceof Animal) {
            if (isRegionProtected(pos)) {
                Region region = getRegion(pos);
                if (region != null && !region.getFlags().getOrDefault("animal-spawning", true)) {
                    event.setCanceled(true);
                    entity.discard();
                }
            }
        }
    }

	private static boolean isSameBlockType(Block block, ItemStack stack) {
	    if (stack == null || stack.isEmpty() || block == null) return false;
	    Item item = stack.getItem();
	    if (item instanceof BlockItem) {
	        return ((BlockItem)item).getBlock() == block;
	    }
	    return false;
	}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        BlockPos pos = entity.blockPosition();
        
        if (isRegionProtected(pos)) {
            Region region = getRegion(pos);
            if (region != null && !region.getFlags().getOrDefault("mob-drops", true)) {
                event.getDrops().clear();
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Start event) {
        BlockPos pos = new BlockPos((int) event.getExplosion().getPosition().x, 
                                  (int) event.getExplosion().getPosition().y, 
                                  (int) event.getExplosion().getPosition().z);
        
        if (isRegionProtected(pos)) {
            Entity source = event.getExplosion().getDirectSourceEntity();
            if (source instanceof Creeper) {
                if (!isFlagEnabled(pos, "creeper-explosion", null)) {
                    event.setCanceled(true);
                    return;
                }
            } else {
                if (!isFlagEnabled(pos, "other-explosion", null)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCommandExecution(CommandEvent event) {
        if (event.getParseResults().getContext().getSource().getEntity() instanceof Player) {
            Player player = (Player) event.getParseResults().getContext().getSource().getEntity();
            BlockPos pos = player.blockPosition();
            
            if (isRegionProtected(pos)) {
                Region region = getRegion(pos);
                if (region != null) {
                    String command = event.getParseResults().getReader().getString();
                    String commandName = command.split(" ")[0].replace("/", "").toLowerCase();
                    
                    if (region.isCommandBlocked(commandName)) {
                        if (!isFlagEnabled(pos, "command-" + commandName, player)) {
                            event.setCanceled(true);
                            player.sendSystemMessage(NO_PERMISSION_MSG);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;
        
        Player player = event.player;
        BlockPos currentPos = player.blockPosition();
        
        for (Region region : protectedRegions.values()) {
            boolean isInsideNow = region.isWithinRegion(currentPos);
            boolean wasInsideBefore = region.isWithinRegion(player.getOnPos());
            
            if (isInsideNow && !wasInsideBefore) {
                if (!region.enterMessage.isEmpty()) {
                    player.sendSystemMessage(Component.literal(region.enterMessage));
                }
            } else if (!isInsideNow && wasInsideBefore) {
                if (!region.exitMessage.isEmpty()) {
                    player.sendSystemMessage(Component.literal(region.exitMessage));
                }
            }
        }
    }

    public static class ThrowableBrickEntity extends ThrowableItemProjectile {
        public ThrowableBrickEntity(EntityType<? extends ThrowableBrickEntity> type, Level world) {
            super(type, world);
        }

        public ThrowableBrickEntity(Level world, LivingEntity thrower) {
            super(EntityType.SNOWBALL, thrower, world);
        }

        @Override
        protected Item getDefaultItem() {
            return Items.BRICK;
        }

        @Override
        protected void onHit(HitResult result) {
            super.onHit(result);

            if (result.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) result;
                BlockPos pos = blockHitResult.getBlockPos();

                if (isRegionProtected(pos)) {
                    Player owner = this.getOwner() instanceof Player ? (Player) this.getOwner() : null;
                    if (!isFlagEnabled(pos, "destroy", owner)) {
                        this.level().broadcastEntityEvent(this, (byte) 3);
                        this.discard();
                        if (owner != null) {
                            owner.sendSystemMessage(NO_PERMISSION_MSG);
                        }
                        return;
                    }
                }
            } else if (result.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHitResult = (EntityHitResult) result;
                LivingEntity entity = (LivingEntity) entityHitResult.getEntity();
                BlockPos pos = entity.blockPosition();

                if (isRegionProtected(pos)) {
                    Player owner = this.getOwner() instanceof Player ? (Player) this.getOwner() : null;
                    if (!isFlagEnabled(pos, "mob-damage", owner)) {
                        this.level().broadcastEntityEvent(this, (byte) 3);
                        this.discard();
                        if (owner != null) {
                            owner.sendSystemMessage(NO_PERMISSION_MSG);
                        }
                        return;
                    }
                }

                entity.hurt(entity.damageSources().thrown(this, this.getOwner()), 4.0F);
            }

            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }
}