package com.tenicana.dronecraft.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class MekanismRecipeResourcesTest {
	private static final List<String> OPTIONAL_RECIPES = List.of("propeller", "brushless_motor", "esc", "flight_controller",
			"lipo_battery", "carbon_frame", "drone_airframe", "basic_power_module", "advanced_power_module",
			"elite_power_module", "ultimate_power_module", "basic_radio_module", "advanced_radio_module",
			"elite_radio_module", "ultimate_radio_module", "quantum_link_module", "drone_assembly");

	@Test
	void allOptionalRecipesAreSkippedBeforeResolvingMissingMekanismItems() throws IOException {
		for (String recipeName : OPTIONAL_RECIPES) {
			JsonObject recipe = read(recipeName);
			assertTrue(recipe.has("neoforge:conditions"), recipeName);
			JsonObject condition = recipe.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject();
			assertEquals("neoforge:mod_loaded", condition.get("type").getAsString(), recipeName);
			assertEquals("mekanism", condition.get("modid").getAsString(), recipeName);
		}
	}

	@Test
	void standaloneDroneAndControllerHaveVanillaRecipesAndNoLoadout() throws IOException {
		for (String recipeName : List.of("drone", "drone_controller")) {
			JsonObject recipe = read(recipeName);
			assertFalse(recipe.has("neoforge:conditions"));
			assertEquals("fpvdrone:" + recipeName, recipe.getAsJsonObject("result").get("id").getAsString());
			assertFalse(recipe.getAsJsonObject("result").has("components"));
			for (JsonElement ingredient : recipe.getAsJsonObject("key").asMap().values()) {
				assertTrue(ingredient.getAsJsonObject().get("item").getAsString().startsWith("minecraft:"));
			}
		}
	}

	@Test
	void ingredientsUseTargetVersionObjectSyntaxAndAssemblyCarriesCustomLoadout() throws IOException {
		for (String recipeName : OPTIONAL_RECIPES) {
			JsonObject recipe = read(recipeName);
			if (recipe.has("ingredients")) {
				for (JsonElement ingredient : recipe.getAsJsonArray("ingredients")) {
					assertTrue(ingredient.isJsonObject(), recipeName);
					assertTrue(ingredient.getAsJsonObject().has("item"), recipeName);
				}
			}
			if (recipe.has("key")) {
				for (JsonElement ingredient : recipe.getAsJsonObject("key").asMap().values()) {
					assertTrue(ingredient.isJsonObject(), recipeName);
					assertTrue(ingredient.getAsJsonObject().has("item"), recipeName);
				}
			}
		}
		assertEquals("fpvdrone:drone_assembly", read("drone_assembly").get("type").getAsString());
		assertTrue(read("quantum_link_module").toString().contains("mekanism:pellet_antimatter"));
	}

	private static JsonObject read(String recipeName) throws IOException {
		String resourcePath = "/data/fpvdrone/recipe/" + recipeName + ".json";
		InputStream resource = MekanismRecipeResourcesTest.class.getResourceAsStream(resourcePath);
		assertNotNull(resource, "generated target resources must include " + resourcePath);
		try (resource) {
			return JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}
}
