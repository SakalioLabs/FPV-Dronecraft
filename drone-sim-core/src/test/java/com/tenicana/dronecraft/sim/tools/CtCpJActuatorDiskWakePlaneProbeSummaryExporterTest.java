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

class CtCpJActuatorDiskWakePlaneProbeSummaryExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void exportedWakePlanePacketSummarizesToZeroResidualStations() {
		String packetCsv = String.join("\n", CtCpJActuatorDiskWakePlaneProbeExporter.csvLines("apDrone", RHO));

		List<CtCpJActuatorDiskWakePlaneProbeSummaryExporter.SummaryRow> summaries =
				CtCpJActuatorDiskWakePlaneProbeSummaryExporter.summarize(packetCsv, RHO, SOURCE_THICKNESS);

		CtCpJActuatorDiskWakePlaneProbeSummaryExporter.SummaryRow hover =
				summaryFor(summaries, "static_anchored_source_hover", "raw_source", 0, 1.0);
		CtCpJActuatorDiskWakePlaneProbeSummaryExporter.SummaryRow highBlock =
				summaryFor(summaries, "static_anchored_source_high_j_block", "raw_source", 0, 4.0);

		assertEquals(160, summaries.size());
		assertEquals(13, hover.totalSamples());
		assertEquals(13, hover.comparableSamples());
		assertEquals(9, hover.coreSamples());
		assertEquals(4, hover.outerSamples());
		assertTrue(Double.isNaN(hover.coreCfdPFieldMean()));
		assertTrue(hover.comparable());
		assertTrue(hover.coreReferenceAxialMeanMetersPerSecond() > 0.0);
		assertEquals(0.0, hover.coreAxialResidualRootMeanSquareMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, hover.coreVelocityResidualRootMeanSquareMetersPerSecond(), 1.0e-12);
		assertEquals(0.0, hover.outerCfdSpeedMaxMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, hover.centerEdgeAxialSpreadMetersPerSecond(), 1.0e-12);

