package com.tenicana.dronecraft.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.FlightMode;

class DroneStatusFormatterTest {
	@Test
	void healthyStatusHasNoWarningsAndIncludesDiagnosticState() {
		DroneStatusFormatter.Telemetry telemetry = telemetry(
				false,
				true,
				true,
				false,
				0.86,
				1.0,
				1.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.18,
				0.32,
				0.0,
				0.0,
				1.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				false
		);

		assertEquals("none", DroneStatusFormatter.warnings(telemetry));
		String status = DroneStatusFormatter.format(telemetry);
		assertTrue(status.contains("mode HORIZON"));
		assertTrue(status.contains("current-limit 1.00"));
		assertTrue(status.contains("rc 0.003/0.007s err 0.0005"));
		assertTrue(status.contains("spike 0.00V"));
		assertTrue(status.contains("regen 0.0A"));
		assertTrue(status.contains("contact 0.00/0.00/0.00m/s 0d/s"));
		assertTrue(status.contains("airmass 2.7m/s"));
		assertTrue(status.contains("gust 0.00m/s"));
		assertTrue(status.contains("shear 0.00m/s2"));
		assertTrue(status.contains("imu clip G 0.00 A 0.00 dterm 78Hz"));
		assertTrue(status.contains("diagnostic idle"));
		assertTrue(status.contains("forces lift 1.4N sep 0.00 flap 0.0deg cushion 0.2N wash 0.8N wall 0.3N"));
		assertTrue(status.contains("baro 14.6m 0.4m/s 1011.5hPa err 0.04m"));
		assertTrue(status.contains("esc 41.0C 1.00 cool 1.25"));
		assertTrue(status.contains("sig 0.002/0.003s err 0.0007"));
		assertTrue(status.contains("load"));
		assertTrue(status.contains("scrape 0.00"));
		assertTrue(status.contains("ETL"));
		assertTrue(status.contains("adv"));
		assertTrue(status.contains("tipmach 0.32"));
		assertTrue(status.contains("skew"));
		assertTrue(status.contains("water 0.00"));
		assertTrue(status.contains("rain 0.00"));
		assertTrue(status.contains("temp 22.0C"));
		assertTrue(status.contains("coning 0.00"));
		assertTrue(status.contains("prop strikes 0 last none"));
		assertTrue(status.contains("warnings none"));
	}

	@Test
	void degradedStatusNamesTheFlightRisks() {
		DroneStatusFormatter.Telemetry telemetry = telemetry(
				true,
				false,
				false,
				true,
				0.10,
				0.70,
				0.72,
				0.24,
				0.90,
				0.42,
				0.62,
				0.62,
				9.2,
				0.62,
				0.74,
				0.58,
				0.47,
				1.12,
				0.12,
				0.52,
				0.18,
				0.68,
				0.50,
				0.48,
				0.74,
				true
		);

		String warnings = DroneStatusFormatter.warnings(telemetry);
		assertTrue(warnings.contains("failsafe"));
		assertTrue(warnings.contains("raw-link-lost"));
		assertTrue(warnings.contains("fc-link-lost"));
		assertTrue(warnings.contains("battery-limit"));
		assertTrue(warnings.contains("current-limit"));
		assertTrue(warnings.contains("bus-spike"));
		assertTrue(warnings.contains("gusty-air"));
		assertTrue(warnings.contains("contact-impact"));
		assertTrue(warnings.contains("ground-slide"));
		assertTrue(warnings.contains("contact-tumble"));
		assertTrue(warnings.contains("imu-clip"));
		assertTrue(warnings.contains("esc-thermal-limit"));
		assertTrue(warnings.contains("esc-desync"));
		assertTrue(warnings.contains("rotor-stall"));
		assertTrue(warnings.contains("rotor-coning"));
		assertTrue(warnings.contains("airframe-separation"));
		assertTrue(warnings.contains("rotor-flapping"));
		assertTrue(warnings.contains("high-advance"));
		assertTrue(warnings.contains("tip-mach"));
		assertTrue(warnings.contains("vrs"));
		assertTrue(warnings.contains("propwash"));
		assertTrue(warnings.contains("baro-disturbed"));
		assertTrue(warnings.contains("drone-wake"));
		assertTrue(warnings.contains("rotor-wake"));
		assertTrue(warnings.contains("ceiling-effect"));
		assertTrue(warnings.contains("env-asymmetry"));
		assertTrue(warnings.contains("rotor-flow-blocked"));
		assertTrue(warnings.contains("water-ingress"));
		assertTrue(warnings.contains("rain-wet"));
		assertTrue(warnings.contains("hot-air"));
		assertTrue(warnings.contains("prop-scrape"));
		assertTrue(warnings.contains("wall-effect"));
		assertTrue(warnings.contains("dirty-air"));

		String status = DroneStatusFormatter.format(telemetry);
		assertTrue(status.contains("diagnostic roll_step 4.5/16.0s"));
		assertTrue(status.contains("prop strikes 3 last r2/0.11"));
		assertTrue(status.contains("warnings "));
	}

