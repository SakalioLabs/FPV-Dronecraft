package com.tenicana.dronecraft.client.screen;

import com.tenicana.dronecraft.assembly.DroneAssemblyInventory;
import com.tenicana.dronecraft.menu.DroneAssemblyMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Uses the native container click/drag/shift-transfer protocol. */
public final class DroneAssemblyScreen extends AbstractContainerScreen<DroneAssemblyMenu> {
    private static final int PARTS = 13;
    private static final String[] SINGLE_LABELS = {"screen.fpvdrone.assembly_esc", "screen.fpvdrone.assembly_flight_controller", "screen.fpvdrone.assembly_receiver", "screen.fpvdrone.assembly_battery", "screen.fpvdrone.assembly_camera"};

    public DroneAssemblyScreen(DroneAssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 212, 222);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF25313D);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFF111B25);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF6F879A);
            graphics.fill(x, y, x + 16, y + 16, 0xFF263C4A);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, 9, 8, 0xFFE2EEF6);
        graphics.text(font, Component.translatable("screen.fpvdrone.assembly_motors"), 9, 21, 0xFFB8D6E8);
        graphics.text(font, Component.translatable("screen.fpvdrone.assembly_propellers"), 9, 53, 0xFFB8D6E8);
        for (int index = 0; index < SINGLE_LABELS.length; index++) {
            Component label = Component.translatable(SINGLE_LABELS[index]);
            graphics.text(font, label, 28 + 38 * index - font.width(label) / 2, 90, 0xFFB8D6E8);
        }
        int installed = 0;
        for (int index = 0; index < PARTS; index++) if (menu.slots.get(index).hasItem()) installed++;
        boolean flightReady = true;
        for (int index = 0; index < PARTS; index++) {
            if (index != DroneAssemblyInventory.CAMERA && !menu.slots.get(index).hasItem()) flightReady = false;
        }
        Component status = installed == PARTS ? Component.translatable("screen.fpvdrone.assembly_ready")
                : flightReady ? Component.translatable("screen.fpvdrone.assembly_los_ready")
                : Component.translatable("screen.fpvdrone.assembly_incomplete", installed, PARTS);
        graphics.text(font, status, 9, 120, flightReady ? 0xFF97E6AC : 0xFFFFCC79);
        graphics.text(font, playerInventoryTitle, 25, 130, 0xFFB8D6E8);
    }
}
