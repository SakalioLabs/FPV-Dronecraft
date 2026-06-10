package com.tenicana.dronecraft.sim;

public record PidGains(
		double p,
		double i,
		double d,
		double integratorLimit,
		double feedForward,
		double dTermLowPassCutoffHz,
		double antiGravityGain,
		double tpaBreakpoint,
		double tpaStrength
) {
	public PidGains(double p, double i, double d, double integratorLimit) {
		this(p, i, d, integratorLimit, 0.0, 0.0, 0.0, 1.0, 0.0);
	}

	public PidGains {
		p = Math.max(0.0, p);
		i = Math.max(0.0, i);
		d = Math.max(0.0, d);
		integratorLimit = Math.max(0.0, integratorLimit);
		feedForward = MathUtil.clamp(feedForward, 0.0, 0.01);
		dTermLowPassCutoffHz = MathUtil.clamp(dTermLowPassCutoffHz, 0.0, 1000.0);
		antiGravityGain = MathUtil.clamp(antiGravityGain, 0.0, 8.0);
		tpaBreakpoint = MathUtil.clamp(tpaBreakpoint, 0.0, 1.0);
		tpaStrength = MathUtil.clamp(tpaStrength, 0.0, 0.95);
	}
}
