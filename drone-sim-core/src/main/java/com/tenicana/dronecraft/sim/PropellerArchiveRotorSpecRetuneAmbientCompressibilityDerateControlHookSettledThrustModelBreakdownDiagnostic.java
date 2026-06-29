package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public final class PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-RotorSpec-Retune-Ambient-Compressibility-Derate-Control-Hook-Settled-Thrust-Model-Breakdown-Diagnostic-Packet";
	public static final String CAVEAT =
			"RotorSpec retune ambient compressibility derate control-hook settled thrust-model breakdown diagnostic compares APDrone peak thrust ratio against omega-squared, advance, inflow, and compressibility proxies; rows remain lab evidence only and do not enable runtime coupling, playable export, or gameplay auto-apply.";
	public static final String CONTROL_BOUNDARY =
			"DronePhysics.targetOmega=maxOmega*targetMaxRpmScale-before-motor-response";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 6;
	public static final int SPEED_SAMPLE_COUNT = 5;
	public static final int BREAKDOWN_ROW_COUNT = SPEED_SAMPLE_COUNT;
	public static final int SUMMARY_ROW_COUNT = 24;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ BREAKDOWN_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;

	private static final double EPSILON = 1.0e-9;
	private static final double REVIEW_COLD_AIR_TEMPERATURE_CELSIUS = -10.0;
	private static final double REVIEW_DT_SECONDS = 0.005;
	private static final int REVIEW_SAMPLE_COUNT = 360;
	private static final double REVIEW_THROTTLE_COMMAND = 1.0;
	private static final List<Double> FORWARD_SPEED_SAMPLES_METERS_PER_SECOND =
			List.of(0.0, 8.0, 16.0, 22.0, 28.0);
	private static final PidGains ZERO_GAINS = new PidGains(0.0, 0.0, 0.0, 0.0);

	private PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlHookSettledThrustModelBreakdownDiagnostic() {
	}

	public record SettledThrustModelBreakdownRow(
			String scenarioName,
			String presetName,
			String ambientCaseName,
			double forwardSpeedMetersPerSecond,
			int peakSampleIndex,
			double peakTimeSeconds,
			double targetMaxRpmScale,
			double contractThrustRatio,
			double observedThrustRatio,
			double thrustLossOverContractRatio,
			double omegaSquaredProxyRatio,
			double advanceProxyRatio,
			double inflowProxyRatio,
			double compressibilityProxyRatio,
			double residualAfterCompressibilityRatio,
			double proxyDeficitVsContractRatio,
			double residualThrustDeficitRatio,
			double neutralAveragePropellerThrustScale,
			double deratedAveragePropellerThrustScale,
			double neutralAverageInflowLagThrustScale,
			double deratedAverageInflowLagThrustScale,
			double neutralAverageCompressibilityThrustScale,
			double deratedAverageCompressibilityThrustScale,
			double peakRotorAdvanceRatio,
			double peakRotorTipMach,
			boolean omegaSquaredProxyAboveContract,
			boolean compressibilityProxyAboveObserved,
			boolean runtimeCouplingAllowed,
			boolean playableReferenceAllowed,
			boolean gameplayAutoApplyAllowed,
			String dominantDeficitBucket,
			String status,
			String message
	) {
	}

	public record SettledThrustModelBreakdownSummary(
			int rowCount,
			int failedRowCount,
			int omegaSquaredProxyAboveContractRowCount,
			int compressibilityProxyAboveObservedRowCount,
			int explicitProxyDominantRowCount,
			int residualDominantRowCount,
			double minObservedThrustRatio,
			double minOmegaSquaredProxyRatio,
			double maxOmegaSquaredProxyRatio,
			double minAdvanceProxyRatio,
			double minInflowProxyRatio,
			double minCompressibilityProxyRatio,
			double minResidualAfterCompressibilityRatio,
			double maxProxyDeficitVsContractRatio,
			double maxResidualThrustDeficitRatio,
			double blackboxSpeedObservedThrustRatio,
			double blackboxSpeedCompressibilityProxyRatio,
			double blackboxSpeedResidualThrustDeficitRatio,
			double maxNeutralToDeratedPropellerScaleDrop,
			double maxRotorAdvanceRatio,
			double maxRotorTipMach,
			String dominantDeficitBucket,
			String nextRequiredAction,
			boolean settledThrustModelBreakdownPassed
	) {
	}

	public record SettledThrustModelBreakdownAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int speedSampleCount,
			int breakdownRowCount,
			int summaryRowCount,
			int methodRowCount,
			String controlBoundary,
			List<SettledThrustModelBreakdownRow> rows,
			SettledThrustModelBreakdownSummary summary
	) {
		public SettledThrustModelBreakdownAudit {
			rows = List.copyOf(rows);
		}
	}

	public static SettledThrustModelBreakdownAudit audit() {
		PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
				.DerateControlContractRow contract =
						PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract.row(
								"synthetic_derate_validation_all_pass",
								"apDrone",
								"cold_sea_level_minus10c");
		List<SettledThrustModelBreakdownRow> rows = new ArrayList<>();
		for (double forwardSpeedMetersPerSecond : FORWARD_SPEED_SAMPLES_METERS_PER_SECOND) {
			rows.add(row(contract, forwardSpeedMetersPerSecond));
		}
		return new SettledThrustModelBreakdownAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				SPEED_SAMPLE_COUNT,
				BREAKDOWN_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				CONTROL_BOUNDARY,
				rows,
				summary(rows)
		);
	}

	public static SettledThrustModelBreakdownRow row(double forwardSpeedMetersPerSecond) {
		return audit().rows().stream()
				.filter(row -> Double.compare(row.forwardSpeedMetersPerSecond(), forwardSpeedMetersPerSecond) == 0)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown RotorSpec retune ambient derate APDrone settled thrust model row: "
								+ forwardSpeedMetersPerSecond));
	}

	private static SettledThrustModelBreakdownRow row(
			PropellerArchiveRotorSpecRetuneAmbientCompressibilityDerateControlContract
					.DerateControlContractRow contract,
			double forwardSpeedMetersPerSecond
	) {
		BreakdownMetrics metrics = simulate(contract.targetMaxRpmScale(),
				contract.equivalentMaxThrustLossPercent(), forwardSpeedMetersPerSecond);
		double contractThrustRatio = 1.0 - contract.equivalentMaxThrustLossPercent() / 100.0;
		double proxyDeficit = Math.max(0.0, contractThrustRatio - metrics.compressibilityProxyRatio());
		double residualDeficit = Math.max(0.0, metrics.compressibilityProxyRatio() - metrics.observedThrustRatio());
		String bucket = proxyDeficit >= residualDeficit
				? "explicit-omega-advance-inflow-compressibility-proxy"
				: "residual-unmodeled-thrust-scale";
		return new SettledThrustModelBreakdownRow(
				contract.scenarioName(),
				contract.presetName(),
				contract.ambientCaseName(),
				forwardSpeedMetersPerSecond,
				metrics.peakSampleIndex(),
				(metrics.peakSampleIndex() + 1) * REVIEW_DT_SECONDS,
				contract.targetMaxRpmScale(),
				contractThrustRatio,
				metrics.observedThrustRatio(),
				metrics.thrustLossOverContractRatio(),
				metrics.omegaSquaredProxyRatio(),
				metrics.advanceProxyRatio(),
				metrics.inflowProxyRatio(),
				metrics.compressibilityProxyRatio(),
				metrics.residualAfterCompressibilityRatio(),
				proxyDeficit,
				residualDeficit,
				metrics.neutralAveragePropellerThrustScale(),
				metrics.deratedAveragePropellerThrustScale(),
				metrics.neutralAverageInflowLagThrustScale(),
				metrics.deratedAverageInflowLagThrustScale(),
				metrics.neutralAverageCompressibilityThrustScale(),
				metrics.deratedAverageCompressibilityThrustScale(),
				metrics.peakRotorAdvanceRatio(),
				metrics.peakRotorTipMach(),
				metrics.omegaSquaredProxyRatio() > contractThrustRatio,
				metrics.compressibilityProxyRatio() > metrics.observedThrustRatio(),
				false,
				false,
				false,
				bucket,
				"FAIL",
				"settled-thrust-model-breakdown-keeps-apDrone-forward-punchout-blocked"
		);
	}

	private static SettledThrustModelBreakdownSummary summary(
			List<SettledThrustModelBreakdownRow> rows
	) {
		int omegaAbove = 0;
		int compAboveObserved = 0;
		int explicitDominant = 0;
		int failedRows = 0;
		int residualDominant = 0;
		double minObserved = Double.POSITIVE_INFINITY;
		double minOmega = Double.POSITIVE_INFINITY;
		double maxOmega = 0.0;
		double minAdvance = Double.POSITIVE_INFINITY;
		double minInflow = Double.POSITIVE_INFINITY;
		double minCompressibility = Double.POSITIVE_INFINITY;
		double minResidual = Double.POSITIVE_INFINITY;
		double maxProxyDeficit = 0.0;
		double maxResidualDeficit = 0.0;
		double blackboxObserved = 0.0;
		double blackboxCompressibility = 0.0;
		double blackboxResidualDeficit = 0.0;
		double maxPropScaleDrop = 0.0;
		double maxAdvanceRatio = 0.0;
		double maxTipMach = 0.0;
		for (SettledThrustModelBreakdownRow row : rows) {
			if (row.thrustLossOverContractRatio() > 0.0) {
				failedRows++;
			}
			if (row.omegaSquaredProxyAboveContract()) {
				omegaAbove++;
			}
			if (row.compressibilityProxyAboveObserved()) {
				compAboveObserved++;
			}
			if ("explicit-omega-advance-inflow-compressibility-proxy".equals(row.dominantDeficitBucket())) {
				explicitDominant++;
			} else {
				residualDominant++;
			}
			if (Double.compare(row.forwardSpeedMetersPerSecond(), 22.0) == 0) {
				blackboxObserved = row.observedThrustRatio();
				blackboxCompressibility = row.compressibilityProxyRatio();
				blackboxResidualDeficit = row.residualThrustDeficitRatio();
			}
			minObserved = Math.min(minObserved, row.observedThrustRatio());
			minOmega = Math.min(minOmega, row.omegaSquaredProxyRatio());
			maxOmega = Math.max(maxOmega, row.omegaSquaredProxyRatio());
			minAdvance = Math.min(minAdvance, row.advanceProxyRatio());
			minInflow = Math.min(minInflow, row.inflowProxyRatio());
			minCompressibility = Math.min(minCompressibility, row.compressibilityProxyRatio());
			minResidual = Math.min(minResidual, row.residualAfterCompressibilityRatio());
			maxProxyDeficit = Math.max(maxProxyDeficit, row.proxyDeficitVsContractRatio());
			maxResidualDeficit = Math.max(maxResidualDeficit, row.residualThrustDeficitRatio());
			maxPropScaleDrop = Math.max(
					maxPropScaleDrop,
					row.neutralAveragePropellerThrustScale() - row.deratedAveragePropellerThrustScale());
			maxAdvanceRatio = Math.max(maxAdvanceRatio, row.peakRotorAdvanceRatio());
			maxTipMach = Math.max(maxTipMach, row.peakRotorTipMach());
		}
		boolean passed = rows.stream().allMatch(row -> row.thrustLossOverContractRatio() <= 0.0);
		return new SettledThrustModelBreakdownSummary(
				rows.size(),
				failedRows,
				omegaAbove,
				compAboveObserved,
				explicitDominant,
				residualDominant,
				Double.isInfinite(minObserved) ? 0.0 : minObserved,
				Double.isInfinite(minOmega) ? 0.0 : minOmega,
				maxOmega,
				Double.isInfinite(minAdvance) ? 0.0 : minAdvance,
				Double.isInfinite(minInflow) ? 0.0 : minInflow,
				Double.isInfinite(minCompressibility) ? 0.0 : minCompressibility,
				Double.isInfinite(minResidual) ? 0.0 : minResidual,
				maxProxyDeficit,
				maxResidualDeficit,
				blackboxObserved,
				blackboxCompressibility,
				blackboxResidualDeficit,
				maxPropScaleDrop,
				maxAdvanceRatio,
				maxTipMach,
				explicitDominant >= residualDominant
						? "explicit-omega-advance-inflow-compressibility-proxy"
						: "residual-unmodeled-thrust-scale",
				passed
						? "feed-settled-thrust-model-breakdown-to-blackbox-acceptance"
						: "investigate-apDrone-forward-advance-propeller-scale-and-residual-thrust-terms",
				passed
		);
	}

	private static BreakdownMetrics simulate(
			double targetMaxRpmScale,
			double equivalentMaxThrustLossPercent,
			double forwardSpeedMetersPerSecond
	) {
		DroneConfig neutralConfig = reviewConfig(DroneConfig.apDrone(), 1.0);
		DroneConfig deratedConfig = reviewConfig(DroneConfig.apDrone(), targetMaxRpmScale);
		DronePhysics neutral = new DronePhysics(neutralConfig);
		DronePhysics derated = new DronePhysics(deratedConfig);
		Vec3 forwardVelocity = new Vec3(0.0, 0.0, forwardSpeedMetersPerSecond);
		neutral.state().setVelocityMetersPerSecond(forwardVelocity);
		derated.state().setVelocityMetersPerSecond(forwardVelocity);
		BreakdownAccumulator metrics = new BreakdownAccumulator();
		DroneEnvironment environment = coldAirEnvironment();
		DroneInput input = new DroneInput(REVIEW_THROTTLE_COMMAND, 0.0, 0.0, 0.0, true, true);
		for (int sample = 0; sample < REVIEW_SAMPLE_COUNT; sample++) {
			neutral.step(input, REVIEW_DT_SECONDS, environment);
			derated.step(input, REVIEW_DT_SECONDS, environment);
			metrics.record(sample, neutralConfig, neutral, derated, equivalentMaxThrustLossPercent);
		}
		return metrics.toMetrics();
	}

	private static DroneConfig reviewConfig(DroneConfig config, double targetMaxRpmScale) {
		return config
				.withRotorTargetMaxRpmScale(targetMaxRpmScale)
				.withControlLink(0.0, 0.0, 0.020)
				.withControlReceiver(0.0, 0.0)
				.withEscCommandSignal(0.0, 0.0)
				.withRateSuper(Vec3.ZERO)
				.withPitchGains(ZERO_GAINS)
				.withYawGains(ZERO_GAINS)
				.withRollGains(ZERO_GAINS)
				.withEscMotorResponse(1.0, 1000.0, 1000.0, 0.0, 1.0, 0.65)
				.withMotorTimeConstantSeconds(0.006)
				.withBattery(
						config.nominalBatteryVoltage(),
						config.nominalBatteryVoltage() - 0.1,
						0.0,
						Math.max(20.0, config.batteryCapacityAmpHours()),
						Math.max(240.0, config.maxBatteryCurrentAmps()));
	}

	private static DroneEnvironment coldAirEnvironment() {
		return new DroneEnvironment(
				Vec3.ZERO,
				DroneEnvironment.standardAtmosphereAirDensityRatio(0.0, REVIEW_COLD_AIR_TEMPERATURE_CELSIUS),
				Double.POSITIVE_INFINITY,
				0.0,
				0.0,
				0.0,
				Double.POSITIVE_INFINITY,
				null,
				null,
				null,
				null,
				0.0,
				null,
				0.0,
				REVIEW_COLD_AIR_TEMPERATURE_CELSIUS);
	}

	private static final class BreakdownAccumulator {
		private int peakSampleIndex;
		private double observedThrustRatio;
		private double thrustLossOverContractRatio = -1.0;
		private double omegaSquaredProxyRatio;
		private double advanceProxyRatio;
		private double inflowProxyRatio;
		private double compressibilityProxyRatio;
		private double residualAfterCompressibilityRatio;
		private double neutralAveragePropellerThrustScale;
		private double deratedAveragePropellerThrustScale;
		private double neutralAverageInflowLagThrustScale;
		private double deratedAverageInflowLagThrustScale;
		private double neutralAverageCompressibilityThrustScale;
		private double deratedAverageCompressibilityThrustScale;
		private double peakRotorAdvanceRatio;
		private double peakRotorTipMach;

		void record(
				int sampleIndex,
				DroneConfig neutralConfig,
				DronePhysics neutral,
				DronePhysics derated,
				double equivalentMaxThrustLossPercent
		) {
			double neutralThrust = 0.0;
			double deratedThrust = 0.0;
			double neutralOmegaSquaredProxy = 0.0;
			double deratedOmegaSquaredProxy = 0.0;
			double neutralAdvanceProxy = 0.0;
			double deratedAdvanceProxy = 0.0;
			double neutralInflowProxy = 0.0;
			double deratedInflowProxy = 0.0;
			double neutralCompressibilityProxy = 0.0;
			double deratedCompressibilityProxy = 0.0;
			double neutralPropellerScale = 0.0;
			double deratedPropellerScale = 0.0;
			double neutralInflowScale = 0.0;
			double deratedInflowScale = 0.0;
			double neutralCompressibilityScale = 0.0;
			double deratedCompressibilityScale = 0.0;
			double advanceRatio = 0.0;
			double tipMach = 0.0;
			for (int rotorIndex = 0; rotorIndex < neutralConfig.rotors().size(); rotorIndex++) {
				RotorSpec rotor = neutralConfig.rotors().get(rotorIndex);
				double neutralOmegaSquared = rotor.thrustCoefficient()
						* neutral.state().motorOmegaRadiansPerSecond(rotorIndex)
						* neutral.state().motorOmegaRadiansPerSecond(rotorIndex);
				double deratedOmegaSquared = rotor.thrustCoefficient()
						* derated.state().motorOmegaRadiansPerSecond(rotorIndex)
						* derated.state().motorOmegaRadiansPerSecond(rotorIndex);
				double neutralPropeller = neutral.state().rotorPropellerThrustScale(rotorIndex);
				double deratedPropeller = derated.state().rotorPropellerThrustScale(rotorIndex);
				double neutralInflow = neutral.state().rotorInducedLagThrustScale(rotorIndex);
				double deratedInflow = derated.state().rotorInducedLagThrustScale(rotorIndex);
				double neutralCompressibility = neutral.state().rotorCompressibilityThrustScale(rotorIndex);
				double deratedCompressibility = derated.state().rotorCompressibilityThrustScale(rotorIndex);
				neutralOmegaSquaredProxy += neutralOmegaSquared;
				deratedOmegaSquaredProxy += deratedOmegaSquared;
				neutralAdvanceProxy += neutralOmegaSquared * neutralPropeller;
				deratedAdvanceProxy += deratedOmegaSquared * deratedPropeller;
				neutralInflowProxy += neutralOmegaSquared * neutralPropeller * neutralInflow;
				deratedInflowProxy += deratedOmegaSquared * deratedPropeller * deratedInflow;
				neutralCompressibilityProxy += neutralOmegaSquared * neutralPropeller * neutralInflow
						* neutralCompressibility;
				deratedCompressibilityProxy += deratedOmegaSquared * deratedPropeller * deratedInflow
						* deratedCompressibility;
				neutralThrust += Math.max(0.0, neutral.state().rotorThrustNewtons(rotorIndex));
				deratedThrust += Math.max(0.0, derated.state().rotorThrustNewtons(rotorIndex));
				neutralPropellerScale += neutralPropeller;
				deratedPropellerScale += deratedPropeller;
				neutralInflowScale += neutralInflow;
				deratedInflowScale += deratedInflow;
				neutralCompressibilityScale += neutralCompressibility;
				deratedCompressibilityScale += deratedCompressibility;
				advanceRatio = Math.max(advanceRatio, derated.state().rotorAdvanceRatio(rotorIndex));
				tipMach = Math.max(tipMach, derated.state().rotorTipMach(rotorIndex));
			}
			double lossPercent = neutralThrust > EPSILON
					? Math.max(0.0, (neutralThrust - deratedThrust) / neutralThrust) * 100.0
					: 0.0;
			double overContractRatio = Math.max(0.0, lossPercent - equivalentMaxThrustLossPercent) / 100.0;
			if (overContractRatio >= thrustLossOverContractRatio) {
				int rotorCount = Math.max(1, neutralConfig.rotors().size());
				peakSampleIndex = sampleIndex;
				thrustLossOverContractRatio = overContractRatio;
				observedThrustRatio = deratedThrust / Math.max(EPSILON, neutralThrust);
				omegaSquaredProxyRatio = deratedOmegaSquaredProxy / Math.max(EPSILON, neutralOmegaSquaredProxy);
				advanceProxyRatio = deratedAdvanceProxy / Math.max(EPSILON, neutralAdvanceProxy);
				inflowProxyRatio = deratedInflowProxy / Math.max(EPSILON, neutralInflowProxy);
				compressibilityProxyRatio = deratedCompressibilityProxy / Math.max(
						EPSILON,
						neutralCompressibilityProxy);
				residualAfterCompressibilityRatio = observedThrustRatio / Math.max(
						EPSILON,
						compressibilityProxyRatio);
				neutralAveragePropellerThrustScale = neutralPropellerScale / rotorCount;
				deratedAveragePropellerThrustScale = deratedPropellerScale / rotorCount;
				neutralAverageInflowLagThrustScale = neutralInflowScale / rotorCount;
				deratedAverageInflowLagThrustScale = deratedInflowScale / rotorCount;
				neutralAverageCompressibilityThrustScale = neutralCompressibilityScale / rotorCount;
				deratedAverageCompressibilityThrustScale = deratedCompressibilityScale / rotorCount;
				peakRotorAdvanceRatio = advanceRatio;
				peakRotorTipMach = tipMach;
			}
		}

		BreakdownMetrics toMetrics() {
			return new BreakdownMetrics(
					peakSampleIndex,
					observedThrustRatio,
					thrustLossOverContractRatio,
					omegaSquaredProxyRatio,
					advanceProxyRatio,
					inflowProxyRatio,
					compressibilityProxyRatio,
					residualAfterCompressibilityRatio,
					neutralAveragePropellerThrustScale,
					deratedAveragePropellerThrustScale,
					neutralAverageInflowLagThrustScale,
					deratedAverageInflowLagThrustScale,
					neutralAverageCompressibilityThrustScale,
					deratedAverageCompressibilityThrustScale,
					peakRotorAdvanceRatio,
					peakRotorTipMach
			);
		}
	}

	private record BreakdownMetrics(
			int peakSampleIndex,
			double observedThrustRatio,
			double thrustLossOverContractRatio,
			double omegaSquaredProxyRatio,
			double advanceProxyRatio,
			double inflowProxyRatio,
			double compressibilityProxyRatio,
			double residualAfterCompressibilityRatio,
			double neutralAveragePropellerThrustScale,
			double deratedAveragePropellerThrustScale,
			double neutralAverageInflowLagThrustScale,
			double deratedAverageInflowLagThrustScale,
			double neutralAverageCompressibilityThrustScale,
			double deratedAverageCompressibilityThrustScale,
			double peakRotorAdvanceRatio,
			double peakRotorTipMach
	) {
	}
}
