package com.tenicana.dronecraft.integration;

import com.tenicana.dronecraft.sim.MathUtil;
import com.tenicana.dronecraft.sim.Vec3;

/** Compact, allocation-conscious adoption of one A4MC body-center sample. */
public final class AerodynamicsAtmosphereCoupling {
	private AerodynamicsAtmosphereCoupling() {
	}

	public static Vec3 adoptedAtmosphereWind(
			Vec3 fallbackWind,
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		Vec3 fallback = fallbackWind == null || !fallbackWind.isFinite() ? Vec3.ZERO : fallbackWind;
		if (atmosphere == null || !atmosphere.hasFlow() || sourceQuality <= 1.0e-9) {
			return fallback;
		}
		double quality = MathUtil.clamp(sourceQuality, 0.0, 1.0);
		if (!atmosphere.hasMeanVelocity()) {
			return fallback;
		}

		double windX = fallback.x();
		double windY = fallback.y();
		double windZ = fallback.z();
		double fallbackWeight = 1.0 - quality;
		windX = fallback.x() * fallbackWeight + atmosphere.meanVelocityX() * quality;
		windY = fallback.y() * fallbackWeight + atmosphere.meanVelocityY() * quality;
		windZ = fallback.z() * fallbackWeight + atmosphere.meanVelocityZ() * quality;
		return new Vec3(windX, windY, windZ).clamp(-30.0, 30.0);
	}

	public static double adoptedUpdraftMetersPerSecond(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		if (atmosphere == null || !atmosphere.hasFlow() || sourceQuality <= 1.0e-9) {
			return 0.0;
		}
		return MathUtil.clamp(
				separatedAtmosphereUpdraftMetersPerSecond(atmosphere)
						* MathUtil.clamp(sourceQuality, 0.0, 1.0),
				-12.0,
				12.0
		);
	}

	public static double adoptedUpdraftLocalVoxelGain(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		return atmosphere != null && atmosphere.hasFlow() && sourceQuality > 1.0e-9
				? atmosphere.localVoxelFlow() ? 1.0 : 0.72
				: 0.0;
	}

	/** Keeps the coherent A4MC gust separate from the mean-air filter and gates it exactly once. */
	public static Vec3 adoptedAtmosphereGustVelocity(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		if (atmosphere == null || !atmosphere.hasFlow() || sourceQuality <= 1.0e-9) {
			return Vec3.ZERO;
		}
		double quality = MathUtil.clamp(sourceQuality, 0.0, 1.0);
		double gustX = atmosphere.gustVelocityXMetersPerSecond() * quality;
		double gustY = atmosphere.gustVerticalMetersPerSecond() * quality;
		double gustZ = atmosphere.gustVelocityZMetersPerSecond() * quality;
		if (gustX * gustX + gustY * gustY + gustZ * gustZ <= 1.0e-18) {
			return Vec3.ZERO;
		}
		return new Vec3(
				MathUtil.clamp(gustX, -30.0, 30.0),
				MathUtil.clamp(gustY, -30.0, 30.0),
				MathUtil.clamp(gustZ, -30.0, 30.0)
		);
	}

	public static double adoptedAblStability(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		if (atmosphere == null || !atmosphere.hasFlow() || sourceQuality <= 1.0e-9) {
			return 0.0;
		}
		return MathUtil.clamp(
				atmosphere.ablStability() * MathUtil.clamp(sourceQuality, 0.0, 1.0),
				-1.0,
				1.0
		);
	}

	public static double adoptedAblMixingStrength(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		if (atmosphere == null || !atmosphere.hasFlow() || sourceQuality <= 1.0e-9) {
			return 0.0;
		}
		return MathUtil.clamp(
				atmosphere.ablMixingStrength() * MathUtil.clamp(sourceQuality, 0.0, 1.0),
				0.0,
				1.0
		);
	}

	public static double adoptedWindShearMagnitudePerBlock(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		if (atmosphere == null || !atmosphere.hasFlow() || sourceQuality <= 1.0e-9) {
			return 0.0;
		}
		return MathUtil.clamp(
				atmosphere.windShearMagnitudePerBlock() * MathUtil.clamp(sourceQuality, 0.0, 1.0),
				0.0,
				5.0
		);
	}

