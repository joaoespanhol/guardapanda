package net.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Mod.EventBusSubscriber
public class NotradeVilladeCommand {

    // Configuração para persistir o estado e as mensagens
    private static ForgeConfigSpec.BooleanValue noTradeEnabledConfig;
    private static ForgeConfigSpec.ConfigValue<String> messageEnabled;
    private static ForgeConfigSpec.ConfigValue<String> messageDisabled;
    private static ForgeConfigSpec.ConfigValue<String> messageStatus;

    // Bloco de inicialização estática para configurar o arquivo de configuração
    static {
        // Define o caminho da pasta de configuração
        Path configPath = Paths.get("config", "guardapanda");
        if (!configPath.toFile().exists()) {
            configPath.toFile().mkdirs(); // Cria a pasta se não existir
        }

        // Define a configuração
        ForgeConfigSpec.Builder configBuilder = new ForgeConfigSpec.Builder();
        noTradeEnabledConfig = configBuilder
            .comment("Define se a funcionalidade 'no trade' está ativada ou desativada.")
            .define("noTradeEnabled", true); // Valor padrão é true

        messageEnabled = configBuilder
            .comment("Mensagem exibida quando a funcionalidade 'no trade' é ativada.")
            .define("messageEnabled", "Funcionalidade 'no trade' ativada!");

        messageDisabled = configBuilder
            .comment("Mensagem exibida quando a funcionalidade 'no trade' é desativada.")
            .define("messageDisabled", "Funcionalidade 'no trade' desativada!");

        messageStatus = configBuilder
            .comment("Mensagem exibida ao verificar o status da funcionalidade 'no trade'.")
            .define("messageStatus", "A funcionalidade 'no trade' está atualmente %s.");

        ForgeConfigSpec configSpec = configBuilder.build();

        // Registra a configuração no caminho especificado
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, configSpec, configPath.resolve("NoTradeVillage.toml").toString());
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("notradevillade")
            .then(Commands.literal("enable").executes(context -> {
                noTradeEnabledConfig.set(true); // Salva o novo estado no arquivo de configuração
                context.getSource().sendSuccess(() -> Component.literal(messageEnabled.get()), true);
                return 1;
            }))
            .then(Commands.literal("disable").executes(context -> {
                noTradeEnabledConfig.set(false); // Salva o novo estado no arquivo de configuração
                context.getSource().sendSuccess(() -> Component.literal(messageDisabled.get()), true);
                return 1;
            }))
            .executes(context -> {
                String status = noTradeEnabledConfig.get() ? "ativada" : "desativada";
                context.getSource().sendSuccess(() -> Component.literal(String.format(messageStatus.get(), status)), true);
                return 1;
            })
        );
    }

    @SubscribeEvent
    public static void onPlayerInteractWithEntity(PlayerInteractEvent.EntityInteract event) {
        // Verifica se a funcionalidade "no trade" está ativada
        if (noTradeEnabledConfig.get()) {
            // Verifica se o alvo é um Aldeão ou um Comerciante Errante
            if (event.getTarget() instanceof net.minecraft.world.entity.npc.Villager ||
                event.getTarget() instanceof net.minecraft.world.entity.npc.WanderingTrader) {
                // Cancela a interação
                event.setCanceled(true);
            }
        }
    }

    // Registra o handler de eventos
    static {
        MinecraftForge.EVENT_BUS.register(NotradeVilladeCommand.class);
    }
}