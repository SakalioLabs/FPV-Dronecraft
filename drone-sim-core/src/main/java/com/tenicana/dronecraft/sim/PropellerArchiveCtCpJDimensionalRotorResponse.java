package com.tenicana.dronecraft.sim;

import java.util.List;

public final class PropellerArchiveCtCpJDimensionalRotorResponse {
	public static final String SOURCE_ID =
			"User-Propeller-Archive-CT-CP-J-Dimensional-Rotor-Response-Packet";
	public static final String CAVEAT =
			"CT/CP/J dimensional rotor response converts accepted lookup execution rows into SI thrust, shaft power, torque, disk loading, and induced-velocity references; handoff-blocked or lookup-blocked rows emit diagnostics only and cannot mutate runtime physics or gameplay tuning.";
	public static final int SOURCE_REFERENCE_ROW_COUNT = 8;
	public static final int DIMENSIONAL_RULE_ROW_COUNT = 7;
	public static final int SCENARIO_ROW_COUNT =
			PropellerArchiveCtCpJLookupExecutionContract.SCENARIO_ROW_COUNT;
	public static final int SUMMARY_ROW_COUNT = 12;
	public static final int METHOD_ROW_COUNT = 1;
	public static final int PACKET_ROW_COUNT = SOURCE_REFERENCE_ROW_COUNT
			+ DIMENSIONAL_RULE_ROW_COUNT
			+ SCENARIO_ROW_COUNT
			+ SUMMARY_ROW_COUNT
			+ METHOD_ROW_COUNT;
	public static final double STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;
	public static final String NEXT_REQUIRED_ACTION =
			"bind-reviewed-dimensional-rotor-response-to-openfoam-and-runtime-fit-review";

	private static final double EPSILON = 1.0e-12;

	private static final List<DimensionalResponseRule> RULES = List.of(
			new DimensionalResponseRule("accepted_lookup_execution_required", true, false, true,
					"SI response is emitted only for lookup rows accepted by the execution contract",
					"feed-accepted-lookup-execution-results"),
			new DimensionalResponseRule("standard_air_density_reference", true, true, true,
					"reference rows use standard sea-level dry-air density unless a reviewed atmosphere row overrides it",
					"keep-density-explicit-in-reference-packet"),
			new DimensionalResponseRule("propeller_coefficient_equations", true, false, true,
					"thrust and power use T=CT*rho*n^2*D^4 and P=CP*rho*n^3*D^5",
					"verify-dimensioned-propeller-coefficient-closure"),
			new DimensionalResponseRule("torque_closure", true, false, true,
					"shaft torque is P divided by rotor angular speed",
					"verify-power-torque-closure-before-motor-fit"),
			new DimensionalResponseRule("momentum_induced_velocity_reference", true, false, true,
					"ideal induced velocity is sqrt(T/(2*rho*A)) for positive accepted thrust",
					"compare-against-openfoam-induced-wake-evidence"),
			new DimensionalResponseRule("runtime_leak_guard", true, true, true,
					"dimensional responses cannot directly change DronePhysics or presets",
					"keep-runtime-coupling-closed"),
			new DimensionalResponseRule("gameplay_auto_apply_guard", true, true, true,
					"playable/dev may consume reviewed references later but no row auto-applies gameplay tuning",
					"keep-gameplay-auto-apply-closed")
	);

	private PropellerArchiveCtCpJDimensionalRotorResponse() {
	}

	public record DimensionalResponseRule(
			String ruleName,
			boolean required,
			boolean currentSatisfied,
			boolean syntheticTargetSatisfied,
			String requirement,
			String nextRequiredAction
	) {
	}

	public record RotorDimensionalResponse(
			String presetName,
			String caseName,
			boolean lookupExecutionAccepted,
			boolean dimensionalResponseReady,
			double queryAdvanceRatioJ,
			double queryRpm,
			double airDensityKgPerCubicMeter,
			double rotorRadiusMeters,
			double propellerDiameterMeters,
			double revolutionsPerSecond,
			double angularVelocityRadiansPerSecond,
			double axialAdvanceSpeedMetersPerSecond,
			double thrustCoefficientCt,
			double powerCoefficientCp,
			double propulsiveEfficiencyEta,
			double thrustNewtons,
			double shaftPowerWatts,
			double shaftTorqueNewtonMeters,
			double diskAreaSquareMeters,
			double diskLoadingNewtonsPerSquareMeter,
			double idealInducedVelocityMetersPerSecond,
			double idealMomentumPowerWatts,
			double idealMomentumPowerOverShaftPower,
			double totalThrustToWeightRatio,
			boolean runtimeCouplingAllowed,
			boolean gameplayAutoApplyAllowed,
			String status,
			String message
	) {
	}

