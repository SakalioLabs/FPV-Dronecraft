package com.tenicana.dronecraft.sim.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1ShadowScenarios;
import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1ShadowScenarios.ScenarioResult;
import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1RuntimeShadowAdapter.RotorShadowSample;
import com.tenicana.dronecraft.sim.Vec3;

/** Exports deterministic current-runtime versus DA4002 v1 shadow scenarios. */
public final class UiucDa4002AxialSurfaceV1ShadowScenarioExporter {
	public static final String SCENARIO_FILE_NAME = "scenarios.csv";
	public static final String ROTOR_FILE_NAME = "rotors.csv";
	public static final String SUMMARY_FILE_NAME = "summary.md";
	private static final String SCENARIO_HEADER = String.join(",",
			"scenario_id", "scenario_kind", "propeller_id", "initial_rpm",
			"actual_mean_rpm", "target_j", "actual_mean_j", "rotor_count",
			"scalar_comparable_rotors", "vector_comparable_rotors", "blocked_rotors",
			"statuses", "actual_thrust_n", "reference_thrust_n", "thrust_residual_n",
			"thrust_residual_fraction", "actual_power_w", "reference_power_w",
			"power_residual_w", "power_residual_fraction", "actual_torque_nm",
			"reference_torque_nm", "torque_residual_nm", "torque_residual_fraction",
			"force_residual_x_n", "force_residual_y_n", "force_residual_z_n",
			"torque_residual_x_nm", "torque_residual_y_nm", "torque_residual_z_nm",
			"runtime_force_applied"
	);
	private static final String ROTOR_HEADER = String.join(",",
			"scenario_id", "propeller_id", "rotor_index", "spin_direction",
			"axis_x", "axis_y", "axis_z", "rpm", "advance_ratio_j", "status",
			"geometry_matched", "scalar_comparable", "vector_comparable",
			"actual_thrust_n", "reference_thrust_n", "thrust_residual_n",
			"actual_power_w", "reference_power_w", "power_residual_w",
			"actual_torque_nm", "reference_torque_nm", "torque_residual_nm",
			"actual_power_closure_residual_w", "actual_force_x_n", "actual_force_y_n",
			"actual_force_z_n", "reference_force_x_n", "reference_force_y_n",
			"reference_force_z_n", "force_residual_x_n", "force_residual_y_n",
			"force_residual_z_n", "actual_torque_x_nm", "actual_torque_y_nm",
			"actual_torque_z_nm", "reference_torque_x_nm", "reference_torque_y_nm",
			"reference_torque_z_nm", "torque_residual_x_nm", "torque_residual_y_nm",
			"torque_residual_z_nm", "runtime_force_applied", "message"
	);

	private UiucDa4002AxialSurfaceV1ShadowScenarioExporter() {
	}

	public static void main(String[] args) throws IOException {
		Path outputDirectory = args.length >= 1 && !args[0].isBlank()
				? Path.of(args[0])
				: Path.of("build", "uiuc-da4002-axial-surface-v1-shadow");
		write(outputDirectory);
	}

	public static void write(Path outputDirectory) throws IOException {
		if (outputDirectory == null) {
			throw new IllegalArgumentException("outputDirectory must not be null.");
		}
		List<ScenarioResult> results = UiucDa4002AxialSurfaceV1ShadowScenarios.runAll();
		Files.createDirectories(outputDirectory);
		Files.writeString(outputDirectory.resolve(SCENARIO_FILE_NAME),
				String.join("\n", scenarioCsvLines(results)) + "\n", StandardCharsets.UTF_8);
		Files.writeString(outputDirectory.resolve(ROTOR_FILE_NAME),
				String.join("\n", rotorCsvLines(results)) + "\n", StandardCharsets.UTF_8);
		Files.writeString(outputDirectory.resolve(SUMMARY_FILE_NAME),
				String.join("\n", summaryMarkdownLines(results)) + "\n",
				StandardCharsets.UTF_8);
	}

