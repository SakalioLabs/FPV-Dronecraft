package com.tenicana.dronecraft.control;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.tenicana.dronecraft.sim.DroneConfig;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.DroneState;
import com.tenicana.dronecraft.sim.FlightMode;
import com.tenicana.dronecraft.sim.MathUtil;

public final class DroneControlManager {
	private static final int INPUT_TIMEOUT_TICKS = 8;
	private static final int TICKS_PER_SECOND = 20;
	private static final int DEFAULT_DIAGNOSTIC_DURATION_TICKS = 16 * TICKS_PER_SECOND;
	private static final int MIN_DIAGNOSTIC_DURATION_TICKS = 6 * TICKS_PER_SECOND;
	private static final int MAX_DIAGNOSTIC_DURATION_TICKS = 60 * TICKS_PER_SECOND;
	private static final Map<UUID, TimedInput> INPUTS = new ConcurrentHashMap<>();
	private static final Map<UUID, DiagnosticScript> DIAGNOSTICS = new ConcurrentHashMap<>();
	private static final Map<UUID, CompletedDiagnostic> COMPLETED_DIAGNOSTICS = new ConcurrentHashMap<>();

	private DroneControlManager() {
	}

	public static int defaultDiagnosticDurationSeconds() {
		return DEFAULT_DIAGNOSTIC_DURATION_TICKS / TICKS_PER_SECOND;
	}

	public static void update(UUID playerId, DroneInput input, int tickCount) {
		DroneInput sanitized = sanitizeManualInput(playerId, input, tickCount);
		INPUTS.put(playerId, new TimedInput(sanitized, tickCount));
	}

	public static DroneInput get(UUID playerId, int tickCount) {
		return get(playerId, tickCount, null, null);
	}

	public static DroneInput get(UUID playerId, int tickCount, DroneState state, DroneConfig config) {
		DroneInput diagnosticInput = diagnosticInput(playerId, tickCount, state, config);
		if (diagnosticInput != null) {
			return diagnosticInput;
		}

		TimedInput input = INPUTS.get(playerId);
		if (input == null || tickCount - input.tickCount > INPUT_TIMEOUT_TICKS) {
			return DroneInput.idle();
		}
		return input.input;
	}

	public static ActiveInput latestActiveInput(int tickCount) {
		ActiveInput best = null;
		for (Map.Entry<UUID, TimedInput> entry : INPUTS.entrySet()) {
			TimedInput input = entry.getValue();
			if (input == null || input.input() == null) {
				continue;
			}
			int age = tickCount - input.tickCount();
			if (age < 0 || age > INPUT_TIMEOUT_TICKS) {
				continue;
			}
			if (!input.input.linkActive()) {
				continue;
			}
			ActiveInput candidate = new ActiveInput(entry.getKey(), input.input, age);
			if (best == null || age < best.ageTicks()) {
				best = candidate;
			}
		}
		return best;
	}

	public static int startDiagnostic(UUID playerId, int startTick, int durationTicks) {
		return startDiagnostic(playerId, startTick, durationTicks, false);
	}

	public static int startDiagnostic(UUID playerId, int startTick, int durationTicks, boolean autoSaveBlackbox) {
		int safeDuration = (int) MathUtil.clamp(durationTicks, MIN_DIAGNOSTIC_DURATION_TICKS, MAX_DIAGNOSTIC_DURATION_TICKS);
		DIAGNOSTICS.put(playerId, new DiagnosticScript(startTick, safeDuration, autoSaveBlackbox));
		COMPLETED_DIAGNOSTICS.remove(playerId);
		return safeDuration;
	}

	public static boolean stopDiagnostic(UUID playerId) {
		COMPLETED_DIAGNOSTICS.remove(playerId);
		return DIAGNOSTICS.remove(playerId) != null;
	}

	public static CompletedDiagnostic consumeCompletedDiagnostic(UUID playerId) {
		return COMPLETED_DIAGNOSTICS.remove(playerId);
	}

	public static DiagnosticStatus diagnosticStatus(UUID playerId, int tickCount) {
		DiagnosticScript script = DIAGNOSTICS.get(playerId);
		if (script == null) {
			return DiagnosticStatus.inactive();
		}
		if (script.isExpired(tickCount)) {
			completeDiagnostic(playerId, script, tickCount);
			return DiagnosticStatus.inactive();
		}
		return script.status(tickCount);
	}

