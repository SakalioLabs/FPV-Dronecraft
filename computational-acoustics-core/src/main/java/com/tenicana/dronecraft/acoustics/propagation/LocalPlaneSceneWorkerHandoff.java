package com.tenicana.dronecraft.acoustics.propagation;

import java.util.List;
import java.util.Objects;

/**
 * Fixed-capacity latest-generation handoff between a client-tick producer and
 * one scene-solver worker. It owns no Minecraft or audio objects.
 */
public final class LocalPlaneSceneWorkerHandoff implements AutoCloseable {
	public static final int MAXIMUM_SOURCES = 16;
	private final Object lock = new Object();
	private final int sourceCapacity;
	private final Thread worker;
	private final long[] submittedGeneration;
	private final long[] consumedGeneration;
	private final long[] publishedGeneration;
	private final long[] appliedGeneration;
	private final long[] submittedNanos;
	private final double[] sourceX;
	private final double[] sourceY;
	private final double[] sourceZ;
	private final double[] listenerX;
	private final double[] listenerY;
	private final double[] listenerZ;
	private final SceneSnapshot[] snapshots;
	private final Result[] published;
	private boolean started;
	private boolean closed;
	private int scanStart;
	private long pendingOverwrites;
	private long staleSolvedResults;
	private long publishedResults;

	public LocalPlaneSceneWorkerHandoff(int sourceCapacity) {
		if (sourceCapacity < 1 || sourceCapacity > MAXIMUM_SOURCES) {
			throw new IllegalArgumentException(
					"sourceCapacity must be in [1, 16]"
			);
		}
		this.sourceCapacity = sourceCapacity;
		submittedGeneration = new long[sourceCapacity];
		consumedGeneration = new long[sourceCapacity];
		publishedGeneration = new long[sourceCapacity];
		appliedGeneration = new long[sourceCapacity];
		submittedNanos = new long[sourceCapacity];
		sourceX = new double[sourceCapacity];
		sourceY = new double[sourceCapacity];
		sourceZ = new double[sourceCapacity];
		listenerX = new double[sourceCapacity];
		listenerY = new double[sourceCapacity];
		listenerZ = new double[sourceCapacity];
		snapshots = new SceneSnapshot[sourceCapacity];
		published = new Result[sourceCapacity];
		for (int source = 0; source < sourceCapacity; source++) {
			published[source] = new Result();
		}
		worker = new Thread(
				this::workerLoop,
				"fpv-local-plane-scene-worker"
		);
		worker.setDaemon(true);
	}

	public void start() {
		synchronized (lock) {
			if (closed) {
				throw new IllegalStateException("handoff is closed");
			}
			if (started) {
				return;
			}
			started = true;
			worker.start();
			lock.notifyAll();
		}
	}

	public boolean submit(
			int source,
			long generation,
			double nextSourceX,
			double nextSourceY,
			double nextSourceZ,
			double nextListenerX,
			double nextListenerY,
			double nextListenerZ,
			SceneSnapshot snapshot
	) {
		int checked = checkedSource(source);
		if (generation < 1) {
			throw new IllegalArgumentException("generation must be positive");
		}
		requirePosition(
				nextSourceX, nextSourceY, nextSourceZ, "source"
		);
		requirePosition(
				nextListenerX, nextListenerY, nextListenerZ, "listener"
		);
		Objects.requireNonNull(snapshot, "snapshot");
		synchronized (lock) {
			if (closed) {
				return false;
			}
			if (generation <= submittedGeneration[checked]) {
				return false;
			}
			if (submittedGeneration[checked]
					> consumedGeneration[checked]) {
				pendingOverwrites++;
			}
			sourceX[checked] = nextSourceX;
			sourceY[checked] = nextSourceY;
			sourceZ[checked] = nextSourceZ;
			listenerX[checked] = nextListenerX;
			listenerY[checked] = nextListenerY;
			listenerZ[checked] = nextListenerZ;
			snapshots[checked] = snapshot;
			submittedNanos[checked] = System.nanoTime();
			submittedGeneration[checked] = generation;
			lock.notifyAll();
			return true;
		}
	}

