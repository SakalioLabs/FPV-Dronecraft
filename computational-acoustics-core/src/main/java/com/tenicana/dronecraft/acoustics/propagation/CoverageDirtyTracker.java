package com.tenicana.dronecraft.acoustics.propagation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Exact block-cell dirty revisions queried over bounded capture coverage.
 *
 * <p>Storage is grouped by 16-cubed sections, but tokens only include cells
 * inside the requested bounds. A change outside coverage therefore does not
 * invalidate the snapshot, even when it shares a section with covered cells.
 */
public final class CoverageDirtyTracker {
	private static final int SECTION_BITS = 4;
	private static final int SECTION_SIZE = 1 << SECTION_BITS;
	private static final int SECTION_MASK = SECTION_SIZE - 1;
	private static final int CELLS_PER_SECTION =
			SECTION_SIZE * SECTION_SIZE * SECTION_SIZE;

	private final Map<SectionKey, long[]> sections = new HashMap<>();
	private long revision;

	public synchronized long markDirty(int x, int y, int z) {
		if (revision == Long.MAX_VALUE) {
			throw new IllegalStateException(
					"coverage dirty revision exhausted"
			);
		}
		long next = ++revision;
		long[] cells = sections.computeIfAbsent(
				new SectionKey(
						Math.floorDiv(x, SECTION_SIZE),
						Math.floorDiv(y, SECTION_SIZE),
						Math.floorDiv(z, SECTION_SIZE)
				),
				ignored -> new long[CELLS_PER_SECTION]
		);
		cells[index(x, y, z)] = next;
		return next;
	}

	public synchronized long markDirty(CellCaptureBounds bounds) {
		Objects.requireNonNull(bounds, "bounds");
		long latest = revision;
		for (int x = bounds.minimumX(); x <= bounds.maximumX(); x++) {
			for (int z = bounds.minimumZ();
					z <= bounds.maximumZ(); z++) {
				for (int y = bounds.minimumY();
						y <= bounds.maximumY(); y++) {
					latest = markDirty(x, y, z);
				}
			}
		}
		return latest;
	}

	public synchronized long token(CellCaptureBounds bounds) {
		Objects.requireNonNull(bounds, "bounds");
		long token = 0L;
		int minimumSectionX =
				Math.floorDiv(bounds.minimumX(), SECTION_SIZE);
		int minimumSectionY =
				Math.floorDiv(bounds.minimumY(), SECTION_SIZE);
		int minimumSectionZ =
				Math.floorDiv(bounds.minimumZ(), SECTION_SIZE);
		int maximumSectionX =
				Math.floorDiv(bounds.maximumX(), SECTION_SIZE);
		int maximumSectionY =
				Math.floorDiv(bounds.maximumY(), SECTION_SIZE);
		int maximumSectionZ =
				Math.floorDiv(bounds.maximumZ(), SECTION_SIZE);
		for (int sectionX = minimumSectionX;
				sectionX <= maximumSectionX; sectionX++) {
			for (int sectionZ = minimumSectionZ;
					sectionZ <= maximumSectionZ; sectionZ++) {
				for (int sectionY = minimumSectionY;
						sectionY <= maximumSectionY; sectionY++) {
					long[] cells = sections.get(
							new SectionKey(
									sectionX,
									sectionY,
									sectionZ
							)
					);
					if (cells == null) {
						continue;
					}
					int minimumX = Math.max(
							bounds.minimumX(),
							sectionX << SECTION_BITS
					);
					int minimumY = Math.max(
							bounds.minimumY(),
							sectionY << SECTION_BITS
					);
					int minimumZ = Math.max(
							bounds.minimumZ(),
							sectionZ << SECTION_BITS
					);
					int maximumX = Math.min(
							bounds.maximumX(),
							((sectionX + 1) << SECTION_BITS) - 1
					);
					int maximumY = Math.min(
							bounds.maximumY(),
							((sectionY + 1) << SECTION_BITS) - 1
					);
					int maximumZ = Math.min(
							bounds.maximumZ(),
							((sectionZ + 1) << SECTION_BITS) - 1
					);
					for (int x = minimumX; x <= maximumX; x++) {
						for (int z = minimumZ; z <= maximumZ; z++) {
							for (int y = minimumY;
									y <= maximumY; y++) {
								token = Math.max(
										token,
										cells[index(x, y, z)]
								);
							}
						}
					}
				}
			}
		}
		return token;
	}

	public synchronized long currentRevision() {
		return revision;
	}

	private static int index(int x, int y, int z) {
		int localX = Math.floorMod(x, SECTION_SIZE);
		int localY = Math.floorMod(y, SECTION_SIZE);
		int localZ = Math.floorMod(z, SECTION_SIZE);
		return (localY << (SECTION_BITS * 2))
				| (localZ << SECTION_BITS)
				| localX;
	}

	private record SectionKey(int x, int y, int z) {
	}
}