	public record DimensionalResponseScenario(
			String scenarioName,
			RotorDimensionalResponse response
	) {
	}

	public record DimensionalResponseSummary(
			int scenarioCount,
			int readyScenarioCount,
			int blockedScenarioCount,
			double maxThrustNewtons,
			double maxShaftPowerWatts,
			double maxShaftTorqueNewtonMeters,
			double maxDiskLoadingNewtonsPerSquareMeter,
			double maxIdealInducedVelocityMetersPerSecond,
			double maxAxialAdvanceSpeedMetersPerSecond,
			double maxTotalThrustToWeightRatio,
			int runtimeCouplingAllowedCount,
			int gameplayAutoApplyAllowedCount,
			String nextRequiredAction
	) {
	}

	public record CtCpJDimensionalRotorResponseAudit(
			String sourceId,
			String caveat,
			int packetRowCount,
			int sourceReferenceRowCount,
			int dimensionalRuleRowCount,
			int scenarioRowCount,
			int summaryRowCount,
			int methodRowCount,
			List<DimensionalResponseRule> rules,
			List<DimensionalResponseScenario> scenarios,
			DimensionalResponseSummary summary
	) {
		public CtCpJDimensionalRotorResponseAudit {
			rules = List.copyOf(rules);
			scenarios = List.copyOf(scenarios);
		}
	}

	public static CtCpJDimensionalRotorResponseAudit audit() {
		List<DimensionalResponseScenario> scenarios = PropellerArchiveCtCpJLookupExecutionContract.audit()
				.scenarios()
				.stream()
				.map(scenario -> new DimensionalResponseScenario(
						scenario.scenarioName(),
						response(scenario.result())
				))
				.toList();
		return new CtCpJDimensionalRotorResponseAudit(
				SOURCE_ID,
				CAVEAT,
				PACKET_ROW_COUNT,
				SOURCE_REFERENCE_ROW_COUNT,
				DIMENSIONAL_RULE_ROW_COUNT,
				SCENARIO_ROW_COUNT,
				SUMMARY_ROW_COUNT,
				METHOD_ROW_COUNT,
				RULES,
				scenarios,
				summary(scenarios)
		);
	}

	public static DimensionalResponseRule rule(String ruleName) {
		return RULES.stream()
				.filter(rule -> rule.ruleName().equals(ruleName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"unknown CT/CP/J dimensional response rule: " + ruleName));
	}

	public static RotorDimensionalResponse response(
			PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult lookup
	) {
		if (lookup == null) {
			throw new IllegalArgumentException("lookup execution result must not be null.");
		}
		return response(lookup, configFor(lookup.presetName()), STANDARD_AIR_DENSITY_KG_PER_CUBIC_METER);
	}

