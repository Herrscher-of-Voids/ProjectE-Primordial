/**
 * 【变更记录 #2】2026-09-16（流体与气体价格编辑回归）
 * - 删除：无；保留精确价表、旧字段兼容和能源换算的既有断言。
 * - 新增：验证价格编辑介质目标包能够无损往返流体注册名及小数/分数价格文本。
 * - 修改：测试只检查编码协议，不启动真实客户端或修改用户价表文件。
 * - 用途：防止新增的 JEI 价格页包在字段顺序、介质类型或高精度流体价格输入上发生回归。
 */
/**
 * 【文件职责】验证精确价表、旧字段保留、能源原生比例及客户端价格协议。
 * 【变更记录 #1】2026-09-15 15:14:04（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：临时 UTF-8 价格文件、真实可选 API、协议往返和无依赖类加载测试。
 * - 修改：所有文件写入 JUnit 临时目录；不修改模组包或用户价格配置。
 * - 用途：确保水/岩浆默认值、分数流体、超大 EMC 与 FE/RF/原生比例配置实际生效。
 */
/**
 * 【变更记录 #3】2026-09-16 01:59:37（第 3 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：独立物品价缓存包及 ValueTable 可编辑物品集合的断言。
 * - 新增：确认旧物品覆盖只作为原文保留，不被扩展价表重新解析或生成。
 * - 修改：保留气体大整数、流体分数、能源比例及请求编码回归；物品真实定价由 PriceEditorSaveTest 验证。
 * - 用途：防止未来重新引入第二套物品交易价，同时保留已有配置中的历史数据。
 */
/**
 * 【变更记录 #4】2026-10-10 04:14:04（第 4 次备注 · 1.2.0-resource-system-one）
 * - 删除：测试结束时直接单次删除临时目录的易波动清理路径。
 * - 新增：使用 RetryingTestTempDirFactory 的有界清理重试，持续失败仍导致测试失败。
 * - 修改：临时目录仍每项/每类独立，模组持久化代码和交易断言保持原有检查；工厂负责最终删除。
 * - 用途：Windows 短暂目录占用不再误判业务失败，绝不关闭真实保存或回滚验证。
 */
/**
 * 【变更记录 #5】2026-10-11 04:26:53（第 5 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：无。
 * - 新增：覆盖非法模式、非法价格与 inherit 组合的禁用回归。
 * - 修改：配置写回后再次读取，确认禁用状态持久化。
 * - 用途：防止损坏价表重新开启资源交易。
 */
