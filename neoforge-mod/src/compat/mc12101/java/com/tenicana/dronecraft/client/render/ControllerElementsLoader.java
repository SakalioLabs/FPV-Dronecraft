package com.tenicana.dronecraft.client.render;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.neoforged.neoforge.client.model.ElementsModel;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;

/** Retains the controller's original angles on versions with restrictive vanilla JSON parsing. */
public final class ControllerElementsLoader implements IGeometryLoader<ElementsModel> {

	@Override
	public ElementsModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
		List<BlockElement> elements = new ArrayList<>();
		for (JsonElement value : json.getAsJsonArray("controller_elements")) {
			JsonObject elementJson = value.getAsJsonObject().deepCopy();
			JsonObject rotationJson = elementJson.getAsJsonObject("rotation");
			float angle = 0.0F;
			if (rotationJson != null && rotationJson.has("angle")) {
				angle = rotationJson.get("angle").getAsFloat();
			} else if (rotationJson != null) {
				String selectedAxis = "y";
				int activeAxes = 0;
				for (String axis : List.of("x", "y", "z")) {
					float valueAngle = rotationJson.has(axis) ? rotationJson.get(axis).getAsFloat() : 0.0F;
					if (valueAngle != 0.0F) { selectedAxis = axis; angle = valueAngle; activeAxes++; }
					rotationJson.remove(axis);
				}
				if (activeAxes > 1) throw new JsonParseException("This legacy controller loader requires a single rotation axis.");
				rotationJson.addProperty("axis", selectedAxis);
			}
			if (!Float.isFinite(angle) || Math.abs(angle) > 360.0F) {
				throw new JsonParseException("Controller element rotation must be finite and within [-360,360].");
			}
			// Vanilla still validates positions, axis, origin, faces and UVs. Only its
			// five-angle deserialization restriction is bypassed; native baking is retained.
			if (rotationJson != null) rotationJson.addProperty("angle", 0.0F);
			BlockElement parsed = context.deserialize(elementJson, BlockElement.class);
			BlockElementRotation rotation = parsed.rotation == null ? null : new BlockElementRotation(
					parsed.rotation.origin(), parsed.rotation.axis(), angle, parsed.rotation.rescale());
			elements.add(new BlockElement(parsed.from, parsed.to, parsed.faces, rotation, parsed.shade, parsed.getFaceData()));
		}
		return new ElementsModel(elements);
	}
}
