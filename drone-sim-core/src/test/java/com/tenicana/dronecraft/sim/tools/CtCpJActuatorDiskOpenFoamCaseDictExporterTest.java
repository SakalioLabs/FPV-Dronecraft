package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class CtCpJActuatorDiskOpenFoamCaseDictExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;

	@Test
	void dictsContainCylinderSelectionsAndAccelerationSources() {
		CtCpJActuatorDiskOpenFoamCaseDictExporter.OpenFoamCaseDicts dicts =
				CtCpJActuatorDiskOpenFoamCaseDictExporter.dicts(
						"apDrone",
						RHO,
						SOURCE_THICKNESS,
						25.0,
						0.0
				);
		Map<String, String> hover = sourceRow("static_anchored_source_hover", "raw_source", 0);
		String sourceName = hover.get("source_name");
		String cellSetName = sourceName + "_cells";
		String sourceOptionName = sourceName + "_u_source";
		double accelerationY = Double.parseDouble(hover.get("body_force_density_y_n_m3")) / RHO;

		assertTrue(dicts.topoSetDict().contains("object      topoSetDict;"));
		assertTrue(dicts.topoSetDict().contains("source  cylinderToCell;"));
		assertTrue(dicts.topoSetDict().contains("name    " + cellSetName + ";"));
		assertTrue(dicts.topoSetDict().contains("p1      (0.0671751442127220 -0.0250000000000000 0.0671751442127220);"));
		assertTrue(dicts.topoSetDict().contains("p2      (0.0671751442127220 0.0250000000000000 0.0671751442127220);"));
		assertTrue(dicts.topoSetDict().contains("radius  0.0647700000000000;"));
		assertEquals(48, count(dicts.topoSetDict(), "source  cylinderToCell;"));

		assertTrue(dicts.fvOptions().contains("object      fvOptions;"));
		assertTrue(dicts.fvOptions().contains(sourceOptionName));
		assertTrue(dicts.fvOptions().contains("type            vectorSemiImplicitSource;"));
		assertTrue(dicts.fvOptions().contains("cellSet         " + cellSetName + ";"));
		assertTrue(dicts.fvOptions().contains("volumeMode      specific;"));
		assertTrue(dicts.fvOptions().contains(
				"U           ((0.00000000000000 " + number(accelerationY) + " 0.00000000000000) 0);"));
		assertEquals(enabledSourceCount(), count(dicts.fvOptions(), "type            vectorSemiImplicitSource;"));
	}

	@Test
	void disabledRowsKeepTopoSetButDoNotCreateActiveMomentumSource() {
		CtCpJActuatorDiskOpenFoamCaseDictExporter.OpenFoamCaseDicts dicts =
				CtCpJActuatorDiskOpenFoamCaseDictExporter.dicts("apDrone", RHO);
		String disabledSource = sourceRow("static_anchored_source_high_j_block", "raw_source", 0).get("source_name");

		assertTrue(dicts.topoSetDict().contains("name    " + disabledSource + "_cells;"));
		assertTrue(dicts.fvOptions().contains("// disabled source: " + disabledSource
				+ " lookup_status=OUT_OF_ENVELOPE_BLOCKED blocked=true"));
		assertFalse(dicts.fvOptions().contains(disabledSource + "_u_source"));
	}

	@Test
	void writeCreatesTopoSetDictAndFvOptions(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("case").resolve("system");

		CtCpJActuatorDiskOpenFoamCaseDictExporter.write("apDrone", output, RHO);

		String topoSet = Files.readString(output.resolve("topoSetDict"));
		String fvOptions = Files.readString(output.resolve("fvOptions"));
		assertTrue(topoSet.contains("actions"));
		assertTrue(fvOptions.contains("injectionRateSuSp"));
		assertEquals(48, count(topoSet, "source  cylinderToCell;"));
		assertEquals(enabledSourceCount(), count(fvOptions, "active          yes;"));
	}

	private static Map<String, String> sourceRow(String caseName, String rowKind, int rotorIndex) {
		List<String> lines = CtCpJActuatorDiskOpenFoamSourceTableExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		return lines.stream()
				.skip(1)
				.map(line -> record(line, columns))
				.filter(record -> record.get("case").equals(caseName)
						&& record.get("row_kind").equals(rowKind)
						&& Integer.parseInt(record.get("rotor_index")) == rotorIndex)
				.findFirst()
				.orElseThrow();
	}

	private static long enabledSourceCount() {
		List<String> lines = CtCpJActuatorDiskOpenFoamSourceTableExporter.csvLines("apDrone", RHO);
		Map<String, Integer> columns = columns(lines);
		return lines.stream()
				.skip(1)
				.map(line -> record(line, columns))
				.filter(record -> Boolean.parseBoolean(record.get("source_enabled")))
				.count();
	}

	private static int count(String text, String needle) {
		int count = 0;
		int index = text.indexOf(needle);
		while (index >= 0) {
			count++;
			index = text.indexOf(needle, index + needle.length());
		}
		return count;
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

	private static String number(double value) {
		return String.format(java.util.Locale.ROOT, "%.15g", value);
	}
}