package com.primordial.machine;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.energy.EnergyConversions;
import com.primordial.network.C2SPlayerActionPacket;
import com.primordial.service.EmcAmount;
import com.primordial.value.ValueTable;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmcValuesAndEnergyTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    @TempDir(factory = com.primordial.util.RetryingTestTempDirFactory.class, cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER) Path directory;

    @Test void defaultFluidsAndFractionalPricesSurviveUtf8ReloadAndLegacyFieldsAreRetained() throws Exception {
        var file = directory.resolve("values.json");
        var defaults = ValueTable.read(file);
        assertEquals(EmcAmount.parse("1000"), defaults.fluids.get("minecraft:water"));
        assertEquals(defaults.fluids.get("minecraft:water"), defaults.fluids.get("minecraft:lava"));
        Files.writeString(file, """
                {"item_values":{"test:old_auto":1.0},"item_generation_overrides":{"test:old_auto":32},
                "global_generation_cost_multiplier":10,"note":"保留原配置",
                "fluid_values":{"test:cheap":"1/3","minecraft:water":1000.0},
                "gas_values":{"test:gas":1000},"item_overrides":{"test:item":123456789012345678901234567890}}
                """, StandardCharsets.UTF_8);
        var table = ValueTable.read(file);
        assertEquals(EmcAmount.parse("1/3000"), table.fluids.get("test:cheap").divide(1000));
        table.write(file);
        var saved = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(saved.contains("保留原配置"));
        assertTrue(saved.contains("item_generation_overrides"));
        var reloaded = ValueTable.read(file);
        assertTrue(saved.contains("item_overrides"));
        assertEquals(table.fluids, reloaded.fluids);
        assertEquals(table.gases, reloaded.gases);
    }

    @Test void corruptPricesAreDisabledAndExponentInputsAreBounded() throws Exception {
        var file = directory.resolve("invalid.json");
        Files.writeString(file, """
                {"item_overrides":{"test:item":"NaN"},"gas_values":{"test:gas":"0.1"},
                "fluid_values":{"minecraft:water":"-2","test:bad":"1/0"}}
                """, StandardCharsets.UTF_8);
        var table = ValueTable.read(file);
        assertEquals(BigInteger.ZERO, table.gases.get("test:gas"));
        assertEquals(EmcAmount.ZERO, table.fluids.get("minecraft:water"));
        assertEquals(EmcAmount.ZERO, table.fluids.get("test:bad"));
        for (String text : List.of("1e2147483647", "1e-2147483647", "1/0", "-1", "NaN", "9".repeat(4355)))
            assertThrows(RuntimeException.class, () -> EmcAmount.parse(text), text.substring(0, Math.min(30, text.length())));
        assertThrows(IllegalArgumentException.class, () -> ValueTable.parseInteger("0.5"));
        assertEquals(BigInteger.valueOf(1000), ValueTable.parseInteger("1000.0"));
    }

    @Test void feRfAndCustomRatiosHonorConfigChangesWithoutDoubleConversion() {
        var unit = new ResourceLocation("test", "heat");
        try (var config = mockStatic(PrimordialServerConfig.class)) {
            config.when(PrimordialServerConfig::fePerEmc).thenReturn(BigInteger.valueOf(1000));
            config.when(PrimordialServerConfig::nativeFeRatios).thenReturn(List.of("test:heat=7/3"));
            assertEquals(EmcAmount.parse("1/1000"), EnergyConversions.emcPerUnit(EnergyConversions.FE));
            assertEquals(EnergyConversions.emcPerUnit(EnergyConversions.FE), EnergyConversions.emcPerUnit(EnergyConversions.RF));
            assertEquals(EmcAmount.parse("7/3000"), EnergyConversions.emcPerUnit(unit));
            config.when(PrimordialServerConfig::fePerEmc).thenReturn(BigInteger.TEN.pow(40));
            assertEquals(new EmcAmount(BigInteger.ONE, BigInteger.TEN.pow(40)), EnergyConversions.emcPerUnit(EnergyConversions.FE));
            config.when(PrimordialServerConfig::nativeFeRatios).thenReturn(List.of("test:heat=4/1"));
            assertEquals(EmcAmount.parse("4"), EnergyConversions.fePerUnit(unit));
        }
        assertFalse(EnergyConversions.validEntry("forge:fe=3/1"));
        assertFalse(EnergyConversions.validEntry("cofh:rf=3/1"));
        assertFalse(EnergyConversions.validEntry("test:heat=1/0"));
        assertFalse(EnergyConversions.validEntry("missing separator"));
    }

    @Test void invalidModesDisableTradingEvenWhenACustomPriceExistsOrNoPriceIsListed() throws Exception {
        var file = directory.resolve("invalid-modes.json");
        Files.writeString(file, """
                {"fluid_values":{"minecraft:water":"5/7"},"gas_values":{"test:gas":"900"},
                 "fluid_modes":{"minecraft:water":"sell_ony","test:unlisted":null},
                 "gas_modes":{"test:gas":"buy_ony","test:unlisted":{"bad":true}}}
                """, StandardCharsets.UTF_8);
        var table = ValueTable.read(file);
        assertEquals(EmcAmount.parse("5/7"), table.fluids.get("minecraft:water"));
        assertEquals(BigInteger.valueOf(900), table.gases.get("test:gas"));
        assertEquals(com.primordial.value.PriceMode.DISABLED,
                table.fluidModes.get("minecraft:water"));
        assertEquals(com.primordial.value.PriceMode.DISABLED,
                table.fluidModes.get("test:unlisted"));
        assertEquals(com.primordial.value.PriceMode.DISABLED,
                table.gasModes.get("test:gas"));
        assertEquals(com.primordial.value.PriceMode.DISABLED,
                table.gasModes.get("test:unlisted"));
        table.write(file);
        var reloaded = ValueTable.read(file);
        assertEquals(table.fluidModes, reloaded.fluidModes);
        assertEquals(table.gasModes, reloaded.gasModes);
    }

    @Test void corruptPricesCannotBeReenabledByADeclaredInheritMode() throws Exception {
        var file = directory.resolve("invalid-inherit.json");
        Files.writeString(file, """
                {"fluid_values":{"minecraft:water":"1/0"},"gas_values":{"test:gas":"-1"},
                 "fluid_modes":{"minecraft:water":"inherit_native"},
                 "gas_modes":{"test:gas":"inherit_native"}}
                """, StandardCharsets.UTF_8);
        var table = ValueTable.read(file);
        assertEquals(com.primordial.value.PriceMode.DISABLED, table.fluidModes.get("minecraft:water"));
        assertEquals(com.primordial.value.PriceMode.DISABLED, table.gasModes.get("test:gas"));
        assertEquals(EmcAmount.ZERO, table.fluids.get("minecraft:water"));
        assertEquals(BigInteger.ZERO, table.gases.get("test:gas"));
        table.write(file);
        var reloaded = ValueTable.read(file);
        assertEquals(table.fluidModes, reloaded.fluidModes);
        assertEquals(table.gasModes, reloaded.gasModes);
    }

    @Test void acceptedDecimalBoundaryAlwaysRoundTripsThroughSavedFraction() throws Exception {
        var file = directory.resolve("tiny.json");
        var table = new ValueTable();
        var tiny = EmcAmount.parse("1e-128");
        assertEquals(tiny, EmcAmount.parse(tiny.toString()));
        table.fluids.put("test:tiny", tiny); table.write(file);
        assertEquals(tiny, ValueTable.read(file).fluids.get("test:tiny"));
        assertThrows(IllegalArgumentException.class, () -> EmcAmount.parse("9".repeat(4096) + ".1"));
    }

    @Test void auditedNativeApisUseCorrectConversionDirectionAndOverridesWin() {
        var mods = mock(ModList.class);
        when(mods.isLoaded("mekanism")).thenReturn(true);
        when(mods.isLoaded("ae2")).thenReturn(true);
        double oldAe = appeng.api.config.PowerUnits.AE.conversionRatio;
        double oldFe = appeng.api.config.PowerUnits.FE.conversionRatio;
        try (var modList = mockStatic(ModList.class); var config = mockStatic(PrimordialServerConfig.class)) {
            modList.when(ModList::get).thenReturn(mods);
            config.when(PrimordialServerConfig::nativeFeRatios).thenReturn(List.of());
            // UnitDisplayUtils 从实际 Mekanism GeneralConfig 默认值读取 1 FE = 2.5 J。
            assertEquals(EmcAmount.parse("2/5"), EnergyConversions.fePerUnit(new ResourceLocation("mekanism", "joule")));
            appeng.api.config.PowerUnits.AE.conversionRatio = 1;
            appeng.api.config.PowerUnits.FE.conversionRatio = 0.5;
            assertEquals(EmcAmount.parse("2"), EnergyConversions.fePerUnit(new ResourceLocation("ae2", "ae")));
            appeng.api.config.PowerUnits.FE.conversionRatio = 0.25;
            assertEquals(EmcAmount.parse("4"), EnergyConversions.fePerUnit(new ResourceLocation("ae2", "ae")));
            config.when(PrimordialServerConfig::nativeFeRatios).thenReturn(List.of("ae2:ae=3/7"));
            assertEquals(EmcAmount.parse("3/7"), EnergyConversions.fePerUnit(new ResourceLocation("ae2", "ae")));
        } finally {
            appeng.api.config.PowerUnits.AE.conversionRatio = oldAe;
            appeng.api.config.PowerUnits.FE.conversionRatio = oldFe;
        }
    }

    @Test void optionalEnergyClassesCanBeAbsent() throws Exception {
        var code = EnergyConversions.class.getProtectionDomain().getCodeSource().getLocation();
        try (var mods = mockStatic(ModList.class);
             var loader = new java.net.URLClassLoader(new java.net.URL[]{code}, getClass().getClassLoader()) {
                 @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                     if (name.startsWith("mekanism.") || name.startsWith("appeng.")) throw new ClassNotFoundException(name);
                     if (name.startsWith("com.primordial.energy.")) {
                         synchronized (getClassLoadingLock(name)) {
                             Class<?> type = findLoadedClass(name);
                             if (type == null) type = findClass(name);
                             if (resolve) resolveClass(type);
                             return type;
                         }
                     }
                     return super.loadClass(name, resolve);
                 }
             }) {
            mods.when(ModList::get).thenReturn(mock(ModList.class));
            var facade = loader.loadClass(EnergyConversions.class.getName());
            var quote = facade.getMethod("fePerUnit", ResourceLocation.class);
            assertEquals(EmcAmount.ONE, quote.invoke(null, EnergyConversions.FE));
            assertEquals(EmcAmount.ZERO, quote.invoke(null, new ResourceLocation("mekanism", "joule")));
        }
    }

    @Test void bigIntegerPriceRequestsRoundTripWithoutRounding() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        var second = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BigInteger value = BigInteger.TEN.pow(200).add(BigInteger.valueOf(71));
            C2SPlayerActionPacket.encode(new C2SPlayerActionPacket("minecraft:diamond", value, 12), buffer);
            var packet = C2SPlayerActionPacket.decode(buffer);
            C2SPlayerActionPacket.encode(packet, second);
            assertEquals(1, second.readByte());
            assertEquals("minecraft:diamond", second.readUtf(256));
            assertEquals(value.toString(), second.readUtf(4096));
            assertEquals(12, second.readVarInt());
        } finally { buffer.release(); second.release(); }
    }

    @Test void mediaPriceTargetAndFractionPacketRoundTrip() {
        var targetBuffer = new FriendlyByteBuf(Unpooled.buffer());
        var priceBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var target = new com.primordial.network.C2SSetPriceTargetPacket(
                    19, com.primordial.menu.PriceEditorMenu.KIND_FLUID, "minecraft:water");
            com.primordial.network.C2SSetPriceTargetPacket.encode(target, targetBuffer);
            assertEquals(target, com.primordial.network.C2SSetPriceTargetPacket.decode(targetBuffer));

            var price = new com.primordial.network.C2SSetMediaPricePacket(
                    19, com.primordial.menu.PriceEditorMenu.KIND_FLUID, "minecraft:water", "1/3");
            com.primordial.network.C2SSetMediaPricePacket.encode(price, priceBuffer);
            assertEquals(price, com.primordial.network.C2SSetMediaPricePacket.decode(priceBuffer));
        } finally {
            targetBuffer.release();
            priceBuffer.release();
        }
    }
}
