package com.tenicana.dronecraft.acoustics;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Initial [H] material hypotheses for real-time tuning. They are deliberately
 * centralized and must be replaced or calibrated against measurements.
 */
public final class AcousticMaterials {
	public static final int DIAGNOSTIC_SCHEMA_VERSION = 1;
	private static final byte[] DIAGNOSTIC_MAGIC =
			"MCFMAT01".getBytes(StandardCharsets.US_ASCII);
	public static final AcousticMaterial AIR = AcousticMaterial.AIR;
	public static final AcousticMaterial FOLIAGE = material(
			"foliage", 0.3, 1.2, 3.0, 0.10, 0.35, 0.65, 0.75
	);
	public static final AcousticMaterial WOOD = material(
			"wood", 4.0, 9.0, 15.0, 0.12, 0.22, 0.35, 0.45
	);
	public static final AcousticMaterial GLASS = material(
			"glass", 3.0, 8.0, 14.0, 0.05, 0.08, 0.12, 0.08
	);
	public static final AcousticMaterial STONE = material(
			"stone", 12.0, 24.0, 36.0, 0.03, 0.05, 0.08, 0.18
	);
	public static final AcousticMaterial METAL = material(
			"metal", 8.0, 20.0, 35.0, 0.02, 0.04, 0.06, 0.12
	);
	public static final AcousticMaterial WATER = material(
			"water", 6.0, 18.0, 30.0, 0.08, 0.20, 0.42, 0.05
	);
	public static final AcousticMaterial SOFT = material(
			"soft", 2.0, 7.0, 13.0, 0.25, 0.55, 0.78, 0.65
	);

	private AcousticMaterials() {
	}

	/**
	 * Canonical order used by snapshots, parity corpora, and native backends.
	 * Adding or reordering an entry deliberately changes the diagnostic hash.
	 */
	public static List<AcousticMaterial> diagnosticTable() {
		return DiagnosticTable.MATERIALS;
	}

	/**
	 * SHA-256 of a language-neutral, big-endian serialization of every
	 * coefficient in {@link #diagnosticTable()}. The value is computed lazily
	 * so normal material sampling does no hashing or allocation.
	 */
	public static String diagnosticSha256() {
		return DiagnosticTable.SHA256;
	}

	public static int diagnosticMaterialId(AcousticMaterial material) {
		Objects.requireNonNull(material, "material");
		for (int index = 0; index < diagnosticTable().size(); index++) {
			if (diagnosticTable().get(index).equals(material)) {
				return index;
			}
		}
		throw new IllegalArgumentException(
				"material is absent from diagnostic table: " + material.id()
		);
	}

	private static AcousticMaterial material(
			String id,
			double lowTransmissionLoss,
			double midTransmissionLoss,
			double highTransmissionLoss,
			double lowAbsorption,
			double midAbsorption,
			double highAbsorption,
			double scattering
	) {
		return new AcousticMaterial(
				id,
				new AcousticBands(lowTransmissionLoss, midTransmissionLoss, highTransmissionLoss),
				new AcousticBands(lowAbsorption, midAbsorption, highAbsorption),
				scattering
		);
	}

	private static final class DiagnosticTable {
		private static final List<AcousticMaterial> MATERIALS = List.of(
				AIR,
				FOLIAGE,
				WOOD,
				GLASS,
				STONE,
				METAL,
				WATER,
				SOFT
		);
		private static final String SHA256 = computeSha256();

		private DiagnosticTable() {
		}

		private static String computeSha256() {
			try {
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				try (DataOutputStream output = new DataOutputStream(bytes)) {
					output.write(DIAGNOSTIC_MAGIC);
					output.writeInt(DIAGNOSTIC_SCHEMA_VERSION);
					output.writeInt(MATERIALS.size());
					for (AcousticMaterial material : MATERIALS) {
						writeMaterial(output, material);
					}
				}
				return HexFormat.of().formatHex(
						MessageDigest.getInstance("SHA-256")
								.digest(bytes.toByteArray())
				);
			} catch (IOException error) {
				throw new IllegalStateException(
						"unexpected in-memory material table I/O failure",
						error
				);
			} catch (NoSuchAlgorithmException error) {
				throw new IllegalStateException("SHA-256 is unavailable", error);
			}
		}

		private static void writeMaterial(
				DataOutputStream output,
				AcousticMaterial material
		) throws IOException {
			byte[] id = material.id().getBytes(StandardCharsets.UTF_8);
			output.writeInt(id.length);
			output.write(id);
			writeBands(output, material.transmissionLossDbPerMeter());
			writeBands(output, material.surfaceAbsorption());
			output.writeDouble(material.scattering());
		}

		private static void writeBands(
				DataOutputStream output,
				AcousticBands bands
		) throws IOException {
			output.writeDouble(bands.low());
			output.writeDouble(bands.mid());
			output.writeDouble(bands.high());
		}
	}
}
