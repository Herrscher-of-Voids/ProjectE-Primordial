/**
 * 【文件职责】提供九种凭证共同的物品行为和学习提示。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：凭证物品类型及中英文提示，基础行为使用原版 Item。
 * - 修改：凭证通过转化桌学习，持有物品本身不写入资源授权。
 * - 用途：配合注册、原生EMC定价和ResourceKnowledge解锁相应资源。
 */
package com.primordial.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import java.util.List;

/** 凭证本身没有输出能力；玩家必须在 ProjectE 学习后才解锁相应资源。 */
public final class ResourceCertificateItem extends Item {
    public ResourceCertificateItem() { super(new Properties()); }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.primordial.certificate"));
    }
}
