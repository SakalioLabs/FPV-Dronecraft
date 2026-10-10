package com.tenicana.dronecraft.client.control;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
/** 1.21.1 has no native inactivity FPS limiter; retain controller activity diagnostics. */
public final class MinecraftUserActivityNotifier implements UserActivityNotifier {
 public static final MinecraftUserActivityNotifier INSTANCE=new MinecraftUserActivityNotifier();
 private int lastReportTick=Integer.MIN_VALUE;
 private long latestInputTime;
 private MinecraftUserActivityNotifier() { }
 @Override public void notifyUserActivity(int tick) { if(canNotifyUserActivity()) { latestInputTime=System.currentTimeMillis();lastReportTick=tick; } }
 @Override public boolean canNotifyUserActivity() {
  Minecraft client=Minecraft.getInstance();return client!=null&&client.player!=null&&client.level!=null&&!client.isPaused()&&client.getWindow()!=null&&GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(),GLFW.GLFW_ICONIFIED)==GLFW.GLFW_FALSE;
 }
 @Override public Status status() {
  Minecraft client=Minecraft.getInstance();return client==null?Status.unavailable(lastReportTick):new Status(latestInputTime==0?-1:Math.max(0,System.currentTimeMillis()-latestInputTime),"NOT_PRESENT_1_21_1","NONE",client.getFps(),lastReportTick);
 }
}
