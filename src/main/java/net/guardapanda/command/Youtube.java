package net.guardapanda.command;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.BlockPos;

public class Youtube {

    public static class PictureFrameBlock extends Block {
        public PictureFrameBlock() {
            super(BlockBehaviour.Properties.of());
        }

        
        public InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, Player player, UseOnContext context) {
            if (!level.isClientSide) {
                Minecraft.getInstance().setScreen(new VideoLinkInputScreen(player));
            }
            return InteractionResult.SUCCESS;
        }
    }

    public static class VideoLinkInputScreen extends Screen {
        private final Player player;
        private EditBox linkInputField;

        public VideoLinkInputScreen(Player player) {
            super(Component.literal("Insert YouTube Link"));
            this.player = player;
        }

        @Override
        protected void init() {
            super.init();

            this.linkInputField = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 50, 200, 20, Component.literal("Enter YouTube Link"));
            this.addRenderableWidget(linkInputField);

            this.addRenderableWidget(
                Button.builder(Component.literal("Submit Link"), button -> {
                    String youtubeLink = linkInputField.getValue();
                    player.displayClientMessage(Component.literal("Link inserted: " + youtubeLink), true);
                    Minecraft.getInstance().setScreen(null); // Fecha a tela
                }).bounds(this.width / 2 - 100, this.height / 2, 200, 20).build()
            );
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
            this.renderBackground(guiGraphics);
            super.render(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }
}
