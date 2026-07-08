package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLocalVoxelFlowSolver;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLocalVoxelFlowState;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJLocalVoxelFlowSolverExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
	private static final double CELL_SIZE = 0.04;
	private static final int PADDING_CELLS = 1;
	private static final int SUBCELL_SAMPLES = 3;
	private static final double TIME_STEP = 0.005;
	private static final double KINEMATIC_VISCOSITY = 1.0e-4;
	private static final int STEPS = 3;
	private static final double MAX_ADVECTION_COURANT = 0.75;
	private static final int PRESSURE_PROJECTION_ITERATIONS = 12;
	private static final double DOWNSTREAM_WAKE_LENGTH = 0.24;

	@Test
	void csvLinesExportMultiStepLocalVoxelFlowSummaries() {
		List<String> lines = CtCpJLocalVoxelFlowSolverExporter.csvLines(
				"apDrone",
				RHO,
				SOURCE_THICKNESS,
				CELL_SIZE,
				PADDING_CELLS,
				SUBCELL_SAMPLES,
				TIME_STEP,
				KINEMATIC_VISCOSITY,
				STEPS,
				25.0,
				0.0,
				MAX_ADVECTION_COURANT,
				PRESSURE_PROJECTION_ITERATIONS,
				DOWNSTREAM_WAKE_LENGTH
		);
		Map<String, Integer> columns = columns(lines);

		assertEquals(41, lines.size());
		assertTrue(lines.get(0).startsWith("preset,case,row_kind,solver_row_kind,step_index"));
		assertTrue(lines.stream().noneMatch(line -> line.contains("NaN")));

		Map<String, String> hoverInitial =
				recordFor(lines, columns, "static_anchored_source_hover", "raw_source", "initial", 0);
		Map<String, String> hoverStep0 =
				recordFor(lines, columns, "static_anchored_source_hover", "raw_source", "step", 0);
		Map<String, String> hoverStep2 =
				recordFor(lines, columns, "static_anchored_source_hover", "raw_source", "step", 2);
		Map<String, String> blockedStep2 =
				recordFor(lines, columns, "static_anchored_source_high_j_block", "raw_source", "step", 2);

		assertEquals(0, integer(hoverInitial, "completed_steps"));
		assertEquals(0.0, number(hoverInitial, "source_impulse_world_y_ns"), 1.0e-15);
		assertEquals(0.0, number(hoverInitial, "kinetic_energy_after_diffusion_j"), 1.0e-15);
		assertEquals(0, integer(hoverInitial, "solid_cell_count"));
		assertEquals(0, integer(hoverInitial, "solid_clamped_cell_count"));
		assertEquals(STEPS, integer(hoverStep2, "configured_step_count"));
		assertEquals(STEPS, integer(hoverStep2, "completed_steps"));
		assertEquals(KINEMATIC_VISCOSITY * TIME_STEP / (CELL_SIZE * CELL_SIZE),
				number(hoverStep2, "diffusion_number"), 1.0e-15);
		assertEquals(MAX_ADVECTION_COURANT,
				number(hoverStep2, "max_advection_courant_number"), 1.0e-15);
		assertEquals(DOWNSTREAM_WAKE_LENGTH,
				number(hoverStep2, "downstream_wake_length_m"), 1.0e-15);
		assertEquals(0, integer(hoverStep2, "solid_box_count"));
		assertEquals(0.0, number(hoverStep2, "solid_box_minimum_volume_fraction"), 1.0e-15);
		assertEquals(0, integer(hoverStep2, "solid_occluded_source_cell_count"));
		assertEquals(0.0, number(hoverStep2, "solid_occluded_source_momentum_rate_world_y_n"), 1.0e-15);
		assertTrue(number(hoverStep2, "advection_courant_number") > 0.0);
		assertTrue(number(hoverStep2, "advection_courant_number") <= MAX_ADVECTION_COURANT + 1.0e-12);
		assertTrue(integer(hoverStep2, "advection_substep_count") > 1);
		assertEquals(PRESSURE_PROJECTION_ITERATIONS, integer(hoverStep2, "pressure_projection_iterations"));
		assertTrue(integer(hoverStep2, "active_cell_count") > 0);
		assertEquals(0, integer(hoverStep2, "solid_cell_count"));
		assertEquals(0, integer(hoverStep2, "solid_clamped_cell_count"));
		assertTrue(number(hoverStep2, "source_mass_flow_kg_s") > 0.0);
		assertTrue(number(hoverStep2, "cumulative_source_mass_kg") > 0.0);
		assertTrue(number(hoverStep2, "source_ideal_momentum_power_w") > 0.0);
		assertEquals(number(hoverStep2, "source_ideal_momentum_power_w") * TIME_STEP,
				number(hoverStep2, "source_ideal_momentum_energy_j"), 1.0e-12);
		assertEquals(number(hoverStep2, "kinetic_energy_after_source_j")
						- number(hoverStep2, "kinetic_energy_before_source_j"),
				number(hoverStep2, "flow_kinetic_energy_source_delta_j"), 1.0e-12);
		assertEquals(number(hoverStep2, "flow_kinetic_energy_source_delta_j")
						- number(hoverStep2, "source_ideal_momentum_energy_j"),
				number(hoverStep2,
						"flow_kinetic_energy_source_delta_minus_ideal_momentum_energy_j"),
				1.0e-12);
		assertEquals(number(hoverStep2, "source_ideal_momentum_power_w") * TIME_STEP * STEPS,
				number(hoverStep2, "cumulative_source_ideal_momentum_energy_j"), 1.0e-12);
		assertEquals(number(hoverStep2, "cumulative_flow_kinetic_energy_source_delta_j")
						- number(hoverStep2, "cumulative_source_ideal_momentum_energy_j"),
				number(hoverStep2,
						"cumulative_flow_kinetic_energy_source_delta_minus_ideal_momentum_energy_j"),
				1.0e-12);
		assertTrue(Math.abs(number(hoverStep2, "through_flow_impulse_world_y_ns")) > 0.0);
		assertTrue(number(hoverStep2, "kinetic_energy_after_advection_j") > 0.0);
		assertTrue(number(hoverStep2, "kinetic_energy_after_diffusion_j") > 0.0);
		assertTrue(number(hoverStep2, "kinetic_energy_after_projection_j") > 0.0);
		assertEquals(number(hoverStep2, "kinetic_energy_after_projection_j"),
				number(hoverStep2, "kinetic_energy_after_solid_boundary_j"), 1.0e-15);
		assertEquals(0.0, number(hoverStep2, "kinetic_energy_solid_boundary_delta_j"), 1.0e-15);
		assertTrue(number(hoverStep2, "max_speed_after_advection_mps") > 0.0);
		assertTrue(number(hoverStep2, "max_speed_after_diffusion_mps") > 0.0);
		assertTrue(number(hoverStep2, "max_speed_after_projection_mps") > 0.0);
		assertEquals(number(hoverStep2, "max_speed_after_projection_mps"),
				number(hoverStep2, "max_speed_after_solid_boundary_mps"), 1.0e-15);
		assertTrue(number(hoverStep2, "max_divergence_before_projection_s") > 0.0);
		assertTrue(number(hoverStep2, "max_divergence_after_projection_s")
				< number(hoverStep2, "max_divergence_before_projection_s"));
		assertTrue(number(hoverStep2, "rms_divergence_after_projection_s")
				< number(hoverStep2, "rms_divergence_before_projection_s"));
		assertEquals(number(hoverStep2, "target_body_force_world_y_n"),
				number(hoverStep2, "source_momentum_rate_world_y_n"), 1.0e-9);
		assertEquals(number(hoverStep2, "target_wake_angular_momentum_torque_world_y_nm"),
				number(hoverStep2, "source_wake_angular_momentum_torque_world_y_nm"), 1.0e-12);
		assertEquals(0.0,
				number(hoverStep2, "solid_occluded_source_wake_angular_momentum_torque_world_y_nm"), 1.0e-15);
		assertEquals(number(hoverStep2, "target_body_force_world_y_n") * TIME_STEP,
				number(hoverStep0, "source_impulse_world_y_ns"), 1.0e-12);
		assertEquals(number(hoverStep2, "target_body_force_world_y_n") * TIME_STEP * STEPS,
				number(hoverStep2, "cumulative_source_impulse_world_y_ns"), 1.0e-12);
		assertEquals(number(hoverStep2, "source_wake_angular_momentum_torque_world_y_nm") * TIME_STEP,
				number(hoverStep0, "source_wake_angular_momentum_impulse_world_y_nm_s"), 1.0e-14);
		assertEquals(number(hoverStep2, "source_wake_angular_momentum_torque_world_y_nm") * TIME_STEP * STEPS,
				number(hoverStep2, "cumulative_source_wake_angular_momentum_impulse_world_y_nm_s"), 1.0e-14);
		assertEquals(number(hoverStep2, "cumulative_source_impulse_world_y_ns")
						+ number(hoverStep2, "cumulative_through_flow_impulse_world_y_ns")
						+ number(hoverStep2, "cumulative_advection_momentum_residual_world_y_ns")
						+ number(hoverStep2, "cumulative_projection_momentum_residual_world_y_ns")
						+ number(hoverStep2, "cumulative_solid_boundary_momentum_residual_world_y_ns"),
				number(hoverStep2, "final_momentum_world_y_ns"), 1.0e-9);
		assertEquals(number(hoverStep2, "advection_momentum_after_world_y_ns")
						- number(hoverStep2, "advection_momentum_before_world_y_ns"),
				number(hoverStep2, "advection_momentum_residual_world_y_ns"), 1.0e-12);
		assertEquals(0.0, number(hoverStep2, "diffusion_momentum_residual_world_x_ns"), 1.0e-12);
		assertEquals(0.0, number(hoverStep2, "diffusion_momentum_residual_world_y_ns"), 1.0e-12);
		assertEquals(0.0, number(hoverStep2, "diffusion_momentum_residual_world_z_ns"), 1.0e-12);
		assertEquals(number(hoverStep2, "projection_momentum_after_world_y_ns")
						- number(hoverStep2, "projection_momentum_before_world_y_ns"),
				number(hoverStep2, "projection_momentum_residual_world_y_ns"), 1.0e-12);
		assertEquals(number(hoverStep2, "solid_boundary_momentum_after_world_y_ns")
						- number(hoverStep2, "solid_boundary_momentum_before_world_y_ns"),
				number(hoverStep2, "solid_boundary_momentum_residual_world_y_ns"), 1.0e-12);
		assertEquals(0.0, number(hoverStep2, "solid_boundary_momentum_residual_world_y_ns"), 1.0e-15);
		assertEquals(0.0,
				number(hoverStep2, "cumulative_solid_boundary_momentum_residual_world_y_ns"), 1.0e-15);
		assertTrue(Double.isFinite(number(hoverStep2, "flow_angular_momentum_reference_world_x_m")));
		assertTrue(Double.isFinite(number(hoverStep2, "flow_angular_momentum_after_source_world_y_nm_s")));
		assertTrue(Double.isFinite(
				number(hoverStep2, "flow_angular_momentum_after_solid_boundary_world_y_nm_s")));
		assertEquals(number(hoverStep2, "flow_angular_momentum_after_source_world_y_nm_s")
						- number(hoverStep2, "flow_angular_momentum_before_source_world_y_nm_s"),
				number(hoverStep2, "flow_angular_momentum_source_delta_world_y_nm_s"), 1.0e-12);
		assertEquals(number(hoverStep2, "flow_angular_momentum_source_delta_world_y_nm_s")
						- number(hoverStep2, "source_wake_angular_momentum_impulse_world_y_nm_s"),
				number(hoverStep2,
						"flow_angular_momentum_source_delta_minus_wake_impulse_world_y_nm_s"),
				1.0e-12);

		assertEquals("EMPTY_SOURCE_FIELD", blockedStep2.get("grid_status"));
		assertEquals(0, integer(blockedStep2, "active_cell_count"));
		assertEquals(0, integer(blockedStep2, "solid_cell_count"));
		assertEquals(0, integer(blockedStep2, "solid_clamped_cell_count"));
		assertEquals(0, integer(blockedStep2, "applied_source_count"));
		assertEquals(MAX_ADVECTION_COURANT,
				number(blockedStep2, "max_advection_courant_number"), 1.0e-15);
		assertEquals(DOWNSTREAM_WAKE_LENGTH,
				number(blockedStep2, "downstream_wake_length_m"), 1.0e-15);
		assertEquals(0, integer(blockedStep2, "solid_box_count"));
		assertEquals(0.0, number(blockedStep2, "solid_box_minimum_volume_fraction"), 1.0e-15);
		assertEquals(0, integer(blockedStep2, "solid_occluded_source_cell_count"));
		assertEquals(0.0, number(blockedStep2, "advection_courant_number"), 1.0e-15);
		assertEquals(1, integer(blockedStep2, "advection_substep_count"));
		assertEquals(PRESSURE_PROJECTION_ITERATIONS,
				integer(blockedStep2, "pressure_projection_iterations"));
		assertEquals(0.0, number(blockedStep2, "source_impulse_world_y_ns"), 1.0e-15);
		assertEquals(0.0, number(blockedStep2, "through_flow_impulse_world_y_ns"), 1.0e-15);
		assertEquals(0.0, number(blockedStep2, "source_ideal_momentum_power_w"), 1.0e-15);
		assertEquals(0.0, number(blockedStep2, "source_ideal_momentum_energy_j"), 1.0e-15);
		assertEquals(0.0, number(blockedStep2, "flow_kinetic_energy_source_delta_j"), 1.0e-15);
		assertEquals(0.0,
				number(blockedStep2,
						"flow_kinetic_energy_source_delta_minus_ideal_momentum_energy_j"),
				1.0e-15);
		assertEquals(0.0, number(blockedStep2, "kinetic_energy_after_advection_j"), 1.0e-15);
		assertEquals(0.0, number(blockedStep2, "kinetic_energy_after_diffusion_j"), 1.0e-15);
		assertEquals(0.0, number(blockedStep2, "kinetic_energy_after_projection_j"), 1.0e-15);
		assertEquals(0.0, number(blockedStep2, "kinetic_energy_after_solid_boundary_j"), 1.0e-15);
		assertEquals(0.0, number(blockedStep2, "max_divergence_after_projection_s"), 1.0e-15);
		assertEquals(0.0,
				number(blockedStep2, "cumulative_solid_boundary_momentum_residual_world_y_ns"), 1.0e-15);
		assertEquals(0.0, number(blockedStep2, "final_momentum_world_y_ns"), 1.0e-15);
		assertEquals(0.0,
				number(blockedStep2, "flow_angular_momentum_after_source_world_y_nm_s"), 1.0e-15);
		assertEquals(0.0,
				number(blockedStep2, "flow_angular_momentum_source_delta_world_y_nm_s"), 1.0e-15);
		assertEquals(0.0,
				number(blockedStep2,
						"flow_angular_momentum_source_delta_minus_wake_impulse_world_y_nm_s"),
				1.0e-15);
		assertEquals(0.0,
				number(blockedStep2, "flow_angular_momentum_after_solid_boundary_world_y_nm_s"), 1.0e-15);
	}

	@Test
	void csvLinesCanApplyWorldSolidBoxMask() {
		CtCpJLocalVoxelFlowSolverExporter.SolidBoxExportConfig solidBoxConfig =
				new CtCpJLocalVoxelFlowSolverExporter.SolidBoxExportConfig(
						List.of(new PropellerArchiveCtCpJLocalVoxelFlowState.VoxelSolidMask.WorldSolidBox(
								new Vec3(-10.0, -10.0, -10.0),
								new Vec3(10.0, 10.0, 10.0))),
						0.0);

		List<String> lines = CtCpJLocalVoxelFlowSolverExporter.csvLines(
				"apDrone",
				RHO,
				SOURCE_THICKNESS,
				CELL_SIZE,
				PADDING_CELLS,
				SUBCELL_SAMPLES,
				TIME_STEP,
				KINEMATIC_VISCOSITY,
				1,
				25.0,
				0.0,
				MAX_ADVECTION_COURANT,
				PRESSURE_PROJECTION_ITERATIONS,
				DOWNSTREAM_WAKE_LENGTH,
				solidBoxConfig
		);
		Map<String, Integer> columns = columns(lines);

		assertEquals(21, lines.size());
		assertTrue(lines.stream().noneMatch(line -> line.contains("NaN")));

		Map<String, String> hoverInitial =
				recordFor(lines, columns, "static_anchored_source_hover", "raw_source", "initial", 0);
		Map<String, String> hoverStep =
				recordFor(lines, columns, "static_anchored_source_hover", "raw_source", "step", 0);

		assertEquals(1, integer(hoverStep, "solid_box_count"));
		assertEquals(0.0, number(hoverStep, "solid_box_minimum_volume_fraction"), 1.0e-15);
		assertTrue(integer(hoverStep, "grid_cell_count") > 0);
		assertEquals(integer(hoverStep, "grid_cell_count"), integer(hoverInitial, "solid_cell_count"));
		assertEquals(integer(hoverStep, "grid_cell_count"), integer(hoverStep, "solid_cell_count"));
		assertEquals(0, integer(hoverInitial, "solid_clamped_cell_count"));
		assertEquals(0, integer(hoverStep, "solid_clamped_cell_count"));
		assertTrue(integer(hoverStep, "solid_occluded_source_cell_count") > 0);
		assertEquals(0.0, number(hoverStep, "source_momentum_rate_world_y_n"), 1.0e-15);
		assertEquals(number(hoverStep, "target_body_force_world_y_n"),
				number(hoverStep, "solid_occluded_source_momentum_rate_world_y_n"), 1.0e-9);
		assertEquals(0.0,
				number(hoverStep, "source_wake_angular_momentum_torque_world_y_nm"), 1.0e-15);
		assertEquals(number(hoverStep, "target_wake_angular_momentum_torque_world_y_nm"),
				number(hoverStep, "solid_occluded_source_wake_angular_momentum_torque_world_y_nm"), 1.0e-12);
		assertEquals(0.0, number(hoverStep, "source_impulse_world_y_ns"), 1.0e-15);
		assertEquals(0.0,
				number(hoverStep, "source_wake_angular_momentum_impulse_world_y_nm_s"), 1.0e-15);
		assertEquals(0.0, number(hoverStep, "source_ideal_momentum_power_w"), 1.0e-15);
		assertEquals(0.0, number(hoverStep, "source_ideal_momentum_energy_j"), 1.0e-15);
		assertEquals(0.0, number(hoverStep, "flow_kinetic_energy_source_delta_j"), 1.0e-15);
		assertEquals(0.0, number(hoverStep, "source_mass_flow_kg_s"), 1.0e-15);
		assertEquals(0.0, number(hoverStep, "kinetic_energy_after_solid_boundary_j"), 1.0e-15);
		assertEquals(0.0, number(hoverStep, "kinetic_energy_solid_boundary_delta_j"), 1.0e-15);
		assertEquals(0.0, number(hoverStep, "max_speed_after_solid_boundary_mps"), 1.0e-15);
		assertEquals(0.0, number(hoverStep, "solid_boundary_momentum_residual_world_y_ns"), 1.0e-15);
		assertEquals(number(hoverStep, "solid_boundary_momentum_residual_world_y_ns"),
				number(hoverStep, "cumulative_solid_boundary_momentum_residual_world_y_ns"), 1.0e-15);
		assertEquals(0.0, number(hoverStep, "final_momentum_world_y_ns"), 1.0e-12);
	}

	@Test
	void mainAcceptsDelimitedSolidBoxList(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("two-box-local-voxel-solver.csv");

		CtCpJLocalVoxelFlowSolverExporter.main(new String[] {
				"apDrone",
				output.toString(),
				Double.toString(RHO),
				Double.toString(SOURCE_THICKNESS),
				Double.toString(CELL_SIZE),
				Integer.toString(PADDING_CELLS),
				Integer.toString(SUBCELL_SAMPLES),
				Double.toString(TIME_STEP),
				Double.toString(KINEMATIC_VISCOSITY),
				"1",
				"25.0",
				"0.0",
				Double.toString(MAX_ADVECTION_COURANT),
				Integer.toString(PRESSURE_PROJECTION_ITERATIONS),
				Double.toString(DOWNSTREAM_WAKE_LENGTH),
				"-10,-10,-10,0,10,10;0,-10,-10,10,10,10",
				"0.999"
		});

		List<String> lines = Files.readAllLines(output);
		Map<String, Integer> columns = columns(lines);
		Map<String, String> hoverInitial =
				recordFor(lines, columns, "static_anchored_source_hover", "raw_source", "initial", 0);
		Map<String, String> hoverStep =
				recordFor(lines, columns, "static_anchored_source_hover", "raw_source", "step", 0);

		assertEquals(21, lines.size());
		assertTrue(lines.stream().noneMatch(line -> line.contains("NaN")));
		assertEquals(2, integer(hoverStep, "solid_box_count"));
		assertEquals(0.999, number(hoverStep, "solid_box_minimum_volume_fraction"), 1.0e-15);
		assertTrue(integer(hoverStep, "grid_cell_count") > 0);
		assertEquals(integer(hoverStep, "grid_cell_count"), integer(hoverInitial, "solid_cell_count"));
		assertEquals(integer(hoverStep, "grid_cell_count"), integer(hoverStep, "solid_cell_count"));
		assertEquals(0, integer(hoverStep, "solid_clamped_cell_count"));
		assertTrue(integer(hoverStep, "solid_occluded_source_cell_count") > 0);
		assertEquals(0.0, number(hoverStep, "source_momentum_rate_world_y_n"), 1.0e-15);
		assertEquals(number(hoverStep, "target_body_force_world_y_n"),
				number(hoverStep, "solid_occluded_source_momentum_rate_world_y_n"), 1.0e-9);
		assertEquals(0.0, number(hoverStep, "final_momentum_world_y_ns"), 1.0e-12);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("local-voxel-solver.csv");

		CtCpJLocalVoxelFlowSolverExporter.write("apDrone", output, RHO);

		List<String> lines = Files.readAllLines(output);
		assertTrue(lines.size() > 10);
		assertTrue(lines.get(0).contains("cumulative_source_impulse_world_y_ns"));
		assertTrue(lines.get(0).contains("max_advection_courant_number"));
		assertTrue(lines.get(0).contains("downstream_wake_length_m"));
		assertTrue(lines.get(0).contains("advection_courant_number"));
		assertTrue(lines.get(0).contains("advection_substep_count"));
		assertTrue(lines.get(0).contains("cumulative_advection_momentum_residual_world_y_ns"));
		assertTrue(lines.get(0).contains("pressure_projection_iterations"));
		assertTrue(lines.get(0).contains("max_divergence_after_projection_s"));
		assertTrue(lines.get(0).contains("cumulative_projection_momentum_residual_world_y_ns"));
		assertTrue(lines.get(0).contains("solid_box_count"));
		assertTrue(lines.get(0).contains("solid_box_minimum_volume_fraction"));
		assertTrue(lines.get(0).contains("solid_occluded_source_cell_count"));
		assertTrue(lines.get(0).contains("solid_occluded_source_momentum_rate_world_y_n"));
		assertTrue(lines.get(0).contains("target_wake_angular_momentum_torque_world_y_nm"));
		assertTrue(lines.get(0).contains("source_wake_angular_momentum_torque_world_y_nm"));
		assertTrue(lines.get(0).contains("solid_occluded_source_wake_angular_momentum_torque_world_y_nm"));
		assertTrue(lines.get(0).contains("cumulative_source_wake_angular_momentum_impulse_world_y_nm_s"));
		assertTrue(lines.get(0).contains("source_ideal_momentum_power_w"));
		assertTrue(lines.get(0).contains("flow_kinetic_energy_source_delta_j"));
		assertTrue(lines.get(0).contains(
				"cumulative_flow_kinetic_energy_source_delta_minus_ideal_momentum_energy_j"));
		assertTrue(lines.get(0).contains("flow_angular_momentum_reference_world_y_m"));
		assertTrue(lines.get(0).contains("flow_angular_momentum_before_source_world_y_nm_s"));
		assertTrue(lines.get(0).contains("flow_angular_momentum_after_source_world_y_nm_s"));
		assertTrue(lines.get(0).contains("flow_angular_momentum_source_delta_world_y_nm_s"));
		assertTrue(lines.get(0).contains(
				"flow_angular_momentum_source_delta_minus_wake_impulse_world_y_nm_s"));
		assertTrue(lines.get(0).contains("flow_angular_momentum_after_solid_boundary_world_y_nm_s"));
		assertTrue(lines.get(0).contains("solid_cell_count"));
		assertTrue(lines.get(0).contains("solid_clamped_cell_count"));
		assertTrue(lines.get(0).contains("cumulative_solid_boundary_momentum_residual_world_y_ns"));
		assertTrue(lines.get(0).contains("kinetic_energy_after_solid_boundary_j"));
		assertTrue(lines.get(0).contains("solid_boundary_momentum_residual_world_y_ns"));
		Map<String, Integer> columns = columns(lines);
		Map<String, String> hoverStep =
				recordFor(lines, columns, "static_anchored_source_hover", "raw_source", "step", 0);
		assertTrue(number(hoverStep, "advection_courant_number")
				<= PropellerArchiveCtCpJLocalVoxelFlowSolver.DEFAULT_MAX_ADVECTION_COURANT_NUMBER + 1.0e-12);
		assertTrue(lines.get(0).contains("kinetic_energy_after_diffusion_j"));
		assertTrue(lines.get(0).contains("diffusion_momentum_residual_world_y_ns"));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchored_source_high_j_block,raw_source,step,")));
	}

	private static Map<String, Integer> columns(List<String> lines) {
		String[] header = lines.get(0).split(",", -1);
		Map<String, Integer> columns = new LinkedHashMap<>();
		for (int i = 0; i < header.length; i++) {
			columns.put(header[i], i);
		}
		return columns;
	}

	private static Map<String, String> recordFor(
			List<String> lines,
			Map<String, Integer> columns,
			String caseName,
			String rowKind,
			String solverRowKind,
			int stepIndex
	) {
		return lines.stream()
				.skip(1)
				.map(line -> record(line, columns))
				.filter(row -> row.get("case").equals(caseName)
						&& row.get("row_kind").equals(rowKind)
						&& row.get("solver_row_kind").equals(solverRowKind)
						&& integer(row, "step_index") == stepIndex)
				.findFirst()
				.orElseThrow();
	}

	private static Map<String, String> record(String line, Map<String, Integer> columns) {
		String[] cells = line.split(",", -1);
		Map<String, String> record = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : columns.entrySet()) {
			record.put(entry.getKey(), cells[entry.getValue()]);
		}
		return record;
	}

	private static int integer(Map<String, String> row, String columnName) {
		return Integer.parseInt(row.get(columnName));
	}

	private static double number(Map<String, String> row, String columnName) {
		return Double.parseDouble(row.get(columnName));
	}
}
