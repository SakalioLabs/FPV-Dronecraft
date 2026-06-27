package com.aerodynamics4mc.api;

import java.util.Arrays;

public final class AeroL2Request {
	private static final int FLOW_CHANNELS = 4;
	private final int nx;
	private final int ny;
	private final int nz;
	private final float dxMeters;
	private final float dtSeconds;
	private final int steps;
	private final int sampleStride;
	private final float inletVx;
	private final float inletVy;
	private final float inletVz;
	private final float densityKgM3;
	private final float kinematicViscosityM2S;
	private final byte[] solidMask;
	private final float[] initialFlowState;
	private final boolean outputFlowAtlas;
	private final boolean computeForceMoment;
	private final float referenceX;
	private final float referenceY;
	private final float referenceZ;

	private AeroL2Request(Builder builder) {
		this.nx = builder.nx;
		this.ny = builder.ny;
		this.nz = builder.nz;
		this.dxMeters = builder.dxMeters;
		this.dtSeconds = builder.dtSeconds;
		this.steps = builder.steps;
		this.sampleStride = builder.sampleStride;
		this.inletVx = builder.inletVx;
		this.inletVy = builder.inletVy;
		this.inletVz = builder.inletVz;
		this.densityKgM3 = builder.densityKgM3;
		this.kinematicViscosityM2S = builder.kinematicViscosityM2S;
		this.solidMask = builder.solidMask == null ? null : builder.solidMask.clone();
		this.initialFlowState = builder.initialFlowState == null ? null : builder.initialFlowState.clone();
		this.outputFlowAtlas = builder.outputFlowAtlas;
		this.computeForceMoment = builder.computeForceMoment;
		this.referenceX = builder.referenceX;
		this.referenceY = builder.referenceY;
		this.referenceZ = builder.referenceZ;
	}

	public static Builder builder(int nx, int ny, int nz) {
		return new Builder(nx, ny, nz);
	}

	public static byte[] createSolidMask(int nx, int ny, int nz) {
		return new byte[cellCount(nx, ny, nz)];
	}

	public static float[] createFlowState(int nx, int ny, int nz) {
		return new float[cellCount(nx, ny, nz) * FLOW_CHANNELS];
	}

	public static int cellIndex(int nx, int ny, int nz, int x, int y, int z) {
		return (x * ny + y) * nz + z;
	}

	public static void fillUniformFlow(float[] flowState, byte[] solidMask, float vx, float vy, float vz, float pressure) {
		for (int cell = 0; cell < flowState.length / FLOW_CHANNELS; cell++) {
			int base = cell * FLOW_CHANNELS;
			if (solidMask != null && solidMask[cell] != 0) {
				flowState[base] = 0.0f;
				flowState[base + 1] = 0.0f;
				flowState[base + 2] = 0.0f;
				flowState[base + 3] = 0.0f;
			} else {
				flowState[base] = vx;
				flowState[base + 1] = vy;
				flowState[base + 2] = vz;
				flowState[base + 3] = pressure;
			}
		}
	}

	public int nx() {
		return nx;
	}

	public int ny() {
		return ny;
	}

	public int nz() {
		return nz;
	}

	public float dxMeters() {
		return dxMeters;
	}

	public float dtSeconds() {
		return dtSeconds;
	}

	public int steps() {
		return steps;
	}

	public int sampleStride() {
		return sampleStride;
	}

	public float inletVx() {
		return inletVx;
	}

	public float inletVy() {
		return inletVy;
	}

	public float inletVz() {
		return inletVz;
	}

	public float densityKgM3() {
		return densityKgM3;
	}

	public float kinematicViscosityM2S() {
		return kinematicViscosityM2S;
	}

	public byte[] solidMask() {
		return solidMask == null ? null : solidMask.clone();
	}

	public boolean hasInitialFlowState() {
		return initialFlowState != null;
	}

	public float[] initialFlowState() {
		return initialFlowState == null ? null : initialFlowState.clone();
	}

	public boolean outputFlowAtlas() {
		return outputFlowAtlas;
	}

	public boolean computeForceMoment() {
		return computeForceMoment;
	}

	public float referenceX() {
		return referenceX;
	}

	public float referenceY() {
		return referenceY;
	}

	public float referenceZ() {
		return referenceZ;
	}

	private static int cellCount(int nx, int ny, int nz) {
		return nx * ny * nz;
	}

	public static final class Builder {
		private final int nx;
		private final int ny;
		private final int nz;
		private float dxMeters = 1.0f;
		private float dtSeconds = 0.05f;
		private int steps = 1;
		private int sampleStride = 1;
		private float inletVx;
		private float inletVy;
		private float inletVz;
		private float densityKgM3 = 1.225f;
		private float kinematicViscosityM2S = 1.5e-5f;
		private byte[] solidMask;
		private float[] initialFlowState;
		private boolean outputFlowAtlas = true;
		private boolean computeForceMoment;
		private float referenceX;
		private float referenceY;
		private float referenceZ;

		private Builder(int nx, int ny, int nz) {
			this.nx = nx;
			this.ny = ny;
			this.nz = nz;
		}

		public Builder cellSizeMeters(float dxMeters) {
			this.dxMeters = dxMeters;
			return this;
		}

		public Builder timeStepSeconds(float dtSeconds) {
			this.dtSeconds = dtSeconds;
			return this;
		}

		public Builder steps(int steps) {
			this.steps = steps;
			return this;
		}

		public Builder sampleStride(int sampleStride) {
			this.sampleStride = sampleStride;
			return this;
		}

		public Builder inlet(float vx, float vy, float vz) {
			this.inletVx = vx;
			this.inletVy = vy;
			this.inletVz = vz;
			return this;
		}

		public Builder air(float densityKgM3, float kinematicViscosityM2S) {
			this.densityKgM3 = densityKgM3;
			this.kinematicViscosityM2S = kinematicViscosityM2S;
			return this;
		}

		public Builder solidMask(byte[] solidMask) {
			this.solidMask = solidMask == null ? null : Arrays.copyOf(solidMask, solidMask.length);
			return this;
		}

		public Builder initialFlowState(float[] initialFlowState) {
			this.initialFlowState = initialFlowState == null ? null : Arrays.copyOf(initialFlowState, initialFlowState.length);
			return this;
		}

		public Builder outputFlowAtlas(boolean outputFlowAtlas) {
			this.outputFlowAtlas = outputFlowAtlas;
			return this;
		}

		public Builder computeForceMoment(boolean computeForceMoment) {
			this.computeForceMoment = computeForceMoment;
			return this;
		}

		public Builder forceMomentReference(float x, float y, float z) {
			this.computeForceMoment = true;
			this.referenceX = x;
			this.referenceY = y;
			this.referenceZ = z;
			return this;
		}

		public AeroL2Request build() {
			return new AeroL2Request(this);
		}
	}
}
