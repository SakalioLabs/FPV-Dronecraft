package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.voxel.DdaProductionExpectedResults;
import com.tenicana.dronecraft.acoustics.voxel.DdaProductionSnapshotBundle;

import java.io.IOException;
import java.nio.file.Path;

public final class DdaProductionExpectedResultsCli {
	private DdaProductionExpectedResultsCli() {
	}

	public static void main(String[] arguments) throws IOException {
		if (arguments.length != 3
				|| (!arguments[0].equals("generate")
				&& !arguments[0].equals("verify"))) {
			throw new IllegalArgumentException(
					"usage: DdaProductionExpectedResultsCli "
							+ "<generate|verify> <bundle> <expected-results>"
			);
		}
		Path bundle = Path.of(arguments[1]).toAbsolutePath().normalize();
		Path expected = Path.of(arguments[2]).toAbsolutePath().normalize();
		if (arguments[0].equals("generate")) {
			DdaProductionSnapshotBundle.Bundle input =
					DdaProductionSnapshotBundle.read(bundle);
			if (!input.snapshot().complete()) {
				throw new IOException(
						"production snapshot is incomplete"
				);
			}
			DdaProductionExpectedResults.writeGenerated(bundle, expected);
		}
		DdaProductionExpectedResults.VerificationSummary summary =
				DdaProductionExpectedResults.verifyStreaming(
						bundle,
						expected
				);
		System.out.printf(
				"{\"status\":\"valid\",\"schema\":%d,"
						+ "\"bundle_sha256\":\"%s\","
						+ "\"snapshot_sha256\":\"%s\","
						+ "\"rays\":%d,\"segments\":%d,"
						+ "\"expected_results\":\"%s\"}%n",
				DdaProductionExpectedResults.SCHEMA_VERSION,
				summary.bundleSha256(),
				summary.snapshotSha256(),
				summary.rays(),
				summary.segments(),
				json(expected.toString())
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
