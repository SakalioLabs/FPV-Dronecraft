package com.tenicana.dronecraft.client.config;

import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.network.chat.Component;

/** Uses the native scroll area supplied by the 1.21.11 container widget. */
abstract class ScrollableControlsContainer extends AbstractContainerWidget {
    protected ScrollableControlsContainer(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }
}
