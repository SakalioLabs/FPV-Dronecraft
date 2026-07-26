package com.tenicana.dronecraft.acoustics.wave;

import java.util.Arrays;

/**
 * Deterministic two-dimensional linear-acoustic FDTD reference.
 *
 * <p>This is an offline numerical oracle, not a Minecraft runtime component.
 * Rigid cells close every velocity face that touches the solid. The outer
 * domain uses a polynomial sponge; it is deliberately named as such and must
 * pass a reflection gate before it can be treated as an adequate absorbing
 * boundary.</p>
 */
public final class StaggeredGridFdtSolver {
	private final WaveGrid2d grid;
	private final boolean[] solid;
	private final double[] pressure;
	private final double[] horizontalVelocity;
	private final double[] verticalVelocity;
	private final double[] pressureDamping;
	private final double[] horizontalVelocityDamping;
	private final double[] verticalVelocityDamping;

	public StaggeredGridFdtSolver(
			WaveGrid2d grid,
			int spongeWidthCells,
			double maximumSpongeDecayPerSecond
	) {
		if (spongeWidthCells < 0
				|| spongeWidthCells * 2 >= Math.min(
						grid.widthCells(),
						grid.heightCells()
				)) {
			throw new IllegalArgumentException(
					"spongeWidthCells must leave an interior fluid region"
			);
		}
		if (!(maximumSpongeDecayPerSecond >= 0.0)
				|| !Double.isFinite(maximumSpongeDecayPerSecond)) {
			throw new IllegalArgumentException(
					"maximumSpongeDecayPerSecond must be finite and non-negative"
			);
		}
		this.grid = grid;
		solid = new boolean[grid.widthCells() * grid.heightCells()];
		pressure = new double[solid.length];
		horizontalVelocity =
				new double[(grid.widthCells() + 1) * grid.heightCells()];
		verticalVelocity =
				new double[grid.widthCells() * (grid.heightCells() + 1)];
		pressureDamping = createPressureDamping(
				spongeWidthCells,
				maximumSpongeDecayPerSecond
		);
		horizontalVelocityDamping =
				createHorizontalVelocityDamping(pressureDamping);
		verticalVelocityDamping =
				createVerticalVelocityDamping(pressureDamping);
	}

	public WaveGrid2d grid() {
		return grid;
	}

	public void setSolidCell(int xCell, int yCell, boolean value) {
		requireCell(xCell, yCell);
		solid[pressureIndex(xCell, yCell)] = value;
	}

	public void setSolidRectangle(
			int minimumXCell,
			int minimumYCell,
			int maximumXCellExclusive,
			int maximumYCellExclusive
	) {
		if (minimumXCell < 0 || minimumYCell < 0
				|| maximumXCellExclusive > grid.widthCells()
				|| maximumYCellExclusive > grid.heightCells()
				|| minimumXCell >= maximumXCellExclusive
				|| minimumYCell >= maximumYCellExclusive) {
			throw new IllegalArgumentException("invalid solid rectangle");
		}
		for (int yCell = minimumYCell;
				yCell < maximumYCellExclusive;
				yCell++) {
			for (int xCell = minimumXCell;
					xCell < maximumXCellExclusive;
					xCell++) {
				solid[pressureIndex(xCell, yCell)] = true;
			}
		}
	}

	public boolean isSolidCell(int xCell, int yCell) {
		requireCell(xCell, yCell);
		return solid[pressureIndex(xCell, yCell)];
	}

	public double[] run(
			GridPoint source,
			double[] sourcePressureSamples,
			GridPoint receiver
	) {
		return run(source, sourcePressureSamples, new GridPoint[] {receiver})[0];
	}

