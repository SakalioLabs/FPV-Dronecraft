package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.propagation.CellCaptureBounds;
import com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace;
import com.tenicana.dronecraft.acoustics.propagation.CanonicalAcousticWorldEventPort;
import com.tenicana.dronecraft.acoustics.propagation.CoverageDirtyTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Client-thread bridge from native world lifecycle changes to exact acoustic
 * capture dirty tokens. It never reads world geometry and exposes no audio
 * objects.
 */
public final class MinecraftAcousticWorldDirtyTracker {
	private static final int MAXIMUM_ACTIVE_COVERAGES = 4;
	private static final int NATIVE_EVENT_TRACE_CAPACITY = 256;
	private static final boolean NATIVE_EVENT_TRACE_ENABLED =
			Boolean.getBoolean(
					"fpvdrone.acoustics.nativeEventTrace"
			);
	private static final CanonicalAcousticWorldEventPort PORT =
			new CanonicalAcousticWorldEventPort();
	private static final BoundedAcousticWorldEventTrace NATIVE_EVENT_TRACE =
			new BoundedAcousticWorldEventTrace(
					NATIVE_EVENT_TRACE_CAPACITY,
					NATIVE_EVENT_TRACE_ENABLED
			);
	private static ClientLevel activeWorld;
	private static long worldEpoch;
	private static long nextEventSequence;
	private static boolean initialized;

	private MinecraftAcousticWorldDirtyTracker() {
	}

	public static synchronized void initialize() {
		if (initialized) {
			return;
		}
		ClientChunkEvents.CHUNK_LOAD.register(
				MinecraftAcousticWorldDirtyTracker::onChunkLoad
		);
		ClientChunkEvents.CHUNK_UNLOAD.register(
				MinecraftAcousticWorldDirtyTracker::onChunkUnload
		);
		ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register(
				(client, world) -> switchWorld(world)
		);
		initialized = true;
	}

	public static synchronized CoverageDirtyTracker tracker(
			ClientLevel world
	) {
		ensureWorld(world);
		return PORT.tracker();
	}

	public static synchronized void replaceActiveCoverage(
			ClientLevel world,
			CellCaptureBounds[] coverage,
			int count
	) {
		ensureWorld(world);
		if (count > MAXIMUM_ACTIVE_COVERAGES) {
			throw new IllegalArgumentException(
					"active coverage count out of range"
			);
		}
		PORT.replaceActiveCoverage(coverage, count);
	}

	public static synchronized void onBlockChanged(
			ClientLevel world,
			BlockPos position
	) {
		if (world != activeWorld) {
			traceAdapterWorldMismatch(
					BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY,
					position.getX(),
					position.getY(),
					position.getZ(),
					position.getX(),
					position.getY(),
					position.getZ()
			);
			return;
		}
		long sequence = nextSequence();
		CanonicalAcousticWorldEventPort.Outcome outcome = PORT.acceptBlock(
				worldEpoch,
				sequence,
				CanonicalAcousticWorldEventPort.EventType.BLOCK_APPLY,
				position.getX(),
				position.getY(),
				position.getZ()
		);
		trace(
				sequence,
				BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY,
				disposition(outcome),
				position.getX(),
				position.getY(),
				position.getZ(),
				position.getX(),
				position.getY(),
				position.getZ()
		);
	}

	private static synchronized void onChunkLoad(
			ClientLevel world,
			LevelChunk chunk
	) {
		onChunkLoadStateChanged(
				world,
				chunk,
				CanonicalAcousticWorldEventPort.EventType.CHUNK_LOAD
		);
	}

	private static synchronized void onChunkUnload(
			ClientLevel world,
			LevelChunk chunk
	) {
		onChunkLoadStateChanged(
				world,
				chunk,
				CanonicalAcousticWorldEventPort.EventType.CHUNK_UNLOAD
		);
	}

	private static void onChunkLoadStateChanged(
			ClientLevel world,
			LevelChunk chunk,
			CanonicalAcousticWorldEventPort.EventType type
	) {
		if (world != activeWorld) {
			if (NATIVE_EVENT_TRACE.enabled() && worldEpoch > 0L) {
				ChunkPos stalePosition = chunk.getPos();
				traceAdapterWorldMismatch(
						kind(type),
						stalePosition.getMinBlockX(),
						0,
						stalePosition.getMinBlockZ(),
						stalePosition.getMaxBlockX(),
						0,
						stalePosition.getMaxBlockZ()
				);
			}
			return;
		}
		ChunkPos chunkPosition = chunk.getPos();
		long sequence = nextSequence();
		CanonicalAcousticWorldEventPort.Outcome outcome = PORT.acceptChunk(
				worldEpoch,
				sequence,
				type,
				chunkPosition.getMinBlockX(),
				chunkPosition.getMinBlockZ(),
				chunkPosition.getMaxBlockX(),
				chunkPosition.getMaxBlockZ()
		);
		trace(
				sequence,
				kind(type),
				disposition(outcome),
				chunkPosition.getMinBlockX(),
				0,
				chunkPosition.getMinBlockZ(),
				chunkPosition.getMaxBlockX(),
				0,
				chunkPosition.getMaxBlockZ()
		);
	}

