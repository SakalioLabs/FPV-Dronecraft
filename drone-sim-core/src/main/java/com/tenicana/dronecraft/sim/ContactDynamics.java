package com.tenicana.dronecraft.sim;

public final class ContactDynamics {
	public static final ContactSurface DEFAULT_SURFACE = new ContactSurface(1.0, 1.0, 1.0);

	private ContactDynamics() {
	}

	public record ContactSurface(
			double frictionMultiplier,
			double restitutionMultiplier,
			double scrapeMultiplier
	) {
		public ContactSurface {
			frictionMultiplier = finiteClamped(frictionMultiplier, 0.05, 3.0, 1.0);
			restitutionMultiplier = finiteClamped(restitutionMultiplier, 0.05, 3.0, 1.0);
			scrapeMultiplier = finiteClamped(scrapeMultiplier, 0.05, 3.0, 1.0);
		}
	}

	public record Response(
			Vec3 velocityMetersPerSecond,
			double impactSpeedMetersPerSecond,
			double slipSpeedMetersPerSecond,
			double bounceSpeedMetersPerSecond,
			Vec3 contactNormalWorld
	) {
	}

	public static Response resolve(
			Vec3 velocityMetersPerSecond,
			Vec3 attemptedDeltaMeters,
			Vec3 actualDeltaMeters,
			boolean horizontalCollision,
			boolean verticalCollision
	) {
		return resolve(
				velocityMetersPerSecond,
				attemptedDeltaMeters,
				actualDeltaMeters,
				horizontalCollision,
				verticalCollision,
				DEFAULT_SURFACE
		);
	}

	public static Response resolve(
			Vec3 velocityMetersPerSecond,
			Vec3 attemptedDeltaMeters,
			Vec3 actualDeltaMeters,
			boolean horizontalCollision,
			boolean verticalCollision,
			ContactSurface surface
	) {
		Vec3 velocity = velocityMetersPerSecond == null ? Vec3.ZERO : velocityMetersPerSecond;
		Vec3 attempted = attemptedDeltaMeters == null ? Vec3.ZERO : attemptedDeltaMeters;
		Vec3 actual = actualDeltaMeters == null ? Vec3.ZERO : actualDeltaMeters;
		ContactSurface contactSurface = surface == null ? DEFAULT_SURFACE : surface;

		boolean blockedX = blockedAxis(attempted.x(), actual.x(), horizontalCollision);
		boolean blockedY = verticalCollision;
		boolean blockedZ = blockedAxis(attempted.z(), actual.z(), horizontalCollision);
		if (horizontalCollision && !blockedX && !blockedZ) {
			if (Math.abs(velocity.x()) >= Math.abs(velocity.z())) {
				blockedX = true;
			} else {
				blockedZ = true;
			}
		}

		double normalX = blockedX ? velocity.x() : 0.0;
		double normalY = blockedY ? velocity.y() : 0.0;
		double normalZ = blockedZ ? velocity.z() : 0.0;
		double impactSpeed = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
		if (impactSpeed <= 1.0e-9) {
			return new Response(velocity, 0.0, 0.0, 0.0, Vec3.ZERO);
		}
		Vec3 contactNormal = contactNormal(blockedX, blockedY, blockedZ, velocity, attempted);

		double x = blockedX ? rebound(velocity.x(), impactSpeed, false, contactSurface) : velocity.x();
		double y = blockedY ? rebound(velocity.y(), impactSpeed, true, contactSurface) : velocity.y();
		double z = blockedZ ? rebound(velocity.z(), impactSpeed, false, contactSurface) : velocity.z();

		double verticalFriction = blockedY
				? (0.34 + 0.24 * smoothStep(1.0, 8.0, impactSpeed)) * contactSurface.frictionMultiplier()
				: 0.0;
		double wallFriction = (blockedX || blockedZ)
				? (0.12 + 0.16 * smoothStep(1.0, 10.0, impactSpeed)) * contactSurface.frictionMultiplier()
				: 0.0;
		if (!blockedX) {
			x *= MathUtil.clamp(1.0 - verticalFriction, 0.12, 1.0);
			if (blockedZ) {
				x *= MathUtil.clamp(1.0 - wallFriction, 0.28, 1.0);
			}
		}
		if (!blockedY) {
			y *= MathUtil.clamp(1.0 - wallFriction * 0.55, 0.45, 1.0);
		}
		if (!blockedZ) {
			z *= MathUtil.clamp(1.0 - verticalFriction, 0.12, 1.0);
			if (blockedX) {
				z *= MathUtil.clamp(1.0 - wallFriction, 0.28, 1.0);
			}
		}

		Vec3 resolved = new Vec3(x, y, z);
		Vec3 tangent = new Vec3(blockedX ? 0.0 : velocity.x(), blockedY ? 0.0 : velocity.y(), blockedZ ? 0.0 : velocity.z());
		Vec3 bounce = new Vec3(blockedX ? resolved.x() : 0.0, blockedY ? resolved.y() : 0.0, blockedZ ? resolved.z() : 0.0);
		return new Response(
				resolved,
				impactSpeed,
				tangent.length(),
				bounce.length(),
				contactNormal
		);
	}

