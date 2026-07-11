package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.FlightMode;

class PlayableFlightAuthorityMetricsTest {
	private static final float HOVER_THROTTLE = 0.20f;
	private static final float TICK_SECONDS = 0.05f;
	private static final int REPORT_TICKS = 180;

	@Test
	void writesAcroAuthorityBaselineVersusCandidateReport() throws IOException {
		List<ScenarioResult> legacy = scenarioSet(PlayableFlightPreset.LEGACY_HEAVY_RACING_QUAD);
		List<ScenarioResult> candidate = scenarioSet(PlayableFlightPreset.FIVE_INCH_AGILE_CANDIDATE);

		for (ScenarioResult result : combined(legacy, candidate)) {
			assertTrue(result.finite(), result.preset().id() + "/" + result.id() + " produced non-finite values");
			assertTrue(result.maxAchievedRollRateDps() >= 0.0f);
			assertTrue(result.maxEffectiveCollectiveThrustToWeight() >= 0.0f);
		}

		Path report = Path.of(System.getProperty(
				"fpvdrone.flightAuthorityReport",
				"build/reports/fpvdrone/agile-flight-authority-baseline-vs-candidate.md"
		));
		Files.createDirectories(report.getParent());
		Files.writeString(report, renderReport(legacy, candidate), StandardCharsets.UTF_8);
		assertTrue(Files.exists(report));
	}

	private static List<ScenarioResult> scenarioSet(PlayableFlightPreset preset) {
		return List.of(
				runScenario(
						preset,
						"stationary_hover_full_roll",
						"Static hover, full right roll",
						new ControlPlan(0.62f, 0.0f, 1.0f, 0.0f),
						new PlayableFlightModel.State(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, FlightMode.ACRO),
						REPORT_TICKS
				),
				runScenario(
						preset,
						"forward_20mps_full_roll",
						"20 m/s forward, full right roll",
						new ControlPlan(0.66f, 0.0f, 1.0f, 0.0f),
						new PlayableFlightModel.State(0.0f, 0.0f, 20.0f, 0.0f, 0.0f, 0.0f, FlightMode.ACRO),
						REPORT_TICKS
				),
				runScenario(
						preset,
						"diagonal_pitch_roll",
						"Diagonal full pitch + roll",
						new ControlPlan(0.66f, 1.0f, 1.0f, 0.0f),
						new PlayableFlightModel.State(0.0f, 0.0f, 12.0f, 0.0f, 0.0f, 0.0f, FlightMode.ACRO),
						REPORT_TICKS
				),
				runReversalScenario(preset),
				runScenario(
						preset,
						"forward_sink_pullout",
						"20 m/s forward, -8 m/s sink, full throttle pullout",
						new ControlPlan(1.0f, 0.0f, 0.0f, 0.0f),
						new PlayableFlightModel.State(0.0f, -8.0f, 20.0f, (float) Math.toRadians(-18.0), 0.0f, 0.0f, FlightMode.ACRO),
						REPORT_TICKS
				),
				runScenario(
						preset,
						"high_speed_diagonal_pullout",
						"High speed diagonal, -6 m/s sink, pitch+roll pullout",
						new ControlPlan(1.0f, 1.0f, -0.55f, 0.0f),
						new PlayableFlightModel.State(12.0f, -6.0f, 22.0f, (float) Math.toRadians(-16.0), (float) Math.toRadians(12.0), 0.0f, FlightMode.ACRO),
						REPORT_TICKS
				),
				runReleaseScenario(preset)
		);
	}

	private static ScenarioResult runReversalScenario(PlayableFlightPreset preset) {
		RunAccumulator run = new RunAccumulator(preset, "roll_reversal", "Full right roll then full left roll reversal");
		PlayableFlightModel.State state = PlayableFlightModel.State.zero(FlightMode.ACRO);
		int reversalTick = 26;
		for (int tick = 0; tick < REPORT_TICKS; tick++) {
			float roll = tick < reversalTick ? 1.0f : -1.0f;
			state = stepAndRecord(run, state, new ControlPlan(0.66f, 0.0f, roll, 0.0f), tick);
		}
		return run.finish(reversalTick);
	}

