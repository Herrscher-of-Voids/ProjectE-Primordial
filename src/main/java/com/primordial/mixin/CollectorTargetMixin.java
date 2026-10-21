/**
 * 【文件职责】在 ProjectE 三代收集器的共同菜单内选择黑洞材料升级目标。
 * 【变更记录 #1】2026-10-14 05:43:37（第 1 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：凝聚器目标循环 Mixin；其它物品点击仍由 ProjectE 原菜单处理。
 * - 新增：原初黑洞位于待升级槽时，Shift 点击循环八种目标虚影。
 * - 修改：只在服务端有效菜单和当前玩家权限下改变目标，不生成物品或扣除材料。
 * - 用途：选目标交给收集器原生 EMC 升级逻辑完成实际交易。
 */
package com.primordial.mixin;

import com.primordial.equipment.BlackholeCollectorConversion;
import moze_intel.projecte.gameObjs.container.CollectorMK1Container;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=CollectorMK1Container.class,remap=false)
public abstract class CollectorTargetMixin {
    @Inject(method={"clicked","m_150399_"},at=@At("HEAD"),cancellable=true,remap=false)
    private void primordial$cycle(int slot,int button,ClickType click,Player player,CallbackInfo ci){
        CollectorMK1Container menu=(CollectorMK1Container)(Object)this;
        if(slot==0&&click==ClickType.QUICK_MOVE&&player instanceof ServerPlayer serverPlayer
                &&serverPlayer.containerMenu==menu&&menu.stillValid(serverPlayer)
                &&BlackholeCollectorConversion.cycle(menu,serverPlayer))ci.cancel();
    }
}
