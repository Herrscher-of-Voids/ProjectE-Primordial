/**
 * 【变更记录 #3】2026-09-16（流体与气体默认价格）
 * - 删除：无；保留现有价表的人工覆盖、精确分数和零值禁用格式。
 * - 新增：记录 1000 EMC/1000mB 的流体默认语义，以及未写入 gas_values 时的 1 EMC/mB 默认语义。
 * - 修改：文件仍只持久化管理员实际覆盖的条目，查询层负责应用默认值，避免为所有模组注册表预生成巨型 JSON。
 * - 用途：新增流体/气体自动获得可预测 EMC，同时保证配置文件体积和加载时间保持有界。
 */
/**
 * 【文件职责】读写精确整数 EMC 价格文件，将人工价格与旧自动估值分开。
 * 【变更记录 #1】2026-09-15 14:10:10（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：BigInteger 价格、UTF-8 原子替换保存、逐项验证和格式升级。
 * - 修改：旧 item_values 保留为兼容参考，人工 item_overrides 优先于 ProjectE；移除倍率和能源比例旧字段。
 * - 用途：读失败不覆盖原文件，保留管理员流体/气体定价；每 1000mB 流体价格不提前除以 1000。
 */
/**
 * 【变更记录 #2】2026-09-15 14:50:11（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：旧自动 item_values 参与计价和流体必须整数的要求。
 * - 新增：流体精确分数、人工整数校验、保留未识别/旧字段的读写快照。
 * - 修改：读表不写盘；显式编辑时原子保存，旧倍率/生成覆盖仅保留原文不执行。
 * - 用途：保护原有配置，水/岩浆仍为每桶 1000 EMC；新交易只使用明确价格和 ProjectE。
 */
/**
 * 【变更记录 #4】2026-09-16 01:52:17（第 4 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：item_overrides 的解析、可编辑集合和新文件输出，物品改价不再写入本文件。
 * - 新增：中文说明物品使用 ProjectE/custom_emc.json；现有旧字段仅保留原文，不参与交易。
 * - 修改：本文件格式升为 7，只保存流体/气体扩展价格，维持 UTF-8 原子写入及精确数值边界。
 * - 用途：避免配置器与 ProjectE 形成两套物品价表，同时保留管理员原始文件内容以供追溯。
 */
/**
 * 【变更记录 #5】2026-10-10 02:58:08（第 5 次备注 · 1.2.0-value-temp-cleanup）
 * - 删除：原子替换失败后遗留同目录 `.tmp` 文件的路径。
 * - 新增：价格写入或替换抛出 IOException 时删除临时文件；清理自身失败则作为 suppressed exception 附加到原始写入错误。
 * - 修改：成功写入仍优先使用 ATOMIC_MOVE，并仅在不支持原子移动时回退普通替换；失败仍向调用方报告并触发原有内存回滚。
 * - 用途：保障 ValueManager 价格文件原子持久化失败后不污染配置目录，并避免 Windows JUnit TempDir 因残留文件无法清理。
 */
/**
 * 【变更记录 #6】2026-10-11 04:26:53（第 6 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：非法交易模式被忽略后由默认模式重新启用的路径。
 * - 新增：非法流体/气体模式和值统一写入 DISABLED，保留有效人工数值但阻断交易。
 * - 修改：读取顺序先处理模式，再处理数值异常，写回时保留禁用状态。
 * - 用途：损坏配置不会意外产生免费或未授权交易。
 */
package com.primordial.value;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.primordial.PrimordialMod;
import com.primordial.service.EmcAmount;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.TreeMap;

public final class ValueTable {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_DIGITS = 4096;
    public final Map<String, EmcAmount> fluids = new TreeMap<>();
    public final Map<String, BigInteger> gases = new TreeMap<>();
    public final Map<String, PriceMode> fluidModes = new TreeMap<>();
    public final Map<String, PriceMode> gasModes = new TreeMap<>();
    private JsonObject original = new JsonObject();

    public ValueTable() {
        fluids.put("minecraft:water", EmcAmount.of(BigInteger.valueOf(1000)));
        fluids.put("minecraft:lava", EmcAmount.of(BigInteger.valueOf(1000)));
    }