	public double[][] run(
			GridPoint source,
			double[] sourcePressureSamples,
			GridPoint... receivers
	) {
		requireFluidPoint(source, "source");
		if (receivers.length == 0) {
			throw new IllegalArgumentException("at least one receiver is required");
		}
		int[] receiverIndices = new int[receivers.length];
		for (int receiver = 0; receiver < receivers.length; receiver++) {
			requireFluidPoint(receivers[receiver], "receiver");
			receiverIndices[receiver] = pressureIndex(
					receivers[receiver].xCell(),
					receivers[receiver].yCell()
			);
		}
		if (sourcePressureSamples.length == 0) {
			throw new IllegalArgumentException(
					"sourcePressureSamples must not be empty"
			);
		}
		resetState();
		double[][] receiverPressure =
				new double[receivers.length][sourcePressureSamples.length];
		int sourceIndex = pressureIndex(source.xCell(), source.yCell());
		for (int sample = 0; sample < sourcePressureSamples.length; sample++) {
			if (!Double.isFinite(sourcePressureSamples[sample])) {
				throw new IllegalArgumentException(
						"sourcePressureSamples must be finite"
				);
			}
			stepVelocity();
			stepPressure();
			pressure[sourceIndex] += sourcePressureSamples[sample];
			for (int receiver = 0; receiver < receiverIndices.length; receiver++) {
				receiverPressure[receiver][sample] =
						pressure[receiverIndices[receiver]];
			}
		}
		return receiverPressure;
	}

	private void stepVelocity() {
		double scale = grid.timeStepSeconds()
				/ (grid.airDensityKilogramsPerCubicMeter()
				* grid.cellSizeMeters());
		int width = grid.widthCells();
		int height = grid.heightCells();
		for (int yCell = 0; yCell < height; yCell++) {
			int velocityRow = yCell * (width + 1);
			for (int faceX = 1; faceX < width; faceX++) {
				int velocityIndex = velocityRow + faceX;
				int leftPressureIndex = pressureIndex(faceX - 1, yCell);
				int rightPressureIndex = pressureIndex(faceX, yCell);
				if (solid[leftPressureIndex] || solid[rightPressureIndex]) {
					horizontalVelocity[velocityIndex] = 0.0;
				} else {
					horizontalVelocity[velocityIndex] =
							horizontalVelocityDamping[velocityIndex]
							* (
									horizontalVelocity[velocityIndex]
									- scale * (
											pressure[rightPressureIndex]
											- pressure[leftPressureIndex]
									)
							);
				}
			}
		}
		for (int faceY = 1; faceY < height; faceY++) {
			int lowerPressureRow = (faceY - 1) * width;
			int upperPressureRow = faceY * width;
			int velocityRow = faceY * width;
			for (int xCell = 0; xCell < width; xCell++) {
				int velocityIndex = velocityRow + xCell;
				int lowerPressureIndex = lowerPressureRow + xCell;
				int upperPressureIndex = upperPressureRow + xCell;
				if (solid[lowerPressureIndex] || solid[upperPressureIndex]) {
					verticalVelocity[velocityIndex] = 0.0;
				} else {
					verticalVelocity[velocityIndex] =
							verticalVelocityDamping[velocityIndex]
							* (
									verticalVelocity[velocityIndex]
									- scale * (
											pressure[upperPressureIndex]
											- pressure[lowerPressureIndex]
									)
							);
				}
			}
		}
	}

	private void stepPressure() {
		double scale = grid.airDensityKilogramsPerCubicMeter()
				* Math.pow(grid.speedOfSoundMetersPerSecond(), 2.0)
				* grid.timeStepSeconds() / grid.cellSizeMeters();
		int width = grid.widthCells();
		int height = grid.heightCells();
		for (int yCell = 0; yCell < height; yCell++) {
			int pressureRow = yCell * width;
			int horizontalVelocityRow = yCell * (width + 1);
			int lowerVerticalVelocityRow = yCell * width;
			int upperVerticalVelocityRow = (yCell + 1) * width;
			for (int xCell = 0; xCell < width; xCell++) {
				int pressureIndex = pressureRow + xCell;
				if (solid[pressureIndex]) {
					pressure[pressureIndex] = 0.0;
					continue;
				}
				double divergence =
						horizontalVelocity[horizontalVelocityRow + xCell + 1]
						- horizontalVelocity[horizontalVelocityRow + xCell]
						+ verticalVelocity[upperVerticalVelocityRow + xCell]
						- verticalVelocity[lowerVerticalVelocityRow + xCell];
				pressure[pressureIndex] = pressureDamping[pressureIndex]
						* (pressure[pressureIndex] - scale * divergence);
			}
		}
	}

