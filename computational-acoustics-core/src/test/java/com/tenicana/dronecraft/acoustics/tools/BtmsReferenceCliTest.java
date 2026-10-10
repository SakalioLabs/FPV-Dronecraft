package com.tenicana.dronecraft.acoustics.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BtmsReferenceCliTest {
	@Test
	void rejectsMissingOrMalformedArguments() {
		assertThrows(
				IllegalArgumentException.class,
				() -> BtmsReferenceCli.main(new String[0])
		);
		assertThrows(
				IllegalArgumentException.class,
				() -> BtmsReferenceCli.main(new String[] {
						"bad",
						"1",
						"1",
						"0",
						"0",
						"4",
						"6"
				})
		);
	}
}
