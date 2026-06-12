package com.tenicana.dronecraft.sim;

public final class HighAdvanceRotorCalibration {
	private static final double EPSILON = 1.0e-12;
	public static final String APC_HIGH_ADVANCE_SOURCE_ID = "APC-High-J-Axial-Propeller-Packet";
	public static final String APC_HIGH_ADVANCE_CAVEAT =
			"APC rows are axial propeller predictions; use NASA/UMD rotor sources for edgewise high-mu stall.";
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
			AdvancePointAudit extremePitchRetreatingStallEnd
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
				)
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
}
