package com.tenicana.dronecraft.diagnostic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SelfTestChunkReservationsTest {
	@Test
	void acroDestinationIsReservedBeforeCrossingTheNegativeChunkBoundary() {
		SelfTestChunkReservations reservations = new SelfTestChunkReservations();
		Fixture fixture = new Fixture();

		reservations.reserveAround(-31.9, -22.8, fixture::setForced);

		assertTrue(fixture.forced.contains(new Chunk(-3, -2)), "The failed native ACRO destination must be loaded before entry");
		reservations.reserveAround(-32.15936, -22.85936, fixture::setForced);
		assertTrue(fixture.forced.contains(new Chunk(-4, -2)), "Negative coordinates must floor into chunk -3, with its next neighbor reserved");
		assertFalse(fixture.forced.contains(new Chunk(-1, -2)));
		assertEquals(9, fixture.forced.size());
	}

	@Test
	void movingHaloLoadsItsFrontierBeforeReleasingTheTrailingEdge() {
		SelfTestChunkReservations reservations = new SelfTestChunkReservations();
		Fixture fixture = new Fixture();
		reservations.reserveAround(0.0, 0.0, fixture::setForced);
		fixture.operations.clear();

		reservations.reserveAround(16.1, 0.0, fixture::setForced);

		assertEquals(6, fixture.operations.size());
		assertTrue(fixture.operations.subList(0, 3).stream().allMatch(Operation::forced));
		assertTrue(fixture.operations.subList(3, 6).stream().noneMatch(Operation::forced));
		assertTrue(fixture.forced.contains(new Chunk(2, 0)));
		assertFalse(fixture.forced.contains(new Chunk(-1, 0)));
	}

	@Test
	void preexistingReservationsSurviveMovementAndFinalCleanup() {
		SelfTestChunkReservations reservations = new SelfTestChunkReservations();
		Fixture fixture = new Fixture();
		Set<Chunk> preexisting = Set.of(new Chunk(0, 0), new Chunk(7, 0));
		fixture.forced.addAll(preexisting);
		reservations.reserveAround(0.0, 0.0, fixture::setForced);
		reservations.reserveAround(112.0, 0.0, fixture::setForced);

		reservations.releaseAll(fixture::setForced);

		assertEquals(preexisting, fixture.forced);
		assertTrue(fixture.operations.stream().noneMatch(operation -> !operation.forced() && preexisting.contains(operation.chunk())));
	}

	@Test
	void repeatedRefreshAndCleanupDoNotDuplicateOrLeakReservations() {
		SelfTestChunkReservations reservations = new SelfTestChunkReservations();
		Fixture fixture = new Fixture();
		reservations.reserveAround(0.0, 0.0, fixture::setForced);
		fixture.operations.clear();
		reservations.reserveAround(1.0, 2.0, fixture::setForced);
		assertTrue(fixture.operations.isEmpty());

		reservations.releaseAll(fixture::setForced);
		assertTrue(fixture.forced.isEmpty());
		assertEquals(9, fixture.operations.size());
		fixture.operations.clear();
		reservations.releaseAll(fixture::setForced);
		assertTrue(fixture.operations.isEmpty());
	}

	@Test
	void longFlightRetainsOnlyTheCurrentBoundedHalo() {
		SelfTestChunkReservations reservations = new SelfTestChunkReservations();
		Fixture fixture = new Fixture();
		for (int chunk = 0; chunk < 100; chunk++) {
			reservations.reserveAround(chunk * 16.0, -chunk * 16.0, fixture::setForced);
			assertEquals(9, fixture.forced.size());
			assertTrue(fixture.forced.contains(new Chunk(chunk, -chunk)));
		}
		reservations.releaseAll(fixture::setForced);
		assertTrue(fixture.forced.isEmpty());
	}

	private static final class Fixture {
		private final Set<Chunk> forced = new HashSet<>();
		private final List<Operation> operations = new ArrayList<>();

		private boolean setForced(int x, int z, boolean force) {
			Chunk chunk = new Chunk(x, z);
			operations.add(new Operation(chunk, force));
			return force ? forced.add(chunk) : forced.remove(chunk);
		}
	}

	private record Chunk(int x, int z) { }
	private record Operation(Chunk chunk, boolean forced) { }
}
