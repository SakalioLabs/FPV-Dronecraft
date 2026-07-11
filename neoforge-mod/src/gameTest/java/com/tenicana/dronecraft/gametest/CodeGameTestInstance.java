package com.tenicana.dronecraft.gametest;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

public final class CodeGameTestInstance extends GameTestInstance {
	public static final MapCodec<CodeGameTestInstance> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			TestData.CODEC.fieldOf("data").forGetter(CodeGameTestInstance::info),
			Identifier.CODEC.fieldOf("case_id").forGetter(CodeGameTestInstance::caseId)
	).apply(instance, CodeGameTestInstance::new));

	private static final List<ExecutableCase> CASES = List.of(
			caseOf("racing_quad_diagnostic_climbs_in_game", 360, 0, DroneFlightGameTest::racingQuadDiagnosticClimbsInGame),
			caseOf("new_drone_starts_in_stable_angle_mode", 60, 0, DroneFlightGameTest::newDroneStartsInStableAngleMode),
			caseOf("drone_entity_playable_route_executes_through_router", 130, 0, DroneFlightGameTest::droneEntityPlayableRouteExecutesThroughRouter),
			caseOf("drone_entity_simulation_route_executes_through_router", 150, 220, DroneFlightGameTest::droneEntitySimulationRouteExecutesThroughRouter),
			caseOf("direct_flight_disarm_clears_playable_attitude", 140, 0, DroneFlightGameTest::directFlightDisarmClearsPlayableAttitude),
			caseOf("direct_flight_respects_ceiling_collision", 140, 0, DroneFlightGameTest::directFlightRespectsCeilingCollision),
			caseOf("direct_flight_link_loss_uses_playable_failsafe", 150, 0, DroneFlightGameTest::directFlightLinkLossUsesPlayableFailsafe),
			caseOf("controller_reuses_nearest_owned_drone", 60, 0, DroneControllerItemGameTest::controllerReusesNearestOwnedDrone)
	);
	private static final Map<Identifier, Consumer<GameTestHelper>> EXECUTORS = CASES.stream().collect(
			Collectors.toUnmodifiableMap(testCase -> testCase.definition().id(), ExecutableCase::executor)
	);

	private final Identifier caseId;

	public CodeGameTestInstance(
			TestData<Holder<TestEnvironmentDefinition>> info,
			Identifier caseId
	) {
		super(info);
		this.caseId = caseId;
	}

	@Override
	public void run(GameTestHelper helper) {
		Consumer<GameTestHelper> executor = EXECUTORS.get(caseId);
		if (executor == null) {
			helper.fail(Component.literal("Unknown FPV Dronecraft GameTest case: " + caseId));
			return;
		}
		executor.accept(helper);
	}

	@Override
	public MapCodec<CodeGameTestInstance> codec() {
		return CODEC;
	}

	@Override
	protected MutableComponent typeDescription() {
		return Component.literal("FPV Dronecraft code GameTest");
	}

	private Identifier caseId() {
		return caseId;
	}

	static List<CaseDefinition> caseDefinitions() {
		return CASES.stream().map(ExecutableCase::definition).toList();
	}

	private static ExecutableCase caseOf(
			String path,
			int maxTicks,
			int setupTicks,
			Consumer<GameTestHelper> executor
	) {
		return new ExecutableCase(
				new CaseDefinition(FpvDronecraftGameTestMod.testId(path), maxTicks, setupTicks),
				executor
		);
	}

	static record CaseDefinition(Identifier id, int maxTicks, int setupTicks) {
	}

	private record ExecutableCase(CaseDefinition definition, Consumer<GameTestHelper> executor) {
	}
}
