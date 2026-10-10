package com.tenicana.dronecraft.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;

class DroneChunkTrackingViewTest {
	@Test
	void subscriptionIsExactlyTheNativeBodyAndDroneUnionIncludingBorderRules() {
		ChunkPos body = new ChunkPos(-8, -5);
		ChunkPos drone = new ChunkPos(-3, -2);
		int distance = 4;
		ChunkTrackingView bodyView = ChunkTrackingView.of(body, distance);
		ChunkTrackingView droneView = ChunkTrackingView.of(drone, distance);
		DroneChunkTrackingView union = view(body, drone, distance, true);

		for (int chunkX = -15; chunkX <= 6; chunkX++) {
			for (int chunkZ = -12; chunkZ <= 6; chunkZ++) {
				for (boolean border : new boolean[] {false, true}) {
					assertEquals(bodyView.contains(chunkX, chunkZ, border) || droneView.contains(chunkX, chunkZ, border),
							union.contains(chunkX, chunkZ, border), "native boundary at " + chunkX + "," + chunkZ);
				}
			}
		}
		Set<ChunkPos> expected = chunks(bodyView);
		expected.addAll(chunks(droneView));
		assertEquals(expected, chunks(union));
	}

	@Test
	void overlappingAndIdenticalRegionsEmitEachChunkOnce() {
		for (ChunkPos drone : new ChunkPos[] {new ChunkPos(0, 0), new ChunkPos(2, 1)}) {
			DroneChunkTrackingView union = view(new ChunkPos(0, 0), drone, 8, true);
			Set<ChunkPos> emitted = new HashSet<>();
			union.forEach(chunk -> assertTrue(emitted.add(chunk), "duplicate subscription " + chunk));
			assertTrue(emitted.contains(new ChunkPos(0, 0)));
			assertTrue(emitted.contains(drone));
		}
	}

	@Test
	void distantRegionsDoNotSubscribeToTheInterveningWorld() {
		DroneChunkTrackingView union = view(new ChunkPos(-100_000, 0), new ChunkPos(100_000, 0), 8, true);

		assertTrue(union.contains(-100_000, 0));
		assertTrue(union.contains(100_000, 0));
		assertFalse(union.contains(0, 0));
		assertEquals(2 * chunks(ChunkTrackingView.of(new ChunkPos(0, 0), 8)).size(), chunks(union).size());
	}

	@Test
	void movingDroneChangesOnlyItsExposedBoundaryAndRetainsThePilotsChunks() {
		ChunkPos body = new ChunkPos(0, 0);
		DroneChunkTrackingView previous = view(body, new ChunkPos(60, -40), 8, true);
		DroneChunkTrackingView next = view(body, new ChunkPos(61, -40), 8, true);
		Delta delta = difference(previous, next);

		assertExactDelta(previous, next, delta);
		assertFalse(delta.added().isEmpty());
		assertFalse(delta.removed().isEmpty());
		assertTrue(delta.added().size() < chunks(ChunkTrackingView.of(next.droneCenter(), 8)).size());
		for (ChunkPos pilotChunk : chunks(ChunkTrackingView.of(body, 8))) {
			assertFalse(delta.added().contains(pilotChunk), "retained pilot chunk resent");
			assertFalse(delta.removed().contains(pilotChunk), "retained pilot chunk dropped");
		}
	}

	@Test
	void switchingThePrimaryCameraCenterDoesNotUnloadOrResendEitherRegion() {
		ChunkPos body = new ChunkPos(0, 0);
		ChunkPos drone = new ChunkPos(70, 30);
		DroneChunkTrackingView lineOfSight = view(body, drone, 8, false);
		DroneChunkTrackingView remote = view(body, drone, 8, true);
		Delta delta = difference(lineOfSight, remote);

		assertEquals(body, DroneChunkTrackingView.primaryCenter(lineOfSight));
		assertEquals(drone, DroneChunkTrackingView.primaryCenter(remote));
		assertTrue(delta.added().isEmpty());
		assertTrue(delta.removed().isEmpty());
	}

	@Test
	void startingAndEndingRemoteControlPreservesTheExistingBodySubscription() {
		ChunkPos body = new ChunkPos(-4, 3);
		ChunkTrackingView vanilla = ChunkTrackingView.of(body, 6);
		DroneChunkTrackingView remote = view(body, new ChunkPos(80, -100), 6, true);
		Delta entering = difference(vanilla, remote);
		Delta leaving = difference(remote, vanilla);

		assertExactDelta(vanilla, remote, entering);
		assertTrue(entering.removed().isEmpty());
		assertEquals(chunks(ChunkTrackingView.of(remote.droneCenter(), 6)), entering.added());
		assertExactDelta(remote, vanilla, leaving);
		assertTrue(leaving.added().isEmpty());
		assertEquals(entering.added(), leaving.removed());
	}

	@Test
	void reducingViewDistanceDropsOnlyChunksOutsideTheNewUnion() {
		ChunkPos body = new ChunkPos(-2, 1);
		ChunkPos drone = new ChunkPos(5, 4);
		DroneChunkTrackingView previous = view(body, drone, 10, true);
		DroneChunkTrackingView next = view(body, drone, 4, true);
		Delta delta = difference(previous, next);

		assertExactDelta(previous, next, delta);
		assertTrue(delta.added().isEmpty());
		assertFalse(delta.removed().isEmpty());
		assertFalse(delta.removed().contains(body));
		assertFalse(delta.removed().contains(drone));
	}

	private static DroneChunkTrackingView view(ChunkPos body, ChunkPos drone, int distance, boolean remote) {
		return new DroneChunkTrackingView(body, drone, distance, remote ? drone : body);
	}

	private static Set<ChunkPos> chunks(ChunkTrackingView view) {
		Set<ChunkPos> chunks = new HashSet<>();
		view.forEach(chunks::add);
		return chunks;
	}

	private static Delta difference(ChunkTrackingView previous, ChunkTrackingView next) {
		Set<ChunkPos> added = new HashSet<>();
		Set<ChunkPos> removed = new HashSet<>();
		DroneChunkTrackingView.difference(previous, next,
				chunk -> assertTrue(added.add(chunk), "duplicate addition " + chunk),
				chunk -> assertTrue(removed.add(chunk), "duplicate removal " + chunk));
		return new Delta(added, removed);
	}

	private static void assertExactDelta(ChunkTrackingView previous, ChunkTrackingView next, Delta delta) {
		Set<ChunkPos> expectedAdded = chunks(next);
		expectedAdded.removeAll(chunks(previous));
		Set<ChunkPos> expectedRemoved = chunks(previous);
		expectedRemoved.removeAll(chunks(next));
		assertEquals(expectedAdded, delta.added());
		assertEquals(expectedRemoved, delta.removed());
	}

	private record Delta(Set<ChunkPos> added, Set<ChunkPos> removed) {
	}
}
