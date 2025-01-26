
package net.guardapanda.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class LuzCommand {

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("luz")
                .then(Commands.argument("estado", StringArgumentType.string())
                    .suggests((context, builder) -> builder.suggest("on").suggest("off").buildFuture())
                    .executes(LuzCommand::execute))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String estado = StringArgumentType.getString(context, "estado");
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            if (estado.equalsIgnoreCase("on")) {
                // Aplica o efeito Night Vision
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 99999999, 0, false, false));
                source.sendSuccess(() -> Component.literal("Luz ativado!"), true);
            } else if (estado.equalsIgnoreCase("off")) {
                // Remove o efeito Night Vision
                player.removeEffect(MobEffects.NIGHT_VISION);
                source.sendSuccess(() -> Component.literal("Luz desativado!"), true);
            } else {
                source.sendFailure(Component.literal("Argumento inválido! Use 'on' ou 'off'."));
            }
        } else {
            source.sendFailure(Component.literal("Este comando só pode ser usado por um jogador."));
        }
        return 1;
    }
}