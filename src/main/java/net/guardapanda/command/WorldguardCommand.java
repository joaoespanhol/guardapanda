package net.mcreator.guardapanda.command;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldguardCommand {

    private static final Map<String, Region> protectedRegions = new HashMap<>();
    private static final Map<Player, BlockPos> firstPoint = new HashMap<>();
    private static final Map<Player, BlockPos> secondPoint = new HashMap<>();
    private static final File regionFile = new File("protected_regions.json");

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
                            flags.put("build", false);    // Impede colocar blocos por padrão
                            flags.put("destroy", false);  // Impede quebrar blocos
                            flags.put("dano", false);     // Impede dano
                            flags.put("pvp", false);      // Impede PvP
                            flags.put("pocao", false);    // Impede o uso de poções
                            flags.put("dropar", false);   // Impede o drop de itens
                            flags.put("interact", false); // Impede interagir com itens da área protegida
                            flags.put("projeteis", false); // Impede projéteis
                            flags.put("magia", false);    // Impede ataques mágicos
                            flags.put("explosoes", false); // Impede explosões
                            flags.put("entidade_dano", false); // Impede dano causado por entidades
                            flags.put("entidade_quebra", false); // Impede entidades de quebrar blocos

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
        );
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();

        if (isRegionProtected(pos)) {
            if (!isFlagEnabled(pos, "destroy", player)) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("Este bloco está em uma região protegida e não pode ser quebrado."));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            BlockPos pos = event.getPos();

            if (isRegionProtected(pos)) {
                if (!isFlagEnabled(pos, "build", player)) {
                    event.setCanceled(true);
                    player.sendSystemMessage(Component.literal("Este bloco está em uma região protegida e não pode ser colocado."));
                }
            }
        }
    }

	@SubscribeEvent
	public static void onPlayerInteract(PlayerInteractEvent.RightClickBlock event) {
	    Player player = event.getEntity();
	
	    // Verifica se o jogador é um operador (OP)
	    if (!player.hasPermissions(2)) { // 2 é o nível de permissão de OP
	        return;
	    }
	
	    if (player.getMainHandItem().getItem() instanceof AxeItem) {
	        if (event.getHand() == InteractionHand.MAIN_HAND) {
	            firstPoint.put(player, event.getPos());
	            player.sendSystemMessage(Component.literal("Primeiro ponto selecionado: " + event.getPos()));
	            event.setCanceled(true);  // Impede ações padrão
	        }
	    }
	}
	
	@SubscribeEvent
	public static void onPlayerInteractLeft(PlayerInteractEvent.LeftClickBlock event) {
	    Player player = event.getEntity();
	
	    // Verifica se o jogador é um operador (OP)
	    if (!player.hasPermissions(2)) { // 2 é o nível de permissão de OP
	        return;
	    }
	
	    if (player.getMainHandItem().getItem() instanceof AxeItem) {
	        if (event.getHand() == InteractionHand.MAIN_HAND) {
	            secondPoint.put(player, event.getPos());
	            player.sendSystemMessage(Component.literal("Segundo ponto selecionado: " + event.getPos()));
	            event.setCanceled(true);  // Impede ações padrão
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
            if (!isFlagEnabled(explosionPos, "explosoes", null)) {
                // Clear the list of affected blocks to prevent block damage
                event.getAffectedBlocks().clear();
                // Clear the list of affected entities to prevent entity damage
                event.getAffectedEntities().clear();
                System.out.println("Explosão cancelada em região protegida: " + explosionPos);
            }
        }

        // Verifica se a explosão foi causada por uma entidade (mob)
        DamageSource damageSource = event.getExplosion().getDamageSource();
        if (damageSource != null && damageSource.getEntity() != null) {
            LivingEntity sourceEntity = (LivingEntity) damageSource.getEntity();
            BlockPos entityPos = sourceEntity.blockPosition();

            if (isRegionProtected(entityPos)) {
                if (!isFlagEnabled(entityPos, "explosoes", null)) {
                    // Clear the list of affected blocks to prevent block damage
                    event.getAffectedBlocks().clear();
                    // Clear the list of affected entities to prevent entity damage
                    event.getAffectedEntities().clear();
                    System.out.println("Explosão causada por entidade cancelada em região protegida: " + entityPos);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity entity = (LivingEntity) event.getEntity();
            BlockPos pos = entity.blockPosition();

            if (isRegionProtected(pos)) {
                if (!isFlagEnabled(pos, "entidade_dano", entity.getCommandSenderWorld().getNearestPlayer(entity, 10))) {
                    event.setCanceled(true);
                    entity.sendSystemMessage(Component.literal("Você não pode sofrer dano nesta região."));
                }
            }

            if (event.getSource().getEntity() instanceof Player) {
                Player attacker = (Player) event.getSource().getEntity();

                if (isRegionProtected(attacker.blockPosition()) && !isFlagEnabled(attacker.blockPosition(), "pvp", attacker)) {
                    event.setCanceled(true);
                    attacker.sendSystemMessage(Component.literal("PvP está desativado nesta região."));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEntityBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null && isRegionProtected(event.getPos())) {
            if (!isFlagEnabled(event.getPos(), "entidade_quebra", null)) {
                event.setCanceled(true);
                System.out.println("Entidade impedida de quebrar bloco em região protegida: " + event.getPos());
            }
        }
    }

    private static boolean isRegionProtected(BlockPos pos) {
        return protectedRegions.values().stream().anyMatch(region -> region.isWithinRegion(pos));
    }

    private static boolean isFlagEnabled(BlockPos pos, String flag, Player player) {
        if (player == null) {
            return false;
        }

        for (Region region : protectedRegions.values()) {
            if (region.isWithinRegion(pos)) {
                if (region.isOwner(player.getName().getString())) {
                    return true;
                } else if (region.isMember(player.getName().getString())) {
                    return region.getFlags().getOrDefault(flag, false);
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

    private static class Region {
        private BlockPos start;
        private BlockPos end;
        private Map<String, Boolean> flags;
        private String owner;
        private Map<String, Boolean> members;

        public Region(BlockPos start, BlockPos end, Map<String, Boolean> flags, String owner) {
            this.start = start;
            this.end = end;
            this.flags = flags;
            this.owner = owner;
            this.members = new HashMap<>();
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
    }
}