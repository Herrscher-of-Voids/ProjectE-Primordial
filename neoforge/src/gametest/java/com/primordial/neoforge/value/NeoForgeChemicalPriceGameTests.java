/**
 * 【文件职责】不引用可选模组 API，独立验收统一化学品的旧 gas_* 价表格式、单位及拒收边界。
 * 【变更记录 #1】2026-10-22 22:16:06（第 1 次备注 · 2.0.0-neo-dev Mekanism 统一化学品回收）
 * - 删除：无。
 * - 新增：默认/整数/4096 位报价、价格方向、坏条目和坏区段隔离、实时标签与失败回调测试。
 * - 修改：共享配置及快照只在单项同步测试中临时替换，finally 恢复服务端设置并重载正式价表。
 * - 用途：确保无 Mekanism 时也能检查公共报价服务，化学品每 mB 整数价不会误用流体桶价或默默取整。
 * 【变更记录 #2】2026-10-22 22:18:04（第 2 次备注 · 2.0.0-neo-dev 化学品边界独立断言）
 * - 删除：大数量乘法两侧调用同一金额方法的重复验证。
 * - 新增：独立 BigInteger 乘积预期及原生标签 API 链接失败的拒收检查。
 * - 修改：覆盖普通回调异常与缺失 API 两种失败，不要求安装可选模组。
 * - 用途：保证最大整数报价与异常隔离有可区分的验证依据。
 * 【变更记录 #3】2026-10-23 11:42:35（第 3 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无；保留无可选 API 的整数气体价表合同。
 * - 新增：双向模式、购买开关、标签回调故障与坏条目/坏区段的购买断言；增加独立开关 GameTest。
 * - 修改：两项测试都同步保存并在 finally 恢复改动的购买/卖出配置、黑名单及正式价表快照。
 * - 用途：保证化学品购买不误除流体桶单位，也不能绕过仅卖模式、黑名单或损坏配置的停用状态。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonParser;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.service.EmcAmount;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeChemicalPriceGameTests {
    private static final ResourceLocation SAMPLE = ResourceLocation.parse("primordial:chemical_price_probe");
    private static final ResourceLocation OTHER = ResourceLocation.parse("primordial:other_chemical_probe");
    private static final ResourceLocation TAG = ResourceLocation.parse("primordial:blocked_chemicals");

    private NeoForgeChemicalPriceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_chemical_prices", setupTicks = 0, required = true)
    public static void preservesIntegerChemicalUnitsAndIsolatesInvalidConfiguration(GameTestHelper helper) throws IOException {
        check(NeoForgeServerConfig.SPEC.isLoaded(), "Server configuration must be loaded for chemical price tests");
        boolean previousGasSale = NeoForgeServerConfig.GAS_SALE_ENABLED.get();
        boolean previousFluidSale = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
        boolean previousGasPurchase = NeoForgeServerConfig.GAS_PURCHASE_ENABLED.get();
        boolean previousFluidPurchase = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
        List<? extends String> previousGasBlacklist = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());
        List<? extends String> previousFluidBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        Path directory = Files.createTempDirectory("primordial-chemical-prices-");
        Path file = directory.resolve("primordial_values.json");
        try {
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of());
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            verifyDefaults(file);
            verifyIntegerUnitsAndBounds(file);
            verifyModesAndInvalidEntries(file);
            verifyMalformedSections(file, directory);
            verifyBlacklistAndSaleSwitch(file);
        } finally {
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(previousGasSale);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(previousFluidSale);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(previousGasPurchase);
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(previousFluidPurchase);
            NeoForgeServerConfig.GAS_BLACKLIST.set(previousGasBlacklist);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(previousFluidBlacklist);
            NeoForgeRecoveryPrices.reload();
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_chemical_prices", setupTicks = 0, required = true)
    public static void keepsChemicalPurchaseAndSaleSwitchesIndependent(GameTestHelper helper) throws IOException {
        check(NeoForgeServerConfig.SPEC.isLoaded(), "Server configuration must be loaded for chemical purchase switches");
        boolean previousSale = NeoForgeServerConfig.GAS_SALE_ENABLED.get();
        boolean previousPurchase = NeoForgeServerConfig.GAS_PURCHASE_ENABLED.get();
        List<? extends String> previousBlacklist = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());
        Path directory = Files.createTempDirectory("primordial-chemical-purchase-prices-");
        Path file = directory.resolve("primordial_values.json");
        try {
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of());
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(false);
            load(file, prices("17", "custom_value"));
            check(buy(SAMPLE).isZero() && quote(SAMPLE).equals(EmcAmount.of(BigInteger.valueOf(17))),
                    "Disabling chemical purchases must leave selling at its configured integer price");
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(false);
            check(buy(SAMPLE).equals(EmcAmount.of(BigInteger.valueOf(17))) && quote(SAMPLE).isZero(),
                    "Disabling chemical sales must leave purchases available");
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(false);
            check(buy(SAMPLE).isZero() && quote(SAMPLE).isZero(), "Both chemical switches disabled must reject both directions");
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(true);
            check(buy(OTHER).equals(EmcAmount.ONE), "Unlisted chemicals must retain their one EMC per mB purchase default");
            check(NeoForgeRecoveryPrices.chemicalPurchase(null, tag -> false).isZero(), "A null chemical ID must never be purchasable");
        } finally {
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(previousSale);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(previousPurchase);
            NeoForgeServerConfig.GAS_BLACKLIST.set(previousBlacklist);
            NeoForgeRecoveryPrices.reload();
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    private static void verifyDefaults(Path file) throws IOException {
        check(NeoForgeRecoveryPrices.load(file), "A missing file must create valid format-8 defaults");
        var root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        check(root.get("format_version").getAsInt() == 8 && root.getAsJsonObject("gas_values").size() == 0
                        && root.getAsJsonObject("gas_modes").size() == 0,
                "New files must retain the legacy gas field names without pre-filling the chemical registry");
        check(root.get("_comment").getAsString().contains("统一化学品"),
                "New-file metadata must disclose the target version's wider chemical coverage");
        check(quote(SAMPLE).equals(EmcAmount.ONE) && quote(OTHER).equals(EmcAmount.ONE),
                "Unlisted chemical IDs must inherit exactly 1 EMC per mB without requiring an optional API");
        check(buy(SAMPLE).equals(EmcAmount.ONE) && buy(OTHER).equals(EmcAmount.ONE),
                "Chemical purchase defaults must use the same one EMC per mB unit");
        check(NeoForgeRecoveryPrices.chemical(null, tag -> false).isZero(), "A null chemical ID must be rejected");
    }

    private static void verifyIntegerUnitsAndBounds(Path file) throws IOException {
        load(file, "{\"fluid_values\":{\"minecraft:water\":\"1000\"},\"gas_values\":{\"" + SAMPLE + "\":1000.0}}");
        check(quote(SAMPLE).equals(EmcAmount.of(BigInteger.valueOf(1000))) && water().equals(EmcAmount.ONE),
                "1000 EMC means one mB of chemical but one bucket of fluid; chemical prices must never divide by 1000");
        check(buy(SAMPLE).equals(EmcAmount.of(BigInteger.valueOf(1000))) && waterBuy().equals(EmcAmount.ONE),
                "Purchases must keep chemical mB units distinct from fluid bucket prices");
        load(file, prices("6/3", ""));
        check(quote(SAMPLE).equals(EmcAmount.of(BigInteger.TWO)), "Mathematically integer fraction syntax must remain valid");
        check(buy(SAMPLE).equals(EmcAmount.of(BigInteger.TWO)), "Integer fraction purchase syntax must remain exact");
        load(file, prices("2e3", ""));
        check(quote(SAMPLE).equals(EmcAmount.of(BigInteger.valueOf(2000))), "Bounded integer exponent syntax must remain exact");
        check(buy(SAMPLE).equals(EmcAmount.of(BigInteger.valueOf(2000))), "Bounded integer exponent purchases must not round");
        String digits = "9".repeat(4096);
        load(file, prices(digits, ""));
        EmcAmount expected = EmcAmount.of(new BigInteger(digits));
        check(quote(SAMPLE).equals(expected)
                        && quote(SAMPLE).multiply(Long.MAX_VALUE).equals(
                                EmcAmount.of(new BigInteger(digits).multiply(BigInteger.valueOf(Long.MAX_VALUE)))),
                "The maximum 4096-digit integer and long transfer multiplication must retain their full value");
        check(buy(SAMPLE).equals(expected), "Maximum-size purchase prices must retain all 4096 digits");
        load(file, prices("9".repeat(4097), "inherit_native"));
        check(quote(SAMPLE).isZero(), "An over-limit integer must disable the entry even when inheritance is explicit");
        check(buy(SAMPLE).isZero(), "An over-limit integer must disable the purchase direction too");
    }

    private static void verifyModesAndInvalidEntries(Path file) throws IOException {
        for (String mode : List.of("custom_value", "sell_only")) {
            load(file, prices("17", mode));
            check(quote(SAMPLE).equals(EmcAmount.of(BigInteger.valueOf(17))), "Selling modes must retain the configured integer");
            check(buy(SAMPLE).equals("custom_value".equals(mode) ? EmcAmount.of(BigInteger.valueOf(17)) : EmcAmount.ZERO),
                    "Custom permits both directions while sell-only must reject purchasing");
        }
        for (String mode : List.of("buy_only", "disabled", "unknown_mode")) {
            load(file, prices("17", mode));
            check(quote(SAMPLE).isZero(), "Non-selling and invalid modes must reject chemical recovery");
            check(buy(SAMPLE).equals("buy_only".equals(mode) ? EmcAmount.of(BigInteger.valueOf(17)) : EmcAmount.ZERO),
                    "Buy-only must retain its configured price while disabled and invalid modes reject purchasing");
        }
        load(file, prices("0", "inherit_native"));
        check(quote(SAMPLE).equals(EmcAmount.ONE), "A valid old zero override must be ignored by an explicit inherit mode");
        check(buy(SAMPLE).equals(EmcAmount.ONE), "Explicit inheritance must ignore a valid old zero for purchasing too");
        load(file, prices("0", ""));
        check(quote(SAMPLE).isZero(), "A zero explicit price without a mode must infer disabled");
        check(buy(SAMPLE).isZero(), "An inferred disabled zero price must not be purchasable");
        for (String invalid : List.of("0.5", "5/2", "-1", "1/0", "NaN", "1e4096")) {
            String text = prices(invalid, "inherit_native");
            load(file, text);
            check(quote(SAMPLE).isZero() && quote(OTHER).equals(EmcAmount.ONE),
                    "An invalid or non-integer chemical price must disable only its own ID, without rounding or inherit fallback");
            check(buy(SAMPLE).isZero() && buy(OTHER).equals(EmcAmount.ONE),
                    "Invalid prices must reject purchasing without disabling other healthy chemical IDs");
            check(Files.readString(file, StandardCharsets.UTF_8).equals(text), "Rejected entries must preserve the original file");
        }
        for (String invalid : List.of("true", "[]", "null")) {
            load(file, "{\"gas_values\":{\"" + SAMPLE + "\":" + invalid + "}}");
            check(quote(SAMPLE).isZero(), "Boolean, array and null values must not be coerced to an integer price");
            check(buy(SAMPLE).isZero(), "Invalid JSON value types must not reopen through chemical purchasing");
        }
    }

    private static void verifyMalformedSections(Path file, Path directory) throws IOException {
        for (String section : List.of("\"gas_values\":[]", "\"gas_modes\":null")) {
            String text = "{\"fluid_values\":{\"minecraft:water\":\"3000\"}," + section + "}";
            Files.writeString(file, text, StandardCharsets.UTF_8);
            check(!NeoForgeRecoveryPrices.load(file) && quote(SAMPLE).isZero() && quote(OTHER).isZero()
                            && water().equals(EmcAmount.of(BigInteger.valueOf(3))),
                    "A malformed chemical section must reject all chemicals while publishing the valid fluid channel");
            check(buy(SAMPLE).isZero() && buy(OTHER).isZero() && waterBuy().equals(EmcAmount.of(BigInteger.valueOf(3))),
                    "A malformed chemical section must isolate both trade directions from the healthy fluid section");
            check(Files.readString(file, StandardCharsets.UTF_8).equals(text), "Malformed sections must remain unchanged on disk");
        }
        String text = "{\"fluid_values\":[],\"gas_values\":{\"" + SAMPLE + "\":\"7\"}}";
        Files.writeString(file, text, StandardCharsets.UTF_8);
        check(!NeoForgeRecoveryPrices.load(file) && water().isZero()
                        && quote(SAMPLE).equals(EmcAmount.of(BigInteger.valueOf(7))),
                "A malformed fluid section must also leave valid chemical prices available");
        check(waterBuy().isZero() && buy(SAMPLE).equals(EmcAmount.of(BigInteger.valueOf(7))),
                "A malformed fluid section must not suppress a valid chemical purchase quote");
        for (String root : List.of("[]", "{\"gas_values\":", "{\"format_version\":9}")) {
            Files.writeString(file, root, StandardCharsets.UTF_8);
            check(!NeoForgeRecoveryPrices.load(file) && water().isZero() && quote(SAMPLE).isZero(),
                    "An unreadable or unsupported root must disable both media channels");
            check(waterBuy().isZero() && buy(SAMPLE).isZero(), "Rejected roots must reject purchases in both media channels");
            check(Files.readString(file, StandardCharsets.UTF_8).equals(root), "A rejected root must not be overwritten");
        }
        check(!NeoForgeRecoveryPrices.load(directory) && water().isZero() && quote(SAMPLE).isZero(),
                "An I/O read failure must disable both channels without retaining usable old prices");
        check(waterBuy().isZero() && buy(SAMPLE).isZero(), "An I/O failure must not retain old purchase prices");
        load(file, "{}");
        check(water().equals(EmcAmount.ONE) && quote(SAMPLE).equals(EmcAmount.ONE),
                "Correcting the file must restore both default channels after a failed reload");
        check(waterBuy().equals(EmcAmount.ONE) && buy(SAMPLE).equals(EmcAmount.ONE),
                "A corrected file must restore both media purchase defaults");
    }

    private static void verifyBlacklistAndSaleSwitch(Path file) throws IOException {
        load(file, "{}");
        NeoForgeServerConfig.GAS_BLACKLIST.set(List.of(" " + SAMPLE + " "));
        check(quote(SAMPLE).isZero() && quote(OTHER).equals(EmcAmount.ONE), "Exact chemical rules must be trimmed and ID-specific");
        check(buy(SAMPLE).isZero() && buy(OTHER).equals(EmcAmount.ONE), "Exact chemical rules must also block purchases");
        NeoForgeServerConfig.GAS_BLACKLIST.set(List.of("primordial:*"));
        check(quote(SAMPLE).isZero() && quote(OTHER).isZero(), "Namespace chemical rules must match every contained ID");
        check(buy(SAMPLE).isZero() && buy(OTHER).isZero(), "Namespace chemical rules must also block purchases");
        NeoForgeServerConfig.GAS_BLACKLIST.set(List.of(" #" + TAG + " "));
        AtomicBoolean member = new AtomicBoolean(false);
        check(NeoForgeRecoveryPrices.chemical(SAMPLE, tag -> TAG.equals(tag) && member.get()).equals(EmcAmount.ONE),
                "A nonmember must retain its default quote");
        check(NeoForgeRecoveryPrices.chemicalPurchase(SAMPLE, tag -> TAG.equals(tag) && member.get()).equals(EmcAmount.ONE),
                "Nonmembers must retain their chemical purchase quote");
        member.set(true);
        check(NeoForgeRecoveryPrices.chemical(SAMPLE, tag -> TAG.equals(tag) && member.get()).isZero(),
                "Tag membership changes must be read live without a stale blacklist cache");
        check(NeoForgeRecoveryPrices.chemicalPurchase(SAMPLE, tag -> TAG.equals(tag) && member.get()).isZero(),
                "Live tag membership must also update chemical purchase restrictions");
        for (int i = 0; i < 2; i++) {
            check(NeoForgeRecoveryPrices.chemical(SAMPLE, tag -> { throw new IllegalStateException("tag lookup probe"); }).isZero(),
                    "A failed external tag lookup must reject the chemical instead of allowing a blacklist bypass");
            check(NeoForgeRecoveryPrices.chemicalPurchase(SAMPLE, tag -> { throw new IllegalStateException("tag purchase probe"); }).isZero(),
                    "Tag callback exceptions must also reject purchases instead of bypassing the blacklist");
        }
        check(NeoForgeRecoveryPrices.chemical(SAMPLE, tag -> { throw new NoClassDefFoundError("tag API probe"); }).isZero(),
                "An unavailable optional tag API must reject input without escaping into the caller");
        check(NeoForgeRecoveryPrices.chemicalPurchase(SAMPLE, tag -> { throw new NoClassDefFoundError("tag purchase API probe"); }).isZero(),
                "An unavailable optional tag API must reject purchases without escaping");
        check(NeoForgeRecoveryPrices.chemical(SAMPLE, null).isZero(), "A required but missing tag callback must reject input");
        check(NeoForgeRecoveryPrices.chemicalPurchase(SAMPLE, null).isZero(), "A missing required callback must also reject purchases");
        NeoForgeServerConfig.GAS_BLACKLIST.set(List.of());
        check(NeoForgeRecoveryPrices.chemical(SAMPLE, null).equals(EmcAmount.ONE), "No tag callback is needed without tag rules");
        check(NeoForgeRecoveryPrices.chemicalPurchase(SAMPLE, null).equals(EmcAmount.ONE), "Purchases need no callback without tag rules");
        NeoForgeServerConfig.GAS_SALE_ENABLED.set(false);
        check(quote(SAMPLE).isZero() && water().equals(EmcAmount.ONE), "The chemical sale switch must not disable fluid recovery");
        check(buy(SAMPLE).equals(EmcAmount.ONE) && waterBuy().equals(EmcAmount.ONE),
                "Disabling chemical sales must preserve chemical and fluid purchases");
        NeoForgeServerConfig.GAS_SALE_ENABLED.set(true);
    }

    private static String prices(String value, String mode) {
        return "{\"gas_values\":{\"" + SAMPLE + "\":\"" + value + "\"}"
                + (mode.isEmpty() ? "" : ",\"gas_modes\":{\"" + SAMPLE + "\":\"" + mode + "\"}") + "}";
    }

    private static void load(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardCharsets.UTF_8);
        check(NeoForgeRecoveryPrices.load(file), "The structurally valid test price file must load");
    }

    private static EmcAmount quote(ResourceLocation id) { return NeoForgeRecoveryPrices.chemical(id, tag -> false); }
    private static EmcAmount buy(ResourceLocation id) { return NeoForgeRecoveryPrices.chemicalPurchase(id, tag -> false); }
    private static EmcAmount water() { return NeoForgeRecoveryPrices.fluid(new FluidStack(Fluids.WATER, 1)); }
    private static EmcAmount waterBuy() { return NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
