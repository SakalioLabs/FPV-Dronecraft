package com.tenicana.dronecraft.acoustics.propagation;

/**
 * Fail-closed launch eligibility for a native callback trace session.
 *
 * <p>This gate does not open or inspect OpenAL. Eligibility only means the
 * pre-launch inputs are safe enough to start a later runtime verification,
 * which must still prove the opened device is the OpenAL Soft "No Output"
 * null backend.
 */
public final class NativeEventTraceDiagnosticGate {
	public enum State {
		NORMAL,
		ARMED,
		REJECTED
	}

	public enum Reason {
		TRACE_NOT_REQUESTED,
		TRACE_IMPLEMENTATION_DISABLED,
		OPENAL_NULL_BACKEND_NOT_EXCLUSIVE,
		LOCAL_PLANE_SCHEDULER_REQUESTED,
		DRONE_SOUND_MANAGER_NOT_SUPPRESSED,
		GENERAL_ACOUSTIC_COMMANDS_NOT_SUPPRESSED,
		SAFE_TO_START_RUNTIME_VERIFICATION
	}

	private NativeEventTraceDiagnosticGate() {
	}

	public static Decision evaluate(
			boolean traceRequested,
			boolean traceImplementationEnabled,
			String openAlDrivers,
			boolean localPlaneSchedulerRequested,
			boolean droneSoundManagerSuppressed,
			boolean generalAcousticCommandsSuppressed
	) {
		if (!traceRequested) {
			return new Decision(State.NORMAL, Reason.TRACE_NOT_REQUESTED);
		}
		if (!traceImplementationEnabled) {
			return new Decision(
					State.REJECTED,
					Reason.TRACE_IMPLEMENTATION_DISABLED
			);
		}
		if (!"null".equals(openAlDrivers)) {
			return new Decision(
					State.REJECTED,
					Reason.OPENAL_NULL_BACKEND_NOT_EXCLUSIVE
			);
		}
		if (localPlaneSchedulerRequested) {
			return new Decision(
					State.REJECTED,
					Reason.LOCAL_PLANE_SCHEDULER_REQUESTED
			);
		}
		if (!droneSoundManagerSuppressed) {
			return new Decision(
					State.REJECTED,
					Reason.DRONE_SOUND_MANAGER_NOT_SUPPRESSED
			);
		}
		if (!generalAcousticCommandsSuppressed) {
			return new Decision(
					State.REJECTED,
					Reason.GENERAL_ACOUSTIC_COMMANDS_NOT_SUPPRESSED
			);
		}
		return new Decision(
				State.ARMED,
				Reason.SAFE_TO_START_RUNTIME_VERIFICATION
		);
	}

	public record Decision(State state, Reason reason) {
		public boolean armed() {
			return state == State.ARMED;
		}
	}
}
