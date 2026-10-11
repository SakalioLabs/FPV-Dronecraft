package com.tenicana.dronecraft.client;
import static com.tenicana.dronecraft.FpvDronecraftMod.MOD_ID;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.client.control.DroneClientControls;
import com.tenicana.dronecraft.client.command.AcousticDiagnosticClientCommands;
import com.tenicana.dronecraft.client.command.NativeEventTraceDiagnosticClientCommands;
import com.tenicana.dronecraft.client.hud.DroneHud;
import com.tenicana.dronecraft.client.diagnostic.ClientTitleScreenProbe;
import com.tenicana.dronecraft.client.performance.RuntimePerformanceMonitor;
import com.tenicana.dronecraft.client.render.DroneEntityRenderer;
import com.tenicana.dronecraft.client.render.ControllerElementsLoader;
import com.tenicana.dronecraft.client.render.DroneModelLayers;
import com.tenicana.dronecraft.client.sound.*;
import com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
/** Mod-bus registration remains isolated from client runtime events and dedicated servers. */
@EventBusSubscriber(modid=MOD_ID,value=Dist.CLIENT)
public final class FpvDronecraftClient {
 private static boolean traceArmed;
 private FpvDronecraftClient() { }
 @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
  event.enqueueWork(()-> {
   NativeEventTraceDiagnosticGate.Decision mode=MinecraftNativeEventTraceDiagnosticMode.evaluate();
   if (mode.state()==NativeEventTraceDiagnosticGate.State.REJECTED) throw new IllegalStateException("native acoustic event trace launch rejected: "+mode.reason());
   traceArmed=mode.armed();DroneClientControls.initialize();RuntimePerformanceMonitor.initialize();MinecraftAcousticWorldDirtyTracker.initialize();
   if(traceArmed) NativeEventTraceDiagnosticClientCommands.initialize();
   else { AcousticDiagnosticClientCommands.initialize();DroneSoundManager.initialize(); }
   FpvDronecraftMod.LOGGER.info("FPV Dronecraft NeoForge client adapter initialized");
  });
 }
 @SubscribeEvent public static void assemblyScreen(RegisterMenuScreensEvent event) { event.register(com.tenicana.dronecraft.registry.DroneMenus.ASSEMBLY.get(),com.tenicana.dronecraft.client.screen.DroneAssemblyScreen::new); }
 @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { DroneClientControls.registerKeyMappings(event); }
 @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) { DroneModelLayers.registerLayerDefinitions(event); }
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerEntityRenderer(DroneEntityTypes.drone(),DroneEntityRenderer::new); }
 @SubscribeEvent public static void geometry(ModelEvent.RegisterLoaders event) {
  event.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID,"controller_elements"),new ControllerElementsLoader());
 }
 @SubscribeEvent public static void hud(RegisterGuiLayersEvent event) { DroneHud.registerGuiLayers(event);RuntimePerformanceMonitor.registerGuiLayers(event); }
 @SubscribeEvent public static void reload(AddClientReloadListenersEvent event) { AcousticProfileResources.registerReloadListeners(event); }
 @EventBusSubscriber(modid=MOD_ID,value=Dist.CLIENT)
 public static final class RuntimeEvents {
  @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
   Minecraft client=Minecraft.getInstance();DroneClientControls.onClientTick(client);if(!traceArmed) DroneSoundManager.onClientTick(client);ClientTitleScreenProbe.onClientTick(client);
  }
  @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { DroneClientControls.onLoggingIn(Minecraft.getInstance()); }
  @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { DroneClientControls.onLoggingOut(Minecraft.getInstance()); }
  @SubscribeEvent public static void unload(LevelEvent.Unload event) {
   Minecraft client=Minecraft.getInstance();if(event.getLevel().isClientSide()&&event.getLevel()==client.level) { DroneClientControls.onLevelUnload(client);MinecraftAcousticWorldDirtyTracker.switchWorld(null); }
  }
  @SubscribeEvent public static void load(LevelEvent.Load event) { if(event.getLevel() instanceof ClientLevel level) MinecraftAcousticWorldDirtyTracker.switchWorld(level); }
  @SubscribeEvent public static void chunkLoad(ChunkEvent.Load event) { if(event.getLevel() instanceof ClientLevel level&&event.getChunk() instanceof LevelChunk chunk) MinecraftAcousticWorldDirtyTracker.onChunkLoad(level,chunk); }
  @SubscribeEvent public static void chunkUnload(ChunkEvent.Unload event) { if(event.getLevel() instanceof ClientLevel level&&event.getChunk() instanceof LevelChunk chunk) MinecraftAcousticWorldDirtyTracker.onChunkUnload(level,chunk); }
  @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
   Minecraft client=Minecraft.getInstance();if(event.getLevel().isClientSide()&&event.getLevel()==client.level) DroneClientControls.onEntityLeaveLevel(client,event.getEntity());
  }
  @SubscribeEvent public static void stop(ClientStoppingEvent event) { DroneClientControls.onClientStopping(Minecraft.getInstance());DroneSoundManager.onClientStopping(Minecraft.getInstance());RuntimePerformanceMonitor.onClientStopping(Minecraft.getInstance()); }
 }
}
