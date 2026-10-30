/**
 * 【文件职责】资源知识凭证的普通物品行为，只展示学习提示，不因持有或右键授予权限。
 * 【变更记录 #1】2026-10-23 13:30:35（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：目标 1.21.1 TooltipContext 提示接口。
 * - 修改：凭证继续由 ProjectE 原生知识保存与同步，无额外物品能力或使用动作。
 * - 用途：配合类别注册、条件配方和原生 EMC 映射形成可实际学习的凭证。
 */
package com.primordial.neoforge.knowledge;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class NeoForgeCertificateItem extends Item {
    public NeoForgeCertificateItem() { super(new Properties()); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.primordial.certificate"));
    }
}
