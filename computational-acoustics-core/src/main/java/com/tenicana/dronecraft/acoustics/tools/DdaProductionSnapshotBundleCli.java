package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.propagation.SparseMaterialSnapshot;
import com.tenicana.dronecraft.acoustics.voxel.DdaProductionSnapshotBundle;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Generates a deterministic production-bundle fixture for independent
 * cross-language reader verification. It does not capture Minecraft runtime
 * state and must never be presented as a live-world artifact.
 */
public final class DdaProductionSnapshotBundleCli {
	private static final String CONTENT_FINGERPRINT =
			"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";

	private DdaProductionSnapshotBundleCli() {
	}

	public static void main(String[] arguments) throws IOException {
		Path output = arguments.length > 0
				? Path.of(arguments[0])
				: Path.of("build/research/dda-production-fixture-v1.bin");
		DdaProductionSnapshotBundle.Bundle bundle = fixture();
		DdaProductionSnapshotBundle.writeAtomic(output, bundle);
		Path absolute = output.toAbsolutePath();
		System.out.printf(
				"{\"schema\":%d,\"cells\":%d,\"rays\":%d,"
						+ "\"material_table_sha256\":\"%s\","
						+ "\"snapshot_sha256\":\"%s\","
						+ "\"file_sha256\":\"%s\",\"bytes\":%d}%n",
				DdaProductionSnapshotBundle.SCHEMA_VERSION,
				bundle.snapshot().size(),
				bundle.rays().size(),
				AcousticMaterials.diagnosticSha256(),
				bundle.snapshot().diagnosticSha256(),
				sha256(absolute),
				Files.size(absolute)
		);
	}

	private static DdaProductionSnapshotBundle.Bundle fixture() {
		SparseMaterialSnapshot.Builder snapshot =
				SparseMaterialSnapshot.builder();
		List<AcousticMaterial> materials =
				AcousticMaterials.diagnosticTable();
		for (int index = 0; index < materials.size(); index++) {
			AcousticMaterial material = materials.get(index);
			double fillFraction = material.isAir()
					? 0.0
					: (index + 1.0) / materials.size();
			snapshot.put(
					index - 4,
					index * 3 - 12,
					2 - index,
					new DirectPathSolver.MaterialSample(
							material,
							fillFraction
					)
			);
		}
		return new DdaProductionSnapshotBundle.Bundle(
				new DdaProductionSnapshotBundle.Metadata(
						1,
						"fixture-1.21.11",
						"fixture-v1",
						CONTENT_FINGERPRINT,
						42L
				),
				snapshot.build(),
				List.of(
						new DdaProductionSnapshotBundle.Ray(
								new AcousticVector(-3.5, -11.5, 1.5),
								new AcousticVector(3.5, 9.5, -5.5),
								192
						),
						new DdaProductionSnapshotBundle.Ray(
								new AcousticVector(0.5, 0.5, 0.5),
								new AcousticVector(0.5, 0.5, 0.5),
								1
						),
						new DdaProductionSnapshotBundle.Ray(
								new AcousticVector(-64.25, 7.5, 32.75),
								new AcousticVector(64.25, -7.5, -32.75),
								384
						)
				)
		);
	}

	private static String sha256(Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (DigestInputStream input = new DigestInputStream(
					new BufferedInputStream(Files.newInputStream(path)),
					digest
			)) {
				input.transferTo(java.io.OutputStream.nullOutputStream());
			}
			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}
}
