/**
 * 【文件职责】为机器物品补充提供有容量边界的模拟和实际插入，返回实际接受数量供 EMC 结算。
 * 【变更记录 #1】2026-09-15 13:42:29（第 1 次备注 · 1.0.0-thermal-item-overflow）
 * - 删除：无；从 TaskExecutor 提取物品写入职责。
 * - 新增：按目标槽剩余容量裁剪请求、满槽快速返回、执行前重新检查容量及余量边界校验。
 * - 修改：不再将补满的 Integer.MAX_VALUE 哨兵直接作为 ItemStack 数量传给第三方接口。
 * - 用途：避免 CoFH 将现有数量与请求量以 int 相加时溢出，保留大容量槽位和最多 64 轮的推进保护。
 */
package com.primordial.task;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** 仅在服务端线程使用；调用者传入已验证的目标槽视图，价格和余额仍由 TaskExecutor 处理。 */
public final class MachineItemTransfer {
    private static final int MAX_INSERT_PASSES = 64;

    private MachineItemTransfer() {}

    /** 模拟最多 requested 个物品；每个槽只探测真实空位，模拟总量不能超过本次请求。 */
    public static int simulateInsert(IItemHandler handler, ItemStack template, int requested) {
        if (requested <= 0 || template.isEmpty()) return 0;
        int remaining = requested;
        int slots = handler.getSlots();
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            int offered = availableSpace(handler, slot, template, remaining);
            if (offered <= 0) continue;
            ItemStack probe = template.copyWithCount(offered);
            remaining -= acceptedCount(offered, handler.insertItem(slot, probe, true));
        }
        return requested - remaining;
    }

    /**
     * 执行已支付数量，每次调用第三方接口前都重查空间，不能复用模拟时的旧容量。
     * 有进展才继续下一轮；最多 64 轮，未接受的部分由调用者退款，不产生按物品总量增长的循环。
     */
    public static int insertCount(IItemHandler handler, ItemStack template, int requested) {
        if (requested <= 0 || template.isEmpty()) return 0;
        int remaining = requested;
        int slots = handler.getSlots();
        for (int pass = 0; pass < MAX_INSERT_PASSES && remaining > 0; pass++) {
            int before = remaining;
            for (int slot = 0; slot < slots && remaining > 0; slot++) {
                int offered = availableSpace(handler, slot, template, remaining);
                if (offered <= 0) continue;
                ItemStack input = template.copyWithCount(offered);
                remaining -= acceptedCount(offered, handler.insertItem(slot, input, false));
            }
            if (remaining == before) break;
        }
        return requested - remaining;
    }

    /**
     * 先以 long 做减法，再裁剪为正 int，保证槽内数量 + 本次请求不超过接口声明的容量。
     * 使用 handler 的容量而非固定 64，以支持抽屉等大容量库存；物品限制、方向和过滤仍由原接口校验。
     * 只读取现有 ItemStack，不修改接口返回的库存引用或配置样本。
     */
    private static int availableSpace(IItemHandler handler, int slot, ItemStack template, int requested) {
        ItemStack current = handler.getStackInSlot(slot);
        if (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, template)) return 0;
        long stored = current.isEmpty() ? 0L : current.getCount();
        long space = Math.max(0L, (long) handler.getSlotLimit(slot) - stored);
        return (int) Math.min((long) requested, space);
    }

    /** 第三方返回的余量必须裁剪到本次实际提交量，不能使计费数量变成负数或超过请求。 */
    private static int acceptedCount(int offered, ItemStack remainder) {
        return offered - Math.max(0, Math.min(offered, remainder.getCount()));
    }
}
