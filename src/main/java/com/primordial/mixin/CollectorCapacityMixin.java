/**
 * 【文件职责】按原版等级比例扩展 ProjectE 三代能量收集器的 EMC 容量和发电速率。
 * 【变更记录 #1】2026-10-14 13:36:00（第 1 次备注 · 1.2.0-blackhole-emc-ladder）
 * - 删除：无。
 * - 新增：修改收集器构造器传给 EMC 基类的储量上限，三代至少保留 1,000,000,000 EMC。
 * - 修改：容量和发电速率仍保留 ProjectE MK1/MK2/MK3 原有比例，只扩大 EMC 缓冲容量。
 * - 用途：最高黑洞材料为 955,878,144 EMC，扩大后 MK1 也能储存完整差额。
 */
/**
 * 【变更记录 #2】2026-10-14 13:44:23（第 2 次备注 · 1.2.0-blackhole-emc-ladder-generation）
 * - 删除：统一 10 亿 EMC 容量下限及保持原始发电速率的做法。
 * - 新增：容量和发电速率分别按 ProjectE 原版 MK1/MK2/MK3 比例放大 100,000 倍：容量 1/3/6 十亿 EMC，发电 400,000/1,200,000/4,000,000 EMC/刻。
 * - 修改：容量由原版 10,000/30,000/60,000 按比例计算，最低档也能支付最高阶 952,144,245 EMC 差额；速率由原版 4/12/40 EMC/刻按相同比例计算。
 * - 用途：在保留 ProjectE 三代相对成长比例的前提下，让所有等级都能支持黑洞材料购买；仅改能量存储与生成参数，不改收集器输入、升级和世界逻辑。
 */
/**
 * 【变更记录 #3】2026-10-14 13:49:25（第 3 次备注 · 1.2.0-collector-constructor-injection-fix）
 * - 删除：无。
 * - 新增：将修改父类构造实参的 Mixin 参数处理器声明为静态方法。
 * - 修改：保持容量比例乘数和发电速率重定向不变；父类构造调用前 JVM 尚未允许访问未初始化的 this，静态处理器符合 Mixin 构造器注入约束。
 * - 用途：修复指定实例启动日志中的 InvalidInjectionException，使容量修改可在 ProjectE 收集器构造阶段应用。
 */
/**
 * 【变更记录 #4】2026-10-14 14:14:39（第 4 次备注 · 1.2.0-collector-sequential-chain）
 * - 删除：发电速率放大 100,000 倍的失衡参数。
 * - 新增：更新前规范黑洞目标为当前材料的下一阶；禁止黑洞输出自动旋转回输入。
 * - 修改：容量保持 1/3/6 十亿，速率仅为原版十倍，即 40/120/400 EMC/刻；普通燃料的目标和产物旋转规则保持原版。
 * - 用途：容量足够支付高阶差额，但不破坏普通燃料升级路线；兼容旧存档中遗留的跳阶虚影。
 */
/**
 * 【变更记录 #5】2026-10-14 14:28:00（第 5 次备注 · 1.2.0-collector-sequential-chain）
 * - 删除：无，保留历史备注。
 * - 新增：纠正历史备注的生成单位：ProjectE emcGen 是满光照 EMC/秒。
 * - 修改：当前三代生成参数为 40/120/400 EMC/秒；实际逐刻生成量沿用原生光照折算。
 * - 用途：明确十倍倍率的真实含义，CollectorTooltipMixin 同步展示该数值。
 */
package com.primordial.mixin;

import moze_intel.projecte.gameObjs.block_entities.CollectorMK1BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import moze_intel.projecte.gameObjs.EnumCollectorTier;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.primordial.equipment.BlackholeCollectorConversion;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

@Mixin(value=CollectorMK1BlockEntity.class,remap=false)
public abstract class CollectorCapacityMixin {
    @ModifyArg(
            method="<init>(Lmoze_intel/projecte/gameObjs/registration/impl/BlockEntityTypeRegistryObject;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lmoze_intel/projecte/gameObjs/EnumCollectorTier;)V",
            at=@At(value="INVOKE",target="Lmoze_intel/projecte/gameObjs/block_entities/CapabilityEmcBlockEntity;<init>(Lmoze_intel/projecte/gameObjs/registration/impl/BlockEntityTypeRegistryObject;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;J)V",remap=false),
            index=3,
            remap=false)
    private static long primordial$expandStorage(long originalCapacity){
        return Math.multiplyExact(originalCapacity,100_000L);
    }

    @Redirect(
            method="<init>(Lmoze_intel/projecte/gameObjs/registration/impl/BlockEntityTypeRegistryObject;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lmoze_intel/projecte/gameObjs/EnumCollectorTier;)V",
            at=@At(value="INVOKE",target="Lmoze_intel/projecte/gameObjs/EnumCollectorTier;getGenRate()J",remap=false),
            remap=false)
    private long primordial$expandGeneration(EnumCollectorTier tier){
        return Math.multiplyExact(tier.getGenRate(),10L);
    }
    @Inject(method="checkFuelOrKlein",at=@At("HEAD"),remap=false)
    private void primordial$adjacentTarget(CallbackInfo ci){
        var aux=(IItemHandlerModifiable)((CollectorMK1BlockEntity)(Object)this).getAux();
        ItemStack input=aux.getStackInSlot(0),lock=aux.getStackInSlot(2);
        if(BlackholeCollectorConversion.isMaterial(input)){
            ItemStack next=BlackholeCollectorConversion.next(input);
            if(!ItemStack.isSameItemSameTags(next,lock))aux.setStackInSlot(2,next);
        }else if(!input.isEmpty()&&BlackholeCollectorConversion.isMaterial(lock))aux.setStackInSlot(2,ItemStack.EMPTY);
    }
    @Inject(method="rotateUpgraded",at=@At("HEAD"),cancellable=true,remap=false)
    private void primordial$leaveBlackholeOutput(CallbackInfo ci){
        if(BlackholeCollectorConversion.isMaterial(((CollectorMK1BlockEntity)(Object)this).getAux().getStackInSlot(1)))ci.cancel();
    }
}
