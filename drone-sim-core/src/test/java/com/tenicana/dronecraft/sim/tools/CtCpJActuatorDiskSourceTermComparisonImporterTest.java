package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJActuatorDiskSourceTermComparisonImporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void exactHoverSourceTermRowClosesAgainstReference() {
		Map<String, String> reference = referenceRecord("static_anchored_source_hover", "raw_source", 0);
		String input = cfdCsv(reference);

		CtCpJActuatorDiskSourceTermComparisonImporter.ComparisonRow comparison =
				CtCpJActuatorDiskSourceTermComparisonImporter
						.compare(input, RHO, SOURCE_THICKNESS)
						.get(0);

		assertTrue(comparison.comparable());
		assertEquals("ct-cp-j-actuator-disk-source-term-comparison-ready", comparison.message());
		assertTrue(comparison.reference().applied());
		assertEquals(0.0, comparison.pressureJumpResidualPascals(), 1.0e-12);
		assertEquals(0.0, comparison.massFluxResidualKilogramsPerSecondSquareMeter(), 1.0e-12);
		assertEquals(0.0, comparison.idealMomentumPowerLoadingResidualWattsPerSquareMeter(), 1.0e-10);
		assertEquals(0.0, comparison.thrustSurfaceForceResidualWorldNewtonsPerSquareMeter().length(), 1.0e-12);
		assertEquals(0.0, comparison.integratedThrustForceResidualWorldNewtons().length(), 1.0e-12);
		assertEquals(0.0, comparison.bodyForceDensityResidualWorldNewtonsPerCubicMeter().length(), 1.0e-9);
		assertEquals(0.0, comparison.farWakeAxialVelocityResidualWorldMetersPerSecond().length(), 1.0e-12);
		assertEquals(0.0, comparison.cfdIntegratedForceClosureResidualWorldNewtons().length(), 1.0e-12);
		assertEquals(0.0, comparison.cfdBodyForceDensityClosureResidualWorldNewtonsPerCubicMeter().length(), 1.0e-9);

		Map<String, String> output = outputRecord(
				CtCpJActuatorDiskSourceTermComparisonImporter.csvLines(input, RHO, SOURCE_THICKNESS));
		assertEquals("apDrone", output.get("preset"));
		assertEquals("static_anchored_source_hover", output.get("case"));
		assertEquals("raw_source", output.get("row_kind"));
		assertEquals("INTERPOLATED", output.get("reference_lookup_status"));
		assertEquals("true", output.get("reference_applied"));
		assertEquals("true", output.get("comparable"));
		assertEquals("synthetic-source-term", output.get("source_case_sha256"));
		assertEquals(0.0, Double.parseDouble(output.get("integrated_thrust_force_residual_magnitude_n")),
				1.0e-12);
		assertEquals(0.0, Double.parseDouble(output.get("body_force_density_residual_magnitude_n_m3")),
				1.0e-9);
	}

	@Test
	void perturbedMidJSourceTermRowReportsSignedResidualsAndClosure() {
		Map<String, String> reference = referenceRecord("static_anchored_source_mid_j", "raw_source", 0);
		double pressureScale = 1.05;
		double massFluxScale = 0.98;
		double powerLoadingScale = 1.03;
		double bodyForceScale = 1.04;
		double farWakeScale = 0.97;
		String input = cfdCsv(
				reference,
				pressureScale,
				massFluxScale,
				powerLoadingScale,
				bodyForceScale,
				farWakeScale
		);

		CtCpJActuatorDiskSourceTermComparisonImporter.ComparisonRow comparison =
				CtCpJActuatorDiskSourceTermComparisonImporter
						.compare(input, RHO, SOURCE_THICKNESS)
						.get(0);

		double referencePressure = number(reference, "pressure_jump_pa");
		double referenceMassFlux = number(reference, "mass_flux_kg_s_m2");
		double referencePowerLoading = number(reference, "ideal_momentum_power_loading_w_m2");
		double referenceSurfaceY = number(reference, "thrust_surface_force_world_y_n_m2");
		double referenceIntegratedY = number(reference, "integrated_thrust_force_world_y_n");
		double referenceBodyForceY = number(reference, "body_force_density_world_y_n_m3");
		double referenceFarWakeY = number(reference, "far_wake_axial_velocity_world_y_mps");

		assertTrue(comparison.comparable());
		assertEquals(referencePressure * (pressureScale - 1.0),
				comparison.pressureJumpResidualPascals(), 1.0e-12);
		assertEquals(pressureScale - 1.0, comparison.pressureJumpResidualFraction(), 1.0e-12);
		assertEquals(referenceMassFlux * (massFluxScale - 1.0),
				comparison.massFluxResidualKilogramsPerSecondSquareMeter(), 1.0e-12);
		assertEquals(massFluxScale - 1.0, comparison.massFluxResidualFraction(), 1.0e-12);
		assertEquals(referencePowerLoading * (powerLoadingScale - 1.0),
				comparison.idealMomentumPowerLoadingResidualWattsPerSquareMeter(), 1.0e-10);
		assertEquals(powerLoadingScale - 1.0,
				comparison.idealMomentumPowerLoadingResidualFraction(), 1.0e-12);
		assertEquals(referenceSurfaceY * (pressureScale - 1.0),
				comparison.thrustSurfaceForceResidualWorldNewtonsPerSquareMeter().y(), 1.0e-12);
		assertEquals(referenceIntegratedY * (pressureScale - 1.0),
				comparison.integratedThrustForceResidualWorldNewtons().y(), 1.0e-12);
		assertEquals(referenceBodyForceY * (bodyForceScale - 1.0),
				comparison.bodyForceDensityResidualWorldNewtonsPerCubicMeter().y(), 1.0e-9);
		assertEquals(referenceFarWakeY * (farWakeScale - 1.0),
				comparison.farWakeAxialVelocityResidualWorldMetersPerSecond().y(), 1.0e-12);
		assertEquals(0.0, comparison.cfdIntegratedForceClosureResidualWorldNewtons().length(), 1.0e-12);
		assertEquals(referenceBodyForceY * (bodyForceScale - pressureScale),
				comparison.cfdBodyForceDensityClosureResidualWorldNewtonsPerCubicMeter().y(), 1.0e-9);
	}

	@Test
	void blockedHighJSourceTermComparesZeroLoadToOutOfEnvelopeReference() {
		Map<String, String> reference = referenceRecord("static_anchored_source_high_j_block", "raw_source", 0);
		String input = cfdCsv(reference);

		CtCpJActuatorDiskSourceTermComparisonImporter.ComparisonRow comparison =
				CtCpJActuatorDiskSourceTermComparisonImporter
						.compare(input, RHO, SOURCE_THICKNESS)
						.get(0);

		assertTrue(comparison.comparable());
		assertTrue(comparison.reference().blocked());
		assertEquals(false, comparison.reference().applied());
		assertEquals(0.0, comparison.pressureJumpResidualPascals(), 1.0e-15);
		assertEquals(0.0, comparison.thrustSurfaceForceResidualWorldNewtonsPerSquareMeter().length(), 1.0e-15);
		assertEquals(0.0, comparison.integratedThrustForceResidualWorldNewtons().length(), 1.0e-15);
		assertEquals(0.0, comparison.bodyForceDensityResidualWorldNewtonsPerCubicMeter().length(), 1.0e-15);

		Map<String, String> output = outputRecord(
				CtCpJActuatorDiskSourceTermComparisonImporter.csvLines(input, RHO, SOURCE_THICKNESS));
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", output.get("reference_lookup_status"));
		assertEquals("true", output.get("reference_blocked"));
		assertEquals("false", output.get("reference_applied"));
		assertEquals("true", output.get("comparable"));
	}

	@Test
	void writeCreatesParentDirectoriesAndComparisonCsv(@TempDir Path tempDir) throws IOException {
		Path input = tempDir.resolve("source-results.csv");
		Path output = tempDir.resolve("nested").resolve("source-comparison.csv");
		Files.writeString(input, cfdCsv(referenceRecord("static_anchored_source_hover", "raw_source", 0)));

		CtCpJActuatorDiskSourceTermComparisonImporter.write(input, output, RHO, SOURCE_THICKNESS);

		List<String> lines = Files.readAllLines(output);
		assertEquals(2, lines.size());
		assertTrue(lines.get(0).contains("pressure_jump_residual_pa"));
		assertTrue(lines.get(0).contains("cfd_body_force_density_closure_residual_world_y_n_m3"));
		assertTrue(lines.get(1).contains("ct-cp-j-actuator-disk-source-term-comparison-ready"));
	}

	private static String cfdCsv(Map<String, String> reference) {
		return cfdCsv(reference, 1.0, 1.0, 1.0, 1.0, 1.0);
	}

	private static String cfdCsv(
			Map<String, String> reference,
			double pressureScale,
			double massFluxScale,
			double powerLoadingScale,
			double bodyForceScale,
			double farWakeScale
	) {
		return String.join("\n",
				String.join(",",
						"preset",
						"case",
						"row_kind",
						"rotor_index",
						"source_thickness_m",
						"cfd_pressure_jump_pa",
						"cfd_mass_flux_kg_s_m2",
						"cfd_ideal_momentum_power_loading_w_m2",
						"cfd_integrated_thrust_force_world_x_n",
						"cfd_integrated_thrust_force_world_y_n",
						"cfd_integrated_thrust_force_world_z_n",
						"cfd_body_force_density_world_x_n_m3",
						"cfd_body_force_density_world_y_n_m3",
						"cfd_body_force_density_world_z_n_m3",
						"cfd_far_wake_axial_velocity_world_x_mps",
						"cfd_far_wake_axial_velocity_world_y_mps",
						"cfd_far_wake_axial_velocity_world_z_mps",
						"source_case_sha256",
						"solver_status"),
				row(
						reference.get("preset"),
						reference.get("case"),
						reference.get("row_kind"),
						reference.get("rotor_index"),
						reference.get("source_thickness_m"),
						number(reference, "pressure_jump_pa") * pressureScale,
						number(reference, "mass_flux_kg_s_m2") * massFluxScale,
						number(reference, "ideal_momentum_power_loading_w_m2") * powerLoadingScale,
						number(reference, "integrated_thrust_force_world_x_n") * pressureScale,
						number(reference, "integrated_thrust_force_world_y_n") * pressureScale,
						number(reference, "integrated_thrust_force_world_z_n") * pressureScale,
						number(reference, "body_force_density_world_x_n_m3") * bodyForceScale,
						number(reference, "body_force_density_world_y_n_m3") * bodyForceScale,
						number(reference, "body_force_density_world_z_n_m3") * bodyForceScale,
						number(reference, "far_wake_axial_velocity_world_x_mps") * farWakeScale,
						number(reference, "far_wake_axial_velocity_world_y_mps") * farWakeScale,
						number(reference, "far_wake_axial_velocity_world_z_mps") * farWakeScale,
						"synthetic-source-term",
						"CONVERGED"
				));
	}

	private static String row(Object... cells) {
		List<String> values = new java.util.ArrayList<>();
		for (Object cell : cells) {
			values.add(String.valueOf(cell));
		}
		return String.join(",", values);
	}

	private static Map<String, String> referenceRecord(String caseName, String rowKind, int rotorIndex) {
		List<String> lines = CtCpJActuatorDiskSourceTermExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		String line = lines.stream()
				.skip(1)
				.filter(candidate -> {
					String[] cells = candidate.split(",", -1);
					return cells[columns.get("case")].equals(caseName)
							&& cells[columns.get("row_kind")].equals(rowKind)
							&& Integer.parseInt(cells[columns.get("rotor_index")]) == rotorIndex;
				})
				.findFirst()
				.orElseThrow();
		String[] cells = line.split(",", -1);
		Map<String, String> record = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : columns.entrySet()) {
			record.put(entry.getKey(), cells[entry.getValue()]);
		}
		return record;
	}

	private static Map<String, String> outputRecord(List<String> lines) {
		Map<String, Integer> columns = columns(lines);
		String[] cells = lines.get(1).split(",", -1);
		Map<String, String> record = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : columns.entrySet()) {
			record.put(entry.getKey(), cells[entry.getValue()]);
		}
		return record;
	}

	private static Map<String, Integer> columns(List<String> lines) {
		String[] header = lines.get(0).split(",", -1);
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (int i = 0; i < header.length; i++) {
			columns.put(header[i], i);
		}
		return columns;
	}

	private static double number(Map<String, String> record, String columnName) {
		return Double.parseDouble(record.get(columnName));
	}
}
