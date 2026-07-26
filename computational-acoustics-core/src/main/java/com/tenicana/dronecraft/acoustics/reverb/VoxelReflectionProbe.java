package com.tenicana.dronecraft.acoustics.reverb;

import com.tenicana.dronecraft.acoustics.AcousticBands;
import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticVector;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import com.tenicana.dronecraft.acoustics.reverb.ReflectionStatistics.LogRetention;

import java.util.Objects;

/**
 * Deterministic listener-centred voxel reflection probe. It estimates
 * environment statistics for a future shared late-reverb bus; it does not
 * claim source-to-listener early-tap visibility.
 */
public final class VoxelReflectionProbe {
	private static final double GOLDEN_ANGLE =
			Math.PI * (3.0 - Math.sqrt(5.0));
	private static final double SURFACE_EPSILON_METERS = 1.0e-6;
	private static final double MINIMUM_RETENTION = 1.0e-12;

	private VoxelReflectionProbe() {
	}

	public static ReflectionStatistics analyze(
			AcousticVector listener,
			ReflectionVolume volume,
			Config config
	) {
		return analyze(listener, volume, config, SurfaceHitObserver.NONE);
	}

	/**
	 * Diagnostic overload for allocation-free hit-distribution audits. Runtime
	 * callers should normally use the three-argument overload.
	 */
	public static ReflectionStatistics analyze(
			AcousticVector listener,
			ReflectionVolume volume,
			Config config,
			SurfaceHitObserver observer
	) {
		return analyze(
				listener,
				volume,
				config,
				observer,
				ReflectionDirectionPolicy.MATERIAL_SCATTERING
		);
	}

	/**
	 * Research overload that separates the material hit statistics from the
	 * reflection-direction policy. Runtime callers retain material scattering.
	 */
	public static ReflectionStatistics analyze(
			AcousticVector listener,
			ReflectionVolume volume,
			Config config,
			SurfaceHitObserver observer,
			ReflectionDirectionPolicy directionPolicy
	) {
		Objects.requireNonNull(listener, "listener");
		Objects.requireNonNull(volume, "volume");
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(observer, "observer");
		Objects.requireNonNull(directionPolicy, "directionPolicy");
		if (!volume.complete()) {
			throw new IllegalArgumentException(
					"reflection volume must be complete"
			);
		}
		if (!volume.containsPosition(listener.x(), listener.y(), listener.z())) {
			throw new IllegalArgumentException(
					"listener must be inside reflection volume"
			);
		}
		DirectPathSolver.MaterialSample listenerCell =
				volume.materials().sampleAt(
						floor(listener.x()),
						floor(listener.y()),
						floor(listener.z())
				);
		if (isSurface(listenerCell)) {
			throw new IllegalArgumentException(
					"listener must start in an air cell"
			);
		}

		Accumulator accumulator = new Accumulator(
				volume.generation(),
				config.rayCount()
		);
		for (int rayIndex = 0; rayIndex < config.rayCount(); rayIndex++) {
			traceRay(
					listener,
					fibonacciDirection(rayIndex, config.rayCount()),
					rayIndex,
					volume,
					config,
					accumulator,
					observer,
					directionPolicy
			);
		}
		return accumulator.toStatistics();
	}