	private static ScenarioResult runReleaseScenario(PlayableFlightPreset preset) {
		RunAccumulator run = new RunAccumulator(preset, "cruise_release", "Cruise, then centered sticks recovery");
		PlayableFlightModel.State state = new PlayableFlightModel.State(0.0f, 0.0f, 16.0f, (float) Math.toRadians(14.0), 0.0f, 0.0f, FlightMode.ACRO);
		for (int tick = 0; tick < REPORT_TICKS; tick++) {
			ControlPlan controls = tick < 70
					? new ControlPlan(0.58f, 0.35f, 0.0f, 0.0f)
					: new ControlPlan(0.42f, 0.0f, 0.0f, 0.0f);
			state = stepAndRecord(run, state, controls, tick);
		}
		return run.finish(-1);
	}

	private static ScenarioResult runScenario(
			PlayableFlightPreset preset,
			String id,
			String label,
			ControlPlan controls,
			PlayableFlightModel.State initialState,
			int ticks
	) {
		RunAccumulator run = new RunAccumulator(preset, id, label);
		PlayableFlightModel.State state = initialState;
		for (int tick = 0; tick < ticks; tick++) {
			state = stepAndRecord(run, state, controls, tick);
		}
		return run.finish(-1);
	}

	private static PlayableFlightModel.State stepAndRecord(
			RunAccumulator run,
			PlayableFlightModel.State state,
			ControlPlan controls,
			int tick
	) {
		PlayableFlightModel.AcroAuthorityDiagnostics authority = PlayableFlightModel.acroAuthorityDiagnostics(
				run.preset(),
				state,
				controls.throttle(),
				controls.pitch(),
				controls.roll(),
				controls.yaw(),
				HOVER_THROTTLE
		);
		PlayableFlightModel.Step step = PlayableFlightModel.step(
				run.preset(),
				FlightMode.ACRO,
				controls.throttle(),
				controls.pitch(),
				controls.roll(),
				controls.yaw(),
				HOVER_THROTTLE,
				false,
				1.0f,
				state
		);
		run.record(tick, controls, authority, step);
		return new PlayableFlightModel.State(
				step.velocityX(),
				step.velocityY(),
				step.velocityZ(),
				step.pitchRadians(),
				step.rollRadians(),
				step.yawDegreesPerTick(),
				step.mode(),
				step.modeSwitchTicksRemaining(),
				step.acroCollectiveThrustToWeight(),
				step.acroPitchRateRadiansPerTick(),
				step.acroRollRateRadiansPerTick(),
				step.acroRollRecoveryTicksRemaining(),
				step.acroAeroCrossflowLag(),
				step.acroSidewashMemory()
		);
	}

	private static List<ScenarioResult> combined(List<ScenarioResult> legacy, List<ScenarioResult> candidate) {
		List<ScenarioResult> results = new ArrayList<>(legacy.size() + candidate.size());
		results.addAll(legacy);
		results.addAll(candidate);
		return results;
	}

