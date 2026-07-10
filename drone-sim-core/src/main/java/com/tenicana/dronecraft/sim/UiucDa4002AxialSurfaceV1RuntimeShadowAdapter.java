package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorForceModel.ForceApplicationStatus;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorForceModel.ForceSample;
import com.tenicana.dronecraft.sim.UiucDa4002MeasuredRotorModel.Propeller;

/** Builds read-only DA4002 v1 reference samples from runtime state telemetry. */
public final class UiucDa4002AxialSurfaceV1RuntimeShadowAdapter {
	private static final double GEOMETRY_TOLERANCE_METERS = 1.0e-6;

	private UiucDa4002AxialSurfaceV1RuntimeShadowAdapter() {
	}

	public enum ShadowStatus {
		AXIAL_REFERENCE_AVAILABLE,
		OBLIQUE_AXIAL_PROJECTION_REFERENCE,
		NON_POSITIVE_THRUST_REFERENCE,
		BLOCKED_NON_POSITIVE_RPM,
		BLOCKED_GEOMETRY_MISMATCH,
		BLOCKED_REVERSE_AXIAL_FLOW,
		BLOCKED_COEFFICIENT_SURFACE
	}

	public static StateShadowSample sample(
			Propeller propeller,
			DroneConfig config,
			DroneState state,
			DroneEnvironment environment
	) {
		if (propeller == null) {
			throw new IllegalArgumentException("propeller must not be null.");
		}
		if (config == null) {
			throw new IllegalArgumentException("config must not be null.");
		}
		if (state == null) {
			throw new IllegalArgumentException("state must not be null.");
		}
		if (state.motorCount() != config.rotors().size()) {
			throw new IllegalArgumentException(
					"state motor count must match configured rotor count."
			);
		}
		DroneEnvironment resolvedEnvironment = environment == null
				? DroneEnvironment.calm()
				: environment;
		double airDensity = UiucDa4002AxialSurfaceV1
				.REFERENCE_AIR_DENSITY_KG_PER_CUBIC_METER
				* resolvedEnvironment.effectiveAirDensityRatio();
		double temperature = resolvedEnvironment.effectiveAmbientTemperatureCelsius();
		double humidity = resolvedEnvironment.ambientHumidity();
		double dynamicViscosity = PropellerArchiveCtCpJRotorForceModel.operatingPoint(
				config.rotors().get(0),
				Vec3.ZERO,
				0.0,
				airDensity,
				temperature,
				humidity
		).dynamicViscosityPascalSeconds();
		Vec3[] relativeAirVelocities = rotorRelativeAirVelocities(
				config,
				state,
				resolvedEnvironment
		);
		List<RotorShadowSample> rotorSamples = new ArrayList<>();
		for (int index = 0; index < config.rotors().size(); index++) {
			RotorSpec rotor = config.rotors().get(index);
			double rpm = state.motorRpm(index);
			GeometryMatch geometry = geometryMatch(propeller, rotor);
			Optional<ForceSample> reference = Optional.empty();
			ShadowStatus status;
			String message;
			if (!geometry.matched()) {
				status = ShadowStatus.BLOCKED_GEOMETRY_MISMATCH;
				message = geometry.message();
			} else if (!Double.isFinite(rpm) || rpm <= 0.0) {
				status = ShadowStatus.BLOCKED_NON_POSITIVE_RPM;
				message = "runtime-rotor-rpm-is-not-positive";
			} else {
				ForceSample forceSample = UiucDa4002MeasuredRotorForceModel.sample(
						new UiucDa4002MeasuredRotorForceModel.Query(
								propeller,
								rotor,
								rpm,
								airDensity,
								dynamicViscosity,
								config.centerOfMassOffsetBodyMeters(),
								relativeAirVelocities[index]
						)
				);
				reference = Optional.of(forceSample);
				status = shadowStatus(forceSample.forceApplicationStatus());
				message = forceSample.message();
			}
			rotorSamples.add(new RotorShadowSample(
					index,
					rotor,
					rpm,
					relativeAirVelocities[index],
					geometry,
					status,
					reference,
					state.rotorThrustNewtons(index),
					state.motorShaftPowerWatts(index),
					state.motorAerodynamicTorqueNewtonMeters(index),
					state.rotorForceBodyNewtons(index),
					state.rotorTorqueBodyNewtonMeters(index),
					message
			));
		}
		return new StateShadowSample(
				UiucDa4002AxialSurfaceV1.VERSION_ID,
				propeller,
				airDensity,
				dynamicViscosity,
				temperature,
				humidity,
				rotorSamples,
				false
		);
	}