	private static void traceRay(
			AcousticVector listener,
			AcousticVector initialDirection,
			int rayIndex,
			ReflectionVolume volume,
			Config config,
			Accumulator accumulator,
			SurfaceHitObserver observer,
			ReflectionDirectionPolicy directionPolicy
	) {
		AcousticVector position = listener;
		AcousticVector direction = initialDirection;
		AcousticBands energy = new AcousticBands(1.0, 1.0, 1.0);
		double cumulativePathMeters = 0.0;
		boolean terminal = false;

		for (int bounce = 0; bounce < config.maximumBounces(); bounce++) {
			LegResult leg = traceLeg(
					position,
					direction,
					volume,
					config.maximumLegDistanceMeters(),
					config.maximumCellsPerLeg()
			);
			if (leg.truncated()) {
				accumulator.truncatedLegs++;
				terminal = true;
				break;
			}
			if (leg.escaped()) {
				accumulator.escapedRays++;
				terminal = true;
				break;
			}

			SurfaceHit hit = leg.hit();
			cumulativePathMeters += hit.distanceMeters();
			AcousticMaterial material = hit.sample().material();
			observer.onHit(
					rayIndex,
					bounce,
					material,
					hit.normal(),
					hit.distanceMeters()
			);
			double fill = hit.sample().fillFraction();
			AcousticBands retention = material.surfaceAbsorption().map(
					absorption -> Math.max(
							MINIMUM_RETENTION,
							fill * (1.0 - absorption)
					)
			);
			energy = energy.multiply(retention);
			LogRetention logRetention = new LogRetention(
					Math.log(retention.low()),
					Math.log(retention.mid()),
					Math.log(retention.high())
			);
			accumulator.recordHit(
					bounce,
					hit.distanceMeters(),
					material.scattering(),
					logRetention,
					energy
			);
			if (energy.totalEnergy() <= config.energyFloor()) {
				accumulator.absorptionTerminatedRays++;
				terminal = true;
				break;
			}

			double directionScattering = directionPolicy.scattering(
					material,
					bounce,
					cumulativePathMeters
			);
			if (!Double.isFinite(directionScattering)
					|| directionScattering < 0.0
					|| directionScattering > 1.0) {
				throw new IllegalArgumentException(
						"direction scattering must be in [0, 1]"
				);
			}
			AcousticVector reflected = specularReflection(
					direction,
					hit.normal()
			);
			AcousticVector diffuse = diffuseHemisphereDirection(
					hit.normal(),
					rayIndex,
					bounce
			);
			direction = reflected
					.multiply(1.0 - directionScattering)
					.add(diffuse.multiply(directionScattering))
					.normalized();
			if (direction.length() <= 1.0e-12) {
				direction = reflected;
			}
			position = hit.position().add(
					direction.multiply(SURFACE_EPSILON_METERS)
			);
		}
		if (!terminal) {
			accumulator.maximumBounceTerminatedRays++;
		}
	}

	private static LegResult traceLeg(
			AcousticVector start,
			AcousticVector direction,
			ReflectionVolume volume,
			double maximumDistanceMeters,
			int maximumCells
	) {
		int x = floor(start.x());
		int y = floor(start.y());
		int z = floor(start.z());
		int stepX = sign(direction.x());
		int stepY = sign(direction.y());
		int stepZ = sign(direction.z());
		double tDeltaX = stepX == 0
				? Double.POSITIVE_INFINITY
				: Math.abs(1.0 / direction.x());
		double tDeltaY = stepY == 0
				? Double.POSITIVE_INFINITY
				: Math.abs(1.0 / direction.y());
		double tDeltaZ = stepZ == 0
				? Double.POSITIVE_INFINITY
				: Math.abs(1.0 / direction.z());
		double tMaxX = initialDistance(start.x(), direction.x(), x, stepX);
		double tMaxY = initialDistance(start.y(), direction.y(), y, stepY);
		double tMaxZ = initialDistance(start.z(), direction.z(), z, stepZ);

		for (int count = 0; count < maximumCells; count++) {
			double crossing = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
			if (crossing > maximumDistanceMeters) {
				return LegResult.escapedResult();
			}
			double epsilon = Math.max(
					1.0e-12,
					Math.abs(crossing) * 1.0e-12
			);
			int normalX = 0;
			int normalY = 0;
			int normalZ = 0;
			if (Math.abs(tMaxX - crossing) <= epsilon) {
				x += stepX;
				normalX = -stepX;
				tMaxX += tDeltaX;
			}
			if (Math.abs(tMaxY - crossing) <= epsilon) {
				y += stepY;
				normalY = -stepY;
				tMaxY += tDeltaY;
			}
			if (Math.abs(tMaxZ - crossing) <= epsilon) {
				z += stepZ;
				normalZ = -stepZ;
				tMaxZ += tDeltaZ;
			}
			if (!volume.containsCell(x, y, z)) {
				return LegResult.escapedResult();
			}
			DirectPathSolver.MaterialSample sample =
					volume.materials().sampleAt(x, y, z);
			if (isSurface(sample)) {
				AcousticVector position = start.add(
						direction.multiply(crossing)
				);
				AcousticVector normal = new AcousticVector(
						normalX,
						normalY,
						normalZ
				).normalized();
				return LegResult.hit(
						new SurfaceHit(
								position,
								normal,
								sample,
								crossing
						)
				);
			}
		}
		return LegResult.truncatedResult();
	}

