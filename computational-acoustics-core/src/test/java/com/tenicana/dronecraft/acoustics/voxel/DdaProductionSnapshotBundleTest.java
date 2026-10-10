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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DdaProductionSnapshotBundleTest {
	private static final String CONTENT_HASH =
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	@TempDir
	Path temporaryDirectory;

	@Test
	void roundTripsNegativeCoordinatesAndIdentity() throws IOException {
		DdaProductionSnapshotBundle.Bundle expected = bundle();
		Path output = temporaryDirectory.resolve("snapshot-v1.bin");

		DdaProductionSnapshotBundle.writeAtomic(output, expected);
		DdaProductionSnapshotBundle.Bundle actual =
				DdaProductionSnapshotBundle.read(output);

		assertEquals(expected.metadata(), actual.metadata());
		assertEquals(
				expected.snapshot().diagnosticSha256(),
				actual.snapshot().diagnosticSha256()
		);
		assertEquals(expected.rays(), actual.rays());
		assertEquals(
				AcousticMaterials.WOOD,
				actual.snapshot().sampleAt(-7, -12, -3).material()
		);
		try (var files = Files.list(temporaryDirectory)) {
			assertEquals(List.of(output), files.toList());
		}
	}

	@Test
	void rejectsSnapshotHashCorruption() throws IOException {
		Path output = temporaryDirectory.resolve("corrupt-v1.bin");
		DdaProductionSnapshotBundle.write(output, bundle());
		byte[] bytes = Files.readAllBytes(output);
		bytes[140] ^= 1;
		Files.write(output, bytes);

		assertThrows(
				IOException.class,
				() -> DdaProductionSnapshotBundle.read(output)
		);
	}

	@Test
	void rejectsMalformedUtf8Metadata() throws IOException {
		Path output = temporaryDirectory.resolve("malformed-utf8-v1.bin");
		DdaProductionSnapshotBundle.write(output, bundle());
		byte[] bytes = Files.readAllBytes(output);
		bytes[56] = (byte) 0xc3;
		Files.write(output, bytes);

		assertThrows(
				IOException.class,
				() -> DdaProductionSnapshotBundle.read(output)
		);
	}

	@Test
	void rejectsMaterialOutsideCanonicalTable() {
		SparseMaterialSnapshot snapshot = SparseMaterialSnapshot.builder()
				.put(
						0,
						0,
						0,
						DirectPathSolver.MaterialSample.full(
								new com.tenicana.dronecraft.acoustics.AcousticMaterial(
										"custom",
										com.tenicana.dronecraft.acoustics.AcousticBands.SILENT,
										com.tenicana.dronecraft.acoustics.AcousticBands.SILENT,
										0.0
								)
						)
				)
				.build();
		DdaProductionSnapshotBundle.Bundle invalid =
				new DdaProductionSnapshotBundle.Bundle(
						metadata(),
						snapshot,
						List.of(ray())
				);

		assertThrows(
				IllegalArgumentException.class,
				() -> DdaProductionSnapshotBundle.write(
						temporaryDirectory.resolve("invalid.bin"),
						invalid
				)
		);
	}

	private static DdaProductionSnapshotBundle.Bundle bundle() {
		SparseMaterialSnapshot snapshot = SparseMaterialSnapshot.builder()
				.put(
						4,
						5,
						6,
						new DirectPathSolver.MaterialSample(
								AcousticMaterials.GLASS,
								0.75
						)
				)
				.put(
						-7,
						-12,
						-3,
						DirectPathSolver.MaterialSample.full(
								AcousticMaterials.WOOD
						)
				)
				.build();
		return new DdaProductionSnapshotBundle.Bundle(
				metadata(),
				snapshot,
				List.of(ray())
		);
	}

	private static DdaProductionSnapshotBundle.Metadata metadata() {
		return new DdaProductionSnapshotBundle.Metadata(
				1,
				"1.21.11",
				"test-build",
				CONTENT_HASH,
				42L
		);
	}

	private static DdaProductionSnapshotBundle.Ray ray() {
		return new DdaProductionSnapshotBundle.Ray(
				new AcousticVector(-6.5, -11.5, -2.5),
				new AcousticVector(4.5, 5.5, 6.5),
				192
		);
	}
}
