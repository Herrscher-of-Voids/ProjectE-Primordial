/**
 * 【文件职责】在 Mekanism 存在时，将原生统一 ChemicalStack 和化学品容器转换为定价器通用样本数据。
 * 【变更记录 #1】2026-10-23 16:57:42（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。
 * - 新增：mekanism:chemical_handler 的物品能力只读采样、非空注册校验及原生名称/图标/着色读取。
 * - 修改：在 ItemStack 防御副本上查询各 tank，不调用 insert、extract 或 setter；JEI 原生 ChemicalStack 仅临时读取。
 * - 用途：全部统一化学品沿用 gas 价格通道，公共类只反射本类的通用类型签名；无 Mekanism 时本类不被载入。
 */
package com.primordial.neoforge.compat.mekanism;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;

public final class NeoForgePriceEditorChemicalSamples {
    // NeoForge 按能力 ID、处理接口和 Void 上下文返回原生同一实例，不引用 Mekanism 的 common 实现类。
    private static final ItemCapability<IChemicalHandler, Void> CHEMICAL = ItemCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    private NeoForgePriceEditorChemicalSamples() {}

    public static ResourceLocation sampleContainer(ItemStack source) {
        if (source == null || source.isEmpty()) return null;
        // 部分物品能力会在首次查询时懒初始化；副本也保护实物不受该初始化或第三方处理器影响。
        ItemStack probe = source.copy();
        IChemicalHandler handler = probe.getCapability(CHEMICAL);
        if (handler == null) return null;
        int tanks = handler.getChemicalTanks();
        for (int tank = 0; tank < tanks; tank++) {
            ResourceLocation id = chemicalId(handler.getChemicalInTank(tank));
            if (id != null) return id;
        }
        return null;
    }

    public static ResourceLocation chemicalId(Object ingredient) {
        if (!(ingredient instanceof ChemicalStack sample) || sample.isEmpty() || sample.getAmount() <= 0) return null;
        ResourceLocation id = sample.getTypeRegistryName();
        return validChemical(id) ? id : null;
    }

    public static long chemicalAmount(Object ingredient) {
        return chemicalId(ingredient) == null ? 0 : ((ChemicalStack) ingredient).getAmount();
    }

    public static boolean validChemical(ResourceLocation id) {
        return id != null && MekanismAPI.CHEMICAL_REGISTRY.containsKey(id)
                && !MekanismAPI.CHEMICAL_REGISTRY.get(id).isEmptyType();
    }

    public static Component chemicalName(ResourceLocation id) {
        return validChemical(id) ? MekanismAPI.CHEMICAL_REGISTRY.get(id).getTextComponent()
                : id == null ? Component.empty() : Component.literal(id.toString());
    }

    public static ResourceLocation chemicalTexture(ResourceLocation id) {
        return validChemical(id) ? MekanismAPI.CHEMICAL_REGISTRY.get(id).getIcon() : null;
    }

    public static int chemicalTint(ResourceLocation id) {
        return validChemical(id) ? MekanismAPI.CHEMICAL_REGISTRY.get(id).getTint() : 0xFFFFFF;
    }
}
