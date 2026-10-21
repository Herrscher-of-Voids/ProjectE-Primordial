/**
 * 【文件职责】将随模组发布的附属价表加入 ProjectE 原生 EMC 图，不维护第二套物品交易价格。
 * 【变更记录 #1】2026-10-14 21:07:00（第 1 次备注 · 1.2.0-mainstream-mod-native-emc）
 * - 删除：无；不复制两个附属模组的机器配方代码。
 * - 新增：资源价表读取、按模组过滤、失效 ID 检查、标签展开、显式转换及人工覆盖保护。
 * - 修改：先 Expanded 补充再 Integration 1.20.1，同一物品优先采用后者；原生数据包随后正常映射。
 * - 用途：让原生转化桌、EMC 提示、配置器和回收共享价格；每次重算重读资源，无常驻旧世界缓存。
 */
package com.primordial.compat;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.JsonParser;
import com.primordial.PrimordialMod;
import java.io.StringReader;
import java.util.*;
import java.util.function.Consumer;
import moze_intel.projecte.api.mapper.EMCMapper;
import moze_intel.projecte.api.mapper.IEMCMapper;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.nss.NSSTag;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.emc.mappers.customConversions.CustomConversionMapper;
import moze_intel.projecte.emc.mappers.customConversions.json.CustomConversion;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

@EMCMapper(priority = 1, requiredMods = "primordial")
public final class NativeCompatEmcMapper implements IEMCMapper<NormalizedSimpleStack, Long> {
    @Override public String getName() { return "PrimordialCompatDefaults"; }
    @Override public String getDescription() { return "Base EMC defaults imported from ProjectE Integration and Expanded Equivalence"; }

    @Override public void addMappings(IMappingCollector<NormalizedSimpleStack, Long> collector,
            CommentedFileConfig config, ReloadableServerResources resources, RegistryAccess registries, ResourceManager manager) {
        Set<NormalizedSimpleStack> custom = new HashSet<>();
        if (CustomEMCParser.currentEntries != null) {
            for (var entry : CustomEMCParser.currentEntries.entries) expand(entry.item, custom::add);
        }
        Map<NormalizedSimpleStack, Long> prices = new LinkedHashMap<>();
        Map<NormalizedSimpleStack, CustomConversion> conversions = new LinkedHashMap<>();
        var files = new ArrayList<>(manager.listResources("compat_emc", id -> id.getNamespace().equals("primordial")
                && id.getPath().endsWith(".json")).entrySet());
        files.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        for (var entry : files) {
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                String required = json.get("required_mod").getAsString();
                if (!required.isEmpty() && !ModList.get().isLoaded(required)) continue;
                var file = CustomConversionMapper.parseJson(new StringReader(json.toString()));
                // 标签先展开成实际物品，使不同命名的共享标签遵循同一优先级。
                file.values.setValueBefore.forEach((stack, price) -> expand(stack, actual -> {
                    if (valid(actual) && !custom.contains(actual)) prices.put(actual, price);
                }));
                for (var conversion : file.values.conversion) {
                    if (conversion.count <= 0 || conversion.ingredients == null || conversion.ingredients.isEmpty()
                            || conversion.ingredients.entrySet().stream().anyMatch(i -> !valid(i.getKey()) || i.getValue() <= 0)) continue;
                    expand(conversion.output, output -> {
                        if (valid(output) && !custom.contains(output)) conversions.put(output,
                                CustomConversion.getFor(conversion.count, output, conversion.ingredients));
                    });
                }
            } catch (Exception failure) {
                PrimordialMod.LOGGER.error("Cannot load compatibility EMC resource {}", entry.getKey(), failure);
            }
        }
        prices.forEach(collector::setValueBefore);
        conversions.values().forEach(c -> collector.setValueFromConversion(c.count, c.output, c.ingredients));
        PrimordialMod.LOGGER.info("Compatibility EMC defaults: {} registered prices, {} explicit conversions", prices.size(), conversions.size());
    }

    private static void expand(NormalizedSimpleStack stack, Consumer<NormalizedSimpleStack> consumer) {
        if (stack instanceof NSSTag tag && tag.representsTag()) tag.forEachElement(consumer);
        else if (stack != null) consumer.accept(stack);
    }

    /** 缺失模组与旧版 ID 不建立图节点；缺失输入的转换也不能变成零成本输出。 */
    private static boolean valid(NormalizedSimpleStack stack) {
        if (stack == null) return false;
        if (stack instanceof NSSTag tag && tag.representsTag()) {
            boolean[] found = {false}; tag.forEachElement(s -> { if (valid(s)) found[0] = true; }); return found[0];
        }
        String encoded = stack.json();
        boolean fluid = encoded.startsWith("FLUID|");
        if (fluid) encoded = encoded.substring(6);
        else if (encoded.startsWith("ITEM|")) encoded = encoded.substring(5);
        int nbt = encoded.indexOf('{');
        ResourceLocation id = ResourceLocation.tryParse(nbt < 0 ? encoded : encoded.substring(0, nbt));
        return id != null && (fluid ? ForgeRegistries.FLUIDS.containsKey(id) : ForgeRegistries.ITEMS.containsKey(id));
    }
}