    public static ValueTable read(Path path) throws IOException {
        ValueTable table = new ValueTable();
        if (!Files.exists(path)) return table;
        JsonObject root = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), JsonObject.class);
        if (root == null) throw new IOException("Empty values file");
        table.original = root.deepCopy();
        readModes(root, "fluid_modes", table.fluidModes);
        readModes(root, "gas_modes", table.gasModes);
        if (root.has("fluid_values")) {
            for (var entry : root.getAsJsonObject("fluid_values").entrySet()) {
                try {
                    if (ResourceLocation.tryParse(entry.getKey()) == null) throw new IllegalArgumentException("Invalid fluid id");
                    table.fluids.put(entry.getKey(), EmcAmount.parse(entry.getValue().getAsString()));
                } catch (RuntimeException invalid) {
                    // 非法值明确禁用，不能回退默认水价后意外开启被管理员停用的转换。
                    table.fluids.put(entry.getKey(), EmcAmount.ZERO);
                    table.fluidModes.put(entry.getKey(), PriceMode.DISABLED);
                    PrimordialMod.LOGGER.warn("[Primordial] Invalid fluid EMC entry {}", entry.getKey());
                }
            }
        }
        readMap(root, "gas_values", table.gases, table.gasModes);
        table.fluids.forEach((id, value) -> table.fluidModes.putIfAbsent(id,
                value.isZero() ? PriceMode.DISABLED : PriceMode.CUSTOM_VALUE));
        table.gases.forEach((id, value) -> table.gasModes.putIfAbsent(id,
                value.signum() == 0 ? PriceMode.DISABLED : PriceMode.CUSTOM_VALUE));
        // item_values 混有旧 PM 缩放和稀有度兜底，原文保留但不猜测其单位。
        return table;
    }

    private static void readModes(JsonObject root, String name, Map<String, PriceMode> target) {
        if (!root.has(name)) return;
        for (var entry : root.getAsJsonObject(name).entrySet()) {
            try {
                if (ResourceLocation.tryParse(entry.getKey()) == null) throw new IllegalArgumentException("Invalid registry id");
                target.put(entry.getKey(), PriceMode.parse(entry.getValue().getAsString()));
            } catch (RuntimeException invalid) {
                target.put(entry.getKey(), PriceMode.DISABLED);
                PrimordialMod.LOGGER.warn("[Primordial] Invalid price mode {} in {}; trading disabled", entry.getKey(), name);
            }
        }
    }

    private static void readMap(JsonObject root, String name, Map<String, BigInteger> target,
                                Map<String, PriceMode> modes) {
        if (!root.has(name)) return;
        for (var entry : root.getAsJsonObject(name).entrySet()) {
            try {
                if (ResourceLocation.tryParse(entry.getKey()) == null) throw new IllegalArgumentException("Invalid registry id");
                String text = entry.getValue().getAsString();
                if (text.length() > MAX_DIGITS + 8) throw new IllegalArgumentException("Price too long");
                target.put(entry.getKey(), parseInteger(text));
            } catch (RuntimeException invalid) {
                target.put(entry.getKey(), BigInteger.ZERO);
                modes.put(entry.getKey(), PriceMode.DISABLED);
                PrimordialMod.LOGGER.warn("[Primordial] Invalid integer EMC entry {} in {}", entry.getKey(), name);
            }
        }
    }

    public void write(Path path) throws IOException {
        JsonObject root = original.deepCopy();
        root.addProperty("_comment", "物品价格直接使用 ProjectE，配置器物品改价写入 config/ProjectE/custom_emc.json。此文件仅扩展流体和气体：fluid_values 为 EMC/1000mB，可用整数、小数或分数；gas_values 为整数 EMC/mB。未列出的流体/气体默认 1 EMC/mB，0 禁用转换。能源比例见 primordial-server.toml 的 energy。旧 item_overrides、item_values 和倍率字段仅保留原文，不再参与计价。");
        root.addProperty("format_version", 8);
        JsonObject fluidJson = new JsonObject();
        fluids.forEach((id, amount) -> fluidJson.addProperty(id, amount.toString()));
        root.add("fluid_values", fluidJson);
        root.add("gas_values", json(gases));
        root.add("fluid_modes", modes(fluidModes));
        root.add("gas_modes", modes(gasModes));
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            try { Files.deleteIfExists(temporary); }
            catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    /** 物品及气体允许旧 JSON 的 1000.0，但绝不接受非整数、负数或无界指数。 */
    public static BigInteger parseInteger(String text) {
        EmcAmount amount = EmcAmount.parse(text);
        if (!amount.denominator().equals(BigInteger.ONE)) throw new IllegalArgumentException("Expected integer EMC");
        return amount.numerator();
    }

    private static JsonObject json(Map<String, BigInteger> values) {
        JsonObject object = new JsonObject();
        values.forEach(object::addProperty);
        return object;
    }
    private static JsonObject modes(Map<String, PriceMode> values) {
        JsonObject object = new JsonObject();
        values.forEach((id, mode) -> object.addProperty(id, mode.name().toLowerCase(java.util.Locale.ROOT)));
        return object;
    }
}