	private static String renderReport(List<ScenarioResult> legacy, List<ScenarioResult> candidate) {
		List<ScenarioResult> results = combined(legacy, candidate);
		StringBuilder report = new StringBuilder();
		report.append("# Agile flight authority baseline versus candidate\n\n");
		report.append("This report compares the frozen `legacy_heavy_racing_quad` playable ACRO behavior against the explicit `5inch_agile_candidate`. It records metrics only; it does not update golden traces.\n\n");
		report.append("| Preset | Scenario | Max commanded roll dps | Max achieved roll dps | Rise 10-90 ms | Reversal ms | 360 roll s | Pullout loss m | Max T/W | Min combined thrust scale | First bottleneck |\n");
		report.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
		for (ScenarioResult result : results) {
			report.append("| ")
					.append(result.preset().id())
					.append(" | ")
					.append(result.label())
					.append(" | ")
					.append(format(result.maxCommandedRollRateDps()))
					.append(" | ")
					.append(format(result.maxAchievedRollRateDps()))
					.append(" | ")
					.append(format(result.rollRiseTimeMs()))
					.append(" | ")
					.append(format(result.reversalTimeMs()))
					.append(" | ")
					.append(format(result.roll360Seconds()))
					.append(" | ")
					.append(format(result.pulloutAltitudeLossMeters()))
					.append(" | ")
					.append(format(result.maxEffectiveCollectiveThrustToWeight()))
					.append(" | ")
					.append(format(result.minCombinedThrustScale()))
					.append(" | ")
					.append(result.firstBottleneck())
					.append(" |\n");
		}
		report.append("\n## Layer minima\n\n");
		report.append("| Preset | Scenario | Motor authority | Rate smoothing | Aero rate keep | Residual torque keep | Rotor gyro keep | Advance scale | Dynamic inflow scale | Vertical thrust T/W |\n");
		report.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
		for (ScenarioResult result : results) {
			report.append("| ")
					.append(result.preset().id())
					.append(" | ")
					.append(result.label())
					.append(" | ")
					.append(format(result.minMotorAuthority()))
					.append(" | ")
					.append(format(result.minRateSmoothingKeep()))
					.append(" | ")
					.append(format(result.minAeroRateKeep()))
					.append(" | ")
					.append(format(result.minResidualTorqueKeep()))
					.append(" | ")
					.append(format(result.minRotorGyroKeep()))
					.append(" | ")
					.append(format(result.minAdvanceRatioScale()))
					.append(" | ")
					.append(format(result.minDynamicInflowScale()))
					.append(" | ")
					.append(format(result.maxEffectiveVerticalThrustToWeight()))
					.append(" |\n");
		}
		return report.toString();
	}

