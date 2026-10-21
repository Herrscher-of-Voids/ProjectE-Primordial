/**
 * 【文件职责】保存加速器的目标种类、倍率、范围几何和运行选项，供服务端与界面共用。
 * 【变更记录 #1】2026-09-18 01:30:39（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：方块、实体、范围、昼夜、天气及两种范围标记模式；范围各边 1 至 15，偏移 -15 至 15。
 * - 修改：旧数据未指定种类时按方块读取；异常 NBT 数值规范化，网络参数必须先通过有效性检查。
 * - 用途：同一结构用于选中配置与已绑定目标，几何运算有界，不保存世界对象或额外余额。
 */
package com.primordial.service;

import com.primordial.item.EmcAcceleratorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;

public record AccelerationSettings(Kind kind, int rate, int sizeX, int sizeY, int sizeZ,
                                   int offsetX, int offsetY, int offsetZ, boolean idleThrottle, boolean disableAi) {
    public enum Kind {
        BLOCK, ENTITY, AREA, DAYTIME, WEATHER, AREA_FILTER, IDLE_FILTER;
        public boolean world() { return this == DAYTIME || this == WEATHER; }
        public boolean filter() { return this == AREA_FILTER || this == IDLE_FILTER; }
        public String translation() { return "screen.primordial.accelerator.mode_" + name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static Kind kind(CompoundTag tag) {
        if (!tag.contains("AccelerationKind")) return Kind.BLOCK;
        try { return Kind.valueOf(tag.getString("AccelerationKind")); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    public static AccelerationSettings read(CompoundTag tag) {
        Kind kind = kind(tag);
        return new AccelerationSettings(kind == null ? Kind.BLOCK : kind, AccelerationBindings.rate(tag),
                size(tag, "SizeX"), size(tag, "SizeY"), size(tag, "SizeZ"),
                offset(tag.getInt("OffsetX")), offset(tag.getInt("OffsetY")), offset(tag.getInt("OffsetZ")),
                !tag.contains("IdleThrottle") || tag.getBoolean("IdleThrottle"), tag.getBoolean("DisableEntityAi"));
    }

    public boolean valid() {
        return kind != null && EmcAccelerationService.isMultiplier(rate)
                && sizeX >= 1 && sizeX <= 15 && sizeY >= 1 && sizeY <= 15 && sizeZ >= 1 && sizeZ <= 15
                && Math.abs((long) offsetX) <= 15 && Math.abs((long) offsetY) <= 15 && Math.abs((long) offsetZ) <= 15;
    }

    public void write(CompoundTag tag) {
        if (!valid()) throw new IllegalArgumentException("Invalid acceleration settings");
        tag.putString("AccelerationKind", kind.name());
        tag.putInt(EmcAcceleratorItem.MULTIPLIER_KEY, rate);
        tag.putInt("SizeX", sizeX); tag.putInt("SizeY", sizeY); tag.putInt("SizeZ", sizeZ);
        tag.putInt("OffsetX", offsetX); tag.putInt("OffsetY", offsetY); tag.putInt("OffsetZ", offsetZ);
        tag.putBoolean("IdleThrottle", idleThrottle); tag.putBoolean("DisableEntityAi", disableAi);
    }

    public AccelerationSettings withKind(Kind value) {
        return new AccelerationSettings(value, rate, sizeX, sizeY, sizeZ, offsetX, offsetY, offsetZ, idleThrottle, disableAi);
    }

    public int volume() { return sizeX * sizeY * sizeZ; }
    public BlockPos minimum(BlockPos anchor) {
        return anchor.offset(offsetX - sizeX / 2, offsetY - sizeY / 2, offsetZ - sizeZ / 2);
    }
    public BlockPos position(BlockPos anchor, int index) {
        return minimum(anchor).offset(index % sizeX, index / sizeX % sizeY, index / (sizeX * sizeY));
    }
    public AABB bounds(BlockPos anchor) {
        BlockPos min = minimum(anchor);
        return new AABB(min.getX(), min.getY(), min.getZ(), min.getX() + sizeX, min.getY() + sizeY, min.getZ() + sizeZ);
    }

    private static int size(CompoundTag tag, String key) { return tag.contains(key) ? Math.max(1, Math.min(15, tag.getInt(key))) : 3; }
    private static int offset(int value) { return Math.max(-15, Math.min(15, value)); }
}
