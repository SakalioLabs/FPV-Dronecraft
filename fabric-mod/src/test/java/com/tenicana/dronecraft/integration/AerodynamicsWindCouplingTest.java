package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.sim.DroneEnvironment;
import com.tenicana.dronecraft.sim.DronePhysics;
import com.tenicana.dronecraft.sim.Vec3;

class AerodynamicsWindCouplingTest {
	private static final double ROTOR_DISK_SURFACE_CENTER_WEIGHT = 0.36;
	private static final double ROTOR_DISK_SURFACE_CARDINAL_WEIGHT = 0.11;
	private static final double ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT = 0.05;

	@Test
	void generatedLocalVoxelCouplingPacketSummaryMatchesRuntimeFormulas() throws Exception {
		Map<String, Double> summary = localVoxelPacketSummary();
		double wallSkimSourceQuality = AerodynamicsWindCoupling.sourceQualityFactor(true, true, 0.86, 0L);

		assertEquals(40, summary.size());
		assertEquals(48.0, summaryMetric(summary, "quality_residual_scenario_count"), 1.0e-9);
		assertEquals(48.0, summaryMetric(summary, "precipitation_exposure_scenario_count"), 1.0e-9);
		assertEquals(6.0, summaryMetric(summary, "rotor_residual_fallback_scenario_count"), 1.0e-9);
		assertEquals(60.0, summaryMetric(summary, "pressure_gradient_scenario_count"), 1.0e-9);
		assertEquals(36.0, summaryMetric(summary, "pressure_contrast_scenario_count"), 1.0e-9);
		assertEquals(60.0, summaryMetric(summary, "shelter_gradient_scenario_count"), 1.0e-9);
		assertEquals(84.0, summaryMetric(summary, "pressure_center_scenario_count"), 1.0e-9);
		assertEquals(6.0, summaryMetric(summary, "ventilation_scenario_count"), 1.0e-9);
		assertEquals(ROTOR_DISK_SURFACE_CENTER_WEIGHT, summaryMetric(summary, "disk_sample_center_weight"), 1.0e-12);
		assertEquals(ROTOR_DISK_SURFACE_CARDINAL_WEIGHT, summaryMetric(summary, "disk_sample_cardinal_weight"), 1.0e-12);
		assertEquals(ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT, summaryMetric(summary, "disk_sample_diagonal_weight"), 1.0e-12);

		assertEquals(
				AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(true, true, true, 0.86, 0.74, 0L),
				summaryMetric(summary, "wall_skim_quality_0p86_shelter_0p74_residual"),
				1.0e-12
		);
		assertEquals(
				AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(true, true, true, 0.86, 0.74, 0L),
				summaryMetric(summary, "wall_skim_quality_0p86_shelter_0p74_precipitation_exposure"),
				1.0e-12
		);
		assertEquals(
				AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(true, true, true, 1.0, 1.0, 0L),
				summaryMetric(summary, "trusted_full_shelter_precipitation_exposure"),
				1.0e-12
		);
		assertEquals(1.0, summaryMetric(summary, "stale_wall_skim_precipitation_exposure"), 1.0e-12);
		assertEquals(
				summaryMetric(summary, "wall_skim_quality_0p86_shelter_0p74_residual"),
				summaryMetric(summary, "coarse_rotor_sample_body_fallback_residual"),
				1.0e-12
		);
		assertEquals(0.68, summaryMetric(summary, "trusted_exposed_rotor_sample_residual"), 1.0e-12);

		AerodynamicsWindCoupling.RotorDiskPressureBlend wallSkimPressure = oneSidedPressureBlend(220.0);
		Vec3 wallSkimPressureWind = AerodynamicsWindCoupling.localVoxelPressureGradientWindEquivalent(wallSkimPressure);
		assertEquals(
				wallSkimPressureWind.x() * wallSkimSourceQuality,
				summaryMetric(summary, "wall_skim_pressure_220pa_quality_weighted_gradient_mps"),
				1.0e-12
		);
		assertEquals(0.0, wallSkimPressureWind.y(), 1.0e-12);
		assertEquals(0.0, wallSkimPressureWind.z(), 1.0e-12);

		Vec3 maxPressureWind = AerodynamicsWindCoupling.localVoxelPressureGradientWindEquivalent(oneSidedPressureBlend(3200.0));
		assertEquals(
				maxPressureWind.x(),
				summaryMetric(summary, "max_pressure_quality_weighted_gradient_mps"),
				1.0e-11
		);
		AerodynamicsWindCoupling.RotorDiskPressureBlend halfQualityPressureContrast =
				oneSidedPressureContrastBlend(-5000.0, 5000.0, 0.50, 0L);
		Vec3 halfQualityPressureContrastWind =
				AerodynamicsWindCoupling.localVoxelPressureGradientWindEquivalent(halfQualityPressureContrast);
		assertEquals(
				halfQualityPressureContrastWind.x(),
				summaryMetric(summary, "saturated_pressure_contrast_runtime_weighted_gradient_mps"),
				1.0e-10
		);
		assertEquals(1.2, summaryMetric(summary, "saturated_pressure_contrast_post_equivalent_scaled_mps"), 1.0e-12);
		assertTrue(summaryMetric(summary, "saturated_pressure_contrast_quality_first_ratio") > 1.70);

		AerodynamicsWindCoupling.RotorDiskShelterBlend wallSkimShelter =
				oneSidedShelterBlend(0.35, 0.74, 0.86, 0L);
		assertEquals(
				AerodynamicsWindCoupling.localVoxelShelterGradientObstruction(wallSkimShelter),
				summaryMetric(summary, "wall_skim_shelter_delta_0p74_obstruction"),
				1.0e-12
		);
		assertEquals(0.0, summaryMetric(summary, "stale_shelter_delta_0p74_obstruction"), 1.0e-12);

		assertEquals(
				0.000285616448892,
				summaryMetric(summary, "wall_skim_pressure_center_offset_magnitude_m"),
				1.0e-15
		);
		assertEquals(-0.024, summaryMetric(summary, "right_combined_pressure_center_offset_x_m"), 1.0e-15);
		assertEquals(
				summaryMetric(summary, "right_combined_pressure_center_offset_x_m"),
				summaryMetric(summary, "right_combined_half_quality_offset_x_m"),
				1.0e-15
		);
		assertEquals(-0.024, summaryMetric(summary, "front_combined_pressure_center_offset_z_m"), 1.0e-15);
		assertEquals(
				0.0,
				summaryMetric(summary, "right_tangential_pressure_center_offset_magnitude_m"),
				1.0e-15
		);
		assertEquals(
				0.0,
				summaryMetric(summary, "right_tangential_pressure_center_radial_gradient_mps"),
				1.0e-12
		);
		assertEquals(0.0, summaryMetric(summary, "quality_zero_pressure_center_offset_magnitude_m"), 1.0e-15);

		DroneEnvironment wallSkimVentilation = a4mcVentilationEnvironment(
				0.85,
				true,
				1.0,
				0L,
				new double[] {0.50, 0.50, 0.80, 0.80},
				new double[] {0.16, 0.16, 0.08, 0.08}
		);
		DroneEnvironment halfQualityVentilation = a4mcVentilationEnvironment(
				0.85,
				true,
				0.50,
				0L,
				new double[] {0.75, 0.75, 0.90, 0.90},
				new double[] {0.08, 0.08, 0.04, 0.04}
		);
		DroneEnvironment coarseVentilation = a4mcVentilationEnvironment(
				0.85,
				false,
				1.0,
				0L,
				new double[] {0.50, 0.50, 0.80, 0.80},
				new double[] {0.16, 0.16, 0.08, 0.08}
		);
		DroneEnvironment staleVentilation = a4mcVentilationEnvironment(
				0.85,
				true,
				0.86,
				160L,
				new double[] {0.50, 0.50, 0.80, 0.80},
				new double[] {0.16, 0.16, 0.08, 0.08}
		);
		DroneEnvironment clampedTunnelVentilation = a4mcVentilationEnvironment(
				1.0,
				true,
				1.0,
				0L,
				new double[] {0.0, 0.0, 0.0, 0.0},
				new double[] {1.0, 1.0, 1.0, 1.0}
		);
		assertEquals(
				invokeA4mcLocalVoxelVentilationEfficiency(wallSkimVentilation, 0),
				summaryMetric(summary, "wall_skim_rotor0_ventilation_efficiency"),
				1.0e-12
		);
		assertEquals(
				invokeA4mcPackVentilationEfficiency(wallSkimVentilation, 4),
				summaryMetric(summary, "wall_skim_pack_ventilation_efficiency"),
				1.0e-12
		);
		assertEquals(
				invokeA4mcLocalVoxelVentilationEfficiency(halfQualityVentilation, 0),
				summaryMetric(summary, "half_quality_wall_skim_rotor0_ventilation_efficiency"),
				1.0e-12
		);
		assertEquals(
				invokeA4mcPackVentilationEfficiency(halfQualityVentilation, 4),
				summaryMetric(summary, "half_quality_wall_skim_pack_ventilation_efficiency"),
				1.0e-12
		);
		assertEquals(
				invokeA4mcLocalVoxelVentilationEfficiency(coarseVentilation, 0),
				summaryMetric(summary, "coarse_wall_skim_rotor0_ventilation_efficiency"),
				1.0e-12
		);
		assertEquals(
				invokeA4mcPackVentilationEfficiency(staleVentilation, 4),
				summaryMetric(summary, "stale_wall_skim_pack_ventilation_efficiency"),
				1.0e-12
		);
		assertEquals(
				invokeA4mcLocalVoxelVentilationEfficiency(clampedTunnelVentilation, 0),
				summaryMetric(summary, "clamped_tunnel_rotor0_ventilation_efficiency"),
				1.0e-12
		);
		assertEquals(
				invokeA4mcPackVentilationEfficiency(clampedTunnelVentilation, 4),
				summaryMetric(summary, "clamped_tunnel_pack_ventilation_efficiency"),
				1.0e-12
		);
	}

