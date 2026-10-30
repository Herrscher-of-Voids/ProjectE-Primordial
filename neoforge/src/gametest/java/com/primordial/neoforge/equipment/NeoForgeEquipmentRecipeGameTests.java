package com.primordial.neoforge.equipment;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.NeoForgeModRegistries;
import moze_intel.projecte.api.conversion.CustomConversionFile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeEquipmentRecipeGameTests {
    private NeoForgeEquipmentRecipeGameTests() {}

    @GameTest(template="capability_proxy_empty", templateNamespace=PrimordialNeoForgeMod.MOD_ID)
    public static void customArmorRetainsForgeStatsTexturesAndNoDurability(GameTestHelper helper) {
        for(String name:new String[]{"helmet","chestplate","leggings","boots"}) {
            ItemStack stack=new ItemStack(NeoForgeModRegistries.BLACKHOLE_GEAR.get(name).get());
            ArmorItem armor=(ArmorItem)stack.getItem(); var material=armor.getMaterial().value();
            check(material.getDefense(ArmorItem.Type.HELMET)==10&&material.getDefense(ArmorItem.Type.CHESTPLATE)==16
                    &&material.getDefense(ArmorItem.Type.LEGGINGS)==14&&material.getDefense(ArmorItem.Type.BOOTS)==10,
                    "Custom armor defenses must match Forge");
            check(material.enchantmentValue()==30&&material.toughness()==8&&material.knockbackResistance()==0.25F,
                    "Custom material secondary stats must match Forge");
            check(material.layers().size()==1&&material.layers().getFirst().texture(false).toString().equals("primordial:textures/models/armor/blackhole_layer_1.png")
                    &&material.layers().getFirst().texture(true).toString().equals("primordial:textures/models/armor/blackhole_layer_2.png"),
                    "The material renderer must select the original blackhole armor layers");
            stack.setDamageValue(123);
            check(!stack.isDamageableItem()&&stack.getDamageValue()==0,"Armor must remain unbreakable after damage writes");
        }
        helper.succeed();
    }

    @GameTest(template="capability_proxy_empty", templateNamespace=PrimordialNeoForgeMod.MOD_ID)
    public static void everyEquipmentRecipeLoadsAndActuallyCrafts(GameTestHelper helper) {
        for(String name:new String[]{"blackhole","blackhole_material_primordial","blackhole_helmet","blackhole_chestplate",
                "blackhole_leggings","blackhole_boots","blackhole_pick","blackhole_axe","blackhole_shovel","blackhole_hoe",
                "blackhole_shears","blackhole_sword","blackhole_hammer","blackhole_katar","blackhole_morning_star","blackhole_bow","blackhole_charm","emc_accelerator","entity_generator","transmutation_interface"}) {
            ResourceLocation id=ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID,name);
            var holder=helper.getLevel().getRecipeManager().byKey(id).orElseThrow(()->new GameTestAssertException("Missing 1.21 recipe: "+id));
            check(holder.value() instanceof ShapedRecipe,"Equipment recipe must remain shaped: "+id);
            ShapedRecipe recipe=(ShapedRecipe)holder.value(); var ingredients=new ArrayList<ItemStack>();
            for(var ingredient:recipe.getIngredients())ingredients.add(ingredient.isEmpty()?ItemStack.EMPTY:ingredient.getItems()[0].copy());
            CraftingInput input=CraftingInput.of(recipe.getWidth(),recipe.getHeight(),ingredients);
            check(recipe.matches(input,helper.getLevel()),"Loaded ingredient grid must match: "+id);
            ItemStack result=recipe.assemble(input,helper.getLevel().registryAccess());
            check(!result.isEmpty()&&result.getCount()==1&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(result.getItem()).equals(id),
                    "The recipe must craft exactly its registered result: "+id);
        }
        helper.succeed();
    }

    @GameTest(template="capability_proxy_empty", templateNamespace=PrimordialNeoForgeMod.MOD_ID)
    public static void materialPricesAndConversionsDecodeWithActualProjectECodec(GameTestHelper helper) throws Exception {
        String path="/data/primordial/pe_custom_conversions/blackhole_materials.json";
        try(var stream=NeoForgeEquipmentRecipeGameTests.class.getResourceAsStream(path)) {
            check(stream!=null,"Material price data must be packaged");
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
            CustomConversionFile data=CustomConversionFile.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
            check(data.values().setValueAfter().size()==9,"The native codec must retain all nine fixed EMC prices");
            check(data.groups().get("blackhole_collector_upgrade").conversions().size()==8,"The native codec must retain all eight adjacent upgrade conversions");
        }
        helper.succeed();
    }

    private static void check(boolean success,String message) { if(!success)throw new GameTestAssertException(message); }
}
