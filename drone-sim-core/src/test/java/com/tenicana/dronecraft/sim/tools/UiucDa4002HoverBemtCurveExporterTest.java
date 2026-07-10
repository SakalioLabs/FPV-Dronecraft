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

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.RotorHoverBladeElementModel;

class UiucDa4002HoverBemtCurveExporterTest {
	@Test
	void exportsCompleteMeasuredCurveAndPreservesObservedResidualShape(@TempDir Path tempDir)
			throws IOException {
		Path output = tempDir.resolve("nested").resolve("comparison.csv");
		UiucDa4002HoverBemtCurveExporter.write(
				output,
				PropellerArchiveCtCpJDimensionalRotorResponse
						.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER,
				UiucDa4002HoverBemtCurveExporter.DEFAULT_DYNAMIC_VISCOSITY_PASCAL_SECONDS,
				RotorHoverBladeElementModel.DEFAULT_ANNULI_PER_GEOMETRY_INTERVAL
		);
		List<String> lines = Files.readAllLines(output);
		Map<String, Integer> columns = columns(lines.get(0));

		assertEquals(65, lines.size());
		assertEquals(52, columns.size());
		assertTrue(lines.stream().skip(1).allMatch(line -> cells(line).length == columns.size()));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				"SOLVED".equals(textCell(line, columns, "bemt_status"))));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				doubleCell(line, columns, "cp_residual_fraction") < -0.17));

		String lowNineInch = row(
				lines,
				columns,
				"uiuc-da4002-9x6.75-static",
				"1546.66700000000",
				"AXIAL_MOMENTUM_ONLY"
		);
		String highNineInch = row(
				lines,
				columns,
				"uiuc-da4002-9x6.75-static",
				"5943.33300000000",
				"AXIAL_MOMENTUM_ONLY"
		);
		String highNineInchWithSwirl = row(
				lines,
				columns,
				"uiuc-da4002-9x6.75-static",
				"5943.33300000000",
				"COUPLED_LIFT_TORQUE_ANGULAR_MOMENTUM"
		);
		assertTrue(doubleCell(lowNineInch, columns, "ct_residual_fraction") < -0.20);
		assertTrue(Math.abs(doubleCell(highNineInch, columns, "ct_residual_fraction")) < 0.01);
		assertTrue(doubleCell(highNineInch, columns, "cp_residual_fraction") < -0.17);
		assertTrue(doubleCell(highNineInch, columns, "cp_residual_fraction") > -0.25);
		assertTrue(doubleCell(highNineInch, columns, "reynolds_75") > 70_000.0);
		assertTrue(doubleCell(highNineInch, columns,
				"bemt_reynolds_clamped_annuli") > 0.0);
		assertTrue(doubleCell(highNineInch, columns,
				"bemt_reynolds_clamped_annuli")
				< doubleCell(highNineInch, columns, "bemt_annulus_count"));
		assertEquals(0.0, doubleCell(highNineInch, columns,
				"bemt_momentum_wake_torque_nm"), 0.0);
		assertTrue(doubleCell(highNineInch, columns,
				"bemt_angular_momentum_closure_residual_nm") > 0.0);
		assertTrue(doubleCell(highNineInchWithSwirl, columns, "bemt_ct")
				< doubleCell(highNineInch, columns, "bemt_ct"));
		assertTrue(doubleCell(highNineInchWithSwirl, columns, "bemt_cp")
				< doubleCell(highNineInch, columns, "bemt_cp"));
		assertEquals(0.0, doubleCell(highNineInchWithSwirl, columns,
				"bemt_angular_momentum_closure_residual_nm"), 1.0e-10);
		assertTrue(doubleCell(highNineInchWithSwirl, columns,
				"bemt_momentum_wake_torque_nm") > 0.0);
		assertTrue(doubleCell(highNineInchWithSwirl, columns,
				"bemt_wake_swirl_kinetic_power_w") > 0.0);
		assertTrue(doubleCell(highNineInchWithSwirl, columns,
				"bemt_maximum_tangential_induction_to_blade_speed") < 0.06);

		List<String> fiveInchRows = lines.stream()
				.skip(1)
				.filter(line -> "uiuc-da4002-5x3.75-static"
						.equals(textCell(line, columns, "case_id")))
				.filter(line -> "AXIAL_MOMENTUM_ONLY"
						.equals(textCell(line, columns, "wake_rotation_policy")))
				.toList();
		assertEquals(13, fiveInchRows.size());
		assertTrue(fiveInchRows.stream().allMatch(line ->
				doubleCell(line, columns, "reynolds_75") < 40_000.0));
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
			String rpm,
			String wakeRotationPolicy
	) {
		return lines.stream()
				.skip(1)
				.filter(line -> line.startsWith('"' + caseId + "\","))
				.filter(line -> cells(line)[2].equals(rpm))
				.filter(line -> wakeRotationPolicy.equals(
						textCell(line, columns, "wake_rotation_policy")))
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
