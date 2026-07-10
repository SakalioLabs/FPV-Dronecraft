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

import com.tenicana.dronecraft.sim.UiucDa4002PublishedCfdComparison;

class UiucDa4002PublishedCfdComparisonExporterTest {
	@Test
	void exportsTraceableRowsAndAConstrainedInterpretation(@TempDir Path tempDir)
			throws IOException {
		UiucDa4002PublishedCfdComparisonExporter.write(tempDir);
		Path comparisonPath = tempDir.resolve(
				UiucDa4002PublishedCfdComparisonExporter.COMPARISON_FILE_NAME);
		Path summaryPath = tempDir.resolve(
				UiucDa4002PublishedCfdComparisonExporter.SUMMARY_FILE_NAME);
		List<String> comparisonLines = Files.readAllLines(comparisonPath);
		String summary = Files.readString(summaryPath);

		assertEquals(11, comparisonLines.size());
		assertEquals(40, comparisonLines.get(0).split(",", -1).length);
		assertTrue(comparisonLines.stream().skip(1)
				.allMatch(line -> line.split(",", -1).length == 40));
		assertFalse(comparisonLines.stream().anyMatch(line ->
				line.contains("NaN") || line.contains("Infinity")));
		assertTrue(comparisonLines.stream().anyMatch(line ->
				line.contains("K_OMEGA") && line.contains("0.0760000000000000")));
		assertTrue(summary.contains("UIUC DA4002 Published CFD Comparison"));
		assertTrue(summary.contains(UiucDa4002PublishedCfdComparison.SOURCE_PDF_SHA256));
		assertTrue(summary.contains(UiucDa4002PublishedCfdComparison.CFX_SOURCE_LOCATOR));
		assertTrue(summary.contains(
				UiucDa4002PublishedCfdComparison.OPENFOAM_SOURCE_LOCATOR));
		assertTrue(summary.contains("1.17931175815697 kg/m^3"));
		assertTrue(summary.contains("9 of 10 CFX rows"));
		assertTrue(summary.contains("0.076 Nm"));
		assertTrue(summary.contains("not corrected"));
		assertTrue(summary.contains("OpenFOAM Rounded Summary Anchors"));
		assertTrue(summary.contains("does not validate the DA4002 5x3.75 surface"));
	}
}
