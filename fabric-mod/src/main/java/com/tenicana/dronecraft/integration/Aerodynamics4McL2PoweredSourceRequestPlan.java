package com.tenicana.dronecraft.integration;

import java.util.List;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.Vec3;

public final class Aerodynamics4McL2PoweredSourceRequestPlan {
	public static final String SOURCE_ID = "A4MC-L2-Powered-Source-Request-Plan-Packet";
	public static final String CAVEAT =
			"Request plan packages hover and cruise source-map targets with static L2 request geometry; keep plan-only until A4MC exposes a powered body-force or porous-source request API.";
	public static final int SOURCE_REFERENCE_COUNT = 6;
	public static final int REQUEST_SAMPLE_COUNT = 8;
	public static final int REQUEST_METRIC_COUNT = 36;
	public static final int SUMMARY_METRIC_ROW_COUNT = 10;
	public static final int METHOD_METRIC_ROW_COUNT = 1;
	public static final int PACKET_METRIC_ROW_COUNT = SOURCE_REFERENCE_COUNT
			+ REQUEST_SAMPLE_COUNT * REQUEST_METRIC_COUNT
			+ SUMMARY_METRIC_ROW_COUNT
			+ METHOD_METRIC_ROW_COUNT;

	private Aerodynamics4McL2PoweredSourceRequestPlan() {
	}

	public record PoweredSourceRequest(
			String presetName,
			String spinState,
			String sourceMapId,
			String validationPacketId,
			String acceptanceGatePacketId,
			int rotorCount,
			int sourceTermCount,
			int nx,
			int ny,
			int nz,
			int gridCellCount,
			double cellSizeMeters,
			int steps,
			double inletVxMetersPerSecond,
			double inletVyMetersPerSecond,
			double inletVzMetersPerSecond,
			double inletSpeedMetersPerSecond,
			double spinRatio,
			double totalThrustNewtons,
			double thrustToWeight,
			double totalOpenAreaSquareMeters,
			double meanPressureJumpPascals,
			double maxPressureJumpPascals,
			double netForceXNewtons,
			double netForceYNewtons,
			double netForceZNewtons,
			double netForceMagnitudeNewtons,
			double netMomentXNewtonMeters,
			double netMomentYNewtonMeters,
			double netMomentZNewtonMeters,
			double netMomentMagnitudeNewtonMeters,
			double centerOfThrustOffsetMeters,
			boolean baselineForceMomentRequest,
			boolean poweredSourceApiRequired,
			boolean poweredSourceApiAvailable,
			boolean requestBuildAllowed,
			String runtimeInfo
	) {
	}

	public record PoweredSourceRequestExtrema(
			int requestCount,
			int hoverRequestCount,
			int cruiseRequestCount,
			int sourceTermCount,
			int maxGridCellCount,
			double maxMeanPressureJumpPascals,
			double maxNetForceMagnitudeNewtons,
			double maxCenterOfThrustOffsetMeters,
			int poweredSourceApiAvailableCount,
			int requestBuildAllowedCount
	) {
	}

