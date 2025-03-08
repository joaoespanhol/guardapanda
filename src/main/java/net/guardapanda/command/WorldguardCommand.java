package net.guardapanda.command;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.BlockEvent;
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
import com.google.gson.reflect.TypeToken;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid = "guardapanda", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WorldguardCommand {

    private static final Map<String, Region> protectedRegions = new HashMap<>();
    private static final Map<Player, BlockPos> firstPoint = new HashMap<>();
    private static final Map<Player, BlockPos> secondPoint = new HashMap<>();
    private static final File regionFile = new File("protected_regions.json");

    // Classe interna para representar uma região protegida
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

    // Métodos para verificar proteção e flags
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

    // Métodos para salvar e carregar regiões protegidas
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

    // Comandos do WorldGuard
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
            .then(Commands.literal("flagsAtuais") // Novo comando para ver as flags da região atual
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    BlockPos playerPos = player.blockPosition();

                    // Verifica em qual região o jogador está
                    for (Map.Entry<String, Region> entry : protectedRegions.entrySet()) {
                        String regionName = entry.getKey();
                        Region region = entry.getValue();

                        if (region.isWithinRegion(playerPos)) {
                            // Exibe as flags da região
                            player.sendSystemMessage(Component.literal("Flags da região '" + regionName + "':"));
                            for (Map.Entry<String, Boolean> flagEntry : region.getFlags().entrySet()) {
                                String flag = flagEntry.getKey();
                                boolean value = flagEntry.getValue();
                                player.sendSystemMessage(Component.literal("- " + flag + ": " + value));
                            }
                            return 1;
                        }
                    }

                    // Se o jogador não estiver em uma região protegida
                    player.sendSystemMessage(Component.literal("Você não está em uma região protegida."));
                    return 0;
                })
            )
            .then(Commands.literal("listarFlags") // Novo comando para listar todas as flags disponíveis
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrException();

                    // Lista de todas as flags disponíveis
                    List<String> flags = List.of(
                        "build", "destroy", "dano", "pvp", "pocao", "dropar", "interact", "projeteis", "magia", "explosoes", "entidade_dano", "entidade_quebra"
                    );

                    // Exibe as flags
                    player.sendSystemMessage(Component.literal("Flags disponíveis:"));
                    for (String flag : flags) {
                        player.sendSystemMessage(Component.literal("- " + flag));
                    }

                    return 1;
                })
            )
        );
    }

    // Eventos do WorldGuard
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

        // Verifica se o jogador está segurando um machado de ouro
        if (player.getMainHandItem().getItem() == Items.GOLDEN_AXE) {
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

        // Verifica se o jogador está segurando um machado de ouro
        if (player.getMainHandItem().getItem() == Items.GOLDEN_AXE) {
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

        // Verifica se a explosão ocorre dentro de uma região protegida
        if (isRegionProtected(explosionPos)) {
            if (!isFlagEnabled(explosionPos, "explosoes", null)) {
                // Cancela a explosão completamente
                event.getAffectedBlocks().clear();
                event.getAffectedEntities().clear();
            }
        } else {
            // Verifica se a explosão afeta blocos ou entidades dentro de uma região protegida
            List<BlockPos> affectedBlocks = new ArrayList<>(event.getAffectedBlocks());
            for (BlockPos pos : affectedBlocks) {
                if (isRegionProtected(pos)) {
                    event.getAffectedBlocks().remove(pos);
                }
            }

            // Filtra apenas as entidades que são LivingEntity
            List<LivingEntity> affectedEntities = new ArrayList<>();
            for (Entity entity : event.getAffectedEntities()) {
                if (entity instanceof LivingEntity) {
                    affectedEntities.add((LivingEntity) entity);
                }
            }

            // Remove entidades dentro de regiões protegidas
            for (LivingEntity entity : affectedEntities) {
                if (isRegionProtected(entity.blockPosition())) {
                    event.getAffectedEntities().remove(entity);
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
    public static void onEntityBlockBreak(LivingDestroyBlockEvent event) {
        BlockPos pos = event.getPos();

        if (isRegionProtected(pos)) {
            if (!isFlagEnabled(pos, "entidade_quebra", null)) {
                event.setCanceled(true);
            }
        }
    }

    // Classe interna para o ThrowableBrickEntity
    public static class ThrowableBrickEntity extends ThrowableItemProjectile {

        public ThrowableBrickEntity(EntityType<? extends ThrowableBrickEntity> type, Level world) {
            super(type, world);
        }

        public ThrowableBrickEntity(Level world, LivingEntity thrower) {
            super(EntityType.SNOWBALL, thrower, world); // Use o tipo de entidade correto aqui
        }

        @Override
        protected Item getDefaultItem() {
            return Items.BRICK; // Use o item correto aqui
        }

        @Override
        protected void onHit(HitResult result) {
            super.onHit(result);

            if (result.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) result;
                BlockPos pos = blockHitResult.getBlockPos();

                // Verifica se o bloco está em uma região protegida
                if (isRegionProtected(pos)) {
                    Player owner = this.getOwner() instanceof Player ? (Player) this.getOwner() : null;

                    // Verifica se a flag "destroy" está desativada
                    if (!isFlagEnabled(pos, "destroy", owner)) {
                        this.level().broadcastEntityEvent(this, (byte) 3); // Efeito de partícula ao atingir
                        this.discard(); // Remove a entidade
                        if (owner != null) {
                            owner.sendSystemMessage(Component.literal("Você não pode quebrar blocos nesta região protegida."));
                        }
                        return;
                    }
                }

                // Lógica para quebrar o bloco (se permitido)
                // ...

            } else if (result.getType() == HitResult.Type.ENTITY) {
                EntityHitResult entityHitResult = (EntityHitResult) result;
                LivingEntity entity = (LivingEntity) entityHitResult.getEntity();
                BlockPos pos = entity.blockPosition();

                // Verifica se a entidade está em uma região protegida
                if (isRegionProtected(pos)) {
                    Player owner = this.getOwner() instanceof Player ? (Player) this.getOwner() : null;

                    // Verifica se a flag "entidade_dano" está desativada
                    if (!isFlagEnabled(pos, "entidade_dano", owner)) {
                        this.level().broadcastEntityEvent(this, (byte) 3); // Efeito de partícula ao atingir
                        this.discard(); // Remove a entidade
                        if (owner != null) {
                            owner.sendSystemMessage(Component.literal("Você não pode causar dano a entidades nesta região protegida."));
                        }
                        return;
                    }
                }

                // Lógica para causar dano à entidade (se permitido)
                entity.hurt(entity.damageSources().thrown(this, this.getOwner()), 4.0F); // Exemplo de dano
            }

            this.level().broadcastEntityEvent(this, (byte) 3); // Efeito de partícula ao atingir
            this.discard(); // Remove a entidade
        }
    }
}