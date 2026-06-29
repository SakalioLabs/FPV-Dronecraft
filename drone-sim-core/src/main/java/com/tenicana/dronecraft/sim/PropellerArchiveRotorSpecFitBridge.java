package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecFitBridge {
	public static final String SOURCE_ID = "User-Propeller-Archive-RotorSpec-Fit-Bridge-Packet";
	public static final String CAVEAT =
			"RotorSpec fit bridge maps accepted compact propeller references into RotorSpec candidate units; current candidates are audit-only, no DroneConfig patch is emitted, and runtime coupling/gameplay auto-apply stay closed.";
	public static final double STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int BRIDGE_SAMPLE_COUNT = PropellerArchiveCompactReferenceTable.REFERENCE_SAMPLE_COUNT;
	public static final int BRIDGE_METRIC_ROW_COUNT = 22;
	public static final int SUMMARY_ROW_COUNT = 10;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ BRIDGE_SAMPLE_COUNT * BRIDGE_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecFitBridge() {
	}

	public record RotorSpecBridgeRow(
			String presetName,
			String performanceMatchId,
			String geometryMatchId,
			double targetDiameterMeters,
			double targetRadiusMeters,
			double targetPitchMeters,
			int bladeCountCandidate,
			double staticCtAnchor,
			double staticCpAnchor,
			double thrustCoefficientCandidate,
			double yawTorquePerThrustCandidateMeters,
			double chordToRadiusCandidate,
			double betaRadiansCandidate,
			boolean performanceReferenceAvailable,
			boolean geometryReferenceAvailable,
			boolean fullRotorSpecFitReady,
			boolean rotorSpecPatchAllowed,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message,
			String sourceRuntimeInfo
	) {
	}

	public record RotorSpecBridgeExtrema(
			int rowCount,
			int performanceReferenceAvailableCount,
			int geometryReferenceAvailableCount,
			int fullRotorSpecFitReadyCount,
			int rotorSpecPatchAllowedCount,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			double maxThrustCoefficientCandidate,
			double maxYawTorquePerThrustCandidateMeters,
			double maxBetaRadiansCandidate
	) {
	}

	public record RotorSpecBridgeAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int bridgeSampleCount,
			int bridgeMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<RotorSpecBridgeRow> rows,
			RotorSpecBridgeExtrema extrema
	) {
		public RotorSpecBridgeAudit {
			rows = List.copyOf(rows);
		}
	}

	public static RotorSpecBridgeAudit audit() {
		return audit(PropellerArchiveCompactReferenceTable.audit());
	}

	public static RotorSpecBridgeAudit audit(
			PropellerArchiveCompactReferenceTable.CompactReferenceTableAudit referenceTable
	) {
		if (referenceTable == null) {
			throw new IllegalArgumentException("referenceTable must not be null.");
		}
		List<RotorSpecBridgeRow> rows = referenceTable.rows()
				.stream()
				.map(PropellerArchiveRotorSpecFitBridge::row)
				.toList();
		return new RotorSpecBridgeAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				BRIDGE_SAMPLE_COUNT,
				BRIDGE_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static RotorSpecBridgeRow row(String presetName) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown RotorSpec bridge row: " + presetName));
	}

	public static RotorSpecBridgeRow row(
			PropellerArchiveCompactReferenceTable.CompactReferenceRow reference
	) {
		if (reference == null) {
			throw new IllegalArgumentException("reference row must not be null.");
		}
		double diameterMeters = inchesToMeters(reference.targetDiameterInches());
		double radiusMeters = diameterMeters * 0.5;
		double pitchMeters = inchesToMeters(reference.targetPitchInches());
		double thrustCoefficient = thrustCoefficientCandidate(reference.staticCtAnchorCandidate(), diameterMeters);
		double yawTorquePerThrust = yawTorquePerThrustCandidate(
				reference.staticCtAnchorCandidate(),
				reference.staticCpAnchorCandidate(),
				diameterMeters
		);
		boolean performanceAvailable = reference.referenceMaterialExportAllowed()
				&& reference.ctCpEtaReferenceWeight() > 0.0
				&& reference.staticAnchorReferenceWeight() > 0.0;
		boolean geometryAvailable = reference.referenceRowAvailable()
				&& reference.geometryReferenceWeight() > 0.0
				&& reference.geometryStationAvailable();
		boolean fullFitReady = performanceAvailable && geometryAvailable;
		return new RotorSpecBridgeRow(
				reference.presetName(),
				reference.performanceMatchId(),
				reference.geometryMatchId(),
				diameterMeters,
				radiusMeters,
				pitchMeters,
				reference.targetBladeCount(),
				reference.staticCtAnchorCandidate(),
				reference.staticCpAnchorCandidate(),
				thrustCoefficient,
				yawTorquePerThrust,
				reference.chordToRadius(),
				Math.toRadians(reference.betaDegrees()),
				performanceAvailable,
				geometryAvailable,
				fullFitReady,
				false,
				false,
				false,
				fullFitReady ? "READY" : "BLOCKED",
				messageFor(reference, performanceAvailable, geometryAvailable),
				reference.sourceRuntimeInfo()
		);
	}

	public static double thrustCoefficientCandidate(double staticCtAnchor, double diameterMeters) {
		if (!Double.isFinite(staticCtAnchor) || !Double.isFinite(diameterMeters)
				|| staticCtAnchor <= 0.0 || diameterMeters <= 0.0) {
			return 0.0;
		}
		return staticCtAnchor
				* STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER
				* Math.pow(diameterMeters, 4.0)
				/ (4.0 * Math.PI * Math.PI);
	}

	public static double yawTorquePerThrustCandidate(
			double staticCtAnchor,
			double staticCpAnchor,
			double diameterMeters
	) {
		if (!Double.isFinite(staticCtAnchor) || !Double.isFinite(staticCpAnchor)
				|| !Double.isFinite(diameterMeters) || staticCtAnchor <= 0.0
				|| staticCpAnchor <= 0.0 || diameterMeters <= 0.0) {
			return 0.0;
		}
		return (staticCpAnchor / staticCtAnchor) * diameterMeters / (2.0 * Math.PI);
	}

	private static RotorSpecBridgeExtrema extrema(List<RotorSpecBridgeRow> rows) {
		int performance = 0;
		int geometry = 0;
		int full = 0;
		int patch = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxThrustCoefficient = 0.0;
		double maxYawTorquePerThrust = 0.0;
		double maxBeta = 0.0;
		for (RotorSpecBridgeRow row : rows) {
			if (row.performanceReferenceAvailable()) {
				performance++;
			}
			if (row.geometryReferenceAvailable()) {
				geometry++;
			}
			if (row.fullRotorSpecFitReady()) {
				full++;
			}
			if (row.rotorSpecPatchAllowed()) {
				patch++;
			}
			if (row.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (row.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
			maxThrustCoefficient = Math.max(maxThrustCoefficient, row.thrustCoefficientCandidate());
			maxYawTorquePerThrust = Math.max(maxYawTorquePerThrust, row.yawTorquePerThrustCandidateMeters());
			maxBeta = Math.max(maxBeta, row.betaRadiansCandidate());
		}
		return new RotorSpecBridgeExtrema(
				rows.size(),
				performance,
				geometry,
				full,
				patch,
				runtime,
				gameplay,
				maxThrustCoefficient,
				maxYawTorquePerThrust,
				maxBeta
		);
	}

	private static double inchesToMeters(double inches) {
		if (!Double.isFinite(inches) || inches <= 0.0) {
			return 0.0;
		}
		return inches * 0.0254;
	}

	private static String messageFor(
			PropellerArchiveCompactReferenceTable.CompactReferenceRow reference,
			boolean performanceAvailable,
			boolean geometryAvailable
	) {
		if (!reference.referenceMaterialExportAllowed()) {
			return "reference-table-handoff-blocked";
		}
		if (!performanceAvailable) {
			return "rotor-spec-performance-reference-missing";
		}
		if (!geometryAvailable) {
			return "rotor-spec-geometry-reference-missing";
		}
		return "rotor-spec-fit-candidate-ready";
	}
}
