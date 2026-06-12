package com.tenicana.dronecraft.sim;

public enum EscCommandProtocol {
	GENERIC(0.0, 0, 0),
	DSHOT150(150.0, 16, 2000),
	DSHOT300(300.0, 16, 2000),
	DSHOT600(600.0, 16, 2000);

	private final double bitrateKilobitsPerSecond;
	private final int bitsPerFrame;
	private final int throttleSteps;

	EscCommandProtocol(double bitrateKilobitsPerSecond, int bitsPerFrame, int throttleSteps) {
		this.bitrateKilobitsPerSecond = bitrateKilobitsPerSecond;
		this.bitsPerFrame = bitsPerFrame;
		this.throttleSteps = throttleSteps;
	}

	public boolean digital() {
		return bitrateKilobitsPerSecond > 0.0 && bitsPerFrame > 0;
	}

	public double bitrateKilobitsPerSecond() {
		return bitrateKilobitsPerSecond;
	}

	public int bitsPerFrame() {
		return bitsPerFrame;
	}

	public int throttleSteps() {
		return throttleSteps;
	}

	public double rawFrameSeconds() {
		return digital() ? bitsPerFrame / (bitrateKilobitsPerSecond * 1000.0) : 0.0;
	}

	public double rawFrameMicroseconds() {
		return rawFrameSeconds() * 1_000_000.0;
	}

	public double rawFrameRateHertz() {
		double seconds = rawFrameSeconds();
		return seconds > 0.0 ? 1.0 / seconds : 0.0;
	}

	public double commandWireUtilization(double commandFrameRateHertz) {
		if (!digital() || !Double.isFinite(commandFrameRateHertz) || commandFrameRateHertz <= 0.0) {
			return 0.0;
		}
		return MathUtil.clamp(commandFrameRateHertz * rawFrameSeconds(), 0.0, 1.0);
	}

	public double commandIntervalRawFrameRatio(double commandFrameRateHertz) {
		if (!digital() || !Double.isFinite(commandFrameRateHertz) || commandFrameRateHertz <= 0.0) {
			return 0.0;
		}
		return 1.0 / commandFrameRateHertz / rawFrameSeconds();
	}

	public EscCommandProtocol normalizedForResolution(int resolutionSteps) {
		if (digital() && resolutionSteps != throttleSteps) {
			return GENERIC;
		}
		return this;
	}

	public static EscCommandProtocol fromBitrateKilobitsPerSecond(double bitrateKilobitsPerSecond) {
		if (!Double.isFinite(bitrateKilobitsPerSecond) || bitrateKilobitsPerSecond <= 0.0) {
			return GENERIC;
		}
		if (Math.abs(bitrateKilobitsPerSecond - DSHOT150.bitrateKilobitsPerSecond) < 75.0) {
			return DSHOT150;
		}
		if (Math.abs(bitrateKilobitsPerSecond - DSHOT300.bitrateKilobitsPerSecond) < 150.0) {
			return DSHOT300;
		}
		return DSHOT600;
	}
}
