/**
 * 【文件职责】每 20 tick 调度机器任务并维护每配置的检查/提示计时。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：删除仅分配 20 个计时槽的容量限制。
 * - 新增：为 9 条物品输入失败提示保留 20-28 的计时位置。
 * - 修改：SLOTS_PER_CONFIG 改为 29，与 TaskExecutor 的提示索引一致，避免退款提示时数组越界。
 * - 用途：原物品/流体/供能/回收的 0-19 定时规则保留。
 */
/**
 * 【变更记录 #2】2026-09-14 13:50:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：每秒调度 PlayerInventoryTask，按玩家规则维持指定栏位数量。
 * - 修改：机器任务仍固定每 20 tick 执行；玩家规则和机器规则在同一服务端线程顺序运行。
 * - 用途：集中控制自动化频率，避免为每个玩家或机器创建独立 tick 监听器。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
 */
/**
 * 【变更记录 #8】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
/**
 * 【变更记录 #3】2026-09-16（资源计时槽扩展）
 * - 修改：计时数组扩展为 60 个槽，覆盖六条独立资源输入和六条输出规则，修复高位资源槽越界风险。
 * - 用途：资源规则与既有物品、流体、能量规则共用服务端 tick 调度器。
 */
package com.primordial.task;

import java.util.HashMap;
import java.util.Map;

import com.primordial.PrimordialMod;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 服务端调度入口：每个服务端 tick 执行一轮全配置任务；固定模式由各规则的 tick 间隔控制。
 * 每配置 60 个计时槽：物品/流体/能量使用低位槽，48-53 为六条资源输入、54-59 为六条资源输出。
 * 纯内存，重启归零（所有间隔重新起算，可接受）。
 */
@Mod.EventBusSubscriber(modid = com.primordial.PrimordialMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaskScheduler {
    /** 必须覆盖 TaskExecutor 的最高资源输出槽 59（54 + 5）。 */
    public static final int SLOTS_PER_CONFIG = 60;

    private static final Map<String, long[]> TIMERS = new HashMap<>();

    private TaskScheduler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        // 服务端 tick 直接使用 intervalTicks 判断任务是否到期，补充模式由执行器单独按每 tick 规则处理。
        com.primordial.util.BalanceSyncer.onServerTick(server);
        try {
            PlayerInventoryTask.run(server);
            TaskExecutor.runAll(server);
        } catch (Exception e) {
            PrimordialMod.LOGGER.error("[Primordial] task tick failed", e);
        }
    }

    /** 判断指定规则经过 intervalTicks 个服务端 tick 后是否到期，并更新下一次执行时间。 */
    public static boolean intervalDue(String key, int slot, int intervalTicks, long gameTime) {
        long[] timers = TIMERS.computeIfAbsent(key, k -> new long[SLOTS_PER_CONFIG]);
        long next = timers[slot] + Math.max(1, intervalTicks);
        if (gameTime >= next) {
            timers[slot] = gameTime;
            return true;
        }
        return false;
    }

    public static void dropTimers(String key) {
        TIMERS.remove(key);
    }
}
