package com.tenicana.dronecraft.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.camera.FpvCameraOrientation;
import com.tenicana.dronecraft.sim.Quaternion;
import com.tenicana.dronecraft.sim.Vec3;

class PlayableAttitudeTest {
    @Test
    void correctedSnapshotCrossesVerticalPitchWithoutReplacingYawAndRollByHalfTurns() {
        for (double verticalDegrees : new double[]{-630, -450, -270, -90, 90, 270, 450, 630}) {
            for (double direction : new double[]{-1, 1}) {
                double previousPitch = Math.toRadians(verticalDegrees - direction * 4);
                double correctedPitch = Math.toRadians(verticalDegrees + direction * 4);
                double correctedRoll = Math.toRadians(23);
                Quaternion corrected = PlayableAttitude.quaternion(37, correctedPitch, correctedRoll);
                var angles = PlayableAttitude.project(corrected, 35, previousPitch, Math.toRadians(21));
                assertEquals(37, angles.yawDegrees(), 1e-6);
                assertEquals(correctedPitch, angles.pitchRadians(), 1e-7);
                assertEquals(correctedRoll, angles.rollRadians(), 1e-7);
            }
        }
    }

    @Test
    void snapshotBodyBasisAgreesWithMinecraftMovementAndFpvCameraThroughMultipleTurns() {
        for (float yaw : new float[]{-170, -90, 0, 90, 135, 370}) {
            for (int pitch = -720; pitch <= 720; pitch += 30) {
                for (int roll : new int[]{-720, -330, -180, 0, 75, 180, 360, 735}) {
                    Quaternion q = PlayableAttitude.quaternion(yaw, Math.toRadians(pitch), Math.toRadians(roll));
                    Vec3 forward = q.rotate(new Vec3(0, 0, 1));
                    Vec3 up = q.rotate(new Vec3(0, 1, 0));
                    var camera = FpvCameraOrientation.fromCameraAngles(yaw, pitch, (float) Math.toRadians(roll));
                    assertEquals(forward.x(), camera.forwards().x(), 2e-6);
                    assertEquals(forward.y(), camera.forwards().y(), 2e-6);
                    assertEquals(forward.z(), camera.forwards().z(), 2e-6);
                    assertEquals(up.x(), camera.up().x(), 2e-6);
                    assertEquals(up.y(), camera.up().y(), 2e-6);
                    assertEquals(up.z(), camera.up().z(), 2e-6);
                }
            }
            Vec3 forward = PlayableAttitude.quaternion(yaw, 0, 0).rotate(new Vec3(0, 0, 1));
            var velocity = PlayableFlightModel.worldVelocityForYaw(0, 0, 1, yaw);
            assertEquals(forward.x(), velocity.x(), 1e-6);
            assertEquals(forward.z(), velocity.z(), 1e-6);
        }
    }

    @Test
    void projectingSnapshotPreservesTurnBranchYawAndOrientationIncludingVerticalPitch() {
        for (double yaw : new double[]{-510, -90, 0, 135, 490}) {
            for (int pitch = -750; pitch <= 750; pitch += 15) {
                for (double roll : new double[]{-735, -180, 0, 46, 360, 745}) {
                    double p = Math.toRadians(pitch), r = Math.toRadians(roll);
                    Quaternion q = PlayableAttitude.quaternion(yaw, p, r);
                    var projected = PlayableAttitude.project(q, yaw, p, r);
                    assertEquals(yaw, projected.yawDegrees(), 1e-5);
                    assertEquals(p, projected.pitchRadians(), 1e-7);
                    assertEquals(r, projected.rollRadians(), 1e-7);
                    Quaternion restored = PlayableAttitude.quaternion(projected.yawDegrees(), projected.pitchRadians(), projected.rollRadians());
                    assertTrue(Math.abs(q.w()*restored.w()+q.x()*restored.x()+q.y()*restored.y()+q.z()*restored.z()) > 1-1e-10);
                }
            }
        }
    }
}