	public boolean pollLatest(
			int source,
			long minimumGeneration,
			Result output
	) {
		int checked = checkedSource(source);
		Objects.requireNonNull(output, "output");
		synchronized (lock) {
			long generation = publishedGeneration[checked];
			if (generation < minimumGeneration
					|| generation <= appliedGeneration[checked]) {
				return false;
			}
			output.copyFrom(published[checked]);
			appliedGeneration[checked] = generation;
			return true;
		}
	}

	public Statistics statistics(Statistics output) {
		Objects.requireNonNull(output, "output");
		synchronized (lock) {
			output.pendingOverwrites = pendingOverwrites;
			output.staleSolvedResults = staleSolvedResults;
			output.publishedResults = publishedResults;
			return output;
		}
	}

	private void workerLoop() {
		LocalPlaneSceneSolver.Workspace workspace =
				new LocalPlaneSceneSolver.Workspace();
		while (true) {
			int requestSource;
			long requestGeneration;
			long requestSubmittedNanos;
			double requestSourceX;
			double requestSourceY;
			double requestSourceZ;
			double requestListenerX;
			double requestListenerY;
			double requestListenerZ;
			SceneSnapshot requestSnapshot;
			synchronized (lock) {
				int source;
				while ((source = nextPendingSource()) < 0 && !closed) {
					try {
						lock.wait();
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						closed = true;
					}
				}
				if (closed) {
					return;
				}
				requestSource = source;
				requestGeneration = submittedGeneration[source];
				requestSubmittedNanos = submittedNanos[source];
				requestSourceX = sourceX[source];
				requestSourceY = sourceY[source];
				requestSourceZ = sourceZ[source];
				requestListenerX = listenerX[source];
				requestListenerY = listenerY[source];
				requestListenerZ = listenerZ[source];
				requestSnapshot = snapshots[source];
				consumedGeneration[source] = requestGeneration;
				scanStart = (source + 1) % sourceCapacity;
			}
			long solveStarted = System.nanoTime();
			if (requestSnapshot.complete()) {
				LocalPlaneSceneSolver.solve(
						requestSourceX,
						requestSourceY,
						requestSourceZ,
						requestListenerX,
						requestListenerY,
						requestListenerZ,
						requestSnapshot.patches(),
						requestSnapshot.coarseBlockers(),
						requestSnapshot.exactBlockers(),
						requestSnapshot.coverage(),
						requestSnapshot.maximumCellsPerLeg(),
						workspace
				);
			}
			long solveCompleted = System.nanoTime();
			synchronized (lock) {
				if (submittedGeneration[requestSource]
						!= requestGeneration) {
					staleSolvedResults++;
					continue;
				}
				Result result = published[requestSource];
				result.set(
						requestSource,
						requestGeneration,
						requestSubmittedNanos,
						solveStarted,
						solveCompleted,
						System.nanoTime(),
						requestSnapshot.complete(),
						workspace
				);
				publishedGeneration[requestSource] =
						requestGeneration;
				publishedResults++;
				lock.notifyAll();
			}
		}
	}

	private int nextPendingSource() {
		for (int offset = 0; offset < sourceCapacity; offset++) {
			int source = (scanStart + offset) % sourceCapacity;
			if (submittedGeneration[source]
					> consumedGeneration[source]) {
				return source;
			}
		}
		return -1;
	}

	private int checkedSource(int source) {
		if (source < 0 || source >= sourceCapacity) {
			throw new IndexOutOfBoundsException(source);
		}
		return source;
	}

