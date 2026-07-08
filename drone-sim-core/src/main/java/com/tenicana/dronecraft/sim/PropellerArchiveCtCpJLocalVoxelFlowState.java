package com.tenicana.dronecraft.sim;

import java.util.ArrayList;
import java.util.List;

public record PropellerArchiveCtCpJLocalVoxelFlowState(
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
		List<Vec3> velocitiesWorldMetersPerSecond
) {
	private static final double EPSILON = 1.0e-12;
	private static final int MAX_ADVECTION_SUBSTEPS = 4096;
	private static final int MAX_PRESSURE_PROJECTION_ITERATIONS = 4096;

	public PropellerArchiveCtCpJLocalVoxelFlowState {
		if (gridSpec == null) {
			throw new IllegalArgumentException("gridSpec must not be null.");
		}
		List<Vec3> velocities = velocitiesWorldMetersPerSecond == null
				? List.of()
				: velocitiesWorldMetersPerSecond;
		if (velocities.size() != gridSpec.totalCellCount()) {
			throw new IllegalArgumentException("velocity count must match voxel grid cell count.");
		}
		ArrayList<Vec3> sanitized = new ArrayList<>(velocities.size());
		for (Vec3 velocity : velocities) {
			sanitized.add(finiteVecOrZero(velocity));
		}
		velocitiesWorldMetersPerSecond = List.copyOf(sanitized);
	}

	public record VoxelFlowAdvance(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			PropellerArchiveCtCpJLocalVoxelMomentumStep.MassFluxResidenceStepSample residenceStep
	) {
		public VoxelFlowAdvance {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (residenceStep == null) {
				throw new IllegalArgumentException("residenceStep must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("flow advance states must share a voxel grid.");
			}
		}

		public Vec3 totalSourceMomentumRateWorldNewtons() {
			return residenceStep.totalSourceMomentumRateWorldNewtons();
		}

		public Vec3 totalThroughFlowMomentumRateWorldNewtons() {
			return residenceStep.totalThroughFlowMomentumRateWorldNewtons();
		}

		public Vec3 totalCombinedMomentumRateWorldNewtons() {
			return residenceStep.totalCombinedMomentumRateWorldNewtons();
		}

		public Vec3 totalSourceImpulseWorldNewtonSeconds() {
			return residenceStep.totalSourceImpulseWorldNewtonSeconds();
		}

		public Vec3 totalWakeAngularMomentumTorqueWorldNewtonMeters() {
			return residenceStep.totalWakeAngularMomentumTorqueWorldNewtonMeters();
		}

		public Vec3 totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds() {
			return residenceStep.totalWakeAngularMomentumImpulseWorldNewtonMeterSeconds();
		}

		public Vec3 totalThroughFlowImpulseWorldNewtonSeconds() {
			return residenceStep.totalThroughFlowImpulseWorldNewtonSeconds();
		}

		public double totalSourceMassFlowRateKilogramsPerSecond() {
			return residenceStep.totalSourceMassFlowRateKilogramsPerSecond();
		}

		public double totalIdealMomentumPowerWatts() {
			return residenceStep.totalIdealMomentumPowerWatts();
		}

		public double idealMomentumEnergyJoules() {
			return totalIdealMomentumPowerWatts() * residenceStep.sourceMomentumSample().timeStepSeconds();
		}

		public double sourceMechanicalWorkEnergyJoules() {
			return residenceStep.sourceMomentumSample().totalSourceMechanicalWorkEnergyJoules();
		}

		public double sourceMechanicalWorkPowerWatts() {
			return residenceStep.sourceMomentumSample().meanSourceMechanicalPowerWatts();
		}

		public double flowKineticEnergyDeltaJoules() {
			return flowKineticEnergyDeltaJoules(VoxelSolidMask.open(previousState.gridSpec()));
		}

		public double flowKineticEnergyDeltaJoules(VoxelSolidMask solidMask) {
			if (solidMask == null || !previousState.gridSpec().equals(solidMask.gridSpec())) {
				throw new IllegalArgumentException("solidMask grid must match flow advance states.");
			}
			double airDensity = residenceStep.sourceMomentumSample().airDensityKgPerCubicMeter();
			return nextState.totalKineticEnergyJoules(airDensity, solidMask)
					- previousState.totalKineticEnergyJoules(airDensity, solidMask);
		}

		public double flowKineticEnergyDeltaMinusIdealMomentumEnergyJoules() {
			return flowKineticEnergyDeltaJoules() - idealMomentumEnergyJoules();
		}

		public double flowKineticEnergyDeltaMinusIdealMomentumEnergyJoules(VoxelSolidMask solidMask) {
			return flowKineticEnergyDeltaJoules(solidMask) - idealMomentumEnergyJoules();
		}

		public double flowKineticEnergyDeltaMinusSourceMechanicalWorkJoules() {
			return flowKineticEnergyDeltaJoules() - sourceMechanicalWorkEnergyJoules();
		}

		public double flowKineticEnergyDeltaMinusSourceMechanicalWorkJoules(VoxelSolidMask solidMask) {
			return flowKineticEnergyDeltaJoules(solidMask) - sourceMechanicalWorkEnergyJoules();
		}

		public double maxResidenceAlpha() {
			return residenceStep.maxResidenceAlpha();
		}

		public double meanActiveWakeResidualAfterResidenceMetersPerSecond() {
			double sum = 0.0;
			int count = 0;
			for (PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMassFluxResidenceStep cell
					: residenceStep.activeCells()) {
				sum += cell.targetWakeVelocityResidualAfterResidenceWorldMetersPerSecond().length();
				count++;
			}
			return count == 0 ? 0.0 : sum / count;
		}
	}

	public record VoxelSolidMask(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
			List<Boolean> solidCells,
			List<Double> solidVolumeFractions,
			int solidCellCount
	) {
		public record WorldSolidBox(
				Vec3 minWorldMeters,
				Vec3 maxWorldMeters
		) {
			public WorldSolidBox {
				if (minWorldMeters == null || maxWorldMeters == null
						|| !minWorldMeters.isFinite() || !maxWorldMeters.isFinite()) {
					throw new IllegalArgumentException("solid box bounds must be finite.");
				}
				if (maxWorldMeters.x() <= minWorldMeters.x()
						|| maxWorldMeters.y() <= minWorldMeters.y()
						|| maxWorldMeters.z() <= minWorldMeters.z()) {
					throw new IllegalArgumentException("solid box max bounds must be greater than min bounds.");
				}
			}

		}

		private record ClippedSolidBox(
				double minX,
				double minY,
				double minZ,
				double maxX,
				double maxY,
				double maxZ
		) {
			boolean contains(double x, double y, double z) {
				return x >= minX && x <= maxX
						&& y >= minY && y <= maxY
						&& z >= minZ && z <= maxZ;
			}
		}

		public VoxelSolidMask(
				PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
				List<Boolean> solidCells
		) {
			this(gridSpec, solidCells, List.of());
		}

		public VoxelSolidMask(
				PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
				List<Boolean> solidCells,
				List<Double> solidVolumeFractions
		) {
			this(gridSpec, solidCells, solidVolumeFractions, 0);
		}

		public VoxelSolidMask {
			if (gridSpec == null) {
				throw new IllegalArgumentException("gridSpec must not be null.");
			}
			List<Boolean> cells = solidCells == null ? List.of() : solidCells;
			if (cells.size() != gridSpec.totalCellCount()) {
				throw new IllegalArgumentException("solid cell count must match voxel grid cell count.");
			}
			List<Double> fractions = solidVolumeFractions == null ? List.of() : solidVolumeFractions;
			if (!fractions.isEmpty() && fractions.size() != gridSpec.totalCellCount()) {
				throw new IllegalArgumentException("solid volume fraction count must match voxel grid cell count.");
			}
			ArrayList<Boolean> sanitized = new ArrayList<>(cells.size());
			ArrayList<Double> sanitizedFractions = new ArrayList<>(cells.size());
			int sanitizedSolidCellCount = 0;
			for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
				boolean solid = Boolean.TRUE.equals(cells.get(cellIndex));
				double fraction = fractions.isEmpty()
						? (solid ? 1.0 : 0.0)
						: finiteFraction(fractions.get(cellIndex));
				sanitized.add(solid);
				sanitizedFractions.add(fraction);
				if (solid) {
					sanitizedSolidCellCount++;
				}
			}
			solidCells = List.copyOf(sanitized);
			solidVolumeFractions = List.copyOf(sanitizedFractions);
			solidCellCount = sanitizedSolidCellCount;
		}

		public static VoxelSolidMask open(
				PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec
		) {
			if (gridSpec == null) {
				throw new IllegalArgumentException("gridSpec must not be null.");
			}
			ArrayList<Boolean> cells = new ArrayList<>(gridSpec.totalCellCount());
			ArrayList<Double> fractions = new ArrayList<>(gridSpec.totalCellCount());
			for (int i = 0; i < gridSpec.totalCellCount(); i++) {
				cells.add(Boolean.FALSE);
				fractions.add(0.0);
			}
			return new VoxelSolidMask(gridSpec, cells, fractions);
		}

		public static VoxelSolidMask fromWorldSolidBoxes(
				PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
				List<WorldSolidBox> solidBoxes
		) {
			return fromWorldSolidBoxes(gridSpec, solidBoxes, 0.0);
		}

		public static VoxelSolidMask fromWorldSolidBoxes(
				PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
				List<WorldSolidBox> solidBoxes,
				double minimumSolidVolumeFraction
		) {
			if (gridSpec == null) {
				throw new IllegalArgumentException("gridSpec must not be null.");
			}
			if (!Double.isFinite(minimumSolidVolumeFraction)
					|| minimumSolidVolumeFraction < 0.0
					|| minimumSolidVolumeFraction > 1.0) {
				throw new IllegalArgumentException("minimumSolidVolumeFraction must be within [0, 1].");
			}
			List<WorldSolidBox> inputBoxes = solidBoxes == null ? List.of() : solidBoxes;
			ArrayList<WorldSolidBox> sanitizedBoxes = new ArrayList<>(inputBoxes.size());
			for (WorldSolidBox box : inputBoxes) {
				if (box == null) {
					throw new IllegalArgumentException("solidBoxes must not contain null boxes.");
				}
				sanitizedBoxes.add(box);
			}
			List<WorldSolidBox> boxes = List.copyOf(sanitizedBoxes);
			ArrayList<Boolean> cells = new ArrayList<>(gridSpec.totalCellCount());
			ArrayList<Double> fractions = new ArrayList<>(gridSpec.totalCellCount());
			double cellSize = gridSpec.cellSizeMeters();
			double cellVolume = gridSpec.cellVolumeCubicMeters();
			for (int y = 0; y < gridSpec.cellCountY(); y++) {
				for (int z = 0; z < gridSpec.cellCountZ(); z++) {
					for (int x = 0; x < gridSpec.cellCountX(); x++) {
						double cellMinX = gridSpec.originWorldMeters().x() + x * cellSize;
						double cellMinY = gridSpec.originWorldMeters().y() + y * cellSize;
						double cellMinZ = gridSpec.originWorldMeters().z() + z * cellSize;
						double solidVolumeFraction = solidVolumeFraction(
								boxes,
								cellMinX,
								cellMinY,
								cellMinZ,
								cellMinX + cellSize,
								cellMinY + cellSize,
								cellMinZ + cellSize,
								cellVolume);
						fractions.add(solidVolumeFraction);
						cells.add(solidVolumeFraction > EPSILON
								&& solidVolumeFraction + EPSILON >= minimumSolidVolumeFraction);
					}
				}
			}
			return new VoxelSolidMask(gridSpec, cells, fractions);
		}

		public int solidCellCount() {
			return solidCellCount;
		}

		public boolean isSolid(int xIndex, int yIndex, int zIndex) {
			return isSolidCellIndex(linearIndex(xIndex, yIndex, zIndex));
		}

		public boolean isSolidCellIndex(int cellIndex) {
			if (cellIndex < 0 || cellIndex >= solidCells.size()) {
				throw new IndexOutOfBoundsException("cell index outside voxel grid.");
			}
			return solidCells.get(cellIndex).booleanValue();
		}

		public double solidVolumeFraction(int xIndex, int yIndex, int zIndex) {
			return solidVolumeFractionCellIndex(linearIndex(xIndex, yIndex, zIndex));
		}

		public double solidVolumeFractionCellIndex(int cellIndex) {
			if (cellIndex < 0 || cellIndex >= solidVolumeFractions.size()) {
				throw new IndexOutOfBoundsException("cell index outside voxel grid.");
			}
			return solidVolumeFractions.get(cellIndex);
		}

		public double openVolumeFractionCellIndex(int cellIndex) {
			return 1.0 - solidVolumeFractionCellIndex(cellIndex);
		}

		public boolean hasSolidVolume() {
			for (double fraction : solidVolumeFractions) {
				if (fraction > EPSILON) {
					return true;
				}
			}
			return false;
		}

		private int linearIndex(int xIndex, int yIndex, int zIndex) {
			if (xIndex < 0 || xIndex >= gridSpec.cellCountX()
					|| yIndex < 0 || yIndex >= gridSpec.cellCountY()
					|| zIndex < 0 || zIndex >= gridSpec.cellCountZ()) {
				throw new IndexOutOfBoundsException("cell index outside voxel grid.");
			}
			return (yIndex * gridSpec.cellCountZ() + zIndex) * gridSpec.cellCountX() + xIndex;
		}

		private static double solidVolumeFraction(
				List<WorldSolidBox> boxes,
				double cellMinX,
				double cellMinY,
				double cellMinZ,
				double cellMaxX,
				double cellMaxY,
				double cellMaxZ,
				double cellVolume
		) {
			ArrayList<ClippedSolidBox> clippedBoxes = new ArrayList<>(boxes.size());
			ArrayList<Double> xCuts = new ArrayList<>();
			ArrayList<Double> yCuts = new ArrayList<>();
			ArrayList<Double> zCuts = new ArrayList<>();
			xCuts.add(cellMinX);
			xCuts.add(cellMaxX);
			yCuts.add(cellMinY);
			yCuts.add(cellMaxY);
			zCuts.add(cellMinZ);
			zCuts.add(cellMaxZ);
			for (WorldSolidBox box : boxes) {
				double minX = Math.max(box.minWorldMeters().x(), cellMinX);
				double minY = Math.max(box.minWorldMeters().y(), cellMinY);
				double minZ = Math.max(box.minWorldMeters().z(), cellMinZ);
				double maxX = Math.min(box.maxWorldMeters().x(), cellMaxX);
				double maxY = Math.min(box.maxWorldMeters().y(), cellMaxY);
				double maxZ = Math.min(box.maxWorldMeters().z(), cellMaxZ);
				if (maxX <= minX || maxY <= minY || maxZ <= minZ) {
					continue;
				}
				clippedBoxes.add(new ClippedSolidBox(minX, minY, minZ, maxX, maxY, maxZ));
				addCut(xCuts, minX);
				addCut(xCuts, maxX);
				addCut(yCuts, minY);
				addCut(yCuts, maxY);
				addCut(zCuts, minZ);
				addCut(zCuts, maxZ);
			}
			if (clippedBoxes.isEmpty() || cellVolume <= EPSILON) {
				return 0.0;
			}
			sortCuts(xCuts);
			sortCuts(yCuts);
			sortCuts(zCuts);
			double coveredVolume = 0.0;
			for (int xi = 0; xi < xCuts.size() - 1; xi++) {
				double minX = xCuts.get(xi);
				double maxX = xCuts.get(xi + 1);
				double midX = 0.5 * (minX + maxX);
				for (int yi = 0; yi < yCuts.size() - 1; yi++) {
					double minY = yCuts.get(yi);
					double maxY = yCuts.get(yi + 1);
					double midY = 0.5 * (minY + maxY);
					for (int zi = 0; zi < zCuts.size() - 1; zi++) {
						double minZ = zCuts.get(zi);
						double maxZ = zCuts.get(zi + 1);
						double midZ = 0.5 * (minZ + maxZ);
						if (coveredByAnyBox(clippedBoxes, midX, midY, midZ)) {
							coveredVolume += (maxX - minX) * (maxY - minY) * (maxZ - minZ);
						}
					}
				}
			}
			return MathUtil.clamp(coveredVolume / cellVolume, 0.0, 1.0);
		}

		private static boolean coveredByAnyBox(List<ClippedSolidBox> boxes, double x, double y, double z) {
			for (ClippedSolidBox box : boxes) {
				if (box.contains(x, y, z)) {
					return true;
				}
			}
			return false;
		}

		private static void addCut(ArrayList<Double> cuts, double value) {
			for (double cut : cuts) {
				if (Math.abs(cut - value) <= EPSILON) {
					return;
				}
			}
			cuts.add(value);
		}

		private static void sortCuts(ArrayList<Double> cuts) {
			cuts.sort(Double::compare);
		}

		private static double finiteFraction(Double value) {
			return value != null && Double.isFinite(value)
					? MathUtil.clamp(value, 0.0, 1.0)
					: 0.0;
		}

	}

	public record SolidBoundaryStep(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			VoxelSolidMask solidMask,
			double airDensityKgPerCubicMeter,
			int solidCellCount,
			int clampedCellCount,
			Vec3 totalMomentumBeforeWorldNewtonSeconds,
			Vec3 totalMomentumAfterWorldNewtonSeconds,
			double kineticEnergyBeforeJoules,
			double kineticEnergyAfterJoules
	) {
		public SolidBoundaryStep {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (solidMask == null) {
				throw new IllegalArgumentException("solidMask must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())
					|| !previousState.gridSpec().equals(solidMask.gridSpec())) {
				throw new IllegalArgumentException("solid boundary states and mask must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (solidCellCount < 0) {
				throw new IllegalArgumentException("solidCellCount must be nonnegative.");
			}
			if (clampedCellCount < 0 || clampedCellCount > solidCellCount) {
				throw new IllegalArgumentException("clampedCellCount must be within solid cell count.");
			}
			totalMomentumBeforeWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumBeforeWorldNewtonSeconds);
			totalMomentumAfterWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumAfterWorldNewtonSeconds);
			kineticEnergyBeforeJoules = finiteNonnegative(kineticEnergyBeforeJoules);
			kineticEnergyAfterJoules = finiteNonnegative(kineticEnergyAfterJoules);
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds.subtract(totalMomentumBeforeWorldNewtonSeconds);
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules - kineticEnergyBeforeJoules;
		}
	}

	public record VelocityDiffusionStep(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			double airDensityKgPerCubicMeter,
			double kinematicViscositySquareMetersPerSecond,
			double timeStepSeconds,
			double diffusionNumber,
			Vec3 totalMomentumBeforeWorldNewtonSeconds,
			Vec3 totalMomentumAfterWorldNewtonSeconds,
			double kineticEnergyBeforeJoules,
			double kineticEnergyAfterJoules
	) {
		public VelocityDiffusionStep {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("diffusion states must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(kinematicViscositySquareMetersPerSecond)
					|| kinematicViscositySquareMetersPerSecond < 0.0) {
				throw new IllegalArgumentException(
						"kinematicViscositySquareMetersPerSecond must be finite and nonnegative.");
			}
			if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
				throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
			}
			diffusionNumber = finiteNonnegative(diffusionNumber);
			totalMomentumBeforeWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumBeforeWorldNewtonSeconds);
			totalMomentumAfterWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumAfterWorldNewtonSeconds);
			kineticEnergyBeforeJoules = finiteNonnegative(kineticEnergyBeforeJoules);
			kineticEnergyAfterJoules = finiteNonnegative(kineticEnergyAfterJoules);
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds.subtract(totalMomentumBeforeWorldNewtonSeconds);
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules - kineticEnergyBeforeJoules;
		}

		public double viscousDissipatedEnergyJoules() {
			return Math.max(0.0, kineticEnergyBeforeJoules - kineticEnergyAfterJoules);
		}

		public double meanViscousDissipationPowerWatts() {
			return viscousDissipatedEnergyJoules() / timeStepSeconds;
		}
	}

	public record VelocityAdvectionStep(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double maxCourantNumber,
			Vec3 totalMomentumBeforeWorldNewtonSeconds,
			Vec3 totalMomentumAfterWorldNewtonSeconds,
			double kineticEnergyBeforeJoules,
			double kineticEnergyAfterJoules
	) {
		public VelocityAdvectionStep {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("advection states must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
				throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
			}
			maxCourantNumber = finiteNonnegative(maxCourantNumber);
			totalMomentumBeforeWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumBeforeWorldNewtonSeconds);
			totalMomentumAfterWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumAfterWorldNewtonSeconds);
			kineticEnergyBeforeJoules = finiteNonnegative(kineticEnergyBeforeJoules);
			kineticEnergyAfterJoules = finiteNonnegative(kineticEnergyAfterJoules);
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds.subtract(totalMomentumBeforeWorldNewtonSeconds);
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules - kineticEnergyBeforeJoules;
		}
	}

	public record VelocityAdvectionRun(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double maxAllowedCourantNumber,
			List<VelocityAdvectionStep> substeps
	) {
		public VelocityAdvectionRun {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("advection run states must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
				throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
			}
			if (!Double.isFinite(maxAllowedCourantNumber) || maxAllowedCourantNumber <= EPSILON) {
				throw new IllegalArgumentException("maxAllowedCourantNumber must be finite and positive.");
			}
			substeps = List.copyOf(substeps == null ? List.of() : substeps);
			if (substeps.isEmpty()) {
				throw new IllegalArgumentException("advection run must contain at least one substep.");
			}
			if (!substeps.get(0).previousState().equals(previousState)) {
				throw new IllegalArgumentException("first advection substep must start from previousState.");
			}
			if (!substeps.get(substeps.size() - 1).nextState().equals(nextState)) {
				throw new IllegalArgumentException("last advection substep must end at nextState.");
			}
			for (int i = 1; i < substeps.size(); i++) {
				if (!substeps.get(i - 1).nextState().equals(substeps.get(i).previousState())) {
					throw new IllegalArgumentException("advection substeps must be contiguous.");
				}
			}
		}

		public int completedSubstepCount() {
			return substeps.size();
		}

		public double maxCourantNumber() {
			double max = 0.0;
			for (VelocityAdvectionStep substep : substeps) {
				max = Math.max(max, substep.maxCourantNumber());
			}
			return max;
		}

		public Vec3 totalMomentumBeforeWorldNewtonSeconds() {
			return substeps.get(0).totalMomentumBeforeWorldNewtonSeconds();
		}

		public Vec3 totalMomentumAfterWorldNewtonSeconds() {
			return substeps.get(substeps.size() - 1).totalMomentumAfterWorldNewtonSeconds();
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds().subtract(totalMomentumBeforeWorldNewtonSeconds());
		}

		public double kineticEnergyBeforeJoules() {
			return substeps.get(0).kineticEnergyBeforeJoules();
		}

		public double kineticEnergyAfterJoules() {
			return substeps.get(substeps.size() - 1).kineticEnergyAfterJoules();
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules() - kineticEnergyBeforeJoules();
		}
	}

	public record DivergenceMetrics(
			double maxAbsDivergencePerSecond,
			double rmsDivergencePerSecond,
			double meanDivergencePerSecond
	) {
		public DivergenceMetrics {
			maxAbsDivergencePerSecond = finiteNonnegative(maxAbsDivergencePerSecond);
			rmsDivergencePerSecond = finiteNonnegative(rmsDivergencePerSecond);
			meanDivergencePerSecond = Double.isFinite(meanDivergencePerSecond)
					? meanDivergencePerSecond
					: 0.0;
		}
	}

	public record VorticityMetrics(
			double maxMagnitudePerSecond,
			double rmsMagnitudePerSecond,
			Vec3 meanVorticityWorldPerSecond
	) {
		public VorticityMetrics {
			maxMagnitudePerSecond = finiteNonnegative(maxMagnitudePerSecond);
			rmsMagnitudePerSecond = finiteNonnegative(rmsMagnitudePerSecond);
			meanVorticityWorldPerSecond = finiteVecOrZero(meanVorticityWorldPerSecond);
		}
	}

	public record VorticityIntegralMetrics(
			double enstrophyCubicMetersPerSecondSquared,
			double helicityFourthMetersPerSecondSquared,
			double meanHelicityDensityMetersPerSecondSquared
	) {
		public VorticityIntegralMetrics {
			enstrophyCubicMetersPerSecondSquared =
					finiteNonnegative(enstrophyCubicMetersPerSecondSquared);
			helicityFourthMetersPerSecondSquared = Double.isFinite(helicityFourthMetersPerSecondSquared)
					? helicityFourthMetersPerSecondSquared
					: 0.0;
			meanHelicityDensityMetersPerSecondSquared =
					Double.isFinite(meanHelicityDensityMetersPerSecondSquared)
							? meanHelicityDensityMetersPerSecondSquared
							: 0.0;
		}
	}

	public record VelocityProjectionStep(
			PropellerArchiveCtCpJLocalVoxelFlowState previousState,
			PropellerArchiveCtCpJLocalVoxelFlowState nextState,
			double airDensityKgPerCubicMeter,
			int pressureProjectionIterations,
			DivergenceMetrics divergenceBefore,
			DivergenceMetrics divergenceAfter,
			Vec3 totalMomentumBeforeWorldNewtonSeconds,
			Vec3 totalMomentumAfterWorldNewtonSeconds,
			double kineticEnergyBeforeJoules,
			double kineticEnergyAfterJoules
	) {
		public VelocityProjectionStep {
			if (previousState == null) {
				throw new IllegalArgumentException("previousState must not be null.");
			}
			if (nextState == null) {
				throw new IllegalArgumentException("nextState must not be null.");
			}
			if (!previousState.gridSpec().equals(nextState.gridSpec())) {
				throw new IllegalArgumentException("projection states must share a voxel grid.");
			}
			if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
				throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
			}
			if (pressureProjectionIterations < 0) {
				throw new IllegalArgumentException("pressureProjectionIterations must be nonnegative.");
			}
			if (pressureProjectionIterations > MAX_PRESSURE_PROJECTION_ITERATIONS) {
				throw new IllegalArgumentException("pressureProjectionIterations exceeds maximum supported bound.");
			}
			divergenceBefore = divergenceBefore == null
					? new DivergenceMetrics(0.0, 0.0, 0.0)
					: divergenceBefore;
			divergenceAfter = divergenceAfter == null
					? new DivergenceMetrics(0.0, 0.0, 0.0)
					: divergenceAfter;
			totalMomentumBeforeWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumBeforeWorldNewtonSeconds);
			totalMomentumAfterWorldNewtonSeconds =
					finiteVecOrZero(totalMomentumAfterWorldNewtonSeconds);
			kineticEnergyBeforeJoules = finiteNonnegative(kineticEnergyBeforeJoules);
			kineticEnergyAfterJoules = finiteNonnegative(kineticEnergyAfterJoules);
		}

		public Vec3 momentumResidualWorldNewtonSeconds() {
			return totalMomentumAfterWorldNewtonSeconds.subtract(totalMomentumBeforeWorldNewtonSeconds);
		}

		public double kineticEnergyDeltaJoules() {
			return kineticEnergyAfterJoules - kineticEnergyBeforeJoules;
		}
	}

	public static PropellerArchiveCtCpJLocalVoxelFlowState calm(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec
	) {
		return uniform(gridSpec, Vec3.ZERO);
	}

	public static PropellerArchiveCtCpJLocalVoxelFlowState uniform(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSpec gridSpec,
			Vec3 velocityWorldMetersPerSecond
	) {
		if (gridSpec == null) {
			throw new IllegalArgumentException("gridSpec must not be null.");
		}
		Vec3 velocity = finiteVecOrZero(velocityWorldMetersPerSecond);
		ArrayList<Vec3> velocities = new ArrayList<>(gridSpec.totalCellCount());
		for (int i = 0; i < gridSpec.totalCellCount(); i++) {
			velocities.add(velocity);
		}
		return new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, velocities);
	}

	public Vec3 velocityAt(int xIndex, int yIndex, int zIndex) {
		return velocitiesWorldMetersPerSecond.get(linearIndex(xIndex, yIndex, zIndex));
	}

	public Vec3 vorticityAt(int xIndex, int yIndex, int zIndex) {
		return vorticityAt(xIndex, yIndex, zIndex, VoxelSolidMask.open(gridSpec));
	}

	public Vec3 vorticityAt(int xIndex, int yIndex, int zIndex, VoxelSolidMask solidMask) {
		validateSolidMask(solidMask);
		return vorticityAtCell(xIndex, yIndex, zIndex, solidMask);
	}

	public SolidBoundaryStep applySolidMask(
			VoxelSolidMask solidMask,
			double airDensityKgPerCubicMeter
	) {
		if (solidMask == null) {
			throw new IllegalArgumentException("solidMask must not be null.");
		}
		if (!gridSpec.equals(solidMask.gridSpec())) {
			throw new IllegalArgumentException("solidMask grid must match this flow state.");
		}
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		ArrayList<Vec3> nextVelocities = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
		int clampedCellCount = 0;
		for (int i = 0; i < velocitiesWorldMetersPerSecond.size(); i++) {
			Vec3 velocity = velocitiesWorldMetersPerSecond.get(i);
			if (solidMask.isSolidCellIndex(i)) {
				if (velocity.lengthSquared() > EPSILON * EPSILON) {
					clampedCellCount++;
				}
				nextVelocities.add(Vec3.ZERO);
			} else {
				nextVelocities.add(velocity);
			}
		}
		PropellerArchiveCtCpJLocalVoxelFlowState nextState =
				new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities);
		return new SolidBoundaryStep(
				this,
				nextState,
				solidMask,
				airDensityKgPerCubicMeter,
				solidMask.solidCellCount(),
				clampedCellCount,
				totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter, solidMask),
				nextState.totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter, solidMask),
				totalKineticEnergyJoules(airDensityKgPerCubicMeter, solidMask),
				nextState.totalKineticEnergyJoules(airDensityKgPerCubicMeter, solidMask)
		);
	}

	public VoxelFlowAdvance advanceWithSource(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double sourceThicknessMeters
	) {
		return advanceWithSource(
				sourceGridSample,
				airDensityKgPerCubicMeter,
				timeStepSeconds,
				sourceThicknessMeters,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public VoxelFlowAdvance advanceWithSource(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double sourceThicknessMeters,
			VoxelSolidMask solidMask
	) {
		if (sourceGridSample == null) {
			throw new IllegalArgumentException("sourceGridSample must not be null.");
		}
		if (!gridSpec.equals(sourceGridSample.gridSpec())) {
			throw new IllegalArgumentException("sourceGridSample grid must match this flow state.");
		}
		validateSolidMask(solidMask);
		PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample openSourceGridSample =
				sourceGridSampleWithSolidVolumeOcclusion(sourceGridSample, solidMask);
		PropellerArchiveCtCpJLocalVoxelMomentumStep.MassFluxResidenceStepSample residenceStep =
				PropellerArchiveCtCpJLocalVoxelMomentumStep.stepWithMassFluxResidence(
						openSourceGridSample,
						airDensityKgPerCubicMeter,
						timeStepSeconds,
						sourceThicknessMeters,
						velocitiesWorldMetersPerSecond
				);
		ArrayList<Vec3> nextVelocities = new ArrayList<>(residenceStep.cells().size());
		for (PropellerArchiveCtCpJLocalVoxelMomentumStep.CellMassFluxResidenceStep cell
				: residenceStep.cells()) {
			nextVelocities.add(cell.velocityAfterResidenceWorldMetersPerSecond());
		}
		return new VoxelFlowAdvance(
				this,
				new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities),
				residenceStep
		);
	}

	private PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSampleWithSolidVolumeOcclusion(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample sourceGridSample,
			VoxelSolidMask solidMask
	) {
		if (!solidMask.hasSolidVolume()) {
			return sourceGridSample;
		}
		ArrayList<PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample> cells =
				new ArrayList<>(sourceGridSample.cells().size());
		for (int cellIndex = 0; cellIndex < sourceGridSample.cells().size(); cellIndex++) {
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample sourceCell =
					sourceGridSample.cells().get(cellIndex);
			double openVolumeFraction = solidMask.openVolumeFractionCellIndex(cellIndex);
			if (openVolumeFraction >= 1.0 - EPSILON) {
				cells.add(sourceCell);
			} else if (openVolumeFraction <= EPSILON) {
				cells.add(zeroSourceCell(sourceCell));
			} else {
				cells.add(scaleSourceCell(sourceCell, openVolumeFraction));
			}
		}
		return new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelGridSample(
				sourceGridSample.gridSpec(),
				sourceGridSample.subcellSamplesPerAxis(),
				cells
		);
	}

	private static PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample zeroSourceCell(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample sourceCell
	) {
		return new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample(
				sourceCell.xIndex(),
				sourceCell.yIndex(),
				sourceCell.zIndex(),
				sourceCell.cellCenterWorldMeters(),
				sourceCell.cellVolumeCubicMeters(),
				sourceCell.totalSubsampleCount(),
				0,
				0.0,
				Vec3.ZERO,
				Vec3.ZERO,
				0.0,
				0.0,
				0.0,
				Vec3.ZERO,
				Vec3.ZERO,
				Vec3.ZERO
		);
	}

	private static PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample scaleSourceCell(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample sourceCell,
			double scale
	) {
		double sourceScale = MathUtil.clamp(scale, 0.0, 1.0);
		if (sourceScale <= EPSILON) {
			return zeroSourceCell(sourceCell);
		}
		int activeSubsamples = scaledActiveSubsampleCount(sourceCell, sourceScale);
		return new PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample(
				sourceCell.xIndex(),
				sourceCell.yIndex(),
				sourceCell.zIndex(),
				sourceCell.cellCenterWorldMeters(),
				sourceCell.cellVolumeCubicMeters() * sourceScale,
				sourceCell.totalSubsampleCount(),
				activeSubsamples,
				sourceCell.sourceVolumeFraction(),
				sourceCell.bodyForceDensityWorldNewtonsPerCubicMeter(),
				sourceCell.wakeAngularMomentumTorqueDensityWorldNewtonMetersPerCubicMeter(),
				sourceCell.pressureJumpPascals(),
				sourceCell.massFluxKilogramsPerSecondSquareMeter(),
				sourceCell.idealMomentumPowerLoadingWattsPerSquareMeter(),
				sourceCell.farWakeAxialVelocityWorldMetersPerSecond(),
				sourceCell.wakeSwirlVelocityWorldMetersPerSecond(),
				sourceCell.targetWakeVelocityWorldMetersPerSecond()
		);
	}

	private static int scaledActiveSubsampleCount(
			PropellerArchiveCtCpJActuatorDiskSourceField.VoxelCellSample sourceCell,
			double scale
	) {
		if (!sourceCell.active()) {
			return 0;
		}
		int scaled = (int) Math.round(sourceCell.activeSubsampleCount() * scale);
		return Math.max(1, Math.min(sourceCell.totalSubsampleCount(), scaled));
	}

	public VelocityAdvectionStep advectVelocity(
			double airDensityKgPerCubicMeter,
			double timeStepSeconds
	) {
		return advectVelocity(
				airDensityKgPerCubicMeter,
				timeStepSeconds,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public VelocityAdvectionStep advectVelocity(
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			VoxelSolidMask solidMask
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
			throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
		}
		validateSolidMask(solidMask);
		double maxCourantNumber = 0.0;
		ArrayList<Vec3> nextVelocities = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					int index = linearIndex(x, y, z);
					Vec3 currentVelocity = velocityAt(x, y, z);
					if (solidMask.isSolidCellIndex(index)) {
						nextVelocities.add(currentVelocity);
						continue;
					}
					maxCourantNumber = Math.max(
							maxCourantNumber,
							currentVelocity.length() * timeStepSeconds / gridSpec.cellSizeMeters()
					);
					Vec3 backtracedPoint = gridSpec.cellCenterWorldMeters(x, y, z)
							.subtract(currentVelocity.multiply(timeStepSeconds));
					nextVelocities.add(sampleVelocityClamped(backtracedPoint, solidMask));
				}
			}
		}
		PropellerArchiveCtCpJLocalVoxelFlowState nextState =
				new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities);
		return new VelocityAdvectionStep(
				this,
				nextState,
				airDensityKgPerCubicMeter,
				timeStepSeconds,
				maxCourantNumber,
				totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter, solidMask),
				nextState.totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter, solidMask),
				totalKineticEnergyJoules(airDensityKgPerCubicMeter, solidMask),
				nextState.totalKineticEnergyJoules(airDensityKgPerCubicMeter, solidMask)
		);
	}

	public VelocityAdvectionRun advectVelocityWithCourantLimit(
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double maxAllowedCourantNumber
	) {
		return advectVelocityWithCourantLimit(
				airDensityKgPerCubicMeter,
				timeStepSeconds,
				maxAllowedCourantNumber,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public VelocityAdvectionRun advectVelocityWithCourantLimit(
			double airDensityKgPerCubicMeter,
			double timeStepSeconds,
			double maxAllowedCourantNumber,
			VoxelSolidMask solidMask
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
			throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
		}
		if (!Double.isFinite(maxAllowedCourantNumber) || maxAllowedCourantNumber <= EPSILON) {
			throw new IllegalArgumentException("maxAllowedCourantNumber must be finite and positive.");
		}
		validateSolidMask(solidMask);
		int substepCount = advectionSubstepCount(timeStepSeconds, maxAllowedCourantNumber, solidMask);
		double substepSeconds = timeStepSeconds / substepCount;
		ArrayList<VelocityAdvectionStep> substeps = new ArrayList<>(substepCount);
		PropellerArchiveCtCpJLocalVoxelFlowState state = this;
		for (int i = 0; i < substepCount; i++) {
			VelocityAdvectionStep substep = state.advectVelocity(
					airDensityKgPerCubicMeter,
					substepSeconds,
					solidMask
			);
			substeps.add(substep);
			state = substep.nextState();
		}
		return new VelocityAdvectionRun(
				this,
				state,
				airDensityKgPerCubicMeter,
				timeStepSeconds,
				maxAllowedCourantNumber,
				substeps
		);
	}

	public VelocityDiffusionStep diffuseVelocity(
			double airDensityKgPerCubicMeter,
			double kinematicViscositySquareMetersPerSecond,
			double timeStepSeconds
	) {
		return diffuseVelocity(
				airDensityKgPerCubicMeter,
				kinematicViscositySquareMetersPerSecond,
				timeStepSeconds,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public VelocityDiffusionStep diffuseVelocity(
			double airDensityKgPerCubicMeter,
			double kinematicViscositySquareMetersPerSecond,
			double timeStepSeconds,
			VoxelSolidMask solidMask
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (!Double.isFinite(kinematicViscositySquareMetersPerSecond)
				|| kinematicViscositySquareMetersPerSecond < 0.0) {
			throw new IllegalArgumentException(
					"kinematicViscositySquareMetersPerSecond must be finite and nonnegative.");
		}
		if (!Double.isFinite(timeStepSeconds) || timeStepSeconds <= EPSILON) {
			throw new IllegalArgumentException("timeStepSeconds must be finite and positive.");
		}
		validateSolidMask(solidMask);
		double diffusionNumber = kinematicViscositySquareMetersPerSecond
				* timeStepSeconds
				/ (gridSpec.cellSizeMeters() * gridSpec.cellSizeMeters());
		if (diffusionNumber > 1.0 / 6.0 + 1.0e-15) {
			throw new IllegalArgumentException("diffusionNumber must be <= 1/6 for explicit 3D stability.");
		}
		ArrayList<Vec3> deltas = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
		for (int i = 0; i < velocitiesWorldMetersPerSecond.size(); i++) {
			deltas.add(Vec3.ZERO);
		}
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					int index = linearIndex(x, y, z);
					if (x + 1 < gridSpec.cellCountX()) {
						accumulateDiffusiveExchange(
								deltas,
								index,
								linearIndex(x + 1, y, z),
								diffusionNumber,
								solidMask);
					}
					if (y + 1 < gridSpec.cellCountY()) {
						accumulateDiffusiveExchange(
								deltas,
								index,
								linearIndex(x, y + 1, z),
								diffusionNumber,
								solidMask);
					}
					if (z + 1 < gridSpec.cellCountZ()) {
						accumulateDiffusiveExchange(
								deltas,
								index,
								linearIndex(x, y, z + 1),
								diffusionNumber,
								solidMask);
					}
				}
			}
		}
		ArrayList<Vec3> nextVelocities = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
		for (int i = 0; i < velocitiesWorldMetersPerSecond.size(); i++) {
			nextVelocities.add(velocitiesWorldMetersPerSecond.get(i).add(deltas.get(i)));
		}
		PropellerArchiveCtCpJLocalVoxelFlowState nextState =
				new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities);
		return new VelocityDiffusionStep(
				this,
				nextState,
				airDensityKgPerCubicMeter,
				kinematicViscositySquareMetersPerSecond,
				timeStepSeconds,
				diffusionNumber,
				totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter, solidMask),
				nextState.totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter, solidMask),
				totalKineticEnergyJoules(airDensityKgPerCubicMeter, solidMask),
				nextState.totalKineticEnergyJoules(airDensityKgPerCubicMeter, solidMask)
		);
	}

	public VelocityProjectionStep projectVelocityDivergence(
			double airDensityKgPerCubicMeter,
			int pressureProjectionIterations
	) {
		return projectVelocityDivergence(
				airDensityKgPerCubicMeter,
				pressureProjectionIterations,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public VelocityProjectionStep projectVelocityDivergence(
			double airDensityKgPerCubicMeter,
			int pressureProjectionIterations,
			VoxelSolidMask solidMask
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		if (pressureProjectionIterations < 0) {
			throw new IllegalArgumentException("pressureProjectionIterations must be nonnegative.");
		}
		if (pressureProjectionIterations > MAX_PRESSURE_PROJECTION_ITERATIONS) {
			throw new IllegalArgumentException("pressureProjectionIterations exceeds maximum supported bound.");
		}
		validateSolidMask(solidMask);
		DivergenceMetrics divergenceBefore = divergenceMetrics(solidMask);
		PropellerArchiveCtCpJLocalVoxelFlowState nextState = this;
		if (pressureProjectionIterations > 0 && divergenceBefore.maxAbsDivergencePerSecond() > EPSILON) {
			double[] divergence = divergenceValues(solidMask);
			double[] pressurePotential = pressurePotential(divergence, pressureProjectionIterations, solidMask);
			ArrayList<Vec3> nextVelocities = new ArrayList<>(velocitiesWorldMetersPerSecond.size());
			for (int y = 0; y < gridSpec.cellCountY(); y++) {
				for (int z = 0; z < gridSpec.cellCountZ(); z++) {
					for (int x = 0; x < gridSpec.cellCountX(); x++) {
						int index = linearIndex(x, y, z);
						nextVelocities.add(solidMask.isSolidCellIndex(index)
								? velocityAt(x, y, z)
								: projectedVelocityAt(pressurePotential, solidMask, x, y, z));
					}
				}
			}
			nextState = new PropellerArchiveCtCpJLocalVoxelFlowState(gridSpec, nextVelocities);
		}
		return new VelocityProjectionStep(
				this,
				nextState,
				airDensityKgPerCubicMeter,
				pressureProjectionIterations,
				divergenceBefore,
				nextState.divergenceMetrics(solidMask),
				totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter, solidMask),
				nextState.totalMomentumWorldNewtonSeconds(airDensityKgPerCubicMeter, solidMask),
				totalKineticEnergyJoules(airDensityKgPerCubicMeter, solidMask),
				nextState.totalKineticEnergyJoules(airDensityKgPerCubicMeter, solidMask)
		);
	}

	public DivergenceMetrics divergenceMetrics() {
		return divergenceMetrics(VoxelSolidMask.open(gridSpec));
	}

	public DivergenceMetrics divergenceMetrics(VoxelSolidMask solidMask) {
		validateSolidMask(solidMask);
		double[] divergence = divergenceValues(solidMask);
		double maxAbs = 0.0;
		double sumSquares = 0.0;
		double sum = 0.0;
		int openCount = 0;
		for (int i = 0; i < divergence.length; i++) {
			if (solidMask.isSolidCellIndex(i)) {
				continue;
			}
			double value = divergence[i];
			maxAbs = Math.max(maxAbs, Math.abs(value));
			sumSquares += value * value;
			sum += value;
			openCount++;
		}
		return new DivergenceMetrics(
				maxAbs,
				openCount == 0 ? 0.0 : Math.sqrt(sumSquares / openCount),
				openCount == 0 ? 0.0 : sum / openCount
		);
	}

	public VorticityMetrics vorticityMetrics() {
		return vorticityMetrics(VoxelSolidMask.open(gridSpec));
	}

	public VorticityMetrics vorticityMetrics(VoxelSolidMask solidMask) {
		validateSolidMask(solidMask);
		double maxMagnitude = 0.0;
		double sumWeightedMagnitudeSquares = 0.0;
		double totalWeight = 0.0;
		Vec3 weightedVorticity = Vec3.ZERO;
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					int cellIndex = linearIndex(x, y, z);
					if (solidMask.isSolidCellIndex(cellIndex)) {
						continue;
					}
					double weight = solidMask.openVolumeFractionCellIndex(cellIndex);
					if (weight <= EPSILON) {
						continue;
					}
					Vec3 vorticity = vorticityAtCell(x, y, z, solidMask);
					double magnitude = vorticity.length();
					maxMagnitude = Math.max(maxMagnitude, magnitude);
					sumWeightedMagnitudeSquares += magnitude * magnitude * weight;
					weightedVorticity = weightedVorticity.add(vorticity.multiply(weight));
					totalWeight += weight;
				}
			}
		}
		return new VorticityMetrics(
				maxMagnitude,
				totalWeight <= EPSILON ? 0.0 : Math.sqrt(sumWeightedMagnitudeSquares / totalWeight),
				totalWeight <= EPSILON ? Vec3.ZERO : weightedVorticity.multiply(1.0 / totalWeight)
		);
	}

	public VorticityIntegralMetrics vorticityIntegralMetrics() {
		return vorticityIntegralMetrics(VoxelSolidMask.open(gridSpec));
	}

	public VorticityIntegralMetrics vorticityIntegralMetrics(VoxelSolidMask solidMask) {
		validateSolidMask(solidMask);
		double enstrophy = 0.0;
		double helicity = 0.0;
		double openVolume = 0.0;
		double cellVolume = gridSpec.cellVolumeCubicMeters();
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					int cellIndex = linearIndex(x, y, z);
					if (solidMask.isSolidCellIndex(cellIndex)) {
						continue;
					}
					double volume = cellVolume * solidMask.openVolumeFractionCellIndex(cellIndex);
					if (volume <= EPSILON) {
						continue;
					}
					Vec3 vorticity = vorticityAtCell(x, y, z, solidMask);
					Vec3 velocity = velocitiesWorldMetersPerSecond.get(cellIndex);
					enstrophy += 0.5 * vorticity.lengthSquared() * volume;
					helicity += velocity.dot(vorticity) * volume;
					openVolume += volume;
				}
			}
		}
		return new VorticityIntegralMetrics(
				enstrophy,
				helicity,
				openVolume <= EPSILON ? 0.0 : helicity / openVolume
		);
	}

	public double totalKineticEnergyJoules(double airDensityKgPerCubicMeter) {
		return totalKineticEnergyJoules(
				airDensityKgPerCubicMeter,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public double totalKineticEnergyJoules(double airDensityKgPerCubicMeter, VoxelSolidMask solidMask) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		validateSolidMask(solidMask);
		double energy = 0.0;
		for (int cellIndex = 0; cellIndex < velocitiesWorldMetersPerSecond.size(); cellIndex++) {
			double cellMass = openCellAirMassKilograms(airDensityKgPerCubicMeter, solidMask, cellIndex);
			Vec3 velocity = velocitiesWorldMetersPerSecond.get(cellIndex);
			energy += 0.5 * cellMass * velocity.lengthSquared();
		}
		return energy;
	}

	public Vec3 totalMomentumWorldNewtonSeconds(double airDensityKgPerCubicMeter) {
		return totalMomentumWorldNewtonSeconds(
				airDensityKgPerCubicMeter,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public Vec3 totalMomentumWorldNewtonSeconds(double airDensityKgPerCubicMeter, VoxelSolidMask solidMask) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		validateSolidMask(solidMask);
		Vec3 momentum = Vec3.ZERO;
		for (int cellIndex = 0; cellIndex < velocitiesWorldMetersPerSecond.size(); cellIndex++) {
			double cellMass = openCellAirMassKilograms(airDensityKgPerCubicMeter, solidMask, cellIndex);
			Vec3 velocity = velocitiesWorldMetersPerSecond.get(cellIndex);
			momentum = momentum.add(velocity.multiply(cellMass));
		}
		return momentum;
	}

	public Vec3 totalAngularMomentumWorldNewtonMeterSeconds(
			double airDensityKgPerCubicMeter,
			Vec3 momentReferenceWorldMeters
	) {
		return totalAngularMomentumWorldNewtonMeterSeconds(
				airDensityKgPerCubicMeter,
				momentReferenceWorldMeters,
				VoxelSolidMask.open(gridSpec)
		);
	}

	public Vec3 totalAngularMomentumWorldNewtonMeterSeconds(
			double airDensityKgPerCubicMeter,
			Vec3 momentReferenceWorldMeters,
			VoxelSolidMask solidMask
	) {
		if (!Double.isFinite(airDensityKgPerCubicMeter) || airDensityKgPerCubicMeter <= EPSILON) {
			throw new IllegalArgumentException("airDensityKgPerCubicMeter must be finite and positive.");
		}
		validateSolidMask(solidMask);
		Vec3 reference = finiteVecOrZero(momentReferenceWorldMeters);
		Vec3 angularMomentum = Vec3.ZERO;
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					int cellIndex = linearIndex(x, y, z);
					double cellMass = openCellAirMassKilograms(airDensityKgPerCubicMeter, solidMask, cellIndex);
					Vec3 momentum = velocitiesWorldMetersPerSecond.get(cellIndex).multiply(cellMass);
					Vec3 leverArm = gridSpec.cellCenterWorldMeters(x, y, z).subtract(reference);
					angularMomentum = angularMomentum.add(leverArm.cross(momentum));
				}
			}
		}
		return angularMomentum;
	}

	private double openCellAirMassKilograms(
			double airDensityKgPerCubicMeter,
			VoxelSolidMask solidMask,
			int cellIndex
	) {
		return airDensityKgPerCubicMeter
				* gridSpec.cellVolumeCubicMeters()
				* solidMask.openVolumeFractionCellIndex(cellIndex);
	}

	public double maxSpeedMetersPerSecond() {
		double max = 0.0;
		for (Vec3 velocity : velocitiesWorldMetersPerSecond) {
			max = Math.max(max, velocity.length());
		}
		return max;
	}

	private int advectionSubstepCount(double timeStepSeconds, double maxAllowedCourantNumber) {
		return advectionSubstepCount(
				timeStepSeconds,
				maxAllowedCourantNumber,
				VoxelSolidMask.open(gridSpec)
		);
	}

	private int advectionSubstepCount(
			double timeStepSeconds,
			double maxAllowedCourantNumber,
			VoxelSolidMask solidMask
	) {
		double maxCourantNumber = maxSpeedMetersPerSecond(solidMask) * timeStepSeconds / gridSpec.cellSizeMeters();
		int substepCount = Math.max(1, (int) Math.ceil(maxCourantNumber / maxAllowedCourantNumber));
		if (substepCount > MAX_ADVECTION_SUBSTEPS) {
			throw new IllegalArgumentException("advection substep count exceeds maximum supported bound.");
		}
		return substepCount;
	}

	private double maxSpeedMetersPerSecond(VoxelSolidMask solidMask) {
		double max = 0.0;
		for (int i = 0; i < velocitiesWorldMetersPerSecond.size(); i++) {
			if (solidMask.isSolidCellIndex(i)) {
				continue;
			}
			max = Math.max(max, velocitiesWorldMetersPerSecond.get(i).length());
		}
		return max;
	}

	private int linearIndex(int xIndex, int yIndex, int zIndex) {
		if (xIndex < 0 || xIndex >= gridSpec.cellCountX()
				|| yIndex < 0 || yIndex >= gridSpec.cellCountY()
				|| zIndex < 0 || zIndex >= gridSpec.cellCountZ()) {
			throw new IndexOutOfBoundsException("cell index outside voxel grid.");
		}
		return (yIndex * gridSpec.cellCountZ() + zIndex) * gridSpec.cellCountX() + xIndex;
	}

	private double[] divergenceValues() {
		return divergenceValues(VoxelSolidMask.open(gridSpec));
	}

	private double[] divergenceValues(VoxelSolidMask solidMask) {
		double[] divergence = new double[velocitiesWorldMetersPerSecond.size()];
		for (int y = 0; y < gridSpec.cellCountY(); y++) {
			for (int z = 0; z < gridSpec.cellCountZ(); z++) {
				for (int x = 0; x < gridSpec.cellCountX(); x++) {
					divergence[linearIndex(x, y, z)] = divergenceAt(x, y, z, solidMask);
				}
			}
		}
		return divergence;
	}

	private double divergenceAt(int x, int y, int z) {
		return divergenceAt(x, y, z, VoxelSolidMask.open(gridSpec));
	}

	private double divergenceAt(int x, int y, int z, VoxelSolidMask solidMask) {
		int centerIndex = linearIndex(x, y, z);
		if (solidMask.isSolidCellIndex(centerIndex)) {
			return 0.0;
		}
		double dx = gridSpec.cellSizeMeters();
		Vec3 center = velocityAt(x, y, z);
		double east = x + 1 < gridSpec.cellCountX()
				? openFaceVelocity(solidMask, x + 1, y, z, 0.5 * (center.x() + velocityAt(x + 1, y, z).x()))
				: center.x();
		double west = x > 0
				? openFaceVelocity(solidMask, x - 1, y, z, 0.5 * (velocityAt(x - 1, y, z).x() + center.x()))
				: center.x();
		double up = y + 1 < gridSpec.cellCountY()
				? openFaceVelocity(solidMask, x, y + 1, z, 0.5 * (center.y() + velocityAt(x, y + 1, z).y()))
				: center.y();
		double down = y > 0
				? openFaceVelocity(solidMask, x, y - 1, z, 0.5 * (velocityAt(x, y - 1, z).y() + center.y()))
				: center.y();
		double south = z + 1 < gridSpec.cellCountZ()
				? openFaceVelocity(solidMask, x, y, z + 1, 0.5 * (center.z() + velocityAt(x, y, z + 1).z()))
				: center.z();
		double north = z > 0
				? openFaceVelocity(solidMask, x, y, z - 1, 0.5 * (velocityAt(x, y, z - 1).z() + center.z()))
				: center.z();
		return (east - west + up - down + south - north) / dx;
	}

	private Vec3 vorticityAtCell(int x, int y, int z, VoxelSolidMask solidMask) {
		int centerIndex = linearIndex(x, y, z);
		if (solidMask.isSolidCellIndex(centerIndex)
				|| solidMask.openVolumeFractionCellIndex(centerIndex) <= EPSILON) {
			return Vec3.ZERO;
		}
		double dVzDy = velocityDerivative(x, y, z, solidMask, 1, 2);
		double dVyDz = velocityDerivative(x, y, z, solidMask, 2, 1);
		double dVxDz = velocityDerivative(x, y, z, solidMask, 2, 0);
		double dVzDx = velocityDerivative(x, y, z, solidMask, 0, 2);
		double dVyDx = velocityDerivative(x, y, z, solidMask, 0, 1);
		double dVxDy = velocityDerivative(x, y, z, solidMask, 1, 0);
		return new Vec3(
				dVzDy - dVyDz,
				dVxDz - dVzDx,
				dVyDx - dVxDy
		);
	}

	private double velocityDerivative(
			int x,
			int y,
			int z,
			VoxelSolidMask solidMask,
			int axis,
			int component
	) {
		double dx = gridSpec.cellSizeMeters();
		double center = vectorComponent(velocityAt(x, y, z), component);
		int lowX = axis == 0 ? x - 1 : x;
		int lowY = axis == 1 ? y - 1 : y;
		int lowZ = axis == 2 ? z - 1 : z;
		int highX = axis == 0 ? x + 1 : x;
		int highY = axis == 1 ? y + 1 : y;
		int highZ = axis == 2 ? z + 1 : z;
		boolean hasLow = openNeighbor(solidMask, lowX, lowY, lowZ);
		boolean hasHigh = openNeighbor(solidMask, highX, highY, highZ);
		if (hasLow && hasHigh) {
			return (vectorComponent(velocityAt(highX, highY, highZ), component)
					- vectorComponent(velocityAt(lowX, lowY, lowZ), component)) / (2.0 * dx);
		}
		if (hasHigh) {
			return (vectorComponent(velocityAt(highX, highY, highZ), component) - center) / dx;
		}
		if (hasLow) {
			return (center - vectorComponent(velocityAt(lowX, lowY, lowZ), component)) / dx;
		}
		return 0.0;
	}

	private double[] pressurePotential(double[] divergence, int iterationCount) {
		return pressurePotential(divergence, iterationCount, VoxelSolidMask.open(gridSpec));
	}

	private double[] pressurePotential(double[] divergence, int iterationCount, VoxelSolidMask solidMask) {
		double meanDivergence = mean(divergence, solidMask);
		double dxSquared = gridSpec.cellSizeMeters() * gridSpec.cellSizeMeters();
		double[] previous = new double[divergence.length];
		double[] next = new double[divergence.length];
		for (int iteration = 0; iteration < iterationCount; iteration++) {
			for (int y = 0; y < gridSpec.cellCountY(); y++) {
				for (int z = 0; z < gridSpec.cellCountZ(); z++) {
					for (int x = 0; x < gridSpec.cellCountX(); x++) {
						int index = linearIndex(x, y, z);
						if (solidMask.isSolidCellIndex(index)) {
							next[index] = 0.0;
							continue;
						}
						int neighborCount = 0;
						double neighborSum = 0.0;
						if (openNeighbor(solidMask, x - 1, y, z)) {
							neighborSum += previous[linearIndex(x - 1, y, z)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x + 1, y, z)) {
							neighborSum += previous[linearIndex(x + 1, y, z)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x, y - 1, z)) {
							neighborSum += previous[linearIndex(x, y - 1, z)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x, y + 1, z)) {
							neighborSum += previous[linearIndex(x, y + 1, z)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x, y, z - 1)) {
							neighborSum += previous[linearIndex(x, y, z - 1)];
							neighborCount++;
						}
						if (openNeighbor(solidMask, x, y, z + 1)) {
							neighborSum += previous[linearIndex(x, y, z + 1)];
							neighborCount++;
						}
						next[index] = neighborCount == 0
								? 0.0
								: (neighborSum - (divergence[index] - meanDivergence) * dxSquared) / neighborCount;
					}
				}
			}
			subtractMean(next, solidMask);
			double[] swap = previous;
			previous = next;
			next = swap;
		}
		return previous;
	}

	private Vec3 projectedVelocityAt(double[] pressurePotential, int x, int y, int z) {
		return projectedVelocityAt(pressurePotential, VoxelSolidMask.open(gridSpec), x, y, z);
	}

	private Vec3 projectedVelocityAt(double[] pressurePotential, VoxelSolidMask solidMask, int x, int y, int z) {
		Vec3 center = velocityAt(x, y, z);
		double dx = gridSpec.cellSizeMeters();
		double lowX = center.x();
		double highX = center.x();
		if (solidNeighbor(solidMask, x - 1, y, z)) {
			lowX = 0.0;
		} else if (openNeighbor(solidMask, x - 1, y, z)) {
			lowX = 0.5 * (velocityAt(x - 1, y, z).x() + center.x())
					- (pressurePotential[linearIndex(x, y, z)]
					- pressurePotential[linearIndex(x - 1, y, z)]) / dx;
		}
		if (solidNeighbor(solidMask, x + 1, y, z)) {
			highX = 0.0;
		} else if (openNeighbor(solidMask, x + 1, y, z)) {
			highX = 0.5 * (center.x() + velocityAt(x + 1, y, z).x())
					- (pressurePotential[linearIndex(x + 1, y, z)]
					- pressurePotential[linearIndex(x, y, z)]) / dx;
		}
		double lowY = center.y();
		double highY = center.y();
		if (solidNeighbor(solidMask, x, y - 1, z)) {
			lowY = 0.0;
		} else if (openNeighbor(solidMask, x, y - 1, z)) {
			lowY = 0.5 * (velocityAt(x, y - 1, z).y() + center.y())
					- (pressurePotential[linearIndex(x, y, z)]
					- pressurePotential[linearIndex(x, y - 1, z)]) / dx;
		}
		if (solidNeighbor(solidMask, x, y + 1, z)) {
			highY = 0.0;
		} else if (openNeighbor(solidMask, x, y + 1, z)) {
			highY = 0.5 * (center.y() + velocityAt(x, y + 1, z).y())
					- (pressurePotential[linearIndex(x, y + 1, z)]
					- pressurePotential[linearIndex(x, y, z)]) / dx;
		}
		double lowZ = center.z();
		double highZ = center.z();
		if (solidNeighbor(solidMask, x, y, z - 1)) {
			lowZ = 0.0;
		} else if (openNeighbor(solidMask, x, y, z - 1)) {
			lowZ = 0.5 * (velocityAt(x, y, z - 1).z() + center.z())
					- (pressurePotential[linearIndex(x, y, z)]
					- pressurePotential[linearIndex(x, y, z - 1)]) / dx;
		}
		if (solidNeighbor(solidMask, x, y, z + 1)) {
			highZ = 0.0;
		} else if (openNeighbor(solidMask, x, y, z + 1)) {
			highZ = 0.5 * (center.z() + velocityAt(x, y, z + 1).z())
					- (pressurePotential[linearIndex(x, y, z + 1)]
					- pressurePotential[linearIndex(x, y, z)]) / dx;
		}
		return new Vec3(
				0.5 * (lowX + highX),
				0.5 * (lowY + highY),
				0.5 * (lowZ + highZ)
		);
	}

	private Vec3 sampleVelocityClamped(Vec3 pointWorldMeters) {
		return sampleVelocityClamped(pointWorldMeters, VoxelSolidMask.open(gridSpec));
	}

	private Vec3 sampleVelocityClamped(Vec3 pointWorldMeters, VoxelSolidMask solidMask) {
		double fx = fractionalCellIndex(pointWorldMeters.x(), gridSpec.originWorldMeters().x(), gridSpec.cellCountX());
		double fy = fractionalCellIndex(pointWorldMeters.y(), gridSpec.originWorldMeters().y(), gridSpec.cellCountY());
		double fz = fractionalCellIndex(pointWorldMeters.z(), gridSpec.originWorldMeters().z(), gridSpec.cellCountZ());
		int x0 = (int) Math.floor(fx);
		int y0 = (int) Math.floor(fy);
		int z0 = (int) Math.floor(fz);
		int x1 = Math.min(x0 + 1, gridSpec.cellCountX() - 1);
		int y1 = Math.min(y0 + 1, gridSpec.cellCountY() - 1);
		int z1 = Math.min(z0 + 1, gridSpec.cellCountZ() - 1);
		double tx = fx - x0;
		double ty = fy - y0;
		double tz = fz - z0;
		if (solidMask.solidCellCount() > 0) {
			return sampleOpenVelocityWeighted(solidMask, x0, y0, z0, x1, y1, z1, tx, ty, tz);
		}
		Vec3 x00 = lerp(velocityAt(x0, y0, z0), velocityAt(x1, y0, z0), tx);
		Vec3 x10 = lerp(velocityAt(x0, y1, z0), velocityAt(x1, y1, z0), tx);
		Vec3 x01 = lerp(velocityAt(x0, y0, z1), velocityAt(x1, y0, z1), tx);
		Vec3 x11 = lerp(velocityAt(x0, y1, z1), velocityAt(x1, y1, z1), tx);
		Vec3 y0Blend = lerp(x00, x10, ty);
		Vec3 y1Blend = lerp(x01, x11, ty);
		return lerp(y0Blend, y1Blend, tz);
	}

	private Vec3 sampleOpenVelocityWeighted(
			VoxelSolidMask solidMask,
			int x0,
			int y0,
			int z0,
			int x1,
			int y1,
			int z1,
			double tx,
			double ty,
			double tz
	) {
		Vec3 weighted = Vec3.ZERO;
		double totalWeight = 0.0;
		double wx0 = 1.0 - tx;
		double wx1 = tx;
		double wy0 = 1.0 - ty;
		double wy1 = ty;
		double wz0 = 1.0 - tz;
		double wz1 = tz;
		WeightedVelocitySample sample = weightedOpenSample(solidMask, x0, y0, z0, wx0 * wy0 * wz0);
		weighted = weighted.add(sample.weightedVelocity());
		totalWeight += sample.weight();
		sample = weightedOpenSample(solidMask, x1, y0, z0, wx1 * wy0 * wz0);
		weighted = weighted.add(sample.weightedVelocity());
		totalWeight += sample.weight();
		sample = weightedOpenSample(solidMask, x0, y1, z0, wx0 * wy1 * wz0);
		weighted = weighted.add(sample.weightedVelocity());
		totalWeight += sample.weight();
		sample = weightedOpenSample(solidMask, x1, y1, z0, wx1 * wy1 * wz0);
		weighted = weighted.add(sample.weightedVelocity());
		totalWeight += sample.weight();
		sample = weightedOpenSample(solidMask, x0, y0, z1, wx0 * wy0 * wz1);
		weighted = weighted.add(sample.weightedVelocity());
		totalWeight += sample.weight();
		sample = weightedOpenSample(solidMask, x1, y0, z1, wx1 * wy0 * wz1);
		weighted = weighted.add(sample.weightedVelocity());
		totalWeight += sample.weight();
		sample = weightedOpenSample(solidMask, x0, y1, z1, wx0 * wy1 * wz1);
		weighted = weighted.add(sample.weightedVelocity());
		totalWeight += sample.weight();
		sample = weightedOpenSample(solidMask, x1, y1, z1, wx1 * wy1 * wz1);
		weighted = weighted.add(sample.weightedVelocity());
		totalWeight += sample.weight();
		return totalWeight <= EPSILON ? Vec3.ZERO : weighted.multiply(1.0 / totalWeight);
	}

	private WeightedVelocitySample weightedOpenSample(
			VoxelSolidMask solidMask,
			int x,
			int y,
			int z,
			double weight
	) {
		int index = linearIndex(x, y, z);
		if (weight <= EPSILON || solidMask.isSolidCellIndex(index)) {
			return new WeightedVelocitySample(Vec3.ZERO, 0.0);
		}
		return new WeightedVelocitySample(velocityAt(x, y, z).multiply(weight), weight);
	}

	private record WeightedVelocitySample(Vec3 weightedVelocity, double weight) {
	}

	private double fractionalCellIndex(double worldCoordinate, double originCoordinate, int cellCount) {
		double raw = (worldCoordinate - originCoordinate) / gridSpec.cellSizeMeters() - 0.5;
		return MathUtil.clamp(raw, 0.0, Math.max(0, cellCount - 1));
	}

	private double openFaceVelocity(VoxelSolidMask solidMask, int x, int y, int z, double openFaceVelocity) {
		return solidMask.isSolidCellIndex(linearIndex(x, y, z)) ? 0.0 : openFaceVelocity;
	}

	private boolean openNeighbor(VoxelSolidMask solidMask, int x, int y, int z) {
		return x >= 0 && x < gridSpec.cellCountX()
				&& y >= 0 && y < gridSpec.cellCountY()
				&& z >= 0 && z < gridSpec.cellCountZ()
				&& !solidMask.isSolidCellIndex(linearIndex(x, y, z));
	}

	private boolean solidNeighbor(VoxelSolidMask solidMask, int x, int y, int z) {
		return x >= 0 && x < gridSpec.cellCountX()
				&& y >= 0 && y < gridSpec.cellCountY()
				&& z >= 0 && z < gridSpec.cellCountZ()
				&& solidMask.isSolidCellIndex(linearIndex(x, y, z));
	}

	private void validateSolidMask(VoxelSolidMask solidMask) {
		if (solidMask == null) {
			throw new IllegalArgumentException("solidMask must not be null.");
		}
		if (!gridSpec.equals(solidMask.gridSpec())) {
			throw new IllegalArgumentException("solidMask grid must match this flow state.");
		}
	}

	private static Vec3 lerp(Vec3 first, Vec3 second, double t) {
		return first.multiply(1.0 - t).add(second.multiply(t));
	}

	private static double vectorComponent(Vec3 vector, int component) {
		return switch (component) {
			case 0 -> vector.x();
			case 1 -> vector.y();
			case 2 -> vector.z();
			default -> throw new IllegalArgumentException("component must be x, y, or z.");
		};
	}

	private void accumulateDiffusiveExchange(
			ArrayList<Vec3> deltas,
			int firstIndex,
			int secondIndex,
			double diffusionNumber
	) {
		accumulateDiffusiveExchange(
				deltas,
				firstIndex,
				secondIndex,
				diffusionNumber,
				VoxelSolidMask.open(gridSpec));
	}

	private void accumulateDiffusiveExchange(
			ArrayList<Vec3> deltas,
			int firstIndex,
			int secondIndex,
			double diffusionNumber,
			VoxelSolidMask solidMask
	) {
		if (diffusionNumber <= EPSILON) {
			return;
		}
		if (solidMask.isSolidCellIndex(firstIndex) || solidMask.isSolidCellIndex(secondIndex)) {
			return;
		}
		Vec3 exchange = velocitiesWorldMetersPerSecond.get(secondIndex)
				.subtract(velocitiesWorldMetersPerSecond.get(firstIndex))
				.multiply(diffusionNumber);
		deltas.set(firstIndex, deltas.get(firstIndex).add(exchange));
		deltas.set(secondIndex, deltas.get(secondIndex).subtract(exchange));
	}

	private static Vec3 finiteVecOrZero(Vec3 value) {
		return value == null || !value.isFinite() ? Vec3.ZERO : value;
	}

	private static double finiteNonnegative(double value) {
		return Double.isFinite(value) && value > 0.0 ? value : 0.0;
	}

	private static double mean(double[] values) {
		if (values.length == 0) {
			return 0.0;
		}
		double sum = 0.0;
		for (double value : values) {
			sum += value;
		}
		return sum / values.length;
	}

	private static double mean(double[] values, VoxelSolidMask solidMask) {
		if (values.length == 0) {
			return 0.0;
		}
		double sum = 0.0;
		int count = 0;
		for (int i = 0; i < values.length; i++) {
			if (solidMask.isSolidCellIndex(i)) {
				continue;
			}
			sum += values[i];
			count++;
		}
		return count == 0 ? 0.0 : sum / count;
	}

	private static void subtractMean(double[] values) {
		double mean = mean(values);
		for (int i = 0; i < values.length; i++) {
			values[i] -= mean;
		}
	}

	private static void subtractMean(double[] values, VoxelSolidMask solidMask) {
		double mean = mean(values, solidMask);
		for (int i = 0; i < values.length; i++) {
			if (solidMask.isSolidCellIndex(i)) {
				values[i] = 0.0;
			} else {
				values[i] -= mean;
			}
		}
	}
}
