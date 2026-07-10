package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tenicana.dronecraft.sim.UiucDa4002AxialSurfaceV1;
import com.tenicana.dronecraft.sim.tools.UiucDa4002AxialSurfaceV1Exporter.BundleReport;

class UiucDa4002AxialSurfaceV1ExporterTest {
	private static final String EXPECTED_CURVE_BUNDLE_SHA256 =
			"49f20e2f7ea42771ce07bc2b4b1f371b54e6966616921da09c8bbf82612043cf";

	@Test
	void writesAReproducibleChecksummedCurveBundle(@TempDir Path tempDir)
			throws IOException {
		Path first = tempDir.resolve("first");
		Path second = tempDir.resolve("second");
		BundleReport firstReport = UiucDa4002AxialSurfaceV1Exporter.write(first);
		BundleReport secondReport = UiucDa4002AxialSurfaceV1Exporter.write(second);

		assertEquals(firstReport, secondReport);
		assertEquals(12, firstReport.curveArtifacts().size());
		assertEquals(EXPECTED_CURVE_BUNDLE_SHA256,
				firstReport.curveBundleSha256());
		assertTrue(firstReport.curveArtifacts().stream()
				.allMatch(artifact -> artifact.sampleCount() == 41));
		for (var artifact : firstReport.curveArtifacts()) {
			Path curve = first.resolve(artifact.relativePath());
			String content = Files.readString(curve);
			assertEquals(42, Files.readAllLines(curve).size());
			assertEquals(artifact.sha256(), UiucDa4002AxialSurfaceV1.sha256(content));
			assertFalse(content.contains("NaN"));
			assertFalse(content.contains("Infinity"));
		}

		List<String> checksums = Files.readAllLines(first.resolve(
				UiucDa4002AxialSurfaceV1Exporter.CHECKSUM_FILE_NAME));
		assertEquals(UiucDa4002AxialSurfaceV1Exporter.checksumLines(firstReport),
				checksums);
		String manifest = Files.readString(first.resolve(
				UiucDa4002AxialSurfaceV1Exporter.MANIFEST_FILE_NAME));
		assertTrue(manifest.contains(UiucDa4002AxialSurfaceV1.VERSION_ID));
		assertTrue(manifest.contains(UiucDa4002AxialSurfaceV1.sourceDataSha256()));
		assertTrue(manifest.contains(
				UiucDa4002AxialSurfaceV1.interpolationAlgorithmSha256()));
		assertTrue(manifest.contains(EXPECTED_CURVE_BUNDLE_SHA256));
		assertTrue(manifest.contains("32 static + 112 advancing"));
		assertTrue(manifest.contains("Supported residual samples: `170`"));
		assertTrue(manifest.contains("blocked nominal-track candidates: `1`"));
		assertTrue(manifest.contains("External CFD coverage is limited"));
		try (Stream<Path> paths = Files.walk(first)) {
			assertEquals(14, paths.filter(Files::isRegularFile).count());
		}
	}
}
