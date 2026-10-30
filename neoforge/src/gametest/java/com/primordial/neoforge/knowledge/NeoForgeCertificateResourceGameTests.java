/**
 * 【文件职责】用真实注册表、配方管理器和 mod 配置事件校验凭证资源闭环。
 * 【变更记录 #1】2026-10-23 13:35:01（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：11 种注册身份、条件配方与真实能源凭证合成、Loading/Reloading 快照一致性验证。
 * - 修改：测试只反射读取 FML 已注册配置对象，事件仍经过真实 mod 总线；finally 恢复配置。
 * - 用途：发现错误 recipe 目录、result 字段、条件注册或热重载误改会话策略；不进入生产 JAR。
 * 【变更记录 #2】2026-10-23 13:42:08（第 2 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：把重启型 ConfigValue.set 当作冷启动自动清缓存的错误测试假设。
 * - 新增：模拟冷启动前显式 clearCache，finally 对恢复值也清缓存。
 * - 修改：Reloading 验证仍保留缓存，Loading 验证才按真实重新载入后的值发布快照。
 * - 用途：测试启动快照的事件接线，不要求世界重启型设置即时改变运行会话。
 */
package com.primordial.neoforge.knowledge;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.registry.NeoForgeCertificates;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeCertificateResourceGameTests {
    private NeoForgeCertificateResourceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = "primordial",
            batch = "primordial_knowledge_resources", setupTicks = 0)
    public static void registeredCertificatesAndActualConditionalCrafting(GameTestHelper helper) {
        check(NeoForgeKnowledgeConfig.required(), "This test world starts with knowledge required");
        var recipes = helper.getLevel().getServer().getRecipeManager();
        check(NeoForgeCertificates.CERTIFICATES.size() == 11, "All eleven Forge certificate identities must be registered");
        for (Kind kind : Kind.values()) {
            check(BuiltInRegistries.ITEM.getKey(NeoForgeCertificates.CERTIFICATES.get(kind).get()).equals(id(kind.itemId())),
                    "Certificate IDs must match the maintained Forge branch");
            boolean expected = kind != Kind.FLUID && kind != Kind.GAS
                    && (kind != Kind.IRON_MANA || ModList.get().isLoaded("irons_spellbooks"))
                    && (kind != Kind.SOUL_ENERGY || ModList.get().isLoaded("goety"));
            check(recipes.byKey(id(kind.itemId())).isPresent() == expected,
                    "Fluid/gas have no default recipe; optional resource recipes require their actual mod");
        }
        ItemStack darkMatter = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("projecte:dark_matter")));
        CraftingInput input = CraftingInput.of(3, 3, List.of(
                new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.PAPER), new ItemStack(Items.GOLD_INGOT),
                darkMatter.copy(), new ItemStack(Items.REDSTONE_BLOCK), darkMatter.copy(),
                new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.PAPER), new ItemStack(Items.GOLD_INGOT)));
        var recipe = recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
        ItemStack result = recipe.value().assemble(input, helper.getLevel().registryAccess());
        check(result.is(NeoForgeCertificates.CERTIFICATES.get(Kind.ENERGY).get()) && result.getCount() == 1,
                "The actual 1.21.1 recipe must craft exactly one energy certificate from the unchanged ingredients");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = "primordial",
            batch = "primordial_knowledge_resources", setupTicks = 0)
    public static void loadingSnapshotAndRecipeConditionShareOneRule(GameTestHelper helper) throws ReflectiveOperationException {
        check(NeoForgeKnowledgeConfig.SPEC.isLoaded(), "COMMON config must load before recipes and gameplay");
        Field configs = ConfigTracker.class.getDeclaredField("configsByMod");
        configs.setAccessible(true);
        var values = (Map<?, ?>) configs.get(ConfigTracker.INSTANCE);
        ModConfig config = ((List<?>) values.get("primordial")).stream().map(ModConfig.class::cast)
                .filter(entry -> entry.getSpec() == NeoForgeKnowledgeConfig.SPEC).findFirst().orElseThrow();
        var container = ModList.get().getModContainerById("primordial").orElseThrow();
        boolean previous = NeoForgeKnowledgeConfig.REQUIRE_KNOWLEDGE.get();
        ICondition condition = ICondition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"primordial:knowledge_required\"}")).getOrThrow();
        try {
            NeoForgeKnowledgeConfig.REQUIRE_KNOWLEDGE.set(false);
            container.acceptEvent(new ModConfigEvent.Reloading(config));
            check(NeoForgeKnowledgeConfig.required() && condition.test(ICondition.IContext.EMPTY),
                    "Reloading must not mutate the running knowledge/recipe snapshot");
            NeoForgeKnowledgeConfig.REQUIRE_KNOWLEDGE.clearCache();
            container.acceptEvent(new ModConfigEvent.Loading(config));
            check(!NeoForgeKnowledgeConfig.required() && !condition.test(ICondition.IContext.EMPTY),
                    "The registered native Loading event must publish the same false snapshot to both consumers");
        } finally {
            NeoForgeKnowledgeConfig.REQUIRE_KNOWLEDGE.set(previous);
            NeoForgeKnowledgeConfig.REQUIRE_KNOWLEDGE.clearCache();
            container.acceptEvent(new ModConfigEvent.Loading(config));
        }
        check(NeoForgeKnowledgeConfig.required() == previous, "The actual original startup rule must be restored");
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("primordial", path); }
    private static void check(boolean valid, String message) { if (!valid) throw new GameTestAssertException(message); }
}
