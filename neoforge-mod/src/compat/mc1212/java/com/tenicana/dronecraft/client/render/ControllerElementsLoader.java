package com.tenicana.dronecraft.client.render;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
/** Retains exact controller rotations while using the native face baker and model context. */
public final class ControllerElementsLoader implements IGeometryLoader<ControllerElementsLoader.Geometry> {
 @Override public Geometry read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
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
			elements.add(new BlockElement(parsed.from, parsed.to, parsed.faces, rotation, parsed.shade, parsed.lightEmission, parsed.getFaceData()));
		}
  return new Geometry(elements);
 }
 public static final class Geometry extends SimpleUnbakedGeometry<Geometry> {
  private final List<BlockElement> elements;
  Geometry(List<BlockElement> elements) { this.elements=List.copyOf(elements); }
  @Override protected void addQuads(IGeometryBakingContext context, IModelBuilder<?> builder, ModelBaker baker,
   Function<Material,TextureAtlasSprite> sprites, ModelState state) {
   var root=context.getRootTransform();
   if(!root.isIdentity()) state=UnbakedGeometryHelper.composeRootTransformIntoModelState(state,root);
   for(BlockElement element:elements) for(Direction side:element.faces.keySet()) {
    var face=element.faces.get(side);
    var quad=BlockModel.bakeFace(element,face,sprites.apply(context.getMaterial(face.texture())),side,state);
    if(face.cullForDirection()==null) builder.addUnculledFace(quad);
    else builder.addCulledFace(state.getRotation().rotateTransform(face.cullForDirection()),quad);
   }
  }
 }
}
