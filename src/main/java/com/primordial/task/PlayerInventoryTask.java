/**
 * 【文件职责】每秒执行在线玩家背包规则，只在指定槽位补充物品并从 EMC 余额扣费。
 * 【变更记录 #1】2026-09-14 12:50:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：严格槽位匹配、空间模拟、EMC 原子扣费和失败回退。
 * - 修改：补充失败不会尝试其它槽位，在线玩家之外不扫描实体，控制每秒遍历规模。
 * - 用途：响应玩家背包配置页的“维持数量”规则，避免自动化物品乱塞。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
 */
/**
 * 【变更记录 #8】2026-09-14 16:45:00（第 8 次备注 · 1.0.0-stage5-feedback2-hotfix）
 * - 删除：运行时把旧 rate 字段当作固定模式速率的路径；旧字段仅保留给已有存档读取。
 * - 新增：固定模式使用 amount 数量字段，补充模式根据目标容量或缺口计算，不受固定数量影响。
 * - 修改：EnergyConfig、玩家背包规则、配置器界面和任务执行器统一采用“数量 + tick 间隔”语义，并继续限制非法值。
 * - 用途：避免高容量设备因为旧上限或错误速率解释而产生过量循环、配置丢失或跨槽位写入。
 */
/**
 * 变更记录 #3（2026-09-14）：玩家背包配置的物品补充遵守 ProjectE 学习状态。
 * - 新增：生成前调用 EmcService.hasKnowledge。
 * - 修改：未学习物品在查询阶段直接跳过，不模拟扣款、不写入背包。
 * - 目的：玩家背包配置与机器配置器共享“只输出已学习物品”的服务端规则。
 */
/**
 * 【变更记录 #9】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
/**
 * 【变更记录 #10】2026-09-15 14:54:10（第 10 次备注 · 1.0.0-emc-service）
 * - 删除：直接计算整数总价并全量扣费的分支，以及固定数量与现有数量 int 相加。
 * - 新增：统一 purchaseItem、按余额部分补充与操作后背包变更通知。
 * - 修改：空槽也先检查知识与正价格；每个规则仅写绑定槽，不创建充能物品或改变暂停状态。
 * - 用途：背包复用机器的真实数量结算，避免无价免费复制及大数量溢出。
 */
/**
 * 【变更记录 #11】2026-10-14 17:12:00（第 11 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：在线玩家原生法力、灵魂账户按开关补充实际缺口。
 * - 修改：价格、知识、模拟接收与实际退款全部复用 EmcService，失败单独隔离。
 * - 用途：配置器启用后自动购买，满额、欠费、无容器或缺少可选模组时不扣费。
 */
package com.primordial.task;

import com.primordial.data.PlayerInventoryConfigSavedData;
import com.primordial.service.EmcService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class PlayerInventoryTask {
    private PlayerInventoryTask() {}

    public static void run(MinecraftServer server) {
        var data = PlayerInventoryConfigSavedData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int index = 0; index < com.primordial.resource.PlayerResourcePorts.COUNT; index++) {
                if (!data.resourceEnabled(player.getUUID(), index)) continue;
                try {
                    var port = com.primordial.resource.PlayerResourcePorts.find(player, index);
                    if (port != null && port.canReceive())
                        EmcService.supplyResource(server, player.getUUID(), port, Math.max(0, port.capacity() - port.amount()));
                } catch (RuntimeException | LinkageError failure) {
                    com.primordial.PrimordialMod.LOGGER.debug("[Primordial] player resource supplement failed", failure);
                }
            }
            for (var entry : data.rules(player.getUUID()).entrySet()) {
                int slot = entry.getKey(); PlayerInventoryConfigSavedData.Rule rule = entry.getValue();
                if (!rule.enabled() || rule.targetCount() <= 0) continue;
                if (rule.mode() == com.primordial.data.SampleMode.FIXED_RATE
                        && !TaskScheduler.intervalDue("player:" + player.getUUID(), slot, rule.intervalTicks(), player.level().getGameTime())) continue;
                ItemStack current = player.getInventory().getItem(slot);
                if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, rule.filter())) continue;
                if (!EmcService.hasKnowledge(server, player.getUUID(), rule.filter())) continue;
                int have = current.isEmpty() ? 0 : current.getCount();
                int room = Math.max(0, rule.filter().getMaxStackSize() - have);
                int missing = rule.mode() == com.primordial.data.SampleMode.KEEP_LEVEL
                        ? room : Math.min(rule.targetCount(), room);
                if (missing <= 0) continue;
                EmcService.purchaseItem(server, player.getUUID(), rule.filter(), missing, units -> {
                    ItemStack result = current.isEmpty() ? rule.filter().copyWithCount((int) units)
                            : current.copyWithCount(have + (int) units);
                    player.getInventory().setItem(slot, result);
                    player.getInventory().setChanged();
                    return units;
                });
            }
        }
    }
}