	public static List<String> scenarioCsvLines(List<ScenarioResult> results) {
		List<ScenarioResult> rows = copyResults(results);
		List<String> lines = new ArrayList<>();
		lines.add(SCENARIO_HEADER);
		for (ScenarioResult result : rows) {
			var shadow = result.shadow();
			Vec3 forceResidual = shadow.forceResidualBodyNewtons();
			Vec3 torqueResidual = shadow.torqueResidualBodyNewtonMeters();
			lines.add(String.join(",",
					escape(result.definition().id()),
					escape(result.definition().kind().name()),
					escape(result.definition().propeller().id()),
					number(result.definition().initialRpm()),
					number(result.actualMeanRpm()),
					number(result.definition().targetAdvanceRatioJ()),
					number(result.actualMeanAdvanceRatioJ()),
					Integer.toString(shadow.rotorSamples().size()),
					Integer.toString(shadow.comparableScalarRotorCount()),
					Integer.toString(shadow.comparableVectorRotorCount()),
					Integer.toString(shadow.blockedRotorCount()),
					escape(statuses(result)),
					number(shadow.actualTotalThrustNewtons()),
					number(shadow.referenceTotalThrustNewtons()),
					number(shadow.thrustResidualNewtons()),
					number(result.thrustResidualFraction()),
					number(shadow.actualTotalShaftPowerWatts()),
					number(shadow.referenceTotalShaftPowerWatts()),
					number(shadow.shaftPowerResidualWatts()),
					number(result.shaftPowerResidualFraction()),
					number(shadow.actualTotalShaftTorqueNewtonMeters()),
					number(shadow.referenceTotalShaftTorqueNewtonMeters()),
					number(shadow.shaftTorqueResidualNewtonMeters()),
					number(result.shaftTorqueResidualFraction()),
					number(forceResidual.x()), number(forceResidual.y()),
					number(forceResidual.z()), number(torqueResidual.x()),
					number(torqueResidual.y()), number(torqueResidual.z()),
					Boolean.toString(shadow.runtimeForceApplied())
			));
		}
		return List.copyOf(lines);
	}

	public static List<String> rotorCsvLines(List<ScenarioResult> results) {
		List<ScenarioResult> rows = copyResults(results);
		List<String> lines = new ArrayList<>();
		lines.add(ROTOR_HEADER);
		for (ScenarioResult result : rows) {
			for (RotorShadowSample rotor : result.shadow().rotorSamples()) {
				lines.add(rotorCsvLine(result, rotor));
			}
		}
		return List.copyOf(lines);
	}

	public static List<String> summaryMarkdownLines(List<ScenarioResult> results) {
		List<ScenarioResult> rows = copyResults(results);
		int rotorRows = rows.stream().mapToInt(row -> row.shadow().rotorSamples().size()).sum();
		int scalarComparable = rows.stream()
				.mapToInt(row -> row.shadow().comparableScalarRotorCount()).sum();
		int vectorComparable = rows.stream()
				.mapToInt(row -> row.shadow().comparableVectorRotorCount()).sum();
		int blocked = rows.stream().mapToInt(row -> row.shadow().blockedRotorCount()).sum();
		List<String> lines = new ArrayList<>();
		lines.add("# UIUC DA4002 Axial Surface V1 Runtime Shadow Scenarios");
		lines.add("");
		lines.add("These rows compare the existing runtime rotor telemetry with the "
				+ "read-only DA4002 v1 reference. They do not apply v1 forces.");
		lines.add("");
		lines.add("- Runtime telemetry step: `"
				+ number(UiucDa4002AxialSurfaceV1ShadowScenarios
						.RUNTIME_TELEMETRY_STEP_SECONDS) + " s`.");
		lines.add("- Coverage: `" + rows.size() + " scenarios / " + rotorRows
				+ " rotor rows`; scalar comparable `" + scalarComparable
				+ "`, vector comparable `" + vectorComparable + "`, blocked `" + blocked
				+ "`.");
		lines.add("- Runtime force applied by shadow: `false` for every row.");
		lines.add("");
		lines.add("| Scenario | Propeller | Kind | RPM | J | Scalar/vector/blocked | "
				+ "Actual T | Ref T | dT | Actual P | Ref P | dP | Actual Q | Ref Q | dQ |");
		lines.add("| --- | --- | --- | ---: | ---: | --- | ---: | ---: | ---: | "
				+ "---: | ---: | ---: | ---: | ---: | ---: |");
		for (ScenarioResult result : rows) {
			var shadow = result.shadow();
			lines.add(String.format(Locale.ROOT,
					"| `%s` | %s | %s | %s | %s | %d/%d/%d | %s | %s | %s | "
							+ "%s | %s | %s | %s | %s | %s |",
					result.definition().id(), result.definition().propeller().id(),
					result.definition().kind().name(), number(result.actualMeanRpm()),
					number(result.actualMeanAdvanceRatioJ()),
					shadow.comparableScalarRotorCount(), shadow.comparableVectorRotorCount(),
					shadow.blockedRotorCount(), number(shadow.actualTotalThrustNewtons()),
					number(shadow.referenceTotalThrustNewtons()),
					number(shadow.thrustResidualNewtons()),
					number(shadow.actualTotalShaftPowerWatts()),
					number(shadow.referenceTotalShaftPowerWatts()),
					number(shadow.shaftPowerResidualWatts()),
					number(shadow.actualTotalShaftTorqueNewtonMeters()),
					number(shadow.referenceTotalShaftTorqueNewtonMeters()),
					number(shadow.shaftTorqueResidualNewtonMeters())));
		}
		lines.add("");
		lines.add("Near-zero-thrust rows intentionally retain absolute residuals; "
				+ "a percentage residual is not meaningful when reference thrust approaches zero.");
		lines.add("Oblique rows are scalar axial-projection comparisons only. Out-of-envelope "
				+ "rows remain blocked and are excluded from comparable counts.");
		return List.copyOf(lines);
	}

