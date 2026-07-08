package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJActuatorDiskWakeProbeComparisonImporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void exactHoverWakeProbeRowClosesAgainstReference() {
		Map<String, String> reference = referenceRecord("static_anchored_source_hover", "raw_source", 0, 1.0);
		String input = cfdCsv(reference);

		CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow comparison =
				CtCpJActuatorDiskWakeProbeComparisonImporter.compare(input, RHO, SOURCE_THICKNESS).get(0);

		assertTrue(comparison.comparable());
		assertEquals("ct-cp-j-actuator-disk-wake-probe-comparison-ready", comparison.message());
		assertTrue(comparison.reference().sourceEnabled());
		assertEquals(0.0, comparison.probePointResidualWorldMeters().length(), 1.0e-15);
		assertEquals(0.0, comparison.probeVelocityResidualWorldMetersPerSecond().length(), 1.0e-12);
		assertEquals(0.0, comparison.axialVelocityResidualMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, comparison.speedResidualMetersPerSecond(), 1.0e-12);
		assertTrue(comparison.referenceExpectedAxialVelocityMetersPerSecond() > 0.0);
		assertEquals(0.0, comparison.cfdProbeTransverseVelocityMetersPerSecond(), 1.0e-12);

		Map<String, String> output = outputRecord(
				CtCpJActuatorDiskWakeProbeComparisonImporter.csvLines(input, RHO, SOURCE_THICKNESS));
		assertEquals("apDrone", output.get("preset"));
		assertEquals("static_anchored_source_hover", output.get("case"));
		assertEquals("raw_source", output.get("row_kind"));
		assertEquals("INTERPOLATED", output.get("reference_lookup_status"));
		assertEquals("true", output.get("reference_source_enabled"));
		assertEquals("true", output.get("comparable"));
		assertEquals("synthetic-wake-probe", output.get("source_case_sha256"));
		assertEquals(number(reference, "pressure_jump_pa"),
				Double.parseDouble(output.get("cfd_probe_p_field")), 1.0e-12);
		assertEquals(0.0, Double.parseDouble(output.get("probe_velocity_residual_magnitude_mps")), 1.0e-12);
		assertEquals(0.0, Double.parseDouble(output.get("probe_point_residual_magnitude_m")), 1.0e-15);
	}

	@Test
	void exactHoverSwirlRadiusProbeClosesAgainstCombinedWakeVelocity() {
		Map<String, String> reference = referenceRecord(
				"static_anchored_source_hover",
				"raw_source",
				0,
				1.0,
				"swirl_radius"
		);
		String input = cfdCsv(reference);

		CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow comparison =
				CtCpJActuatorDiskWakeProbeComparisonImporter.compare(input, RHO, SOURCE_THICKNESS).get(0);

		assertTrue(comparison.comparable());
		assertEquals("swirl_radius", comparison.reference().probeKind());
		assertEquals(0.0, comparison.probePointResidualWorldMeters().length(), 1.0e-15);
		assertEquals(0.0, comparison.probeVelocityResidualWorldMetersPerSecond().length(), 1.0e-12);
		assertTrue(comparison.referenceExpectedAxialVelocityMetersPerSecond() > 0.0);
		assertTrue(comparison.referenceExpectedTransverseVelocityMetersPerSecond() > 0.0);
		assertEquals(comparison.referenceExpectedTransverseVelocityMetersPerSecond(),
				comparison.cfdProbeTransverseVelocityMetersPerSecond(), 1.0e-12);
		assertTrue(comparison.cfdProbeSpeedMetersPerSecond()
				> comparison.referenceExpectedAxialVelocityMetersPerSecond());
	}

	@Test
	void perturbedMidJWakeProbeReportsAxialAndTransverseResiduals() {
		Map<String, String> reference = referenceRecord("static_anchored_source_mid_j", "raw_source", 0, 2.0);
		double axialScale = 0.92;
		double transverseOffset = 0.35;
		String input = cfdCsv(
				reference,
				new Vec3(
						number(reference, "expected_far_wake_velocity_world_x_mps") + transverseOffset,
						number(reference, "expected_far_wake_velocity_world_y_mps") * axialScale,
						number(reference, "expected_far_wake_velocity_world_z_mps")),
				new Vec3(
						number(reference, "probe_point_world_x_m") + 0.002,
						number(reference, "probe_point_world_y_m"),
						number(reference, "probe_point_world_z_m"))
		);

		CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow comparison =
				CtCpJActuatorDiskWakeProbeComparisonImporter.compare(input, RHO, SOURCE_THICKNESS).get(0);

		double referenceAxial = number(reference, "expected_far_wake_velocity_world_y_mps");
		assertTrue(comparison.comparable());
		assertEquals(referenceAxial * (axialScale - 1.0),
				comparison.axialVelocityResidualMetersPerSecond(), 1.0e-12);
		assertEquals(axialScale - 1.0, comparison.axialVelocityResidualFraction(), 1.0e-12);
		assertEquals(transverseOffset, comparison.cfdProbeTransverseVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(transverseOffset, comparison.transverseVelocityResidualMetersPerSecond(), 1.0e-12);
		assertEquals(0.002, comparison.probePointResidualWorldMeters().length(), 1.0e-15);
		assertTrue(comparison.probeVelocityResidualWorldMetersPerSecond().length() > transverseOffset);
	}

	@Test
	void highJWakeProbeUsesTiltedDiskNormalForAxialProjection() {
		Map<String, String> reference = referenceRecord("static_anchored_source_high_j", "raw_source", 0, 0.5);
		String input = cfdCsv(reference);

		CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow comparison =
				CtCpJActuatorDiskWakeProbeComparisonImporter.compare(input, RHO, SOURCE_THICKNESS).get(0);

		assertTrue(comparison.comparable());
		assertEquals(-1.0, number(reference, "disk_normal_world_x"), 1.0e-12);
		assertTrue(comparison.referenceExpectedAxialVelocityMetersPerSecond() > 0.0);
		assertEquals(comparison.referenceExpectedAxialVelocityMetersPerSecond(),
				comparison.cfdProbeAxialVelocityMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, comparison.cfdProbeTransverseVelocityMetersPerSecond(), 1.0e-9);
		assertEquals(0.0, comparison.probeVelocityResidualWorldMetersPerSecond().length(), 1.0e-12);
	}

	@Test
	void blockedHighJWakeProbeComparesZeroVelocityToOutOfEnvelopeReference() {
		Map<String, String> reference = referenceRecord("static_anchored_source_high_j_block", "raw_source", 0, 4.0);
		String input = cfdCsv(reference);

		CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow comparison =
				CtCpJActuatorDiskWakeProbeComparisonImporter.compare(input, RHO, SOURCE_THICKNESS).get(0);

		assertTrue(comparison.comparable());
		assertTrue(comparison.reference().blocked());
		assertEquals(false, comparison.reference().sourceEnabled());
		assertEquals("OUT_OF_ENVELOPE_BLOCKED", comparison.reference().lookupStatus());
		assertEquals(0.0, comparison.reference().expectedFarWakeSpeedMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, comparison.cfdProbeSpeedMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, comparison.probeVelocityResidualWorldMetersPerSecond().length(), 1.0e-15);
	}

	@Test
	void exportedWakeProbePacketRoundTripsAsComparisonInput() {
		String packetCsv = String.join("\n", CtCpJActuatorDiskWakeProbeExporter.csvLines("apDrone", RHO));

		List<CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow> comparisons =
				CtCpJActuatorDiskWakeProbeComparisonImporter.compare(packetCsv, RHO, SOURCE_THICKNESS);

		assertEquals(384, comparisons.size());
		assertTrue(comparisons.stream().allMatch(CtCpJActuatorDiskWakeProbeComparisonImporter
				.ComparisonRow::comparable));
		assertTrue(comparisons.stream()
				.allMatch(row -> row.probePointResidualWorldMeters().length() < 1.0e-15
						&& row.probeVelocityResidualWorldMetersPerSecond().length() < 1.0e-12
						&& Math.abs(row.speedResidualMetersPerSecond()) < 1.0e-12));
		assertTrue(comparisons.stream()
				.anyMatch(row -> row.reference().blocked()
						&& "static_anchored_source_high_j_block".equals(row.reference().caseName())
						&& row.reference().expectedFarWakeSpeedMetersPerSecond() == 0.0));
	}

	@Test
	void writeCreatesParentDirectoriesAndComparisonCsv(@TempDir Path tempDir) throws IOException {
		Path input = tempDir.resolve("wake-results.csv");
		Path output = tempDir.resolve("nested").resolve("wake-comparison.csv");
		Files.writeString(input, cfdCsv(referenceRecord("static_anchored_source_hover", "raw_source", 0, 1.0)));

		CtCpJActuatorDiskWakeProbeComparisonImporter.write(input, output, RHO, SOURCE_THICKNESS);

		List<String> lines = Files.readAllLines(output);
		assertEquals(2, lines.size());
		assertTrue(lines.get(0).contains("probe_velocity_residual_magnitude_mps"));
		assertTrue(lines.get(0).contains("axial_velocity_residual_mps"));
		assertTrue(lines.get(1).contains("ct-cp-j-actuator-disk-wake-probe-comparison-ready"));
	}

	private static String cfdCsv(Map<String, String> reference) {
		return cfdCsv(
				reference,
				new Vec3(
						number(reference, "expected_wake_velocity_world_x_mps"),
						number(reference, "expected_wake_velocity_world_y_mps"),
						number(reference, "expected_wake_velocity_world_z_mps")),
				new Vec3(
						number(reference, "probe_point_world_x_m"),
						number(reference, "probe_point_world_y_m"),
						number(reference, "probe_point_world_z_m")),
				number(reference, "pressure_jump_pa")
		);
	}

	private static String cfdCsv(Map<String, String> reference, Vec3 cfdVelocity, Vec3 cfdPoint) {
		return cfdCsv(reference, cfdVelocity, cfdPoint, number(reference, "pressure_jump_pa"));
	}

	private static String cfdCsv(
			Map<String, String> reference,
			Vec3 cfdVelocity,
			Vec3 cfdPoint,
			double cfdPField
	) {
		return String.join("\n",
				String.join(",",
						"preset",
						"case",
						"row_kind",
						"rotor_index",
						"probe_kind",
						"probe_distance_radius",
						"cfd_probe_point_world_x_m",
						"cfd_probe_point_world_y_m",
						"cfd_probe_point_world_z_m",
						"cfd_probe_velocity_world_x_mps",
						"cfd_probe_velocity_world_y_mps",
						"cfd_probe_velocity_world_z_mps",
						"cfd_probe_p_field",
						"source_case_sha256",
						"solver_status"),
				row(
						reference.get("preset"),
						reference.get("case"),
						reference.get("row_kind"),
						reference.get("rotor_index"),
						reference.get("probe_kind"),
						reference.get("probe_distance_radius"),
						Double.toString(cfdPoint.x()),
						Double.toString(cfdPoint.y()),
						Double.toString(cfdPoint.z()),
						Double.toString(cfdVelocity.x()),
						Double.toString(cfdVelocity.y()),
						Double.toString(cfdVelocity.z()),
						Double.toString(cfdPField),
						"synthetic-wake-probe",
						"CONVERGED"
				));
	}

	private static Map<String, String> referenceRecord(
			String caseName,
			String rowKind,
			int rotorIndex,
			double probeDistanceRadius
	) {
		return referenceRecord(caseName, rowKind, rotorIndex, probeDistanceRadius, "centerline_axial");
	}

	private static Map<String, String> referenceRecord(
			String caseName,
			String rowKind,
			int rotorIndex,
			double probeDistanceRadius,
			String probeKind
	) {
		List<String> lines = CtCpJActuatorDiskWakeProbeExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		int caseColumn = columns.get("case");
		int rowKindColumn = columns.get("row_kind");
		int rotorIndexColumn = columns.get("rotor_index");
		int probeDistanceColumn = columns.get("probe_distance_radius");
		return lines.stream()
				.skip(1)
				.map(line -> record(line, columns))
				.filter(record -> record.get("case").equals(caseName)
						&& record.get("row_kind").equals(rowKind)
						&& Integer.parseInt(record.get("rotor_index")) == rotorIndex
						&& record.get("probe_kind").equals(probeKind)
						&& Math.abs(Double.parseDouble(record.get("probe_distance_radius"))
								- probeDistanceRadius) < 1.0e-15)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"missing reference row: " + caseName + "/" + rowKind + "/" + rotorIndex + "/"
								+ probeDistanceRadius + " columns=" + caseColumn + rowKindColumn
								+ rotorIndexColumn + probeDistanceColumn));
	}

	private static Map<String, String> outputRecord(List<String> lines) {
		return record(lines.get(1), columns(lines));
	}

	private static Map<String, Integer> columns(List<String> lines) {
		String[] header = lines.get(0).split(",", -1);
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (int i = 0; i < header.length; i++) {
			columns.put(header[i], i);
		}
		return columns;
	}

	private static Map<String, String> record(String line, Map<String, Integer> columns) {
		String[] cells = line.split(",", -1);
		Map<String, String> record = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : columns.entrySet()) {
			record.put(entry.getKey(), cells[entry.getValue()]);
		}
		return record;
	}

	private static double number(Map<String, String> record, String columnName) {
		return Double.parseDouble(record.get(columnName));
	}

	private static String row(String... cells) {
		return String.join(",", cells);
	}
}
