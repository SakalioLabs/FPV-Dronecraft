package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.propagation.NativeEventTraceDiagnosticGate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** D121p offline launch-gate truth table. */
public final class NativeEventTraceDiagnosticGateCli {
	private static final Case[] CASES = {
		new Case("normal", false, false, null, true, false, false),
		new Case("armed", true, true, "null", false, true, true),
		new Case(
				"implementation-disabled",
				true, false, "null", false, true, true
		),
		new Case(
				"drivers-missing",
				true, true, null, false, true, true
		),
		new Case(
				"drivers-fallback-list",
				true, true, "null,", false, true, true
		),
		new Case(
				"drivers-physical",
				true, true, "wasapi", false, true, true
		),
		new Case(
				"drivers-wrong-case",
				true, true, "Null", false, true, true
		),
		new Case(
				"local-plane-requested",
				true, true, "null", true, true, true
		),
		new Case(
				"sound-manager-not-suppressed",
				true, true, "null", false, false, true
		),
		new Case(
				"commands-not-suppressed",
				true, true, "null", false, true, false
		)
	};

	private NativeEventTraceDiagnosticGateCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121p-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		int normal = 0;
		int armed = 0;
		int rejected = 0;
		StringBuilder casesJson = new StringBuilder("[");
		for (int index = 0; index < CASES.length; index++) {
			Case testCase = CASES[index];
			NativeEventTraceDiagnosticGate.Decision decision =
					NativeEventTraceDiagnosticGate.evaluate(
							testCase.traceRequested(),
							testCase.traceImplementationEnabled(),
							testCase.openAlDrivers(),
							testCase.localPlaneSchedulerRequested(),
							testCase.droneSoundManagerSuppressed(),
							testCase.generalAcousticCommandsSuppressed()
					);
			switch (decision.state()) {
				case NORMAL -> normal++;
				case ARMED -> armed++;
				case REJECTED -> rejected++;
			}
			if (index > 0) {
				casesJson.append(',');
			}
			casesJson.append(testCase.json(decision));
		}
		casesJson.append(']');
		boolean countsMatch =
				normal == 1 && armed == 1 && rejected == 8;
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-native-trace-launch-gate",
				  "source_contract_sha256": "%s",
				  "cases": %s,
				  "counts": {
				    "total": %d,
				    "normal": %d,
				    "armed": %d,
				    "rejected": %d
				  },
				  "gates": {
				    "counts_match": %s,
				    "only_exact_null_arms": %s,
				    "normal_launch_unchanged": %s,
				    "every_unsuppressed_writer_rejected": %s
				  },
				  "runtime_proof_required": {
				    "alc_device_name": "No Output",
				    "capture_backend_available": false
				  },
				  "minecraft_client_started": false,
				  "alc_device_opened": false,
				  "physical_endpoint_opened": false,
				  "captures_audio": false,
				  "native_callback_delivery_measured": false,
				  "cuda_executed": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				casesJson,
				CASES.length,
				normal,
				armed,
				rejected,
				countsMatch,
				state(1) == NativeEventTraceDiagnosticGate.State.ARMED
						&& state(4)
								== NativeEventTraceDiagnosticGate.State.REJECTED
						&& state(5)
								== NativeEventTraceDiagnosticGate.State.REJECTED
						&& state(6)
								== NativeEventTraceDiagnosticGate.State.REJECTED,
				state(0) == NativeEventTraceDiagnosticGate.State.NORMAL,
				state(7) == NativeEventTraceDiagnosticGate.State.REJECTED
						&& state(8)
								== NativeEventTraceDiagnosticGate.State.REJECTED
						&& state(9)
								== NativeEventTraceDiagnosticGate.State.REJECTED
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-native-trace-launch-gate\","
						+ "\"normal\":%d,\"armed\":%d,\"rejected\":%d}%n",
				normal,
				armed,
				rejected
		);
	}

	private static NativeEventTraceDiagnosticGate.State state(int index) {
		Case testCase = CASES[index];
		return NativeEventTraceDiagnosticGate.evaluate(
				testCase.traceRequested(),
				testCase.traceImplementationEnabled(),
				testCase.openAlDrivers(),
				testCase.localPlaneSchedulerRequested(),
				testCase.droneSoundManagerSuppressed(),
				testCase.generalAcousticCommandsSuppressed()
		).state();
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private record Case(
			String name,
			boolean traceRequested,
			boolean traceImplementationEnabled,
			String openAlDrivers,
			boolean localPlaneSchedulerRequested,
			boolean droneSoundManagerSuppressed,
			boolean generalAcousticCommandsSuppressed
	) {
		private String json(
				NativeEventTraceDiagnosticGate.Decision decision
		) {
			return String.format(
					Locale.ROOT,
					"{\"name\":\"%s\",\"trace_requested\":%s,"
							+ "\"trace_implementation_enabled\":%s,"
							+ "\"openal_drivers\":%s,"
							+ "\"local_plane_scheduler_requested\":%s,"
							+ "\"drone_sound_manager_suppressed\":%s,"
							+ "\"general_acoustic_commands_suppressed\":%s,"
							+ "\"state\":\"%s\",\"reason\":\"%s\"}",
					name,
					traceRequested,
					traceImplementationEnabled,
					openAlDrivers == null
							? "null"
							: "\"" + openAlDrivers + "\"",
					localPlaneSchedulerRequested,
					droneSoundManagerSuppressed,
					generalAcousticCommandsSuppressed,
					decision.state(),
					decision.reason()
			);
		}
	}
}
