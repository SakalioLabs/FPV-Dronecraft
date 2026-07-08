package com.tenicana.dronecraft.sim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tenicana.dronecraft.sim.PropellerArchiveCtCpJDimensionalRotorResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CtCpJActuatorDiskOpenFoamSampleDictExporterTest {
	private static final double RHO =
			PropellerArchiveCtCpJDimensionalRotorResponse.STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER;
	private static final double SOURCE_THICKNESS = CtCpJActuatorDiskSourceTermExporter.DEFAULT_SOURCE_THICKNESS_METERS;
	private static final Pattern POINT_LINE = Pattern.compile(
			"^\\s*\\([-+0-9.Ee]+ [-+0-9.Ee]+ [-+0-9.Ee]+\\)\\s*$");

	@Test
	void sampleDictContainsDeterministicCenterlineAndWakePlaneSets() {
		String text = CtCpJActuatorDiskOpenFoamSampleDictExporter.sampleDict(
				"apDrone",
				RHO,
				SOURCE_THICKNESS,
				25.0,
				0.0
		);

		assertTrue(text.contains("object      sampleDict;"));
		assertTrue(text.contains("libs (\"libsampling.so\");"));
		assertTrue(text.contains("setFormat raw;"));
		assertTrue(text.contains("fields\n(\n    U\n    p\n);"));
		assertTrue(text.contains("ctcpj_apdrone_static_anchored_source_hover_raw_source_rotor_0_centerline"));
		assertTrue(text.contains("ctcpj_apdrone_static_anchored_source_hover_raw_source_rotor_0_wake_plane"));
		assertTrue(text.contains(
				"(0.0671751442127220 0.0323850000000000 0.0671751442127220)"));
		assertTrue(text.contains(
				"case=static_anchored_source_high_j_block row_kind=raw_source rotor_index=0 "
						+ "source_enabled=false lookup_status=OUT_OF_ENVELOPE_BLOCKED"));
		assertTrue(text.contains("static_anchored_source_mid_j_skew"));

		assertEquals(48, countSetDeclarations(text, "_centerline"));
		assertEquals(48, countSetDeclarations(text, "_wake_plane"));
		assertEquals(2880, countPointLines(text));
	}

	@Test
	void sampleDictPointCountMatchesProbePackets() {
		String text = CtCpJActuatorDiskOpenFoamSampleDictExporter.sampleDict("apDrone", RHO);
		int expectedPoints = CtCpJActuatorDiskWakeProbeExporter.csvLines("apDrone", RHO).size() - 1
				+ CtCpJActuatorDiskWakePlaneProbeExporter.csvLines("apDrone", RHO).size() - 1;

		assertEquals(expectedPoints, countPointLines(text));
	}

	@Test
	void writeCreatesParentDirectoriesAndSampleDictFile(@TempDir Path tempDir) throws IOException {
		Path output = tempDir.resolve("nested").resolve("sampleDict");
		CtCpJActuatorDiskOpenFoamSampleDictExporter.write("apDrone", output, RHO);

		String text = Files.readString(output);
		assertTrue(text.contains("type sets;"));
		assertTrue(text.contains("type    cloud;"));
		assertTrue(text.contains("probe_packet=wake_plane_top_hat points=52"));
		assertTrue(text.contains("probe_packet=centerline_axial_and_swirl_radius points=8"));
		assertEquals(2880, countPointLines(text));
	}

	private static long countSetDeclarations(String text, String suffix) {
		return text.lines()
				.map(String::trim)
				.filter(line -> line.endsWith(suffix))
				.count();
	}

	private static long countPointLines(String text) {
		return text.lines()
				.filter(line -> POINT_LINE.matcher(line).matches())
				.count();
	}
}
