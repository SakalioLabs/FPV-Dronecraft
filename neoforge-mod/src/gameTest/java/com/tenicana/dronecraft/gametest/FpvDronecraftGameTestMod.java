package com.tenicana.dronecraft.gametest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
@Mod(FpvDronecraftGameTestMod.MOD_ID)
public final class FpvDronecraftGameTestMod {
 public static final String MOD_ID = "fpvdrone_gametest";
 public FpvDronecraftGameTestMod(IEventBus bus) {
  String report = System.getProperty("fpvdrone.gameTestReport");
  if (report != null) {
   try {
    net.minecraft.gametest.framework.GlobalTestReporter.replaceWith(
      new net.minecraft.gametest.framework.JUnitLikeTestReporter(new java.io.File(report)));
   } catch (javax.xml.parsers.ParserConfigurationException failure) {
    throw new IllegalStateException("Cannot initialize the native GameTest XML reporter", failure);
   }
  }
  bus.addListener((RegisterGameTestsEvent event) -> {
   event.register(DroneFlightGameTest.class);
   event.register(DroneControllerItemGameTest.class);
   event.register(MekanismAssemblyGameTest.class);
  });
 }
}
