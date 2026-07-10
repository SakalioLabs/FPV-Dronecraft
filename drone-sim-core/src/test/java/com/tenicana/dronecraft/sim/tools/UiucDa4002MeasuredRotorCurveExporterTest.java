package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel;

class UiucDa4002MeasuredRotorCurveExporterTest {
	@Test
	void exportsStaticForwardHighAdvanceAndBlockedRows(@TempDir Path tempDir)
			throws IOException {
		Path output = tempDir.resolve("nested").resolve("curve.csv");
		UiucDa4002MeasuredRotorCurveExporter.write(
				output,
				UiucDa4002MeasuredRotorModel.Propeller.DA4002_9X6_75,
				4_000.0,
				0.025,
				1.0,
				1.225,
				UiucDa4002MeasuredRotorCurveExporter
						.DEFAULT_DYNAMIC_VISCOSITY_PASCAL_SECONDS
		);
		List<String> lines = Files.readAllLines(output);
		Map<String, Integer> columns = columns(lines.get(0));

		assertEquals(42, lines.size());
		assertEquals(34, columns.size());
		assertEquals("STATIC_POSITIVE_THRUST", textCell(row(lines, columns, 0.0),
				columns, "propulsive_regime"));
		assertEquals("AXIAL_POSITIVE_THRUST", textCell(row(lines, columns, 0.4),
				columns, "propulsive_regime"));
		assertEquals("false", textCell(row(lines, columns, 0.85), columns, "blocked"));
		assertEquals("true", textCell(row(lines, columns, 0.875), columns, "blocked"));
		assertEquals("true", textCell(row(lines, columns, 1.0), columns, "blocked"));
		assertEquals("false", textCell(row(lines, columns, 1.0), columns, "clamped"));
	}

	private static Map<String, Integer> columns(String header) {
		String[] names = header.split(",", -1);
		Map<String, Integer> columns = new HashMap<>();
		for (int index = 0; index < names.length; index++) {
			columns.put(names[index], index);
		}
		return columns;
	}

	private static String row(
			List<String> lines,
			Map<String, Integer> columns,
			double advanceRatio
	) {
		return lines.stream()
				.skip(1)
				.filter(line -> Math.abs(Double.parseDouble(
						textCell(line, columns, "advance_ratio_j")) - advanceRatio) < 1.0e-12)
				.findFirst()
				.orElseThrow();
	}

	private static String textCell(String line, Map<String, Integer> columns, String column) {
		String value = line.split(",", -1)[columns.get(column)];
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1).replace("\"\"", "\"");
		}
		return value;
	}
}
