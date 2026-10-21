/**
 * 【文件职责】验证真实 Forge 配置、标签失效、缓存边界和规则规范化。
 * 【变更记录 #1】2026-10-13 23:30:00（第 1 次备注 · 1.2.0-blacklist-system）
 * - 删除：无。新增：ID/通配/标签、对象与 ID 一致性、重载和大规则集重复查询回归。
 * - 修改：流体使用真实注册表标签，气体仅模拟可选注册边界。用途：缓存提速不沿用旧世界或旧标签判断。
 */
/**
 * 【变更记录 #2】2026-10-13 23:31:57（第 2 次备注 · 1.2.0-blacklist-system）
 * - 删除/新增/修改：删除纯 JUnit 中构造 ModConfigEvent 导致 ModLauncher 未初始化的路径；新增只读诊断命令权限与不改配置断言；重载直接调用同一失效入口，真实事件注册由实例启动检查。
 * - 用途：第 10 系统保持作者主动配置才生效；缓存和诊断共用真实规则，不删除已有价格与知识，不新增默认交易门槛。
 */
package com.primordial.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.primordial.compat.MekanismGasCompat;
import com.primordial.machine.AdapterTestBootstrap;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TagsUpdatedEvent;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceBlacklistRulesTest {
    private CommentedConfig previous;
    private static final ResourceLocation WATER = new ResourceLocation("minecraft:water");
    private static final ResourceLocation GAS = new ResourceLocation("mekanism:hydrogen");
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    @BeforeEach void setup() throws Exception {
        var field = ForgeConfigSpec.class.getDeclaredField("childConfig"); field.setAccessible(true);
        previous = (CommentedConfig) field.get(PrimordialServerConfig.SPEC);
        PrimordialServerConfig.SPEC.setConfig(CommentedConfig.inMemory());
        ResourceBlacklistRules.invalidate();
    }
    @AfterEach void cleanup() { PrimordialServerConfig.SPEC.setConfig(previous); ResourceBlacklistRules.invalidate(); }

    @Test void defaultRulesAreEmptyAndCanonicalIdsMatchAcrossAllKinds() {
        assertFalse(PrimordialServerConfig.fluidBlocked(Fluids.WATER));
        assertFalse(PrimordialServerConfig.gasBlocked(GAS));
        assertFalse(PrimordialServerConfig.resourceBlocked(new ResourceLocation("forge:fe")));
        PrimordialServerConfig.FLUID_BLACKLIST.set(List.of(" water "));
        assertTrue(PrimordialServerConfig.fluidBlocked(WATER));
        assertTrue(PrimordialServerConfig.fluidBlocked(Fluids.WATER));
        assertEquals("minecraft:water", ResourceBlacklistRules.matchingRule(ResourceBlacklistRules.Kind.FLUID, WATER).orElseThrow());
        PrimordialServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:*", "minecraft:water"));
        assertEquals("minecraft:water", ResourceBlacklistRules.matchingRule(ResourceBlacklistRules.Kind.FLUID, WATER).orElseThrow());
        assertTrue(PrimordialServerConfig.fluidBlocked(Fluids.LAVA));
        PrimordialServerConfig.GAS_BLACKLIST.set(List.of("mekanism:*"));
        assertTrue(PrimordialServerConfig.gasBlocked(GAS));
        assertFalse(PrimordialServerConfig.gasBlocked(new ResourceLocation("other:hydrogen")));
        PrimordialServerConfig.RESOURCE_BLACKLIST.set(List.of("botania:*"));
        assertTrue(PrimordialServerConfig.resourceBlocked(new ResourceLocation("botania:mana")));
        assertFalse(PrimordialServerConfig.resourceBlocked(new ResourceLocation("forge:fe")));
        assertTrue(PrimordialServerConfig.fluidBlocked((ResourceLocation) null));
    }

    @Test void fluidTagsApplyToBothOverloadsAndRefreshAfterDataReload() {
        var registry = BuiltInRegistries.FLUID;
        var previousTags = registry.getTags().collect(java.util.stream.Collectors.toMap(
                com.mojang.datafixers.util.Pair::getFirst, pair -> pair.getSecond().stream().toList()));
        var tag = TagKey.create(Registries.FLUID, new ResourceLocation("pack:blocked"));
        try {
            registry.bindTags(java.util.Map.of(tag, List.of(registry.wrapAsHolder(Fluids.WATER))));
            PrimordialServerConfig.FLUID_BLACKLIST.set(List.of("#pack:blocked"));
            assertTrue(PrimordialServerConfig.fluidBlocked(Fluids.WATER));
            assertTrue(PrimordialServerConfig.fluidBlocked(WATER));
            assertFalse(PrimordialServerConfig.fluidBlocked(Fluids.LAVA));
            registry.bindTags(java.util.Map.of(tag, List.of(registry.wrapAsHolder(Fluids.LAVA))));
            ResourceBlacklistRules.onTagsUpdated(mock(TagsUpdatedEvent.class));
            assertFalse(PrimordialServerConfig.fluidBlocked(WATER));
            assertTrue(PrimordialServerConfig.fluidBlocked(Fluids.LAVA));
        } finally { registry.bindTags(previousTags); ResourceBlacklistRules.invalidate(); }
    }

    @Test void repeatedQueriesDoNotReparseListsOrRescanTagsAndReloadInvalidatesResults() {
        var tag = new ResourceLocation("pack:gases");
        var entries = new java.util.ArrayList<String>();
        for (int i = 0; i < 2000; i++) entries.add("other:gas_" + i);
        entries.add("#pack:gases");
        var source = spy(entries);
        PrimordialServerConfig.GAS_BLACKLIST.set(source);
        try (var compat = mockStatic(MekanismGasCompat.class)) {
            compat.when(() -> MekanismGasCompat.gasInTag(GAS, tag)).thenReturn(true);
            for (int i = 0; i < 1000; i++) assertTrue(PrimordialServerConfig.gasBlocked(GAS));
            compat.verify(() -> MekanismGasCompat.gasInTag(GAS, tag), times(1));
            verify(source, times(1)).iterator();
            compat.when(() -> MekanismGasCompat.gasInTag(GAS, tag)).thenReturn(false);
            ResourceBlacklistRules.onTagsUpdated(mock(TagsUpdatedEvent.class));
            assertFalse(PrimordialServerConfig.gasBlocked(GAS));
            compat.verify(() -> MekanismGasCompat.gasInTag(GAS, tag), times(2));
            source.add("mekanism:hydrogen");
            // 纯 JUnit 不启动 ModLauncher；调用 Forge 重载监听器使用的同一个失效入口。
            ResourceBlacklistRules.invalidate();
            assertTrue(PrimordialServerConfig.gasBlocked(GAS));
            PrimordialServerConfig.GAS_BLACKLIST.set(List.of());
            assertFalse(PrimordialServerConfig.gasBlocked(GAS));
        }
    }

    @Test void invalidEntriesAreRejectedIndividuallyWithoutChangingOtherRules() {
        for (String invalid : List.of("", " ", "#", "#pack:*", "pack:bad value", ":*", "x".repeat(513)))
            assertFalse(ResourceBlacklistRules.validEntry(invalid, true), invalid);
        assertFalse(ResourceBlacklistRules.validEntry(17, true));
        assertFalse(ResourceBlacklistRules.validEntry("#pack:resources", false));
        assertTrue(ResourceBlacklistRules.validEntry(" #pack:fluids ", true));
        PrimordialServerConfig.FLUID_BLACKLIST.set(List.of("invalid entry", "minecraft:water"));
        assertTrue(PrimordialServerConfig.fluidBlocked(WATER));
        assertFalse(PrimordialServerConfig.fluidBlocked(Fluids.LAVA));
    }

    @Test void queryCacheRemainsBoundedAndUnloadedWorldDoesNotKeepRules() throws Exception {
        PrimordialServerConfig.GAS_BLACKLIST.set(List.of("mekanism:*"));
        for (int i = 0; i < 5000; i++) assertTrue(PrimordialServerConfig.gasBlocked(new ResourceLocation("mekanism:gas_" + i)));
        var field = ResourceBlacklistRules.class.getDeclaredField("snapshots"); field.setAccessible(true);
        Object snapshot = ((Object[]) field.get(null))[ResourceBlacklistRules.Kind.GAS.ordinal()];
        var results = snapshot.getClass().getDeclaredMethod("results"); results.setAccessible(true);
        assertTrue(((java.util.Map<?, ?>) results.invoke(snapshot)).size() <= 4096);
        PrimordialServerConfig.SPEC.setConfig(null);
        assertFalse(PrimordialServerConfig.gasBlocked(GAS));
    }

    @Test void diagnosticCommandRequiresAdminAndOnlyReportsCurrentRule() throws Exception {
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        com.primordial.command.PrimordialCommands.register(dispatcher);
        var source = mock(net.minecraft.commands.CommandSourceStack.class);
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("primordial blacklist check fluid minecraft:water", source));
        when(source.hasPermission(2)).thenReturn(true);
        PrimordialServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:water"));
        assertEquals(1, dispatcher.execute("primordial blacklist check fluid minecraft:water", source));
        assertEquals(0, dispatcher.execute("primordial blacklist check fluid minecraft:lava", source));
        assertEquals(0, dispatcher.execute("primordial blacklist check fluid unknown:fluid", source));
        assertEquals(List.of("minecraft:water"), PrimordialServerConfig.FLUID_BLACKLIST.get());
        verify(source, never()).getServer();
    }
}
