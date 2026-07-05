package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJCurveExporterTest {
	@Test
	void csvLinesExportAcceptedReferenceDimensionalCurve() {
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		List<String> lines = CtCpJCurveExporter.csvLines(
				"apDrone",
				PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER,
				diameter
		);

		assertEquals(14, lines.size());
		assertTrue(lines.get(0).startsWith("preset,case,query_j,query_rpm,effective_j,effective_rpm"));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("apDrone,static_anchor_low_rpm,0.00000000000000,1477.80000000000")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("apDrone,mid_domain_mid_rpm,0.406400000000000,4712.25000000000")));
		assertTrue(lines.stream().anyMatch(line ->
				line.startsWith("apDrone,high_domain_max_rpm,0.731520000000000,7946.70000000000")));
		assertFalse(lines.stream().skip(1).anyMatch(line -> line.contains(",true,") || line.contains(",BLOCKED,")));

		double midThrust = numericCell(lines, "mid_domain_mid_rpm", "0.406400000000000", 13);
		double highThrust = numericCell(lines, "high_domain_max_rpm", "0.731520000000000", 13);
		double midPower = numericCell(lines, "mid_domain_mid_rpm", "0.406400000000000", 14);
		double highPower = numericCell(lines, "high_domain_max_rpm", "0.731520000000000", 14);
		assertTrue(midThrust > 0.0);
		assertTrue(highThrust > midThrust);
		assertTrue(highPower > midPower);
	}

	@Test
	void writeCreatesParentDirectoriesAndCsvFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("curve.csv");
		double diameter = DroneConfig.apDrone().rotors().get(0).radiusMeters() * 2.0;
		CtCpJCurveExporter.write(
				"apDrone",
				output,
				PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER,
				diameter
		);

		List<String> lines = Files.readAllLines(output);
		assertEquals(14, lines.size());
		assertTrue(lines.get(0).contains("shaft_torque_nm"));
	}

	private static double numericCell(List<String> lines, String caseName, String queryJ, int cellIndex) {
		String line = lines.stream()
				.filter(candidate -> candidate.startsWith("apDrone," + caseName + "," + queryJ + ","))
				.findFirst()
				.orElseThrow();
		return Double.parseDouble(line.split(",", -1)[cellIndex]);
	}
}
