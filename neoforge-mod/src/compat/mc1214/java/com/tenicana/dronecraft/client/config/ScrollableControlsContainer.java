package com.tenicana.dronecraft.client.config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.network.chat.Component;
/** Native scroll area adapter for the 1.21.4–1.21.8 widget callbacks. */
abstract class ScrollableControlsContainer extends AbstractContainerWidget {
    protected ScrollableControlsContainer(int x,int y,int width,int height,Component message) { super(x,y,width,height,message); }
    protected void renderScrollbar(GuiGraphics graphics,int mouseX,int mouseY) { super.renderScrollbar(graphics); }
}