	public static Vec3 angularVelocityImpulseBody(
			DroneConfig config,
			Quaternion orientation,
			Vec3 incomingVelocityWorldMetersPerSecond,
			Vec3 contactNormalWorld,
			double impactSpeedMetersPerSecond,
			double slipSpeedMetersPerSecond
	) {
		return angularVelocityImpulseBody(
				config,
				orientation,
				incomingVelocityWorldMetersPerSecond,
				contactNormalWorld,
				impactSpeedMetersPerSecond,
				slipSpeedMetersPerSecond,
				DEFAULT_SURFACE
		);
	}

	public static Vec3 angularVelocityImpulseBody(
			DroneConfig config,
			Quaternion orientation,
			Vec3 incomingVelocityWorldMetersPerSecond,
			Vec3 contactNormalWorld,
			double impactSpeedMetersPerSecond,
			double slipSpeedMetersPerSecond,
			ContactSurface surface
	) {
		if (config == null || impactSpeedMetersPerSecond <= 0.20) {
			return Vec3.ZERO;
		}
		ContactSurface contactSurface = surface == null ? DEFAULT_SURFACE : surface;

		Quaternion attitude = orientation == null ? Quaternion.IDENTITY : orientation.normalized();
		Vec3 normalWorld = contactNormalWorld == null ? Vec3.ZERO : contactNormalWorld.normalized();
		if (normalWorld.lengthSquared() <= 1.0e-9) {
			return Vec3.ZERO;
		}

		Vec3 velocityWorld = incomingVelocityWorldMetersPerSecond == null ? Vec3.ZERO : incomingVelocityWorldMetersPerSecond;
		Vec3 tangentVelocityWorld = velocityWorld.subtract(normalWorld.multiply(velocityWorld.dot(normalWorld)));
		Vec3 contactArmBody = contactArmBody(config, attitude, normalWorld, tangentVelocityWorld);
		if (contactArmBody.lengthSquared() <= 1.0e-9) {
			return Vec3.ZERO;
		}

		double normalImpulseScale = (0.18 + 0.14 * smoothStep(1.0, 12.0, impactSpeedMetersPerSecond))
				* Math.sqrt(contactSurface.restitutionMultiplier());
		Vec3 impulseWorld = normalWorld.multiply(config.massKg() * impactSpeedMetersPerSecond * normalImpulseScale);
		double tangentSpeed = tangentVelocityWorld.length();
		if (tangentSpeed > 1.0e-6 && slipSpeedMetersPerSecond > 0.0) {
			double frictionScale = (0.08 + 0.18 * smoothStep(1.0, 8.0, impactSpeedMetersPerSecond))
					* contactSurface.frictionMultiplier();
			double frictionImpulse = config.massKg()
					* Math.min(slipSpeedMetersPerSecond, impactSpeedMetersPerSecond * 1.35)
					* frictionScale;
			impulseWorld = impulseWorld.add(tangentVelocityWorld.normalized().multiply(-frictionImpulse));
		}

		Vec3 impulseBody = attitude.conjugate().rotate(impulseWorld);
		Vec3 angularMomentum = contactArmBody.cross(impulseBody).multiply(0.72);
		Vec3 inertia = config.inertiaKgMetersSquared();
		return angularMomentum.divide(inertia).clamp(-18.0, 18.0);
	}