	public record PoweredSourceRequestAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceReferenceCount,
			int requestSampleCount,
			int requestMetricCount,
			int summaryMetricRowCount,
			int methodMetricRowCount,
			List<PoweredSourceRequest> requests,
			PoweredSourceRequestExtrema extrema
	) {
		public PoweredSourceRequestAudit {
			requests = List.copyOf(requests);
		}
	}

	public static PoweredSourceRequestAudit audit() {
		List<PoweredSourceRequest> requests = List.of(
				request("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72, "hover"),
				request("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64, "hover"),
				request("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48, "hover"),
				request("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80, "hover"),
				request("racingQuad", DroneConfig.racingQuad(), new Vec3(0.0, 0.0, -18.0), 72, "cruise"),
				request("apDrone", DroneConfig.apDrone(), new Vec3(0.0, 0.0, -14.0), 64, "cruise"),
				request("cinewhoop", DroneConfig.cinewhoop(), new Vec3(0.0, 0.0, -8.0), 48, "cruise"),
				request("heavyLift", DroneConfig.heavyLift(), new Vec3(0.0, 0.0, -12.0), 80, "cruise")
		);
		return new PoweredSourceRequestAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_METRIC_ROW_COUNT,
				SOURCE_REFERENCE_COUNT,
				REQUEST_SAMPLE_COUNT,
				REQUEST_METRIC_COUNT,
				SUMMARY_METRIC_ROW_COUNT,
				METHOD_METRIC_ROW_COUNT,
				requests,
				extrema(requests)
		);
	}

	public static PoweredSourceRequest request(
			String presetName,
			DroneConfig config,
			Vec3 inletVelocity,
			int steps,
			String spinState
	) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		if (!"hover".equals(spinState) && !"cruise".equals(spinState)) {
			throw new IllegalArgumentException("spinState must be hover or cruise.");
		}
		Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe =
				Aerodynamics4McL2DroneProbe.forceMomentProbe(config, inletVelocity, steps);
		Aerodynamics4McL2Bridge.L2RequestSpec request = probe.requestSpec();
		if ("hover".equals(spinState)) {
			return fromHoverSourceMap(
					Aerodynamics4McL2PoweredHoverSourceMap.sourceMap(presetName, config, inletVelocity, steps),
					probe,
					request
			);
		}
		return fromCruiseSourceMap(
				Aerodynamics4McL2PoweredCruiseSourceMap.sourceMap(presetName, config, inletVelocity, steps),
				probe,
				request
		);
	}

	private static PoweredSourceRequest fromHoverSourceMap(
			Aerodynamics4McL2PoweredHoverSourceMap.PoweredHoverSourceMap sourceMap,
			Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe,
			Aerodynamics4McL2Bridge.L2RequestSpec request
	) {
		return new PoweredSourceRequest(
				sourceMap.presetName(),
				sourceMap.spinState(),
				Aerodynamics4McL2PoweredHoverSourceMap.SOURCE_ID,
				Aerodynamics4McL2PoweredHoverValidation.SOURCE_ID,
				Aerodynamics4McL2PoweredHoverAcceptanceGate.SOURCE_ID,
				sourceMap.rotorCount(),
				sourceMap.sourceTermCount(),
				probe.nx(),
				probe.ny(),
				probe.nz(),
				probe.nx() * probe.ny() * probe.nz(),
				probe.cellSizeMeters(),
				request.steps(),
				request.inletVx(),
				request.inletVy(),
				request.inletVz(),
				sourceMap.inletSpeedMetersPerSecond(),
				sourceMap.spinRatio(),
				sourceMap.totalThrustNewtons(),
				sourceMap.thrustToWeight(),
				sourceMap.totalOpenAreaSquareMeters(),
				sourceMap.meanPressureJumpPascals(),
				sourceMap.maxPressureJumpPascals(),
				sourceMap.netForceXNewtons(),
				sourceMap.netForceYNewtons(),
				sourceMap.netForceZNewtons(),
				sourceMap.netForceMagnitudeNewtons(),
				sourceMap.netMomentXNewtonMeters(),
				sourceMap.netMomentYNewtonMeters(),
				sourceMap.netMomentZNewtonMeters(),
				sourceMap.netMomentMagnitudeNewtonMeters(),
				sourceMap.centerOfThrustOffsetMeters(),
				request.computeForceMoment(),
				sourceMap.poweredSourceApiRequired(),
				sourceMap.poweredSourceApiAvailable(),
				false,
				"plan-only-powered-source-api-unavailable"
		);
	}

	private static PoweredSourceRequest fromCruiseSourceMap(
			Aerodynamics4McL2PoweredCruiseSourceMap.PoweredCruiseSourceMap sourceMap,
			Aerodynamics4McL2DroneProbe.DroneWindTunnelProbe probe,
			Aerodynamics4McL2Bridge.L2RequestSpec request
	) {
		return new PoweredSourceRequest(
				sourceMap.presetName(),
				sourceMap.spinState(),
				Aerodynamics4McL2PoweredCruiseSourceMap.SOURCE_ID,
				Aerodynamics4McL2PoweredCruiseValidation.SOURCE_ID,
				Aerodynamics4McL2PoweredCruiseAcceptanceGate.SOURCE_ID,
				sourceMap.rotorCount(),
				sourceMap.sourceTermCount(),
				probe.nx(),
				probe.ny(),
				probe.nz(),
				probe.nx() * probe.ny() * probe.nz(),
				probe.cellSizeMeters(),
				request.steps(),
				request.inletVx(),
				request.inletVy(),
				request.inletVz(),
				sourceMap.inletSpeedMetersPerSecond(),
				sourceMap.spinRatio(),
				sourceMap.totalThrustNewtons(),
				sourceMap.thrustToWeight(),
				sourceMap.totalOpenAreaSquareMeters(),
				sourceMap.meanPressureJumpPascals(),
				sourceMap.maxPressureJumpPascals(),
				sourceMap.netForceXNewtons(),
				sourceMap.netForceYNewtons(),
				sourceMap.netForceZNewtons(),
				sourceMap.netForceMagnitudeNewtons(),
				sourceMap.netMomentXNewtonMeters(),
				sourceMap.netMomentYNewtonMeters(),
				sourceMap.netMomentZNewtonMeters(),
				sourceMap.netMomentMagnitudeNewtonMeters(),
				sourceMap.centerOfThrustOffsetMeters(),
				request.computeForceMoment(),
				sourceMap.poweredSourceApiRequired(),
				sourceMap.poweredSourceApiAvailable(),
				false,
				"plan-only-powered-source-api-unavailable"
		);
	}

	private static PoweredSourceRequestExtrema extrema(List<PoweredSourceRequest> requests) {
		int hover = 0;
		int cruise = 0;
		int sourceTerms = 0;
		int maxGridCells = 0;
		double maxMeanPressureJump = 0.0;
		double maxNetForce = 0.0;
		double maxCenterOffset = 0.0;
		int poweredSourceApiAvailable = 0;
		int buildAllowed = 0;
		for (PoweredSourceRequest request : requests) {
			if ("hover".equals(request.spinState())) {
				hover++;
			}
			if ("cruise".equals(request.spinState())) {
				cruise++;
			}
			sourceTerms += request.sourceTermCount();
			maxGridCells = Math.max(maxGridCells, request.gridCellCount());
			maxMeanPressureJump = Math.max(maxMeanPressureJump, request.meanPressureJumpPascals());
			maxNetForce = Math.max(maxNetForce, request.netForceMagnitudeNewtons());
			maxCenterOffset = Math.max(maxCenterOffset, request.centerOfThrustOffsetMeters());
			if (request.poweredSourceApiAvailable()) {
				poweredSourceApiAvailable++;
			}
			if (request.requestBuildAllowed()) {
				buildAllowed++;
			}
		}
		return new PoweredSourceRequestExtrema(
				requests.size(),
				hover,
				cruise,
				sourceTerms,
				maxGridCells,
				maxMeanPressureJump,
				maxNetForce,
				maxCenterOffset,
				poweredSourceApiAvailable,
				buildAllowed
		);
	}
}
