package com.tenicana.dronecraft.acoustics.propagation;

import org.junit.jupiter.api.Test;

import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Disposition.ACCEPTED_DIRTY;
import static com.tenicana.dronecraft.acoustics.propagation.BoundedAcousticWorldEventTrace.Kind.BLOCK_APPLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedAcousticWorldEventTraceTest {
	@Test
	void disabledTraceIsNoOp() {
		BoundedAcousticWorldEventTrace trace =
				new BoundedAcousticWorldEventTrace(2, false);
		trace.record(
				0L,
				-1L,
				0L,
				null,
				null,
				1,
				1,
				1,
				0,
				0,
				0
		);
		assertFalse(trace.enabled());
		assertEquals(0, trace.size());
		assertEquals(0L, trace.recorded());
	}

	@Test
	void overwritesOldestAndCopiesChronologically() {
		BoundedAcousticWorldEventTrace trace =
				new BoundedAcousticWorldEventTrace(3, true);
		for (int sequence = 1; sequence <= 5; sequence++) {
			trace.record(
					1L,
					sequence,
					7L,
					BLOCK_APPLY,
					ACCEPTED_DIRTY,
					sequence,
					2,
					3,
					sequence,
					2,
					3
			);
		}
		BoundedAcousticWorldEventTrace.Entry[] output = entries(3);
		assertEquals(3, trace.copyChronological(output));
		assertEquals(2L, trace.overwritten());
		assertEquals(3L, output[0].ordinal());
		assertEquals(3L, output[0].sequence());
		assertEquals(5L, output[2].ordinal());
		assertEquals(5L, output[2].sequence());
		assertEquals(7L, output[2].threadId());
	}

	@Test
	void rejectsUndersizedOrUninitializedExport() {
		BoundedAcousticWorldEventTrace trace =
				new BoundedAcousticWorldEventTrace(2, true);
		trace.record(
				1L, 1L, 1L, BLOCK_APPLY, ACCEPTED_DIRTY,
				0, 0, 0, 0, 0, 0
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> trace.copyChronological(new BoundedAcousticWorldEventTrace.Entry[0])
		);
		assertThrows(
				NullPointerException.class,
				() -> trace.copyChronological(
						new BoundedAcousticWorldEventTrace.Entry[1]
				)
		);
	}

	private static BoundedAcousticWorldEventTrace.Entry[] entries(
			int count
	) {
		BoundedAcousticWorldEventTrace.Entry[] output =
				new BoundedAcousticWorldEventTrace.Entry[count];
		for (int index = 0; index < count; index++) {
			output[index] = new BoundedAcousticWorldEventTrace.Entry();
		}
		return output;
	}
}
