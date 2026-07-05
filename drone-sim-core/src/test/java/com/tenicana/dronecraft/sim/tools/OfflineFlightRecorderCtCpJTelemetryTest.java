package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
		int availableIndex = column(header, "rotor_ctcpj_ref_available");
		int blockedIndex = column(header, "rotor_ctcpj_ref_blocked");
		int rotorStatusIndex = column(header, "rotor_0_ctcpj_ref_status");
		int rotorJIndex = column(header, "rotor_0_ctcpj_ref_j");
		int rotorCtIndex = column(header, "rotor_0_ctcpj_ref_ct");
		int rotorPowerIndex = column(header, "rotor_0_ctcpj_ref_shaft_power_w");
		int rotorThrustResidualIndex = column(header, "rotor_0_ctcpj_ref_thrust_residual_n");
		int rotorPowerResidualIndex = column(header, "rotor_0_ctcpj_ref_shaft_power_residual_w");
		int rotorThrustRatioIndex = column(header, "rotor_0_ctcpj_ref_thrust_ratio");

		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_0_ctcpj_ref_ct"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_shaft_torque_nm"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_7_ctcpj_ref_thrust_residual_n"));
		assertTrue(OfflineFlightRecorder.csvHeader().contains("rotor_ctcpj_ref_thrust_ratio"));

		boolean sawReferenceState = false;
		for (int i = 1; i < lines.size(); i++) {
			String[] row = lines.get(i).split(",", -1);
			assertEquals(header.length, row.length, "CSV row " + i + " column count changed");
			double available = Double.parseDouble(row[availableIndex]);
			double blocked = Double.parseDouble(row[blockedIndex]);
			if (available > 0.0 || blocked > 0.0) {
				sawReferenceState = true;
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorStatusIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorJIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorCtIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorPowerIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorThrustResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorPowerResidualIndex])));
				assertTrue(Double.isFinite(Double.parseDouble(row[rotorThrustRatioIndex])));
			}
		}
		assertTrue(sawReferenceState, "apDrone trace should expose available or blocked CT/CP/J reference telemetry");
		assertTrue(report.ctCpJReferenceRotorSampleCount() > 0);
		assertEquals(
				report.ctCpJReferenceRotorSampleCount(),
				report.ctCpJReferenceAvailableRotorSampleCount() + report.ctCpJReferenceBlockedRotorSampleCount()
		);
		assertTrue(report.ctCpJReferenceCoverageFraction() >= 0.0);
		assertTrue(report.ctCpJReferenceCoverageFraction() <= 1.0);
		assertTrue(Double.isFinite(report.meanCtCpJReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceAbsThrustResidualNewtons()));
		assertTrue(Double.isFinite(report.meanCtCpJReferenceAbsPowerResidualWatts()));
		assertTrue(Double.isFinite(report.maxCtCpJReferenceAbsPowerResidualWatts()));
	}

	private static int column(String[] header, String name) {
		for (int i = 0; i < header.length; i++) {
			if (name.equals(header[i])) {
				return i;
			}
		}
		throw new AssertionError("missing CSV column: " + name);
	}
}