	private static void ensureWorld(ClientLevel world) {
		if (world == null) {
			throw new NullPointerException("world");
		}
		if (world != activeWorld) {
			switchWorld(world);
		}
	}

	private static synchronized void switchWorld(ClientLevel world) {
		if (world == activeWorld && worldEpoch != 0L) {
			return;
		}
		if (worldEpoch == Long.MAX_VALUE) {
			throw new IllegalStateException(
					"acoustic world epoch exhausted"
			);
		}
		activeWorld = world;
		worldEpoch++;
		nextEventSequence = 0L;
		PORT.replaceWorld(worldEpoch);
		trace(
				0L,
				BoundedAcousticWorldEventTrace.Kind.WORLD_REPLACE,
				BoundedAcousticWorldEventTrace.Disposition
						.WORLD_REPLACED,
				0,
				0,
				0,
				0,
				0,
				0
		);
	}

	private static long nextSequence() {
		if (nextEventSequence == Long.MAX_VALUE) {
			throw new IllegalStateException(
					"acoustic world event sequence exhausted"
			);
		}
		return ++nextEventSequence;
	}

	public static boolean nativeEventTraceEnabled() {
		return NATIVE_EVENT_TRACE.enabled();
	}

	public static synchronized int copyNativeEventTrace(
			BoundedAcousticWorldEventTrace.Entry[] output
	) {
		return NATIVE_EVENT_TRACE.copyChronological(output);
	}

	public static long nativeEventTraceOverwritten() {
		return NATIVE_EVENT_TRACE.overwritten();
	}

	public static int nativeEventTraceSize() {
		return NATIVE_EVENT_TRACE.size();
	}

	public static int nativeEventTraceCapacity() {
		return NATIVE_EVENT_TRACE.capacity();
	}

	private static void trace(
			long sequence,
			BoundedAcousticWorldEventTrace.Kind kind,
			BoundedAcousticWorldEventTrace.Disposition disposition,
			int minimumX,
			int minimumY,
			int minimumZ,
			int maximumX,
			int maximumY,
			int maximumZ
	) {
		if (!NATIVE_EVENT_TRACE.enabled()) {
			return;
		}
		NATIVE_EVENT_TRACE.record(
				worldEpoch,
				sequence,
				Thread.currentThread().threadId(),
				kind,
				disposition,
				minimumX,
				minimumY,
				minimumZ,
				maximumX,
				maximumY,
				maximumZ
		);
	}

	private static void traceAdapterWorldMismatch(
			BoundedAcousticWorldEventTrace.Kind kind,
			int minimumX,
			int minimumY,
			int minimumZ,
			int maximumX,
			int maximumY,
			int maximumZ
	) {
		if (!NATIVE_EVENT_TRACE.enabled() || worldEpoch == 0L) {
			return;
		}
		trace(
				0L,
				kind,
				BoundedAcousticWorldEventTrace.Disposition
						.ADAPTER_WORLD_MISMATCH_REJECTED,
				minimumX,
				minimumY,
				minimumZ,
				maximumX,
				maximumY,
				maximumZ
		);
	}

	private static BoundedAcousticWorldEventTrace.Kind kind(
			CanonicalAcousticWorldEventPort.EventType type
	) {
		return switch (type) {
			case BLOCK_APPLY ->
					BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY;
			case BLOCK_ROLLBACK ->
					BoundedAcousticWorldEventTrace.Kind.BLOCK_ROLLBACK;
			case CHUNK_LOAD ->
					BoundedAcousticWorldEventTrace.Kind.CHUNK_LOAD;
			case CHUNK_REPLACE ->
					BoundedAcousticWorldEventTrace.Kind.CHUNK_REPLACE;
			case CHUNK_UNLOAD ->
					BoundedAcousticWorldEventTrace.Kind.CHUNK_UNLOAD;
		};
	}

	private static BoundedAcousticWorldEventTrace.Disposition disposition(
			CanonicalAcousticWorldEventPort.Outcome outcome
	) {
		return switch (outcome) {
			case ACCEPTED_DIRTY ->
					BoundedAcousticWorldEventTrace.Disposition
							.ACCEPTED_DIRTY;
			case ACCEPTED_OUTSIDE_COVERAGE ->
					BoundedAcousticWorldEventTrace.Disposition
							.ACCEPTED_OUTSIDE_COVERAGE;
			case DUPLICATE_IGNORED ->
					BoundedAcousticWorldEventTrace.Disposition
							.DUPLICATE_IGNORED;
			case CONFLICTING_SEQUENCE_REJECTED ->
					BoundedAcousticWorldEventTrace.Disposition
							.CONFLICTING_SEQUENCE_REJECTED;
			case OUT_OF_ORDER_REJECTED ->
					BoundedAcousticWorldEventTrace.Disposition
							.OUT_OF_ORDER_REJECTED;
			case WORLD_MISMATCH_REJECTED ->
					BoundedAcousticWorldEventTrace.Disposition
							.WORLD_MISMATCH_REJECTED;
		};
	}
}
