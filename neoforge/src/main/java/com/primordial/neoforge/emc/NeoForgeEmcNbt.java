/**
 * 【文件职责】将运行时精确 EMC 分数存为 BigInteger 字节数组；调用方负责损坏数据隔离。
 * 【变更记录 #1】2026-10-22 21:05:30（第 1 次备注 · 2.0.0-neo-dev 精确 EMC 入账）
 * - 删除：无。
 * - 新增：非负约分金额的 n/d 字节数组读写，缺字段、空数组或非法分数明确拒绝。
 * - 修改：沿用共享 EmcAmount 数值语义，不使用有长度上限的十进制 NBT 字符串。
 * - 用途：世界入账账本与黑洞本地队列共用格式；不承担 Forge 世界转换或价格文本输入。
 */
package com.primordial.neoforge.emc;

import java.math.BigInteger;

import com.primordial.service.EmcAmount;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class NeoForgeEmcNbt {
    private NeoForgeEmcNbt() {}

    public static CompoundTag write(EmcAmount amount) {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("n", amount.numerator().toByteArray());
        tag.putByteArray("d", amount.denominator().toByteArray());
        return tag;
    }

    public static EmcAmount read(CompoundTag tag) {
        if (!tag.contains("n", Tag.TAG_BYTE_ARRAY) || !tag.contains("d", Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("Missing EMC numerator or denominator");
        }
        return new EmcAmount(new BigInteger(tag.getByteArray("n")), new BigInteger(tag.getByteArray("d")));
    }
}
