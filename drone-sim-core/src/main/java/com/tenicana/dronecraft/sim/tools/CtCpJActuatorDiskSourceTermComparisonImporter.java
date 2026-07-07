package com.tenicana.dronecraft.sim.tools;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;
import com.tenicana.dronecraft.sim.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CtCpJActuatorDiskSourceTermComparisonImporter {
	private static final String HEADER = String.join(",",
			"preset",
			"case",
			"row_kind",
			"rotor_index",
			"source_thickness_m",
			"reference_lookup_status",
			"reference_clamped",
			"reference_blocked",
			"reference_applied",
			"cfd_solver_status",
			"source_case_sha256",
			"reference_pressure_jump_pa",
			"cfd_pressure_jump_pa",
			"pressure_jump_residual_pa",
			"pressure_jump_residual_fraction",
			"reference_mass_flux_kg_s_m2",
			"cfd_mass_flux_kg_s_m2",
			"mass_flux_residual_kg_s_m2",
			"mass_flux_residual_fraction",
			"reference_ideal_momentum_power_loading_w_m2",
			"cfd_ideal_momentum_power_loading_w_m2",
			"ideal_momentum_power_loading_residual_w_m2",
			"ideal_momentum_power_loading_residual_fraction",
			"reference_thrust_surface_force_world_x_n_m2",
			"reference_thrust_surface_force_world_y_n_m2",
			"reference_thrust_surface_force_world_z_n_m2",
			"cfd_thrust_surface_force_world_x_n_m2",
			"cfd_thrust_surface_force_world_y_n_m2",
			"cfd_thrust_surface_force_world_z_n_m2",
			"thrust_surface_force_residual_world_x_n_m2",
			"thrust_surface_force_residual_world_y_n_m2",
			"thrust_surface_force_residual_world_z_n_m2",
			"thrust_surface_force_residual_magnitude_n_m2",
			"reference_integrated_thrust_force_world_x_n",
			"reference_integrated_thrust_force_world_y_n",
			"reference_integrated_thrust_force_world_z_n",
			"cfd_integrated_thrust_force_world_x_n",
			"cfd_integrated_thrust_force_world_y_n",
			"cfd_integrated_thrust_force_world_z_n",
			"integrated_thrust_force_residual_world_x_n",
			"integrated_thrust_force_residual_world_y_n",
			"integrated_thrust_force_residual_world_z_n",
			"integrated_thrust_force_residual_magnitude_n",
			"reference_body_force_density_world_x_n_m3",
			"reference_body_force_density_world_y_n_m3",
			"reference_body_force_density_world_z_n_m3",
			"cfd_body_force_density_world_x_n_m3",
			"cfd_body_force_density_world_y_n_m3",
			"cfd_body_force_density_world_z_n_m3",
			"body_force_density_residual_world_x_n_m3",
			"body_force_density_residual_world_y_n_m3",
			"body_force_density_residual_world_z_n_m3",
			"body_force_density_residual_magnitude_n_m3",
			"reference_far_wake_axial_velocity_world_x_mps",
			"reference_far_wake_axial_velocity_world_y_mps",
			"reference_far_wake_axial_velocity_world_z_mps",
			"cfd_far_wake_axial_velocity_world_x_mps",
			"cfd_far_wake_axial_velocity_world_y_mps",
			"cfd_far_wake_axial_velocity_world_z_mps",
			"far_wake_axial_velocity_residual_world_x_mps",
			"far_wake_axial_velocity_residual_world_y_mps",
			"far_wake_axial_velocity_residual_world_z_mps",
			"far_wake_axial_velocity_residual_magnitude_mps",
			"cfd_integrated_force_closure_residual_world_x_n",
			"cfd_integrated_force_closure_residual_world_y_n",
			"cfd_integrated_force_closure_residual_world_z_n",
			"cfd_body_force_density_closure_residual_world_x_n_m3",
			"cfd_body_force_density_closure_residual_world_y_n_m3",
			"cfd_body_force_density_closure_residual_world_z_n_m3",
			"comparable",
			"message"
	);
	private static final double EPSILON = 1.0e-12;

	private CtCpJActuatorDiskSourceTermComparisonImporter() {
	}

	public record CfdSourceTermRow(
			String presetName,
			String caseName,
			String rowKind,
			int rotorIndex,
			double sourceThicknessMeters,
			double airDensityKgPerCubicMeter,
			double cfdPressureJumpPascals,
			double cfdMassFluxKilogramsPerSecondSquareMeter,
			double cfdIdealMomentumPowerLoadingWattsPerSquareMeter,
			Vec3 cfdThrustSurfaceForceWorldNewtonsPerSquareMeter,
			Vec3 cfdIntegratedThrustForceWorldNewtons,
			Vec3 cfdBodyForceDensityWorldNewtonsPerCubicMeter,
			Vec3 cfdFarWakeAxialVelocityWorldMetersPerSecond,
			String sourceCaseSha256,
			String solverStatus
	) {
		public CfdSourceTermRow {
			presetName = presetName == null || presetName.isBlank()
					? PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME
					: presetName.trim();
			caseName = caseName == null ? "" : caseName.trim();
			if (caseName.isBlank()) {
				throw new IllegalArgumentException("case must not be blank.");
			}
			rowKind = rowKind == null || rowKind.isBlank() ? "raw_source" : rowKind.trim();
			rotorIndex = Math.max(0, rotorIndex);
			if (!Double.isFinite(sourceThicknessMeters) || sourceThicknessMeters <= 0.0) {
				throw new IllegalArgumentException("sourceThicknessMeters must be finite and positive.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			cfdThrustSurfaceForceWorldNewtonsPerSquareMeter =
					cfdThrustSurfaceForceWorldNewtonsPerSquareMeter == null
							? nanVector()
							: cfdThrustSurfaceForceWorldNewtonsPerSquareMeter;
			cfdIntegratedThrustForceWorldNewtons = cfdIntegratedThrustForceWorldNewtons == null
					? nanVector()
					: cfdIntegratedThrustForceWorldNewtons;
			cfdBodyForceDensityWorldNewtonsPerCubicMeter =
					cfdBodyForceDensityWorldNewtonsPerCubicMeter == null
							? nanVector()
							: cfdBodyForceDensityWorldNewtonsPerCubicMeter;
			cfdFarWakeAxialVelocityWorldMetersPerSecond =
					cfdFarWakeAxialVelocityWorldMetersPerSecond == null
							? nanVector()
							: cfdFarWakeAxialVelocityWorldMetersPerSecond;
			sourceCaseSha256 = sourceCaseSha256 == null ? "" : sourceCaseSha256.trim();
			solverStatus = solverStatus == null || solverStatus.isBlank() ? "UNSPECIFIED" : solverStatus.trim();
		}
	}

	public record ReferenceSourceTermRow(
			String presetName,
			String caseName,
			String rowKind,
			int rotorIndex,
			double sourceThicknessMeters,
			String lookupStatus,
			boolean clamped,
			boolean blocked,
			boolean applied,
			double pressureJumpPascals,
			double massFluxKilogramsPerSecondSquareMeter,
			double idealMomentumPowerLoadingWattsPerSquareMeter,
			Vec3 diskNormalWorld,
			double diskAreaSquareMeters,
			Vec3 thrustSurfaceForceWorldNewtonsPerSquareMeter,
			Vec3 integratedThrustForceWorldNewtons,
			Vec3 bodyForceDensityWorldNewtonsPerCubicMeter,
			Vec3 farWakeAxialVelocityWorldMetersPerSecond
	) {
	}

	public record ComparisonRow(
			CfdSourceTermRow cfd,
			ReferenceSourceTermRow reference,
			double cfdPressureJumpPascals,
			Vec3 cfdThrustSurfaceForceWorldNewtonsPerSquareMeter,
			Vec3 cfdIntegratedThrustForceWorldNewtons,
			Vec3 cfdBodyForceDensityWorldNewtonsPerCubicMeter,
			double pressureJumpResidualPascals,
			double pressureJumpResidualFraction,
			double massFluxResidualKilogramsPerSecondSquareMeter,
			double massFluxResidualFraction,
			double idealMomentumPowerLoadingResidualWattsPerSquareMeter,
			double idealMomentumPowerLoadingResidualFraction,
			Vec3 thrustSurfaceForceResidualWorldNewtonsPerSquareMeter,
			Vec3 integratedThrustForceResidualWorldNewtons,
			Vec3 bodyForceDensityResidualWorldNewtonsPerCubicMeter,
			Vec3 farWakeAxialVelocityResidualWorldMetersPerSecond,
			Vec3 cfdIntegratedForceClosureResidualWorldNewtons,
			Vec3 cfdBodyForceDensityClosureResidualWorldNewtonsPerCubicMeter,
			boolean comparable,
			String message
	) {
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 2 || args.length > 4) {
			throw new IllegalArgumentException(
					"usage: CtCpJActuatorDiskSourceTermComparisonImporter "
							+ "<input.csv> <output.csv> [defaultDensityKgM3] [defaultSourceThicknessM]");
		}
		double defaultDensity = args.length >= 3 && !args[2].isBlank()
				? Double.parseDouble(args[2])
				: PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
		double defaultSourceThickness = args.length >= 4 && !args[3].isBlank()
				? Double.parseDouble(args[3])
				: CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
		write(Path.of(args[0]), Path.of(args[1]), defaultDensity, defaultSourceThickness);
	}

	public static void write(
			Path input,
			Path output,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) throws IOException {
		if (input == null || output == null) {
			throw new IllegalArgumentException("input and output paths must not be null.");
		}
		List<String> lines = csvLines(
				Files.readString(input, StandardCharsets.UTF_8),
				defaultAirDensityKgPerCubicMeter,
				defaultSourceThicknessMeters
		);
		Path parent = output.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(output, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	public static List<String> csvLines(
			String inputCsv,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) {
		List<ComparisonRow> rows = compare(inputCsv, defaultAirDensityKgPerCubicMeter, defaultSourceThicknessMeters);
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		for (ComparisonRow row : rows) {
			lines.add(csvLine(row));
		}
		return List.copyOf(lines);
	}

	public static List<ComparisonRow> compare(
			String inputCsv,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) {
		if (inputCsv == null) {
			throw new IllegalArgumentException("inputCsv must not be null.");
		}
		if (!Double.isFinite(defaultAirDensityKgPerCubicMeter) || defaultAirDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("defaultAirDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(defaultSourceThicknessMeters) || defaultSourceThicknessMeters <= 0.0) {
			throw new IllegalArgumentException("defaultSourceThicknessMeters must be finite and positive.");
		}
		List<ComparisonRow> rows = new ArrayList<>();
		for (Map<String, String> record : parseCsv(inputCsv)) {
			rows.add(compare(cfdRow(record, defaultAirDensityKgPerCubicMeter, defaultSourceThicknessMeters)));
		}
		return List.copyOf(rows);
	}

	public static ComparisonRow compare(CfdSourceTermRow cfd) {
		if (cfd == null) {
			throw new IllegalArgumentException("cfd row must not be null.");
		}
		ReferenceSourceTermRow reference = referenceRow(cfd);
		boolean hasSourceLoad = Double.isFinite(cfd.cfdPressureJumpPascals())
				|| finiteVector(cfd.cfdThrustSurfaceForceWorldNewtonsPerSquareMeter())
				|| finiteVector(cfd.cfdIntegratedThrustForceWorldNewtons())
				|| finiteVector(cfd.cfdBodyForceDensityWorldNewtonsPerCubicMeter());
		Vec3 cfdSurfaceForce = resolvedSurfaceForce(cfd, reference);
		double cfdPressureJump = Double.isFinite(cfd.cfdPressureJumpPascals())
				? cfd.cfdPressureJumpPascals()
				: cfdSurfaceForce.dot(reference.diskNormalWorld());
		Vec3 cfdIntegratedForce = finiteVector(cfd.cfdIntegratedThrustForceWorldNewtons())
				? cfd.cfdIntegratedThrustForceWorldNewtons()
				: cfdSurfaceForce.multiply(reference.diskAreaSquareMeters());
		Vec3 cfdBodyForceDensity = finiteVector(cfd.cfdBodyForceDensityWorldNewtonsPerCubicMeter())
				? cfd.cfdBodyForceDensityWorldNewtonsPerCubicMeter()
				: cfdSurfaceForce.multiply(1.0 / cfd.sourceThicknessMeters());
		Vec3 surfaceResidual = cfdSurfaceForce.subtract(reference.thrustSurfaceForceWorldNewtonsPerSquareMeter());
		Vec3 integratedResidual = cfdIntegratedForce.subtract(reference.integratedThrustForceWorldNewtons());
		Vec3 bodyForceResidual = cfdBodyForceDensity.subtract(reference.bodyForceDensityWorldNewtonsPerCubicMeter());
		Vec3 farWakeResidual = finiteVector(cfd.cfdFarWakeAxialVelocityWorldMetersPerSecond())
				? cfd.cfdFarWakeAxialVelocityWorldMetersPerSecond()
						.subtract(reference.farWakeAxialVelocityWorldMetersPerSecond())
				: nanVector();
		Vec3 integratedClosureResidual = cfdIntegratedForce
				.subtract(cfdSurfaceForce.multiply(reference.diskAreaSquareMeters()));
		Vec3 bodyForceClosureResidual = cfdBodyForceDensity
				.subtract(cfdSurfaceForce.multiply(1.0 / cfd.sourceThicknessMeters()));
		boolean comparable = hasSourceLoad
				&& Double.isFinite(cfdPressureJump)
				&& finiteVector(cfdSurfaceForce)
				&& finiteVector(cfdIntegratedForce)
				&& finiteVector(cfdBodyForceDensity);
		String message = comparable
				? "ct-cp-j-actuator-disk-source-term-comparison-ready"
				: "cfd-row-missing-finite-source-term-channel";
		return new ComparisonRow(
				cfd,
				reference,
				cfdPressureJump,
				cfdSurfaceForce,
				cfdIntegratedForce,
				cfdBodyForceDensity,
				cfdPressureJump - reference.pressureJumpPascals(),
				ratio(cfdPressureJump - reference.pressureJumpPascals(), reference.pressureJumpPascals()),
				cfd.cfdMassFluxKilogramsPerSecondSquareMeter()
						- reference.massFluxKilogramsPerSecondSquareMeter(),
				ratio(cfd.cfdMassFluxKilogramsPerSecondSquareMeter()
								- reference.massFluxKilogramsPerSecondSquareMeter(),
						reference.massFluxKilogramsPerSecondSquareMeter()),
				cfd.cfdIdealMomentumPowerLoadingWattsPerSquareMeter()
						- reference.idealMomentumPowerLoadingWattsPerSquareMeter(),
				ratio(cfd.cfdIdealMomentumPowerLoadingWattsPerSquareMeter()
								- reference.idealMomentumPowerLoadingWattsPerSquareMeter(),
						reference.idealMomentumPowerLoadingWattsPerSquareMeter()),
				surfaceResidual,
				integratedResidual,
				bodyForceResidual,
				farWakeResidual,
				integratedClosureResidual,
				bodyForceClosureResidual,
				comparable,
				message
		);
	}

	private static Vec3 resolvedSurfaceForce(CfdSourceTermRow cfd, ReferenceSourceTermRow reference) {
		if (finiteVector(cfd.cfdThrustSurfaceForceWorldNewtonsPerSquareMeter())) {
			return cfd.cfdThrustSurfaceForceWorldNewtonsPerSquareMeter();
		}
		if (finiteVector(cfd.cfdIntegratedThrustForceWorldNewtons())
				&& reference.diskAreaSquareMeters() > EPSILON) {
			return cfd.cfdIntegratedThrustForceWorldNewtons()
					.multiply(1.0 / reference.diskAreaSquareMeters());
		}
		if (finiteVector(cfd.cfdBodyForceDensityWorldNewtonsPerCubicMeter())) {
			return cfd.cfdBodyForceDensityWorldNewtonsPerCubicMeter()
					.multiply(cfd.sourceThicknessMeters());
		}
		if (Double.isFinite(cfd.cfdPressureJumpPascals())) {
			return reference.diskNormalWorld().multiply(cfd.cfdPressureJumpPascals());
		}
		return nanVector();
	}

	private static CfdSourceTermRow cfdRow(
			Map<String, String> record,
			double defaultAirDensityKgPerCubicMeter,
			double defaultSourceThicknessMeters
	) {
		return new CfdSourceTermRow(
				text(record, "preset", PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME),
				text(record, "case", ""),
				text(record, "row_kind", "raw_source"),
				(int) requiredDouble(record, "rotor_index"),
				optionalDouble(record, "source_thickness_m", defaultSourceThicknessMeters),
				optionalDouble(record, "air_density_kg_m3", defaultAirDensityKgPerCubicMeter),
				optionalDouble(record, "cfd_pressure_jump_pa", Double.NaN),
				optionalDouble(record, "cfd_mass_flux_kg_s_m2", Double.NaN),
				optionalDouble(record, "cfd_ideal_momentum_power_loading_w_m2", Double.NaN),
				vector(record,
						"cfd_thrust_surface_force_world_x_n_m2",
						"cfd_thrust_surface_force_world_y_n_m2",
						"cfd_thrust_surface_force_world_z_n_m2"),
				vector(record,
						"cfd_integrated_thrust_force_world_x_n",
						"cfd_integrated_thrust_force_world_y_n",
						"cfd_integrated_thrust_force_world_z_n"),
				vector(record,
						"cfd_body_force_density_world_x_n_m3",
						"cfd_body_force_density_world_y_n_m3",
						"cfd_body_force_density_world_z_n_m3"),
				vector(record,
						"cfd_far_wake_axial_velocity_world_x_mps",
						"cfd_far_wake_axial_velocity_world_y_mps",
						"cfd_far_wake_axial_velocity_world_z_mps"),
				text(record, "source_case_sha256", ""),
				text(record, "solver_status", "UNSPECIFIED")
		);
	}

	private static ReferenceSourceTermRow referenceRow(CfdSourceTermRow cfd) {
		for (Map<String, String> record : parseCsv(String.join("\n",
				CtCpJActuatorDiskSourceTermExporter.csvLines(
						cfd.presetName(),
						cfd.airDensityKgPerCubicMeter(),
						cfd.sourceThicknessMeters(),
						25.0,
						0.0)))) {
			if (text(record, "case", "").equals(cfd.caseName())
					&& text(record, "row_kind", "").equals(cfd.rowKind())
					&& (int) requiredDouble(record, "rotor_index") == cfd.rotorIndex()) {
				return referenceRow(record);
			}
		}
		throw new IllegalArgumentException("no reference source-term row for "
				+ cfd.presetName() + "/" + cfd.caseName() + "/" + cfd.rowKind() + "/" + cfd.rotorIndex());
	}

	private static ReferenceSourceTermRow referenceRow(Map<String, String> record) {
		return new ReferenceSourceTermRow(
				text(record, "preset", PropellerArchiveCtCpJLookupEvaluator.DEFAULT_PRESET_NAME),
				text(record, "case", ""),
				text(record, "row_kind", "raw_source"),
				(int) requiredDouble(record, "rotor_index"),
				requiredDouble(record, "source_thickness_m"),
				text(record, "lookup_status", ""),
				Boolean.parseBoolean(text(record, "clamped", "false")),
				Boolean.parseBoolean(text(record, "blocked", "false")),
				Boolean.parseBoolean(text(record, "applied", "false")),
				requiredDouble(record, "pressure_jump_pa"),
				requiredDouble(record, "mass_flux_kg_s_m2"),
				requiredDouble(record, "ideal_momentum_power_loading_w_m2"),
				vector(record, "disk_normal_world_x", "disk_normal_world_y", "disk_normal_world_z"),
				requiredDouble(record, "disk_area_m2"),
				vector(record,
						"thrust_surface_force_world_x_n_m2",
						"thrust_surface_force_world_y_n_m2",
						"thrust_surface_force_world_z_n_m2"),
				vector(record,
						"integrated_thrust_force_world_x_n",
						"integrated_thrust_force_world_y_n",
						"integrated_thrust_force_world_z_n"),
				vector(record,
						"body_force_density_world_x_n_m3",
						"body_force_density_world_y_n_m3",
						"body_force_density_world_z_n_m3"),
				vector(record,
						"far_wake_axial_velocity_world_x_mps",
						"far_wake_axial_velocity_world_y_mps",
						"far_wake_axial_velocity_world_z_mps")
		);
	}

	private static List<Map<String, String>> parseCsv(String inputCsv) {
		List<List<String>> rawRows = new ArrayList<>();
		for (String line : inputCsv.split("\\R")) {
			if (line == null || line.isBlank()) {
				continue;
			}
			rawRows.add(parseCsvLine(line));
		}
		if (rawRows.isEmpty()) {
			return List.of();
		}
		List<String> header = rawRows.get(0).stream()
				.map(CtCpJActuatorDiskSourceTermComparisonImporter::normalizeHeader)
				.toList();
		List<Map<String, String>> records = new ArrayList<>();
		for (int row = 1; row < rawRows.size(); row++) {
			Map<String, String> record = new LinkedHashMap<>();
			List<String> cells = rawRows.get(row);
			for (int column = 0; column < header.size(); column++) {
				record.put(header.get(column), column < cells.size() ? cells.get(column).trim() : "");
			}
			records.add(record);
		}
		return records;
	}

	private static List<String> parseCsvLine(String line) {
		List<String> cells = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (quoted) {
				if (ch == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					cell.append(ch);
				}
			} else if (ch == '"') {
				quoted = true;
			} else if (ch == ',') {
				cells.add(cell.toString());
				cell.setLength(0);
			} else {
				cell.append(ch);
			}
		}
		cells.add(cell.toString());
		return cells;
	}

	private static String csvLine(ComparisonRow row) {
		CfdSourceTermRow cfd = row.cfd();
		ReferenceSourceTermRow reference = row.reference();
		return String.join(",",
				escape(cfd.presetName()),
				escape(cfd.caseName()),
				escape(cfd.rowKind()),
				Integer.toString(cfd.rotorIndex()),
				number(cfd.sourceThicknessMeters()),
				escape(reference.lookupStatus()),
				Boolean.toString(reference.clamped()),
				Boolean.toString(reference.blocked()),
				Boolean.toString(reference.applied()),
				escape(cfd.solverStatus()),
				escape(cfd.sourceCaseSha256()),
				number(reference.pressureJumpPascals()),
				number(row.cfdPressureJumpPascals()),
				number(row.pressureJumpResidualPascals()),
				number(row.pressureJumpResidualFraction()),
				number(reference.massFluxKilogramsPerSecondSquareMeter()),
				number(cfd.cfdMassFluxKilogramsPerSecondSquareMeter()),
				number(row.massFluxResidualKilogramsPerSecondSquareMeter()),
				number(row.massFluxResidualFraction()),
				number(reference.idealMomentumPowerLoadingWattsPerSquareMeter()),
				number(cfd.cfdIdealMomentumPowerLoadingWattsPerSquareMeter()),
				number(row.idealMomentumPowerLoadingResidualWattsPerSquareMeter()),
				number(row.idealMomentumPowerLoadingResidualFraction()),
				vec(reference.thrustSurfaceForceWorldNewtonsPerSquareMeter()),
				vec(row.cfdThrustSurfaceForceWorldNewtonsPerSquareMeter()),
				vec(row.thrustSurfaceForceResidualWorldNewtonsPerSquareMeter()),
				number(row.thrustSurfaceForceResidualWorldNewtonsPerSquareMeter().length()),
				vec(reference.integratedThrustForceWorldNewtons()),
				vec(row.cfdIntegratedThrustForceWorldNewtons()),
				vec(row.integratedThrustForceResidualWorldNewtons()),
				number(row.integratedThrustForceResidualWorldNewtons().length()),
				vec(reference.bodyForceDensityWorldNewtonsPerCubicMeter()),
				vec(row.cfdBodyForceDensityWorldNewtonsPerCubicMeter()),
				vec(row.bodyForceDensityResidualWorldNewtonsPerCubicMeter()),
				number(row.bodyForceDensityResidualWorldNewtonsPerCubicMeter().length()),
				vec(reference.farWakeAxialVelocityWorldMetersPerSecond()),
				vec(cfd.cfdFarWakeAxialVelocityWorldMetersPerSecond()),
				vec(row.farWakeAxialVelocityResidualWorldMetersPerSecond()),
				number(row.farWakeAxialVelocityResidualWorldMetersPerSecond().length()),
				vec(row.cfdIntegratedForceClosureResidualWorldNewtons()),
				vec(row.cfdBodyForceDensityClosureResidualWorldNewtonsPerCubicMeter()),
				Boolean.toString(row.comparable()),
				escape(row.message())
		);
	}

	private static String vec(Vec3 value) {
		return String.join(",", number(value.x()), number(value.y()), number(value.z()));
	}

	private static Vec3 vector(Map<String, String> record, String x, String y, String z) {
		return new Vec3(
				optionalDouble(record, x, Double.NaN),
				optionalDouble(record, y, Double.NaN),
				optionalDouble(record, z, Double.NaN)
		);
	}

	private static String normalizeHeader(String value) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
			normalized = normalized.substring(1);
		}
		return normalized;
	}

	private static String text(Map<String, String> record, String name, String fallback) {
		String value = record.getOrDefault(name, "");
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static double requiredDouble(Map<String, String> record, String name) {
		String value = record.get(name);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("required CSV column is missing or blank: " + name);
		}
		return Double.parseDouble(value);
	}

	private static double optionalDouble(Map<String, String> record, String name, double fallback) {
		String value = record.get(name);
		return value == null || value.isBlank() ? fallback : Double.parseDouble(value);
	}

	private static boolean finiteVector(Vec3 value) {
		return value != null && value.isFinite();
	}

	private static Vec3 nanVector() {
		return new Vec3(Double.NaN, Double.NaN, Double.NaN);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return Double.NaN;
		}
		return numerator / denominator;
	}

	private static String number(double value) {
		return Double.isFinite(value) ? String.format(Locale.ROOT, "%.15g", value) : "";
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}
}
