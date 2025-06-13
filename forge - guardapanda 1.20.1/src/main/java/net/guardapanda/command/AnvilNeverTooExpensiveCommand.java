package net.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import java.nio.file.Path;
import java.nio.file.Paths;

@Mod.EventBusSubscriber
public class AnvilNeverTooExpensiveCommand {

    // Configuração para persistir o estado e as mensagens
    private static ForgeConfigSpec.BooleanValue anvilLimitDisabledConfig;
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
        anvilLimitDisabledConfig = configBuilder
            .comment("Define se o limite de custo da bigorna está desativado.")
            .define("anvilLimitDisabled", false); // Valor padrão é false (limite habilitado)

        messageEnabled = configBuilder
            .comment("Mensagem exibida quando o limite de custo da bigorna é desativado.")
            .define("messageEnabled", "Limite de custo da bigorna desativado!");

        messageDisabled = configBuilder
            .comment("Mensagem exibida quando o limite de custo da bigorna é ativado.")
            .define("messageDisabled", "Limite de custo da bigorna ativado!");

        messageStatus = configBuilder
            .comment("Mensagem exibida ao verificar o status do limite de custo da bigorna.")
            .define("messageStatus", "O limite de custo da bigorna está atualmente %s.");

        ForgeConfigSpec configSpec = configBuilder.build();

        // Registra a configuração no caminho especificado
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, configSpec, configPath.resolve("AnvilNeverTooExpensive.toml").toString());
    }

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("anvilnevertooexpensive")
            .requires(source -> source.hasPermission(2)) // Define o nível de permissão como 2 (apenas operadores)
            .then(Commands.literal("enable").executes(context -> {
                anvilLimitDisabledConfig.set(true); // Desativa o limite de custo
                context.getSource().sendSuccess(() -> Component.literal(messageEnabled.get()), true);
                return 1;
            }))
            .then(Commands.literal("disable").executes(context -> {
                anvilLimitDisabledConfig.set(false); // Ativa o limite de custo
                context.getSource().sendSuccess(() -> Component.literal(messageDisabled.get()), true);
                return 1;
            }))
            .executes(context -> {
                String status = anvilLimitDisabledConfig.get() ? "desativado" : "ativado";
                context.getSource().sendSuccess(() -> Component.literal(String.format(messageStatus.get(), status)), true);
                return 1;
            })
        );
    }

    // Mixin para a tela da bigorna (AnvilScreen)
    @Mixin(AnvilScreen.class)
    public static class AnvilScreenMixin {
        @ModifyConstant(method = "renderLabels", constant = @Constant(intValue = 40))
        private int mixinLimitInt(int i) {
            // Respeita a configuração
            return anvilLimitDisabledConfig.get() ? Integer.MAX_VALUE : 40;
        }
    }

    // Mixin para o menu da bigorna (AnvilMenu)
    @Mixin(AnvilMenu.class)
    public static class AnvilMenuMixin {
        @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40))
        private int mixinLimitInt(int i) {
            // Respeita a configuração
            return anvilLimitDisabledConfig.get() ? Integer.MAX_VALUE : 40;
        }

        @ModifyConstant(method = "createResult", constant = @Constant(intValue = 39))
        private int mixinMaxInt(int i) {
            // Respeita a configuração
            return anvilLimitDisabledConfig.get() ? Integer.MAX_VALUE - 1 : 39;
        }
    }

    // Registra o handler de eventos
    static {
        MinecraftForge.EVENT_BUS.register(AnvilNeverTooExpensiveCommand.class);
    }
}