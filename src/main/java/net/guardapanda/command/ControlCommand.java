package net.guardapanda.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class ControlCommand {

    private static final Map<UUID, Entity> controllingPlayers = new HashMap<>();
    private static final Map<UUID, Integer> previousEntityIds = new HashMap<>();




    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("control")
			    .requires(source -> source.hasPermission(2)) // Requer permissão de OP (nível 2)

                .then(Commands.literal("on")
                    .executes(context -> enableControl(context)))
                .then(Commands.literal("off")
                    .executes(context -> disableControl(context)))
        );
    }

    private static int enableControl(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Entity entity = source.getEntity();

        if (!(entity instanceof ServerPlayer)) {
            source.sendFailure(Component.literal("Este comando só pode ser usado por jogadores."));
            return Command.SINGLE_SUCCESS;
        }

        ServerPlayer player = (ServerPlayer) entity;
        Entity targetedEntity = getLookedAtEntity(player);

        if (targetedEntity == null) {
            player.sendSystemMessage(Component.literal("Não foi encontrada nenhuma entidade para controlar."));
            return Command.SINGLE_SUCCESS;
        }

        // Salva o ID da entidade anterior
        previousEntityIds.put(player.getUUID(), targetedEntity.getId());

        // Controla a nova entidade
        controllingPlayers.put(player.getUUID(), targetedEntity);
        player.sendSystemMessage(Component.literal("Agora está a controlar a entidade: " + targetedEntity.getName().getString()));
        return Command.SINGLE_SUCCESS;
    }

    private static int disableControl(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Entity entity = source.getEntity();

        if (!(entity instanceof ServerPlayer)) {
            source.sendFailure(Component.literal("Este comando só pode ser usado por jogadores."));
            return Command.SINGLE_SUCCESS;
        }

        ServerPlayer player = (ServerPlayer) entity;

        if (!controllingPlayers.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Não está a controlar nenhuma entidade."));
            return Command.SINGLE_SUCCESS;
        }

        // Retira o controle da entidade e retorna ao controle do jogador
        controllingPlayers.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Saiu do controlo da entidade."));

        // Se o jogador estava controlando outra entidade anteriormente, retorna ao jogador
        if (previousEntityIds.containsKey(player.getUUID())) {
            int previousEntityId = previousEntityIds.get(player.getUUID());
            Entity previousEntity = player.getCommandSenderWorld().getEntity(previousEntityId);
            if (previousEntity != null) {
                controllingPlayers.put(player.getUUID(), previousEntity);
                player.sendSystemMessage(Component.literal("Agora está a controlar a entidade anterior."));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.player;

            if (player.getCommandSenderWorld().isClientSide) {
                return; // Evita a execução no lado cliente
            }

            if (controllingPlayers.containsKey(player.getUUID())) {
                Entity controlledEntity = controllingPlayers.get(player.getUUID());

                if (controlledEntity == null || !controlledEntity.isAlive()) {
                    controllingPlayers.remove(player.getUUID());
                    player.sendSystemMessage(Component.literal("A entidade controlada foi removida ou está morta."));
                    return;
                }

                // Sincroniza a posição, rotação e movimentos da entidade controlada com o jogador
                Vec3 movement = new Vec3(player.zza, player.yya, player.xxa);
                controlledEntity.setDeltaMovement(movement.scale(0.5)); // Ajusta a velocidade da entidade
                controlledEntity.setYRot(player.getYRot());
                controlledEntity.setXRot(player.getXRot());
                controlledEntity.setPos(player.getX(), player.getY(), player.getZ());
            }
        }
    }

    private static Entity getLookedAtEntity(ServerPlayer player) {
        return player.getCommandSenderWorld().getEntities(player, player.getBoundingBox().inflate(5)).stream()
            .filter(e -> e != player)
            .findFirst()
            .orElse(null);
    }
}