	@Test
	void generatedSourceQualityPressurePacketSummaryTracksRuntimeFormulas() throws IOException {
		Map<String, Double> summary = sourceQualityPacketSummary();
		double wallSkimSourceQuality = AerodynamicsWindCoupling.sourceQualityFactor(true, true, 0.86, 0L);
		double halfQuality = AerodynamicsWindCoupling.sourceQualityFactor(true, true, 0.50, 0L);

		assertEquals(45, summary.size());
		assertEquals(325.0, summaryMetric(summary, "pressure_matrix_scenario_count"), 1.0e-9);
		assertEquals(
				DroneEnvironment.pressureAnomalyAirDensityMultiplier(-4500.0),
				summaryMetric(summary, "trusted_low_pressure_neg4500_density_multiplier"),
				1.0e-9
		);
		assertEquals(
				DroneEnvironment.pressureAnomalyAirDensityMultiplier(4500.0),
				summaryMetric(summary, "trusted_high_pressure_4500_density_multiplier"),
				1.0e-9
		);
		assertEquals(1.0, summaryMetric(summary, "stale_high_pressure_4500_density_multiplier"), 1.0e-12);
		assertEquals(-12.0 * wallSkimSourceQuality / 100.0,
				summaryMetric(summary, "wall_skim_pressure_neg12pa_barometer_delta_hpa"), 1.0e-9);
		assertEquals(
				staticPortPressureErrorMeters(-12.0 * wallSkimSourceQuality, 0.74, 0.38, 0.042393),
				summaryMetric(summary, "wall_skim_pressure_neg12pa_static_port_error_m"),
				1.0e-9
		);
		assertEquals(
				staticPortPressureErrorMeters(-12.0 * halfQuality, 0.74, 0.38, 0.042393),
				summaryMetric(summary, "wall_skim_pressure_neg12pa_half_quality_static_port_error_m"),
				1.0e-9
		);
		assertEquals(0.65, summaryMetric(summary, "wall_skim_pressure_neg220pa_static_port_error_m"), 1.0e-12);
		assertTrue(
				summaryMetric(summary, "open_air_pressure_neg12pa_static_port_error_m")
						< summaryMetric(summary, "wall_skim_pressure_neg12pa_static_port_error_m")
		);
		assertEquals(0.0, summaryMetric(summary, "coarse_pressure_neg12pa_static_port_error_m"), 1.0e-12);
	}

