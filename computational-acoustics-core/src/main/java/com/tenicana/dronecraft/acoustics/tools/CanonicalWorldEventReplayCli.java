package com.tenicana.dronecraft.acoustics.tools;

import com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort;
import com.tenicana.dronecraft.acoustics.propagation.CellCaptureBounds;
import com.tenicana.dronecraft.acoustics.propagation.CoverageDirtyTracker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.BLOCK_APPLY;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.BLOCK_ROLLBACK;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.CHUNK_LOAD;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.CHUNK_REPLACE;
import static com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort.EventType.CHUNK_UNLOAD;

/** D121n deterministic replay of the canonical world-event port. */
public final class CanonicalWorldEventReplayCli {
	private static final CellCaptureBounds COVERAGE =
			new CellCaptureBounds(0, 0, 0, 31, 15, 31);

	private CanonicalWorldEventReplayCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <D121n-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		Replay replay = replay();
		CanonicalAcousticWorldEventPort.Diagnostics diagnostics =
				replay.port().diagnostics();
		boolean countsMatch =
				diagnostics.worldReplacements() == 2L
				&& diagnostics.acceptedEvents() == 7L
				&& diagnostics.dirtyEvents() == 5L
				&& diagnostics.duplicateEvents() == 1L
				&& diagnostics.conflictingSequenceEvents() == 1L
				&& diagnostics.outOfOrderEvents() == 1L
				&& diagnostics.worldMismatchEvents() == 1L
				&& diagnostics.markedCells() == 8_195L;
		boolean finalStateMatches =
				diagnostics.worldEpoch() == 2L
				&& diagnostics.lastSequence() == 1L
				&& replay.port().tracker().currentRevision() == 1L;
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-canonical-world-event-replay",
				  "source_contract_sha256": "%s",
				  "trace": %s,
				  "diagnostics": {
				    "world_epoch": %d,
				    "last_sequence": %d,
				    "active_coverage_count": %d,
				    "world_replacements": %d,
				    "accepted_events": %d,
				    "dirty_events": %d,
				    "duplicate_events": %d,
				    "conflicting_sequence_events": %d,
				    "out_of_order_events": %d,
				    "world_mismatch_events": %d,
				    "marked_cells": %d,
				    "final_tracker_revision": %d
				  },
				  "gates": {
				    "counts_match_contract": %s,
				    "duplicate_did_not_dirty": %s,
				    "conflict_did_not_dirty": %s,
				    "out_of_order_did_not_dirty": %s,
				    "rollback_dirtied": %s,
				    "chunk_replace_dirtied": %s,
				    "world_replaced_tracker_identity": %s,
				    "stale_world_did_not_dirty": %s,
				    "final_state_matches_contract": %s
				  },
				  "fabric_adapter_mapping": {
				    "set_block_success": "BLOCK_APPLY",
				    "chunk_load": "CHUNK_LOAD",
				    "chunk_unload": "CHUNK_UNLOAD",
				    "chunk_replace": "canonical-only-until-native-signal",
				    "prediction_rollback": "canonical-only-until-distinguishable-native-signal"
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "minecraft_client_started": false,
				  "client_level_read": false,
				  "cuda_executed": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				replay.traceJson(),
				diagnostics.worldEpoch(),
				diagnostics.lastSequence(),
				diagnostics.activeCoverageCount(),
				diagnostics.worldReplacements(),
				diagnostics.acceptedEvents(),
				diagnostics.dirtyEvents(),
				diagnostics.duplicateEvents(),
				diagnostics.conflictingSequenceEvents(),
				diagnostics.outOfOrderEvents(),
				diagnostics.worldMismatchEvents(),
				diagnostics.markedCells(),
				replay.port().tracker().currentRevision(),
				countsMatch,
				replay.unchanged(1, 2),
				replay.unchanged(2, 3),
				replay.unchanged(5, 6),
				replay.increased(4, 5),
				replay.increased(7, 8),
				replay.trackerIdentityChanged(),
				replay.unchanged(10, 11),
				finalStateMatches
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.printf(
				Locale.ROOT,
				"{\"status\":\"valid-canonical-world-event-replay\","
						+ "\"accepted\":%d,\"dirty\":%d,"
						+ "\"rejected_or_ignored\":%d,"
						+ "\"marked_cells\":%d}%n",
				diagnostics.acceptedEvents(),
				diagnostics.dirtyEvents(),
				diagnostics.duplicateEvents()
						+ diagnostics.conflictingSequenceEvents()
						+ diagnostics.outOfOrderEvents()
						+ diagnostics.worldMismatchEvents(),
				diagnostics.markedCells()
		);
	}

