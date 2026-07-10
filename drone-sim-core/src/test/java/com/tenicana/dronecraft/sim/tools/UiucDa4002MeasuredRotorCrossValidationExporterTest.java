package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiucDa4002MeasuredRotorCrossValidationExporterTest {
	@Test
	void exportsOneResidualTableAndOneHumanSummary(@TempDir Path tempDir)
			throws IOException {
		UiucDa4002MeasuredRotorCrossValidationExporter.write(tempDir);
		Path residualPath = tempDir.resolve(
				UiucDa4002MeasuredRotorCrossValidationExporter.RESIDUAL_FILE_NAME);
		Path summaryPath = tempDir.resolve(
				UiucDa4002MeasuredRotorCrossValidationExporter.SUMMARY_FILE_NAME);
		List<String> residualLines = Files.readAllLines(residualPath);
		String summary = Files.readString(summaryPath);

		assertEquals(171, residualLines.size());
		assertEquals(32, residualLines.get(0).split(",", -1).length);
		assertTrue(residualLines.get(0).contains("signed_shaft_torque_residual_nm"));
		assertTrue(residualLines.stream().skip(1).allMatch(line ->
				line.split(",", -1).length == 32));
		assertFalse(residualLines.stream().anyMatch(line ->
				line.contains("NaN") || line.contains("Infinity")));
		assertTrue(summary.contains("This is internal interpolation validation, "
				+ "not independent aerodynamic validation."));
		assertTrue(summary.contains("| STATIC_ROW_LEAVE_ONE_OUT | da4002-5x3.75 "
				+ "| 11 | 11 | 0 |"));
		assertTrue(summary.contains("| NOMINAL_RPM_TRACK_LEAVE_ONE_OUT "
				+ "| da4002-9x6.75 | 37 | 36 | 1 |"));
		assertTrue(summary.contains("Measured Zero-Thrust Brackets"));
		assertTrue(summary.contains("Blocked Candidates"));
		assertTrue(summary.contains("0.887498000000000"));
		assertTrue(summary.contains("da4002-9x6.75-rpm5064"));
	}
}
