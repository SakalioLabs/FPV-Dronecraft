package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tenicana.dronecraft.sim.RotorAxialBladeElementModel;
import com.tenicana.dronecraft.sim.UiucDa4002AdvancePerformanceLookup;

class UiucDa4002AxialBemtCurveExporterTest {
	@Test
	void exportsEveryMeasuredPointAndKeepsTheHighAdvanceBoundaryExplicit(
			@TempDir Path tempDir
	) throws IOException {
		Path output = tempDir.resolve("nested").resolve("comparison.csv");
		UiucDa4002AxialBemtCurveExporter.write(
				output,
				1.225,
				UiucDa4002AxialBemtCurveExporter.DEFAULT_DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				16
		);
		List<String> lines = Files.readAllLines(output);
		Map<String, Integer> columns = columns(lines.get(0));

		assertEquals(113, lines.size());
		assertEquals(52, columns.size());
		assertTrue(lines.stream().skip(1).allMatch(line ->
				cells(line).length == columns.size()));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				UiucDa4002AdvancePerformanceLookup.DATA_SOURCE_ID.equals(
						textCell(line, columns, "reference_data_source_id"))));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				Math.abs(doubleCell(line, columns, "reference_eta_closure_residual"))
						< 2.0e-4));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				"true".equals(textCell(line, columns, "bemt_raw_solution_available"))));
		assertTrue(lines.stream().skip(1).anyMatch(line ->
				RotorAxialBladeElementModel.Status.BLOCKED_NON_POSITIVE_THRUST.name()
						.equals(textCell(line, columns, "bemt_status"))));

		String low = row(lines, columns, "da4002-9x6.75-rpm2013", 0.315488);
		String mid = row(lines, columns, "da4002-9x6.75-rpm2013", 0.543643);
		String high = row(lines, columns, "da4002-9x6.75-rpm2013", 0.779962);
		String negative = row(lines, columns, "da4002-9x6.75-rpm2013", 0.894262);
		assertEquals(RotorAxialBladeElementModel.Status.SOLVED.name(),
				textCell(low, columns, "bemt_status"));
		assertEquals(RotorAxialBladeElementModel.Status.SOLVED.name(),
				textCell(mid, columns, "bemt_status"));
		assertEquals(RotorAxialBladeElementModel.Status.SOLVED.name(),
				textCell(high, columns, "bemt_status"));
		assertEquals(RotorAxialBladeElementModel.Status.BLOCKED_NON_POSITIVE_THRUST.name(),
				textCell(negative, columns, "bemt_status"));
		assertTrue(doubleCell(low, columns, "bemt_raw_ct")
				> doubleCell(mid, columns, "bemt_raw_ct"));
		assertTrue(doubleCell(mid, columns, "bemt_raw_ct")
				> doubleCell(high, columns, "bemt_raw_ct"));
		assertTrue(doubleCell(high, columns, "bemt_raw_ct") > 0.0);
		assertTrue(doubleCell(negative, columns, "bemt_raw_ct") < 0.0);
		assertTrue(doubleCell(low, columns, "bemt_raw_shaft_power_w") > 0.0);
		assertTrue(doubleCell(mid, columns, "bemt_raw_shaft_torque_nm") > 0.0);
		assertTrue(doubleCell(high, columns, "bemt_raw_eta") > 0.0);
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
			String caseId,
			double advanceRatio
	) {
		return lines.stream()
				.skip(1)
				.filter(line -> caseId.equals(textCell(line, columns, "case_id")))
				.filter(line -> Math.abs(doubleCell(line, columns, "advance_ratio_j")
						- advanceRatio) < 1.0e-12)
				.findFirst()
				.orElseThrow();
	}

	private static double doubleCell(String line, Map<String, Integer> columns, String column) {
		return Double.parseDouble(textCell(line, columns, column));
	}

	private static String textCell(String line, Map<String, Integer> columns, String column) {
		String value = cells(line)[columns.get(column)];
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1).replace("\"\"", "\"");
		}
		return value;
	}

	private static String[] cells(String line) {
		return line.split(",", -1);
	}
}
