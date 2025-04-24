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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@OnlyIn(Dist.CLIENT)
public class LimititensCommandClient {
    private static final ResourceLocation GUI_BACKGROUND = new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int SLOTS_PER_ROW = 9;
    private static final int VISIBLE_SLOT_ROWS = 5;
    private static final int SLOTS_PER_PAGE = SLOTS_PER_ROW * VISIBLE_SLOT_ROWS - 2; // Reserve 2 slots for arrows

    public static void openGui(Player player) {
        Minecraft.getInstance().setScreen(new LimitCheckScreen(
            new LimitCheckMenu(0, player.getInventory()),
            player.getInventory(),
            Component.literal("Limites Globais de Itens")
        ));
    }

    public static class LimitCheckMenu extends AbstractContainerMenu {
        private int currentPage = 0;
        
        public LimitCheckMenu(int id, Inventory inv) {
            super(null, id);
            updateSlots();
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public int getMaxPages() {
            return Math.max(1, (int) Math.ceil((double) LimititensCommand.getGlobalItemLimits().size() / SLOTS_PER_PAGE));
        }

        private void updateSlots() {
            this.slots.clear();

            List<Map.Entry<String, Integer>> cachedEntries = new ArrayList<>(LimititensCommand.getGlobalItemLimits().entrySet());
            int startIndex = currentPage * SLOTS_PER_PAGE;
            int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, cachedEntries.size());
            
            // Position items in a grid, leaving space for arrows
            for (int i = startIndex; i < endIndex; i++) {
                var entry = cachedEntries.get(i);
                ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(entry.getKey())));
                if (!stack.isEmpty()) {
                    int slotIndex = i - startIndex;
                    int row = slotIndex / (SLOTS_PER_ROW - 1); // One less column to make space
                    int col = slotIndex % (SLOTS_PER_ROW - 1);
                    
                    // Skip the last column if we're in the last row to make space for arrows
                    if (row == VISIBLE_SLOT_ROWS - 1 && col >= SLOTS_PER_ROW - 3) {
                        col = SLOTS_PER_ROW - 3 + (col - (SLOTS_PER_ROW - 3));
                    }
                    
                    this.addSlot(new ReadOnlySlot(stack, 8 + col * 18, 18 + row * 18));
                }
            }
        }

        public void nextPage() {
            if (currentPage < getMaxPages() - 1) {
                currentPage++;
                updateSlots();
            }
        }

        public void prevPage() {
            if (currentPage > 0) {
                currentPage--;
                updateSlots();
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

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            // Block all interactions
        }
    }

    public static class LimitCheckScreen extends AbstractContainerScreen<LimitCheckMenu> {
        private Button prevButton;
        private Button nextButton;
        private Button pageLabel;

        public LimitCheckScreen(LimitCheckMenu menu, Inventory inv, Component title) {
            super(menu, inv, title);
            this.imageHeight = 222;
            this.inventoryLabelY = -1000;
        }

        @Override
        protected void init() {
            super.init();
            
            // Position arrows in the reserved slots (last two slots of last row)
            int arrowX1 = this.leftPos + this.imageWidth - 40;
            int arrowX2 = this.leftPos + this.imageWidth - 20;
            int arrowY = this.topPos + 18 + (VISIBLE_SLOT_ROWS - 1) * 18;
            
            prevButton = Button.builder(Component.literal("◀"), button -> {
                menu.prevPage();
                updateButtonStates();
            }).bounds(arrowX1, arrowY, 18, 18).build();
            
            nextButton = Button.builder(Component.literal("▶"), button -> {
                menu.nextPage();
                updateButtonStates();
            }).bounds(arrowX2, arrowY, 18, 18).build();
            
            pageLabel = Button.builder(
                Component.literal((menu.getCurrentPage() + 1) + "/" + menu.getMaxPages()), 
                button -> {}
            ).bounds(arrowX1 + 9, arrowY, 22, 18).build();
            
            this.addRenderableWidget(prevButton);
            this.addRenderableWidget(nextButton);
            this.addRenderableWidget(pageLabel);
            
            updateButtonStates();
        }

        private void updateButtonStates() {
            int maxPages = menu.getMaxPages();
            boolean showButtons = maxPages > 1;
            
            prevButton.visible = showButtons;
            nextButton.visible = showButtons;
            pageLabel.visible = showButtons;
            
            if (showButtons) {
                prevButton.active = menu.getCurrentPage() > 0;
                nextButton.active = menu.getCurrentPage() < maxPages - 1;
                pageLabel.setMessage(Component.literal((menu.getCurrentPage() + 1) + "/" + maxPages));
            }
        }

        @Override
        protected void renderBg(GuiGraphics gui, float partialTicks, int mouseX, int mouseY) {
            int x = this.leftPos;
            int y = this.topPos;
            
            // Main inventory background
            gui.blit(GUI_BACKGROUND, x, y, 0, 0, this.imageWidth, VISIBLE_SLOT_ROWS * 18 + 17);
            
            // Extended background for buttons
            gui.blit(GUI_BACKGROUND, 
                x, y + VISIBLE_SLOT_ROWS * 18 + 17, 
                0, VISIBLE_SLOT_ROWS * 18 + 17, 
                this.imageWidth, 56);
            
            // Render item limits
            for (Slot slot : this.menu.slots) {
                if (slot instanceof ReadOnlySlot readOnlySlot && !readOnlySlot.getItem().isEmpty()) {
                    ItemStack stack = readOnlySlot.getItem();
                    String limit = String.valueOf(LimititensCommand.getGlobalItemLimits()
                        .get(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString()));
                    
                    gui.drawString(
                        this.font, 
                        limit, 
                        x + slot.x + 19 - this.font.width(limit) / 2, 
                        y + slot.y + 6, 
                        0xFFFFFF,
                        false
                    );
                }
            }
        }

        @Override
        protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
            gui.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        }

        @Override
        public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            this.renderBackground(gui);
            super.render(gui, mouseX, mouseY, partialTicks);
            this.renderTooltip(gui, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.getSlotUnderMouse() != null) {
                return true; // Block slot clicks
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    public static class ReadOnlySlot extends Slot {
        private final ItemStack displayStack;

        public ReadOnlySlot(ItemStack stack, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
            this.displayStack = stack;
            ((SimpleContainer)this.container).setItem(0, stack.copy());
        }

        @Override
        public ItemStack getItem() {
            return displayStack.copy();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public void set(ItemStack stack) {
            // Prevent any changes
        }
    }
}