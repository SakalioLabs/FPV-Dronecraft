package com.tenicana.dronecraft.sim;

public final class PidController {
	private PidGains gains;
	private double integrator;
	private double previousError;
	private double filteredDerivative;
	private boolean hasPreviousError;
	private boolean hasFilteredDerivative;
	private double lastProportionalTerm;
	private double lastIntegralTerm;
	private double lastDerivativeTerm;
	private double lastFeedForwardTerm;
	private double lastOutput;

	public PidController(PidGains gains) {
		this.gains = gains;
	}

	public void setGains(PidGains gains) {
		this.gains = gains;
		this.integrator = MathUtil.clamp(integrator, -gains.integratorLimit(), gains.integratorLimit());
	}

	public double step(double error, double dt) {
		return step(error, dt, 1.0, 0.0, 1.0, 0.0);
	}

	public double step(double error, double dt, double pScale, double integratorBoost, double dScale, double feedForward) {
		return step(error, dt, pScale, integratorBoost, dScale, feedForward, 1.0);
	}

	public double step(
			double error,
			double dt,
			double pScale,
			double integratorBoost,
			double dScale,
			double feedForward,
			double integratorAttenuation
	) {
		double safeDt = Math.max(dt, 1.0e-6);
		double derivative = hasPreviousError ? (error - previousError) / safeDt : 0.0;
		return stepWithDerivativeInput(
				error,
				derivative,
				safeDt,
				pScale,
				integratorBoost,
				dScale,
				feedForward,
				integratorAttenuation
		);
	}

	public double stepWithDerivativeInput(
			double error,
			double derivativeInput,
			double dt,
			double pScale,
			double integratorBoost,
			double dScale,
			double feedForward,
			double integratorAttenuation
	) {
		return stepWithDerivativeInput(
				error,
				derivativeInput,
				dt,
				pScale,
				integratorBoost,
				dScale,
				feedForward,
				integratorAttenuation,
				gains.dTermLowPassCutoffHz()
		);
	}

	public double stepWithDerivativeInput(
			double error,
			double derivativeInput,
			double dt,
			double pScale,
			double integratorBoost,
			double dScale,
			double feedForward,
			double integratorAttenuation,
			double dTermLowPassCutoffHz
	) {
		double safeDt = Math.max(dt, 1.0e-6);
		double relax = 1.0 - MathUtil.clamp(integratorAttenuation, 0.0, 1.0);
		if (relax > 1.0e-6) {
			integrator *= 1.0 - MathUtil.clamp(relax * safeDt * 8.0, 0.0, 0.35);
		}
		double integratorScale = (1.0 + Math.max(0.0, integratorBoost)) * MathUtil.clamp(integratorAttenuation, 0.0, 1.0);
		integrator = MathUtil.clamp(
				integrator + error * safeDt * integratorScale,
				-gains.integratorLimit(),
				gains.integratorLimit()
		);
		double dTerm = filteredDerivative(derivativeInput, safeDt, dTermLowPassCutoffHz);
		previousError = error;
		hasPreviousError = true;
		lastProportionalTerm = gains.p() * Math.max(0.0, pScale) * error;
		lastIntegralTerm = gains.i() * integrator;
		lastDerivativeTerm = gains.d() * Math.max(0.0, dScale) * dTerm;
		lastFeedForwardTerm = feedForward;
		lastOutput = lastProportionalTerm + lastIntegralTerm + lastDerivativeTerm + lastFeedForwardTerm;
		return lastOutput;
	}

	private double filteredDerivative(double derivative, double dt) {
		return filteredDerivative(derivative, dt, gains.dTermLowPassCutoffHz());
	}

	private double filteredDerivative(double derivative, double dt, double cutoffHz) {
		cutoffHz = MathUtil.clamp(cutoffHz, 0.0, 1000.0);
		if (cutoffHz <= 0.0) {
			filteredDerivative = derivative;
			hasFilteredDerivative = true;
			return derivative;
		}

		if (!hasFilteredDerivative) {
			filteredDerivative = derivative;
			hasFilteredDerivative = true;
			return filteredDerivative;
		}

		double timeConstantSeconds = 1.0 / (Math.PI * 2.0 * cutoffHz);
		double alpha = MathUtil.expSmoothing(dt, timeConstantSeconds);
		filteredDerivative += (derivative - filteredDerivative) * alpha;
		return filteredDerivative;
	}

	public void reset() {
		integrator = 0.0;
		previousError = 0.0;
		filteredDerivative = 0.0;
		hasPreviousError = false;
		hasFilteredDerivative = false;
		lastProportionalTerm = 0.0;
		lastIntegralTerm = 0.0;
		lastDerivativeTerm = 0.0;
		lastFeedForwardTerm = 0.0;
		lastOutput = 0.0;
	}

	public double lastProportionalTerm() {
		return lastProportionalTerm;
	}

	public double lastIntegralTerm() {
		return lastIntegralTerm;
	}

	public double lastDerivativeTerm() {
		return lastDerivativeTerm;
	}

	public double lastFeedForwardTerm() {
		return lastFeedForwardTerm;
	}

	public double lastOutput() {
		return lastOutput;
	}
}