	private static DroneStatusFormatter.Telemetry telemetry(
			boolean armed,
			boolean rawLink,
			boolean processedLink,
			boolean failsafe,
			double batterySoc,
			double frameHealth,
			double rotorHealth,
			double escDesync,
			double mixerSaturation,
			double rotorStall,
			double propwash,
			double airframeSeparation,
			double rotorFlappingTilt,
			double rotorAdvanceRatio,
			double rotorTipMach,
			double droneWake,
			double rotorWakeInterference,
			double ceilingEffect,
			double environmentAsymmetry,
			double rotorFlowObstruction,
			double waterImmersion,
			double precipitationWetness,
			double vibration,
			double rotorConing,
			double obstacleProximity,
			boolean diagnosticActive
	) {
		return new DroneStatusFormatter.Telemetry(
				armed,
				FlightMode.HORIZON,
				rawLink,
				processedLink,
				failsafe,
				failsafe ? 0.48 : 0.0,
				0.003,
				0.0067,
				0.0005,
				0.42,
				0.05,
				-0.03,
				0.02,
				7.5,
				diagnosticActive ? 5.1 : 0.0,
				diagnosticActive ? 4.2 : 0.0,
				diagnosticActive ? 0.8 : 0.0,
				diagnosticActive ? 720.0 : 0.0,
				8.1,
				11.0,
				-3.0,
				1.4,
				airframeSeparation,
				rotorFlappingTilt,
				0.2,
				0.8,
				diagnosticActive ? 3.1 : 0.3,
				14.6,
				0.4,
				1011.5,
				diagnosticActive ? 1.8 : 0.04,
				15.2,
				0.35,
				diagnosticActive ? 6.5 : 0.0,
				diagnosticActive ? 0.42 : 0.0,
				batterySoc,
				38.0,
				batterySoc < 0.20 ? 0.75 : 1.0,
				batterySoc < 0.20 ? 0.76 : 1.0,
				diagnosticActive ? 0.12 : 0.0,
				diagnosticActive ? 0.08 : 0.0,
				78.0,
				frameHealth,
				rotorHealth,
				52.0,
				rotorHealth < 0.80 ? 0.95 : 1.0,
				diagnosticActive ? 82.0 : 41.0,
				diagnosticActive ? 0.72 : 1.0,
				diagnosticActive ? 0.62 : 1.25,
				escDesync,
				diagnosticActive ? 0.004 : 0.002,
				0.0025,
				diagnosticActive ? 0.084 : 0.0007,
				1.08,
				diagnosticActive ? 0.55 : 0.0,
				propwash,
				propwash > 0.55 ? 0.42 : 0.0,
				droneWake > 0.55 ? 0.36 : 0.0,
				rotorAdvanceRatio,
				rotorTipMach,
				droneWake > 0.55 ? 0.28 : 0.0,
				rotorWakeInterference,
				droneWake,
				ceilingEffect,
				environmentAsymmetry,
				rotorFlowObstruction,
				waterImmersion,
				precipitationWetness,
				diagnosticActive ? 46.0 : 22.0,
				rotorStall,
				vibration,
				rotorConing,
				mixerSaturation,
				3.2,
				2.7,
				diagnosticActive ? 1.3 : 0.0,
				diagnosticActive ? 5.2 : 0.0,
				0.22,
				obstacleProximity,
				1.08,
				256,
				6000,
				diagnosticActive ? 3 : 0,
				diagnosticActive ? 2 : -1,
				diagnosticActive ? 0.11 : 0.0,
				diagnosticActive,
				diagnosticActive ? "roll_step" : "idle",
				diagnosticActive ? 4.5 : 0.0,
				diagnosticActive ? 16.0 : 0.0
		);
	}
}
