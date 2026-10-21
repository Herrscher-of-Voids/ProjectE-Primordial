/**
 * 【文件职责】限制单把加速器的一轮运行时长与全部目标的累计净消费。
 * 【变更记录 #1】2026-09-16 15:26:22（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：600 服务端 tick 截止时间、2100000000 EMC 总额和本次服务端会话标识。
 * - 修改：轮内新增目标共享剩余时间和额度；全部停用后再次启动才开始新一轮。
 * - 用途：重启不能续用旧授权；不足一次额外调用的余额不超扣，统计采用 BigInteger。
 */
/**
 * 【文件职责】保存每把加速器一轮的截止时间、服务端会话和净消费。
 * 【变更记录 #2】2026-09-16 19:41:50（第 2 次备注 · 1.0.0-review-ready）
 * - 删除：移除异常负值被当作零消费及读取额度时创建 NBT 的路径。
 * - 新增：600 tick 与 2100000000 EMC 共同限制本轮；新增目标共享额度，旧会话强制失效。
 * - 修改：到期开始新轮前暂停旧目标，仅恢复本次明确选择；金额按 BigInteger 保守校验。
 * - 用途：由确认与执行服务共用，预算不足仅允许可支付的额外调用，避免超扣。
 */
/**
 * 【变更记录 #3】2026-09-18 01:06:25（第 3 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：以整数整 tick 费用估算调用额度的 affordableCalls，改由调用方按精确单价裁剪。
 * - 新增：无。
 * - 修改：本轮消费继续累计统一结算返回的整数净扣款，分数找零保存在玩家统一账户中。
 * - 用途：秒计费下保持 600 tick 与 21 亿 EMC 上限，重新激活也不会丢失已付款的找零。
 */
package com.primordial.service;

import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public final class AccelerationRun {
    public static final int DURATION_TICKS = 600;
    public static final BigInteger MAX_SPEND = BigInteger.valueOf(2_100_000_000L);
    private static UUID session = UUID.randomUUID();
    private AccelerationRun() { }

    static void begin(ItemStack stack, MinecraftServer server) {
        if (AccelerationBindings.view(stack).stream().anyMatch(AccelerationBindings::enabled) && !expired(stack, server)) return;
        if (expired(stack, server)) AccelerationBindings.pauseAll(stack);
        CompoundTag root = stack.getOrCreateTag();
        root.putUUID("RunSession", session);
        root.putLong("RunDeadline", (long) server.getTickCount() + DURATION_TICKS);
        root.putString("RunSpent", "0");
    }

    public static boolean expired(ItemStack stack, MinecraftServer server) {
        CompoundTag root = stack.getTag();
        return root == null || !root.hasUUID("RunSession") || !session.equals(root.getUUID("RunSession"))
                || server.getTickCount() >= root.getLong("RunDeadline");
    }

    public static BigInteger remaining(ItemStack stack) {
        try {
            CompoundTag root = stack.getTag();
            if (root == null) return BigInteger.ZERO;
            String amount = root.getString("RunSpent");
            if (!amount.matches("[0-9]{1,10}")) return BigInteger.ZERO;
            return MAX_SPEND.subtract(new BigInteger(amount)).max(BigInteger.ZERO);
        } catch (NumberFormatException invalid) { return BigInteger.ZERO; }
    }

    static void charge(ItemStack stack, BigInteger amount) {
        stack.getOrCreateTag().putString("RunSpent", MAX_SPEND.subtract(remaining(stack)).add(amount).toString());
    }

    static void reset() { session = UUID.randomUUID(); }
}