	private static String rotorCsvLine(ScenarioResult result, RotorShadowSample rotor) {
		Vec3 axis = rotor.rotor().thrustAxisBody();
		Vec3 actualForce = rotor.actualForceBodyNewtons();
		Vec3 referenceForce = rotor.referenceForceBodyNewtons();
		Vec3 forceResidual = rotor.forceResidualBodyNewtons();
		Vec3 actualTorque = rotor.actualTorqueBodyNewtonMeters();
		Vec3 referenceTorque = rotor.referenceTorqueBodyNewtonMeters();
		Vec3 torqueResidual = rotor.torqueResidualBodyNewtonMeters();
		double advanceRatio = rotor.reference()
				.map(sample -> sample.signedAdvanceRatioJ()).orElse(0.0);
		return String.join(",",
				escape(result.definition().id()),
				escape(result.definition().propeller().id()),
				Integer.toString(rotor.rotorIndex()),
				Integer.toString(rotor.rotor().spinDirection()),
				number(axis.x()), number(axis.y()), number(axis.z()),
				number(rotor.rpm()), number(advanceRatio), escape(rotor.status().name()),
				Boolean.toString(rotor.geometryMatch().matched()),
				Boolean.toString(rotor.scalarReferenceComparable()),
				Boolean.toString(rotor.vectorReferenceComparable()),
				number(rotor.actualThrustNewtons()), number(rotor.referenceThrustNewtons()),
				number(rotor.thrustResidualNewtons()), number(rotor.actualShaftPowerWatts()),
				number(rotor.referenceShaftPowerWatts()),
				number(rotor.shaftPowerResidualWatts()),
				number(rotor.actualShaftTorqueNewtonMeters()),
				number(rotor.referenceShaftTorqueNewtonMeters()),
				number(rotor.shaftTorqueResidualNewtonMeters()),
				number(rotor.actualPowerClosureResidualWatts()),
				number(actualForce.x()), number(actualForce.y()), number(actualForce.z()),
				number(referenceForce.x()), number(referenceForce.y()),
				number(referenceForce.z()), number(forceResidual.x()),
				number(forceResidual.y()), number(forceResidual.z()),
				number(actualTorque.x()), number(actualTorque.y()), number(actualTorque.z()),
				number(referenceTorque.x()), number(referenceTorque.y()),
				number(referenceTorque.z()), number(torqueResidual.x()),
				number(torqueResidual.y()), number(torqueResidual.z()),
				Boolean.toString(rotor.runtimeForceApplied()), escape(rotor.message())
		);
	}

	private static String statuses(ScenarioResult result) {
		return result.shadow().rotorSamples().stream()
				.map(rotor -> rotor.status().name())
				.distinct()
				.collect(Collectors.joining("+"));
	}

	private static List<ScenarioResult> copyResults(List<ScenarioResult> results) {
		if (results == null || results.stream().anyMatch(result -> result == null)) {
			throw new IllegalArgumentException("results must not contain null.");
		}
		return List.copyOf(results);
	}

	private static String number(double value) {
		return String.format(Locale.ROOT, "%.15g", value);
	}

	private static String escape(String value) {
		String text = value == null ? "" : value;
		return '"' + text.replace("\"", "\"\"") + '"';
	}
}
