package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJLookupEvaluator;

class OfflineFlightRecorderCtCpJTelemetryTest {
	@TempDir
	Path tempDir;

	@Test
	void apDroneTraceExportsCtCpJReferenceTelemetryColumns() throws IOException {
		Path output = tempDir.resolve("apdrone.csv");
		OfflineFlightRecorder.FlightReport report = OfflineFlightRecorder.record("apdrone", output, 1.0);

		List<String> lines = Files.readAllLines(output);
		assertTrue(lines.size() > 2);
		String[] header = lines.get(0).split(",", -1);
		int airDensityRatioIndex = column(header, "effective_air_density_ratio");
		int motorRpmIndex = column(header, "motor_0_rpm");
		int rotorThrustIndex = column(header, "rotor_0_thrust_n");
		int motorAerodynamicTorqueIndex = column(header, "motor_0_aero_torque_nm");
		int rotorPropellerJIndex = column(header, "rotor_0_prop_advance_ratio_j");
		int runtimeValidIndex = column(header, "rotor_ctcpj_runtime_valid");
		int rotorRuntimeValidIndex = column(header, "rotor_0_ctcpj_runtime_valid");
		int rotorRuntimeCtIndex = column(header, "rotor_0_ctcpj_runtime_ct");
		int rotorRuntimeCpIndex = column(header, "rotor_0_ctcpj_runtime_cp");
		int rotorRuntimeEtaIndex = column(header, "rotor_0_ctcpj_runtime_eta");
		int rotorRuntimeDiskLoadingIndex = column(header, "rotor_0_ctcpj_runtime_disk_loading_n_m2");
		int rotorRuntimeInducedVelocityIndex = column(header,
				"rotor_0_ctcpj_runtime_ideal_induced_velocity_mps");
		int rotorRuntimeMomentumRatioIndex = column(header,
				"rotor_0_ctcpj_runtime_ideal_momentum_power_over_shaft_power");
		int availableIndex = column(header, "rotor_ctcpj_ref_available");
		int blockedIndex = column(header, "rotor_ctcpj_ref_blocked");
		int clampedIndex = column(header, "rotor_ctcpj_ref_clamped");
		int referenceRpmIndex = column(header, "rotor_ctcpj_ref_rpm");
		int rotorStatusIndex = column(header, "rotor_0_ctcpj_ref_status");
		int rotorLookupStatusIndex = column(header, "rotor_0_ctcpj_ref_lookup_status");
		int rotorJIndex = column(header, "rotor_0_ctcpj_ref_j");
		int rotorRpmIndex = column(header, "rotor_0_ctcpj_ref_rpm");
		int rotorCtIndex = column(header, "rotor_0_ctcpj_ref_ct");
		int rotorPowerIndex = column(header, "rotor_0_ctcpj_ref_shaft_power_w");
		int rotorThrustResidualIndex = column(header, "rotor_0_ctcpj_ref_thrust_residual_n");
		int rotorPowerResidualIndex = column(header, "rotor_0_ctcpj_ref_shaft_power_residual_w");
		int rotorTorqueResidualIndex = column(header, "rotor_0_ctcpj_ref_shaft_torque_residual_nm");
		int rotorThrustRatioIndex = column(header, "rotor_0_ctcpj_ref_thrust_ratio");
		int rotorTorqueRatioIndex = column(header, "rotor_0_ctcpj_ref_shaft_torque_ratio");
		int staticAvailableIndex = column(header, "rotor_ctcpj_static_ref_available");
		int rotorStaticCtIndex = column(header, "rotor_0_ctcpj_static_ref_ct");
		int rotorStaticPowerIndex = column(header, "rotor_0_ctcpj_static_ref_shaft_power_w");
		int rotorStaticDiskLoadingIndex = column(header, "rotor_0_ctcpj_static_ref_disk_loading_n_m2");
		int rotorStaticInducedVelocityIndex = column(header,
				"rotor_0_ctcpj_static_ref_ideal_induced_velocity_mps");
		int rotorStaticMomentumRatioIndex = column(header,
				"rotor_0_ctcpj_static_ref_ideal_momentum_power_over_shaft_power");
		int rotorStaticThrustResidualIndex = column(header, "rotor_0_ctcpj_static_ref_thrust_residual_n");
		int rotorStaticPowerResidualIndex = column(header, "rotor_0_ctcpj_static_ref_shaft_power_residual_w");
		int rotorStaticTorqueResidualIndex = column(header, "rotor_0_ctcpj_static_ref_shaft_torque_residual_nm");
		int rotorStaticInducedVelocityResidualIndex = column(header,
				"rotor_0_ctcpj_static_ref_induced_velocity_residual_mps");
		int rotorStaticThrustRatioIndex = column(header, "rotor_0_ctcpj_static_ref_thrust_ratio");
		int rotorStaticInducedVelocityRatioIndex = column(header,
				"rotor_0_ctcpj_static_ref_induced_velocity_ratio");

		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_runtime_ct"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_runtime_cp"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_7_ctcpj_runtime_ideal_momentum_power_over_shaft_power"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_ct"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_rpm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_lookup_status"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_shaft_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_thrust_residual_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_shaft_torque_residual_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_ctcpj_ref_thrust_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_ctcpj_ref_shaft_torque_ratio"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_static_ref_ct"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains(
				"rotor_0_ctcpj_static_ref_ideal_induced_velocity_mps"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_static_ref_shaft_torque_residual_nm"));

		boolean sawReferenceState = false;
		boolean sawPositiveReferenceRpm = false;
		boolean sawStaticReferenceState = false;
		boolean sawRuntimeCoefficientState = false;
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			assertEquals(header.length, row.length, "CSV row " + i + " column count changed");
			double runtimeValid = Double.parseDouble(row[runtimeValidIndex]);
			double rotorRuntimeValid = Double.parseDouble(row[rotorRuntimeValidIndex]);
			double available = Double.parseDouble(row[availableIndex]);
			double blocked = Double.parseDouble(row[blockedIndex]);
			double clamped = Double.parseDouble(row[clampedIndex]);
			double staticAvailable = Double.parseDouble(row[staticAvailableIndex]);
			double rpm = Double.parseDouble(row[motorRpmIndex]);
			double omega = rpm * 2.0 * Math.PI / 60.0;
			double aerodynamicPower = Double.parseDouble(row[motorAerodynamicTorqueIndex]) * omega;
			if (runtimeValid > 0.0 || rotorRuntimeValid > 0.0) {
				sawRuntimeCoefficientState = true;
				double thrust = Double.parseDouble(row[rotorThrustIndex]);
				double propellerJ = Double.parseDouble(row[rotorPropellerJIndex]);
				double runtimeCt = Double.parseDouble(row[rotorRuntimeCtIndex]);
				double runtimeCp = Double.parseDouble(row[rotorRuntimeCpIndex]);
				double runtimeEta = Double.parseDouble(row[rotorRuntimeEtaIndex]);
				double runtimeDiskLoading = Double.parseDouble(row[rotorRuntimeDiskLoadingIndex]);
				double runtimeInducedVelocity = Double.parseDouble(row[rotorRuntimeInducedVelocityIndex]);
				double runtimeMomentumRatio = Double.parseDouble(row[rotorRuntimeMomentumRatioIndex]);
				assertTrue(Double.isFinite(runtimeCt));
				assertTrue(Double.isFinite(runtimeCp));
				assertTrue(Double.isFinite(runtimeEta));
				assertTrue(Double.isFinite(runtimeDiskLoading));
				assertTrue(Double.isFinite(runtimeInducedVelocity));
				assertTrue(Double.isFinite(runtimeMomentumRatio));
				if (rotorRuntimeValid > 0.0 && rpm > 0.0) {
					double n = rpm / 60.0;
					double airDensity = PropellerArchiveCtCpJDimensionalRotorResponse
							.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
							* Math.max(0.20, Double.parseDouble(row[airDensityRatioIndex]));
					assertEquals(
							thrust / (airDensity * n * n * Math.pow(diameter, 4.0)),
							runtimeCt,
							5.0e-6
					);
					assertEquals(
							aerodynamicPower / (airDensity * n * n * n * Math.pow(diameter, 5.0)),
							runtimeCp,
							1.0e-4
					);
					if (runtimeCp > 1.0e-9) {
						assertEquals(propellerJ * runtimeCt / runtimeCp, runtimeEta, 5.0e-5);
					}
				}
			}
			if (available > 0.0 || blocked > 0.0 || clamped > 0.0) {
				sawReferenceState = true;
				double referenceRpm = Double.parseDouble(row[referenceRpmIndex]);
				double rotorRpm = Double.parseDouble(row[rotorRpmIndex]);
				double lookupStatus = Double.parseDouble(row[rotorLookupStatusIndex]);
				assertTrue(Double.isFinite(referenceRpm));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStatusIndex])));
				assertTrue(Double.isFinite(lookupStatus));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorJIndex])));
				assertTrue(Double.isFinite(rotorRpm));
				sawPositiveReferenceRpm |= referenceRpm > 0.0 || rotorRpm > 0.0;
				if (blocked > 0.0) {
					assertEquals(
							PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.REFERENCE_WINDOW_UNAVAILABLE.ordinal(),
							(int) lookupStatus
					);
				}
				if (clamped > 0.0) {
					assertEquals(
							PropellerArchiveCtCpJLookupEvaluator.LookupStatusCode.CLAMPED.ordinal(),
							(int) lookupStatus
					);
				}
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorCtIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorPowerIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorThrustResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorPowerResidualIndex])));
				assertEquals(
						aerodynamicPower - Double.parseDouble(row[rotorPowerIndex]),
						Double.parseDouble(row[rotorPowerResidualIndex]),
						2.0e-3
				);
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorTorqueResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorThrustRatioIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorTorqueRatioIndex])));
			}
			if (staticAvailable > 0.0) {
				sawStaticReferenceState = true;
				double staticCt = Double.parseDouble(row[rotorStaticCtIndex]);
				double staticPower = Double.parseDouble(row[rotorStaticPowerIndex]);
				assertTrue(staticCt > 0.15 && staticCt < 0.17);
				assertTrue(staticPower > 0.0);
				assertTrue(Double.parseDouble(row[rotorStaticDiskLoadingIndex]) > 0.0);
				assertTrue(Double.parseDouble(row[rotorStaticInducedVelocityIndex]) > 0.0);
				assertTrue(Double.parseDouble(row[rotorStaticMomentumRatioIndex]) > 0.0);
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticThrustResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticPowerResidualIndex])));
				assertEquals(
						aerodynamicPower - staticPower,
						Double.parseDouble(row[rotorStaticPowerResidualIndex]),
						2.0e-3
				);
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticTorqueResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticInducedVelocityResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticThrustRatioIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStaticInducedVelocityRatioIndex])));
			}
		}
		assertTrue(sawRuntimeCoefficientState, "apDrone trace should expose runtime CT/CP/J coefficient telemetry");
		assertTrue(report.ctCpJRuntimeCoefficientRotorSampleCount() > 0);
		assertFiniteOrderedRange(
				report.minCtCpJRuntimeThrustCoefficientCt(),
				report.meanCtCpJRuntimeThrustCoefficientCt(),
				report.maxCtCpJRuntimeThrustCoefficientCt(),
				"runtime CT"
		);
		assertFiniteOrderedRange(
				report.minCtCpJRuntimePowerCoefficientCp(),
				report.meanCtCpJRuntimePowerCoefficientCp(),
				report.maxCtCpJRuntimePowerCoefficientCp(),
				"runtime CP"
		);
		assertFiniteOrderedRange(
				report.minCtCpJRuntimePropulsiveEfficiencyEta(),
				report.meanCtCpJRuntimePropulsiveEfficiencyEta(),
				report.maxCtCpJRuntimePropulsiveEfficiencyEta(),
				"runtime eta"
		);
		assertTrue(report.minCtCpJRuntimeThrustCoefficientCt() > 0.0);
		assertTrue(report.minCtCpJRuntimePowerCoefficientCp() > 0.0);
		assertTrue(report.minCtCpJRuntimePropulsiveEfficiencyEta() >= 0.0);
		assertTrue(Double.isFinite(report.meanCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter()));
		assertTrue(Double.isFinite(report.maxCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter()));
		assertTrue(report.meanCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter() > 0.0);
		assertTrue(report.maxCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter()
				>= report.meanCtCpJRuntimeDiskLoadingNewtonsPerSquareMeter());
		assertTrue(Double.isFinite(report.meanCtCpJRuntimeIdealMomentumPowerOverShaftPower()));
		assertTrue(Double.isFinite(report.maxCtCpJRuntimeIdealMomentumPowerOverShaftPower()));
		assertTrue(report.meanCtCpJRuntimeIdealMomentumPowerOverShaftPower() > 0.0);
		assertTrue(report.maxCtCpJRuntimeIdealMomentumPowerOverShaftPower()
				>= report.meanCtCpJRuntimeIdealMomentumPowerOverShaftPower());
		assertTrue(sawReferenceState, "apDrone trace should expose available or blocked CT/CP/J reference telemetry");
		assertTrue(sawPositiveReferenceRpm, "CT/CP/J reference telemetry should preserve lookup RPM for envelope diagnosis");
		assertTrue(sawStaticReferenceState, "apDrone trace should expose static CT/CP shadow telemetry");
		assertTrue(report.ctCpJReferenceRotorSampleCount() > 0);
		assertEquals(
				report.ctCpJReferenceRotorSampleCount(),
				report.ctCpJReferenceAvailableRotorSampleCount() + report.ctCpJReferenceBlockedRotorSampleCount()
		);
		assertTrue(report.ctCpJReferenceCoverageFraction() >= 0.0);
		assertTrue(report.ctCpJReferenceCoverageFraction() <= 1.0);
		assertTrue(report.ctCpJReferenceAvailableRotorSampleCount() > 0);
		assertEquals(0, report.ctCpJReferenceBlockedRotorSampleCount());
		assertTrue(report.ctCpJReferenceClampedRotorSampleCount()
				<= report.ctCpJReferenceAvailableRotorSampleCount());
		assertTrue(Double.isFinite(report.meanCtCpJReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.meanCtCpJReferenceAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.meanCtCpJReferenceAbsTorqueResidualNewtonMeters()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceAbsTorqueResidualNewtonMeters()));
		assertTrue(report.ctCpJStaticReferenceRotorSampleCount() > 0);
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceAbsTorqueResidualNewtonMeters()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceAbsTorqueResidualNewtonMeters()));
		assertTrue(report.maxCtCpJStaticReferenceAbsThrustResidualNewtons() > 0.0);
		assertTrue(report.maxCtCpJStaticReferenceAbsPowerResidualWatts() > 0.0);
		assertTrue(report.maxCtCpJStaticReferenceAbsTorqueResidualNewtonMeters() > 0.0);
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceAbsInducedVelocityResidualMetersPerSecond()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceAbsInducedVelocityResidualMetersPerSecond()));
		assertTrue(Double.isFinite(report.meanCtCpJStaticReferenceIdealMomentumPowerOverShaftPower()));
		assertTrue(Double.isFinite(report.maxCtCpJStaticReferenceIdealMomentumPowerOverShaftPower()));
		assertTrue(report.maxCtCpJStaticReferenceAbsInducedVelocityResidualMetersPerSecond() > 0.0);
		assertTrue(report.meanCtCpJStaticReferenceIdealMomentumPowerOverShaftPower() > 0.0);
	}

	private static int column(String[] header, String name) {
		for (int i = 0; i < header.length; i++) {
			if (name.equals(header[i])) {
				return i;
			}
		}
		throw new AssertionError("missing CSV column: " + name);
	}

	private static void assertFiniteOrderedRange(double min, double mean, double max, String label) {
		assertTrue(Double.isFinite(min), label + " min should be finite");
		assertTrue(Double.isFinite(mean), label + " mean should be finite");
		assertTrue(Double.isFinite(max), label + " max should be finite");
		assertTrue(min <= mean, label + " min should not exceed mean");
		assertTrue(mean <= max, label + " mean should not exceed max");
	}
}
