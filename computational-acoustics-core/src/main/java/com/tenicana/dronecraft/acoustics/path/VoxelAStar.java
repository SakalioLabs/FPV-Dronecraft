package com.tenicana.dronecraft.acoustics.path;

import com.tenicana.dronecraft.acoustics.voxel.VoxelDda;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * Deterministic 6-neighbour A* over a bounded air-cell graph.
 */
public final class VoxelAStar {
	private static final int[][] NEIGHBOURS = {
			{1, 0, 0},
			{-1, 0, 0},
			{0, 0, 1},
			{0, 0, -1},
			{0, 1, 0},
			{0, -1, 0}
	};

	private VoxelAStar() {
	}

	public static SearchResult search(
			VoxelDda.Cell start,
			VoxelDda.Cell goal,
			SparseAirGrid grid,
			int maxVisitedNodes
	) {
		return search(start, goal, grid, maxVisitedNodes, cell -> true);
	}

	public static SearchResult search(
			VoxelDda.Cell start,
			VoxelDda.Cell goal,
			SparseAirGrid grid,
			int maxVisitedNodes,
			Predicate<VoxelDda.Cell> allowedCell
	) {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(goal, "goal");
		Objects.requireNonNull(grid, "grid");
		Objects.requireNonNull(allowedCell, "allowedCell");
		if (maxVisitedNodes < 1) {
			throw new IllegalArgumentException("maxVisitedNodes must be positive");
		}
		if (!grid.bounds().contains(start.x(), start.y(), start.z())
				|| !grid.bounds().contains(goal.x(), goal.y(), goal.z())
				|| !grid.isPassable(start.x(), start.y(), start.z())
				|| !grid.isPassable(goal.x(), goal.y(), goal.z())
				|| !allowedCell.test(start)
				|| !allowedCell.test(goal)) {
			return new SearchResult(List.of(), 0, false, false);
		}

		PriorityQueue<Node> open = new PriorityQueue<>(Comparator
				.comparingInt(Node::estimatedTotalCost)
				.thenComparingInt(Node::heuristic)
				.thenComparingLong(Node::sequence));
		Map<VoxelDda.Cell, Integer> bestCost = new HashMap<>();
		Map<VoxelDda.Cell, VoxelDda.Cell> previous = new HashMap<>();
		long sequence = 0L;
		int initialHeuristic = manhattan(start, goal);
		open.add(new Node(start, 0, initialHeuristic, sequence++));
		bestCost.put(start, 0);
		int visited = 0;

		while (!open.isEmpty() && visited < maxVisitedNodes) {
			Node current = open.poll();
			Integer knownCost = bestCost.get(current.cell());
			if (knownCost == null || current.cost() != knownCost) {
				continue;
			}
			visited++;
			if (current.cell().equals(goal)) {
				return new SearchResult(reconstruct(previous, goal), visited, true, false);
			}

			for (int[] offset : NEIGHBOURS) {
				VoxelDda.Cell next = new VoxelDda.Cell(
						current.cell().x() + offset[0],
						current.cell().y() + offset[1],
						current.cell().z() + offset[2]
				);
				if (!grid.isPassable(next.x(), next.y(), next.z())
						|| !allowedCell.test(next)) {
					continue;
				}
				int nextCost = current.cost() + 1;
				if (nextCost >= bestCost.getOrDefault(next, Integer.MAX_VALUE)) {
					continue;
				}
				bestCost.put(next, nextCost);
				previous.put(next, current.cell());
				int heuristic = manhattan(next, goal);
				open.add(new Node(next, nextCost, heuristic, sequence++));
			}
		}
		return new SearchResult(List.of(), visited, false, !open.isEmpty());
	}

	public static List<VoxelDda.Cell> simplify(List<VoxelDda.Cell> path) {
		path = List.copyOf(path);
		if (path.size() <= 2) {
			return path;
		}
		List<VoxelDda.Cell> simplified = new ArrayList<>();
		simplified.add(path.getFirst());
		int previousDx = path.get(1).x() - path.get(0).x();
		int previousDy = path.get(1).y() - path.get(0).y();
		int previousDz = path.get(1).z() - path.get(0).z();
		for (int index = 2; index < path.size(); index++) {
			VoxelDda.Cell before = path.get(index - 1);
			VoxelDda.Cell current = path.get(index);
			int dx = current.x() - before.x();
			int dy = current.y() - before.y();
			int dz = current.z() - before.z();
			if (dx != previousDx || dy != previousDy || dz != previousDz) {
				simplified.add(before);
				previousDx = dx;
				previousDy = dy;
				previousDz = dz;
			}
		}
		simplified.add(path.getLast());
		return List.copyOf(simplified);
	}

	private static List<VoxelDda.Cell> reconstruct(
			Map<VoxelDda.Cell, VoxelDda.Cell> previous,
			VoxelDda.Cell goal
	) {
		List<VoxelDda.Cell> path = new ArrayList<>();
		VoxelDda.Cell cell = goal;
		path.add(cell);
		while ((cell = previous.get(cell)) != null) {
			path.add(cell);
		}
		Collections.reverse(path);
		return List.copyOf(path);
	}

	private static int manhattan(VoxelDda.Cell first, VoxelDda.Cell second) {
		return Math.abs(first.x() - second.x())
				+ Math.abs(first.y() - second.y())
				+ Math.abs(first.z() - second.z());
	}

	private record Node(VoxelDda.Cell cell, int cost, int heuristic, long sequence) {
		private int estimatedTotalCost() {
			return cost + heuristic;
		}
	}

	public record SearchResult(
			List<VoxelDda.Cell> path,
			int visitedNodeCount,
			boolean reachedGoal,
			boolean budgetExhausted
	) {
		public SearchResult {
			path = List.copyOf(path);
		}
	}
}
