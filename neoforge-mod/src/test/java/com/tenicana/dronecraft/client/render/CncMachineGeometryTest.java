package com.tenicana.dronecraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;

class CncMachineGeometryTest {
    @Test
    void nativeMeshRetainsAllTwentySixGroupsAndFiveStages() {
        CncMachineGeometry model = model();
        // Three extra cubes are the supplied idle/red/green UV variants of the same lamp.
        assertEquals(272, model.root().getAllParts().filter(part -> !part.isEmpty()).count());
        assertEquals(27, model.root().getAllParts().filter(ModelPart::isEmpty).count());
        int[] cubeCounts = {1, 4, 8, 16, 20};
        for (int stage = 0; stage < 5; stage++) {
            assertEquals(cubeCounts[stage], stage(model, stage).getAllParts().filter(part -> !part.isEmpty()).count());
        }
    }

    @Test
    void threeActualRenderPassesDrawEachMachineCubeExactlyOnce() {
        CncMachineGeometry model = model();
        CncVisualState visual = visual(0.5f, true, 2, true);
        assertEquals(215 * 24, draw(model, visual).vertices);
        assertEquals(4 * 24, draw(model, visual.withLayer(CncVisualState.Layer.GLASS)).vertices);
        assertEquals(24, draw(model, visual.withLayer(CncVisualState.Layer.LAMP)).vertices);
        assertTrue(model.root().getAllParts().allMatch(part -> Float.isFinite(part.x) && Float.isFinite(part.y)));
    }

    @Test
    void previewStageSelectionIsExclusiveAndRecipeNeverDrawsGenericMetal() {
        CncMachineGeometry model = model();
        int[] stageCubes = {1, 4, 8, 16, 20};
        for (int stage = 0; stage < 5; stage++) {
            assertEquals((215 + stageCubes[stage]) * 24, draw(model, visual(0.5f, false, stage, false)).vertices);
            for (int other = 0; other < 5; other++) {
                final boolean visible = stage == other;
                assertTrue(stage(model, other).getAllParts().filter(part -> !part.isEmpty())
                        .allMatch(part -> part.visible == visible));
            }
        }
        assertEquals(215 * 24, draw(model, visual(0.5f, true, 4, true)).vertices);
    }

    @Test
    void sourceCycleUsesItsLocalSlideAndSpindleChannelsAtServerProgress() {
        CncMachineGeometry model = model();
        ModelPart feed = model.part("body/left_mechanism/left_stage_feed");
        ModelPart vertical = feed.getChild("left_stage_vertical");
        ModelPart depth = vertical.getChild("left_stage_depth");
        ModelPart tilt = depth.getChild("tilt_joint");
        float initialX = feed.x, initialY = vertical.y, initialZ = depth.z;
        model.setup(visual(0.25f, true, 1, true));
        assertEquals(initialX + 0.4f, feed.x, 1.0e-6f);
        assertEquals(initialY - 0.22f, vertical.y, 1.0e-6f);
        assertEquals(initialZ + 0.1f, depth.z, 1.0e-6f);
        assertEquals(Math.toRadians(4.0), tilt.zRot, 1.0e-6);
        assertEquals(-Math.PI / 2.0, tilt.getChild("rotary_holder").xRot, 1.0e-6);
        assertEquals(-Math.PI * 6.0, model.part("body/right_spindle_mount/spindle_tool").xRot, 1.0e-5);
    }

    @Test
    void reusedMeshRestoresProgressDoorAndPreparedLampAfterAnotherMachine() {
        CncMachineGeometry model = model();
        model.setup(new CncVisualState(0.0f, false, -1, true, -100.0f, -0.4f, CncVisualState.Layer.OPAQUE));
        assertFalse(model.part("body/control_console/progress_fill").visible);
        model.setup(visual(0.75f, true, 3, true));
        ModelPart fill = model.part("body/control_console/progress_fill");
        assertTrue(fill.visible);
        assertEquals(0.75f, fill.xScale);
        assertEquals(0.0f, model.part("body/observation_door").yRot);
        assertEquals(model.part("body/base/chip_drawer").getInitialPose().z,
                model.part("body/base/chip_drawer").z);
        model.setup(visual(1.0f, false, 4, true).withLayer(CncVisualState.Layer.LAMP));
        assertTrue(model.part(ImportedCncDesign.LAMP_CUBES[3]).visible);
        model.setup(visual(0.2f, true, 1, true).withLayer(CncVisualState.Layer.LAMP));
        assertFalse(model.part(ImportedCncDesign.LAMP_CUBES[3]).visible);
        assertTrue(model.part(ImportedCncDesign.LAMP_CUBES[1]).visible);
    }

    @Test
    void actualLampVerticesPreserveNegativeInflateNativeUpAndSourceHandedness() {
        CncMachineGeometry model = model();
        RecordingVertices vertices = draw(model, visual(0.5f, true, 2, true).withLayer(CncVisualState.Layer.LAMP));
        assertEquals(1.36 / 16.0, vertices.minX, 1.0e-6);
        assertEquals(3.74 / 16.0, vertices.maxX, 1.0e-6);
        assertEquals(10.81 / 16.0, vertices.minY, 1.0e-6);
        assertEquals(10.99 / 16.0, vertices.maxY, 1.0e-6);
        assertEquals(0.97 / 16.0, vertices.minZ, 1.0e-6);
        assertEquals(1.03 / 16.0, vertices.maxZ, 1.0e-6);
    }

    private static CncMachineGeometry model() { return new CncMachineGeometry(CncMachineGeometry.createLayer().bakeRoot()); }
    private static CncVisualState visual(float progress, boolean processing, int stage, boolean recipe) {
        return new CncVisualState(progress, processing, stage, recipe, 0.0f, 0.0f, CncVisualState.Layer.OPAQUE);
    }
    private static ModelPart stage(CncMachineGeometry model, int stage) {
        return model.part("body/left_mechanism/left_stage_feed/left_stage_vertical/left_stage_depth/tilt_joint/rotary_holder/workpiece/"
                + new String[]{"workpiece_0_blank", "workpiece_1_rough", "workpiece_2_semi", "workpiece_3_finishing", "workpiece_4_finished"}[stage]);
    }
    private static RecordingVertices draw(CncMachineGeometry model, CncVisualState visual) {
        model.setup(visual);
        PoseStack poses = new PoseStack();
        CncRenderSupport.machinePose(poses, 0.0f);
        RecordingVertices vertices = new RecordingVertices();
        model.root().render(poses, vertices, 15728880, 0);
        return vertices;
    }

    private static final class RecordingVertices implements VertexConsumer {
        int vertices;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            vertices++;
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            return this;
        }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
