package net.guardapanda.command;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.PacketDistributor;

import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber
public class ListamodsCommand {

    // Criação do canal de rede
    private static final String PROTOCOL_VERSION = "1.0";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("guardapanda", "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    // Registro dos pacotes de rede
    static {
        INSTANCE.registerMessage(0, RequestModListPacket.class, RequestModListPacket::encode, RequestModListPacket::decode, RequestModListPacket::handle);
        INSTANCE.registerMessage(1, SendModListPacket.class, SendModListPacket::encode, SendModListPacket::decode, SendModListPacket::handle);
    }

    // Registro do comando /listamods
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("listamods")
                .then(Commands.argument("nick", EntityArgument.player())
                        .executes(context -> listMods(context.getSource(), EntityArgument.getPlayer(context, "nick")))
                )
                .executes(context -> listMods(context.getSource(), null))
        );
    }

    // Lógica do comando /listamods
    private static int listMods(CommandSourceStack source, ServerPlayer targetPlayer) {
        if (targetPlayer == null && source.getEntity() instanceof ServerPlayer) {
            // Se nenhum jogador for especificado e o executor for um jogador, use o executor como alvo
            targetPlayer = (ServerPlayer) source.getEntity();
        }

        if (targetPlayer != null) {
            // Cria uma cópia final da variável para uso nas lambdas
            final ServerPlayer finalTargetPlayer = targetPlayer;

            // Envia um pacote para o cliente solicitando a lista de mods
            System.out.println("[DEBUG] Preparando para enviar pacote de solicitação para " + finalTargetPlayer.getName().getString());
            INSTANCE.send(PacketDistributor.PLAYER.with(() -> finalTargetPlayer), new RequestModListPacket());
            System.out.println("[DEBUG] Pacote de solicitação enviado para " + finalTargetPlayer.getName().getString());
            source.sendSuccess(() -> Component.literal("Solicitando lista de mods do jogador " + finalTargetPlayer.getName().getString() + "..."), false);
        } else {
            source.sendFailure(Component.literal("Jogador não encontrado ou comando inválido."));
        }
        return 1;
    }

    // Salva a lista de mods em um arquivo
    public static void saveModListToFile(String playerName, String modList) {
        File file = new File(FMLPaths.GAMEDIR.get().toFile(), "modlists.txt");
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(playerName + ": " + modList + "\n");
            System.out.println("[DEBUG] Lista de mods salva para " + playerName);
        } catch (IOException e) {
            System.err.println("[ERRO] Falha ao salvar lista de mods para " + playerName);
            e.printStackTrace();
        }
    }

    // Pacote para solicitar a lista de mods do cliente
    public static class RequestModListPacket {
        public RequestModListPacket() {}

        public static void encode(RequestModListPacket msg, FriendlyByteBuf buffer) {}

        public static RequestModListPacket decode(FriendlyByteBuf buffer) {
            return new RequestModListPacket();
        }

        public static void handle(RequestModListPacket msg, Supplier<NetworkEvent.Context> ctx) {
            System.out.println("[DEBUG] Pacote de solicitação recebido pelo cliente.");
            ctx.get().enqueueWork(() -> {
                // Responde com a lista de mods do cliente
                List<String> mods = ModList.get().getMods().stream()
                        .map(mod -> mod.getDisplayName())
                        .collect(Collectors.toList());
                System.out.println("[DEBUG] Lista de mods coletada no cliente: " + mods);
                INSTANCE.reply(new SendModListPacket(mods), ctx.get());
                System.out.println("[DEBUG] Lista de mods enviada pelo cliente.");
            });
            ctx.get().setPacketHandled(true);
        }
    }

    // Pacote para enviar a lista de mods do cliente para o servidor
    public static class SendModListPacket {
        private final List<String> mods;

        public SendModListPacket(List<String> mods) {
            this.mods = mods;
        }

        public static void encode(SendModListPacket msg, FriendlyByteBuf buffer) {
            buffer.writeInt(msg.mods.size());
            for (String mod : msg.mods) {
                buffer.writeUtf(mod);
            }
        }

        public static SendModListPacket decode(FriendlyByteBuf buffer) {
            int size = buffer.readInt();
            List<String> mods = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                mods.add(buffer.readUtf());
            }
            return new SendModListPacket(mods);
        }

        public static void handle(SendModListPacket msg, Supplier<NetworkEvent.Context> ctx) {
            System.out.println("[DEBUG] Pacote de lista de mods recebido pelo servidor.");
            ctx.get().enqueueWork(() -> {
                // Salva a lista de mods no servidor
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    String playerName = player.getName().getString();
                    String modListString = String.join(", ", msg.mods);
                    saveModListToFile(playerName, modListString);

                    // Exibe a lista de mods no console ou para o executor do comando
                    ctx.get().getSender().getServer().getPlayerList().getPlayers().forEach(p -> {
                        if (p.hasPermissions(2)) { // Apenas para operadores
                            p.sendSystemMessage(Component.literal("Mods de " + playerName + ": " + modListString));
                        }
                    });
                    System.out.println("[DEBUG] Lista de mods processada para " + playerName + ": " + modListString);
                } else {
                    System.err.println("[ERRO] Jogador desconectado antes de enviar a lista de mods.");
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}