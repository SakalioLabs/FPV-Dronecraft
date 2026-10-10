package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.voxel.DdaProductionSnapshotBundle;

import java.io.IOException;
import java.nio.file.Path;

public final class DdaProductionSnapshotBundleVerifyCli {
	private DdaProductionSnapshotBundleVerifyCli() {
	}

	public static void main(String[] arguments) throws IOException {
		boolean requireComplete = false;
		Path bundlePath = null;
		for (String argument : arguments) {
			if (argument.equals("--require-complete")) {
				requireComplete = true;
			} else if (bundlePath == null) {
				bundlePath = Path.of(argument);
			} else {
				throw new IllegalArgumentException(
						"usage: DdaProductionSnapshotBundleVerifyCli "
								+ "[--require-complete] <bundle>"
				);
			}
		}
		if (bundlePath == null) {
			throw new IllegalArgumentException(
					"production bundle path is required"
			);
		}
		DdaProductionSnapshotBundle.Bundle bundle =
				DdaProductionSnapshotBundle.read(bundlePath);
		if (requireComplete && !bundle.snapshot().complete()) {
			throw new IOException(
					"production snapshot is incomplete"
			);
		}
		System.out.printf(
				"{\"status\":\"valid\",\"schema\":%d,"
						+ "\"mapping_algorithm_version\":%d,"
						+ "\"material_table_schema\":%d,"
						+ "\"material_table_sha256\":\"%s\","
						+ "\"minecraft_version\":\"%s\","
						+ "\"mod_version\":\"%s\","
						+ "\"content_fingerprint_sha256\":\"%s\","
						+ "\"snapshot_generation\":%d,"
						+ "\"snapshot_complete\":%s,"
						+ "\"snapshot_sha256\":\"%s\","
						+ "\"cells\":%d,\"rays\":%d}%n",
				DdaProductionSnapshotBundle.SCHEMA_VERSION,
				bundle.metadata().mappingAlgorithmVersion(),
				AcousticMaterials.DIAGNOSTIC_SCHEMA_VERSION,
				AcousticMaterials.diagnosticSha256(),
				json(bundle.metadata().minecraftVersion()),
				json(bundle.metadata().modVersion()),
				bundle.metadata().contentFingerprintSha256(),
				bundle.metadata().snapshotGeneration(),
				bundle.snapshot().complete(),
				bundle.snapshot().diagnosticSha256(),
				bundle.snapshot().size(),
				bundle.rays().size()
		);
	}

	private static String json(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
				case '"' -> escaped.append("\\\"");
				case '\\' -> escaped.append("\\\\");
				case '\b' -> escaped.append("\\b");
				case '\f' -> escaped.append("\\f");
				case '\n' -> escaped.append("\\n");
				case '\r' -> escaped.append("\\r");
				case '\t' -> escaped.append("\\t");
				default -> {
					if (character < 0x20) {
						escaped.append(String.format(
								"\\u%04x",
								(int) character
						));
					} else {
						escaped.append(character);
					}
				}
			}
		}
		return escaped.toString();
	}
}
