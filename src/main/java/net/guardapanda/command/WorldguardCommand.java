package net.guardapanda.command;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.ItemPickupEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
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
                            flags.put("projeteis2", false); // Impede projeteis
                            flags.put("projeteis", false); // Impede projeteis
                            flags.put("magia", false); //impede ataque magicos  
                            flags.put("Explosões", false); //impede contra TODO:as as explosões
                            flags.put("entidade_dano", false); //impede contra TODO:as as explosões

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
	    // Verifica se o dano foi causado por um projétil de qualquer tipo
	    if (event.getSource().getDirectEntity() instanceof Projectile) {  // Verifica se a fonte do dano é um projétil
	        // Obtém a posição do dano (convertendo as coordenadas para int)
	        BlockPos pos = new BlockPos((int) event.getEntity().getX(), (int) event.getEntity().getY(), (int) event.getEntity().getZ());
	        Player player = event.getSource().getEntity() instanceof Player ? (Player) event.getSource().getEntity() : null;
	
	        // Verifica se a posição está dentro de uma região protegida
	        if (isRegionProtected(pos)) {
	            // Se for um projétil e estiver em uma região protegida, cancela o dano
	            if (!isFlagEnabled(pos, "Projeteis2", player)) {
	                event.setCanceled(true);  // Cancela o evento de dano
	                if (player != null) {
	                    player.sendSystemMessage(Component.literal("Projétil não pode causar dano nesta região protegida."));
	                }
	            }
	        }
	    }
	}
	
	@SubscribeEvent
	public static void onEntityDamageCancel(LivingDamageEvent event) {
	    // Verifica se a fonte do dano é uma entidade (pode ser jogador, mob, projétil, etc.)
	    if (event.getSource().getEntity() != null) {
	        // Obtém a posição do dano (convertendo as coordenadas para int)
	        BlockPos pos = new BlockPos((int) event.getEntity().getX(), (int) event.getEntity().getY(), (int) event.getEntity().getZ());
	        Player player = event.getSource().getEntity() instanceof Player ? (Player) event.getSource().getEntity() : null;
	
	        // Verifica se a posição está dentro de uma região protegida
	        if (isRegionProtected(pos)) {
	            // Se a flag "dano" estiver desabilitada para a região, cancela o dano
	            if (!isFlagEnabled(pos, "projeteis", player)) {
	                event.setCanceled(true);
	                // Envia uma mensagem para a entidade que está sendo danificada
	                event.getEntity().sendSystemMessage(Component.literal("Você não pode ser danificado nesta região protegida."));
	            }
	        }
	    }
	}
	
@SubscribeEvent
public static void onPlayerInteract(PlayerInteractEvent event) {
    Player player = event.getEntity();
    BlockPos pos = event.getPos();

    // Verifica se o jogador é nulo
    if (player == null) {
        return;  // Se o jogador for nulo, simplesmente sai do método
    }

    // Verifica se a região é protegida e se a flag "interact" está desativada
    if (isRegionProtected(pos) && !isFlagEnabled(pos, "interact", player)) {
        event.setCanceled(true);  // Cancela a interação
        player.sendSystemMessage(Component.literal("Interação não permitida nesta região."));
    }
}

private static boolean isRegionProtected(BlockPos pos) {
    return protectedRegions.values().stream().anyMatch(region -> region.isWithinRegion(pos));
}



private static boolean isFlagEnabled(BlockPos pos, String flag, Player player) {
    if (player == null) {
        return false;  // Se o jogador for null, retorna false imediatamente
    }

    for (Region region : protectedRegions.values()) {
        if (region.isWithinRegion(pos)) {
            // Se o jogador for o dono ou um membro, verifica a flag
            if (region.isOwner(player.getName().getString())) {
                return true;  // Dono sempre tem permissão, independentemente da flag
            } else if (region.isMember(player.getName().getString())) {
                return region.getFlags().getOrDefault(flag, false);  // Membro verifica as flags
            }
        }
    }
    return false;  // Se não for dono nem membro, a flag será considerada como desabilitada
}






