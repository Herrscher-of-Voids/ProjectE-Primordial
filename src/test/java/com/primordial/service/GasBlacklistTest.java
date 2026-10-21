/** 【文件职责】验证气体黑名单标签、旧规则和可选模组缺失边界。
 * 【变更记录 #1】2026-10-10 01:55:50（第 1 次备注 · 1.2.0-gas-blacklist-tags）
 * - 删除：无。新增：真实配置接受标签、标签变化立即生效及旧 ID/通配规则回归。
 * - 修改：无。用途：防止标签规则被配置校验删除或缓存旧结果；不改生产数据。 */
/**
 * 【变更记录 #2】2026-10-13 23:31:57（第 2 次备注 · 1.2.0-blacklist-system）
 * - 删除/新增/修改：新增模拟标签变化后的缓存失效调用；删除无需标签更新事件即可改变成员的测试假设；与游戏 /reload 标签同步边界一致，旧规则仍完整验证。
 * - 用途：第 10 系统保持作者主动配置才生效；缓存和诊断共用真实规则，不删除已有价格与知识，不新增默认交易门槛。
 */
package com.primordial.service;

import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.machine.AdapterTestBootstrap;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GasBlacklistTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void tagRulesSurviveValidationAndFollowCurrentMembership() throws Exception {
        var field = ForgeConfigSpec.class.getDeclaredField("childConfig");
        field.setAccessible(true);
        var previous = (CommentedConfig) field.get(PrimordialServerConfig.SPEC);
        var gas = new ResourceLocation("mekanism:hydrogen");
        var tag = new ResourceLocation("test:blocked_gases");
        try (var compat = mockStatic(MekanismGasCompat.class)) {
            var config = CommentedConfig.inMemory();
            config.set(PrimordialServerConfig.GAS_BLACKLIST.getPath(), List.of("#test:blocked_gases"));
            PrimordialServerConfig.SPEC.setConfig(config);
            assertEquals(List.of("#test:blocked_gases"), PrimordialServerConfig.GAS_BLACKLIST.get());
            assertFalse(PrimordialServerConfig.gasBlocked(gas));
            compat.when(() -> MekanismGasCompat.gasInTag(gas, tag)).thenReturn(true);
            com.primordial.config.ResourceBlacklistRules.invalidate();
            assertTrue(PrimordialServerConfig.gasBlocked(gas));
            compat.when(() -> MekanismGasCompat.gasInTag(gas, tag)).thenReturn(false);
            com.primordial.config.ResourceBlacklistRules.invalidate();
            assertFalse(PrimordialServerConfig.gasBlocked(gas));
            PrimordialServerConfig.GAS_BLACKLIST.set(List.of("mekanism:hydrogen"));
            assertTrue(PrimordialServerConfig.gasBlocked(gas));
            PrimordialServerConfig.GAS_BLACKLIST.set(List.of("mekanism:*"));
            assertTrue(PrimordialServerConfig.gasBlocked(gas));
            assertFalse(PrimordialServerConfig.gasBlocked(new ResourceLocation("other:hydrogen")));
            assertTrue(PrimordialServerConfig.gasBlocked(null));
        } finally { PrimordialServerConfig.SPEC.setConfig(previous); }
    }

    @Test void missingMekanismDoesNotResolveGasApi() {
        try (var compat = mockStatic(MekanismGasCompat.class, CALLS_REAL_METHODS)) {
            compat.when(MekanismGasCompat::loaded).thenReturn(false);
            assertFalse(MekanismGasCompat.gasInTag(new ResourceLocation("mekanism:hydrogen"),
                    new ResourceLocation("test:blocked_gases")));
        }
    }
}
