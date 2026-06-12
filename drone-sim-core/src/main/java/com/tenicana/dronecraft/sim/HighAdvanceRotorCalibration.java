package com.tenicana.dronecraft.sim;

public final class HighAdvanceRotorCalibration {
	private static final double EPSILON = 1.0e-12;
	public static final String APC_HIGH_ADVANCE_SOURCE_ID = "APC-High-J-Axial-Propeller-Packet";
	public static final String APC_HIGH_ADVANCE_CAVEAT =
			"APC rows are axial propeller predictions; use NASA/UMD rotor sources for edgewise high-mu stall.";
	public static final String MEJZLIK_WIND_TUNNEL_SOURCE_ID = "Mejzlik-Wind-Tunnel-Prop-Packet";
	public static final String MEJZLIK_WIND_TUNNEL_CAVEAT =
			"AirShaper/Mejzlik rows are axial propeller wind-tunnel table values; keep them separate from FPV 5-inch edgewise high-mu data.";
	public static final int SELECTED_APC_PROPELLER_COUNT = 7;
	public static final int SELECTED_APC_ROW_COUNT = 7_590;
	public static final double SELECTED_APC_MAX_ADVANCE_RATIO_J = 2.4664;
	public static final double SELECTED_APC_MAX_EQUIVALENT_PROJECT_MU = 0.785079503284;
	public static final double UIUC_5IN_FORWARD_FLOW_MAX_ADVANCE_RATIO_J = 0.571;
	public static final double UIUC_5IN_FORWARD_FLOW_MAX_PROJECT_MU = 0.181754945011;
	public static final double CURRENT_LIFT_DISSYMMETRY_START_PROJECT_MU = 0.08;
	public static final double CURRENT_LIFT_DISSYMMETRY_START_EQUIVALENT_J = 0.251327412287;
	public static final double CURRENT_LIFT_DISSYMMETRY_END_PROJECT_MU = 0.34;
	public static final double CURRENT_LIFT_DISSYMMETRY_END_EQUIVALENT_J = 1.06814150222;
	public static final double CURRENT_RETREATING_STALL_START_PROJECT_MU = 0.42;
	public static final double CURRENT_RETREATING_STALL_START_EQUIVALENT_J = 1.31946891451;
	public static final double CURRENT_HIGH_ADVANCE_LOSS_START_PROJECT_MU = 0.46;
	public static final double CURRENT_HIGH_ADVANCE_LOSS_START_EQUIVALENT_J = 1.44513262065;
	public static final double CURRENT_RETREATING_STALL_END_PROJECT_MU = 0.82;
	public static final double CURRENT_RETREATING_STALL_END_EQUIVALENT_J = 2.57610597594;
	public static final int MEJZLIK_PACKET_METRIC_ROW_COUNT = 129;
	public static final int MEJZLIK_SOURCE_INVENTORY_ROW_COUNT = 6;
	public static final int MEJZLIK_TABLE_VALUE_ROW_COUNT = 52;
	public static final int MEJZLIK_MODEL_VS_TUNNEL_ROW_COUNT = 60;
	public static final int MEJZLIK_SUMMARY_ROW_COUNT = 10;
	public static final double MEJZLIK_WIND_TUNNEL_RPM = 4900.0;
	public static final double MEJZLIK_WIND_TUNNEL_SPEED_MAX_METERS_PER_SECOND = 35.0;
	public static final double MEJZLIK_CFD_MAX_SPEED_METERS_PER_SECOND = 60.0;
	public static final double MEJZLIK_CFD_MAX_ADVANCE_RATIO_J = 1.38;
	public static final int MEJZLIK_PUBLISHED_TABLE_J_COUNT = 4;
	public static final double MEJZLIK_WIND_TUNNEL_CT_ZERO_CROSSING_J = 0.784304932735;
	public static final double MEJZLIK_WIND_TUNNEL_CT_ZERO_CROSSING_PROJECT_MU = 0.249652013872;

