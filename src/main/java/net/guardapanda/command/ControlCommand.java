package net.guardapanda.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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
    private static final Map<UUID, Inventory> playerInventories = new HashMap<>();
    private static final Map<UUID, Vec3> playerOriginalPositions = new HashMap<>();

	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
	    event.getDispatcher().register(
	        Commands.literal("control")
	            .requires(source -> source.hasPermission(2)) // Nível de permissão 2 (OP)
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

        // Salva a posição original do jogador
        playerOriginalPositions.put(player.getUUID(), player.position());

        // Salva o inventário do jogador
        playerInventories.put(player.getUUID(), player.getInventory());

        // Transfere o inventário do jogador para a entidade controlada
        if (targetedEntity instanceof Player) {
            Player controlledPlayer = (Player) targetedEntity;
            Inventory controlledInventory = controlledPlayer.getInventory();
            controlledInventory.replaceWith(player.getInventory());
        }

        // Teleporta o jogador para dentro da entidade
        player.teleportTo(targetedEntity.getX(), targetedEntity.getY(), targetedEntity.getZ());

        // Torna a entidade invisível
        targetedEntity.setInvisible(true);

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

        // Restaura a posição original do jogador
        if (playerOriginalPositions.containsKey(player.getUUID())) {
            Vec3 originalPosition = playerOriginalPositions.get(player.getUUID());
            player.teleportTo(originalPosition.x, originalPosition.y, originalPosition.z);
            playerOriginalPositions.remove(player.getUUID());
        }

        // Retorna o inventário ao jogador
        if (playerInventories.containsKey(player.getUUID())) {
            player.getInventory().replaceWith(playerInventories.get(player.getUUID()));
            playerInventories.remove(player.getUUID());
        }

        // Restaura a visibilidade da entidade
        Entity controlledEntity = controllingPlayers.get(player.getUUID());
        if (controlledEntity != null) {
            controlledEntity.setInvisible(false);
        }

        // Retira o controle da entidade
        controllingPlayers.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Saiu do controlo da entidade."));

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
                controlledEntity.setYRot(player.getYRot());
                controlledEntity.setXRot(player.getXRot());
                controlledEntity.setPos(player.getX(), player.getY(), player.getZ());

                // Sincroniza o movimento da entidade
                Vec3 movement = new Vec3(player.xxa, player.yya, player.zza);
                controlledEntity.setDeltaMovement(movement.scale(0.5)); // Ajusta a velocidade da entidade
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