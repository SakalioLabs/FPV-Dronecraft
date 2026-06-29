package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveRotorSpecConfigDeltaReport {
	public static final String SOURCE_ID = "User-Propeller-Archive-RotorSpec-Config-Delta-Packet";
	public static final String CAVEAT =
			"RotorSpec config delta report compares current DroneConfig rotor parameters to accepted propeller-archive RotorSpec candidates; it is review material only, never patches DroneConfig, and only opens playable reference rows when the bridge full-fit gate is ready.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int DELTA_SAMPLE_COUNT = PropellerArchiveRotorSpecFitBridge.BRIDGE_SAMPLE_COUNT;
	public static final int DELTA_METRIC_ROW_COUNT = 28;
	public static final int SUMMARY_ROW_COUNT = 12;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ DELTA_SAMPLE_COUNT * DELTA_METRIC_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private PropellerArchiveRotorSpecConfigDeltaReport() {
	}

	public record RotorSpecConfigDeltaRow(
			String presetName,
			int currentRotorCount,
			boolean bridgeFullRotorSpecFitReady,
			double currentRadiusMeters,
			double candidateRadiusMeters,
			double radiusRatioCandidateOverCurrent,
			double currentBladePitchMeters,
			double candidateBladePitchMeters,
			double bladePitchRatioCandidateOverCurrent,
			int currentBladeCount,
			int candidateBladeCount,
			int bladeCountDelta,
			double currentThrustCoefficient,
			double candidateThrustCoefficient,
			double thrustCoefficientRatioCandidateOverCurrent,
			double currentYawTorquePerThrustMeters,
			double candidateYawTorquePerThrustMeters,
			double yawTorqueRatioCandidateOverCurrent,
			double currentChordToRadius,
			double candidateChordToRadius,
			double chordRatioCandidateOverCurrent,
			double currentBetaRadians,
			double candidateBetaRadians,
			double betaDeltaRadians,
			boolean configPatchAllowed,
			boolean playableReferenceAllowed,
			String status,
			String message
	) {
	}

	public record RotorSpecConfigDeltaExtrema(
			int rowCount,
			int bridgeFullRotorSpecFitReadyCount,
			int configPatchAllowedCount,
			int playableReferenceAllowedCount,
			double maxRadiusRatioError,
			double maxBladePitchRatioError,
			int maxBladeCountDeltaAbs,
			double maxThrustCoefficientRatioError,
			double maxYawTorqueRatioError,
			double maxChordRatioError,
			double maxBetaDeltaRadians,
			int maxRotorCount
	) {
	}

	public record RotorSpecConfigDeltaAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int deltaSampleCount,
			int deltaMetricRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<RotorSpecConfigDeltaRow> rows,
			RotorSpecConfigDeltaExtrema extrema
	) {
		public RotorSpecConfigDeltaAudit {
			rows = List.copyOf(rows);
		}
	}

	public static RotorSpecConfigDeltaAudit audit() {
		return audit(PropellerArchiveRotorSpecFitBridge.audit());
	}

	public static RotorSpecConfigDeltaAudit audit(
			PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeAudit bridgeAudit
	) {
		if (bridgeAudit == null) {
			throw new IllegalArgumentException("bridgeAudit must not be null.");
		}
		List<RotorSpecConfigDeltaRow> rows = bridgeAudit.rows()
				.stream()
				.map(PropellerArchiveRotorSpecConfigDeltaReport::row)
				.toList();
		return new RotorSpecConfigDeltaAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				DELTA_SAMPLE_COUNT,
				DELTA_METRIC_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				rows,
				extrema(rows)
		);
	}

	public static RotorSpecConfigDeltaRow row(String presetName) {
		return audit().rows().stream()
				.filter(row -> row.presetName().equals(presetName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("unknown RotorSpec config delta row: " + presetName));
	}

	public static RotorSpecConfigDeltaRow row(
			PropellerArchiveRotorSpecFitBridge.RotorSpecBridgeRow bridgeRow
	) {
		if (bridgeRow == null) {
			throw new IllegalArgumentException("bridge row must not be null.");
		}
		DroneConfig config = configFor(bridgeRow.presetName());
		RotorSpec representativeRotor = config.rotors().get(0);
		boolean configPatchAllowed = false;
		boolean playableReferenceAllowed = bridgeRow.fullRotorSpecFitReady();
		return new RotorSpecConfigDeltaRow(
				bridgeRow.presetName(),
				config.rotors().size(),
				bridgeRow.fullRotorSpecFitReady(),
				representativeRotor.radiusMeters(),
				bridgeRow.targetRadiusMeters(),
				ratio(bridgeRow.targetRadiusMeters(), representativeRotor.radiusMeters()),
				representativeRotor.bladePitchMeters(),
				bridgeRow.targetPitchMeters(),
				ratio(bridgeRow.targetPitchMeters(), representativeRotor.bladePitchMeters()),
				representativeRotor.bladeCount(),
				bridgeRow.bladeCountCandidate(),
				bridgeRow.bladeCountCandidate() - representativeRotor.bladeCount(),
				representativeRotor.thrustCoefficient(),
				bridgeRow.thrustCoefficientCandidate(),
				ratio(bridgeRow.thrustCoefficientCandidate(), representativeRotor.thrustCoefficient()),
				representativeRotor.yawTorquePerThrustMeter(),
				bridgeRow.yawTorquePerThrustCandidateMeters(),
				ratio(bridgeRow.yawTorquePerThrustCandidateMeters(), representativeRotor.yawTorquePerThrustMeter()),
				representativeRotor.representativeBladeChordToRadiusRatio(),
				bridgeRow.chordToRadiusCandidate(),
				ratio(bridgeRow.chordToRadiusCandidate(), representativeRotor.representativeBladeChordToRadiusRatio()),
				representativeRotor.geometricBladePitchAngleRadians(),
				bridgeRow.betaRadiansCandidate(),
				bridgeRow.betaRadiansCandidate() - representativeRotor.geometricBladePitchAngleRadians(),
				configPatchAllowed,
				playableReferenceAllowed,
				playableReferenceAllowed ? "REVIEW_READY" : "BLOCKED",
				playableReferenceAllowed ? "rotor-spec-config-delta-ready-for-review" : bridgeRow.message()
		);
	}

	public static DroneConfig configFor(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unknown DroneConfig preset: " + presetName);
		};
	}

	private static RotorSpecConfigDeltaExtrema extrema(List<RotorSpecConfigDeltaRow> rows) {
		int fullReady = 0;
		int patchAllowed = 0;
		int playableAllowed = 0;
		double maxRadius = 0.0;
		double maxPitch = 0.0;
		int maxBladeDelta = 0;
		double maxThrust = 0.0;
		double maxYaw = 0.0;
		double maxChord = 0.0;
		double maxBeta = 0.0;
		int maxRotors = 0;
		for (RotorSpecConfigDeltaRow row : rows) {
			if (row.bridgeFullRotorSpecFitReady()) {
				fullReady++;
			}
			if (row.configPatchAllowed()) {
				patchAllowed++;
			}
			if (row.playableReferenceAllowed()) {
				playableAllowed++;
			}
			maxRadius = Math.max(maxRadius, ratioError(row.radiusRatioCandidateOverCurrent()));
			maxPitch = Math.max(maxPitch, ratioError(row.bladePitchRatioCandidateOverCurrent()));
			maxBladeDelta = Math.max(maxBladeDelta, Math.abs(row.bladeCountDelta()));
			maxThrust = Math.max(maxThrust, ratioError(row.thrustCoefficientRatioCandidateOverCurrent()));
			maxYaw = Math.max(maxYaw, ratioError(row.yawTorqueRatioCandidateOverCurrent()));
			maxChord = Math.max(maxChord, ratioError(row.chordRatioCandidateOverCurrent()));
			maxBeta = Math.max(maxBeta, Math.abs(row.betaDeltaRadians()));
			maxRotors = Math.max(maxRotors, row.currentRotorCount());
		}
		return new RotorSpecConfigDeltaExtrema(
				rows.size(),
				fullReady,
				patchAllowed,
				playableAllowed,
				maxRadius,
				maxPitch,
				maxBladeDelta,
				maxThrust,
				maxYaw,
				maxChord,
				maxBeta,
				maxRotors
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || denominator <= 0.0) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static double ratioError(double ratio) {
		if (!Double.isFinite(ratio)) {
			return 1.0;
		}
		return Math.abs(ratio - 1.0);
	}
}