	private static final ApcPropellerReference APC_5X4E_3BLADE = new ApcPropellerReference(
			"APC_5x4E_3blade",
			"https://www.apcprop.com/files/PER3_5x4E-3.dat",
			"5-inch three-blade axial reference",
			5.0,
			4.0,
			0.8,
			3,
			1_057,
			36,
			0.9593,
			0.305354673816,
			0.9585,
			0.305100025907,
			0.9545,
			0.7062
	);
	private static final ApcPropellerReference APC_5P1X5E_3BLADE = new ApcPropellerReference(
			"APC_5.1x5.0E_3blade",
			"https://www.apcprop.com/files/PER3_51x50E-3.dat",
			"5.1-inch three-blade FPV-adjacent reference",
			5.1,
			5.0,
			0.980392156863,
			3,
			1_138,
			38,
			1.1302,
			0.359753833365,
			1.1295,
			0.359531016445,
			1.1241,
			0.7805
	);
	private static final ApcPropellerReference APC_5X11E = new ApcPropellerReference(
			"APC_5x11E",
			"https://www.apcprop.com/files/PER3_5x11E.dat",
			"5-inch extreme-pitch high-J coverage",
			5.0,
			11.0,
			2.2,
			2,
			1_080,
			36,
			2.4664,
			0.785079503284,
			2.4548,
			0.781387108604,
			2.4569,
			0.8623
	);

	private HighAdvanceRotorCalibration() {
	}

	public record ApcPropellerReference(
			String propellerId,
			String sourceUrl,
			String role,
			double diameterInches,
			double pitchInches,
			double pitchToDiameterRatio,
			int bladeCount,
			int rowCount,
			int rpmCount,
			double maxAdvanceRatioJ,
			double maxEquivalentProjectMu,
			double highestPositiveCtAdvanceRatioJ,
			double highestPositiveCtEquivalentProjectMu,
			double nearestZeroCtAdvanceRatioJ,
			double maxEfficiency
	) {
	}

	public record AdvancePointAudit(
			String pointId,
			ApcPropellerReference apcReference,
			double targetEquivalentAdvanceRatioJ,
			double packetProjectMu,
			double currentRotorAdvanceRatio,
			double currentEquivalentAdvanceRatioJ,
			boolean targetWithinApcFileRange,
			double apcCt,
			double apcCp,
			double apcCtOverStaticCt,
			double apcCpOverStaticCp,
			double currentThrustScale,
			double currentPowerScale,
			double currentTorquePerThrustScale,
			double currentThrustScaleOverApcCtRatio,
			double currentPowerScaleOverApcCpRatio
	) {
	}

	public record MejzlikWindTunnelPoint(
			String pointId,
			double advanceRatioJ,
			double codeEquivalentProjectMu,
			double windTunnelCt,
			double windTunnelCp,
			double windTunnelEfficiency,
			double windTunnelCtOverJ02,
			double windTunnelCpOverJ02,
			boolean windTunnelPositiveThrust,
			double currentRotorAdvanceRatio,
			double currentEquivalentAdvanceRatioJ,
			double currentThrustScale,
			double currentPowerScale,
			double currentTorquePerThrustScale,
			double currentThrustScaleOverPositiveWindTunnelCtRatio,
			double currentPowerScaleOverWindTunnelCpRatio
	) {
	}

	public record MejzlikWindTunnelAudit(
			String sourceId,
			String caveat,
			int packetMetricRowCount,
			int sourceInventoryRowCount,
			int tableValueRowCount,
			int modelVsTunnelRowCount,
			int summaryRowCount,
			double windTunnelRpm,
			double windTunnelSpeedMaxMetersPerSecond,
			double cfdMaxSpeedMetersPerSecond,
			double cfdMaxAdvanceRatioJ,
			int publishedTableJCount,
			double ctZeroCrossingAdvanceRatioJ,
			double ctZeroCrossingProjectMu,
			double racingHoverSpeedAtCtZeroMetersPerSecond,
			double currentLiftDissymmetryEndAdvanceRatioJ,
			double currentRetreatingStallStartAdvanceRatioJ,
			double currentHighAdvanceLossStartAdvanceRatioJ,
			double currentLiftDissymmetryEndOverCtZeroJ,
			double currentRetreatingStallStartOverCtZeroJ,
			double currentHighAdvanceLossStartOverCtZeroJ,
			double racingHoverSpeedAtCurrentHighAdvanceLossStartMetersPerSecond,
			MejzlikWindTunnelPoint lowAdvancePoint,
			MejzlikWindTunnelPoint midAdvancePoint,
			MejzlikWindTunnelPoint highMeasuredPoint,
			MejzlikWindTunnelPoint windmillingBoundaryPoint
	) {
	}

