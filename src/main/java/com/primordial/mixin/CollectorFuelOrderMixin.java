/**
 * 【文件职责】确保 ProjectE 收集器燃料图中原初黑洞始终先于末端黑洞目标。
 * 【变更记录 #1】2026-10-14 05:54:38（第 1 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：无。
 * - 新增：燃料表重载完成后，仅在顺序颠倒时将原初黑洞移到克尔-纽曼之前。
 * - 修改：不改变其它燃料相对顺序和所有材料的 EMC 价格。
 * - 用途：避免同价材料标签迭代顺序让原初黑洞成为不可投入的末级燃料。
 */
/**
 * 【变更记录 #2】2026-10-14 05:57:15（第 2 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：只在原初黑洞排在末端目标之后才重排的条件。
 * - 新增：两者在燃料表中都存在时，保证原初黑洞紧邻克尔-纽曼之前。
 * - 修改：其它燃料的相对顺序保持不变，支持整合包添加更多同价收集器燃料。
 * - 用途：确保 ProjectE 原生第八条升级配方稳定为原初黑洞到克尔-纽曼。
 */
/**
 * 【变更记录 #3】2026-10-14 14:14:39（第 3 次备注 · 1.2.0-collector-sequential-chain）
 * - 删除：只有原初和末阶两种燃料、原初紧邻末阶的旧链。
 * - 新增：九种黑洞在燃料表末尾按阶排序；黑洞升级返回唯一下一阶；普通燃料最后一级不会跨入黑洞链。
 * - 修改：loadMap 与服务端同步的 setFuelMap 均规范排序，isStackMaxFuel 和 getFuelUpgrade 同时隔离两条链。
 * - 用途：保留原版煤炭到高阶燃料路线，并让收集器和 ProjectE 原生 JEI 映射看到相邻黑洞配方。
 */
/**
 * 【变更记录 #4】2026-10-14 14:28:00（第 4 次备注 · 1.2.0-collector-sequential-chain）
 * - 删除：直接修改调用者燃料列表的实现。
 * - 新增：创建可修改副本后排列九种材料。
 * - 修改：setFuelMap 的参数替换为独立列表，兼容不可变同步数据。
 * - 用途：避免重载和客户端同步因只读列表崩溃。
 */
package com.primordial.mixin;

import com.primordial.registry.ModItems;
import java.util.ArrayList;
import moze_intel.projecte.emc.FuelMapper;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.primordial.equipment.BlackholeCollectorConversion;
import net.minecraft.world.item.ItemStack;

@Mixin(value=FuelMapper.class,remap=false)
public abstract class CollectorFuelOrderMixin {
    @Inject(method="loadMap",at=@At("TAIL"),remap=false)
    private static void primordial$keepBaseUpgradeable(CallbackInfo ci){
        var fuels=new ArrayList<Item>(FuelMapper.getFuelMap());
        FuelMapper.setFuelMap(fuels);
    }
    @ModifyVariable(method="setFuelMap",at=@At("HEAD"),argsOnly=true,ordinal=0,remap=false)
    private static java.util.List<Item> primordial$orderTiers(java.util.List<Item> input){
        var fuels=new ArrayList<Item>(input);
        fuels.removeIf(item->BlackholeCollectorConversion.isMaterial(new ItemStack(item)));
        for(String tier:BlackholeCollectorConversion.TIERS)fuels.add(ModItems.BLACKHOLE_MATERIALS.get(tier).get());
        return fuels;
    }
    @Inject(method="getFuelUpgrade",at=@At("HEAD"),cancellable=true,remap=false)
    private static void primordial$separateUpgrade(ItemStack stack,CallbackInfoReturnable<ItemStack> cir){
        if(BlackholeCollectorConversion.isMaterial(stack))cir.setReturnValue(BlackholeCollectorConversion.next(stack));
        else if(primordial$lastOrdinary(stack))cir.setReturnValue(ItemStack.EMPTY);
    }
    @Inject(method="isStackMaxFuel",at=@At("HEAD"),cancellable=true,remap=false)
    private static void primordial$separateTerminal(ItemStack stack,CallbackInfoReturnable<Boolean> cir){
        if(BlackholeCollectorConversion.isMaterial(stack))cir.setReturnValue(BlackholeCollectorConversion.next(stack).isEmpty());
        else if(primordial$lastOrdinary(stack))cir.setReturnValue(true);
    }
    private static boolean primordial$lastOrdinary(ItemStack stack){
        var fuels=FuelMapper.getFuelMap();int i=fuels.indexOf(stack.getItem());
        return i>=0&&i+1<fuels.size()&&BlackholeCollectorConversion.isMaterial(new ItemStack(fuels.get(i+1)));
    }
}
