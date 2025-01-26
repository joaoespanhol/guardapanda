
package net.guardapanda.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber
public class ItensCommand {

    // Lista de atributos válidos
    private static final List<String> ATTRIBUTE_LIST = List.of(
        "generic.attack_damage",
        "generic.armor",
        "generic.movement_speed",
        "generic.max_health",
        "generic.knockback_resistance",
        "generic.attack_speed",
        "generic.luck"
    );

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        // Comando para editar itens (agora com permissão OP)
        event.getDispatcher().register(
            Commands.literal("itens")
                .requires(source -> source.hasPermission(2)) // Verificação de permissão OP para "/itens"
                .then(Commands.literal("itemedit")
                    .then(Commands.literal("attribute")
                        .then(Commands.argument("attribute", StringArgumentType.string())
                            .then(Commands.argument("value", IntegerArgumentType.integer())
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ItemStack itemInHand = player.getMainHandItem();
                                    if (itemInHand.isEmpty()) {
                                        player.sendSystemMessage(Component.literal("Você não está segurando nenhum item."));
                                        return 0;
                                    }
                                    String attributeName = StringArgumentType.getString(context, "attribute");
                                    int value = IntegerArgumentType.getInteger(context, "value");

                                    // Verifica se o atributo é válido
                                    if (!ATTRIBUTE_LIST.contains(attributeName)) {
                                        player.sendSystemMessage(Component.literal("Atributo inválido: " + attributeName));
                                        return 0;
                                    }

                                    // Cria o modificador de atributo
                                    Attribute attribute = getAttributeByName(attributeName);
                                    if (attribute != null) {
                                        AttributeModifier modifier = new AttributeModifier(UUID.randomUUID(), "CustomModifier", value, AttributeModifier.Operation.ADDITION);
                                        itemInHand.addAttributeModifier(attribute, modifier, EquipmentSlot.MAINHAND);
                                        player.sendSystemMessage(Component.literal("Atributo " + attributeName + " definido para: " + value));
                                        return 1;
                                    } else {
                                        player.sendSystemMessage(Component.literal("Atributo inválido: " + attributeName));
                                        return 0;
                                    }
                                })
                            )
                        )
                    )
                    .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.string())
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ItemStack itemInHand = player.getMainHandItem();
                                if (itemInHand.isEmpty()) {
                                    player.sendSystemMessage(Component.literal("Você não está segurando nenhum item."));
                                    return 0;
                                }
                                String name = StringArgumentType.getString(context, "name");

                                // Renomeando o item
                                CompoundTag tag = itemInHand.getOrCreateTag();
                                CompoundTag displayTag = tag.getCompound("display");
                                displayTag.putString("Name", "\"" + name + "\"");
                                tag.put("display", displayTag);
                                player.sendSystemMessage(Component.literal("Item renomeado para: " + name));
                                return 1;
                            })
                        )
                    )
                )
        );

        // Comando separado para adicionar lore (também com permissão OP)
        event.getDispatcher().register(
            Commands.literal("lore")
                .requires(source -> source.hasPermission(2)) // Permissão OP necessária (perm. 2)
                .then(Commands.argument("lore", StringArgumentType.greedyString()) // Aceita várias palavras
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        ItemStack itemInHand = player.getMainHandItem();
                        if (itemInHand.isEmpty()) {
                            player.sendSystemMessage(Component.literal("Você não está segurando nenhum item."));
                            return 0;
                        }
                        String lore = StringArgumentType.getString(context, "lore");

                        // Modificando o lore do item
                        CompoundTag tag = itemInHand.getOrCreateTag();
                        CompoundTag displayTag = tag.getCompound("display");
                        if (!displayTag.contains("Lore")) {
                            displayTag.put("Lore", new ListTag());
                        }
                        ListTag loreList = displayTag.getList("Lore", 8);
                        loreList.add(StringTag.valueOf("\"" + lore + "\""));
                        tag.put("display", displayTag);
                        player.sendSystemMessage(Component.literal("Lore do item modificado para: " + lore));
                        return 1;
                    })
                )
        );

        // Comando GM para mudar o modo de jogo (agora também com permissão OP)
        event.getDispatcher().register(
            Commands.literal("gm")
                .requires(source -> source.hasPermission(2)) // Verificação de permissão OP para "/gm"
                .then(Commands.argument("mode", IntegerArgumentType.integer())
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        int mode = IntegerArgumentType.getInteger(context, "mode");

                        // Alterando o modo de jogo
                        switch (mode) {
                            case 1:
                                player.setGameMode(GameType.CREATIVE);  // Corrigido para GameType
                                player.sendSystemMessage(Component.literal("Modo de jogo alterado para Criativo"));
                                break;
                            case 0:
                                player.setGameMode(GameType.SURVIVAL);  // Corrigido para GameType
                                player.sendSystemMessage(Component.literal("Modo de jogo alterado para Sobrevivência"));
                                break;
                            case 3:
                                player.setGameMode(GameType.SPECTATOR);  // Corrigido para GameType
                                player.sendSystemMessage(Component.literal("Modo de jogo alterado para Espectador"));
                                break;
                            default:
                                player.sendSystemMessage(Component.literal("Modo de jogo inválido! Use 1 para Criativo, 0 para Sobrevivência ou 3 para Espectador."));
                                return 0;
                        }
                        return 1;
                    })
                )
        );
    }

    // Mapeia os atributos com os nomes corretos
    private static Attribute getAttributeByName(String name) {
        switch (name) {
            case "generic.attack_damage":
                return Attributes.ATTACK_DAMAGE;
            case "generic.armor":
                return Attributes.ARMOR;
            case "generic.movement_speed":
                return Attributes.MOVEMENT_SPEED;
            case "generic.max_health":
                return Attributes.MAX_HEALTH;
            case "generic.knockback_resistance":
                return Attributes.KNOCKBACK_RESISTANCE;
            case "generic.attack_speed":
                return Attributes.ATTACK_SPEED;
            case "generic.luck":
                return Attributes.LUCK;
            default:
                return null;
        }
    }
}