	private static AcousticVector fibonacciDirection(int index, int count) {
		double z = 1.0 - 2.0 * (index + 0.5) / count;
		double radius = Math.sqrt(Math.max(0.0, 1.0 - z * z));
		double azimuth = index * GOLDEN_ANGLE;
		return new AcousticVector(
				radius * Math.cos(azimuth),
				z,
				radius * Math.sin(azimuth)
		);
	}

	private static AcousticVector specularReflection(
			AcousticVector direction,
			AcousticVector normal
	) {
		return direction.subtract(
				normal.multiply(2.0 * direction.dot(normal))
		).normalized();
	}

	private static AcousticVector diffuseHemisphereDirection(
			AcousticVector normal,
			int rayIndex,
			int bounce
	) {
		long key = ((long) rayIndex << 32)
				^ Integer.toUnsignedLong(bounce)
				^ 0x6A09E667F3BCC909L;
		double first = unitDouble(mix64(key));
		double second = unitDouble(mix64(key + 0x9E3779B97F4A7C15L));
		double radial = Math.sqrt(first);
		double azimuth = 2.0 * Math.PI * second;
		double localX = radial * Math.cos(azimuth);
		double localY = radial * Math.sin(azimuth);
		double localZ = Math.sqrt(Math.max(0.0, 1.0 - first));

		AcousticVector helper = Math.abs(normal.y()) < 0.9
				? new AcousticVector(0.0, 1.0, 0.0)
				: new AcousticVector(1.0, 0.0, 0.0);
		AcousticVector tangent = helper.cross(normal).normalized();
		AcousticVector bitangent = normal.cross(tangent).normalized();
		return tangent.multiply(localX)
				.add(bitangent.multiply(localY))
				.add(normal.multiply(localZ))
				.normalized();
	}

