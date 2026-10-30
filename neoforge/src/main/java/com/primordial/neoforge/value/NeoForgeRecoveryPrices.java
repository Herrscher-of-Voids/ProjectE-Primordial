/**
 * 【文件职责】提供黑洞物品原生售价、流体每 mB 精确售价和 FE 售价；查询只读当前服务端快照。
 * 【变更记录 #1】2026-10-22 21:36:12（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：ProjectE getSellValue、格式 8 流体价表读取/初建、方向状态与配置黑名单校验。
 * - 修改：模式先于数值解析；非法条目禁用，文件损坏则暂停所有流体回收并保留原文。
 * - 用途：报价不模拟接收、不学习或结算；不剥离物品组件，不猜测第三方容器内容价格。
 * 【变更记录 #2】2026-10-22 21:39:21（第 2 次备注 · 2.0.0-neo-dev 回收价格配置边界）
 * - 删除：数组等 JSON 类型被隐式转为价格字符串的路径。
 * - 新增：省略 minecraft 命名空间的统一解析及数值/模式类型校验。
 * - 修改：黑名单与价表均以规范注册 ID 比较，显式非法值不会因 inherit 模式恢复交易。
 * - 用途：保持有效手工配置可用，损坏条目安全拒收。
 * 【变更记录 #3】2026-10-22 22:14:24（第 3 次备注 · 2.0.0-neo-dev Mekanism 统一化学品回收）
 * - 删除：价表快照仅能服务流体的限制，保留全部旧字段、物品售价和 FE 规则。
 * - 新增：无可选 API 类型的化学品报价、旧 gas_* 整数 EMC/mB 解析及标签回调失败拒收。
 * - 修改：流体/化学品原子发布各自可读状态，介质区段损坏仅停用对应类别，根文件损坏停用两者。
 * - 用途：新端口覆盖目标版全部统一化学品，明确超出 Forge 原有仅气体范围；默认 1 EMC/mB，不套用流体桶价。
 * 【变更记录 #4】2026-10-23 11:37:39（第 4 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无；保留物品原生售价及损坏价表停用规则。
 * - 新增：ProjectE 原生物品购买价，以及流体/化学品每 mB 的只读购买报价。
 * - 修改：买卖查询共用同一不可变介质快照，分别检查购买/卖出开关与模式；黑名单同时阻断两方向。
 * - 用途：统一交易服务可按实际交付数计费；本层不检查知识、不更改账户，也不把卖价误作购买价。
 * 【变更记录 #5】2026-10-23 11:45:51（第 5 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无。
 * - 新增：AtomicLong 价表发布序号及只读 revision() 查询。
 * - 修改：实际载入无论成功还是失败，均在发布新快照后推进一次序号；普通报价查询不推进。
 * - 用途：统一交易服务可发现知识/标签回调期间发生的价表重载，避免将不同发布代的授权与报价混用。
 * 【变更记录 #6】2026-10-23 16:59:12（第 6 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无；保留全部旧价表读取、默认单位和损坏隔离规则。
 * - 新增：管理员原价/模式只读状态、编辑候选快照预检和保存后一次发布入口。
 * - 修改：文件结构校验共用；原生物品重映射也可推进同一报价序号。
 * - 用途：编辑失败不改正在使用的价格，禁用和继承仍能显示保留的人工原价。
 * 【变更记录 #7】2026-10-23 17:09:56（第 7 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：将保留的人工原价混作编辑器当前有效价格的回显。
 * - 新增：有效价与 retainedPrice 独立读取，继承回显默认值、禁用回显零。
 * - 修改：五模式切换继续保留原值，审计单独记录保留价与模式。
 * - 用途：显示符合正在应用的模式，恢复定制时仍使用原管理员金额。
 * 【变更记录 #8】2026-10-23 17:31:58（第 8 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：管理员重载的两通道只读预检，失败不覆盖文件或提前发布部分新价。
 * - 修改：普通生命周期重载仍沿用损坏停用规则；命令重载需介质和原生物品都准备成功。
 * - 用途：reload_values 可先发现坏根，再决定是否执行原生知识清理和价格同步。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.service.EmcAmount;
import com.primordial.value.PriceMode;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeRecoveryPrices {
    private static final EmcAmount DEFAULT_BUCKET = EmcAmount.of(BigInteger.valueOf(1000));
    private static final ResourceLocation FE = ResourceLocation.fromNamespaceAndPath("forge", "fe");
    private static final AtomicBoolean ITEM_WARNED = new AtomicBoolean();
    private static final AtomicBoolean ITEM_PURCHASE_WARNED = new AtomicBoolean();
    private static final AtomicBoolean CHEMICAL_TAG_WARNED = new AtomicBoolean();
    private static final AtomicLong REVISION = new AtomicLong();
    private static final Channel UNREADABLE = new Channel(Map.of(), Map.of(), false);
    private static volatile Snapshot snapshot = new Snapshot(UNREADABLE, UNREADABLE);
    private static volatile boolean loaded;

    private record Channel(Map<String, EmcAmount> values, Map<String, PriceMode> modes, boolean readable) {}
    private record Snapshot(Channel fluids, Channel chemicals) {}
    record MediaState(boolean readable, EmcAmount price, PriceMode mode, EmcAmount retainedPrice) {}
    record Prepared(Snapshot value) {}

    private NeoForgeRecoveryPrices() {}

    /** 仅观察实际价表发布，不触发载入，也不把普通报价视为重载。 */
    public static long revision() { return REVISION.get(); }

    public static void invalidatePrices() { REVISION.incrementAndGet(); }

    static synchronized MediaState mediaState(boolean fluid, ResourceLocation id) {
        if (!loaded) reload();
        Channel channel = fluid ? snapshot.fluids : snapshot.chemicals;
        EmcAmount fallback = fluid ? DEFAULT_BUCKET : EmcAmount.ONE;
        EmcAmount retained = channel.values.getOrDefault(id.toString(), fallback);
        PriceMode mode = channel.modes.getOrDefault(id.toString(), PriceMode.INHERIT_NATIVE);
        EmcAmount effective = !channel.readable || mode == PriceMode.DISABLED ? EmcAmount.ZERO
                : mode == PriceMode.INHERIT_NATIVE ? fallback : retained;
        return new MediaState(channel.readable, effective, mode, retained);
    }

    /** 在写盘之前解析候选，另一通道损坏仍保留原文并保持其停用状态。 */
    static Prepared prepareEdited(JsonObject root, boolean fluid) throws IOException {
        validateRoot(root);
        Snapshot next = new Snapshot(readChannel(root, "fluid", false), readChannel(root, "gas", true));
        if (!(fluid ? next.fluids.readable : next.chemicals.readable))
            throw new IOException("Selected price section is unreadable");
        return new Prepared(next);
    }

    static Prepared prepareReload() throws IOException {
        Path file = FMLPaths.CONFIGDIR.get().resolve("primordial_values.json");
        JsonObject root = new JsonObject();
        if (Files.exists(file)) {
            var parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("Expected values root object");
            root = parsed.getAsJsonObject();
        }
        validateRoot(root);
        Snapshot next = new Snapshot(readChannel(root, "fluid", false), readChannel(root, "gas", true));
        if (!next.fluids.readable || !next.chemicals.readable) throw new IOException("Unreadable values section");
        return new Prepared(next);
    }

    static synchronized void publishEdited(Prepared prepared) {
        snapshot = prepared.value;
        loaded = true;
        REVISION.incrementAndGet();
    }

    /** 必须读取 ProjectE 售价以遵守原生损耗，不以购买价或容器内容推算兜底。 */
    public static EmcAmount item(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return EmcAmount.ZERO;
        try {
            return EmcAmount.of(BigInteger.valueOf(IEMCProxy.INSTANCE.getSellValue(ItemInfo.fromStack(stack))));
        } catch (RuntimeException | LinkageError failure) {
            if (ITEM_WARNED.compareAndSet(false, true)) LogUtils.getLogger().warn(
                    "[Primordial] ProjectE recovery price unavailable; item input rejected", failure);
            return EmcAmount.ZERO;
        }
    }

    /** 保留完整组件交给 ProjectE 查询购买价，不以可能带损耗的售价代替。 */
    public static EmcAmount itemPurchase(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return EmcAmount.ZERO;
        try {
            return EmcAmount.of(BigInteger.valueOf(IEMCProxy.INSTANCE.getValue(ItemInfo.fromStack(stack))));
        } catch (RuntimeException | LinkageError failure) {
            if (ITEM_PURCHASE_WARNED.compareAndSet(false, true)) LogUtils.getLogger().warn(
                    "[Primordial] ProjectE purchase price unavailable; item output rejected", failure);
            return EmcAmount.ZERO;
        }
    }

    public static EmcAmount fluid(FluidStack stack) {
        return fluid(stack, false);
    }

    public static EmcAmount fluidPurchase(FluidStack stack) {
        return fluid(stack, true);
    }

    private static EmcAmount fluid(FluidStack stack, boolean purchase) {
        if (stack == null || stack.isEmpty() || !(purchase
                ? NeoForgeServerConfig.fluidPurchaseEnabled() : NeoForgeServerConfig.fluidSaleEnabled()))
            return EmcAmount.ZERO;
        if (!loaded) reload();
        Channel current = snapshot.fluids;
        if (!current.readable) return EmcAmount.ZERO;
        Fluid fluid = stack.getFluid();
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        if (id == null || blocked(id, tag -> fluid.builtInRegistryHolder().is(TagKey.create(Registries.FLUID, tag)),
                NeoForgeServerConfig.fluidBlacklist())) return EmcAmount.ZERO;
        PriceMode mode = current.modes.getOrDefault(id.toString(), PriceMode.INHERIT_NATIVE);
        if (!(purchase ? mode.allowsBuy() : mode.allowsSell())) return EmcAmount.ZERO;
        EmcAmount bucket = mode == PriceMode.INHERIT_NATIVE ? DEFAULT_BUCKET
                : current.values.getOrDefault(id.toString(), DEFAULT_BUCKET);
        return bucket.divide(1000);
    }

    /** gas_* 沿用旧文件名与整数 EMC/mB 单位；目标端口明确扩展到全部统一化学品。 */
    public static EmcAmount chemical(ResourceLocation id, Predicate<ResourceLocation> inTag) {
        return chemical(id, inTag, false);
    }

    public static EmcAmount chemicalPurchase(ResourceLocation id, Predicate<ResourceLocation> inTag) {
        return chemical(id, inTag, true);
    }

    private static EmcAmount chemical(ResourceLocation id, Predicate<ResourceLocation> inTag, boolean purchase) {
        if (id == null || !(purchase
                ? NeoForgeServerConfig.gasPurchaseEnabled() : NeoForgeServerConfig.gasSaleEnabled()))
            return EmcAmount.ZERO;
        if (!loaded) reload();
        Channel current = snapshot.chemicals;
        if (!current.readable) return EmcAmount.ZERO;
        try {
            if (blocked(id, inTag, NeoForgeServerConfig.gasBlacklist())) return EmcAmount.ZERO;
        } catch (RuntimeException | LinkageError failure) {
            if (CHEMICAL_TAG_WARNED.compareAndSet(false, true)) LogUtils.getLogger().warn(
                    "[Primordial] Chemical blacklist tag lookup unavailable; chemical trade rejected", failure);
            return EmcAmount.ZERO;
        }
        PriceMode mode = current.modes.getOrDefault(id.toString(), PriceMode.INHERIT_NATIVE);
        if (!(purchase ? mode.allowsBuy() : mode.allowsSell())) return EmcAmount.ZERO;
        return mode == PriceMode.INHERIT_NATIVE ? EmcAmount.ONE
                : current.values.getOrDefault(id.toString(), EmcAmount.ONE);
    }

    public static EmcAmount energy() {
        if (blocked(FE, null, NeoForgeServerConfig.resourceBlacklist())) return EmcAmount.ZERO;
        BigInteger ratio = NeoForgeServerConfig.fePerEmc();
        return ratio == null || ratio.signum() <= 0 ? EmcAmount.ZERO : new EmcAmount(BigInteger.ONE, ratio);
    }

    /** 配置/资源重载由服务端生命周期调用；读失败停止回收，绝不覆盖已有管理员文件。 */
    public static synchronized boolean reload() {
        return load(FMLPaths.CONFIGDIR.get().resolve("primordial_values.json"));
    }

    static synchronized boolean load(Path file) {
        try {
            if (!Files.exists(file)) createDefaults(file);
            var parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("Expected values object");
            JsonObject root = parsed.getAsJsonObject();
            validateRoot(root);
            Channel fluids = readChannel(root, "fluid", false);
            Channel chemicals = readChannel(root, "gas", true);
            snapshot = new Snapshot(fluids, chemicals);
            loaded = true;
            REVISION.incrementAndGet();
            return fluids.readable && chemicals.readable;
        } catch (IOException | RuntimeException failure) {
            snapshot = new Snapshot(UNREADABLE, UNREADABLE);
            loaded = true;
            REVISION.incrementAndGet();
            LogUtils.getLogger().error("[Primordial] Cannot read recovery values; media input disabled and original file preserved", failure);
            return false;
        }
    }

    static void validateRoot(JsonObject root) throws IOException {
        if (root == null) throw new IOException("Expected values object");
        if (root.has("format_version")) {
            if (!root.get("format_version").isJsonPrimitive()) throw new IOException("Expected values version");
            String version = root.get("format_version").getAsString();
            if (!version.matches("[0-9]+") || new BigInteger(version).compareTo(BigInteger.valueOf(8)) > 0)
                throw new IOException("Unsupported values format");
        }
    }

    /** 模式先读，非法条目局部停用；区段结构损坏不影响另一介质的完整快照。 */
    private static Channel readChannel(JsonObject root, String prefix, boolean integer) {
        try {
            Map<String, EmcAmount> values = new ConcurrentHashMap<>();
            Map<String, PriceMode> modes = new ConcurrentHashMap<>();
            if (!integer) {
                values.put("minecraft:water", DEFAULT_BUCKET);
                values.put("minecraft:lava", DEFAULT_BUCKET);
            }
            for (var entry : object(root, prefix + "_modes").entrySet()) {
                try {
                    requireId(entry.getKey());
                    if (!entry.getValue().isJsonPrimitive()
                            || !entry.getValue().getAsJsonPrimitive().isString())
                        throw new IllegalArgumentException("Expected price mode string");
                    modes.put(canonicalId(entry.getKey()), PriceMode.parse(entry.getValue().getAsString()));
                } catch (RuntimeException invalid) {
                    modes.put(canonicalId(entry.getKey()), PriceMode.DISABLED);
                    LogUtils.getLogger().warn("[Primordial] Invalid {} price mode {}; recovery disabled", prefix, entry.getKey());
                }
            }
            for (var entry : object(root, prefix + "_values").entrySet()) {
                try {
                    requireId(entry.getKey());
                    if (!entry.getValue().isJsonPrimitive()
                            || entry.getValue().getAsJsonPrimitive().isBoolean())
                        throw new IllegalArgumentException("Expected numeric price");
                    String text = entry.getValue().getAsString();
                    if (integer && text.length() > 4104) throw new IllegalArgumentException("Integer price text too long");
                    EmcAmount amount = EmcAmount.parse(text);
                    if (integer && !amount.denominator().equals(BigInteger.ONE))
                        throw new IllegalArgumentException("Expected integer EMC per mB");
                    values.put(canonicalId(entry.getKey()), amount);
                } catch (RuntimeException invalid) {
                    values.put(canonicalId(entry.getKey()), EmcAmount.ZERO);
                    modes.put(canonicalId(entry.getKey()), PriceMode.DISABLED);
                    LogUtils.getLogger().warn("[Primordial] Invalid {} recovery value {}; recovery disabled", prefix, entry.getKey());
                }
            }
            values.forEach((id, value) -> modes.putIfAbsent(id,
                    value.isZero() ? PriceMode.DISABLED : PriceMode.CUSTOM_VALUE));
            return new Channel(Map.copyOf(values), Map.copyOf(modes), true);
        } catch (IOException | RuntimeException failure) {
            LogUtils.getLogger().error("[Primordial] Cannot read {} recovery section; its input disabled and original file preserved", prefix, failure);
            return UNREADABLE;
        }
    }

    private static JsonObject object(JsonObject root, String key) throws IOException {
        if (!root.has(key)) return new JsonObject();
        if (!root.get(key).isJsonObject()) throw new IOException("Expected object for " + key);
        return root.getAsJsonObject(key);
    }

    private static void requireId(String id) {
        if (ResourceLocation.tryParse(id) == null) throw new IllegalArgumentException("Invalid media registry ID");
    }

    private static String canonicalId(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        return parsed == null ? id : parsed.toString();
    }

    private static boolean blocked(ResourceLocation id, Predicate<ResourceLocation> inTag, List<? extends String> rules) {
        for (String raw : rules) {
            String rule = raw.trim();
            if (rule.equals(id.getNamespace() + ":*")) return true;
            if (!rule.startsWith("#") && id.equals(ResourceLocation.tryParse(rule))) return true;
            if (rule.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(rule.substring(1));
                if (tag != null) {
                    if (inTag == null) throw new IllegalStateException("Tag membership callback unavailable");
                    if (inTag.test(tag)) return true;
                }
            }
        }
        return false;
    }

    private static void createDefaults(Path file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "物品买卖分别使用 ProjectE 原生购买价与售价。fluid_values 为 EMC/1000mB，可用整数、小数或分数；gas_values 沿用旧字段名，为整数 EMC/mB，不除以 1000。目标版 gas_* 覆盖 Mekanism 全部统一化学品，范围比 Forge 原有气体更广。未列介质默认 1 EMC/mB，0 默认禁用；fluid_modes/gas_modes 支持 inherit_native/custom_value/disabled/sell_only/buy_only，继承忽略有效旧价。购买/卖出开关、黑名单与能源比例见 primordial-server.toml。");
        root.addProperty("format_version", 8);
        JsonObject values = new JsonObject();
        values.addProperty("minecraft:water", "1000");
        values.addProperty("minecraft:lava", "1000");
        root.add("fluid_values", values);
        JsonObject modes = new JsonObject();
        modes.addProperty("minecraft:water", "custom_value");
        modes.addProperty("minecraft:lava", "custom_value");
        root.add("fluid_modes", modes);
        root.add("gas_values", new JsonObject());
        root.add("gas_modes", new JsonObject());
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
}