	public record HighAdvanceAudit(
			String sourceId,
			String caveat,
			int selectedApcPropellerCount,
			int selectedApcRowCount,
			double selectedApcMaxAdvanceRatioJ,
			double selectedApcMaxEquivalentProjectMu,
			ApcPropellerReference conventionalThreeBladeReference,
			ApcPropellerReference fpvAdjacentThreeBladeReference,
			ApcPropellerReference extremeHighPitchReference,
			double representativeRotorRadiusMeters,
			double representativeRotorPitchToDiameterRatio,
			int representativeRotorBladeCount,
			AdvancePointAudit uiucMeasuredRangeMax,
			AdvancePointAudit fpvAdjacentLiftDissymmetryEnd,
			AdvancePointAudit extremePitchRetreatingStallStart,
			AdvancePointAudit extremePitchHighAdvanceLossStart,
			AdvancePointAudit extremePitchRetreatingStallEnd,
			MejzlikWindTunnelAudit mejzlikWindTunnelAudit
	) {
	}

	public static HighAdvanceAudit audit(DroneConfig config) {
		if (config == null || config.rotors().isEmpty()) {
			throw new IllegalArgumentException("config must include at least one rotor.");
		}

		RotorSpec rotor = config.rotors().get(0);
		return new HighAdvanceAudit(
				APC_HIGH_ADVANCE_SOURCE_ID,
				APC_HIGH_ADVANCE_CAVEAT,
				SELECTED_APC_PROPELLER_COUNT,
				SELECTED_APC_ROW_COUNT,
				SELECTED_APC_MAX_ADVANCE_RATIO_J,
				SELECTED_APC_MAX_EQUIVALENT_PROJECT_MU,
				APC_5X4E_3BLADE,
				APC_5P1X5E_3BLADE,
				APC_5X11E,
				rotor.radiusMeters(),
				rotor.bladePitchToDiameterRatio(),
				rotor.bladeCount(),
				advancePoint(
						rotor,
						"uiuc_5in_forward_flow_max",
						APC_5P1X5E_3BLADE,
						UIUC_5IN_FORWARD_FLOW_MAX_ADVANCE_RATIO_J,
						UIUC_5IN_FORWARD_FLOW_MAX_PROJECT_MU,
						true,
						0.1687,
						0.1708,
						0.711514129059,
						1.03515151515
				),
				advancePoint(
						rotor,
						"fpv_5p1x5_lift_dissymmetry_end",
						APC_5P1X5E_3BLADE,
						CURRENT_LIFT_DISSYMMETRY_END_EQUIVALENT_J,
						CURRENT_LIFT_DISSYMMETRY_END_PROJECT_MU,
						true,
						0.0190,
						0.0504,
						0.0793319415449,
						0.342158859470
				),
				advancePoint(
						rotor,
						"extreme_5x11_retreating_stall_start",
						APC_5X11E,
						CURRENT_RETREATING_STALL_START_EQUIVALENT_J,
						CURRENT_RETREATING_STALL_START_PROJECT_MU,
						true,
						0.1566,
						0.2941,
						1.01228183581,
						1.38139971818
				),
				advancePoint(
						rotor,
						"extreme_5x11_high_advance_loss_start",
						APC_5X11E,
						CURRENT_HIGH_ADVANCE_LOSS_START_EQUIVALENT_J,
						CURRENT_HIGH_ADVANCE_LOSS_START_PROJECT_MU,
						true,
						0.1572,
						0.2864,
						1.00575815739,
						1.45454545455
				),
				advancePoint(
						rotor,
						"extreme_5x11_retreating_stall_end",
						APC_5X11E,
						CURRENT_RETREATING_STALL_END_EQUIVALENT_J,
						CURRENT_RETREATING_STALL_END_PROJECT_MU,
						false,
						0.0,
						0.0302,
						0.0,
						0.152911392405
				),
				mejzlikWindTunnelAudit(config)
		);
	}

