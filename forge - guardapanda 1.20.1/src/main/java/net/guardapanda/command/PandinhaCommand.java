package net.guardapanda.command;


import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Optional;
import java.util.HashMap;
import java.util.UUID;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent; // Importação corrigida

@Mod.EventBusSubscriber
public class PandinhaCommand {
    private static final HashMap<UUID, double[]> lastLocations = new HashMap<>();

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        // Comando /alerta
        event.getDispatcher().register(Commands.literal("alerta")
          .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
            .then(Commands.argument("mensagem", MessageArgument.message())
                .executes(context -> {
                    String mensagem = MessageArgument.getMessage(context, "mensagem").getString();
                    context.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Alerta] " + mensagem), false);
                    context.getSource().sendSuccess(() -> Component.literal("Alerta enviado: " + mensagem), false);
                    return 1;
                })
            )
        );

        // Comando /echest
        event.getDispatcher().register(Commands.literal("echest")
          .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(context -> {
                    String playerName = StringArgumentType.getString(context, "player");
                    Optional<ServerPlayer> targetPlayer = context.getSource().getServer().getPlayerList().getPlayers().stream()
                        .filter(player -> player.getName().getString().equals(playerName))
                        .findFirst();
                    if (targetPlayer.isPresent()) {
                        ServerPlayer target = targetPlayer.get();
                        context.getSource().sendSuccess(() -> Component.literal("Abrindo o Ender Chest de " + playerName + "..."), false);
                        ServerPlayer sourcePlayer = (ServerPlayer) context.getSource().getEntity();
                        sourcePlayer.openMenu(new SimpleMenuProvider(
                            (id, inventory, p) -> ChestMenu.threeRows(id, inventory, target.getEnderChestInventory()),
                            Component.literal("Ender Chest de " + playerName)));
                    } else {
                        context.getSource().sendFailure(Component.literal("Jogador não encontrado!"));
                    }
                    return 1;
                })
            )
            .executes(context -> {
                if (context.getSource().getEntity() instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
                    player.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> ChestMenu.threeRows(id, inventory, player.getEnderChestInventory()),
                        Component.literal("Ender Chest")));
                    context.getSource().sendSuccess(() -> Component.literal("Abrindo seu Ender Chest..."), false);
                } else {
                    context.getSource().sendFailure(Component.literal("Este comando só pode ser usado por jogadores!"));
                }
                return 1;
            })
        );

        // Comando /fly
        event.getDispatcher().register(Commands.literal("fly")
          .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
            .then(Commands.literal("on")
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer) {
                        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
                        // Ativar o voo
                        if (!player.getAbilities().flying) {
                            player.getAbilities().flying = true;
                            player.getAbilities().mayfly = true;
                            player.onUpdateAbilities();
                            context.getSource().sendSuccess(() -> Component.literal("Modo fly ativado!"), false);
                        } else {
                            context.getSource().sendSuccess(() -> Component.literal("O voo já está ativado!"), false);
                        }
                    } else {
                        context.getSource().sendFailure(Component.literal("Este comando só pode ser usado por jogadores!"));
                    }
                    return 1;
                })
            )
            .then(Commands.literal("off")
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer) {
                        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
                        // Desativar o voo
                        if (player.getAbilities().flying) {
                            player.getAbilities().flying = false;
                            player.getAbilities().mayfly = false;
                            player.onUpdateAbilities();
                            context.getSource().sendSuccess(() -> Component.literal("Modo fly desativado!"), false);
                        } else {
                            context.getSource().sendSuccess(() -> Component.literal("O voo já está desativado!"), false);
                        }
                    } else {
                        context.getSource().sendFailure(Component.literal("Este comando só pode ser usado por jogadores!"));
                    }
                    return 1;
                })
            )
        );

        // Comando /god
        event.getDispatcher().register(Commands.literal("god")
          .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
            .then(Commands.literal("on")
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer) {
                        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
                        // Ativar o modo God (invulnerabilidade)
                        if (!player.isInvulnerable()) {
                            player.setInvulnerable(true); // Torna o jogador invulnerável
                            context.getSource().sendSuccess(() -> Component.literal("Modo God ativado! Você está invulnerável."), false);
                        } else {
                            context.getSource().sendSuccess(() -> Component.literal("Você já está no modo God!"), false);
                        }
                    } else {
                        context.getSource().sendFailure(Component.literal("Este comando só pode ser usado por jogadores!"));
                    }
                    return 1;
                })
            )
            .then(Commands.literal("off")
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer) {
                        ServerPlayer player = (ServerPlayer) context.getSource().getEntity();
                        // Desativar o modo God (remover invulnerabilidade)
                        if (player.isInvulnerable()) {
                            player.setInvulnerable(false); // Remove a invulnerabilidade
                            context.getSource().sendSuccess(() -> Component.literal("Modo God desativado! Você não está mais invulnerável."), false);
                        } else {
                            context.getSource().sendSuccess(() -> Component.literal("Você não está no modo God!"), false);
                        }
                    } else {
                        context.getSource().sendFailure(Component.literal("Este comando só pode ser usado por jogadores!"));
                    }
                    return 1;
                })
            )
        );

        // Comando /tp
        event.getDispatcher().register(Commands.literal("tp")
          .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(context -> {
                    String playerName = StringArgumentType.getString(context, "player");
                    CommandSourceStack source = context.getSource();
                    Optional<ServerPlayer> targetPlayer = source.getServer().getPlayerList().getPlayers().stream()
                        .filter(player -> player.getName().getString().equals(playerName))
                        .findFirst();
                    if (targetPlayer.isPresent() && source.getEntity() instanceof ServerPlayer) {
                        ServerPlayer sourcePlayer = (ServerPlayer) source.getEntity();
                        ServerPlayer target = targetPlayer.get();
                        sourcePlayer.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                        source.sendSuccess(() -> Component.literal("Teletransportado para " + playerName + "."), false);
                    } else {
                        source.sendFailure(Component.literal("Jogador não encontrado!"));
                    }
                    return 1;
                })
            )
        );

        // Comando /tphere
        event.getDispatcher().register(Commands.literal("tphere")
          .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)
            .then(Commands.argument("player", StringArgumentType.word())
                .executes(context -> {
                    String playerName = StringArgumentType.getString(context, "player");
                    CommandSourceStack source = context.getSource();
                    Optional<ServerPlayer> targetPlayer = source.getServer().getPlayerList().getPlayers().stream()
                        .filter(player -> player.getName().getString().equals(playerName))
                        .findFirst();
                    if (targetPlayer.isPresent() && source.getEntity() instanceof ServerPlayer) {
                        ServerPlayer target = targetPlayer.get();
                        ServerPlayer sourcePlayer = (ServerPlayer) source.getEntity();
                        target.teleportTo(sourcePlayer.serverLevel(), sourcePlayer.getX(), sourcePlayer.getY(), sourcePlayer.getZ(), sourcePlayer.getYRot(), sourcePlayer.getXRot());
                        source.sendSuccess(() -> Component.literal(playerName + " foi teletransportado para você."), false);
                    } else {
                        source.sendFailure(Component.literal("Jogador não encontrado!"));
                    }
                    return 1;
                })
            )
        );

        // Comando /back
        event.getDispatcher().register(Commands.literal("back")
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                if (source.getEntity() instanceof ServerPlayer) {
                    ServerPlayer player = (ServerPlayer) source.getEntity();
                    UUID playerUUID = player.getUUID();

                    // Verifica se o jogador tem uma localização anterior salva
                    if (lastLocations.containsKey(playerUUID)) {
                        double[] lastLocation = lastLocations.get(playerUUID);
                        player.teleportTo(player.serverLevel(), lastLocation[0], lastLocation[1], lastLocation[2], player.getYRot(), player.getXRot());
                        source.sendSuccess(() -> Component.literal("Você voltou para a última localização salva."), false);
                        return 1; // Sucesso
                    } else {
                        source.sendFailure(Component.literal("Nenhuma localização anterior foi salva!"));
                        return 0; // Falha
                    }
                } else {
                    source.sendFailure(Component.literal("Este comando só pode ser usado por jogadores!"));
                    return 0; // Falha
                }
            })
        );
    }

    // Salva a localização do jogador ao morrer
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            savePlayerLocation(player);
        }
    }

    // Salva a localização do jogador ao ser teletransportado
    @SubscribeEvent
    public static void onPlayerTeleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            savePlayerLocation(player);
        }
    }

    // Método para salvar a localização do jogador
    private static void savePlayerLocation(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        lastLocations.put(playerUUID, new double[]{player.getX(), player.getY(), player.getZ()});
    }
}