package com.tenicana.dronecraft.client.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.network.chat.Component;

/** Adapts the 26.3 native scroll area to the panel's existing content layout. */
abstract class ScrollableControlsContainer extends AbstractContainerWidget {
    protected ScrollableControlsContainer(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message, AbstractScrollArea.defaultSettings(20));
    }

	protected final void renderScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		extractScrollbar(graphics, mouseX, mouseY);
	}
}
