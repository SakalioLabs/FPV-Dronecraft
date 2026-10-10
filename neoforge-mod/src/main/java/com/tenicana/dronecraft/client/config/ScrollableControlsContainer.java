package com.tenicana.dronecraft.client.config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.network.chat.Component;
/** Small scroll adapter for the pre-1.21.11 container widget API. */
abstract class ScrollableControlsContainer extends AbstractContainerWidget {
 private double scroll;
 private boolean scrollbarDragging;
 protected ScrollableControlsContainer(int x,int y,int width,int height,Component message) { super(x,y,width,height,message); }
 protected abstract int contentHeight();
 protected abstract double scrollRate();
 protected double scrollAmount() { return scroll; }
 public void setScrollAmount(double amount) { scroll=Math.max(0,Math.min(Math.max(0,contentHeight()-getHeight()),amount)); }
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) { if(!isMouseOver(x,y)) return false;setScrollAmount(scroll-vertical*scrollRate());return true; }
 @Override public boolean mouseClicked(double x,double y,int button) {
  if(button==0&&x>=getRight()-8&&x<=getRight()&&y>=getY()&&y<=getBottom()&&contentHeight()>getHeight()) { scrollbarDragging=true;moveScrollbar(y);return true; }
  return super.mouseClicked(x,y,button);
 }
 @Override public boolean mouseReleased(double x,double y,int button) { scrollbarDragging=false;return super.mouseReleased(x,y,button); }
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) { if(scrollbarDragging&&button==0) { moveScrollbar(y);return true; }return super.mouseDragged(x,y,button,dx,dy); }
 private void moveScrollbar(double y) { setScrollAmount((y-getY())/Math.max(1,getHeight())*Math.max(0,contentHeight()-getHeight())); }
 protected void renderScrollbar(GuiGraphics graphics,int mouseX,int mouseY) {
  if(contentHeight()<=getHeight()) return;
  int h=Math.max(16,getHeight()*getHeight()/contentHeight());int y=getY()+(int)(scroll/Math.max(1,contentHeight()-getHeight())*(getHeight()-h));
  graphics.fill(getRight()-6,getY(),getRight(),getBottom(),0x80303030);graphics.fill(getRight()-6,y,getRight(),y+h,0xFFC0C0C0);
 }
}
