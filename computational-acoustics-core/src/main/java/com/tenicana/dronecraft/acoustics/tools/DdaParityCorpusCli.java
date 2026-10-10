package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.voxel.DdaParityCorpus;

import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Generates a deterministic, versioned binary DDA parity corpus.
 */
public final class DdaParityCorpusCli {
	private static final long DEFAULT_RAY_SEED = 0x4650564444410001L;
	private static final long DEFAULT_MATERIAL_SEED = 0x4D4154455249414CL;

	private DdaParityCorpusCli() {
	}

	public static void main(String[] args) throws Exception {
		Path output = args.length > 0
				? Path.of(args[0])
				: Path.of("build", "research", "dda-parity-v1.bin");
		int randomRays = args.length > 1
				? Integer.parseInt(args[1])
				: 4_096;
		long raySeed = args.length > 2
				? Long.decode(args[2])
				: DEFAULT_RAY_SEED;
		long materialSeed = args.length > 3
				? Long.decode(args[3])
				: DEFAULT_MATERIAL_SEED;
		Path temporary = output.toAbsolutePath().resolveSibling(
				output.getFileName() + ".tmp"
		);
		DdaParityCorpus.StreamingSummary summary;
		try {
			DdaParityCorpus.writeGenerated(
					temporary,
					randomRays,
					raySeed,
					materialSeed
			);
			summary = DdaParityCorpus.verifyStreaming(temporary);
			publish(temporary, output.toAbsolutePath());
		} catch (Exception error) {
			Files.deleteIfExists(temporary);
			throw error;
		}
		String digest = sha256(output.toAbsolutePath());
		System.out.printf(
				"{\"schema\":%d,\"round_trip\":true,\"streaming\":true,"
						+ "\"rays\":%d,\"segments\":%d,"
						+ "\"bytes\":%d,\"sha256\":\"%s\","
						+ "\"material_table_sha256\":\"%s\",\"path\":\"%s\"}%n",
				DdaParityCorpus.SCHEMA_VERSION,
				summary.rays(),
				summary.segments(),
				Files.size(output),
				digest,
				summary.materialTableSha256(),
				output.toAbsolutePath().toString()
						.replace("\\", "\\\\")
						.replace("\"", "\\\"")
		);
	}

	private static void publish(Path temporary, Path output) throws Exception {
		try {
			Files.move(
					temporary,
					output,
					StandardCopyOption.ATOMIC_MOVE,
					StandardCopyOption.REPLACE_EXISTING
			);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(
					temporary,
					output,
					StandardCopyOption.REPLACE_EXISTING
			);
		}
	}

	private static String sha256(Path path) throws Exception {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (
				InputStream source = Files.newInputStream(path);
				DigestInputStream ignored = new DigestInputStream(
						source,
						digest
				)
		) {
			ignored.transferTo(java.io.OutputStream.nullOutputStream());
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}
