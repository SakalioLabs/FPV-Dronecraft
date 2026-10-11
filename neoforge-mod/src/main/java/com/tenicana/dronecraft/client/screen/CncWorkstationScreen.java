package com.tenicana.dronecraft.client.screen;

import com.tenicana.dronecraft.menu.CncWorkstationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Native container screen; manufacturing and inventory changes remain on the server. */
public final class CncWorkstationScreen extends AbstractContainerScreen<CncWorkstationMenu> {
    public CncWorkstationScreen(CncWorkstationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF26333D);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF111B25);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF718995);
            graphics.fill(x, y, x + 16, y + 16, 0xFF2B404D);
        }
        int width = Math.min(38, Math.max(0, 38 * menu.getProgress() / Math.max(1, menu.getProcessingTicks())));
        graphics.fill(leftPos + 70, topPos + 39, leftPos + 108, topPos + 47, 0xFF394B58);
        graphics.fill(leftPos + 70, topPos + 39, leftPos + 70 + width, topPos + 47, 0xFF5BD4C7);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 7, 0xFFE2EEF6, false);
        graphics.drawString(font, Component.translatable("screen.fpvdrone.cnc_recipe"), 8, 21, 0xFFB8D6E8, false);
        Component status = menu.slots.get(1).hasItem() ? Component.translatable("screen.fpvdrone.cnc_collect")
                : menu.getProgress() > 0 ? Component.translatable("screen.fpvdrone.cnc_processing",
                    100 * menu.getProgress() / Math.max(1, menu.getProcessingTicks()))
                : Component.translatable("screen.fpvdrone.cnc_insert");
        graphics.drawString(font, status, 8, 59, 0xFF8FE0CC, false);
        graphics.drawString(font, playerInventoryTitle, 8, 73, 0xFFB8D6E8, false);
    }
}