	private static DroneInput diagnosticInput(UUID playerId, int tickCount, DroneState state, DroneConfig config) {
		DiagnosticScript script = DIAGNOSTICS.get(playerId);
		if (script == null) {
			return null;
		}
		if (script.isExpired(tickCount)) {
			completeDiagnostic(playerId, script, tickCount);
			return null;
		}
		return script.input(tickCount, state, config);
	}

	private static void completeDiagnostic(UUID playerId, DiagnosticScript script, int tickCount) {
		if (DIAGNOSTICS.remove(playerId, script)) {
			COMPLETED_DIAGNOSTICS.put(playerId, script.completed(tickCount));
		}
	}

	private static DroneInput sanitizeManualInput(UUID playerId, DroneInput input, int tickCount) {
		DroneInput sanitized = input == null ? DroneInput.idle() : input.normalized();
		if (!sanitized.armed()) {
			return sanitized;
		}
		TimedInput previous = INPUTS.get(playerId);
		boolean alreadyArmed = previous != null
				&& tickCount - previous.tickCount() <= INPUT_TIMEOUT_TICKS
				&& previous.input() != null
				&& previous.input().armed();
		if (alreadyArmed || DroneArmSafetyRules.canTransitionToArmed(
				sanitized.throttle(),
				sanitized.pitch(),
				sanitized.roll(),
				sanitized.yaw()
		)) {
			return sanitized;
		}
		return new DroneInput(
				sanitized.throttle(),
				sanitized.pitch(),
				sanitized.roll(),
				sanitized.yaw(),
				false,
				sanitized.linkActive(),
				sanitized.flightMode()
		);
	}

	private record TimedInput(DroneInput input, int tickCount) {
	}

	public record ActiveInput(UUID playerId, DroneInput input, int ageTicks) {
	}

	public record DiagnosticStatus(boolean active, int elapsedTicks, int durationTicks, String phase) {
		private static DiagnosticStatus inactive() {
			return new DiagnosticStatus(false, 0, 0, "idle");
		}

		public double elapsedSeconds() {
			return elapsedTicks / (double) TICKS_PER_SECOND;
		}

		public double durationSeconds() {
			return durationTicks / (double) TICKS_PER_SECOND;
		}
	}

	public record CompletedDiagnostic(int elapsedTicks, int durationTicks, boolean autoSaveBlackbox) {
	}

	private static final class DiagnosticScript {
		private final int startTick;
		private final int durationTicks;
		private final boolean autoSaveBlackbox;
		private double originAltitudeMeters = Double.NaN;

		private DiagnosticScript(int startTick, int durationTicks, boolean autoSaveBlackbox) {
			this.startTick = startTick;
			this.durationTicks = durationTicks;
			this.autoSaveBlackbox = autoSaveBlackbox;
		}

		private boolean isExpired(int tickCount) {
			return elapsedTicks(tickCount) >= durationTicks;
		}

		private DiagnosticStatus status(int tickCount) {
			int elapsedTicks = Math.min(elapsedTicks(tickCount), durationTicks);
			return new DiagnosticStatus(true, elapsedTicks, durationTicks, phaseName(progress(elapsedTicks)));
		}

		private CompletedDiagnostic completed(int tickCount) {
			return new CompletedDiagnostic(Math.min(elapsedTicks(tickCount), durationTicks), durationTicks, autoSaveBlackbox);
		}

		private DroneInput input(int tickCount, DroneState state, DroneConfig config) {
			int elapsedTicks = elapsedTicks(tickCount);
			double progress = progress(elapsedTicks);
			if (state != null && Double.isNaN(originAltitudeMeters)) {
				originAltitudeMeters = state.positionMeters().y();
			}

			boolean armed = progress < 0.985;
			double hoverThrottle = config == null ? 0.32 : MathUtil.clamp(config.hoverThrottle(), 0.08, 0.75);
			double throttle = commandedThrottle(progress, state, hoverThrottle);
			AxisCommand axes = axisCommand(progress);
			return new DroneInput(throttle, axes.pitch(), axes.roll(), axes.yaw(), armed, true, FlightMode.HORIZON).normalized();
		}

		private int elapsedTicks(int tickCount) {
			return Math.max(0, tickCount - startTick);
		}

