/**
 * 【文件职责】让 ProjectE 收集器物品的原生统计提示匹配运行时容量和发电参数。
 * 【变更记录 #1】2026-10-14 14:28:00（第 1 次备注 · 1.2.0-collector-sequential-chain）
 * - 删除：无；沿用 ProjectE 原有翻译、颜色和提示开关。
 * - 新增：提示取值处按容量十万倍、生成速率十倍计算。
 * - 修改：MK1/MK2/MK3 最大容量为 1/3/6 十亿 EMC，满光照生成 40/120/400 EMC/秒。
 * - 用途：物品描述与机器相符；getGenRate 的原生单位是每秒而非每刻。
 */
package com.primordial.mixin;

import moze_intel.projecte.gameObjs.EnumCollectorTier;
import moze_intel.projecte.gameObjs.items.blocks.CollectorItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value=CollectorItem.class,remap=false)
public abstract class CollectorTooltipMixin {
    @Redirect(method={"appendHoverText","m_7373_"},at=@At(value="INVOKE",target="Lmoze_intel/projecte/gameObjs/EnumCollectorTier;getGenRate()J"),remap=false)
    private long primordial$generation(EnumCollectorTier tier){return Math.multiplyExact(tier.getGenRate(),10L);}
    @Redirect(method={"appendHoverText","m_7373_"},at=@At(value="INVOKE",target="Lmoze_intel/projecte/gameObjs/EnumCollectorTier;getStorage()J"),remap=false)
    private long primordial$capacity(EnumCollectorTier tier){return Math.multiplyExact(tier.getStorage(),100_000L);}
}