	public static RotorDimensionalResponse response(
			PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult lookup,
			DroneConfig config,
			double airDensityKgPerCubicMeter
	) {
		if (lookup == null) {
			throw new IllegalArgumentException("lookup execution result must not be null.");
		}
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= 0.0) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		RotorSpec rotor = config.rotors().get(0);
		double diameter = rotor.radiusMeters() * 2.0;
		double diskArea = Math.PI * rotor.radiusMeters() * rotor.radiusMeters();
		double revolutionsPerSecond = lookup.queryRpm() / 60.0;
		double angularVelocity = revolutionsPerSecond * 2.0 * Math.PI;
		if (!lookup.acceptedByLookupGate()) {
			return blocked(lookup, config, rotor, airDensityKgPerCubicMeter, diameter, diskArea,
					revolutionsPerSecond, angularVelocity);
		}
		double advanceSpeed = lookup.queryAdvanceRatioJ() * revolutionsPerSecond * diameter;
		double thrust = lookup.ctCoefficient()
				* airDensityKgPerCubicMeter
				* revolutionsPerSecond
				* revolutionsPerSecond
				* Math.pow(diameter, 4.0);
		double shaftPower = lookup.cpCoefficient()
				* airDensityKgPerCubicMeter
				* Math.pow(revolutionsPerSecond, 3.0)
				* Math.pow(diameter, 5.0);
		double shaftTorque = angularVelocity > EPSILON ? shaftPower / angularVelocity : 0.0;
		double diskLoading = diskArea > EPSILON ? thrust / diskArea : 0.0;
		double inducedVelocity = thrust > EPSILON && diskArea > EPSILON
				? Math.sqrt(thrust / (2.0 * airDensityKgPerCubicMeter * diskArea))
				: 0.0;
		double idealMomentumPower = thrust * inducedVelocity;
		double momentumOverShaft = ratio(idealMomentumPower, shaftPower);
		double thrustToWeight = ratio(thrust * config.rotors().size(),
				config.massKg() * config.gravityMetersPerSecondSquared());
		return new RotorDimensionalResponse(
				lookup.presetName(),
				lookup.caseName(),
				true,
				true,
				lookup.queryAdvanceRatioJ(),
				lookup.queryRpm(),
				airDensityKgPerCubicMeter,
				rotor.radiusMeters(),
				diameter,
				revolutionsPerSecond,
				angularVelocity,
				advanceSpeed,
				lookup.ctCoefficient(),
				lookup.cpCoefficient(),
				lookup.eta(),
				thrust,
				shaftPower,
				shaftTorque,
				diskArea,
				diskLoading,
				inducedVelocity,
				idealMomentumPower,
				momentumOverShaft,
				thrustToWeight,
				false,
				false,
				"DIMENSIONAL_RESPONSE_READY",
				"ct-cp-j-dimensional-response-ready"
		);
	}

	private static RotorDimensionalResponse blocked(
			PropellerArchiveCtCpJLookupExecutionContract.LookupExecutionResult lookup,
			DroneConfig config,
			RotorSpec rotor,
			double airDensityKgPerCubicMeter,
			double diameter,
			double diskArea,
			double revolutionsPerSecond,
			double angularVelocity
	) {
		return new RotorDimensionalResponse(
				lookup.presetName(),
				lookup.caseName(),
				lookup.acceptedByLookupGate(),
				false,
				lookup.queryAdvanceRatioJ(),
				lookup.queryRpm(),
				airDensityKgPerCubicMeter,
				rotor.radiusMeters(),
				diameter,
				Math.max(0.0, revolutionsPerSecond),
				Math.max(0.0, angularVelocity),
				0.0,
				lookup.ctCoefficient(),
				lookup.cpCoefficient(),
				lookup.eta(),
				0.0,
				0.0,
				0.0,
				diskArea,
				0.0,
				0.0,
				0.0,
				0.0,
				ratio(0.0, config.massKg() * config.gravityMetersPerSecondSquared()),
				false,
				false,
				"LOOKUP_EXECUTION_BLOCKED",
				lookup.message()
		);
	}

	private static DimensionalResponseSummary summary(List<DimensionalResponseScenario> scenarios) {
		int ready = 0;
		int runtime = 0;
		int gameplay = 0;
		double maxThrust = 0.0;
		double maxPower = 0.0;
		double maxTorque = 0.0;
		double maxDiskLoading = 0.0;
		double maxInduced = 0.0;
		double maxAdvanceSpeed = 0.0;
		double maxThrustToWeight = 0.0;
		for (DimensionalResponseScenario scenario : scenarios) {
			RotorDimensionalResponse response = scenario.response();
			if (response.dimensionalResponseReady()) {
				ready++;
			}
			maxThrust = Math.max(maxThrust, response.thrustNewtons());
			maxPower = Math.max(maxPower, response.shaftPowerWatts());
			maxTorque = Math.max(maxTorque, response.shaftTorqueNewtonMeters());
			maxDiskLoading = Math.max(maxDiskLoading, response.diskLoadingNewtonsPerSquareMeter());
			maxInduced = Math.max(maxInduced, response.idealInducedVelocityMetersPerSecond());
			maxAdvanceSpeed = Math.max(maxAdvanceSpeed, response.axialAdvanceSpeedMetersPerSecond());
			maxThrustToWeight = Math.max(maxThrustToWeight, response.totalThrustToWeightRatio());
			if (response.runtimeCouplingAllowed()) {
				runtime++;
			}
			if (response.gameplayAutoApplyAllowed()) {
				gameplay++;
			}
		}
		return new DimensionalResponseSummary(
				scenarios.size(),
				ready,
				scenarios.size() - ready,
				maxThrust,
				maxPower,
				maxTorque,
				maxDiskLoading,
				maxInduced,
				maxAdvanceSpeed,
				maxThrustToWeight,
				runtime,
				gameplay,
				NEXT_REQUIRED_ACTION
		);
	}

	private static DroneConfig configFor(String presetName) {
		return switch (presetName) {
			case "racingQuad" -> DroneConfig.racingQuad();
			case "apDrone" -> DroneConfig.apDrone();
			case "cinewhoop" -> DroneConfig.cinewhoop();
			case "heavyLift" -> DroneConfig.heavyLift();
			default -> throw new IllegalArgumentException("unknown DroneConfig preset: " + presetName);
		};
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}
}
