/**
 * 【文件职责】在 ProjectE 1.1.0 原生映射中按知识启动快照维护凭证 EMC，保留整张普通物品与组件价表。
 * 【变更记录 #1】2026-10-23 13:34:21（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：关闭知识时移除全部凭证基础与组件映射，不触碰管理员 custom_emc.json。
 * - 新增：开启时仅为缺失或非正基础价补 8192；原有正价和全部组件价保持原样。
 * - 修改：复制原生 SyncEmcPKT.data() 后整表发布；由专用 Mixin 在知识清理前修补，事件仅幂等校验。
 * - 用途：避免把 updateEmcValues 误认为增量更新，或在重映射清理后才补价导致已学凭证遗失。
 * 【变更记录 #2】2026-10-23 17:05:23（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：凭证显式设零后仍被默认 8192 价重新启用的分支。
 * - 新增：映射时识别 ProjectE 原生公共覆盖表中的基础物品零值。
 * - 修改：显式零值移除基础映射，撤销覆盖后才恢复默认补价；其余普通价和组件价保持原样。
 * - 用途：确保管理员禁用凭证与恢复默认具有不同且可持久化的行为。
 */
package com.primordial.neoforge.compat;

import java.util.HashSet;
import java.util.Set;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.registry.NeoForgeCertificates;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.config.CustomEMCParser;
import net.minecraft.core.registries.BuiltInRegistries;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.emc.EMCMappingHandler;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeCertificatePrices {
    public static final long FALLBACK_EMC = 8192L;

    private NeoForgeCertificatePrices() {}

    /** 只在原生映射生命周期调用；updateEmcValues 本身不会重新发布 EMCRemapEvent。 */
    public static boolean refresh() {
        Object2LongMap<ItemInfo> original = EMCMappingHandler.createPacketData().data();
        Object2LongMap<ItemInfo> replacement = adjusted(original);
        if (replacement.equals(original)) return false;
        EMCMappingHandler.updateEmcValues(replacement);
        return true;
    }

    /** 不修改输入或原生全局价表，供相同启动规则下的映射边界复用。 */
    public static Object2LongMap<ItemInfo> adjusted(Object2LongMap<ItemInfo> source) {
        return adjusted(source, NeoForgeKnowledgeConfig.required());
    }

    static Object2LongMap<ItemInfo> adjusted(Object2LongMap<ItemInfo> source, boolean required) {
        Object2LongMap<ItemInfo> replacement = new Object2LongOpenHashMap<>(source);
        Set<Item> certificates = new HashSet<>();
        NeoForgeCertificates.CERTIFICATES.values().forEach(holder -> certificates.add(holder.get()));
        if (required) {
            for (Item certificate : certificates) {
                ItemInfo base = ItemInfo.fromItem(certificate);
                var custom = CustomEMCParser.currentEntries;
                var key = NSSItem.createItem(BuiltInRegistries.ITEM.getKey(certificate));
                if (custom != null && custom.entries().containsKey(key) && custom.entries().getLong(key) == 0L) {
                    replacement.removeLong(base);
                    continue;
                }
                if (!replacement.containsKey(base) || replacement.getLong(base) <= 0L)
                    replacement.put(base, FALLBACK_EMC);
            }
        } else {
            replacement.object2LongEntrySet().removeIf(entry -> certificates.contains(entry.getKey().getItem().value()));
        }
        return replacement;
    }

    /** 主要修正在 Mixin 的 prune 前入口完成；此处不重发事件或递归加载映射。 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRemap(EMCRemapEvent event) {
        refresh();
    }
}