	@Override
	public void close() {
		synchronized (lock) {
			if (closed) {
				return;
			}
			closed = true;
			lock.notifyAll();
		}
		if (started && Thread.currentThread() != worker) {
			try {
				worker.join(5_000L);
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
			if (worker.isAlive()) {
				worker.interrupt();
			}
		}
	}

	private static void requirePosition(
			double x, double y, double z, String label
	) {
		if (!Double.isFinite(x)
				|| !Double.isFinite(y)
				|| !Double.isFinite(z)) {
			throw new IllegalArgumentException(
					label + " coordinates must be finite"
			);
		}
	}

	public record SceneSnapshot(
			boolean complete,
			List<AxisAlignedPlanePatch> patches,
			LocalPlaneReflectionSolver.CellBlockQuery coarseBlockers,
			LocalPlaneReflectionSolver.SegmentBlockQuery exactBlockers,
			LocalPlaneReflectionSolver.CellCoverageQuery coverage,
			int maximumCellsPerLeg
	) {
		public SceneSnapshot(
				boolean complete,
				List<AxisAlignedPlanePatch> patches,
				LocalPlaneReflectionSolver.CellBlockQuery coarseBlockers,
				LocalPlaneReflectionSolver.SegmentBlockQuery exactBlockers,
				int maximumCellsPerLeg
		) {
			this(
					complete,
					patches,
					coarseBlockers,
					exactBlockers,
					LocalPlaneReflectionSolver.CellCoverageQuery.ALL,
					maximumCellsPerLeg
			);
		}

		public SceneSnapshot {
			patches = List.copyOf(
					Objects.requireNonNull(patches, "patches")
			);
			Objects.requireNonNull(coarseBlockers, "coarseBlockers");
			Objects.requireNonNull(exactBlockers, "exactBlockers");
			Objects.requireNonNull(coverage, "coverage");
			if (maximumCellsPerLeg < 1) {
				throw new IllegalArgumentException(
						"maximumCellsPerLeg must be positive"
				);
			}
		}
	}

	public static final class Result
			implements EarlyReflectionClusterSlew.Input {
		private int source;
		private long generation;
		private long submittedNanos;
		private long solveStartedNanos;
		private long solveCompletedNanos;
		private long publishedNanos;
		private boolean complete;
		private boolean conservativeFallback;
		private int selectedCount;
		private int clusterCount;
		private final int[] patchIndex =
				new int[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] pathLength =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] low =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] mid =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] high =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] clusterArrival =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] clusterLow =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] clusterMid =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] clusterHigh =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final int[] clusterPathCount =
				new int[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] clusterDirectionX =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] clusterDirectionY =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];
		private final double[] clusterDirectionZ =
				new double[LocalPlaneSceneSolver.MAXIMUM_CANDIDATES];

		private void set(
				int nextSource,
				long nextGeneration,
				long nextSubmittedNanos,
				long nextSolveStartedNanos,
				long nextSolveCompletedNanos,
				long nextPublishedNanos,
				boolean nextComplete,
				LocalPlaneSceneSolver.Workspace workspace
		) {
			source = nextSource;
			generation = nextGeneration;
			submittedNanos = nextSubmittedNanos;
			solveStartedNanos = nextSolveStartedNanos;
			solveCompletedNanos = nextSolveCompletedNanos;
			publishedNanos = nextPublishedNanos;
			complete = nextComplete;
			conservativeFallback = !nextComplete;
			selectedCount = nextComplete ? workspace.selectedCount() : 0;
			clusterCount = nextComplete ? workspace.clusterCount() : 0;
			for (int index = 0; index < selectedCount; index++) {
				patchIndex[index] = workspace.patchIndex(index);
				pathLength[index] = workspace.pathLengthMeters(index);
				low[index] = workspace.low(index);
				mid[index] = workspace.mid(index);
				high[index] = workspace.high(index);
			}
			for (int index = 0; index < clusterCount; index++) {
				clusterArrival[index] =
						workspace.clusterArrivalSamples(index);
				clusterLow[index] = workspace.clusterLow(index);
				clusterMid[index] = workspace.clusterMid(index);
				clusterHigh[index] = workspace.clusterHigh(index);
				clusterPathCount[index] =
						workspace.clusterPathCount(index);
				clusterDirectionX[index] =
						workspace.clusterDirectionX(index);
				clusterDirectionY[index] =
						workspace.clusterDirectionY(index);
				clusterDirectionZ[index] =
						workspace.clusterDirectionZ(index);
			}
		}

		private void copyFrom(Result sourceResult) {
			source = sourceResult.source;
			generation = sourceResult.generation;
			submittedNanos = sourceResult.submittedNanos;
			solveStartedNanos = sourceResult.solveStartedNanos;
			solveCompletedNanos = sourceResult.solveCompletedNanos;
			publishedNanos = sourceResult.publishedNanos;
			complete = sourceResult.complete;
			conservativeFallback = sourceResult.conservativeFallback;
			selectedCount = sourceResult.selectedCount;
			clusterCount = sourceResult.clusterCount;
			for (int index = 0; index < selectedCount; index++) {
				patchIndex[index] = sourceResult.patchIndex[index];
				pathLength[index] = sourceResult.pathLength[index];
				low[index] = sourceResult.low[index];
				mid[index] = sourceResult.mid[index];
				high[index] = sourceResult.high[index];
			}
			for (int index = 0; index < clusterCount; index++) {
				clusterArrival[index] =
						sourceResult.clusterArrival[index];
				clusterLow[index] = sourceResult.clusterLow[index];
				clusterMid[index] = sourceResult.clusterMid[index];
				clusterHigh[index] = sourceResult.clusterHigh[index];
				clusterPathCount[index] =
						sourceResult.clusterPathCount[index];
				clusterDirectionX[index] =
						sourceResult.clusterDirectionX[index];
				clusterDirectionY[index] =
						sourceResult.clusterDirectionY[index];
				clusterDirectionZ[index] =
						sourceResult.clusterDirectionZ[index];
			}
		}

		public int source() {
			return source;
		}

		public long generation() {
			return generation;
		}

		public long submittedNanos() {
			return submittedNanos;
		}

		public long solveStartedNanos() {
			return solveStartedNanos;
		}

		public long solveCompletedNanos() {
			return solveCompletedNanos;
		}

		public long publishedNanos() {
			return publishedNanos;
		}

		public boolean complete() {
			return complete;
		}

		public boolean conservativeFallback() {
			return conservativeFallback;
		}

		public int selectedCount() {
			return selectedCount;
		}

		public int clusterCount() {
			return clusterCount;
		}

		public int patchIndex(int index) {
			return patchIndex[checkedSelected(index)];
		}

		public double pathLengthMeters(int index) {
			return pathLength[checkedSelected(index)];
		}

		public double low(int index) {
			return low[checkedSelected(index)];
		}

		public double mid(int index) {
			return mid[checkedSelected(index)];
		}

		public double high(int index) {
			return high[checkedSelected(index)];
		}

		public double clusterArrivalSamples(int index) {
			return clusterArrival[checkedCluster(index)];
		}

		public double clusterLow(int index) {
			return clusterLow[checkedCluster(index)];
		}

		public double clusterMid(int index) {
			return clusterMid[checkedCluster(index)];
		}

		public double clusterHigh(int index) {
			return clusterHigh[checkedCluster(index)];
		}

		public int clusterPathCount(int index) {
			return clusterPathCount[checkedCluster(index)];
		}

		public double clusterDirectionX(int index) {
			return clusterDirectionX[checkedCluster(index)];
		}

		public double clusterDirectionY(int index) {
			return clusterDirectionY[checkedCluster(index)];
		}

		public double clusterDirectionZ(int index) {
			return clusterDirectionZ[checkedCluster(index)];
		}

		private int checkedSelected(int index) {
			if (index < 0 || index >= selectedCount) {
				throw new IndexOutOfBoundsException(index);
			}
			return index;
		}

		private int checkedCluster(int index) {
			if (index < 0 || index >= clusterCount) {
				throw new IndexOutOfBoundsException(index);
			}
			return index;
		}
	}

	public static final class Statistics {
		private long pendingOverwrites;
		private long staleSolvedResults;
		private long publishedResults;

		public long pendingOverwrites() {
			return pendingOverwrites;
		}

		public long staleSolvedResults() {
			return staleSolvedResults;
		}

		public long publishedResults() {
			return publishedResults;
		}
	}

}