	private double[] createPressureDamping(
			int spongeWidthCells,
			double maximumSpongeDecayPerSecond
	) {
		double[] damping =
				new double[grid.widthCells() * grid.heightCells()];
		for (int yCell = 0; yCell < grid.heightCells(); yCell++) {
			for (int xCell = 0; xCell < grid.widthCells(); xCell++) {
				int boundaryDistance = Math.min(
						Math.min(xCell, grid.widthCells() - 1 - xCell),
						Math.min(yCell, grid.heightCells() - 1 - yCell)
				);
				double normalizedDepth = spongeDepth(
						boundaryDistance,
						spongeWidthCells
				);
				double decay = maximumSpongeDecayPerSecond
						* normalizedDepth * normalizedDepth;
				damping[pressureIndex(xCell, yCell)] =
						Math.exp(-decay * grid.timeStepSeconds());
			}
		}
		return damping;
	}

	private double[] createHorizontalVelocityDamping(double[] cellDamping) {
		int width = grid.widthCells();
		int height = grid.heightCells();
		double[] damping = new double[(width + 1) * height];
		for (int yCell = 0; yCell < height; yCell++) {
			int velocityRow = yCell * (width + 1);
			for (int faceX = 0; faceX <= width; faceX++) {
				double left = faceX == 0
						? cellDamping[pressureIndex(0, yCell)]
						: cellDamping[pressureIndex(faceX - 1, yCell)];
				double right = faceX == width
						? cellDamping[pressureIndex(width - 1, yCell)]
						: cellDamping[pressureIndex(faceX, yCell)];
				damping[velocityRow + faceX] = Math.sqrt(left * right);
			}
		}
		return damping;
	}

	private double[] createVerticalVelocityDamping(double[] cellDamping) {
		int width = grid.widthCells();
		int height = grid.heightCells();
		double[] damping = new double[width * (height + 1)];
		for (int faceY = 0; faceY <= height; faceY++) {
			int velocityRow = faceY * width;
			for (int xCell = 0; xCell < width; xCell++) {
				double lower = faceY == 0
						? cellDamping[pressureIndex(xCell, 0)]
						: cellDamping[pressureIndex(xCell, faceY - 1)];
				double upper = faceY == height
						? cellDamping[pressureIndex(xCell, height - 1)]
						: cellDamping[pressureIndex(xCell, faceY)];
				damping[velocityRow + xCell] = Math.sqrt(lower * upper);
			}
		}
		return damping;
	}

	private static double spongeDepth(
			int boundaryDistanceCells,
			int spongeWidthCells
	) {
		if (spongeWidthCells == 0 || boundaryDistanceCells >= spongeWidthCells) {
			return 0.0;
		}
		return (spongeWidthCells - boundaryDistanceCells)
				/ (double) spongeWidthCells;
	}

	private void resetState() {
		Arrays.fill(pressure, 0.0);
		Arrays.fill(horizontalVelocity, 0.0);
		Arrays.fill(verticalVelocity, 0.0);
	}

	private void requireFluidPoint(GridPoint point, String name) {
		requireCell(point.xCell(), point.yCell());
		if (solid[pressureIndex(point.xCell(), point.yCell())]) {
			throw new IllegalArgumentException(name + " must be in a fluid cell");
		}
	}

	private void requireCell(int xCell, int yCell) {
		if (xCell < 0 || xCell >= grid.widthCells()
				|| yCell < 0 || yCell >= grid.heightCells()) {
			throw new IllegalArgumentException("cell is outside the grid");
		}
	}

	private int pressureIndex(int xCell, int yCell) {
		return yCell * grid.widthCells() + xCell;
	}
}