@SubscribeEvent
public static void onExplosion(ExplosionEvent.Detonate event) {
    // Obtém a posição da explosão como Vec3
    Vec3 explosionVec = event.getExplosion().getPosition();
    
    // Converte a posição de Vec3 para BlockPos (com coordenadas inteiras)
    BlockPos explosionPos = new BlockPos((int) explosionVec.x, (int) explosionVec.y, (int) explosionVec.z);

    // Obtém o mundo diretamente do evento
    Level world = event.getLevel();  // Acesso correto ao mundo via evento

    // Obtém a lista de blocos a serem destruídos pela explosão
    List<BlockPos> toBlow = new ArrayList<>(event.getExplosion().getToBlow());

    // Lista para armazenar os blocos a serem removidos
    List<BlockPos> toRemove = new ArrayList<>();

    for (BlockPos pos : toBlow) {
        // Verifica se o bloco está dentro de uma região protegida
        if (isRegionProtected(pos)) {
            // Verifica se a flag de explosões está desabilitada para a região
            if (!isFlagEnabled(pos, "Explosões", null)) {
                // Adiciona o bloco à lista de remoção
                toRemove.add(pos);
                System.out.println("Bloco marcado para remoção da explosão em região protegida: " + pos);
            }
        }
    }

    // Remove os blocos da lista original que foram marcados para remoção
    toBlow.removeAll(toRemove);

    // Atualiza a lista de blocos a serem destruídos
    event.getExplosion().getToBlow().clear();  // Limpa a lista original
    event.getExplosion().getToBlow().addAll(toBlow);  // Adiciona os blocos filtrados

    // Se todos os blocos a serem destruídos foram removidos, cancela a explosão
    if (toBlow.isEmpty()) {
        event.setCanceled(true);  // Cancela a explosão
        System.out.println("Explosão cancelada, nenhum bloco a ser destruído!");
    }
}


    @SubscribeEvent
    public static void onMagicDamageCancel(LivingDamageEvent event) {
        // Verifica se o dano é causado por um projétil mágico
        if (event.getSource().getDirectEntity() instanceof Projectile) {
            // Obtém a posição do dano
            BlockPos pos = new BlockPos((int) event.getEntity().getX(), (int) event.getEntity().getY(), (int) event.getEntity().getZ());
            Player player = event.getSource().getEntity() instanceof Player ? (Player) event.getSource().getEntity() : null;

            // Verifica se a origem do projétil é uma bola de fogo ou uma poção
			if (event.getSource().getDirectEntity() instanceof Projectile && 
			    (event.getSource().getDirectEntity() instanceof SmallFireball ||
			     event.getSource().getDirectEntity() instanceof LargeFireball ||
			     event.getSource().getDirectEntity() instanceof ThrownPotion)) {


                // Verifica se a posição está dentro de uma região protegida
                if (isRegionProtected(pos)) {
                    // Se a flag "magia" estiver desabilitada para a região, cancela o dano
                    if (!isFlagEnabled(pos, "magia", player)) {
                        event.setCanceled(true);
                        // Envia uma mensagem informando que o ataque mágico foi desativado na região
                        event.getEntity().sendSystemMessage(Component.literal("Ataques mágicos são desativados nesta região protegida."));
                    }
                }
            }
        }
    }



@SubscribeEvent
public static void onEntityDamage(LivingDamageEvent event) {
    // Verifica se a entidade que está recebendo dano é do tipo LivingEntity (pode ser qualquer entidade vivente)
    if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity) {
        net.minecraft.world.entity.LivingEntity entity = (net.minecraft.world.entity.LivingEntity) event.getEntity();
        BlockPos pos = entity.blockPosition();

        // Verifica se a região está protegida e se a flag "entidade_dano" está desativada
        if (isRegionProtected(pos) && !isFlagEnabled(pos, "entidade_dano", entity.getCommandSenderWorld().getNearestPlayer(entity, 10))) {
            event.setCanceled(true); // Cancela o dano
            entity.sendSystemMessage(Component.literal("Você não pode sofrer dano nesta região."));
        }

        // Verifica se o atacante é um jogador e se a flag "pvp" está desativada para o jogador
        if (event.getSource().getEntity() instanceof Player) {
            Player attacker = (Player) event.getSource().getEntity();

            if (isRegionProtected(attacker.blockPosition()) && !isFlagEnabled(attacker.blockPosition(), "pvp", attacker)) {
                event.setCanceled(true); // Cancela o dano
                attacker.sendSystemMessage(Component.literal("PvP está desativado nesta região."));
            }
        }
    }
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
        private String owner;  // Dono da região
        private Map<String, Boolean> members;  // Membros com permissões para construir/destruir

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



public boolean hasPermission(String playerName, String flag) {
    if (isOwner(playerName)) {
        return true;
    }

    if (flags.containsKey(flag)) {
        return flags.get(flag);
    }

    return false;
}

public boolean isOwner(String playerName) {
    return owner != null && owner.equals(playerName);
}


public boolean isMember(String playerName) {
    if (this.members == null) {
        this.members = new HashMap<>(); // Inicializa a lista de membros, caso seja nula
    }
    return members.containsKey(playerName); // Verifica se o jogador é um membro
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