	private static Vec3 contactNormal(
			boolean blockedX,
			boolean blockedY,
			boolean blockedZ,
			Vec3 velocity,
			Vec3 attempted
	) {
		return new Vec3(
				blockedX ? -signedAxis(velocity.x(), attempted.x()) : 0.0,
				blockedY ? -signedAxis(velocity.y(), attempted.y()) : 0.0,
				blockedZ ? -signedAxis(velocity.z(), attempted.z()) : 0.0
		).normalized();
	}

	private static double signedAxis(double velocity, double attempted) {
		double value = Math.abs(velocity) > 1.0e-7 ? velocity : attempted;
		return Math.signum(value);
	}

	private static Vec3 contactArmBody(
			DroneConfig config,
			Quaternion orientation,
			Vec3 contactNormalWorld,
			Vec3 tangentVelocityWorld
	) {
		Vec3 normalBody = orientation.conjugate().rotate(contactNormalWorld).normalized();
		Vec3 tangentBody = orientation.conjugate().rotate(tangentVelocityWorld).normalized();
		Vec3 bestArm = Vec3.ZERO;
		double bestScore = Double.POSITIVE_INFINITY;
		double minScore = Double.POSITIVE_INFINITY;
		double maxScore = Double.NEGATIVE_INFINITY;

		for (RotorSpec rotor : config.rotors()) {
			Vec3 arm = rotor.positionBodyMeters();
			double score = orientation.rotate(arm).dot(contactNormalWorld);
			minScore = Math.min(minScore, score);
			maxScore = Math.max(maxScore, score);
			double tieBreaker = tangentBody.lengthSquared() > 1.0e-9 ? arm.normalized().dot(tangentBody) * 1.0e-4 : 0.0;
			double adjustedScore = score + tieBreaker;
			if (adjustedScore < bestScore) {
				bestScore = adjustedScore;
				bestArm = arm;
			}
		}

		if (bestArm.lengthSquared() <= 1.0e-9) {
			return Vec3.ZERO;
		}
		if (maxScore - minScore < 1.0e-4
				&& tangentVelocityWorld.lengthSquared() < 0.25 * 0.25
				&& Math.abs(contactNormalWorld.y()) > 0.70) {
			return Vec3.ZERO;
		}
		return bestArm.add(normalBody.multiply(-0.045));
	}

	private static boolean blockedAxis(double attempted, double actual, boolean collision) {
		if (!collision || Math.abs(attempted) <= 1.0e-7) {
			return false;
		}
		return Math.abs(actual) < Math.abs(attempted) * 0.72 || Math.signum(attempted) != Math.signum(actual);
	}

	private static double rebound(double component, double impactSpeed, boolean vertical, ContactSurface surface) {
		double speed = Math.abs(component);
		if (speed <= 0.12) {
			return 0.0;
		}

		double base = vertical ? 0.050 : 0.070;
		double dynamic = vertical ? 0.145 : 0.175;
		double restitution = (base + dynamic * smoothStep(1.0, 12.0, impactSpeed)) * surface.restitutionMultiplier();
		double rebound = -component * restitution;
		return Math.abs(rebound) < 0.035 ? 0.0 : rebound;
	}

	private static double finiteClamped(double value, double min, double max, double fallback) {
		return Double.isFinite(value) ? MathUtil.clamp(value, min, max) : fallback;
	}

	private static double smoothStep(double edge0, double edge1, double value) {
		if (edge1 <= edge0) {
			return value >= edge1 ? 1.0 : 0.0;
		}
		double t = MathUtil.clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
