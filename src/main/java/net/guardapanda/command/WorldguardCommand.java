package net.guardapanda.command;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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


@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldguardCommand {

    private static final Map<String, Region> protectedRegions = new HashMap<>();
    private static final Map<Player, BlockPos> firstPoint = new HashMap<>();
    private static final Map<Player, BlockPos> secondPoint = new HashMap<>();
    private static final File regionFile = new File("protected_regions.json");

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
        private Set<String> allowedPlayers;

        public Region(BlockPos start, BlockPos end, Map<String, Boolean> flags, String owner) {
            this.start = start;
            this.end = end;
            this.flags = flags;
            this.owner = owner;
            this.members = new HashMap<>();
            this.memberFlags = new HashMap<>();
            this.blockedCommands = new HashSet<>();
            this.enterMessage = "";
            this.exitMessage = "";
            this.allowedEntities = new HashSet<>();
            this.allowedPlayers = new HashSet<>();
            
            // Flags padrão para membros
            this.memberFlags.put("build", false);
            this.memberFlags.put("destroy", false);
            this.memberFlags.put("interact", false);
            this.memberFlags.put("sign", false);
            
            // Inicializa todas as flags com valores padrão
            this.flags.putIfAbsent("sign", false);
            this.flags.putIfAbsent("creeper-explosion", false);
            this.flags.putIfAbsent("enderdragon-block-damage", false);
            this.flags.putIfAbsent("ghast-fireball", false);
            this.flags.putIfAbsent("other-explosion", false);
            this.flags.putIfAbsent("fire-spread", false);
            this.flags.putIfAbsent("enderman-grief", false);
            this.flags.putIfAbsent("snowman-trails", false);
            this.flags.putIfAbsent("ravager-grief", false);
            this.flags.putIfAbsent("mob-damage", false);
            this.flags.putIfAbsent("mob-spawning", false);
            this.flags.putIfAbsent("wither-damage", false);
            this.flags.putIfAbsent("entity-painting-destroy", false);
            this.flags.putIfAbsent("entity-item-frame-destroy", false);
            this.flags.putIfAbsent("build", false);
            this.flags.putIfAbsent("interact", false);
            this.flags.putIfAbsent("block-break", false);
            this.flags.putIfAbsent("block-place", false);
            this.flags.putIfAbsent("use", false);
            this.flags.putIfAbsent("damage-animals", false);
            this.flags.putIfAbsent("chest-access", false);
            this.flags.putIfAbsent("ride", false);
            this.flags.putIfAbsent("pvp", false);
            this.flags.putIfAbsent("sleep", false);
            this.flags.putIfAbsent("respawn-anchors", false);
            this.flags.putIfAbsent("tnt", false);
            this.flags.putIfAbsent("vehicle-place", false);
            this.flags.putIfAbsent("vehicle-destroy", false);
            this.flags.putIfAbsent("lighter", false);
            this.flags.putIfAbsent("block-trampling", false);
            this.flags.putIfAbsent("frosted-ice-form", false);
            this.flags.putIfAbsent("item-frame-rotation", false);
            this.flags.putIfAbsent("firework-damage", false);
            this.flags.putIfAbsent("use-anvil", false);
            this.flags.putIfAbsent("use-dripleaf", false);
            this.flags.putIfAbsent("item-pickup", true);
            this.flags.putIfAbsent("item-drop", true);
            this.flags.putIfAbsent("exp-drops", true);
            this.flags.putIfAbsent("invincible", false);
            this.flags.putIfAbsent("fall-damage", true);
            this.flags.putIfAbsent("pistons", true);
            this.flags.putIfAbsent("send-chat", true);
            this.flags.putIfAbsent("receive-chat", true);
            this.flags.putIfAbsent("potion-splash", true);
            this.flags.putIfAbsent("teleport", false);
            this.flags.putIfAbsent("command", false);
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
            this.enterMessage = message;
        }
        
        public void setExitMessage(String message) {
            this.exitMessage = message;
        }
        
        public void allowEntity(String entityName) {
            allowedEntities.add(entityName.toLowerCase());
        }
        
        public void disallowEntity(String entityName) {
            allowedEntities.remove(entityName.toLowerCase());
        }
        
        public void allowPlayer(String playerName) {
            allowedPlayers.add(playerName.toLowerCase());
        }
        
        public void disallowPlayer(String playerName) {
            allowedPlayers.remove(playerName.toLowerCase());
        }
        
        public boolean isEntityAllowed(String entityName) {
            return allowedEntities.isEmpty() || allowedEntities.contains(entityName.toLowerCase());
        }
        
        public boolean isPlayerAllowed(String playerName) {
            return allowedPlayers.isEmpty() || allowedPlayers.contains(playerName.toLowerCase());
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
    
        for (Region region : protectedRegions.values()) {
            if (region.isWithinRegion(pos)) {
                if (!region.isPlayerAllowed(player.getName().getString())) {
                    return false;
                }
                
                if (region.isOwner(player.getName().getString())) {
                    return true;
                } else if (region.isMember(player.getName().getString())) {
                    return region.hasMemberFlag(player.getName().getString(), flag) || 
                           region.getFlags().getOrDefault(flag, false);
                }
            }
        }
        return false;
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
                protectedRegions.clear();
                protectedRegions.putAll(gson.fromJson(reader, type));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static {
        loadRegionsFromFile();
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
                            
                            if (!region.enterMessage.isEmpty()) {
                                player.sendSystemMessage(Component.literal("Mensagem de entrada: " + region.enterMessage));
                            }
                            if (!region.exitMessage.isEmpty()) {
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
                        "build", "destroy", "dano", "pvp", "pocao", "dropar", "interact", "projeteis", "magia", "explosoes", 
                        "entidade_dano", "entidade_quebra", "teleport", "command", "sign", "creeper-explosion", 
                        "enderdragon-block-damage", "ghast-fireball", "other-explosion", "fire-spread", "enderman-grief",
                        "snowman-trails", "ravager-grief", "mob-damage", "mob-spawning", "wither-damage",
                        "entity-painting-destroy", "entity-item-frame-destroy", "block-break", "block-place", "use",
                        "damage-animals", "chest-access", "ride", "sleep", "respawn-anchors", "tnt", "vehicle-place",
                        "vehicle-destroy", "lighter", "block-trampling", "frosted-ice-form", "item-frame-rotation",
                        "firework-damage", "use-anvil", "use-dripleaf", "item-pickup", "item-drop", "exp-drops",
                        "invincible", "fall-damage", "pistons", "send-chat", "receive-chat", "potion-splash"
                    );

                    player.sendSystemMessage(Component.literal("Flags disponíveis:"));
                    for (String flag : flags) {
                        player.sendSystemMessage(Component.literal("- " + flag));
                    }
                    
                    player.sendSystemMessage(Component.literal("Flags de membros disponíveis:"));
                    player.sendSystemMessage(Component.literal("- build"));
                    player.sendSystemMessage(Component.literal("- destroy"));
                    player.sendSystemMessage(Component.literal("- interact"));
                    player.sendSystemMessage(Component.literal("- sign"));

                    return 1;
                })
            )
        );
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();

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

        if (!player.hasPermissions(2)) {
            return;
        }

        if (player.getMainHandItem().getItem() == Items.GOLDEN_AXE) {
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                firstPoint.put(player, event.getPos());
                player.sendSystemMessage(Component.literal("Primeiro ponto selecionado: " + event.getPos()));
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteractLeft(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();

        if (!player.hasPermissions(2)) {
            return;
        }

        if (player.getMainHandItem().getItem() == Items.GOLDEN_AXE) {
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                secondPoint.put(player, event.getPos());
                player.sendSystemMessage(Component.literal("Segundo ponto selecionado: " + event.getPos()));
                event.setCanceled(true);
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
            // Verifica se é uma entidade cinética do Create
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

    private static boolean isCreateKineticEntity(Entity entity) {
        if (entity == null) return false;
        String className = entity.getClass().getName().toLowerCase();
        return className.contains("com.simibubi.create") || className.contains("create");
    }
    


    @SubscribeEvent
    public static void onSignInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        
        if (state.getBlock() instanceof SignBlock || state.getBlock() instanceof WallSignBlock) {
            if (isRegionProtected(pos) && !isFlagEnabled(pos, "sign", player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("Você não pode interagir com placas nesta região protegida."));
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
            if (!currentRegion.isPlayerAllowed(player.getName().getString()) && !player.hasPermissions(2)) {
                Vec3 safeSpot = findSafeSpotOutsideRegion(currentRegion, player);
                player.teleportTo(safeSpot.x, safeSpot.y, safeSpot.z);
                player.sendSystemMessage(Component.literal("Você não tem permissão para entrar nesta área protegida."));
                return;
            }
            
            if (!player.getPersistentData().contains("lastRegion")) {
                player.getPersistentData().putString("lastRegion", "");
            }
            
            String lastRegion = player.getPersistentData().getString("lastRegion");
            if (!lastRegion.equals(currentRegion.toString())) {
                if (!currentRegion.enterMessage.isEmpty()) {
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
                
                if (region != null && !region.exitMessage.isEmpty()) {
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