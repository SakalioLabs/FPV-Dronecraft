package com.tenicana.dronecraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;

/** Native pivots are parent-relative here; source animation channels are local deltas. */
public final class CncMachineGeometry {
    private static final float[] TIMES = {0.0f, 0.8f, 1.5f, 3.0f, 4.5f, 5.2f, 6.0f};
    private static final float[] FEED = {0.0f, -0.05f, -0.4f, -0.28f, -0.4f, -0.05f, 0.0f};
    private static final float[] VERTICAL = {0.0f, 0.1f, 0.22f, 0.1f, -0.1f, 0.0f, 0.0f};
    private static final float[] DEPTH = {0.0f, 0.05f, 0.1f, -0.1f, 0.05f, 0.0f, 0.0f};
    private static final float[] TILT = {0.0f, 3.0f, 4.0f, -3.0f, -4.0f, 0.0f, 0.0f};
    private static final String HOLDER = "body/left_mechanism/left_stage_feed/left_stage_vertical/left_stage_depth/tilt_joint/rotary_holder";

    private final ModelPart root;
    private final ModelPart[] opaque;
    private final ModelPart[] glass;
    private final ModelPart[] stages;
    private final ModelPart[] lamps;
    private final ModelPart feed, vertical, depth, tilt, holder, spindle, progressFill, door, drawer;

    public CncMachineGeometry(ModelPart root) {
        this.root = root;
        opaque = parts(ImportedCncDesign.OPAQUE_CUBES);
        glass = parts(ImportedCncDesign.GLASS_CUBES);
        stages = parts(ImportedCncDesign.STAGES_CUBES);
        lamps = parts(ImportedCncDesign.LAMP_CUBES);
        feed = part("body/left_mechanism/left_stage_feed");
        vertical = feed.getChild("left_stage_vertical");
        depth = vertical.getChild("left_stage_depth");
        tilt = depth.getChild("tilt_joint");
        holder = tilt.getChild("rotary_holder");
        spindle = part("body/right_spindle_mount/spindle_tool");
        progressFill = part("body/control_console/progress_fill");
        door = part("body/observation_door");
        drawer = part("body/base/chip_drawer");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        ImportedCncDesign.addTo(mesh.getRoot());
        return LayerDefinition.create(mesh, ImportedCncDesign.TEXTURE_WIDTH, ImportedCncDesign.TEXTURE_HEIGHT);
    }

    public ModelPart root() { return root; }

    ModelPart part(String path) {
        ModelPart current = root;
        for (String name : path.split("/")) current = current.getChild(name);
        return current;
    }

    private ModelPart[] parts(String[] paths) {
        ModelPart[] result = new ModelPart[paths.length];
        for (int i = 0; i < paths.length; i++) result[i] = part(paths[i]);
        return result;
    }

    public void setup(CncVisualState state) {
        root.getAllParts().forEach(part -> { part.resetPose(); part.visible = true; });
        visibility(opaque, state.layer() == CncVisualState.Layer.OPAQUE);
        visibility(glass, state.layer() == CncVisualState.Layer.GLASS);
        visibility(stages, false);
        visibility(lamps, false);
        if (state.layer() == CncVisualState.Layer.LAMP) lamps[state.lampIndex()].visible = true;
        if (state.layer() == CncVisualState.Layer.OPAQUE && !state.recipeWorkpiece() && state.stage() >= 0) {
            // Retain all supplied stage meshes for model diagnostics; recipes draw their real native items.
            holder.getChild("workpiece").getChild(stageName(state.stage())).getAllParts()
                    .forEach(part -> part.visible = true);
        }
        float time = state.progress() * 6.0f;
        feed.x -= sample(time, FEED);
        vertical.y -= sample(time, VERTICAL);
        depth.z += sample(time, DEPTH);
        tilt.zRot += radians(sample(time, TILT));
        holder.xRot -= state.progress() * (float) (Math.PI * 2.0);
        spindle.xRot -= state.progress() * (float) (Math.PI * 24.0);
        progressFill.xScale = state.progress();
        if (state.progress() <= 0.0f) progressFill.visible = false;
        door.yRot -= radians(state.doorDegrees());
        drawer.z += state.drawerOffset();
    }

    /** Apply the exact animated holder chain, then restore the native item's upright coordinate basis. */
    public void workpiecePose(CncVisualState state, PoseStack poses) {
        setup(state);
        ModelPart current = root;
        for (String name : HOLDER.split("/")) {
            current = current.getChild(name);
            current.translateAndRotate(poses);
        }
        poses.scale(-1.0f, -1.0f, 1.0f);
        poses.rotateDegrees(Axis.ZP, 90.0f);
        poses.scale(0.32f, 0.32f, 0.32f);
    }

    private static void visibility(ModelPart[] parts, boolean visible) {
        for (ModelPart part : parts) part.visible = visible;
    }

    private static float sample(float time, float[] values) {
        for (int i = 1; i < TIMES.length; i++) {
            if (time <= TIMES[i]) {
                float fraction = (time - TIMES[i - 1]) / (TIMES[i] - TIMES[i - 1]);
                return values[i - 1] + fraction * (values[i] - values[i - 1]);
            }
        }
        return values[values.length - 1];
    }

    private static float radians(float degrees) { return degrees * (float) (Math.PI / 180.0); }
    private static String stageName(int stage) {
        return new String[]{"workpiece_0_blank", "workpiece_1_rough", "workpiece_2_semi",
                "workpiece_3_finishing", "workpiece_4_finished"}[stage];
    }
}