	private static MejzlikWindTunnelAudit mejzlikWindTunnelAudit(DroneConfig config) {
		RotorSpec rotor = config.rotors().get(0);
		return new MejzlikWindTunnelAudit(
				MEJZLIK_WIND_TUNNEL_SOURCE_ID,
				MEJZLIK_WIND_TUNNEL_CAVEAT,
				MEJZLIK_PACKET_METRIC_ROW_COUNT,
				MEJZLIK_SOURCE_INVENTORY_ROW_COUNT,
				MEJZLIK_TABLE_VALUE_ROW_COUNT,
				MEJZLIK_MODEL_VS_TUNNEL_ROW_COUNT,
				MEJZLIK_SUMMARY_ROW_COUNT,
				MEJZLIK_WIND_TUNNEL_RPM,
				MEJZLIK_WIND_TUNNEL_SPEED_MAX_METERS_PER_SECOND,
				MEJZLIK_CFD_MAX_SPEED_METERS_PER_SECOND,
				MEJZLIK_CFD_MAX_ADVANCE_RATIO_J,
				MEJZLIK_PUBLISHED_TABLE_J_COUNT,
				MEJZLIK_WIND_TUNNEL_CT_ZERO_CROSSING_J,
				MEJZLIK_WIND_TUNNEL_CT_ZERO_CROSSING_PROJECT_MU,
				hoverSpeedForPropellerAdvanceRatioJ(config, MEJZLIK_WIND_TUNNEL_CT_ZERO_CROSSING_J),
				CURRENT_LIFT_DISSYMMETRY_END_EQUIVALENT_J,
				CURRENT_RETREATING_STALL_START_EQUIVALENT_J,
				CURRENT_HIGH_ADVANCE_LOSS_START_EQUIVALENT_J,
				ratio(CURRENT_LIFT_DISSYMMETRY_END_EQUIVALENT_J, MEJZLIK_WIND_TUNNEL_CT_ZERO_CROSSING_J),
				ratio(CURRENT_RETREATING_STALL_START_EQUIVALENT_J, MEJZLIK_WIND_TUNNEL_CT_ZERO_CROSSING_J),
				ratio(CURRENT_HIGH_ADVANCE_LOSS_START_EQUIVALENT_J, MEJZLIK_WIND_TUNNEL_CT_ZERO_CROSSING_J),
				hoverSpeedForPropellerAdvanceRatioJ(config, CURRENT_HIGH_ADVANCE_LOSS_START_EQUIVALENT_J),
				mejzlikPoint(rotor, "wind_tunnel_j_0.2", 0.2, 0.0918, 0.0417, 0.4452),
				mejzlikPoint(rotor, "wind_tunnel_j_0.4", 0.4, 0.0711, 0.0417, 0.6810),
				mejzlikPoint(rotor, "wind_tunnel_j_0.6", 0.6, 0.0411, 0.0321, 0.7745),
				mejzlikPoint(rotor, "wind_tunnel_j_0.8", 0.8, -0.0035, 0.0081, 0.2132)
		);
	}

