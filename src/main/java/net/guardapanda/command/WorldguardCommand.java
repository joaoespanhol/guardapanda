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

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
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
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldguardCommand {

    private static final Map<String, Region> protectedRegions = new HashMap<>();
    private static final Map<Player, BlockPos> firstPoint = new HashMap<>();
    private static final Map<Player, BlockPos> secondPoint = new HashMap<>();
    private static final File regionFile = new File("protected_regions.json");
    private static final File bannedItemsFile = new File("banned_items.json");
    private static final Set<String> bannedModItems = new HashSet<>();
    private static final Set<String> detectedFakePlayers = new HashSet<>();

    static {
        loadRegionsFromFile();
        loadBannedItems();
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
            flags.putIfAbsent("block-place", false);
            flags.putIfAbsent("block-break", false);
            flags.putIfAbsent("destroy", false);
            flags.putIfAbsent("mod-interaction", false);
            
            // Interaction flags
            flags.putIfAbsent("interact", false);
            flags.putIfAbsent("use", false);
            flags.putIfAbsent("chest-access", false);
            flags.putIfAbsent("sign", false); // Flag única para placas
            flags.putIfAbsent("item-frame-rotation", false);
            flags.putIfAbsent("item-frame-remove", false);
            flags.putIfAbsent("item-frame-break", false);
            flags.putIfAbsent("use-anvil", false);
            flags.putIfAbsent("container-access", false);
            
            // Entity flags
            flags.putIfAbsent("mob-spawning", false);
            flags.putIfAbsent("mob-damage", false);
            flags.putIfAbsent("pvp", false);
            flags.putIfAbsent("damage-animals", false);
            
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
            
            // Default member flags
            memberFlags.put("build", false);
            memberFlags.put("block-break", false);
            memberFlags.put("block-place", false);
            memberFlags.put("interact", false);
            memberFlags.put("sign", false); // Permissão de placas para membros
            memberFlags.put("use", false);
            memberFlags.put("item-frame-rotation", false);
        }

        public boolean isWithinRegion(BlockPos pos) {
            return pos.getX() >= Math.min(start.getX(), end.getX()) && pos.getX() <= Math.max(start.getX(), end.getX()) &&
                   pos.getY() >= Math.min(start.getY(), end.getY()) && pos.getY() <= Math.max(start.getY(), end.getY()) &&
                   pos.getZ() >= Math.min(start.getZ(), end.getZ()) && pos.getZ() <= Math.max(start.getZ(), end.getZ());
        }

        public boolean isOwner(String playerName) {
            return owner != null && owner.equals(playerName);
        }

        public boolean isMember(String playerName) {
            return members.containsKey(playerName);
        }

        public void addMember(String playerName) {
            members.put(playerName, true);
        }

        public void removeMember(String playerName) {
            members.remove(playerName);
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
                   (allowedPlayers == null || allowedPlayers.isEmpty() || 
                   allowedPlayers.contains(playerName.toLowerCase()));
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
        
        Region region = getRegion(pos);
        if (region == null) {
            return true;
        }
        
        if (!region.isPlayerAllowed(player.getName().getString())) {
            if (flag.equals("entry") || flag.equals("teleport")) {
                return false;
            }
            return false;
        }
        
        // Verificação específica para placas
        if (flag.equals("sign-place") || flag.equals("sign-break")) {
            // Se for membro e tiver permissão específica
            if (region.isMember(player.getName().getString()) && 
                region.hasMemberFlag(player.getName().getString(), "sign")) {
                return true;
            }
            // Se a flag geral de placas estiver ativada
            return region.getFlags().getOrDefault("sign", false);
        }
        
        if (region.isOwner(player.getName().getString())) {
            return true;
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
            // Default banned items
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

    private static boolean isBannedModItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
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
                                player.sendSystemMessage(Component.literal("Já existe uma região protegida nesta área!"));
                                return 0;
                            }

                            Map<String, Boolean> flags = new HashMap<>();
                            protectedRegions.put(regionName, new Region(start, end, flags, player.getName().getString()));
                            saveRegionsToFile();
                            player.sendSystemMessage(Component.literal("Região '" + regionName + "' foi protegida com sucesso!"));
                            firstPoint.remove(player);
                            secondPoint.remove(player);
                        } else {
                            player.sendSystemMessage(Component.literal("Selecione dois pontos usando o machado."));
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
                                player.sendSystemMessage(Component.literal("A região '" + regionName + "' foi removida com sucesso."));
                            } else {
                                player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode removê-la."));
                            }
                        } else {
                            player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                                    player.sendSystemMessage(Component.literal("Jogador '" + playerName + "' foi adicionado à região '" + regionName + "'."));
                                } else {
                                    player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode adicionar membros."));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                                    player.sendSystemMessage(Component.literal("Jogador '" + playerName + "' foi removido da região '" + regionName + "'."));
                                } else {
                                    player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode remover membros."));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                                        player.sendSystemMessage(Component.literal("Flag '" + flag + "' na região '" + regionName + "' foi alterada para '" + flagValue + "'."));
                                    } else {
                                        player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode modificar flags."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                                        player.sendSystemMessage(Component.literal("Flag de membro '" + flag + "' na região '" + regionName + "' foi alterada para '" + flagValue + "'."));
                                    } else {
                                        player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode modificar flags de membros."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                                    player.sendSystemMessage(Component.literal("Comando '" + command + "' foi bloqueado na região '" + regionName + "'."));
                                } else {
                                    player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode bloquear comandos."));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                                    player.sendSystemMessage(Component.literal("Comando '" + command + "' foi desbloqueado na região '" + regionName + "'."));
                                } else {
                                    player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode desbloquear comandos."));
                                }
                            } else {
                                player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                                            player.sendSystemMessage(Component.literal("Mensagem de entrada da região '" + regionName + "' definida para: " + mensagem));
                                        } else if (tipo.equalsIgnoreCase("saida")) {
                                            region.setExitMessage(mensagem);
                                            player.sendSystemMessage(Component.literal("Mensagem de saída da região '" + regionName + "' definida para: " + mensagem));
                                        } else {
                                            player.sendSystemMessage(Component.literal("Tipo inválido. Use 'entrada' ou 'saida'."));
                                        }
                                        saveRegionsToFile();
                                    } else {
                                        player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode modificar mensagens."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                                            player.sendSystemMessage(Component.literal("Jogador '" + nome + "' foi permitido na região '" + regionName + "'."));
                                        } else if (tipo.equalsIgnoreCase("entity")) {
                                            region.allowEntity(nome);
                                            player.sendSystemMessage(Component.literal("Entidade '" + nome + "' foi permitida na região '" + regionName + "'."));
                                        } else {
                                            player.sendSystemMessage(Component.literal("Tipo inválido. Use 'player' ou 'entity'."));
                                        }
                                        saveRegionsToFile();
                                    } else {
                                        player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode modificar permissões de entrada."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                                            player.sendSystemMessage(Component.literal("Jogador '" + nome + "' foi removido da lista de permissões da região '" + regionName + "'."));
                                        } else if (tipo.equalsIgnoreCase("entity")) {
                                            region.disallowEntity(nome);
                                            player.sendSystemMessage(Component.literal("Entidade '" + nome + "' foi removida da lista de permissões da região '" + regionName + "'."));
                                        } else {
                                            player.sendSystemMessage(Component.literal("Tipo inválido. Use 'player' ou 'entity'."));
                                        }
                                        saveRegionsToFile();
                                    } else {
                                        player.sendSystemMessage(Component.literal("Você não é o dono desta região e não pode modificar permissões de entrada."));
                                    }
                                } else {
                                    player.sendSystemMessage(Component.literal("A região '" + regionName + "' não existe."));
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
                            player.sendSystemMessage(Component.literal("Flags da região '" + regionName + "':"));
                            for (Map.Entry<String, Boolean> flagEntry : region.getFlags().entrySet()) {
                                String flag = flagEntry.getKey();
                                boolean value = flagEntry.getValue();
                                player.sendSystemMessage(Component.literal("- " + flag + ": " + value));
                            }
                            
                            player.sendSystemMessage(Component.literal("Flags de membros:"));
                            for (Map.Entry<String, Boolean> flagEntry : region.memberFlags.entrySet()) {
                                String flag = flagEntry.getKey();
                                boolean value = flagEntry.getValue();
                                player.sendSystemMessage(Component.literal("- " + flag + ": " + value));
                            }
                            
                            if (!region.blockedCommands.isEmpty()) {
                                player.sendSystemMessage(Component.literal("Comandos bloqueados:"));
                                for (String cmd : region.blockedCommands) {
                                    player.sendSystemMessage(Component.literal("- " + cmd));
                                }
                            }
                            
                            if (region.enterMessage != null && !region.enterMessage.isEmpty()) {
                                player.sendSystemMessage(Component.literal("Mensagem de entrada: " + region.enterMessage));
                            }
                            if (region.exitMessage != null && !region.exitMessage.isEmpty()) {
                                player.sendSystemMessage(Component.literal("Mensagem de saída: " + region.exitMessage));
                            }
                            
                            if (!region.allowedEntities.isEmpty()) {
                                player.sendSystemMessage(Component.literal("Entidades permitidas:"));
                                for (String entity : region.allowedEntities) {
                                    player.sendSystemMessage(Component.literal("- " + entity));
                                }
                            }
                            
                            if (!region.allowedPlayers.isEmpty()) {
                                player.sendSystemMessage(Component.literal("Jogadores permitidos:"));
                                for (String pl : region.allowedPlayers) {
                                    player.sendSystemMessage(Component.literal("- " + pl));
                                }
                            }
                            return 1;
                        }
                    }

                    player.sendSystemMessage(Component.literal("Você não está em uma região protegida."));
                    return 0;
                })
            )
            .then(Commands.literal("listarFlags")
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();

                    List<String> flags = List.of(
                        "build", "block-place", "block-break", "destroy", "interact", "use", 
                        "chest-access", "sign", "item-frame-rotation", "item-frame-remove", 
                        "item-frame-break", "use-anvil", "container-access", "mob-spawning", 
                        "mob-damage", "pvp", "damage-animals", "creeper-explosion", 
                        "other-explosion", "tnt", "fire-spread", "send-chat", "receive-chat", 
                        "teleport", "entry", "invincible", "item-pickup", "item-drop", "mod-interaction"
                    );

                    player.sendSystemMessage(Component.literal("Flags disponíveis:"));
                    for (String flag : flags) {
                        player.sendSystemMessage(Component.literal("- " + flag));
                    }
                    
                    player.sendSystemMessage(Component.literal("Flags de membros disponíveis:"));
                    player.sendSystemMessage(Component.literal("- build"));
                    player.sendSystemMessage(Component.literal("- block-break"));
                    player.sendSystemMessage(Component.literal("- block-place"));
                    player.sendSystemMessage(Component.literal("- interact"));
                    player.sendSystemMessage(Component.literal("- sign"));
                    player.sendSystemMessage(Component.literal("- use"));
                    player.sendSystemMessage(Component.literal("- item-frame-rotation"));

                    return 1;
                })
            )
            .then(Commands.literal("banirItem")
                .then(Commands.argument("itemId", StringArgumentType.greedyString())
                    .executes(context -> {
                        String itemId = StringArgumentType.getString(context, "itemId");
                        Player player = context.getSource().getPlayerOrException();
                        
                        if (!itemId.contains(":")) {
                            player.sendSystemMessage(Component.literal("Formato inválido. Use modid:itemname (ex: create:drill)"));
                            return 0;
                        }
                        
                        bannedModItems.add(itemId.toLowerCase());
                        saveBannedItems();
                        player.sendSystemMessage(Component.literal("Item '" + itemId + "' foi banido de regiões protegidas."));
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
                            player.sendSystemMessage(Component.literal("Item '" + itemId + "' foi permitido em regiões protegidas."));
                        } else {
                            player.sendSystemMessage(Component.literal("Item '" + itemId + "' não estava na lista de banidos."));
                        }
                        return 1;
                    })
                )
            )
            .then(Commands.literal("listarItensBanidos")
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    
                    if (bannedModItems.isEmpty()) {
                        player.sendSystemMessage(Component.literal("Nenhum item está banido no momento."));
                    } else {
                        player.sendSystemMessage(Component.literal("Itens banidos:"));
                        for (String item : bannedModItems) {
                            player.sendSystemMessage(Component.literal("- " + item));
                        }
                    }
                    return 1;
                })
            )
            .then(Commands.literal("listarFakePlayers")
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    
                    if (detectedFakePlayers.isEmpty()) {
                        player.sendSystemMessage(Component.literal("Nenhum FakePlayer foi detectado ainda."));
                    } else {
                        player.sendSystemMessage(Component.literal("FakePlayers detectados:"));
                        for (String fakePlayer : detectedFakePlayers) {
                            player.sendSystemMessage(Component.literal("- " + fakePlayer));
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

        // Verificação específica para placas
        if (state.getBlock() instanceof SignBlock || state.getBlock() instanceof WallSignBlock) {
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "sign-break", player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("Você não pode quebrar placas nesta região protegida."));
                return;
            }
        }

        // Check for fake players (like from Create)
        if (player instanceof FakePlayer) {
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", null)) {
                event.setCanceled(true);
                return;
            }
        }

        // Check if using banned mod item
        if (isBannedModItem(player.getMainHandItem())) {
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("Este item não pode ser usado em regiões protegidas."));
                return;
            }
        }

        // Normal protection check
        if (isRegionProtected(pos)) {
            if (!isFlagEnabled(pos, "block-break", player) || !isFlagEnabled(pos, "destroy", player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("Você não pode quebrar blocos nesta região protegida."));
            }
        }
    }
	@SubscribeEvent
	public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
	    if (event.getEntity() instanceof Player) {
	        Player player = (Player) event.getEntity();
	        BlockPos pos = event.getPos();
	        // Obter o BlockState do bloco que está sendo colocado de forma diferente
	        BlockState state = event.getPlacedBlock();
	
	        // Verificação específica para placas
	        if (state.getBlock() instanceof SignBlock || state.getBlock() instanceof WallSignBlock) {
	            if (isRegionProtected(pos) && !isFlagEnabled(pos, "sign-place", player)) {
	                event.setCanceled(true);
	                player.sendSystemMessage(Component.literal("Você não pode colocar placas nesta região protegida."));
	                return;
	            }
	        }
	
	        // Check for fake players (like from Create)
	        if (player instanceof FakePlayer) {
	            if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", null)) {
	                event.setCanceled(true);
	                return;
	            }
	        }
	
	        // Check if using banned mod item
	        if (isBannedModItem(player.getMainHandItem())) {
	            if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", player)) {
	                event.setCanceled(true);
	                player.sendSystemMessage(Component.literal("Este item não pode ser usado em regiões protegidas."));
	                return;
	            }
	        }
	
	        if (isRegionProtected(pos)) {
	            if (!isFlagEnabled(pos, "block-place", player) || !isFlagEnabled(pos, "build", player)) {
	                event.setCanceled(true);
	                player.sendSystemMessage(Component.literal("Você não pode colocar blocos nesta região protegida."));
	            }
	        }
	    }
	}

	@SubscribeEvent
	public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
	    Player player = event.getEntity();
	
	    if (player.getMainHandItem().getItem() == Items.GOLDEN_AXE) {
	        if (event.getHand() == InteractionHand.MAIN_HAND) {
	            firstPoint.put(player, event.getPos());
	            player.sendSystemMessage(Component.literal("Primeiro ponto selecionado: " + event.getPos()));
	            event.setCanceled(true);
	        }
	        return;
	    }
	
	    BlockPos pos = event.getPos();
	    BlockState state = event.getLevel().getBlockState(pos);
	    BlockEntity blockEntity = event.getLevel().getBlockEntity(pos);
	
	    if (blockEntity instanceof MenuProvider && !(state.getBlock() instanceof AnvilBlock)) {
	        if (isRegionProtected(pos) && !isFlagEnabled(pos, "container-access", player)) {
	            event.setCanceled(true);
	            player.sendSystemMessage(Component.literal("Você não pode acessar containers nesta região protegida."));
	            return;
	        }
	    }
	
	    // Check interaction with anvils
	    if (state.getBlock() instanceof AnvilBlock) {
	        if (isRegionProtected(pos) && !isFlagEnabled(pos, "use-anvil", player)) {
	            event.setCanceled(true);
	            player.sendSystemMessage(Component.literal("Você não pode usar bigornas nesta região protegida."));
	            return;
	        }
	    }
	
	    // Check for fake players (like from Create)
	    if (player instanceof FakePlayer) {
	        if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", null)) {
	            event.setCanceled(true);
	            return;
	        }
	    }
	
	    // Check banned mod items
	    if (isBannedModItem(player.getMainHandItem())) {
	        if (isRegionProtected(pos) && !isFlagEnabled(pos, "mod-interaction", player)) {
	            event.setCanceled(true);
	            player.sendSystemMessage(Component.literal("Este item não pode ser usado em regiões protegidas."));
	        }
	    }
	}

    @SubscribeEvent
    public static void onPlayerInteractLeft(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();

        if (player.getMainHandItem().getItem() == Items.GOLDEN_AXE) {
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                secondPoint.put(player, event.getPos());
                player.sendSystemMessage(Component.literal("Segundo ponto selecionado: " + event.getPos()));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onItemFrameInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof ItemFrame) {
            Player player = event.getEntity();
            BlockPos pos = event.getTarget().blockPosition();
            
            if (isRegionProtected(pos)) {
                Region region = getRegion(pos);
                
                if (player.isShiftKeyDown() && !isFlagEnabled(pos, "item-frame-remove", player)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("Você não pode remover itens de frames nesta região protegida."));
                    return;
                }
                
                if (!isFlagEnabled(pos, "item-frame-rotation", player)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("Você não pode girar itens em frames nesta região protegida."));
                }
            }
        }
    }
	
	@SubscribeEvent
	public static void onItemFrameBreak(PlayerInteractEvent.LeftClickBlock event) {
	    Player player = event.getEntity();
	    BlockPos pos = event.getPos();
	
	    // Find ItemFrame entities at the clicked position
	    List<ItemFrame> itemFrames = event.getLevel().getEntitiesOfClass(ItemFrame.class,
	        new AABB(pos)); // One block area
	
	    if (!itemFrames.isEmpty()) {
	        if (isRegionProtected(pos) && !isFlagEnabled(pos, "item-frame-break", player)) {
	            event.setCanceled(true);
	            player.sendSystemMessage(Component.literal("Você não pode quebrar item frames nesta região protegida."));
	        }
	    }
	}

    @SubscribeEvent
    public static void onProjectileDamage(LivingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof Projectile) {
            BlockPos pos = new BlockPos((int) event.getEntity().getX(), (int) event.getEntity().getY(), (int) event.getEntity().getZ());
            Player player = event.getSource().getEntity() instanceof Player ? (Player) event.getSource().getEntity() : null;

            if (isRegionProtected(pos)) {
                if (!isFlagEnabled(pos, "projeteis", player)) {
                    event.setCanceled(true);
                    if (player != null) {
                        player.sendSystemMessage(Component.literal("Projéteis não podem causar dano nesta região protegida."));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Vec3 explosionVec = event.getExplosion().getPosition();
        BlockPos explosionPos = new BlockPos((int) explosionVec.x, (int) explosionVec.y, (int) explosionVec.z);

        if (isRegionProtected(explosionPos)) {
            if (!isFlagEnabled(explosionPos, "other-explosion", null)) {
                event.getAffectedBlocks().clear();
                event.getAffectedEntities().clear();
            }
        } else {
            List<BlockPos> affectedBlocks = new ArrayList<>(event.getAffectedBlocks());
            for (BlockPos pos : affectedBlocks) {
                if (isRegionProtected(pos)) {
                    event.getAffectedBlocks().remove(pos);
                }
            }

            List<LivingEntity> affectedEntities = new ArrayList<>();
            for (Entity entity : event.getAffectedEntities()) {
                if (entity instanceof LivingEntity) {
                    affectedEntities.add((LivingEntity) entity);
                }
            }

            for (LivingEntity entity : affectedEntities) {
                if (isRegionProtected(entity.blockPosition())) {
                    event.getAffectedEntities().remove(entity);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        BlockPos pos = event.getPos();
        
        if (isRegionProtected(pos)) {
            String className = event.getEntity().getClass().getName().toLowerCase();
            if (className.contains("com.simibubi.create") || className.contains("create")) {
                if (!isFlagEnabled(pos, "entidade_quebra", null)) {
                    event.setCanceled(true);
                    Level world = event.getEntity().getCommandSenderWorld();
                    world.playSound(null, pos, SoundEvents.ANVIL_LAND, 
                        SoundSource.BLOCKS, 0.5f, 1.0f);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onSignInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        
        if (state.getBlock() instanceof SignBlock || state.getBlock() instanceof WallSignBlock) {
            if (isRegionProtected(pos)) {
                Region region = getRegion(pos);
                if (!isFlagEnabled(pos, "interact", player) && 
                    !(region != null && region.hasMemberFlag(player.getName().getString(), "interact"))) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("Você não pode interagir com placas nesta região protegida."));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCreeperExplosion(ExplosionEvent.Start event) {
        Entity source = event.getExplosion().getExploder();
        if (source instanceof Creeper) {
            BlockPos pos = new BlockPos((int)source.getX(), (int)source.getY(), (int)source.getZ());
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "creeper-explosion", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEnderDragonDestroy(LivingDestroyBlockEvent event) {
        if (event.getEntity() instanceof EnderDragon) {
            BlockPos pos = event.getPos();
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "enderdragon-block-damage", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onGhastFireball(LivingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof Fireball) {
            BlockPos pos = new BlockPos((int)event.getEntity().getX(), (int)event.getEntity().getY(), (int)event.getEntity().getZ());
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "ghast-fireball", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onFireSpread(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().getBlock() == Blocks.FIRE) {
            BlockPos pos = event.getPos();
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "fire-spread", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEndermanGrief(LivingDestroyBlockEvent event) {
        if (event.getEntity() instanceof EnderMan) {
            BlockPos pos = event.getPos();
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "enderman-grief", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onSnowmanTrails(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof SnowGolem) {
            BlockPos pos = event.getPos();
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "snowman-trails", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRavagerGrief(LivingDestroyBlockEvent event) {
        if (event.getEntity() instanceof Ravager) {
            BlockPos pos = event.getPos();
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "ravager-grief", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMobDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Monster && event.getEntity() instanceof Player) {
            BlockPos pos = new BlockPos((int)event.getEntity().getX(), (int)event.getEntity().getY(), (int)event.getEntity().getZ());
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "mob-damage", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMobSpawn(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Mob) {
            BlockPos pos = event.getEntity().blockPosition();
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "mob-spawning", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onWitherDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof WitherBoss) {
            BlockPos pos = new BlockPos((int)event.getEntity().getX(), (int)event.getEntity().getY(), (int)event.getEntity().getZ());
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "wither-damage", null)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerMove(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) return;
        
        Player player = event.player;
        BlockPos currentPos = player.blockPosition();
        Region currentRegion = getRegion(currentPos);
        
        if (currentRegion != null) {
            if (!isFlagEnabled(currentPos, "entry", player) || 
                !currentRegion.isPlayerAllowed(player.getName().getString())) {
                
                if (!player.hasPermissions(2)) {
                    Vec3 safeSpot = findSafeSpotOutsideRegion(currentRegion, player);
                    player.teleportTo(safeSpot.x, safeSpot.y, safeSpot.z);
                    player.sendSystemMessage(Component.literal("Você não tem permissão para entrar nesta área protegida."));
                    return;
                }
            }
            
            if (!player.getPersistentData().contains("lastRegion")) {
                player.getPersistentData().putString("lastRegion", "");
            }
            
            String lastRegion = player.getPersistentData().getString("lastRegion");
            if (!lastRegion.equals(currentRegion.toString())) {
                if (currentRegion.enterMessage != null && !currentRegion.enterMessage.isEmpty()) {
                    player.sendSystemMessage(Component.literal(currentRegion.enterMessage));
                }
                player.getPersistentData().putString("lastRegion", currentRegion.toString());
            }
        } else {
            if (player.getPersistentData().contains("lastRegion") && 
                !player.getPersistentData().getString("lastRegion").isEmpty()) {
                
                String lastRegion = player.getPersistentData().getString("lastRegion");
                Region region = protectedRegions.values().stream()
                    .filter(r -> r.toString().equals(lastRegion))
                    .findFirst().orElse(null);
                
                if (region != null && region.exitMessage != null && !region.exitMessage.isEmpty()) {
                    player.sendSystemMessage(Component.literal(region.exitMessage));
                }
                
                player.getPersistentData().putString("lastRegion", "");
            }
        }
    }

    private static Vec3 findSafeSpotOutsideRegion(Region region, Player player) {
        BlockPos center = new BlockPos(
            (region.start.getX() + region.end.getX()) / 2,
            (region.start.getY() + region.end.getY()) / 2,
            (region.start.getZ() + region.end.getZ()) / 2
        );
        
        for (int i = 1; i <= 10; i++) {
            BlockPos[] testPositions = {
                center.offset(i, 0, 0),
                center.offset(-i, 0, 0),
                center.offset(0, 0, i),
                center.offset(0, 0, -i),
                center.offset(i, 0, i),
                center.offset(-i, 0, -i),
                center.offset(i, 0, -i),
                center.offset(-i, 0, i)
            };
            
            for (BlockPos pos : testPositions) {
                if (!region.isWithinRegion(pos) && player.level().isEmptyBlock(pos) && player.level().isEmptyBlock(pos.above())) {
                    return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                }
            }
        }
        
        return player.position();
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (event.getParseResults() == null || event.getParseResults().getContext().getSource() == null) {
            return;
        }
        
        String command = event.getParseResults().getReader().getString();
        String commandName = command.split(" ")[0].replace("/", "").toLowerCase();
        
        if (event.getParseResults().getContext().getSource().getEntity() instanceof Player) {
            Player player = (Player) event.getParseResults().getContext().getSource().getEntity();
            BlockPos pos = player.blockPosition();
            
            if (isRegionProtected(pos)) {
                Region region = getRegion(pos);
                
                if (region != null && region.isCommandBlocked(commandName) && 
                    !isFlagEnabled(pos, "command", player) && !player.hasPermissions(2)) {
                    
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("Este comando está bloqueado nesta região protegida."));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onTeleport(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        BlockPos pos = player.blockPosition();
        
        if (isRegionProtected(pos)) {
            Region region = getRegion(pos);
            
            if (region != null && !region.isPlayerAllowed(player.getName().getString()) && 
                !isFlagEnabled(pos, "teleport", player) && !player.hasPermissions(2)) {
                
                Vec3 safeSpot = findSafeSpotOutsideRegion(region, player);
                player.teleportTo(safeSpot.x, safeSpot.y, safeSpot.z);
                player.sendSystemMessage(Component.literal("Você não tem permissão para entrar nesta área protegida."));
            }
        }
    }

    @SubscribeEvent
    public static void onEntitySpawn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        BlockPos pos = player.blockPosition();
        
        if (isRegionProtected(pos)) {
            Region region = getRegion(pos);
            
            if (region != null && !region.isPlayerAllowed(player.getName().getString()) && 
                !player.hasPermissions(2)) {
                
                Vec3 safeSpot = findSafeSpotOutsideRegion(region, player);
                player.teleportTo(safeSpot.x, safeSpot.y, safeSpot.z);
                player.sendSystemMessage(Component.literal("Você não tem permissão para entrar nesta área protegida."));
            }
        }
    }
    
	@SubscribeEvent
	public static void onPlayerChat(net.minecraftforge.event.ServerChatEvent event) {
	    Player player = event.getPlayer();
	    BlockPos pos = player.blockPosition();
	
	    if (isRegionProtected(pos)) {
	        if (!isFlagEnabled(pos, "send-chat", player)) {
	            player.sendSystemMessage(Component.literal("Você não pode enviar mensagens nesta região."));
	            event.setCanceled(true);
	            return;
	        }
	
	        Region region = getRegion(pos);
	        if (region != null) {
	            String message = "<" + player.getName().getString() + "> " + event.getMessage();
	            event.setCanceled(true); // Cancel default message sending
	
	            // Send message only to authorized players
	            for (ServerPlayer target : player.getServer().getPlayerList().getPlayers()) {
	                BlockPos targetPos = target.blockPosition();
	                if (!isRegionProtected(targetPos) || isFlagEnabled(targetPos, "receive-chat", target)) {
	                    target.sendSystemMessage(Component.literal(message));
	                }
	            }
	        }
	    }
	}

    @SubscribeEvent
    public static void onPvP(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player && 
            event.getEntity() instanceof Player) {
            
            Player attacker = (Player) event.getSource().getEntity();
            Player victim = (Player) event.getEntity();
            BlockPos pos = victim.blockPosition();
            
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "pvp", attacker)) {
                event.setCanceled(true);
                attacker.sendSystemMessage(Component.literal("PvP não é permitido nesta região."));
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
                            owner.sendSystemMessage(Component.literal("Você não pode quebrar blocos nesta região protegida."));
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
                    if (!isFlagEnabled(pos, "entidade_dano", owner)) {
                        this.level().broadcastEntityEvent(this, (byte) 3);
                        this.discard();
                        if (owner != null) {
                            owner.sendSystemMessage(Component.literal("Você não pode causar dano a entidades nesta região protegida."));
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