	private static String format(float value) {
		if (!Float.isFinite(value)) {
			return "n/a";
		}
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private record ControlPlan(float throttle, float pitch, float roll, float yaw) {
	}

	private record ScenarioResult(
			PlayableFlightPreset preset,
			String id,
			String label,
			boolean finite,
			float maxCommandedRollRateDps,
			float maxAchievedRollRateDps,
			float rollRiseTimeMs,
			float reversalTimeMs,
			float roll360Seconds,
			float pulloutAltitudeLossMeters,
			float maxEffectiveCollectiveThrustToWeight,
			float maxEffectiveVerticalThrustToWeight,
			float minCombinedThrustScale,
			float minMotorAuthority,
			float minRateSmoothingKeep,
			float minAeroRateKeep,
			float minResidualTorqueKeep,
			float minRotorGyroKeep,
			float minAdvanceRatioScale,
			float minDynamicInflowScale,
			String firstBottleneck
	) {
	}

	private static final class RunAccumulator {
		private final PlayableFlightPreset preset;
		private final String id;
		private final String label;
		private final List<Float> achievedRollRates = new ArrayList<>();
		private final List<Float> signedRollRates = new ArrayList<>();
		private boolean finite = true;
		private float maxCommandedRollRateDps;
		private float maxAchievedRollRateDps;
		private float maxEffectiveCollectiveThrustToWeight;
		private float maxEffectiveVerticalThrustToWeight;
		private float minCombinedThrustScale = Float.POSITIVE_INFINITY;
		private float minMotorAuthority = Float.POSITIVE_INFINITY;
		private float minRateSmoothingKeep = Float.POSITIVE_INFINITY;
		private float minAeroRateKeep = Float.POSITIVE_INFINITY;
		private float minResidualTorqueKeep = Float.POSITIVE_INFINITY;
		private float minRotorGyroKeep = Float.POSITIVE_INFINITY;
		private float minAdvanceRatioScale = Float.POSITIVE_INFINITY;
		private float minDynamicInflowScale = Float.POSITIVE_INFINITY;
		private float altitudeMeters;
		private float minAltitudeMeters;
		private int firstRoll360Tick = -1;
		private int firstBottleneckTick = -1;
		private String firstBottleneck = "none";

		private RunAccumulator(PlayableFlightPreset preset, String id, String label) {
			this.preset = preset;
			this.id = id;
			this.label = label;
		}

		private PlayableFlightPreset preset() {
			return preset;
		}

		private void record(
				int tick,
				ControlPlan controls,
				PlayableFlightModel.AcroAuthorityDiagnostics authority,
				PlayableFlightModel.Step step
		) {
			finite &= allFinite(authority, step);
			float commandedRollRateDps = Math.abs(degreesPerSecond(authority.targetRollRateRadiansPerTick()));
			float signedRollRateDps = degreesPerSecond(step.acroRollRateRadiansPerTick());
			float achievedRollRateDps = Math.abs(signedRollRateDps);
			achievedRollRates.add(achievedRollRateDps);
			signedRollRates.add(signedRollRateDps);
			maxCommandedRollRateDps = Math.max(maxCommandedRollRateDps, commandedRollRateDps);
			maxAchievedRollRateDps = Math.max(maxAchievedRollRateDps, achievedRollRateDps);
			maxEffectiveCollectiveThrustToWeight = Math.max(maxEffectiveCollectiveThrustToWeight, authority.effectiveCollectiveThrustToWeight());
			maxEffectiveVerticalThrustToWeight = Math.max(maxEffectiveVerticalThrustToWeight, authority.effectiveVerticalThrustToWeight());
			minCombinedThrustScale = Math.min(minCombinedThrustScale, authority.combinedThrustScale());
			minMotorAuthority = Math.min(minMotorAuthority, authority.motorRateAuthority());
			minRateSmoothingKeep = Math.min(minRateSmoothingKeep, rateSmoothingKeep(authority, controls));
			minAeroRateKeep = Math.min(minAeroRateKeep, 1.0f - Math.max(authority.aerodynamicPitchDamping(), authority.aerodynamicRollDamping()));
			minResidualTorqueKeep = Math.min(minResidualTorqueKeep, 1.0f - authority.residualTorqueLoad());
			minRotorGyroKeep = Math.min(minRotorGyroKeep, 1.0f - authority.rotorGyroLoad());
			minAdvanceRatioScale = Math.min(minAdvanceRatioScale, authority.advanceRatioThrustScale());
			minDynamicInflowScale = Math.min(minDynamicInflowScale, authority.dynamicInflowThrustScale());
			if (firstRoll360Tick < 0 && Math.abs(step.rollRadians()) >= Math.PI * 2.0) {
				firstRoll360Tick = tick;
			}
			altitudeMeters += step.velocityY() * TICK_SECONDS;
			minAltitudeMeters = Math.min(minAltitudeMeters, altitudeMeters);
			noteBottleneck(tick, "motor", authority.motorRateAuthority());
			noteBottleneck(tick, "rate_smoothing", rateSmoothingKeep(authority, controls));
			noteBottleneck(tick, "aero_rate", 1.0f - Math.max(authority.aerodynamicPitchDamping(), authority.aerodynamicRollDamping()));
			noteBottleneck(tick, "residual_torque", 1.0f - authority.residualTorqueLoad());
			noteBottleneck(tick, "rotor_gyro", 1.0f - authority.rotorGyroLoad());
			noteBottleneck(tick, "advance_ratio_thrust", authority.advanceRatioThrustScale());
			noteBottleneck(tick, "dynamic_inflow", authority.dynamicInflowThrustScale());
		}

		private ScenarioResult finish(int reversalTick) {
			float rollRise = riseTimeMs();
			float reversal = reversalTick >= 0 ? reversalTimeMs(reversalTick) : Float.NaN;
			float roll360 = firstRoll360Tick < 0 ? Float.NaN : (firstRoll360Tick + 1) * TICK_SECONDS;
			return new ScenarioResult(
					preset,
					id,
					label,
					finite,
					maxCommandedRollRateDps,
					maxAchievedRollRateDps,
					rollRise,
					reversal,
					roll360,
					-minAltitudeMeters,
					maxEffectiveCollectiveThrustToWeight,
					maxEffectiveVerticalThrustToWeight,
					finiteMin(minCombinedThrustScale),
					finiteMin(minMotorAuthority),
					finiteMin(minRateSmoothingKeep),
					finiteMin(minAeroRateKeep),
					finiteMin(minResidualTorqueKeep),
					finiteMin(minRotorGyroKeep),
					finiteMin(minAdvanceRatioScale),
					finiteMin(minDynamicInflowScale),
					firstBottleneck
			);
		}

		private void noteBottleneck(int tick, String name, float keep) {
			if (firstBottleneckTick >= 0 || !Float.isFinite(keep) || keep >= 0.92f) {
				return;
			}
			firstBottleneckTick = tick;
			firstBottleneck = name + "@tick" + tick + "=" + format(keep);
		}

		private float riseTimeMs() {
			if (maxAchievedRollRateDps <= 1.0e-6f) {
				return Float.NaN;
			}
			float low = maxAchievedRollRateDps * 0.10f;
			float high = maxAchievedRollRateDps * 0.90f;
			int lowTick = -1;
			for (int i = 0; i < achievedRollRates.size(); i++) {
				float value = achievedRollRates.get(i);
				if (lowTick < 0 && value >= low) {
					lowTick = i;
				}
				if (lowTick >= 0 && value >= high) {
					return (i - lowTick) * TICK_SECONDS * 1000.0f;
				}
			}
			return Float.NaN;
		}

		private float reversalTimeMs(int reversalTick) {
			for (int i = reversalTick; i < signedRollRates.size(); i++) {
				if (signedRollRates.get(i) < 0.0f) {
					return (i - reversalTick) * TICK_SECONDS * 1000.0f;
				}
			}
			return Float.NaN;
		}
	}

	private static float rateSmoothingKeep(PlayableFlightModel.AcroAuthorityDiagnostics authority, ControlPlan controls) {
		float keep = 1.0f;
		if (Math.abs(controls.pitch()) > 1.0e-6f && Math.abs(authority.motorLimitedPitchRateRadiansPerTick()) > 1.0e-6f) {
			keep = Math.min(keep, Math.abs(authority.responsivePitchRateRadiansPerTick() / authority.motorLimitedPitchRateRadiansPerTick()));
		}
		if (Math.abs(controls.roll()) > 1.0e-6f && Math.abs(authority.motorLimitedRollRateRadiansPerTick()) > 1.0e-6f) {
			keep = Math.min(keep, Math.abs(authority.responsiveRollRateRadiansPerTick() / authority.motorLimitedRollRateRadiansPerTick()));
		}
		return keep;
	}

	private static float degreesPerSecond(float radiansPerTick) {
		return (float) Math.toDegrees(radiansPerTick / TICK_SECONDS);
	}

	private static float finiteMin(float value) {
		return Float.isFinite(value) ? value : Float.NaN;
	}

	private static boolean allFinite(
			PlayableFlightModel.AcroAuthorityDiagnostics authority,
			PlayableFlightModel.Step step
	) {
		return Float.isFinite(authority.finalPitchRateRadiansPerTick())
				&& Float.isFinite(authority.finalRollRateRadiansPerTick())
				&& Float.isFinite(authority.effectiveCollectiveThrustToWeight())
				&& Float.isFinite(step.velocityX())
				&& Float.isFinite(step.velocityY())
				&& Float.isFinite(step.velocityZ())
				&& Float.isFinite(step.pitchRadians())
				&& Float.isFinite(step.rollRadians());
	}
}