	@Test
	void unavailableOrCoarseWindKeepsLocalObstructionModel() {
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(null), 1.0e-9);
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				false,
				1.0,
				1.0
		), 1.0e-9);
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				false,
				true,
				1.0,
				1.0
		), 1.0e-9);
	}

	@Test
	void trustedLocalVoxelFlowLeavesBoundedResidualObstruction() {
		assertEquals(0.68, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				1.0,
				0.0
		), 1.0e-9);
		assertEquals(0.28, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				1.0,
				1.0
		), 1.0e-9);
		assertEquals(0.72, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				0.5,
				0.5
		), 1.0e-9);
	}

	@Test
	void trustedLocalVoxelShelterReducesPrecipitationExposure() {
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor((Aerodynamics4McWindBridge.WindSample) null), 1.0e-9);
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(
				true,
				true,
				false,
				1.0,
				1.0,
				0L
		), 1.0e-9);
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(
				true,
				false,
				true,
				1.0,
				1.0,
				0L
		), 1.0e-9);
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(
				true,
				true,
				true,
				1.0,
				0.0,
				0L
		), 1.0e-9);
		assertEquals(0.28, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(
				true,
				true,
				true,
				1.0,
				1.0,
				0L
		), 1.0e-9);
		assertEquals(0.82, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(
				true,
				true,
				true,
				0.5,
				0.5,
				0L
		), 1.0e-9);
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(
				true,
				true,
				true,
				1.0,
				1.0,
				160L
		), 1.0e-9);
	}

	@Test
	void rotorDiskShelterBlendMapsToPrecipitationExposure() {
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(
				new AerodynamicsWindCoupling.RotorDiskShelterBlend(0.0, Vec3.ZERO)
		), 1.0e-9);
		assertEquals(0.46, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(
				new AerodynamicsWindCoupling.RotorDiskShelterBlend(0.75, Vec3.ZERO)
		), 1.0e-9);
		assertEquals(0.28, AerodynamicsWindCoupling.localVoxelPrecipitationExposureFactor(
				new AerodynamicsWindCoupling.RotorDiskShelterBlend(1.0, Vec3.ZERO)
		), 1.0e-9);
	}

	@Test
	void sourceQualityUsesConfidenceAndFreshnessAge() {
		assertEquals(1.0, AerodynamicsWindCoupling.sourceQualityFactor(
				true,
				true,
				1.0,
				-1L
		), 1.0e-9);
		assertEquals(0.50, AerodynamicsWindCoupling.sourceQualityFactor(
				true,
				true,
				0.50,
				20L
		), 1.0e-9);
		assertEquals(0.50, AerodynamicsWindCoupling.sourceQualityFactor(
				true,
				true,
				1.0,
				100L
		), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceQualityFactor(
				true,
				true,
				1.0,
				160L
		), 1.0e-9);
	}

	@Test
	void staleLocalVoxelFlowRestoresLocalObstacleModel() {
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				1.0,
				1.0,
				160L
		), 1.0e-9);
		assertEquals(0.84, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				1.0,
				0.0,
				100L
		), 1.0e-9);
	}

	@Test
	void rotorLocalVoxelResidualOverridesBodyFallbackWhenSampleIsUsable() {
		double bodyShelteredResidual = AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(
				true,
				true,
				true,
				1.0,
				1.0
		);
		Aerodynamics4McWindBridge.WindSample exposedRotor = windSampleWithLocalVoxelShelter(0.0, true, 20L);
		Aerodynamics4McWindBridge.WindSample shelteredRotor = windSampleWithLocalVoxelShelter(1.0, true, 20L);
		Aerodynamics4McWindBridge.WindSample coarseRotor = windSampleWithLocalVoxelShelter(1.0, false, 20L);
		Aerodynamics4McWindBridge.WindSample staleRotor = windSampleWithLocalVoxelShelter(1.0, true, 200L);

		assertEquals(0.28, bodyShelteredResidual, 1.0e-9);
		assertEquals(0.68, AerodynamicsWindCoupling.localVoxelObstacleResidualFactorOrFallback(
				exposedRotor,
				bodyShelteredResidual
		), 1.0e-9);
		assertEquals(0.28, AerodynamicsWindCoupling.localVoxelObstacleResidualFactorOrFallback(
				shelteredRotor,
				1.0
		), 1.0e-9);
		assertEquals(bodyShelteredResidual, AerodynamicsWindCoupling.localVoxelObstacleResidualFactorOrFallback(
				coarseRotor,
				bodyShelteredResidual
		), 1.0e-9);
		assertEquals(bodyShelteredResidual, AerodynamicsWindCoupling.localVoxelObstacleResidualFactorOrFallback(
				staleRotor,
				bodyShelteredResidual
		), 1.0e-9);
		assertEquals(bodyShelteredResidual, AerodynamicsWindCoupling.localVoxelObstacleResidualFactorOrFallback(
				null,
				bodyShelteredResidual
		), 1.0e-9);
	}

	@Test
	void rotorDiskSampleDirectionPreservesVerticalRotorDiskDirections() {
		Vec3 direction = AerodynamicsWindCoupling.rotorDiskSampleDirectionBody(
				new Vec3(1.0, 0.0, 1.0),
				new Vec3(0.0, 1.0, 0.0)
		);

		assertEquals(new Vec3(1.0, 0.0, 1.0).normalized(), direction);
		assertEquals(1.0, direction.length(), 1.0e-12);
	}

	@Test
	void rotorDiskSampleDirectionProjectsCantedRotorDiskDirections() {
		double cant = Math.toRadians(30.0);
		Vec3 rotorAxisBody = new Vec3(Math.sin(cant), Math.cos(cant), 0.0).normalized();
		Vec3 direction = AerodynamicsWindCoupling.rotorDiskSampleDirectionBody(
				new Vec3(1.0, 0.0, 0.0),
				rotorAxisBody
		);

		assertEquals(1.0, direction.length(), 1.0e-12);
		assertEquals(0.0, direction.dot(rotorAxisBody), 1.0e-12);
		assertEquals(Math.cos(cant), direction.x(), 1.0e-12);
		assertEquals(-Math.sin(cant), direction.y(), 1.0e-12);
		assertEquals(0.0, direction.z(), 1.0e-12);
	}

	@Test
	void rotorDiskSampleDirectionFallsBackWhenSeedParallelsRotorAxis() {
		Vec3 direction = AerodynamicsWindCoupling.rotorDiskSampleDirectionBody(
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(1.0, 0.0, 0.0)
		);

		assertEquals(1.0, direction.length(), 1.0e-12);
		assertEquals(0.0, direction.dot(new Vec3(1.0, 0.0, 0.0)), 1.0e-12);
		assertEquals(new Vec3(0.0, 1.0, 0.0), direction);
	}

	@Test
	void rotorDiskShelterBlendCapturesDirectionalA4mcShelterGradient() {
		AerodynamicsWindCoupling.RotorDiskShelterBlend blend = AerodynamicsWindCoupling.rotorDiskShelterBlend(
				windSampleWithLocalVoxelShelter(0.25, true, 20L),
				new Aerodynamics4McWindBridge.WindSample[] {
						windSampleWithLocalVoxelShelter(1.0, true, 20L),
						windSampleWithLocalVoxelShelter(0.25, true, 20L)
				},
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(-1.0, 0.0, 0.0)
				},
				new double[] { 1.0, 1.0 },
				1.0
		);

		assertEquals(0.50, blend.meanShelterFactor(), 1.0e-9);
		assertEquals(new Vec3(0.375, 0.0, 0.0), blend.gradientBody());
		assertEquals(0.1063125, AerodynamicsWindCoupling.localVoxelShelterGradientObstruction(blend), 1.0e-9);
	}

	@Test
	void rotorDiskShelterBlendAppliesSampleQualityBeforeObstructionGate() {
		double sourceQuality = AerodynamicsWindCoupling.sourceQualityFactor(true, true, 0.86, 0L);
		AerodynamicsWindCoupling.RotorDiskShelterBlend rawBlend = oneSidedShelterBlend(0.35, 0.74);
		AerodynamicsWindCoupling.RotorDiskShelterBlend qualityWeightedBlend =
				oneSidedShelterBlend(0.35, 0.74, 0.86, 0L);
		double runtimeObstruction =
				AerodynamicsWindCoupling.localVoxelShelterGradientObstruction(qualityWeightedBlend);
		double postObstructionScaled =
				AerodynamicsWindCoupling.localVoxelShelterGradientObstruction(rawBlend) * sourceQuality;
		double affectedDiskWeight = ROTOR_DISK_SURFACE_CARDINAL_WEIGHT
				+ 2.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT;
		double edgeWeight = 4.0 * ROTOR_DISK_SURFACE_CARDINAL_WEIGHT
				+ 4.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT;
		double adoptedCenterShelter = 0.35 * sourceQuality;
		double adoptedEdgeShelter = adoptedCenterShelter * (1.0 - sourceQuality) + sourceQuality;
		double adoptedUnaffectedEdgeShelter = adoptedCenterShelter * (1.0 - sourceQuality)
				+ 0.35 * sourceQuality;
		double expectedGradient = (adoptedEdgeShelter - adoptedUnaffectedEdgeShelter)
				* (ROTOR_DISK_SURFACE_CARDINAL_WEIGHT
						+ 2.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT / Math.sqrt(2.0))
				/ edgeWeight;
		double expectedMean = (
				adoptedCenterShelter * ROTOR_DISK_SURFACE_CENTER_WEIGHT
						+ adoptedEdgeShelter * affectedDiskWeight
						+ adoptedUnaffectedEdgeShelter * (edgeWeight - affectedDiskWeight)
		) / (ROTOR_DISK_SURFACE_CENTER_WEIGHT + edgeWeight);

		assertEquals(expectedMean, qualityWeightedBlend.meanShelterFactor(), 1.0e-12);
		assertEquals(expectedGradient, qualityWeightedBlend.gradientBody().x(), 1.0e-12);
		assertEquals(0.0, qualityWeightedBlend.gradientBody().y(), 1.0e-12);
		assertEquals(0.0, qualityWeightedBlend.gradientBody().z(), 1.0e-12);
		assertTrue(runtimeObstruction < postObstructionScaled);
	}

	@Test
	void rotorDiskShelterBlendTreatsMissingEdgesAsCenterShelter() {
		AerodynamicsWindCoupling.RotorDiskShelterBlend blend = AerodynamicsWindCoupling.rotorDiskShelterBlend(
				windSampleWithLocalVoxelShelter(0.70, true, 20L),
				new Aerodynamics4McWindBridge.WindSample[] {
						null,
						windSampleWithLocalVoxelShelter(0.10, true, 200L)
				},
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(-1.0, 0.0, 0.0)
				},
				new double[] { 1.0, 1.0 },
				1.0
		);

		assertEquals(0.70, blend.meanShelterFactor(), 1.0e-9);
		assertEquals(Vec3.ZERO, blend.gradientBody());
		assertEquals(0.0, AerodynamicsWindCoupling.localVoxelShelterGradientObstruction(blend), 1.0e-9);
	}

	@Test
	void rotorDiskPressureBlendAddsEquivalentA4mcDiskGradient() {
		AerodynamicsWindCoupling.RotorDiskPressureBlend blend = AerodynamicsWindCoupling.rotorDiskPressureBlend(
				windSampleWithPressureAnomaly(100.0, true, 20L),
				new Aerodynamics4McWindBridge.WindSample[] {
						windSampleWithPressureAnomaly(900.0, true, 20L),
						windSampleWithPressureAnomaly(-300.0, true, 20L)
				},
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(-1.0, 0.0, 0.0)
				},
				new double[] { 1.0, 1.0 },
				1.0
		);

		assertEquals(700.0 / 3.0, blend.meanPressureAnomalyPascals(), 1.0e-9);
		assertEquals(new Vec3(600.0, 0.0, 0.0), blend.gradientBodyPascals());
		Vec3 equivalentWindGradient = AerodynamicsWindCoupling.localVoxelPressureGradientWindEquivalent(blend);
		assertEquals(0.9, equivalentWindGradient.x(), 1.0e-9);
		assertEquals(0.0, equivalentWindGradient.y(), 1.0e-9);
		assertEquals(0.0, equivalentWindGradient.z(), 1.0e-9);
	}

	@Test
	void rotorDiskPressureBlendAppliesSampleQualityBeforeRuntimeOutputs() {
		double sourceQuality = AerodynamicsWindCoupling.sourceQualityFactor(true, true, 0.86, 0L);
		AerodynamicsWindCoupling.RotorDiskPressureBlend rawBlend = oneSidedPressureBlend(220.0);
		AerodynamicsWindCoupling.RotorDiskPressureBlend qualityWeightedBlend = oneSidedPressureBlend(220.0, 0.86, 0L);
		Vec3 rawEquivalentWindGradient = AerodynamicsWindCoupling.localVoxelPressureGradientWindEquivalent(rawBlend);
		Vec3 qualityWeightedEquivalentWindGradient =
				AerodynamicsWindCoupling.localVoxelPressureGradientWindEquivalent(qualityWeightedBlend);

		assertEquals(rawEquivalentWindGradient.x() * sourceQuality, qualityWeightedEquivalentWindGradient.x(), 1.0e-12);
		assertEquals(0.0, qualityWeightedEquivalentWindGradient.y(), 1.0e-12);
		assertEquals(0.0, qualityWeightedEquivalentWindGradient.z(), 1.0e-12);
	}

	@Test
	void rotorDiskPressureBlendAppliesQualityBeforePressureWindSaturation() {
		double sourceQuality = AerodynamicsWindCoupling.sourceQualityFactor(true, true, 0.50, 0L);
		AerodynamicsWindCoupling.RotorDiskPressureBlend rawBlend =
				oneSidedPressureContrastBlend(-5000.0, 5000.0, 1.0, 0L);
		AerodynamicsWindCoupling.RotorDiskPressureBlend qualityWeightedBlend =
				oneSidedPressureContrastBlend(-5000.0, 5000.0, 0.50, 0L);
		Vec3 rawEquivalentWindGradient = AerodynamicsWindCoupling.localVoxelPressureGradientWindEquivalent(rawBlend);
		Vec3 qualityWeightedEquivalentWindGradient =
				AerodynamicsWindCoupling.localVoxelPressureGradientWindEquivalent(qualityWeightedBlend);
		double adoptedPressureContrast = (5000.0 - -5000.0) * sourceQuality;
		double adoptedGradient = adoptedPressureContrast
				* (ROTOR_DISK_SURFACE_CARDINAL_WEIGHT + 2.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT / Math.sqrt(2.0))
				/ (4.0 * ROTOR_DISK_SURFACE_CARDINAL_WEIGHT + 4.0 * ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT);
		double expectedQualityFirstWind = adoptedGradient / 1600.0 * 2.4;

		assertEquals(2.4, rawEquivalentWindGradient.x(), 1.0e-12);
		assertEquals(expectedQualityFirstWind, qualityWeightedEquivalentWindGradient.x(), 1.0e-12);
		assertTrue(qualityWeightedEquivalentWindGradient.x() > rawEquivalentWindGradient.x() * sourceQuality);
		assertEquals(0.0, qualityWeightedEquivalentWindGradient.y(), 1.0e-12);
		assertEquals(0.0, qualityWeightedEquivalentWindGradient.z(), 1.0e-12);
	}

	@Test
	void rotorDiskPressureBlendTreatsMissingEdgesAsCenterPressure() {
		AerodynamicsWindCoupling.RotorDiskPressureBlend blend = AerodynamicsWindCoupling.rotorDiskPressureBlend(
				windSampleWithPressureAnomaly(500.0, true, 20L),
				new Aerodynamics4McWindBridge.WindSample[] {
						null,
						windSampleWithPressureAnomaly(-500.0, true, 200L),
						windSampleWithPressureAnomaly(-500.0, false, 20L)
				},
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(-1.0, 0.0, 0.0),
						new Vec3(0.0, 0.0, 1.0)
				},
				new double[] { 1.0, 1.0, 1.0 },
				1.0
		);

		assertEquals(500.0, blend.meanPressureAnomalyPascals(), 1.0e-9);
		assertEquals(Vec3.ZERO, blend.gradientBodyPascals());
		assertEquals(Vec3.ZERO, AerodynamicsWindCoupling.localVoxelPressureGradientWindEquivalent(blend));
	}

	@Test
	void windSampleAdapterUsesSanitizedA4mcTelemetry() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(1.0, 2.0, 0.0),
				0.0,
				0.0,
				0.75,
				0.0,
				false,
				0.0,
				false,
				0.0,
				0.80,
				-1250.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				3L,
				0.0,
				0.0
		);

		assertEquals(0.456, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(sample), 1.0e-9);
		assertEquals(2.0, sample.gustSpeedMetersPerSecond(), 1.0e-9);
	}

	@Test
	void windSamplePreservesExplicitA4mcGustVectorWhenPresent() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(9.0, 0.0, 0.0),
				new Vec3(0.0, 2.0, -1.0),
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				3L,
				0.0,
				0.0
		);

		assertEquals(new Vec3(0.0, 2.0, -1.0), sample.gustVelocityWorldMetersPerSecond());
		assertEquals(Math.sqrt(5.0), sample.gustSpeedMetersPerSecond(), 1.0e-9);
		assertEquals(new Vec3(0.0, 2.0, -1.0), AerodynamicsWindCoupling.sourceWeightedGustVelocityWorldMetersPerSecond(sample));
	}

	@Test
	void a4mcGustAddsBoundedNaturalTurbulenceEnergy() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(1.0, 2.0, 0.0),
				0.0,
				0.0,
				0.75,
				0.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				3L,
				0.0,
				0.0
		);

		assertEquals(0.10, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, null), 1.0e-9);
		assertEquals(0.38, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, sample), 1.0e-9);
	}

	@Test
	void rawA4mcSourceTurbulenceIsQualityGatedIntoNaturalTurbulence() {
		Aerodynamics4McWindBridge.WindSample fresh = windSampleWithSourceTurbulence(0.80, 20L);
		Aerodynamics4McWindBridge.WindSample halfStale = windSampleWithSourceTurbulence(0.80, 100L);
		Aerodynamics4McWindBridge.WindSample stale = windSampleWithSourceTurbulence(0.80, 200L);

		assertEquals(0.80, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, fresh), 1.0e-9);
		assertEquals(0.40, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, halfStale), 1.0e-9);
		assertEquals(0.10, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, stale), 1.0e-9);
	}

	@Test
	void updraftTurbulenceDoesNotDoubleCountExplicitVerticalSourceGust() {
		Aerodynamics4McWindBridge.WindSample pureUpdraft = windSampleWithUpdraftAndSourceGust(
				4.0,
				Vec3.ZERO,
				20L
		);
		Aerodynamics4McWindBridge.WindSample verticalGustOnly = windSampleWithUpdraftAndSourceGust(
				4.0,
				new Vec3(0.0, 4.0, 0.0),
				20L
		);
		Aerodynamics4McWindBridge.WindSample meanUpdraftWithVerticalGust = windSampleWithUpdraftAndSourceGust(
				6.0,
				new Vec3(0.0, 4.0, 0.0),
				20L
		);
		Aerodynamics4McWindBridge.WindSample staleMeanUpdraftWithVerticalGust = windSampleWithUpdraftAndSourceGust(
				6.0,
				new Vec3(0.0, 4.0, 0.0),
				200L
		);

		assertEquals(0.20, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, pureUpdraft), 1.0e-9);
		assertEquals(0.36, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, verticalGustOnly), 1.0e-9);
		assertEquals(0.41, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, meanUpdraftWithVerticalGust), 1.0e-9);
		assertEquals(0.10, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, staleMeanUpdraftWithVerticalGust), 1.0e-9);
	}

	@Test
	void updraftTurbulenceDoesNotDoubleCountAdoptedMeanVerticalWind() {
		Aerodynamics4McWindBridge.WindSample meanUpdraftOnly = windSampleWithMeanUpdraftAndSourceGust(
				new Vec3(2.0, 3.0, 0.0),
				3.0,
				Vec3.ZERO,
				20L
		);
		Aerodynamics4McWindBridge.WindSample residualMeanUpdraft = windSampleWithMeanUpdraftAndSourceGust(
				new Vec3(2.0, 2.0, 0.0),
				5.0,
				Vec3.ZERO,
				20L
		);
		Aerodynamics4McWindBridge.WindSample residualMeanUpdraftWithGust = windSampleWithMeanUpdraftAndSourceGust(
				new Vec3(2.0, 2.0, 0.0),
				7.0,
				new Vec3(0.0, 2.0, 0.0),
				20L
		);

		assertEquals(0.10, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, meanUpdraftOnly), 1.0e-9);
		assertEquals(0.175, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, residualMeanUpdraft), 1.0e-9);
		assertEquals(0.305, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, residualMeanUpdraftWithGust), 1.0e-9);
	}

	@Test
	void localA4mcPressureProxyAddsBoundedNaturalTurbulenceEnergy() {
		Aerodynamics4McWindBridge.WindSample localPressure = windSampleWithPressureAnomaly(900.0, true, 20L);
		Aerodynamics4McWindBridge.WindSample cappedLocalPressure = windSampleWithPressureAnomaly(-4000.0, true, 20L);
		Aerodynamics4McWindBridge.WindSample coarsePressure = windSampleWithPressureAnomaly(1800.0, false, 20L);
		Aerodynamics4McWindBridge.WindSample stalePressure = windSampleWithPressureAnomaly(1800.0, true, 200L);

		assertEquals(0.18, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, localPressure), 1.0e-9);
		assertEquals(0.26, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, cappedLocalPressure), 1.0e-9);
		assertEquals(0.10, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, coarsePressure), 1.0e-9);
		assertEquals(0.10, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, stalePressure), 1.0e-9);
	}

	@Test
	void a4mcGustTurbulenceBoostIsCapped() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				Vec3.ZERO,
				new Vec3(30.0, 0.0, 0.0),
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				3L,
				0.0,
				0.0
		);

		assertEquals(1.5, AerodynamicsWindCoupling.naturalTurbulenceIntensity(1.45, sample), 1.0e-9);
	}

	@Test
	void staleA4mcSourceDoesNotDriveNaturalTurbulence() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				Vec3.ZERO,
				new Vec3(4.0, 0.0, 0.0),
				1.2,
				2.0,
				1.0,
				6.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				200L,
				0.0,
				0.0
		);

		assertEquals(0.12, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.12, sample), 1.0e-9);
	}

	@Test
	void sourceWeightedEffectiveWindFadesTowardFallbackAsQualityDrops() {
		Vec3 fallback = new Vec3(0.0, 0.0, 2.0);
		Aerodynamics4McWindBridge.WindSample fresh = windSampleWithEffectiveWind(new Vec3(4.0, 0.0, 0.0), 20L);
		Aerodynamics4McWindBridge.WindSample halfStale = windSampleWithEffectiveWind(new Vec3(4.0, 0.0, 0.0), 100L);
		Aerodynamics4McWindBridge.WindSample stale = windSampleWithEffectiveWind(new Vec3(4.0, 0.0, 0.0), 200L);

		assertEquals(new Vec3(4.0, 0.0, 0.0), AerodynamicsWindCoupling.sourceWeightedEffectiveWind(fallback, fresh));
		assertEquals(new Vec3(2.0, 0.0, 1.0), AerodynamicsWindCoupling.sourceWeightedEffectiveWind(fallback, halfStale));
		assertEquals(fallback, AerodynamicsWindCoupling.sourceWeightedEffectiveWind(fallback, stale));
		assertEquals(fallback, AerodynamicsWindCoupling.sourceWeightedEffectiveWind(fallback, null));
	}

	@Test
	void sourceWeightedMeanWindKeepsSourceGustOutOfBaseWind() {
		Vec3 fallback = new Vec3(0.0, 0.0, 2.0);
		Aerodynamics4McWindBridge.WindSample fresh = windSampleWithMeanAndEffectiveWind(
				new Vec3(2.0, 0.0, 0.0),
				new Vec3(6.0, 0.0, 0.0),
				20L
		);
		Aerodynamics4McWindBridge.WindSample halfStale = windSampleWithMeanAndEffectiveWind(
				new Vec3(2.0, 0.0, 0.0),
				new Vec3(6.0, 0.0, 0.0),
				100L
		);
		Aerodynamics4McWindBridge.WindSample stale = windSampleWithMeanAndEffectiveWind(
				new Vec3(2.0, 0.0, 0.0),
				new Vec3(6.0, 0.0, 0.0),
				200L
		);

		assertEquals(new Vec3(2.0, 0.0, 0.0), AerodynamicsWindCoupling.sourceWeightedMeanWind(fallback, fresh));
		assertEquals(new Vec3(1.0, 0.0, 1.0), AerodynamicsWindCoupling.sourceWeightedMeanWind(fallback, halfStale));
		assertEquals(fallback, AerodynamicsWindCoupling.sourceWeightedMeanWind(fallback, stale));
		assertEquals(fallback, AerodynamicsWindCoupling.sourceWeightedMeanWind(fallback, null));
	}

	@Test
	void rotorDiskWindBlendKeepsMissingEdgeWeightsConservative() {
		AerodynamicsWindCoupling.RotorDiskWindBlend blend = AerodynamicsWindCoupling.rotorDiskWindBlend(
				Vec3.ZERO,
				new Vec3(0.0, 1.0, 0.0),
				new Vec3[] {
						new Vec3(0.0, 4.0, 0.0),
						null
				},
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(-1.0, 0.0, 0.0)
				},
				new double[] { 1.0, 1.0 },
				1.0
		);

		assertEquals(0.0, blend.meanWindWorldMetersPerSecond().x(), 1.0e-9);
		assertEquals(4.0 / 3.0, blend.meanWindWorldMetersPerSecond().y(), 1.0e-9);
		assertEquals(0.0, blend.meanWindWorldMetersPerSecond().z(), 1.0e-9);
		assertEquals(new Vec3(2.0, 0.0, 0.0), blend.gradientBodyMetersPerSecond());
	}

	@Test
	void rotorDiskWindBlendFallsBackToCenterWhenEdgesAreMissing() {
		Vec3 centerWind = new Vec3(2.0, 0.5, -1.0);
		AerodynamicsWindCoupling.RotorDiskWindBlend blend = AerodynamicsWindCoupling.rotorDiskWindBlend(
				centerWind,
				new Vec3(0.0, 3.0, 0.0),
				new Vec3[] { null, new Vec3(Double.NaN, 1.0, 0.0) },
				new Vec3[] {
						new Vec3(1.0, 0.0, 0.0),
						new Vec3(-1.0, 0.0, 0.0)
				},
				new double[] { 0.25, 0.25 },
				0.5
		);

		assertEquals(centerWind, blend.meanWindWorldMetersPerSecond());
		assertEquals(Vec3.ZERO, blend.gradientBodyMetersPerSecond());
	}

	@Test
	void rotorDiskWindBlendFadesStaleEdgeSamplesTowardCenterWind() {
		Vec3 centerWind = Vec3.ZERO;
		Vec3 rotorAxis = new Vec3(0.0, 1.0, 0.0);
		Vec3[] directions = {
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(-1.0, 0.0, 0.0)
		};
		double[] weights = { 1.0, 1.0 };

		AerodynamicsWindCoupling.RotorDiskWindBlend freshEdges = AerodynamicsWindCoupling.rotorDiskWindBlend(
				centerWind,
				rotorAxis,
				new Aerodynamics4McWindBridge.WindSample[] {
						windSampleWithMeanAndEffectiveWind(new Vec3(0.0, 4.0, 0.0), new Vec3(0.0, 6.0, 0.0), 20L),
						windSampleWithMeanAndEffectiveWind(new Vec3(0.0, -4.0, 0.0), new Vec3(0.0, -6.0, 0.0), 20L)
				},
				directions,
				weights,
				1.0
		);
		AerodynamicsWindCoupling.RotorDiskWindBlend halfStaleEdge = AerodynamicsWindCoupling.rotorDiskWindBlend(
				centerWind,
				rotorAxis,
				new Aerodynamics4McWindBridge.WindSample[] {
						windSampleWithMeanAndEffectiveWind(new Vec3(0.0, 4.0, 0.0), new Vec3(0.0, 6.0, 0.0), 20L),
						windSampleWithMeanAndEffectiveWind(new Vec3(0.0, -4.0, 0.0), new Vec3(0.0, -6.0, 0.0), 100L)
				},
				directions,
				weights,
				1.0
		);
		AerodynamicsWindCoupling.RotorDiskWindBlend staleEdge = AerodynamicsWindCoupling.rotorDiskWindBlend(
				centerWind,
				rotorAxis,
				new Aerodynamics4McWindBridge.WindSample[] {
						windSampleWithMeanAndEffectiveWind(new Vec3(0.0, 4.0, 0.0), new Vec3(0.0, 6.0, 0.0), 20L),
						windSampleWithMeanAndEffectiveWind(new Vec3(0.0, -4.0, 0.0), new Vec3(0.0, -6.0, 0.0), 200L)
				},
				directions,
				weights,
				1.0
		);

		assertEquals(new Vec3(4.0, 0.0, 0.0), freshEdges.gradientBodyMetersPerSecond());
		assertEquals(new Vec3(3.0, 0.0, 0.0), halfStaleEdge.gradientBodyMetersPerSecond());
		assertEquals(new Vec3(2.0, 0.0, 0.0), staleEdge.gradientBodyMetersPerSecond());
	}

	@Test
	void rotorDiskWindBlendUsesMeanWindAndLeavesCoherentGustSeparated() {
		Aerodynamics4McWindBridge.WindSample gustyEdge = new Aerodynamics4McWindBridge.WindSample(
				true,
				new Vec3(4.0, 1.0, 0.0),
				new Vec3(9.0, 3.0, 0.0),
				new Vec3(5.0, 2.0, 0.0),
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				20L,
				0.0,
				0.0
		);

		AerodynamicsWindCoupling.RotorDiskWindBlend blend = AerodynamicsWindCoupling.rotorDiskWindBlend(
				new Vec3(2.0, 0.0, 0.0),
				new Vec3(0.0, 1.0, 0.0),
				new Aerodynamics4McWindBridge.WindSample[] { gustyEdge },
				new Vec3[] { new Vec3(1.0, 0.0, 0.0) },
				new double[] { 1.0 },
				1.0
		);

		assertEquals(new Vec3(3.0, 0.5, 0.0), blend.meanWindWorldMetersPerSecond());
		assertEquals(1.0, blend.gradientBodyMetersPerSecond().x(), 1.0e-9);
	}

	@Test
	void sourceWeightedAtmosphereScalarsUseSourceQuality() {
		Aerodynamics4McWindBridge.WindSample sample = windSampleWithAtmosphere(100L);

		assertEquals(500.0, AerodynamicsWindCoupling.sourceWeightedPressureAnomalyPascals(sample), 1.0e-9);
		assertEquals(30.0, AerodynamicsWindCoupling.sourceWeightedTemperatureCelsius(20.0, sample), 1.0e-9);
		assertEquals(0.4, AerodynamicsWindCoupling.sourceWeightedHumidity(sample), 1.0e-9);
	}

	@Test
	void sourceWeightedWindSpeedsUseSourceQuality() {
		Aerodynamics4McWindBridge.WindSample fresh = windSampleWithMeanAndEffectiveWind(
				new Vec3(3.0, 0.0, 4.0),
				new Vec3(6.0, 0.0, 8.0),
				20L
		);
		Aerodynamics4McWindBridge.WindSample halfStale = windSampleWithMeanAndEffectiveWind(
				new Vec3(3.0, 0.0, 4.0),
				new Vec3(6.0, 0.0, 8.0),
				100L
		);
		Aerodynamics4McWindBridge.WindSample stale = windSampleWithMeanAndEffectiveWind(
				new Vec3(3.0, 0.0, 4.0),
				new Vec3(6.0, 0.0, 8.0),
				200L
		);

		assertEquals(5.0, AerodynamicsWindCoupling.sourceWeightedMeanSpeedMetersPerSecond(fresh), 1.0e-9);
		assertEquals(10.0, AerodynamicsWindCoupling.sourceWeightedEffectiveSpeedMetersPerSecond(fresh), 1.0e-9);
		assertEquals(5.0, AerodynamicsWindCoupling.sourceWeightedGustSpeedMetersPerSecond(fresh), 1.0e-9);
		assertEquals(new Vec3(3.0, 0.0, 4.0), AerodynamicsWindCoupling.sourceWeightedGustVelocityWorldMetersPerSecond(fresh));
		assertEquals(2.5, AerodynamicsWindCoupling.sourceWeightedMeanSpeedMetersPerSecond(halfStale), 1.0e-9);
		assertEquals(5.0, AerodynamicsWindCoupling.sourceWeightedEffectiveSpeedMetersPerSecond(halfStale), 1.0e-9);
		assertEquals(2.5, AerodynamicsWindCoupling.sourceWeightedGustSpeedMetersPerSecond(halfStale), 1.0e-9);
		assertEquals(new Vec3(1.5, 0.0, 2.0), AerodynamicsWindCoupling.sourceWeightedGustVelocityWorldMetersPerSecond(halfStale));
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedMeanSpeedMetersPerSecond(stale), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedEffectiveSpeedMetersPerSecond(stale), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedGustSpeedMetersPerSecond(stale), 1.0e-9);
		assertEquals(Vec3.ZERO, AerodynamicsWindCoupling.sourceWeightedGustVelocityWorldMetersPerSecond(stale));
	}

	@Test
	void staleAtmosphereScalarsFallBack() {
		Aerodynamics4McWindBridge.WindSample sample = windSampleWithAtmosphere(200L);

		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedPressureAnomalyPascals(sample), 1.0e-9);
		assertEquals(20.0, AerodynamicsWindCoupling.sourceWeightedTemperatureCelsius(20.0, sample), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedHumidity(sample), 1.0e-9);
	}

	@Test
	void untrustedA4mcFlowRemainsAuditableButHasZeroPhysicsQuality() {
		Aerodynamics4McWindBridge.WindSample sample = new Aerodynamics4McWindBridge.WindSample(
				true,
				new Vec3(2.0, 0.0, 0.0),
				new Vec3(6.0, 0.0, 0.0),
				new Vec3(3.0, 0.0, 1.0),
				0.45,
				0.80,
				0.60,
				1.40,
				true,
				35.0,
				true,
				0.72,
				0.73,
				-900.0,
				false,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				12L,
				-0.40,
				0.75
		);
		Vec3 fallbackWind = new Vec3(1.0, 0.0, 2.0);

		assertTrue(sample.hasFlow());
		assertFalse(sample.trustedForGameplay());
		assertEquals(0.73, sample.confidence(), 1.0e-9);
		assertEquals("l2", sample.sourceLevel());
		assertEquals(0.0, AerodynamicsWindCoupling.sourceQualityFactor(sample), 1.0e-9);
		assertEquals(fallbackWind, AerodynamicsWindCoupling.sourceWeightedEffectiveWind(fallbackWind, sample));
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedPressureAnomalyPascals(sample), 1.0e-9);
		assertEquals(20.0, AerodynamicsWindCoupling.sourceWeightedTemperatureCelsius(20.0, sample), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedHumidity(sample), 1.0e-9);
		assertEquals(0.0, AerodynamicsWindCoupling.sourceWeightedGustSpeedMetersPerSecond(sample), 1.0e-9);
		assertEquals(Vec3.ZERO, AerodynamicsWindCoupling.sourceWeightedGustVelocityWorldMetersPerSecond(sample));
		assertEquals(0.10, AerodynamicsWindCoupling.naturalTurbulenceIntensity(0.10, sample), 1.0e-9);
		assertEquals(1.0, AerodynamicsWindCoupling.localVoxelObstacleResidualFactor(sample), 1.0e-9);
	}

	private static Map<String, Double> localVoxelPacketSummary() throws IOException {
		return packetSummary("docs/data/a4mc_local_voxel_coupling_packet.csv", "a4mc_local_voxel_packet_summary");
	}

	private static Map<String, Double> sourceQualityPacketSummary() throws IOException {
		return packetSummary("docs/data/a4mc_source_quality_response_packet.csv", "a4mc_source_quality_packet_summary");
	}

	private static DroneEnvironment a4mcVentilationEnvironment(
			double shelterFactor,
			boolean localVoxelFlow,
			double confidence,
			long freshnessAgeTicks,
			double[] rotorLocalVoxelObstacleResiduals,
			double[] rotorA4mcShelterObstructions
	) {
		return new DroneEnvironment(
				Vec3.ZERO,
				1.0,
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
				25.0,
				null,
				null,
				rotorA4mcShelterObstructions,
				DroneEnvironment.WIND_SOURCE_AERODYNAMICS4MC,
				true,
				confidence,
				0.0,
				0.0,
				0.0,
				shelterFactor,
				0.0,
				localVoxelFlow,
				"l2",
				"server_authoritative",
				freshnessAgeTicks,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				0.0,
				0.0,
				Vec3.ZERO,
				null,
				null,
				null,
				null,
				rotorLocalVoxelObstacleResiduals,
				null,
				null
		);
	}

	private static double invokeA4mcLocalVoxelVentilationEfficiency(DroneEnvironment environment, int rotorIndex)
			throws ReflectiveOperationException {
		Method method = DronePhysics.class.getDeclaredMethod(
				"a4mcLocalVoxelVentilationEfficiency",
				DroneEnvironment.class,
				int.class
		);
		method.setAccessible(true);
		try {
			return (double) method.invoke(null, environment, rotorIndex);
		} catch (InvocationTargetException exception) {
			throw new AssertionError(exception.getCause());
		}
	}

	private static double invokeA4mcPackVentilationEfficiency(DroneEnvironment environment, int rotorCount)
			throws ReflectiveOperationException {
		Method method = DronePhysics.class.getDeclaredMethod(
				"a4mcPackVentilationEfficiency",
				DroneEnvironment.class,
				int.class
		);
		method.setAccessible(true);
		try {
			return (double) method.invoke(null, environment, rotorCount);
		} catch (InvocationTargetException exception) {
			throw new AssertionError(exception.getCause());
		}
	}

	private static Map<String, Double> packetSummary(String packetPath, String summaryRowType) throws IOException {
		Path packet = findRepoRoot().resolve(packetPath);
		List<String> lines = Files.readAllLines(packet);
		assertFalse(lines.isEmpty(), "Packet is empty: " + packet);
		List<String> header = parseCsvLine(lines.get(0));
		int rowTypeColumn = columnIndex(header, "row_type");
		int metricColumn = columnIndex(header, "metric");
		int valueColumn = columnIndex(header, "value");
		Map<String, Double> summary = new HashMap<>();
		for (int i = 1; i < lines.size(); i++) {
			List<String> columns = parseCsvLine(lines.get(i));
			assertEquals(header.size(), columns.size(), "CSV column mismatch at " + packet + ":" + (i + 1));
			if (summaryRowType.equals(columns.get(rowTypeColumn))) {
				summary.put(columns.get(metricColumn), Double.parseDouble(columns.get(valueColumn)));
			}
		}
		assertFalse(summary.isEmpty(), "Packet has no " + summaryRowType + " rows: " + packet);
		return summary;
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath();
		for (Path path = current; path != null; path = path.getParent()) {
			if (Files.isRegularFile(path.resolve("settings.gradle"))
					&& Files.isRegularFile(path.resolve("docs/data/a4mc_local_voxel_coupling_packet.csv"))) {
				return path;
			}
		}
		throw new AssertionError("Could not locate repository root from " + current);
	}

	private static List<String> parseCsvLine(String line) {
		List<String> values = new ArrayList<>();
		StringBuilder value = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < line.length(); i++) {
			char character = line.charAt(i);
			if (quoted) {
				if (character == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						value.append('"');
						i++;
					} else {
						quoted = false;
					}
				} else {
					value.append(character);
				}
			} else if (character == '"') {
				quoted = true;
			} else if (character == ',') {
				values.add(value.toString());
				value.setLength(0);
			} else {
				value.append(character);
			}
		}
		values.add(value.toString());
		return values;
	}

	private static int columnIndex(List<String> header, String name) {
		int index = header.indexOf(name);
		assertTrue(index >= 0, "Missing CSV column " + name);
		return index;
	}

	private static double summaryMetric(Map<String, Double> summary, String metric) {
		Double value = summary.get(metric);
		assertTrue(value != null, "Missing packet summary metric " + metric);
		return value;
	}

	private static double staticPortPressureErrorMeters(
			double adoptedPressureAnomalyPascals,
			double shelterFactor,
			double localVoxelCoverage,
			double shelterObstruction
	) {
		double exposure = clamp(0.35 + 0.35 * shelterFactor + 0.18 * localVoxelCoverage + 0.12 * shelterObstruction, 0.25, 1.0);
		double density = 1.225 * DroneEnvironment.pressureAnomalyAirDensityMultiplier(adoptedPressureAnomalyPascals);
		return clamp(-adoptedPressureAnomalyPascals / (density * 9.80665) * exposure, -0.65, 0.65);
	}

	private static double clamp(double value, double lower, double upper) {
		return Math.min(Math.max(value, lower), upper);
	}

	private static AerodynamicsWindCoupling.RotorDiskPressureBlend oneSidedPressureBlend(double edgePressureDeltaPascals) {
		return oneSidedPressureBlend(edgePressureDeltaPascals, 1.0, 0L);
	}

	private static AerodynamicsWindCoupling.RotorDiskPressureBlend oneSidedPressureBlend(
			double edgePressureDeltaPascals,
			double confidence,
			long freshnessAgeTicks
	) {
		return AerodynamicsWindCoupling.rotorDiskPressureBlend(
				windSampleWithPressureAnomaly(0.0, true, freshnessAgeTicks, confidence),
				oneSidedPressureSamples(edgePressureDeltaPascals, confidence, freshnessAgeTicks),
				rotorDiskSampleDirectionsBody(),
				rotorDiskSampleWeights(),
				ROTOR_DISK_SURFACE_CENTER_WEIGHT
		);
	}

	private static AerodynamicsWindCoupling.RotorDiskPressureBlend oneSidedPressureContrastBlend(
			double centerPressurePascals,
			double edgePressurePascals,
			double confidence,
			long freshnessAgeTicks
	) {
		return AerodynamicsWindCoupling.rotorDiskPressureBlend(
				windSampleWithPressureAnomaly(centerPressurePascals, true, freshnessAgeTicks, confidence),
				oneSidedPressureContrastSamples(centerPressurePascals, edgePressurePascals, confidence, freshnessAgeTicks),
				rotorDiskSampleDirectionsBody(),
				rotorDiskSampleWeights(),
				ROTOR_DISK_SURFACE_CENTER_WEIGHT
		);
	}

	private static Aerodynamics4McWindBridge.WindSample[] oneSidedPressureSamples(double edgePressureDeltaPascals) {
		return oneSidedPressureSamples(edgePressureDeltaPascals, 1.0, 0L);
	}

	private static Aerodynamics4McWindBridge.WindSample[] oneSidedPressureSamples(
			double edgePressureDeltaPascals,
			double confidence,
			long freshnessAgeTicks
	) {
		Aerodynamics4McWindBridge.WindSample[] samples = centerPressureSamples(confidence, freshnessAgeTicks);
		samples[0] = windSampleWithPressureAnomaly(edgePressureDeltaPascals, true, freshnessAgeTicks, confidence);
		samples[4] = windSampleWithPressureAnomaly(edgePressureDeltaPascals, true, freshnessAgeTicks, confidence);
		samples[5] = windSampleWithPressureAnomaly(edgePressureDeltaPascals, true, freshnessAgeTicks, confidence);
		return samples;
	}

	private static Aerodynamics4McWindBridge.WindSample[] oneSidedPressureContrastSamples(
			double centerPressurePascals,
			double edgePressurePascals,
			double confidence,
			long freshnessAgeTicks
	) {
		Aerodynamics4McWindBridge.WindSample[] samples = centerPressureSamples(
				centerPressurePascals,
				confidence,
				freshnessAgeTicks
		);
		samples[0] = windSampleWithPressureAnomaly(edgePressurePascals, true, freshnessAgeTicks, confidence);
		samples[4] = windSampleWithPressureAnomaly(edgePressurePascals, true, freshnessAgeTicks, confidence);
		samples[5] = windSampleWithPressureAnomaly(edgePressurePascals, true, freshnessAgeTicks, confidence);
		return samples;
	}

	private static Aerodynamics4McWindBridge.WindSample[] centerPressureSamples() {
		return centerPressureSamples(1.0, 0L);
	}

	private static Aerodynamics4McWindBridge.WindSample[] centerPressureSamples(double confidence, long freshnessAgeTicks) {
		return centerPressureSamples(0.0, confidence, freshnessAgeTicks);
	}

	private static Aerodynamics4McWindBridge.WindSample[] centerPressureSamples(
			double pressureAnomalyPascals,
			double confidence,
			long freshnessAgeTicks
	) {
		Aerodynamics4McWindBridge.WindSample[] samples = new Aerodynamics4McWindBridge.WindSample[8];
		for (int i = 0; i < samples.length; i++) {
			samples[i] = windSampleWithPressureAnomaly(pressureAnomalyPascals, true, freshnessAgeTicks, confidence);
		}
		return samples;
	}

	private static AerodynamicsWindCoupling.RotorDiskShelterBlend oneSidedShelterBlend(
			double centerShelter,
			double edgeShelterDelta
	) {
		return oneSidedShelterBlend(centerShelter, edgeShelterDelta, 1.0, 0L);
	}

	private static AerodynamicsWindCoupling.RotorDiskShelterBlend oneSidedShelterBlend(
			double centerShelter,
			double edgeShelterDelta,
			double confidence,
			long freshnessAgeTicks
	) {
		return AerodynamicsWindCoupling.rotorDiskShelterBlend(
				windSampleWithLocalVoxelShelter(centerShelter, true, freshnessAgeTicks, confidence),
				oneSidedShelterSamples(centerShelter, edgeShelterDelta, confidence, freshnessAgeTicks),
				rotorDiskSampleDirectionsBody(),
				rotorDiskSampleWeights(),
				ROTOR_DISK_SURFACE_CENTER_WEIGHT
		);
	}

	private static Aerodynamics4McWindBridge.WindSample[] oneSidedShelterSamples(
			double centerShelter,
			double edgeShelterDelta
	) {
		return oneSidedShelterSamples(centerShelter, edgeShelterDelta, 1.0, 0L);
	}

	private static Aerodynamics4McWindBridge.WindSample[] oneSidedShelterSamples(
			double centerShelter,
			double edgeShelterDelta,
			double confidence,
			long freshnessAgeTicks
	) {
		Aerodynamics4McWindBridge.WindSample[] samples = centerShelterSamples(
				centerShelter,
				confidence,
				freshnessAgeTicks
		);
		double edgeShelter = centerShelter + edgeShelterDelta;
		samples[0] = windSampleWithLocalVoxelShelter(edgeShelter, true, freshnessAgeTicks, confidence);
		samples[4] = windSampleWithLocalVoxelShelter(edgeShelter, true, freshnessAgeTicks, confidence);
		samples[5] = windSampleWithLocalVoxelShelter(edgeShelter, true, freshnessAgeTicks, confidence);
		return samples;
	}

	private static Aerodynamics4McWindBridge.WindSample[] centerShelterSamples(double centerShelter) {
		return centerShelterSamples(centerShelter, 1.0, 0L);
	}

	private static Aerodynamics4McWindBridge.WindSample[] centerShelterSamples(
			double centerShelter,
			double confidence,
			long freshnessAgeTicks
	) {
		Aerodynamics4McWindBridge.WindSample[] samples = new Aerodynamics4McWindBridge.WindSample[8];
		for (int i = 0; i < samples.length; i++) {
			samples[i] = windSampleWithLocalVoxelShelter(centerShelter, true, freshnessAgeTicks, confidence);
		}
		return samples;
	}

	private static Vec3[] rotorDiskSampleDirectionsBody() {
		return new Vec3[] {
				new Vec3(1.0, 0.0, 0.0),
				new Vec3(-1.0, 0.0, 0.0),
				new Vec3(0.0, 0.0, 1.0),
				new Vec3(0.0, 0.0, -1.0),
				new Vec3(1.0, 0.0, 1.0).normalized(),
				new Vec3(1.0, 0.0, -1.0).normalized(),
				new Vec3(-1.0, 0.0, 1.0).normalized(),
				new Vec3(-1.0, 0.0, -1.0).normalized()
		};
	}

	private static double[] rotorDiskSampleWeights() {
		return new double[] {
				ROTOR_DISK_SURFACE_CARDINAL_WEIGHT,
				ROTOR_DISK_SURFACE_CARDINAL_WEIGHT,
				ROTOR_DISK_SURFACE_CARDINAL_WEIGHT,
				ROTOR_DISK_SURFACE_CARDINAL_WEIGHT,
				ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT,
				ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT,
				ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT,
				ROTOR_DISK_SURFACE_DIAGONAL_WEIGHT
		};
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithEffectiveWind(Vec3 effectiveWind, long freshnessAgeTicks) {
		return windSampleWithMeanAndEffectiveWind(Vec3.ZERO, effectiveWind, freshnessAgeTicks);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithSourceTurbulence(double turbulenceIntensity, long freshnessAgeTicks) {
		return new Aerodynamics4McWindBridge.WindSample(
				true,
				Vec3.ZERO,
				Vec3.ZERO,
				turbulenceIntensity,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				freshnessAgeTicks,
				0.0,
				0.0
		);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithUpdraftAndSourceGust(
			double updraftMetersPerSecond,
			Vec3 sourceGustVelocity,
			long freshnessAgeTicks
	) {
		return windSampleWithMeanUpdraftAndSourceGust(Vec3.ZERO, updraftMetersPerSecond, sourceGustVelocity, freshnessAgeTicks);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithMeanUpdraftAndSourceGust(
			Vec3 meanVelocity,
			double updraftMetersPerSecond,
			Vec3 sourceGustVelocity,
			long freshnessAgeTicks
	) {
		Vec3 safeMeanVelocity = meanVelocity == null ? Vec3.ZERO : meanVelocity;
		Vec3 safeSourceGust = sourceGustVelocity == null ? Vec3.ZERO : sourceGustVelocity;
		return new Aerodynamics4McWindBridge.WindSample(
				true,
				safeMeanVelocity,
				safeMeanVelocity.add(safeSourceGust),
				safeSourceGust,
				0.0,
				0.0,
				0.0,
				updraftMetersPerSecond,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				freshnessAgeTicks,
				0.0,
				0.0
		);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithMeanAndEffectiveWind(
			Vec3 meanWind,
			Vec3 effectiveWind,
			long freshnessAgeTicks
	) {
		return new Aerodynamics4McWindBridge.WindSample(
				true,
				meanWind,
				effectiveWind,
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				1.0,
				0.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				freshnessAgeTicks,
				0.0,
				0.0
		);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithAtmosphere(long freshnessAgeTicks) {
		return new Aerodynamics4McWindBridge.WindSample(
				true,
				Vec3.ZERO,
				new Vec3(4.0, 0.0, 0.0),
				0.0,
				0.0,
				0.0,
				0.0,
				true,
				40.0,
				true,
				0.8,
				1.0,
				1000.0,
				true,
				true,
				"L2",
				"SERVER_AUTHORITATIVE",
				freshnessAgeTicks,
				0.0,
				0.0
		);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithPressureAnomaly(
			double pressureAnomalyPascals,
			boolean localVoxelFlow,
			long freshnessAgeTicks
	) {
		return windSampleWithPressureAnomaly(pressureAnomalyPascals, localVoxelFlow, freshnessAgeTicks, 1.0);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithPressureAnomaly(
			double pressureAnomalyPascals,
			boolean localVoxelFlow,
			long freshnessAgeTicks,
			double confidence
	) {
		return new Aerodynamics4McWindBridge.WindSample(
				true,
				Vec3.ZERO,
				Vec3.ZERO,
				0.0,
				0.0,
				0.0,
				0.0,
				false,
				0.0,
				false,
				0.0,
				confidence,
				pressureAnomalyPascals,
				true,
				localVoxelFlow,
				localVoxelFlow ? "L2" : "L1",
				"SERVER_AUTHORITATIVE",
				freshnessAgeTicks,
				0.0,
				0.0
		);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithLocalVoxelShelter(
			double shelterFactor,
			boolean localVoxelFlow,
			long freshnessAgeTicks
	) {
		return windSampleWithLocalVoxelShelter(shelterFactor, localVoxelFlow, freshnessAgeTicks, 1.0);
	}

	private static Aerodynamics4McWindBridge.WindSample windSampleWithLocalVoxelShelter(
			double shelterFactor,
			boolean localVoxelFlow,
			long freshnessAgeTicks,
			double confidence
	) {
		return new Aerodynamics4McWindBridge.WindSample(
				true,
				Vec3.ZERO,
				Vec3.ZERO,
				0.0,
				0.0,
				shelterFactor,
				0.0,
				false,
				0.0,
				false,
				0.0,
				confidence,
				0.0,
				true,
				localVoxelFlow,
				"L2",
				"SERVER_AUTHORITATIVE",
				freshnessAgeTicks,
				0.0,
				0.0
		);
	}
}