	private static Vec3[] rotorRelativeAirVelocities(
			DroneConfig config,
			DroneState state,
			DroneEnvironment environment
	) {
		Quaternion orientation = finiteOrientation(state.orientation());
		Quaternion worldToBody = orientation.conjugate();
		Vec3 vehicleVelocity = finiteVector(state.velocityMetersPerSecond());
		Vec3 baselineWind = finiteVector(environment.windVelocityWorldMetersPerSecond());
		Vec3 angularVelocity = finiteVector(state.angularVelocityBodyRadiansPerSecond());
		Vec3[] localRotorWind = environment.rotorWindVelocityWorldMetersPerSecond();
		Vec3[] relative = new Vec3[config.rotors().size()];
		for (int index = 0; index < config.rotors().size(); index++) {
			RotorSpec rotor = config.rotors().get(index);
			Vec3 wind = localRotorWind != null
					&& index < localRotorWind.length
					&& localRotorWind[index] != null
					&& localRotorWind[index].isFinite()
						? localRotorWind[index]
						: baselineWind;
			Vec3 bodyRelative = worldToBody.rotate(vehicleVelocity.subtract(wind));
			Vec3 arm = rotor.positionBodyMeters()
					.subtract(config.centerOfMassOffsetBodyMeters());
			relative[index] = bodyRelative.add(angularVelocity.cross(arm));
		}
		return relative;
	}

	private static GeometryMatch geometryMatch(Propeller propeller, RotorSpec rotor) {
		double expectedDiameter = propeller.diameterMeters();
		double expectedPitch = expectedNominalPitchMeters(propeller);
		int expectedBladeCount = propeller.geometry().bladeCount();
		boolean diameterMatches = Math.abs(2.0 * rotor.radiusMeters() - expectedDiameter)
				<= GEOMETRY_TOLERANCE_METERS;
		boolean pitchMatches = Math.abs(rotor.bladePitchMeters() - expectedPitch)
				<= GEOMETRY_TOLERANCE_METERS;
		boolean bladeCountMatches = rotor.bladeCount() == expectedBladeCount;
		List<String> mismatches = new ArrayList<>();
		if (!diameterMatches) {
			mismatches.add("diameter");
		}
		if (!pitchMatches) {
			mismatches.add("pitch");
		}
		if (!bladeCountMatches) {
			mismatches.add("blade-count");
		}
		return new GeometryMatch(
				expectedDiameter,
				2.0 * rotor.radiusMeters(),
				expectedPitch,
				rotor.bladePitchMeters(),
				expectedBladeCount,
				rotor.bladeCount(),
				diameterMatches,
				pitchMatches,
				bladeCountMatches,
				mismatches.isEmpty()
						? "exact-uiuc-da4002-geometry-match"
						: "uiuc-da4002-geometry-mismatch:" + String.join("+", mismatches)
		);
	}

	private static double expectedNominalPitchMeters(Propeller propeller) {
		return switch (propeller) {
			case DA4002_5X3_75 -> 3.75 * 0.0254;
			case DA4002_9X6_75 -> 6.75 * 0.0254;
		};
	}

	private static ShadowStatus shadowStatus(ForceApplicationStatus status) {
		return switch (status) {
			case APPLIED_AXIAL_MEASURED -> ShadowStatus.AXIAL_REFERENCE_AVAILABLE;
			case REFERENCE_ONLY_TRANSVERSE_INFLOW ->
					ShadowStatus.OBLIQUE_AXIAL_PROJECTION_REFERENCE;
			case REFERENCE_ONLY_NON_POSITIVE_THRUST ->
					ShadowStatus.NON_POSITIVE_THRUST_REFERENCE;
			case BLOCKED_REVERSE_AXIAL_FLOW -> ShadowStatus.BLOCKED_REVERSE_AXIAL_FLOW;
			case BLOCKED_COEFFICIENT_SURFACE -> ShadowStatus.BLOCKED_COEFFICIENT_SURFACE;
			case BLOCKED_ROTOR_DIAMETER_MISMATCH -> ShadowStatus.BLOCKED_GEOMETRY_MISMATCH;
		};
	}

