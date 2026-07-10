package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiucDa4002AxialSurfaceV1ShadowScenarioExporterTest {
	@Test
	void writesTheCompleteScenarioSetDeterministically(@TempDir Path tempDir)
			throws IOException {
		Path first = tempDir.resolve("first");
		Path second = tempDir.resolve("second");
		UiucDa4002AxialSurfaceV1ShadowScenarioExporter.write(first);
		UiucDa4002AxialSurfaceV1ShadowScenarioExporter.write(second);

		String firstScenarios = Files.readString(first.resolve(
				UiucDa4002AxialSurfaceV1ShadowScenarioExporter.SCENARIO_FILE_NAME));
		String firstRotors = Files.readString(first.resolve(
				UiucDa4002AxialSurfaceV1ShadowScenarioExporter.ROTOR_FILE_NAME));
		String firstSummary = Files.readString(first.resolve(
				UiucDa4002AxialSurfaceV1ShadowScenarioExporter.SUMMARY_FILE_NAME));
		assertEquals(firstScenarios, Files.readString(second.resolve(
				UiucDa4002AxialSurfaceV1ShadowScenarioExporter.SCENARIO_FILE_NAME)));
		assertEquals(firstRotors, Files.readString(second.resolve(
				UiucDa4002AxialSurfaceV1ShadowScenarioExporter.ROTOR_FILE_NAME)));
		assertEquals(firstSummary, Files.readString(second.resolve(
				UiucDa4002AxialSurfaceV1ShadowScenarioExporter.SUMMARY_FILE_NAME)));

		assertEquals(17, Files.readAllLines(first.resolve(
				UiucDa4002AxialSurfaceV1ShadowScenarioExporter.SCENARIO_FILE_NAME)).size());
		assertEquals(65, Files.readAllLines(first.resolve(
				UiucDa4002AxialSurfaceV1ShadowScenarioExporter.ROTOR_FILE_NAME)).size());
		assertTrue(firstSummary.contains("16 scenarios / 64 rotor rows"));
		assertTrue(firstSummary.contains("scalar comparable `56`"));
		assertTrue(firstSummary.contains("blocked `8`"));
		assertTrue(firstSummary.contains("`five_hover`"));
		assertTrue(firstSummary.contains("`nine_outside`"));
		assertFalse((firstScenarios + firstRotors + firstSummary).contains("NaN"));
		assertFalse((firstScenarios + firstRotors + firstSummary).contains("Infinity"));
	}
}