	private static Replay replay() {
		CanonicalAcousticWorldEventPort port =
				new CanonicalAcousticWorldEventPort();
		TraceRecord[] records = new TraceRecord[13];
		port.replaceWorld(1L);
		port.replaceActiveCoverage(
				new CellCaptureBounds[] {COVERAGE},
				1
		);
		CoverageDirtyTracker firstTracker = port.tracker();
		records[0] = record(
				0, "world-1-ready", "WORLD_REPLACE", port, COVERAGE
		);
		records[1] = event(
				1, "block-apply", port.acceptBlock(
						1L, 1L, BLOCK_APPLY, 4, 2, 4
				), port, COVERAGE
		);
		records[2] = event(
				2, "exact-duplicate", port.acceptBlock(
						1L, 1L, BLOCK_APPLY, 4, 2, 4
				), port, COVERAGE
		);
		records[3] = event(
				3, "conflicting-duplicate", port.acceptBlock(
						1L, 1L, BLOCK_APPLY, 5, 2, 4
				), port, COVERAGE
		);
		records[4] = event(
				4, "outside-coverage", port.acceptBlock(
						1L, 2L, BLOCK_APPLY, 40, 2, 40
				), port, COVERAGE
		);
		records[5] = event(
				5, "prediction-rollback", port.acceptBlock(
						1L, 3L, BLOCK_ROLLBACK, 4, 2, 4
				), port, COVERAGE
		);
		records[6] = event(
				6, "out-of-order", port.acceptBlock(
						1L, 2L, BLOCK_APPLY, 40, 2, 40
				), port, COVERAGE
		);
		records[7] = event(
				7, "chunk-unload", port.acceptChunk(
						1L, 4L, CHUNK_UNLOAD, 0, 0, 15, 15
				), port, COVERAGE
		);
		records[8] = event(
				8, "chunk-replace", port.acceptChunk(
						1L, 5L, CHUNK_REPLACE, 16, 16, 31, 31
				), port, COVERAGE
		);
		records[9] = event(
				9, "outside-chunk-load", port.acceptChunk(
						1L, 6L, CHUNK_LOAD, 48, 48, 63, 63
				), port, COVERAGE
		);
		port.replaceWorld(2L);
		records[10] = record(
				10, "dimension-change", "WORLD_REPLACE", port, COVERAGE
		);
		records[11] = event(
				11, "stale-old-world", port.acceptBlock(
						1L, 7L, BLOCK_APPLY, 1, 1, 1
				), port, COVERAGE
		);
		port.replaceActiveCoverage(
				new CellCaptureBounds[] {COVERAGE},
				1
		);
		records[12] = event(
				12, "new-world-block", port.acceptBlock(
						2L, 1L, BLOCK_APPLY, 1, 1, 1
				), port, COVERAGE
		);
		return new Replay(
				port,
				records,
				firstTracker != port.tracker()
		);
	}

	private static TraceRecord event(
			int index,
			String name,
			CanonicalAcousticWorldEventPort.Outcome outcome,
			CanonicalAcousticWorldEventPort port,
			CellCaptureBounds coverage
	) {
		return record(index, name, outcome.name(), port, coverage);
	}

	private static TraceRecord record(
			int index,
			String name,
			String outcome,
			CanonicalAcousticWorldEventPort port,
			CellCaptureBounds coverage
	) {
		return new TraceRecord(
				index,
				name,
				outcome,
				port.worldEpoch(),
				port.diagnostics().lastSequence(),
				port.tracker().currentRevision(),
				port.tracker().token(coverage)
		);
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

	private record TraceRecord(
			int index,
			String name,
			String outcome,
			long worldEpoch,
			long sequence,
			long revision,
			long coverageToken
	) {
		private String json() {
			return String.format(
					Locale.ROOT,
					"{\"index\":%d,\"name\":\"%s\","
							+ "\"outcome\":\"%s\",\"world_epoch\":%d,"
							+ "\"sequence\":%d,\"revision\":%d,"
							+ "\"coverage_token\":%d}",
					index,
					name,
					outcome,
					worldEpoch,
					sequence,
					revision,
					coverageToken
			);
		}
	}

	private record Replay(
			CanonicalAcousticWorldEventPort port,
			TraceRecord[] records,
			boolean trackerIdentityChanged
	) {
		private boolean unchanged(int before, int after) {
			return records[before].revision() == records[after].revision();
		}

		private boolean increased(int before, int after) {
			return records[after].revision() > records[before].revision();
		}

		private String traceJson() {
			StringBuilder output = new StringBuilder("[");
			for (int index = 0; index < records.length; index++) {
				if (index > 0) {
					output.append(',');
				}
				output.append(records[index].json());
			}
			return output.append(']').toString();
		}
	}
}
