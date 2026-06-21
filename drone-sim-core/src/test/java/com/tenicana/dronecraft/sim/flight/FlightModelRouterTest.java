package com.tenicana.dronecraft.sim.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;

class FlightModelRouterTest {
	@Test
	void selectsAndStepsActiveModel() {
		StubFlightModel first = new StubFlightModel("first");
		StubFlightModel second = new StubFlightModel("second");
		FlightModelRouter router = new FlightModelRouter(List.of(first, second), "first");
		FlightStepContext context = new FlightStepContext(
				new DroneInput(0.2, 0.0, 0.0, 0.0, true, true, FlightMode.HORIZON),
				FlightStateSnapshot.zero(FlightMode.HORIZON),
				DroneEnvironment.calm(),
				0.005,
				1L,
				DroneConfig.racingQuad()
		);

		router.step(context);
		router.select("second");
		router.step(context);

		assertEquals(1, first.steps);
		assertEquals(1, second.steps);
		assertEquals(List.of("first", "second"), router.modelIds());
		assertEquals("second", router.activeModel().id());
	}

	@Test
	void rejectsUnknownOrDuplicateModels() {
		assertThrows(IllegalArgumentException.class, () -> new FlightModelRouter(List.of(new StubFlightModel("same"), new StubFlightModel("same")), "same"));
		FlightModelRouter router = new FlightModelRouter(List.of(new StubFlightModel("only")), "only");
		assertThrows(IllegalArgumentException.class, () -> router.select("missing"));
		assertThrows(IllegalArgumentException.class, () -> new FlightModelRouter(List.of(new StubFlightModel("only")), "missing"));
	}

	private static final class StubFlightModel implements FlightModel {
		private final String id;
		private int steps;

		private StubFlightModel(String id) {
			this.id = id;
		}

		@Override
		public String id() {
			return id;
		}

		@Override
		public FlightModelCapabilities capabilities() {
			return FlightModelCapabilities.minimal();
		}

		@Override
		public void initialize(FlightModelInitializationContext context) {
		}

		@Override
		public void reset(FlightStateSnapshot state) {
		}

		@Override
		public FlightStepResult step(FlightStepContext context) {
			steps++;
			return new FlightStepResult(context.previousState(), ActuatorOutput.empty(), ForceTorqueDiagnostics.zero(), List.of(), FlightModelDiagnostics.empty());
		}

		@Override
		public FlightStateSnapshot snapshot() {
			return FlightStateSnapshot.zero();
		}

		@Override
		public FlightModelDiagnostics diagnostics() {
			return FlightModelDiagnostics.empty();
		}
	}
}
