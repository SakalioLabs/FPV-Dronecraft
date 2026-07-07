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
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJActuatorDiskWakeProbeSummaryExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void exportedWakeProbePacketSummarizesToZeroResidualCenterlines() {
		String packetCsv = String.join("\n", CtCpJActuatorDiskWakeProbeExporter.csvLines("apDrone", RHO));

		List<CtCpJActuatorDiskWakeProbeSummaryExporter.SummaryRow> summaries =
				CtCpJActuatorDiskWakeProbeSummaryExporter.summarize(packetCsv, RHO, SOURCE_THICKNESS);

		CtCpJActuatorDiskWakeProbeSummaryExporter.SummaryRow hover =
				summaryFor(summaries, "static_anchored_source_hover", "raw_source", 0);
		CtCpJActuatorDiskWakeProbeSummaryExporter.SummaryRow highBlock =
				summaryFor(summaries, "static_anchored_source_high_j_block", "raw_source", 0);

		assertEquals(40, summaries.size());
		assertEquals(4, hover.totalSamples());
		assertEquals(4, hover.comparableSamples());
		assertTrue(hover.comparable());
		assertTrue(hover.referenceAxialMeanMetersPerSecond() > 0.0);
		assertEquals(0.0, hover.axialResidualRootMeanSquareMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, hover.velocityResidualRootMeanSquareMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, hover.cfdTransverseMaxMetersPerSecond(), 1.0e-12);
		assertEquals(1.0, hover.cfdAxial4rOver0p5r(), 1.0e-12);

		assertTrue(highBlock.blocked());
		assertEquals(false, highBlock.sourceEnabled());
		assertEquals(0.0, highBlock.referenceAxialMeanMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, highBlock.cfdAxialMeanMetersPerSecond(), 1.0e-15);
		assertTrue(highBlock.comparable());
	}

	@Test
	void perturbedCenterlineReportsResidualTrendAndSolverNativePDelta() {
		List<Map<String, String>> records = sourceRecords("static_anchored_source_mid_j", "raw_source", 0);
		Map<Double, Double> scaleByDistance = Map.of(
				0.5, 1.05,
				1.0, 1.0,
				2.0, 0.8,
				4.0, 0.6
		);
		String inputCsv = cfdCsv(
				records,
				record -> {
					double distance = number(record, "probe_distance_radius");
					double scale = scaleByDistance.get(distance);
					return new Vec3(
							number(record, "expected_far_wake_velocity_world_x_mps"),
							number(record, "expected_far_wake_velocity_world_y_mps") * scale,
							number(record, "expected_far_wake_velocity_world_z_mps"));
				},
				record -> new Vec3(
						number(record, "probe_point_world_x_m") + 0.001,
						number(record, "probe_point_world_y_m"),
						number(record, "probe_point_world_z_m")),
				record -> number(record, "pressure_jump_pa")
						- 2.0 * number(record, "probe_distance_radius")
		);

		CtCpJActuatorDiskWakeProbeSummaryExporter.SummaryRow summary =
				CtCpJActuatorDiskWakeProbeSummaryExporter
						.summarize(inputCsv, RHO, SOURCE_THICKNESS)
						.get(0);

		double referenceAxial = number(records.get(0), "expected_far_wake_velocity_world_y_mps");
		assertEquals(4, summary.totalSamples());
		assertEquals(referenceAxial * (1.05 + 1.0 + 0.8 + 0.6) / 4.0,
				summary.cfdAxialMeanMetersPerSecond(), 1.0e-12);
		assertEquals(referenceAxial * ((1.05 + 1.0 + 0.8 + 0.6) / 4.0 - 1.0),
				summary.axialResidualMeanMetersPerSecond(), 1.0e-12);
		assertTrue(summary.axialResidualRootMeanSquareMetersPerSecond() > 0.0);
		assertEquals((referenceAxial * 0.6) / (referenceAxial * 1.05),
				summary.cfdAxial4rOver0p5r(), 1.0e-12);
		assertEquals(0.001, summary.probePointResidualMaxMeters(), 1.0e-15);
		assertEquals(-7.0, summary.cfdPFieldDelta4rMinus0p5r(), 1.0e-12);
		assertTrue(summary.comparable());
	}

	@Test
	void csvLinesExposeCenterlineSummaryColumns() {
		Map<String, String> reference = referenceRecord("static_anchored_source_hover", "raw_source", 0, 0.5);
		String inputCsv = String.join("\n", csvHeader(), cfdRow(reference));

		List<String> lines = CtCpJActuatorDiskWakeProbeSummaryExporter.csvLines(
				inputCsv,
				RHO,
				SOURCE_THICKNESS
		);
		Map<String, String> output = record(lines.get(1), columns(lines));

		assertEquals(2, lines.size());
		assertTrue(lines.get(0).contains("cfd_axial_4r_over_0p5r"));
		assertTrue(lines.get(0).contains("cfd_p_field_delta_4r_minus_0p5r"));
		assertEquals("static_anchored_source_hover", output.get("case"));
		assertEquals("1", output.get("total_samples"));
		assertEquals("ct-cp-j-actuator-disk-wake-probe-summary-ready", output.get("message"));
	}

	@Test
	void writeCreatesParentDirectoriesAndSummaryCsv(@TempDir Path tempDir) throws IOException {
		Path input = tempDir.resolve("wake-results.csv");
		Path output = tempDir.resolve("nested").resolve("wake-summary.csv");
		Files.writeString(input, String.join("\n",
				csvHeader(),
				cfdRow(referenceRecord("static_anchored_source_hover", "raw_source", 0, 0.5))));

		CtCpJActuatorDiskWakeProbeSummaryExporter.write(input, output, RHO, SOURCE_THICKNESS);

		List<String> lines = Files.readAllLines(output);
		assertEquals(2, lines.size());
		assertTrue(lines.get(0).contains("axial_residual_rms_mps"));
		assertTrue(lines.get(1).contains("ct-cp-j-actuator-disk-wake-probe-summary-ready"));
	}

	private static CtCpJActuatorDiskWakeProbeSummaryExporter.SummaryRow summaryFor(
			List<CtCpJActuatorDiskWakeProbeSummaryExporter.SummaryRow> summaries,
			String caseName,
			String rowKind,
			int rotorIndex
	) {
		return summaries.stream()
				.filter(row -> row.caseName().equals(caseName)
						&& row.rowKind().equals(rowKind)
						&& row.rotorIndex() == rotorIndex)
				.findFirst()
				.orElseThrow();
	}

	private static List<Map<String, String>> sourceRecords(String caseName, String rowKind, int rotorIndex) {
		List<String> lines = CtCpJActuatorDiskWakeProbeExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		return lines.stream()
				.skip(1)
				.map(line -> record(line, columns))
				.filter(record -> record.get("case").equals(caseName)
						&& record.get("row_kind").equals(rowKind)
						&& Integer.parseInt(record.get("rotor_index")) == rotorIndex)
				.toList();
	}

	private static Map<String, String> referenceRecord(
			String caseName,
			String rowKind,
			int rotorIndex,
			double probeDistanceRadius
	) {
		return sourceRecords(caseName, rowKind, rotorIndex).stream()
				.filter(record -> Math.abs(Double.parseDouble(record.get("probe_distance_radius"))
						- probeDistanceRadius) < 1.0e-15)
				.findFirst()
				.orElseThrow();
	}

	private static String cfdCsv(
			List<Map<String, String>> records,
			Function<Map<String, String>, Vec3> velocity,
			Function<Map<String, String>, Vec3> point,
			Function<Map<String, String>, Double> pField
	) {
		List<String> lines = new java.util.ArrayList<>();
		lines.add(csvHeader());
		for (Map<String, String> record : records) {
			lines.add(cfdRow(record, velocity.apply(record), point.apply(record), pField.apply(record)));
		}
		return String.join("\n", lines);
	}

	private static String cfdRow(Map<String, String> reference) {
		return cfdRow(
				reference,
				new Vec3(
						number(reference, "expected_far_wake_velocity_world_x_mps"),
						number(reference, "expected_far_wake_velocity_world_y_mps"),
						number(reference, "expected_far_wake_velocity_world_z_mps")),
				new Vec3(
						number(reference, "probe_point_world_x_m"),
						number(reference, "probe_point_world_y_m"),
						number(reference, "probe_point_world_z_m")),
				number(reference, "pressure_jump_pa")
		);
	}

	private static String cfdRow(Map<String, String> reference, Vec3 cfdVelocity, Vec3 cfdPoint, double pField) {
		return row(
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
				Double.toString(pField),
				"synthetic-wake-summary",
				"CONVERGED"
		);
	}

	private static String csvHeader() {
		return String.join(",",
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
				"solver_status");
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
