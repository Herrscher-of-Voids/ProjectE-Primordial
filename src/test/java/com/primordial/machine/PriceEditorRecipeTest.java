package com.primordial.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PriceEditorRecipeTest {
    @Test void survivalRecipeUsesTheAgreedMaterialsAndUnlocksInTheRecipeBook() throws Exception {
        var recipe = resource("recipes/price_editor.json");
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals(JsonParser.parseString("[\"DPD\",\"MCM\",\"DPD\"]"), recipe.get("pattern"));
        var keys = recipe.getAsJsonObject("key");
        assertEquals("minecraft:diamond", keys.getAsJsonObject("D").get("item").getAsString());
        assertEquals("minecraft:paper", keys.getAsJsonObject("P").get("item").getAsString());
        assertEquals("projecte:dark_matter", keys.getAsJsonObject("M").get("item").getAsString());
        assertEquals("minecraft:clock", keys.getAsJsonObject("C").get("item").getAsString());
        assertEquals("primordial:price_editor", recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());

        var advancement = resource("advancements/recipes/misc/price_editor.json");
        assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString());
        assertEquals("primordial:price_editor", advancement.getAsJsonObject("rewards")
                .getAsJsonArray("recipes").get(0).getAsString());
        assertEquals(JsonParser.parseString("[[\"has_dark_matter\",\"has_the_recipe\"]]"),
                advancement.get("requirements"));
        assertEquals("projecte:dark_matter", advancement.getAsJsonObject("criteria")
                .getAsJsonObject("has_dark_matter").getAsJsonObject("conditions")
                .getAsJsonArray("items").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsString());
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = PriceEditorRecipeTest.class.getClassLoader()
                .getResourceAsStream("data/primordial/" + path)) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
