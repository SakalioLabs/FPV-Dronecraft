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
import com.tenicana.dronecraft.sim.SnelMcCrinkRotationalAugmentation;

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
		assertEquals(93, columns.size());
		assertTrue(lines.stream().skip(1).allMatch(line -> cells(line).length == columns.size()));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				"SOLVED".equals(textCell(line, columns, "bemt_status"))));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				"SOLVED".equals(textCell(line, columns, "augmented_bemt_status"))));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				SnelMcCrinkRotationalAugmentation.Policy.SNEL_MCCRINK_NORMAL_FORCE.name()
						.equals(textCell(line, columns, "rotational_augmentation_policy"))));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				SnelMcCrinkRotationalAugmentation.DATA_SOURCE_ID.equals(
						textCell(line, columns, "rotational_augmentation_source_id"))));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				doubleCell(line, columns, "cp_residual_fraction") < -0.17));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				doubleCell(line, columns, "augmented_ct_delta_from_baseline") > 0.0
						&& doubleCell(line, columns, "augmented_cp_delta_from_baseline") > 0.0
						&& doubleCell(line, columns, "augmented_thrust_delta_n") > 0.0
						&& doubleCell(line, columns, "augmented_shaft_power_delta_w") > 0.0
						&& doubleCell(line, columns, "augmented_shaft_torque_delta_nm") > 0.0
						&& doubleCell(line, columns,
								"rotational_augmentation_torque_nm") > 0.0
						&& doubleCell(line, columns,
								"rotational_augmentation_power_w") > 0.0));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				doubleCell(line, columns, "augmented_cp_residual_fraction") < -0.13));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				doubleCell(line, columns, "rotational_augmentation_applied_annuli")
						+ doubleCell(line, columns,
								"rotational_augmentation_source_span_limited_annuli")
						== doubleCell(line, columns, "bemt_annulus_count")));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				doubleCell(line, columns,
						"rotational_augmentation_applied_on_clamped_polar_annuli") > 0.0));
		assertTrue(lines.stream().skip(1).allMatch(line ->
				doubleCell(line, columns, "rotational_augmentation_max_abs_delta_cl") > 0.0
						&& doubleCell(line, columns,
								"rotational_augmentation_max_abs_delta_cd") > 0.0));
		assertTrue(lines.stream().skip(1).allMatch(line -> "true".equals(
				textCell(line, columns,
						"rotational_augmentation_requires_propeller_specific_validation"))));

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
		assertTrue(doubleCell(highNineInch, columns, "augmented_bemt_ct")
				> doubleCell(highNineInch, columns, "bemt_ct"));
		assertTrue(doubleCell(highNineInch, columns, "augmented_bemt_cp")
				> doubleCell(highNineInch, columns, "bemt_cp"));
		assertTrue(doubleCell(highNineInch, columns, "augmented_cp_residual_fraction")
				> doubleCell(highNineInch, columns, "cp_residual_fraction"));
		assertTrue(doubleCell(highNineInch, columns, "augmented_cp_residual_fraction")
				< -0.15);
		assertTrue(doubleCell(highNineInch, columns, "augmented_ct_residual_fraction")
				> 0.02);
		assertTrue(doubleCell(highNineInch, columns, "reynolds_75") > 70_000.0);
		assertEquals(0.0, doubleCell(highNineInch, columns,
				"bemt_reynolds_clamped_annuli"), 0.0);
		assertTrue(doubleCell(highNineInch, columns,
				"bemt_low_re_extension_annuli") > 0.0);
		assertEquals(1.0, doubleCell(highNineInch, columns,
				"bemt_reynolds_supported_thrust_weight_fraction"), 1.0e-15);
		assertTrue(doubleCell(highNineInch, columns,
				"bemt_fully_supported_thrust_weight_fraction") > 0.7);
		assertTrue(doubleCell(lowNineInch, columns,
				"bemt_reynolds_supported_thrust_weight_fraction") > 0.9);
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
		assertEquals(0.0, doubleCell(highNineInchWithSwirl, columns,
				"required_power_closure_residual_w"), 1.0e-12);
		assertEquals(0.0, doubleCell(highNineInchWithSwirl, columns,
				"required_torque_closure_residual_nm"), 1.0e-15);
		assertTrue(doubleCell(highNineInchWithSwirl, columns,
				"effective_profile_power_scale_if_all_non_lift_loss_were_profile") > 2.0);
		assertTrue(doubleCell(lowNineInch, columns,
				"effective_profile_power_scale_if_all_non_lift_loss_were_profile") < 1.4);
		assertTrue(doubleCell(highNineInchWithSwirl, columns,
				"conditioned_unresolved_power_fraction") > 0.15);
		assertTrue(lines.stream().skip(1).allMatch(line -> "true".equals(
				textCell(line, columns, "effective_profile_power_scale_available"))));

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
		assertTrue(fiveInchRows.stream().allMatch(line ->
				doubleCell(line, columns, "bemt_low_re_extension_annuli")
						== doubleCell(line, columns, "bemt_annulus_count")));
		assertTrue(fiveInchRows.stream().anyMatch(line ->
				doubleCell(line, columns,
						"bemt_reynolds_supported_thrust_weight_fraction") > 0.95));
		assertTrue(fiveInchRows.stream().anyMatch(line ->
				doubleCell(line, columns,
						"rotational_augmentation_applied_on_clamped_polar_annuli")
						< doubleCell(line, columns,
								"rotational_augmentation_applied_annuli")));
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