	private static Quaternion finiteOrientation(Quaternion value) {
		if (value == null
				|| !Double.isFinite(value.w())
				|| !Double.isFinite(value.x())
				|| !Double.isFinite(value.y())
				|| !Double.isFinite(value.z())) {
			return Quaternion.IDENTITY;
		}
		return value.normalized();
	}

	private static Vec3 finiteVector(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}

	public record GeometryMatch(
			double expectedDiameterMeters,
			double actualDiameterMeters,
			double expectedPitchMeters,
			double actualPitchMeters,
			int expectedBladeCount,
			int actualBladeCount,
			boolean diameterMatches,
			boolean pitchMatches,
			boolean bladeCountMatches,
			String message
	) {
		public boolean matched() {
			return diameterMatches && pitchMatches && bladeCountMatches;
		}
	}

	public record RotorShadowSample(
			int rotorIndex,
			RotorSpec rotor,
			double rpm,
			Vec3 relativeAirVelocityBodyMetersPerSecond,
			GeometryMatch geometryMatch,
			ShadowStatus status,
			Optional<ForceSample> reference,
			double actualThrustNewtons,
			double actualShaftPowerWatts,
			double actualShaftTorqueNewtonMeters,
			Vec3 actualForceBodyNewtons,
			Vec3 actualTorqueBodyNewtonMeters,
			String message
	) {
		public RotorShadowSample {
			if (rotor == null || geometryMatch == null || status == null) {
				throw new IllegalArgumentException(
						"rotor, geometryMatch, and status must not be null."
				);
			}
			reference = reference == null ? Optional.empty() : reference;
			relativeAirVelocityBodyMetersPerSecond = finiteVector(
					relativeAirVelocityBodyMetersPerSecond);
			actualForceBodyNewtons = finiteVector(actualForceBodyNewtons);
			actualTorqueBodyNewtonMeters = finiteVector(actualTorqueBodyNewtonMeters);
			message = message == null ? "" : message;
		}

		public boolean blocked() {
			return switch (status) {
				case BLOCKED_NON_POSITIVE_RPM,
						BLOCKED_GEOMETRY_MISMATCH,
						BLOCKED_REVERSE_AXIAL_FLOW,
						BLOCKED_COEFFICIENT_SURFACE -> true;
				default -> false;
			};
		}

		public boolean scalarReferenceComparable() {
			return switch (status) {
				case AXIAL_REFERENCE_AVAILABLE,
						OBLIQUE_AXIAL_PROJECTION_REFERENCE,
						NON_POSITIVE_THRUST_REFERENCE ->
						reference.map(ForceSample::coefficientSampleAvailable).orElse(false);
				default -> false;
			};
		}

		public boolean vectorReferenceComparable() {
			return status == ShadowStatus.AXIAL_REFERENCE_AVAILABLE;
		}

		public boolean runtimeForceApplied() {
			return false;
		}

		public double referenceThrustNewtons() {
			return reference.flatMap(ForceSample::dimensionalSample)
					.map(UiucDa4002MeasuredRotorModel.DimensionalSample::thrustNewtons)
					.orElse(0.0);
		}

		public double referenceShaftPowerWatts() {
			return reference.flatMap(ForceSample::dimensionalSample)
					.map(UiucDa4002MeasuredRotorModel.DimensionalSample::shaftPowerWatts)
					.orElse(0.0);
		}

		public double referenceShaftTorqueNewtonMeters() {
			return reference.flatMap(ForceSample::dimensionalSample)
					.map(UiucDa4002MeasuredRotorModel.DimensionalSample::shaftTorqueNewtonMeters)
					.orElse(0.0);
		}

		public Vec3 referenceForceBodyNewtons() {
			return reference.map(ForceSample::referenceThrustForceBodyNewtons)
					.orElse(Vec3.ZERO);
		}

		public Vec3 referenceTorqueBodyNewtonMeters() {
			return reference.map(ForceSample::referenceTotalTorqueBodyNewtonMeters)
					.orElse(Vec3.ZERO);
		}

		public double thrustResidualNewtons() {
			return actualThrustNewtons - referenceThrustNewtons();
		}

		public double shaftPowerResidualWatts() {
			return actualShaftPowerWatts - referenceShaftPowerWatts();
		}

		public double shaftTorqueResidualNewtonMeters() {
			return actualShaftTorqueNewtonMeters - referenceShaftTorqueNewtonMeters();
		}

		public Vec3 forceResidualBodyNewtons() {
			return actualForceBodyNewtons.subtract(referenceForceBodyNewtons());
		}

		public Vec3 torqueResidualBodyNewtonMeters() {
			return actualTorqueBodyNewtonMeters.subtract(referenceTorqueBodyNewtonMeters());
		}

		public double actualPowerClosureResidualWatts() {
			return actualShaftPowerWatts
					- actualShaftTorqueNewtonMeters * rpm * 2.0 * Math.PI / 60.0;
		}
	}

