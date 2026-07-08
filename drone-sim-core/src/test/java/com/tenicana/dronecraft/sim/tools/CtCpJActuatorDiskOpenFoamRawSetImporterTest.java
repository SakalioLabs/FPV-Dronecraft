package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJActuatorDiskOpenFoamRawSetImporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void importsOpenFoamRawSetFilesIntoComparisonReadyCsvs(@TempDir Path tempDir) throws IOException {
		writeExactRawSets(tempDir);

		CtCpJActuatorDiskOpenFoamRawSetImporter.ImportedRawSetCsv imported =
				CtCpJActuatorDiskOpenFoamRawSetImporter.importRawSets(
						"apDrone",
						tempDir,
						RHO,
						SOURCE_THICKNESS,
						"CONVERGED",
						"raw-set-smoke"
				);

		assertEquals(321, imported.centerlineCsvLines().size());
		assertEquals(2081, imported.wakePlaneCsvLines().size());
		assertTrue(imported.centerlineCsvLines().get(0).contains("cfd_probe_velocity_world_y_mps"));
		assertTrue(imported.centerlineCsvLines().get(0).contains("cfd_probe_p_field"));
		assertTrue(imported.wakePlaneCsvLines().get(0).contains("plane_sample"));
		assertTrue(imported.wakePlaneCsvLines().get(0).contains("cfd_probe_p_field"));
		assertTrue(imported.centerlineCsvLines().stream()
				.anyMatch(line -> line.contains("static_anchored_source_hover")
						&& line.contains("raw-set-smoke,CONVERGED")));
		assertTrue(imported.wakePlaneCsvLines().stream()
				.anyMatch(line -> line.contains("wake_plane_top_hat,center")
						&& line.contains("raw-set-smoke,CONVERGED")));
		Map<String, String> firstCenterlineRow = record(
				imported.centerlineCsvLines().get(1),
				columns(imported.centerlineCsvLines())
		);
		assertEquals(116.895877068887,
				Double.parseDouble(firstCenterlineRow.get("cfd_probe_p_field")), 1.0e-12);

		List<CtCpJActuatorDiskWakeProbeComparisonImporter.ComparisonRow> centerlineComparisons =
				CtCpJActuatorDiskWakeProbeComparisonImporter.compare(
						String.join("\n", imported.centerlineCsvLines()),
						RHO,
						SOURCE_THICKNESS
				);
		List<CtCpJActuatorDiskWakePlaneProbeComparisonImporter.ComparisonRow> wakePlaneComparisons =
				CtCpJActuatorDiskWakePlaneProbeComparisonImporter.compare(
						String.join("\n", imported.wakePlaneCsvLines()),
						RHO,
						SOURCE_THICKNESS
				);

		assertEquals(320, centerlineComparisons.size());
		assertEquals(2080, wakePlaneComparisons.size());
		assertTrue(centerlineComparisons.stream().allMatch(CtCpJActuatorDiskWakeProbeComparisonImporter
				.ComparisonRow::comparable));
		assertTrue(wakePlaneComparisons.stream().allMatch(CtCpJActuatorDiskWakePlaneProbeComparisonImporter
				.ComparisonRow::comparable));
		assertTrue(centerlineComparisons.stream()
				.allMatch(row -> row.probeVelocityResidualWorldMetersPerSecond().length() < 1.0e-12
						&& row.probePointResidualWorldMeters().length() < 1.0e-12));
		assertTrue(wakePlaneComparisons.stream()
				.allMatch(row -> row.probeVelocityResidualWorldMetersPerSecond().length() < 1.0e-12
						&& row.probePointResidualWorldMeters().length() < 1.0e-12));
	}

	@Test
	void writeCreatesBothOutputFiles(@TempDir Path tempDir) throws IOException {
		Path rawSets = tempDir.resolve("sets").resolve("100");
		Files.createDirectories(rawSets);
		writeExactRawSets(rawSets);
		Path centerlineOutput = tempDir.resolve("out").resolve("centerline.csv");
		Path wakePlaneOutput = tempDir.resolve("out").resolve("wake-plane.csv");

		CtCpJActuatorDiskOpenFoamRawSetImporter.write(
				"apDrone",
				rawSets,
				centerlineOutput,
				wakePlaneOutput,
				RHO,
				SOURCE_THICKNESS,
				"CONVERGED",
				"write-smoke"
		);

		assertEquals(321, Files.readAllLines(centerlineOutput).size());
		assertEquals(2081, Files.readAllLines(wakePlaneOutput).size());
		assertTrue(Files.readString(wakePlaneOutput).contains("write-smoke,CONVERGED"));
		assertTrue(Files.readString(centerlineOutput).contains("cfd_probe_p_field"));
	}

	@Test
	void importerIgnoresUtf8BomBeforeRawSetComment(@TempDir Path tempDir) throws IOException {
		writeExactRawSets(tempDir);
		Path firstSet;
		try (Stream<Path> paths = Files.list(tempDir)) {
			firstSet = paths
					.filter(path -> path.getFileName().toString().endsWith("_centerline_U.xy"))
					.sorted()
					.findFirst()
					.orElseThrow();
		}
		String content = Files.readString(firstSet, StandardCharsets.UTF_8);
		Files.writeString(firstSet, "\uFEFF" + content, StandardCharsets.UTF_8);

		CtCpJActuatorDiskOpenFoamRawSetImporter.ImportedRawSetCsv imported =
				CtCpJActuatorDiskOpenFoamRawSetImporter.importRawSets(
						"apDrone",
						tempDir,
						RHO,
						SOURCE_THICKNESS,
						"CONVERGED",
						"bom-smoke"
				);

		assertEquals(321, imported.centerlineCsvLines().size());
	}

	@Test
	void missingRawSetFileFailsExplicitly(@TempDir Path tempDir) throws IOException {
		Files.createDirectories(tempDir);

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
				CtCpJActuatorDiskOpenFoamRawSetImporter.importRawSets(
						"apDrone",
						tempDir,
						RHO,
						SOURCE_THICKNESS,
						"CONVERGED",
						"missing-file"
				));

		assertTrue(error.getMessage().contains("missing OpenFOAM raw U set file"));
	}

	private static void writeExactRawSets(Path directory) throws IOException {
		writeGroupedRawSets(
				directory,
				CtCpJActuatorDiskWakeProbeExporter.csvLines("apDrone", RHO),
				"centerline",
				"expected_wake_velocity_world"
		);
		writeGroupedRawSets(
				directory,
				CtCpJActuatorDiskWakePlaneProbeExporter.csvLines("apDrone", RHO),
				"wake_plane",
				"expected_top_hat_velocity_world"
		);
	}

	private static void writeGroupedRawSets(
			Path directory,
			List<String> csvLines,
			String suffix,
			String velocityPrefix
	) throws IOException {
		Map<String, Integer> columns = columns(csvLines);
		Map<String, List<Map<String, String>>> grouped = new LinkedHashMap<>();
		for (String line : csvLines.subList(1, csvLines.size())) {
			Map<String, String> record = record(line, columns);
			grouped.computeIfAbsent(record.get("source_name"), ignored -> new ArrayList<>()).add(record);
		}
		Files.createDirectories(directory);
		for (Map.Entry<String, List<Map<String, String>>> entry : grouped.entrySet()) {
			List<String> rawLines = new ArrayList<>();
			rawLines.add("# x y z Ux Uy Uz");
			for (Map<String, String> row : entry.getValue()) {
				rawLines.add(String.join(" ",
						row.get("probe_point_world_x_m"),
						row.get("probe_point_world_y_m"),
						row.get("probe_point_world_z_m"),
						row.get(velocityPrefix + "_x_mps"),
						row.get(velocityPrefix + "_y_mps"),
						row.get(velocityPrefix + "_z_mps")
				));
			}
			Files.writeString(
					directory.resolve(entry.getKey() + "_" + suffix + "_U.xy"),
					String.join("\n", rawLines) + "\n",
					StandardCharsets.UTF_8
			);
			List<String> pLines = new ArrayList<>();
			pLines.add("# x y z p");
			for (Map<String, String> row : entry.getValue()) {
				pLines.add(String.join(" ",
						row.get("probe_point_world_x_m"),
						row.get("probe_point_world_y_m"),
						row.get("probe_point_world_z_m"),
						row.get("pressure_jump_pa")
				));
			}
			Files.writeString(
					directory.resolve(entry.getKey() + "_" + suffix + "_p.xy"),
					String.join("\n", pLines) + "\n",
					StandardCharsets.UTF_8
			);
		}
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
}