	private static boolean isSurface(
			DirectPathSolver.MaterialSample sample
	) {
		return sample.fillFraction() > 0.0
				&& !sample.material().equals(AcousticMaterial.AIR);
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static double unitDouble(long bits) {
		return (bits >>> 11) * 0x1.0p-53;
	}

	private static int floor(double value) {
		return (int) Math.floor(value);
	}

	private static int sign(double value) {
		return value > 0.0 ? 1 : value < 0.0 ? -1 : 0;
	}

	private static double initialDistance(
			double start,
			double direction,
			int cell,
			int step
	) {
		if (step == 0) {
			return Double.POSITIVE_INFINITY;
		}
		double boundary = step > 0 ? cell + 1.0 : cell;
		return (boundary - start) / direction;
	}

	public record Config(
			int rayCount,
			int maximumBounces,
			double maximumLegDistanceMeters,
			int maximumCellsPerLeg,
			double energyFloor
	) {
		public Config {
			if (rayCount < 1 || maximumBounces < 1
					|| maximumCellsPerLeg < 1) {
				throw new IllegalArgumentException(
						"ray, bounce and cell counts must be positive"
				);
			}
			if (!Double.isFinite(maximumLegDistanceMeters)
					|| maximumLegDistanceMeters <= 0.0) {
				throw new IllegalArgumentException(
						"maximumLegDistanceMeters must be positive"
				);
			}
			if (!Double.isFinite(energyFloor)
					|| energyFloor <= 0.0 || energyFloor >= 1.0) {
				throw new IllegalArgumentException(
						"energyFloor must be in (0, 1)"
				);
			}
		}

		/**
		 * Research baseline [H], not a measured optimum.
		 */
		public static Config researchBaseline() {
			return new Config(256, 12, 96.0, 256, 1.0e-6);
		}
	}

	@FunctionalInterface
	public interface SurfaceHitObserver {
		SurfaceHitObserver NONE =
				(rayIndex, bounce, material, normal, distanceMeters) -> {
				};

		void onHit(
				int rayIndex,
				int bounce,
				AcousticMaterial material,
				AcousticVector normal,
				double distanceMeters
		);
	}

	@FunctionalInterface
	public interface ReflectionDirectionPolicy {
		ReflectionDirectionPolicy MATERIAL_SCATTERING =
				(material, bounce, cumulativePathMeters) ->
						material.scattering();

		double scattering(
				AcousticMaterial material,
				int bounce,
				double cumulativePathMeters
		);
	}

	private record SurfaceHit(
			AcousticVector position,
			AcousticVector normal,
			DirectPathSolver.MaterialSample sample,
			double distanceMeters
	) {
	}

	private record LegResult(
			SurfaceHit hit,
			boolean escaped,
			boolean truncated
	) {
		private static LegResult hit(SurfaceHit hit) {
			return new LegResult(hit, false, false);
		}

		private static LegResult escapedResult() {
			return new LegResult(null, true, false);
		}

		private static LegResult truncatedResult() {
			return new LegResult(null, false, true);
		}
	}

	private static final class Accumulator {
		private final long snapshotGeneration;
		private final int rayCount;
		private int escapedRays;
		private int surfaceHits;
		private int earlySurfaceHits;
		private int truncatedLegs;
		private int absorptionTerminatedRays;
		private int maximumBounceTerminatedRays;
		private double hitLegDistanceMeters;
		private double earlyHitLegDistanceMeters;
		private double scatteringSum;
		private LogRetention logRetentionSum = LogRetention.ZERO;
		private LogRetention earlyLogRetentionSum = LogRetention.ZERO;
		private AcousticBands firstHitReflectedEnergySum =
				AcousticBands.SILENT;

		private Accumulator(long snapshotGeneration, int rayCount) {
			this.snapshotGeneration = snapshotGeneration;
			this.rayCount = rayCount;
		}

		private void recordHit(
				int bounce,
				double distanceMeters,
				double scattering,
				LogRetention logRetention,
				AcousticBands energy
			) {
			surfaceHits++;
			hitLegDistanceMeters += distanceMeters;
			scatteringSum += scattering;
			logRetentionSum = logRetentionSum.add(logRetention);
			if (bounce < 2) {
				earlySurfaceHits++;
				earlyHitLegDistanceMeters += distanceMeters;
				earlyLogRetentionSum =
						earlyLogRetentionSum.add(logRetention);
			}
			if (bounce == 0) {
				firstHitReflectedEnergySum =
						firstHitReflectedEnergySum.add(energy);
			}
		}

		private ReflectionStatistics toStatistics() {
			return new ReflectionStatistics(
					snapshotGeneration,
					rayCount,
					escapedRays,
					surfaceHits,
					earlySurfaceHits,
					truncatedLegs,
					absorptionTerminatedRays,
					maximumBounceTerminatedRays,
					hitLegDistanceMeters,
					earlyHitLegDistanceMeters,
					scatteringSum,
					logRetentionSum,
					earlyLogRetentionSum,
					firstHitReflectedEnergySum
			);
		}
	}
}
