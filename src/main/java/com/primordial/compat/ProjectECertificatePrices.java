/**
 * 【文件职责】在 ProjectE 重算后维护凭证的原生 EMC 状态。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：关闭知识要求时移除全部凭证基础及带NBT映射。
 * - 新增：保留正常合成映射价格，缺少映射时提供8192 EMC保底价。
 * - 修改：只更新 ProjectE 原生表，不写另一份物品价表或污染custom_emc.json。
 * - 用途：凭证可在转化桌学习；fromPacket不触发重算事件，避免回调递归。
 */
package com.primordial.compat;

import com.primordial.config.PrimordialKnowledgeConfig;
import com.primordial.registry.ModItems;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.network.packets.to_client.SyncEmcPKT.EmcPKTInfo;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.world.item.Item;

/** 每次 ProjectE 重算后修正其原生价表；不写独立物品价格，也不修改用户 custom_emc.json。 */
@Mod.EventBusSubscriber(modid = "primordial")
public final class ProjectECertificatePrices {
    private ProjectECertificatePrices() { }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRemap(EMCRemapEvent event) {
        boolean enabled = PrimordialKnowledgeConfig.required();
        var certificates = ModItems.CERTIFICATES.values().stream().map(net.minecraftforge.registries.RegistryObject::get)
                .collect(java.util.stream.Collectors.toSet());
        var entries = new ArrayList<EmcPKTInfo>();
        var priced = new HashSet<Item>();
        boolean changed = false;
        for (var entry : EMCMappingHandler.createPacketData()) {
            if (certificates.contains(entry.item())) {
                if (!enabled) { changed = true; continue; }
                if (entry.emc() > 0 && entry.nbt() == null) priced.add(entry.item());
            }
            entries.add(entry);
        }
        if (enabled) for (var certificate : ModItems.CERTIFICATES.values()) {
            if (priced.add(certificate.get())) {
                // 配方映射被整合包关闭时提供保底价，低于默认合成成本，避免合成回收套利。
                entries.add(new EmcPKTInfo(certificate.get(), null, 8192L));
                changed = true;
            }
        }
        // fromPacket 只替换原生表，不触发 EMCRemapEvent，因而不会递归。
        if (changed) EMCMappingHandler.fromPacket(entries.toArray(EmcPKTInfo[]::new));
    }
}
