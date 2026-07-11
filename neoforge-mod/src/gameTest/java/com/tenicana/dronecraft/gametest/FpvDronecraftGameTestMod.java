package com.tenicana.dronecraft.gametest;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(FpvDronecraftGameTestMod.MOD_ID)
public final class FpvDronecraftGameTestMod {
	public static final String MOD_ID = "fpvdrone_gametest";
	private static final Identifier STRUCTURE = Identifier.fromNamespaceAndPath("fpvdrone", "empty");
	private static final DeferredRegister<MapCodec<? extends GameTestInstance>> TEST_INSTANCE_TYPES =
			DeferredRegister.create(Registries.TEST_INSTANCE_TYPE, MOD_ID);
	private static final DeferredHolder<MapCodec<? extends GameTestInstance>, MapCodec<CodeGameTestInstance>> CODE_TEST =
			TEST_INSTANCE_TYPES.register("code", () -> CodeGameTestInstance.CODEC);

	public FpvDronecraftGameTestMod(IEventBus modEventBus) {
		TEST_INSTANCE_TYPES.register(modEventBus);
		modEventBus.addListener(FpvDronecraftGameTestMod::registerGameTests);
	}

	private static void registerGameTests(RegisterGameTestsEvent event) {
		Holder<TestEnvironmentDefinition> environment = event.registerEnvironment(testModId("default"));
		for (CodeGameTestInstance.CaseDefinition testCase : CodeGameTestInstance.caseDefinitions()) {
			TestData<Holder<TestEnvironmentDefinition>> data = new TestData<>(
					environment,
					STRUCTURE,
					testCase.maxTicks(),
					testCase.setupTicks(),
					true
			);
			event.registerTest(
					testCase.id(),
					registeredData -> new CodeGameTestInstance(registeredData, testCase.id()),
					data
			);
		}
	}

	static Identifier testId(String path) {
		return Identifier.fromNamespaceAndPath("fpvdrone", path);
	}

	private static Identifier testModId(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