	private static MejzlikWindTunnelPoint mejzlikPoint(
			RotorSpec rotor,
			String pointId,
			double advanceRatioJ,
			double windTunnelCt,
			double windTunnelCp,
			double windTunnelEfficiency
	) {
		double currentAdvanceRatio = DronePhysics.rotorAdvanceRatioForUiucEquivalentPropellerAdvanceRatio(
				rotor,
				advanceRatioJ
		);
		double currentEquivalentJ = DronePhysics.rotorUiucEquivalentPropellerAdvanceRatio(rotor, currentAdvanceRatio);
		double currentThrustScale = DronePhysics.rotorForwardAdvanceThrustScale(rotor, currentAdvanceRatio);
		double currentPowerScale = DronePhysics.rotorForwardAdvancePowerScale(rotor, currentAdvanceRatio);
		double currentTorquePerThrustScale = DronePhysics.rotorForwardAdvanceTorquePerThrustScale(rotor, currentAdvanceRatio);
		double windTunnelCtRatio = ratio(windTunnelCt, 0.0918);
		double windTunnelCpRatio = ratio(windTunnelCp, 0.0417);
		return new MejzlikWindTunnelPoint(
				pointId,
				advanceRatioJ,
				advanceRatioJ / Math.PI,
				windTunnelCt,
				windTunnelCp,
				windTunnelEfficiency,
				windTunnelCtRatio,
				windTunnelCpRatio,
				windTunnelCt > 0.0,
				currentAdvanceRatio,
				currentEquivalentJ,
				currentThrustScale,
				currentPowerScale,
				currentTorquePerThrustScale,
				positiveRatio(currentThrustScale, windTunnelCtRatio),
				positiveRatio(currentPowerScale, windTunnelCpRatio)
		);
	}

	private static AdvancePointAudit advancePoint(
			RotorSpec rotor,
			String pointId,
			ApcPropellerReference apcReference,
			double targetEquivalentAdvanceRatioJ,
			double packetProjectMu,
			boolean targetWithinApcFileRange,
			double apcCt,
			double apcCp,
			double apcCtOverStaticCt,
			double apcCpOverStaticCp
	) {
		double currentAdvanceRatio = DronePhysics.rotorAdvanceRatioForUiucEquivalentPropellerAdvanceRatio(
				rotor,
				targetEquivalentAdvanceRatioJ
		);
		double currentEquivalentJ = DronePhysics.rotorUiucEquivalentPropellerAdvanceRatio(rotor, currentAdvanceRatio);
		double currentThrustScale = DronePhysics.rotorForwardAdvanceThrustScale(rotor, currentAdvanceRatio);
		double currentPowerScale = DronePhysics.rotorForwardAdvancePowerScale(rotor, currentAdvanceRatio);
		double currentTorquePerThrustScale = DronePhysics.rotorForwardAdvanceTorquePerThrustScale(rotor, currentAdvanceRatio);
		return new AdvancePointAudit(
				pointId,
				apcReference,
				targetEquivalentAdvanceRatioJ,
				packetProjectMu,
				currentAdvanceRatio,
				currentEquivalentJ,
				targetWithinApcFileRange,
				apcCt,
				apcCp,
				apcCtOverStaticCt,
				apcCpOverStaticCp,
				currentThrustScale,
				currentPowerScale,
				currentTorquePerThrustScale,
				ratio(currentThrustScale, apcCtOverStaticCt),
				ratio(currentPowerScale, apcCpOverStaticCp)
		);
	}

	private static double ratio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || Math.abs(denominator) <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static double positiveRatio(double numerator, double denominator) {
		if (!Double.isFinite(numerator) || !Double.isFinite(denominator) || denominator <= EPSILON) {
			return 0.0;
		}
		return numerator / denominator;
	}

	private static double hoverSpeedForPropellerAdvanceRatioJ(DroneConfig config, double advanceRatioJ) {
		RotorSpec rotor = config.rotors().get(0);
		double hoverThrustPerRotor = config.massKg() * config.gravityMetersPerSecondSquared() / config.rotors().size();
		double hoverOmega = Math.sqrt(hoverThrustPerRotor / rotor.thrustCoefficient());
		double hoverFrequencyHertz = hoverOmega / (2.0 * Math.PI);
		return Math.max(0.0, advanceRatioJ) * hoverFrequencyHertz * rotor.radiusMeters() * 2.0;
	}
}