		assertTrue(highBlock.blocked());
		assertEquals(false, highBlock.sourceEnabled());
		assertEquals(0.0, highBlock.coreReferenceAxialMeanMetersPerSecond(), 1.0e-15);
		assertEquals(0.0, highBlock.outerCfdSpeedMaxMetersPerSecond(), 1.0e-15);
		assertTrue(highBlock.comparable());
	}

	@Test
	void perturbedWakePlaneStationReportsCoreResidualAndOuterLeak() {
		List<Map<String, String>> records = stationRecords(
				"static_anchored_source_mid_j",
				"raw_source",
				0,
				2.0
		);
		double axialScale = 0.9;
		double outerLeakMetersPerSecond = 0.5;
		String inputCsv = cfdCsv(
				records,
				record -> {
					if ("outer_reference".equals(record.get("probe_region"))) {
						return new Vec3(0.0, outerLeakMetersPerSecond, 0.0);
					}
					return new Vec3(
							number(record, "expected_top_hat_velocity_world_x_mps"),
							number(record, "expected_top_hat_velocity_world_y_mps") * axialScale,
							number(record, "expected_top_hat_velocity_world_z_mps"));
				},
				record -> new Vec3(
						number(record, "probe_point_world_x_m") + 0.002,
						number(record, "probe_point_world_y_m"),
						number(record, "probe_point_world_z_m"))
		);

		CtCpJActuatorDiskWakePlaneProbeSummaryExporter.SummaryRow summary =
				CtCpJActuatorDiskWakePlaneProbeSummaryExporter
						.summarize(inputCsv, RHO, SOURCE_THICKNESS)
						.get(0);

		double referenceAxial = number(records.get(0), "expected_axial_velocity_mps");
		assertEquals(13, summary.totalSamples());
		assertEquals(9, summary.coreSamples());
		assertEquals(4, summary.outerSamples());
		assertEquals(referenceAxial, summary.coreReferenceAxialMeanMetersPerSecond(), 1.0e-12);
		assertEquals(referenceAxial * axialScale, summary.coreCfdAxialMeanMetersPerSecond(), 1.0e-12);
		assertEquals(referenceAxial * (axialScale - 1.0),
				summary.coreAxialResidualMeanMetersPerSecond(), 1.0e-12);
		assertEquals(Math.abs(referenceAxial * (axialScale - 1.0)),
				summary.coreAxialResidualRootMeanSquareMetersPerSecond(), 1.0e-12);
		assertEquals(outerLeakMetersPerSecond, summary.outerCfdSpeedMaxMetersPerSecond(), 1.0e-12);
		assertEquals(outerLeakMetersPerSecond / referenceAxial,
				summary.outerLeakFractionOfCoreReference(), 1.0e-12);
		assertEquals(0.002, summary.probePointResidualMaxMeters(), 1.0e-15);
		assertTrue(summary.comparable());
	}

	@Test
	void wakePlaneStationSummarizesSolverNativePFieldShape() {
		List<Map<String, String>> records = stationRecords(
				"static_anchored_source_hover",
				"raw_source",
				0,
				1.0
		);
		String inputCsv = cfdCsv(
				records,
				record -> new Vec3(
						number(record, "expected_top_hat_velocity_world_x_mps"),
						number(record, "expected_top_hat_velocity_world_y_mps"),
						number(record, "expected_top_hat_velocity_world_z_mps")),
				record -> new Vec3(
						number(record, "probe_point_world_x_m"),
						number(record, "probe_point_world_y_m"),
						number(record, "probe_point_world_z_m")),
				record -> {
					String region = record.get("probe_region");
					if ("centerline".equals(region)) {
						return 120.0;
					}
					if ("wake_core_top_hat".equals(region)) {
						return 100.0;
					}
					return 12.0;
				}
		);

		CtCpJActuatorDiskWakePlaneProbeSummaryExporter.SummaryRow summary =
				CtCpJActuatorDiskWakePlaneProbeSummaryExporter
						.summarize(inputCsv, RHO, SOURCE_THICKNESS)
						.get(0);

		assertEquals((120.0 + 8.0 * 100.0) / 9.0, summary.coreCfdPFieldMean(), 1.0e-12);
		assertEquals(120.0, summary.centerCfdPField(), 1.0e-12);
		assertEquals(100.0, summary.edgeCoreCfdPFieldMean(), 1.0e-12);
		assertEquals(20.0, summary.centerEdgeCfdPFieldDelta(), 1.0e-12);
		assertEquals(12.0, summary.outerCfdPFieldMean(), 1.0e-12);
		assertEquals(12.0, summary.outerCfdPFieldMaxAbs(), 1.0e-12);
		assertEquals(((120.0 + 8.0 * 100.0) / 9.0) - 12.0,
				summary.coreOuterCfdPFieldDelta(), 1.0e-12);
		assertTrue(summary.comparable());
	}

	@Test
	void csvLinesExposeStationSummaryColumns() {
		String inputCsv = String.join("\n",
				csvHeader(),
				cfdRow(referenceRecord("static_anchored_source_hover", "raw_source", 0, 1.0, "center"))
		);

		List<String> lines = CtCpJActuatorDiskWakePlaneProbeSummaryExporter.csvLines(
				inputCsv,
				RHO,
				SOURCE_THICKNESS
		);
		Map<String, String> output = record(lines.get(1), columns(lines));

		assertEquals(2, lines.size());
		assertTrue(lines.get(0).contains("outer_leak_fraction_of_core_ref"));
		assertTrue(lines.get(0).contains("core_cfd_p_field_mean"));
		assertEquals("static_anchored_source_hover", output.get("case"));
		assertEquals("1", output.get("total_samples"));
		assertEquals("1", output.get("core_samples"));
		assertEquals("0", output.get("outer_samples"));
		assertEquals("ct-cp-j-actuator-disk-wake-plane-summary-ready", output.get("message"));
	}

	@Test
	void writeCreatesParentDirectoriesAndSummaryCsv(@TempDir Path tempDir) throws IOException {
		Path input = tempDir.resolve("wake-plane-results.csv");
		Path output = tempDir.resolve("nested").resolve("wake-plane-summary.csv");
		Files.writeString(input, String.join("\n",
				csvHeader(),
				cfdRow(referenceRecord("static_anchored_source_hover", "raw_source", 0, 1.0, "center"))));

		CtCpJActuatorDiskWakePlaneProbeSummaryExporter.write(input, output, RHO, SOURCE_THICKNESS);

		List<String> lines = Files.readAllLines(output);
		assertEquals(2, lines.size());
		assertTrue(lines.get(0).contains("core_axial_residual_rms_mps"));
		assertTrue(lines.get(1).contains("ct-cp-j-actuator-disk-wake-plane-summary-ready"));
	}

	private static CtCpJActuatorDiskWakePlaneProbeSummaryExporter.SummaryRow summaryFor(
			List<CtCpJActuatorDiskWakePlaneProbeSummaryExporter.SummaryRow> summaries,
			String caseName,
			String rowKind,
			int rotorIndex,
			double probeDistanceRadius
	) {
		return summaries.stream()
				.filter(row -> row.caseName().equals(caseName)
						&& row.rowKind().equals(rowKind)
						&& row.rotorIndex() == rotorIndex
						&& Math.abs(row.probeDistanceRadius() - probeDistanceRadius) < 1.0e-15)
				.findFirst()
				.orElseThrow();
	}

	private static List<Map<String, String>> stationRecords(
			String caseName,
			String rowKind,
			int rotorIndex,
			double probeDistanceRadius
	) {
		List<String> lines = CtCpJActuatorDiskWakePlaneProbeExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		return lines.stream()
				.skip(1)
				.map(line -> record(line, columns))
				.filter(record -> record.get("case").equals(caseName)
						&& record.get("row_kind").equals(rowKind)
						&& Integer.parseInt(record.get("rotor_index")) == rotorIndex
						&& Math.abs(Double.parseDouble(record.get("probe_distance_radius"))
								- probeDistanceRadius) < 1.0e-15)
				.toList();
	}

	private static Map<String, String> referenceRecord(
			String caseName,
			String rowKind,
			int rotorIndex,
			double probeDistanceRadius,
			String planeSample
	) {
		return stationRecords(caseName, rowKind, rotorIndex, probeDistanceRadius).stream()
				.filter(record -> record.get("plane_sample").equals(planeSample))
				.findFirst()
				.orElseThrow();
	}

	private static String cfdCsv(
			List<Map<String, String>> records,
			Function<Map<String, String>, Vec3> velocity,
			Function<Map<String, String>, Vec3> point
	) {
		return cfdCsv(records, velocity, point, ignored -> Double.NaN);
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
						number(reference, "expected_top_hat_velocity_world_x_mps"),
						number(reference, "expected_top_hat_velocity_world_y_mps"),
						number(reference, "expected_top_hat_velocity_world_z_mps")),
				new Vec3(
						number(reference, "probe_point_world_x_m"),
						number(reference, "probe_point_world_y_m"),
						number(reference, "probe_point_world_z_m"))
		);
	}

	private static String cfdRow(Map<String, String> reference, Vec3 cfdVelocity, Vec3 cfdPoint) {
		return cfdRow(reference, cfdVelocity, cfdPoint, Double.NaN);
	}

	private static String cfdRow(Map<String, String> reference, Vec3 cfdVelocity, Vec3 cfdPoint, double pField) {
		return row(
				reference.get("preset"),
				reference.get("case"),
				reference.get("row_kind"),
				reference.get("rotor_index"),
				reference.get("probe_kind"),
				reference.get("plane_sample"),
				reference.get("probe_distance_radius"),
				Double.toString(cfdPoint.x()),
				Double.toString(cfdPoint.y()),
				Double.toString(cfdPoint.z()),
				Double.toString(cfdVelocity.x()),
				Double.toString(cfdVelocity.y()),
				Double.toString(cfdVelocity.z()),
				Double.isFinite(pField) ? Double.toString(pField) : "",
				"synthetic-wake-plane-summary",
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
				"plane_sample",
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
