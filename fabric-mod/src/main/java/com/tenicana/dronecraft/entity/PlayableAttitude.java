package com.tenicana.dronecraft.entity;

import com.tenicana.dronecraft.sim.Quaternion;

/** The playable model uses Minecraft yaw and body Y-X-Z pitch/roll. */
final class PlayableAttitude {
    private PlayableAttitude() { }

    static Quaternion quaternion(double yawDegrees, double pitchRadians, double rollRadians) {
        return axis(0, 1, 0, -Math.toRadians(finite(yawDegrees)))
                .multiply(axis(1, 0, 0, finite(pitchRadians)))
                .multiply(axis(0, 0, 1, finite(rollRadians))).normalized();
    }

    /** Choose an equivalent Y-X-Z branch nearest the previously held angles. */
    static Angles project(Quaternion attitude, double yawReferenceDegrees, double pitchReference, double rollReference) {
        Quaternion q = attitude == null || !isFinite(attitude) ? Quaternion.IDENTITY : attitude.normalized();
        double yawReference = Math.toRadians(finite(yawReferenceDegrees));
        pitchReference = finite(pitchReference);
        rollReference = finite(rollReference);
        double sinPitch = Math.clamp(2 * (q.w() * q.x() - q.y() * q.z()), -1, 1);
        double pitch = Math.asin(sinPitch);
        double yaw, roll;
        if (Math.abs(Math.cos(pitch)) < 1e-7) {
            // At vertical pitch, heading alone cannot distinguish yaw from roll.
            // Keep the known yaw and read roll after removing that yaw rotation.
            yaw = yawReference;
            Quaternion local = axis(0, 1, 0, yaw).multiply(q);
            roll = Math.atan2(2 * (local.w() * local.z() - local.x() * local.y()),
                    1 - 2 * (local.y() * local.y() + local.z() * local.z()));
        } else {
            yaw = -Math.atan2(2 * (q.x() * q.z() + q.w() * q.y()),
                    1 - 2 * (q.x() * q.x() + q.y() * q.y()));
            roll = Math.atan2(2 * (q.x() * q.y() + q.w() * q.z()),
                    1 - 2 * (q.x() * q.x() + q.z() * q.z()));
        }
        Angles first = nearest(yaw, pitch, roll, yawReference, pitchReference, rollReference);
        Angles second = nearest(yaw + Math.PI, Math.PI - pitch, roll + Math.PI,
                yawReference, pitchReference, rollReference);
        return distance(first, yawReference, pitchReference, rollReference)
                <= distance(second, yawReference, pitchReference, rollReference) ? first : second;
    }

    private static Angles nearest(double yaw, double pitch, double roll, double yr, double pr, double rr) {
        return new Angles(Math.toDegrees(unwrap(yr, yaw)), unwrap(pr, pitch), unwrap(rr, roll));
    }

    private static double distance(Angles angles, double yr, double pr, double rr) {
        double y = Math.toRadians(angles.yawDegrees()) - yr;
        double p = angles.pitchRadians() - pr, r = angles.rollRadians() - rr;
        return y * y + p * p + r * r;
    }

    private static double unwrap(double reference, double angle) {
        return reference + Math.atan2(Math.sin(angle - reference), Math.cos(angle - reference));
    }

    private static Quaternion axis(double x, double y, double z, double angle) {
        double sine = Math.sin(angle / 2);
        return new Quaternion(Math.cos(angle / 2), x * sine, y * sine, z * sine);
    }

    private static boolean isFinite(Quaternion q) {
        return Double.isFinite(q.w()) && Double.isFinite(q.x()) && Double.isFinite(q.y()) && Double.isFinite(q.z());
    }

    private static double finite(double value) { return Double.isFinite(value) ? value : 0; }

    record Angles(double yawDegrees, double pitchRadians, double rollRadians) { }
}