		private double progress(int elapsedTicks) {
			return durationTicks <= 0 ? 1.0 : MathUtil.clamp(elapsedTicks / (double) durationTicks, 0.0, 1.0);
		}

		private double commandedThrottle(double progress, DroneState state, double hoverThrottle) {
			if (progress >= 0.975) {
				return 0.0;
			}
			if (progress < 0.04) {
				return Math.max(0.10, hoverThrottle * 0.80);
			}

			double altitudeError = 0.0;
			double verticalVelocity = 0.0;
			if (state != null && !Double.isNaN(originAltitudeMeters)) {
				altitudeError = targetAltitudeMeters(progress) - state.positionMeters().y();
				verticalVelocity = state.velocityMetersPerSecond().y();
			}

			double throttleBias = profileThrottleBias(progress);
			double throttle = hoverThrottle + altitudeError * 0.18 - verticalVelocity * 0.085 + throttleBias;
			return MathUtil.clamp(throttle, 0.05, 0.96);
		}

		private double targetAltitudeMeters(double progress) {
			double origin = Double.isNaN(originAltitudeMeters) ? 0.0 : originAltitudeMeters;
			double offset;
			if (progress < 0.10) {
				offset = 0.0;
			} else if (progress < 0.26) {
				offset = MathUtil.lerp(0.0, 1.8, smoothStep((progress - 0.10) / 0.16));
			} else if (progress < 0.76) {
				offset = 1.8 + 0.15 * Math.sin(progress * Math.PI * 10.0);
			} else if (progress < 0.88) {
				offset = MathUtil.lerp(1.8, 0.40, smoothStep((progress - 0.76) / 0.12));
			} else if (progress < 0.94) {
				offset = MathUtil.lerp(0.40, -0.04, smoothStep((progress - 0.88) / 0.06));
			} else {
				offset = -0.04;
			}
			return origin + offset;
		}

		private static double profileThrottleBias(double progress) {
			if (progress < 0.26) {
				return 0.16;
			}
			if (progress >= 0.66 && progress < 0.78) {
				return 0.15;
			}
			if (progress >= 0.78 && progress < 0.90) {
				return -0.06;
			}
			return 0.0;
		}

		private static AxisCommand axisCommand(double progress) {
			if (progress < 0.26 || progress >= 0.88) {
				return AxisCommand.ZERO;
			}
			if (progress < 0.40) {
				return new AxisCommand(0.0, alternatingStep(progress, 0.26, 0.40, 0.24), 0.0);
			}
			if (progress < 0.54) {
				return new AxisCommand(alternatingStep(progress, 0.40, 0.54, -0.22), 0.0, 0.0);
			}
			if (progress < 0.66) {
				return new AxisCommand(0.0, 0.0, alternatingStep(progress, 0.54, 0.66, 0.34));
			}
			if (progress < 0.78) {
				double local = (progress - 0.66) / 0.12;
				return new AxisCommand(-0.12, 0.18 * Math.sin(local * Math.PI * 2.0), 0.14);
			}
			if (progress < 0.88) {
				return new AxisCommand(0.04, 0.0, 0.0);
			}
			return AxisCommand.ZERO;
		}

		private static double alternatingStep(double progress, double start, double end, double amplitude) {
			double local = MathUtil.clamp((progress - start) / Math.max(1.0e-9, end - start), 0.0, 1.0);
			return local < 0.50 ? amplitude : -amplitude;
		}

		private static String phaseName(double progress) {
			if (progress < 0.04) {
				return "spool";
			}
			if (progress < 0.26) {
				return "takeoff";
			}
			if (progress < 0.40) {
				return "roll_step";
			}
			if (progress < 0.54) {
				return "pitch_step";
			}
			if (progress < 0.66) {
				return "yaw_step";
			}
			if (progress < 0.78) {
				return "throttle_punch";
			}
			if (progress < 0.88) {
				return "descent";
			}
			if (progress < 0.985) {
				return "settle";
			}
			return "disarm";
		}

		private static double smoothStep(double value) {
			double t = MathUtil.clamp(value, 0.0, 1.0);
			return t * t * (3.0 - 2.0 * t);
		}
	}

	private record AxisCommand(double pitch, double roll, double yaw) {
		private static final AxisCommand ZERO = new AxisCommand(0.0, 0.0, 0.0);
	}
}
