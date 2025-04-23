package net.guardapanda.command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class LimititensCommandClient {
    public static void openGui(Player player) {
        Minecraft.getInstance().setScreen(new LimitCheckScreen(
            new LimitCheckMenu(0, player.getInventory()),
            player.getInventory(),
            Component.literal("Limites Globais de Itens")
        ));
    }

    public static class LimitCheckMenu extends AbstractContainerMenu {
        public final SimpleContainer container;

        public LimitCheckMenu(int id, Inventory inv) {
            super(null, id);
            this.container = new SimpleContainer(Math.min(54, LimititensCommand.getGlobalItemLimits().size() + 9));

            int slot = 0;
            for (Map.Entry<String, Integer> entry : LimititensCommand.getGlobalItemLimits().entrySet()) {
                ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.getKey())));
                if (!stack.isEmpty()) {
                    container.setItem(slot, stack);
                    slot++;
                }
            }

            // Slots do inventário
            for (int row = 0; row < 3; ++row) {
                for (int col = 0; col < 9; ++col) {
                    this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
                }
            }

            // Hotbar
            for (int i = 0; i < 9; ++i) {
                this.addSlot(new Slot(inv, i, 8 + i * 18, 142));
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }
    }

    public static class LimitCheckScreen extends AbstractContainerScreen<LimitCheckMenu> {
        public LimitCheckScreen(LimitCheckMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
            this.imageHeight = 184;
            this.inventoryLabelY = this.imageHeight - 94;
        }

        @Override
        protected void renderBg(GuiGraphics gui, float partialTicks, int mouseX, int mouseY) {
            this.renderBackground(gui);
            gui.blit(new ResourceLocation("textures/gui/container/generic_54.png"), 
                this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
            
            for (int i = 0; i < this.menu.container.getContainerSize(); i++) {
                ItemStack stack = this.menu.container.getItem(i);
                if (!stack.isEmpty()) {
                    int x = this.leftPos + 8 + (i % 9) * 18;
                    int y = this.topPos + 18 + (i / 9) * 18;
                    
                    gui.renderItem(stack, x, y);
                    gui.renderItemDecorations(this.font, stack, x, y);
                    
                    String limit = String.valueOf(LimititensCommand.getGlobalItemLimits()
                        .get(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString()));
                    gui.drawString(this.font, limit, x + 12, y + 9, 0xFFFFFF, true);
                }
            }
        }

        @Override
        protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
            gui.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
            gui.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
        }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            super.render(gui, mouseX, mouseY, partialTicks);
            this.renderTooltip(gui, mouseX, mouseY);
        }

        @Override
        protected void init() {
            super.init();
            this.addRenderableWidget(Button.builder(Component.literal("Fechar"), 
                button -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                .build());
        }
    }
}