	public static double adoptedShelterFactor(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		if (atmosphere == null || !atmosphere.hasFlow() || sourceQuality <= 1.0e-9) {
			return 0.0;
		}
		return MathUtil.clamp(
				atmosphere.shelterFactor() * MathUtil.clamp(sourceQuality, 0.0, 1.0),
				0.0,
				1.0
		);
	}

	public static double adoptedAtmosphereTurbulence(
			double fallbackTurbulence,
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		double fallback = Double.isFinite(fallbackTurbulence)
				? MathUtil.clamp(fallbackTurbulence, 0.0, 1.5)
				: 0.0;
		if (atmosphere == null || !atmosphere.hasFlow() || sourceQuality <= 1.0e-9) {
			return fallback;
		}
		double quality = MathUtil.clamp(sourceQuality, 0.0, 1.0);
		double turbulence = Math.max(fallback, atmosphere.turbulenceIntensity() * quality);
		turbulence += quality * MathUtil.clamp(
				atmosphere.windShearMagnitudePerBlock() * 0.45,
				0.0,
				0.35
		);
		turbulence += quality * MathUtil.clamp(atmosphere.shelterFactor() * 0.20, 0.0, 0.20);
		turbulence += quality * MathUtil.clamp(
				Math.abs(separatedAtmosphereUpdraftMetersPerSecond(atmosphere)) * 0.025,
				0.0,
				0.18
		);
		if (atmosphere.localVoxelFlow()) {
			turbulence += quality * MathUtil.clamp(
						Math.abs(atmosphere.pressureAnomalyPascals()) / 1800.0 * 0.16,
						0.0,
						0.16
			);
		}
		turbulence += quality * MathUtil.clamp(
				atmosphere.gustSpeedMetersPerSecond() * 0.065,
				0.0,
				0.26
		);
		return MathUtil.clamp(turbulence, 0.0, 1.5);
	}

	public static double separatedAtmosphereUpdraftMetersPerSecond(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere
	) {
		if (atmosphere == null || !atmosphere.hasFlow()) {
			return 0.0;
		}
		double separated = removeOverlappingVerticalFlow(
				atmosphere.updraftMetersPerSecond(),
				atmosphere.gustVerticalMetersPerSecond()
		);
		return removeOverlappingVerticalFlow(
				separated,
				atmosphere.hasMeanVelocity() ? atmosphere.meanVelocityY() : 0.0
		);
	}

	public static double removeOverlappingVerticalFlow(
			double verticalSignal,
			double representedVerticalFlow
	) {
		if (Math.abs(verticalSignal) <= 1.0e-9
				|| Math.abs(representedVerticalFlow) <= 1.0e-9
				|| Math.signum(verticalSignal) != Math.signum(representedVerticalFlow)) {
			return verticalSignal;
		}
		return Math.abs(verticalSignal) <= Math.abs(representedVerticalFlow)
				? 0.0
				: verticalSignal - representedVerticalFlow;
	}

	public static double adoptedAtmospherePressureAnomalyPascals(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		if (atmosphere == null || !atmosphere.hasFlow() || sourceQuality <= 1.0e-9) {
			return 0.0;
		}
		return MathUtil.clamp(
				atmosphere.pressureAnomalyPascals() * MathUtil.clamp(sourceQuality, 0.0, 1.0),
				-5000.0,
				5000.0
		);
	}

	public static double motorEscVentilationFactor(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		double adoptedShelter = adoptedLocalVoxelShelter(atmosphere, sourceQuality);
		return adoptedShelter <= 1.0e-9
				? 1.0
				: MathUtil.clamp(1.0 - 0.20 * adoptedShelter, 0.72, 1.0);
	}

	public static double batteryVentilationFactor(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		double adoptedShelter = adoptedLocalVoxelShelter(atmosphere, sourceQuality);
		return adoptedShelter <= 1.0e-9
				? 1.0
				: MathUtil.clamp(1.0 - 0.14 * adoptedShelter, 0.78, 1.0);
	}

	private static double adoptedLocalVoxelShelter(
			Aerodynamics4McAtmosphereBridge.AtmosphereSample atmosphere,
			double sourceQuality
	) {
		if (atmosphere == null
				|| !atmosphere.hasFlow()
				|| !atmosphere.localVoxelFlow()
				|| sourceQuality <= 1.0e-9) {
			return 0.0;
		}
		return MathUtil.clamp(
				atmosphere.shelterFactor() * MathUtil.clamp(sourceQuality, 0.0, 1.0),
				0.0,
				1.0
		);
	}
}
