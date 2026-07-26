package com.tenicana.dronecraft.acoustics.voxel;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DdaProductionExpectedResultsTest {
	private static final String CONTENT_HASH =
			"abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";

	@TempDir
	Path temporaryDirectory;

	@Test
	void generatesAndRecomputesEveryExpectedRay() throws IOException {
		Path bundle = temporaryDirectory.resolve("capture.bin");
		Path expected = temporaryDirectory.resolve("capture.expected.bin");
		DdaProductionSnapshotBundle.writeAtomic(bundle, bundle(7L));

		DdaProductionExpectedResults.writeGenerated(bundle, expected);
		DdaProductionExpectedResults.VerificationSummary summary =
				DdaProductionExpectedResults.verifyStreaming(
						bundle,
						expected
				);

		assertEquals(2, summary.rays());
		assertEquals(4L, summary.segments());
		assertEquals(64, summary.bundleSha256().length());
		assertEquals(64, summary.snapshotSha256().length());
	}

	@Test
	void generationIsByteStable() throws IOException {
		Path bundle = temporaryDirectory.resolve("capture.bin");
		Path first = temporaryDirectory.resolve("first.expected.bin");
		Path second = temporaryDirectory.resolve("second.expected.bin");
		DdaProductionSnapshotBundle.writeAtomic(bundle, bundle(7L));

		DdaProductionExpectedResults.writeGenerated(bundle, first);
		DdaProductionExpectedResults.writeGenerated(bundle, second);

		assertArrayEquals(
				Files.readAllBytes(first),
				Files.readAllBytes(second)
		);
	}

	@Test
	void rejectsSnapshotIdentityCorruption() throws IOException {
		Path bundle = temporaryDirectory.resolve("capture.bin");
		Path expected = temporaryDirectory.resolve("capture.expected.bin");
		DdaProductionSnapshotBundle.writeAtomic(bundle, bundle(7L));
		DdaProductionExpectedResults.writeGenerated(bundle, expected);
		byte[] bytes = Files.readAllBytes(expected);
		bytes[44] ^= 1;
		Files.write(expected, bytes);

		IOException error = assertThrows(
				IOException.class,
				() -> DdaProductionExpectedResults.verifyStreaming(
						bundle,
						expected
				)
		);
		assertEquals("snapshot hash mismatch", error.getMessage());
	}

	@Test
	void rejectsSidecarAfterInputBundleChanges() throws IOException {
		Path bundle = temporaryDirectory.resolve("capture.bin");
		Path expected = temporaryDirectory.resolve("capture.expected.bin");
		DdaProductionSnapshotBundle.writeAtomic(bundle, bundle(7L));
		DdaProductionExpectedResults.writeGenerated(bundle, expected);
		DdaProductionSnapshotBundle.writeAtomic(bundle, bundle(8L));

		IOException error = assertThrows(
				IOException.class,
				() -> DdaProductionExpectedResults.verifyStreaming(
						bundle,
						expected
				)
		);
		assertEquals("input bundle hash mismatch", error.getMessage());
	}

	private static DdaProductionSnapshotBundle.Bundle bundle(long generation) {
		SparseMaterialSnapshot snapshot = SparseMaterialSnapshot.builder()
				.put(
						0,
						0,
						0,
						DirectPathSolver.MaterialSample.AIR
				)
				.put(
						1,
						0,
						0,
						new DirectPathSolver.MaterialSample(
								AcousticMaterials.STONE,
								0.5
						)
				)
				.put(
						2,
						0,
						0,
						DirectPathSolver.MaterialSample.full(
								AcousticMaterials.WOOD
						)
				)
				.build();
		return new DdaProductionSnapshotBundle.Bundle(
				new DdaProductionSnapshotBundle.Metadata(
						1,
						"test-minecraft",
						"test-mod",
						CONTENT_HASH,
						generation
				),
				snapshot,
				List.of(
						new DdaProductionSnapshotBundle.Ray(
								new AcousticVector(0.25, 0.5, 0.5),
								new AcousticVector(2.75, 0.5, 0.5),
								16
						),
						new DdaProductionSnapshotBundle.Ray(
								new AcousticVector(0.5, 0.5, 0.5),
								new AcousticVector(0.5, 0.5, 0.5),
								1
						)
				)
		);
	}
}
