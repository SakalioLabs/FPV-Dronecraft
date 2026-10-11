package com.tenicana.dronecraft.client.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;

final class CncMachineModel extends Model<CncVisualState> {
    private final CncMachineGeometry geometry;

    CncMachineModel(ModelPart root) {
        super(root, RenderTypes::entityCutout);
        geometry = new CncMachineGeometry(root);
    }

    @Override
    public void setupAnim(CncVisualState state) { geometry.setup(state); }

    CncMachineGeometry geometry() { return geometry; }
}
