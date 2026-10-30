/**
 * 【文件职责】只修改管理员价表的指定介质条目，原子保存后发布精确报价。
 * 【变更记录 #1】2026-10-23 17:00:16（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：五模式与精确流体桶价/整数化学品 mB 价的定点编辑。
 * - 修改：原文树保留未知字段、另一通道及其他管理员项；结构坏根或坏目标通道拒绝保存。
 * - 用途：写盘失败保留旧内存，切换禁用/继承不擦掉有效人工价，不遍历重建全表。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.primordial.service.EmcAmount;
import com.primordial.value.PriceMode;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

final class NeoForgeMediaPriceEditing {
    private NeoForgeMediaPriceEditing() {}

    static Path file() { return FMLPaths.CONFIGDIR.get().resolve("primordial_values.json"); }

    static synchronized void edit(Path file, boolean fluid, ResourceLocation id, PriceMode mode, EmcAmount amount) throws IOException {
        if (id == null || mode == null || amount != null && (!fluid && !amount.denominator().equals(BigInteger.ONE)))
            throw new IllegalArgumentException("Invalid media edit");
        JsonObject root;
        if (Files.exists(file)) {
            var parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("Expected values root object");
            root = parsed.getAsJsonObject();
        } else root = new JsonObject();
        NeoForgeRecoveryPrices.validateRoot(root);
        // 先检查目标原区段结构，不能以一个新条目覆盖损坏的整个区段。
        String prefix = fluid ? "fluid" : "gas";
        JsonObject values = section(root, prefix + "_values");
        JsonObject modes = section(root, prefix + "_modes");
        if (amount != null) replace(values, id, amount.toString());
        replace(modes, id, mode.name().toLowerCase(Locale.ROOT));
        root.add(prefix + "_values", values);
        root.add(prefix + "_modes", modes);
        var prepared = NeoForgeRecoveryPrices.prepareEdited(root, fluid);
        NeoForgePriceFiles.atomicWrite(file, new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
        NeoForgeRecoveryPrices.publishEdited(prepared);
    }

    private static JsonObject section(JsonObject root, String name) throws IOException {
        if (!root.has(name)) return new JsonObject();
        if (!root.get(name).isJsonObject()) throw new IOException("Expected values object for " + name);
        return root.getAsJsonObject(name);
    }

    /** 旧无命名空间键及规范键视为同一目标，只规范本次编辑的键。 */
    private static void replace(JsonObject section, ResourceLocation id, String value) {
        for (String key : new ArrayList<>(section.keySet()))
            if (id.equals(ResourceLocation.tryParse(key))) section.remove(key);
        section.addProperty(id.toString(), value);
    }
}
