/**
 * 【文件职责】通过真实注册表、ProjectE 售价和独立临时价表验收黑洞资源报价边界。
 * 【变更记录 #1】2026-10-22 21:46:17（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：分数报价、卖出方向、坏文件与坏条目隔离、配置黑名单和原生物品组件保留测试。
 * - 修改：所有共享价表与配置变更在单个同步 GameTest 中顺序执行，finally 恢复运行配置快照。
 * - 用途：避免每 mB/FE 提前取整、继承模式误用旧覆盖、读取失败覆盖管理员文件或测试污染后续批次。
 * 【变更记录 #2】2026-10-23 11:41:32（第 2 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无；保留现有回收边界测试。
 * - 新增：购买价与售价分离、流体双向模式/黑名单/坏文件断言，以及独立购买开关 GameTest。
 * - 修改：临时将 ProjectE 回收倍率设为 0.5，验证购买仍用原生全价；finally 恢复全部配置和正式价表快照。
 * - 用途：防止误用回收价低价购买、卖出开关影响购买或非法条目从购买方向绕过停用。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonParser;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.service.EmcAmount;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.config.ProjectEConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeRecoveryPriceGameTests {
    private NeoForgeRecoveryPriceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_prices", setupTicks = 0, required = true)
    public static void preservesExactRecoveryPricesAndRejectsInvalidConfiguration(GameTestHelper helper) throws IOException {
        check(NeoForgeServerConfig.SPEC.isLoaded(), "Server configuration must be loaded for the price contract test");
        String previousRatio = NeoForgeServerConfig.FE_PER_EMC.get();
        List<? extends String> previousFluids = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        List<? extends String> previousResources = List.copyOf(NeoForgeServerConfig.RESOURCE_BLACKLIST.get());
        boolean previousSale = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
        boolean previousPurchase = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
        Path directory = Files.createTempDirectory("primordial-recovery-prices-");
        Path file = directory.resolve("primordial_values.json");
        try {
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of());
            verifyDefaultFile(file);
            verifyExactFractions(file);
            verifyDirectionalModes(file);
            verifyInvalidEntryIsolation(file);
            verifyInvalidFiles(file, directory);
            verifyFluidRestrictions(file);
            verifyEnergyRestrictions();
            verifyNativeItemQuote();
        } finally {
            NeoForgeServerConfig.FE_PER_EMC.set(previousRatio);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(previousFluids);
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(previousResources);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(previousSale);
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(previousPurchase);
            // 不保存临时配置；恢复生产运行目录的价表，绝不把测试夹具写入管理员配置。
            NeoForgeRecoveryPrices.reload();
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_prices", setupTicks = 0, required = true)
    public static void distinguishesNativePurchasePricesAndIndependentFluidSwitches(GameTestHelper helper) throws IOException {
        check(NeoForgeServerConfig.SPEC.isLoaded(), "Server configuration must be loaded for purchase price tests");
        boolean previousSale = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
        boolean previousPurchase = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
        List<? extends String> previousBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        double previousLoss = ProjectEConfig.server.difficulty.covalenceLoss.get();
        Path directory = Files.createTempDirectory("primordial-purchase-prices-");
        Path file = directory.resolve("primordial_values.json");
        try {
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(false);
            load(file, "{}");
            check(waterBuy().isZero() && lavaBuy().isZero() && water().equals(EmcAmount.ONE),
                    "Disabling fluid purchases must reject output while retaining recovery");
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(false);
            check(waterBuy().equals(EmcAmount.ONE) && lavaBuy().equals(EmcAmount.ONE) && water().isZero(),
                    "Disabling fluid sales must leave purchases available");
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(false);
            check(waterBuy().isZero() && water().isZero(), "Both switches disabled must reject both directions");
            check(NeoForgeRecoveryPrices.fluidPurchase(null).isZero()
                            && NeoForgeRecoveryPrices.fluidPurchase(FluidStack.EMPTY).isZero(),
                    "Empty fluid input must never produce a purchase quote");

            ProjectEConfig.server.difficulty.covalenceLoss.set(0.5);
            ItemStack source = new ItemStack(Items.DIAMOND, 3);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("native purchase price probe"));
            ItemStack original = source.copy();
            long nativeBuy = IEMCProxy.INSTANCE.getValue(ItemInfo.fromStack(source));
            long nativeSell = IEMCProxy.INSTANCE.getSellValue(ItemInfo.fromStack(source));
            check(nativeBuy > nativeSell && nativeSell > 0, "The native test fixture must distinguish purchase and recovery values");
            check(NeoForgeRecoveryPrices.itemPurchase(source).equals(EmcAmount.of(BigInteger.valueOf(nativeBuy)))
                            && NeoForgeRecoveryPrices.item(source).equals(EmcAmount.of(BigInteger.valueOf(nativeSell))),
                    "Buying must use the native full value while selling obeys ProjectE's configured loss");
            check(source.getCount() == original.getCount() && ItemStack.isSameItemSameComponents(source, original),
                    "Neither direction may mutate item counts or components while quoting");
            check(NeoForgeRecoveryPrices.itemPurchase(null).isZero()
                            && NeoForgeRecoveryPrices.itemPurchase(ItemStack.EMPTY).isZero(),
                    "Empty item input must have no purchase value");
        } finally {
            ProjectEConfig.server.difficulty.covalenceLoss.set(previousLoss);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(previousSale);
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(previousPurchase);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(previousBlacklist);
            NeoForgeRecoveryPrices.reload();
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    private static void verifyDefaultFile(Path file) throws IOException {
        check(NeoForgeRecoveryPrices.load(file), "A missing price file must receive readable defaults");
        var root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        check(root.get("format_version").getAsInt() == 8 && root.has("gas_values") && root.has("gas_modes"),
                "New price files must preserve format 8 metadata and the existing media layout");
        check(water().equals(EmcAmount.ONE) && lava().equals(EmcAmount.ONE),
                "Default water and lava prices must be 1000 EMC per bucket, or 1 EMC per mB");
        check(waterBuy().equals(EmcAmount.ONE) && lavaBuy().equals(EmcAmount.ONE),
                "Default fluid purchases must use the same exact one EMC per mB unit");
    }

    private static void verifyExactFractions(Path file) throws IOException {
        load(file, """
                {"fluid_values":{"water":"1/3","minecraft:lava":"0.125"}}
                """);
        check(water().multiply(3000).equals(EmcAmount.ONE),
                "A third of an EMC per bucket must remain exactly one EMC after 3000 mB");
        check(lava().multiply(8000).equals(EmcAmount.ONE),
                "Decimal bucket prices must retain their exact fractional value per mB");
        check(waterBuy().multiply(3000).equals(EmcAmount.ONE) && lavaBuy().multiply(8000).equals(EmcAmount.ONE),
                "Purchase quotes must preserve fractions and decimal units exactly");
        check(NeoForgeRecoveryPrices.fluid(new FluidStack(Fluids.FLOWING_WATER, 1)).equals(EmcAmount.ONE),
                "A registered fluid absent from the price table must inherit 1 EMC per mB");
    }

    private static void verifyDirectionalModes(Path file) throws IOException {
        load(file, """
                {"fluid_values":{"minecraft:water":"7/2","minecraft:lava":"1000"},
                 "fluid_modes":{"minecraft:water":"sell_only","minecraft:lava":"buy_only"}}
                """);
        check(water().multiply(2000).equals(EmcAmount.of(BigInteger.valueOf(7))) && lava().isZero(),
                "Only a mode that permits selling may produce recovery credit");
        check(waterBuy().isZero() && lavaBuy().equals(EmcAmount.ONE),
                "Sell-only must reject buying and buy-only must retain buying at the configured value");
        load(file, """
                {"fluid_values":{"minecraft:water":"1/3","minecraft:lava":"1000"},
                 "fluid_modes":{"minecraft:water":"inherit_native","minecraft:lava":"disabled"}}
                """);
        check(water().equals(EmcAmount.ONE) && lava().isZero(),
                "Inheritance must ignore a valid old override, while disabled remains unavailable");
        check(waterBuy().equals(EmcAmount.ONE) && lavaBuy().isZero(),
                "Inheritance and disabled modes must apply symmetrically to purchases");
        load(file, """
                {"fluid_values":{"minecraft:water":"0","minecraft:lava":"1000"}}
                """);
        check(water().isZero() && lava().equals(EmcAmount.ONE),
                "A zero explicit price without a mode must disable only that resource");
        check(waterBuy().isZero() && lavaBuy().equals(EmcAmount.ONE),
                "An inferred disabled entry must reject purchases too");
    }

    private static void verifyInvalidEntryIsolation(Path file) throws IOException {
        for (String text : List.of(
                "{\"fluid_modes\":{\"water\":\"unknown\"},\"fluid_values\":{\"water\":\"5\",\"lava\":\"2500\"}}",
                "{\"fluid_modes\":{\"water\":\"inherit_native\"},\"fluid_values\":{\"water\":[\"1\"],\"lava\":\"2500\"}}",
                "{\"fluid_modes\":{\"water\":\"inherit_native\"},\"fluid_values\":{\"water\":\"1/0\",\"lava\":\"2500\"}}",
                "{\"fluid_values\":{\"water\":true,\"lava\":\"2500\"}}")) {
            load(file, text);
            check(water().isZero() && lava().multiply(2).equals(EmcAmount.of(BigInteger.valueOf(5))),
                    "Invalid modes or values must disable their own entry even when inheritance is explicit");
            check(waterBuy().isZero() && lavaBuy().multiply(2).equals(EmcAmount.of(BigInteger.valueOf(5))),
                    "Invalid entries must not reopen through purchases, while healthy entries remain available");
            check(Files.readString(file, StandardCharsets.UTF_8).equals(text),
                    "Invalid entries must remain byte-for-byte available for administrator repair");
        }
    }

    private static void verifyInvalidFiles(Path file, Path directory) throws IOException {
        for (String text : List.of("[]", "{\"fluid_values\":", "{\"fluid_values\":[]}", "{\"format_version\":9}")) {
            Files.writeString(file, text, StandardCharsets.UTF_8);
            check(!NeoForgeRecoveryPrices.load(file) && water().isZero() && lava().isZero(),
                    "Unreadable roots and unsupported formats must suspend all fluid recovery");
            check(waterBuy().isZero() && lavaBuy().isZero(), "Rejected price roots must also suspend fluid purchases");
            check(Files.readString(file, StandardCharsets.UTF_8).equals(text),
                    "A rejected file must preserve its complete original contents");
        }
        check(!NeoForgeRecoveryPrices.load(directory) && water().isZero(),
                "An I/O read failure must also suspend fluid recovery");
        check(waterBuy().isZero(), "An I/O failure must not retain an old fluid purchase quote");
        load(file, "{}");
        check(water().equals(EmcAmount.ONE), "A corrected file must restore recovery after a failed reload");
        check(waterBuy().equals(EmcAmount.ONE), "A corrected file must restore purchases after a failed reload");
    }

    private static void verifyFluidRestrictions(Path file) throws IOException {
        load(file, "{}");
        NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of(" water "));
        check(water().isZero() && lava().equals(EmcAmount.ONE),
                "An unqualified, trimmed fluid ID must block only that fluid");
        check(waterBuy().isZero() && lavaBuy().equals(EmcAmount.ONE), "Exact fluid blacklist rules must block purchases too");
        NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:*"));
        check(water().isZero() && lava().isZero(), "A namespace rule must block every fluid in that namespace");
        check(waterBuy().isZero() && lavaBuy().isZero(), "Namespace blacklist rules must block every fluid purchase");
        check(Fluids.WATER.builtInRegistryHolder().is(FluidTags.WATER), "Vanilla water tags must be loaded");
        NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of("#minecraft:water"));
        check(water().isZero() && lava().equals(EmcAmount.ONE),
                "Fluid blacklist tags must use the live fluid registry tag membership");
        check(waterBuy().isZero() && lavaBuy().equals(EmcAmount.ONE), "Fluid blacklist tags must also govern purchases");
        NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
        NeoForgeServerConfig.FLUID_SALE_ENABLED.set(false);
        check(water().isZero() && lava().isZero(), "The global sale switch must reject otherwise priced fluids");
        NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
    }

    private static void verifyEnergyRestrictions() {
        NeoForgeServerConfig.FE_PER_EMC.set("3000");
        check(NeoForgeRecoveryPrices.energy().multiply(3000).equals(EmcAmount.ONE),
                "FE pricing must preserve the exact configured reciprocal instead of rounding to zero");
        for (String rule : List.of(" forge:fe ", "forge:*")) {
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of(rule));
            check(NeoForgeRecoveryPrices.energy().isZero(), "Exact and namespace FE restrictions must disable recovery");
        }
        NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of("minecraft:*"));
        check(!NeoForgeRecoveryPrices.energy().isZero(), "An unrelated namespace must not disable FE recovery");
        NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of());
    }

    private static void verifyNativeItemQuote() {
        ItemStack source = new ItemStack(Items.DIAMOND, 3);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("native recovery price probe"));
        ItemStack original = source.copy();
        long nativeSell = IEMCProxy.INSTANCE.getSellValue(ItemInfo.fromStack(source));
        check(nativeSell > 0 && NeoForgeRecoveryPrices.item(source).equals(EmcAmount.of(BigInteger.valueOf(nativeSell))),
                "A priced diamond must use the real ProjectE sell quote, including its native component policy");
        check(source.getCount() == original.getCount() && ItemStack.isSameItemSameComponents(source, original),
                "Quoting must not change the source item count or remove any data components");
        check(NeoForgeRecoveryPrices.item(ItemStack.EMPTY).isZero(), "An empty item stack must have no recovery quote");
    }

    private static void load(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8);
        check(NeoForgeRecoveryPrices.load(file), "The valid test price file must load");
    }

    private static EmcAmount water() { return NeoForgeRecoveryPrices.fluid(new FluidStack(Fluids.WATER, 1)); }
    private static EmcAmount lava() { return NeoForgeRecoveryPrices.fluid(new FluidStack(Fluids.LAVA, 1)); }
    private static EmcAmount waterBuy() { return NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)); }
    private static EmcAmount lavaBuy() { return NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.LAVA, 1)); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
