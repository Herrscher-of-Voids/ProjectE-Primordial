/**
 * 【变更记录 #1】2026-10-14 02:26:13（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：八种材料在原生能量凝聚器按EMC转化；首次以原初黑洞设置目标，再Shift点击选产物，尊重ProjectE目标设置事件。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 04:23:00（第 2 次备注 · 1.2.0-condenser-jei-display）
 * - 删除：九种黑洞材料悬停时的凝聚器操作说明。
 * - 新增：客户端悬停时仅保留材料名称，去除额外说明与 EMC 行；实际配方展示交由 JEI 的凝聚器分类。
 * - 修改：真实目标循环与 ProjectE 原生计价、保护事件仍保持原样。
 * - 用途：材料 tooltip 保持简洁，玩家从配方页查看八种明确的凝聚器转化。
 */
/**
 * 【变更记录 #3】2026-10-14 05:43:37（第 3 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：凝聚器目标循环与专属凝聚器转化入口。
 * - 新增：收集器待升级槽 Shift 点击循环八种目标虚影，保留材料名称以外 tooltip 清理。
 * - 修改：通过 ProjectE 原生收集器升级槽按目标和原料 EMC 差额转化，三代收集器共用。
 * - 用途：落实用户对“能量收集器”的更正，避免 JEI 与实际机器路线不一致。
 */
/**
 * 【变更记录 #4】2026-10-14 13:36:00（第 4 次备注 · 1.2.0-blackhole-emc-ladder）
 * - 删除：无。
 * - 新增：把克尔–纽曼之前的目标顺序调整为史瓦西、赖斯纳–诺德斯特洛姆、克尔，与 EMC 阶梯一致。
 * - 修改：目标循环顺序固定为恒星级、中等质量、超大质量、极大质量、史瓦西、赖斯纳–诺德斯特洛姆、克尔、克尔–纽曼。
 * - 用途：让收集器 Shift 点击目标顺序与逐阶翻倍的 ProjectE EMC 定价及 JEI 配方保持一致。
 */
/**
 * 【变更记录 #5】2026-10-14 14:14:39（第 5 次备注 · 1.2.0-collector-sequential-chain）
 * - 删除：原初黑洞 Shift 点击任选八阶的捷径。
 * - 新增：完整九阶列表、当前材料的唯一下一阶与前驱查找；待升级槽目标始终由输入决定。
 * - 修改：黑洞配方、燃料映射与机器交易共用相邻阶关系，满级返回空目标；额外材料 tooltip 清理保持原样。
 * - 用途：防止跳阶、倒阶和旧目标虚影绕过收集器的材料链。
 */
/**
 * 【变更记录 #6】2026-10-14 20:50:00（第 6 次备注 · 1.2.0-blackhole-native-emc-tooltip）
 * - 删除：LOWEST 优先级下清空名称后所有提示行的事件处理及其注册，避免误删 ProjectE 原生 EMC。
 * - 新增：无额外提示；材料继续使用普通 Item，由 ProjectE 显示原生单价和堆叠价值。
 * - 修改：只解除提示过滤，不改变九阶 EMC、转化桌交易或收集器升级关系。
 * - 用途：保留玩家要求的原生价格说明，同时不恢复旧的材料操作文案。
 */
package com.primordial.equipment;
import com.primordial.registry.ModItems;
import java.util.List;
import moze_intel.projecte.gameObjs.container.CollectorMK1Container;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class BlackholeCollectorConversion {
    public static final List<String> VARIANTS=List.of("stellar","intermediate","supermassive","ultramassive","schwarzschild","reissner_nordstrom","kerr","kerr_newman");
    public static final List<String> TIERS=java.util.stream.Stream.concat(java.util.stream.Stream.of("primordial"),VARIANTS.stream()).toList();
    private BlackholeCollectorConversion() { }
    public static boolean isMaterial(ItemStack item){return !item.isEmpty()&&ModItems.BLACKHOLE_MATERIALS.values().stream().anyMatch(entry->entry.isPresent()&&item.is(entry.get()));}
    public static boolean isBase(ItemStack stack){return !stack.isEmpty()&&stack.is(ModItems.BLACKHOLE_MATERIALS.get("primordial").get());}
    public static int tier(ItemStack stack){
        for(int i=0;i<TIERS.size();i++)if(stack.is(ModItems.BLACKHOLE_MATERIALS.get(TIERS.get(i)).get()))return i;
        return -1;
    }
    public static ItemStack next(ItemStack stack){
        int i=tier(stack);return i>=0&&i+1<TIERS.size()?material(TIERS.get(i+1)):ItemStack.EMPTY;
    }
    public static ItemStack material(String name){return new ItemStack(ModItems.BLACKHOLE_MATERIALS.get(name).get());}
    public static boolean cycle(CollectorMK1Container menu,ServerPlayer player){
        if(!player.server.isSameThread()||menu.collector.getLevel()!=player.level()||!isMaterial(menu.collector.getAux().getStackInSlot(0)))return false;
        int lockSlot=menu.collector.getInput().getSlots()+2;
        menu.getSlot(lockSlot).set(next(menu.collector.getAux().getStackInSlot(0)));
        menu.broadcastChanges();return true;
    }
}