	public record StateShadowSample(
			String modelVersionId,
			Propeller propeller,
			double airDensityKgPerCubicMeter,
			double dynamicViscosityPascalSeconds,
			double ambientTemperatureCelsius,
			double ambientHumidity,
			List<RotorShadowSample> rotorSamples,
			boolean runtimeForceApplied
	) {
		public StateShadowSample {
			rotorSamples = List.copyOf(rotorSamples);
			if (runtimeForceApplied) {
				throw new IllegalArgumentException("runtime shadow must never apply force.");
			}
		}

		public int comparableScalarRotorCount() {
			return (int) rotorSamples.stream()
					.filter(RotorShadowSample::scalarReferenceComparable).count();
		}

		public int comparableVectorRotorCount() {
			return (int) rotorSamples.stream()
					.filter(RotorShadowSample::vectorReferenceComparable).count();
		}

		public int blockedRotorCount() {
			return (int) rotorSamples.stream().filter(RotorShadowSample::blocked).count();
		}

		public double actualTotalThrustNewtons() {
			return rotorSamples.stream().mapToDouble(RotorShadowSample::actualThrustNewtons)
					.sum();
		}

		public double referenceTotalThrustNewtons() {
			return rotorSamples.stream().mapToDouble(RotorShadowSample::referenceThrustNewtons)
					.sum();
		}

		public double thrustResidualNewtons() {
			return actualTotalThrustNewtons() - referenceTotalThrustNewtons();
		}

		public double actualTotalShaftPowerWatts() {
			return rotorSamples.stream().mapToDouble(RotorShadowSample::actualShaftPowerWatts)
					.sum();
		}

		public double referenceTotalShaftPowerWatts() {
			return rotorSamples.stream().mapToDouble(RotorShadowSample::referenceShaftPowerWatts)
					.sum();
		}

		public double shaftPowerResidualWatts() {
			return actualTotalShaftPowerWatts() - referenceTotalShaftPowerWatts();
		}

		public double actualTotalShaftTorqueNewtonMeters() {
			return rotorSamples.stream()
					.mapToDouble(RotorShadowSample::actualShaftTorqueNewtonMeters).sum();
		}

		public double referenceTotalShaftTorqueNewtonMeters() {
			return rotorSamples.stream()
					.mapToDouble(RotorShadowSample::referenceShaftTorqueNewtonMeters).sum();
		}

		public double shaftTorqueResidualNewtonMeters() {
			return actualTotalShaftTorqueNewtonMeters()
					- referenceTotalShaftTorqueNewtonMeters();
		}

		public Vec3 actualTotalForceBodyNewtons() {
			return sumVectors(rotorSamples.stream()
					.map(RotorShadowSample::actualForceBodyNewtons).toList());
		}

		public Vec3 referenceTotalForceBodyNewtons() {
			return sumVectors(rotorSamples.stream()
					.map(RotorShadowSample::referenceForceBodyNewtons).toList());
		}

		public Vec3 forceResidualBodyNewtons() {
			return actualTotalForceBodyNewtons().subtract(referenceTotalForceBodyNewtons());
		}

		public Vec3 actualTotalTorqueBodyNewtonMeters() {
			return sumVectors(rotorSamples.stream()
					.map(RotorShadowSample::actualTorqueBodyNewtonMeters).toList());
		}

		public Vec3 referenceTotalTorqueBodyNewtonMeters() {
			return sumVectors(rotorSamples.stream()
					.map(RotorShadowSample::referenceTorqueBodyNewtonMeters).toList());
		}

		public Vec3 torqueResidualBodyNewtonMeters() {
			return actualTotalTorqueBodyNewtonMeters()
					.subtract(referenceTotalTorqueBodyNewtonMeters());
		}
	}

	private static Vec3 sumVectors(List<Vec3> vectors) {
		Vec3 sum = Vec3.ZERO;
		for (Vec3 vector : vectors) {
			sum = sum.add(vector);
		}
		return sum;
	}
}
