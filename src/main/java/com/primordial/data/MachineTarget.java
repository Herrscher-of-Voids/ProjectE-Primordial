/**
 * 【文件职责】保存外部方向、端口索引及结构身份，供配置 NBT、网络绑定和任务校验共用。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增端口值对象。
 * - 新增：不可变端口记录、512 字符身份上限和 UNBOUND 状态。
 * - 修改：旧 NBT 缺字段或方向越界时保持未绑定，不解释成自动方向或槽 0。
 * - 用途：MachineConfig 各介质持久化此对象；未绑定时 TaskExecutor 跳过对应样本。
 */
/**
 * 【变更记录 #2】2026-09-14 12:10:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：side=6 表示方块实体的内部/无方向能力，供仅通过 null side 暴露 FE 的能量塔使用。
 * - 修改：普通物品和流体仍只接受六个外部方向；directionOrNull 为能量访问提供统一转换。
 * - 用途：持久化龙之研究等 FE Utility 接口，同时保持旧六面端口数据兼容。
 */
/**
 * 【变更记录 #3】2026-10-13 22:57:15（第 3 次备注 · 1.2.0-relative-target-system）
 * - 删除：新绑定只保存世界方向、机器旋转后仍操作旧面的路径；所有历史备注保留。
 * - 新增/修改：新增 relative_face 存档与相对绑定构造、运行时解析；只转换已知端口身份的方向段，保留索引、handler、组件；旧目标保持绝对方向，内部目标保持 null，损坏相对面拒绝。
 * - 用途：第 9 系统相对面绑定与显示、网络、存档、执行保持一致；已有绝对目标重新绑定才转为相对，避免误改输入输出。
 */
package com.primordial.data;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import com.primordial.util.RelativeMachineSides;

/** 选定的外部自动化端口；空 key 表示未绑定，绝不解释为自动搜索或所有槽。 */
public record MachineTarget(int side, int index, String key, RelativeMachineSides.Face relativeFace) {
    public MachineTarget(int side, int index, String key) { this(side, index, key, null); }
    public static final MachineTarget UNBOUND = new MachineTarget(-1, -1, "");
    public static final int MAX_KEY_LENGTH = 512;
    public static final int INTERNAL_SIDE = 6;

    public boolean bound() {
        return side >= 0 && side <= INTERNAL_SIDE && (side != INTERNAL_SIDE || relativeFace == null) && index >= 0 && key != null
                && !key.isEmpty() && key.length() <= MAX_KEY_LENGTH;
    }

    public Direction direction() {
        if (!bound()) throw new IllegalStateException("Unbound machine target");
        if (side == INTERNAL_SIDE) throw new IllegalStateException("Internal target has no Direction");
        return Direction.from3DDataValue(side);
    }

    /** 返回外部方向；内部端口返回 null，仅供能量 capability 查询使用。 */
    public Direction directionOrNull() {
        if (!bound()) throw new IllegalStateException("Unbound machine target");
        return side == INTERNAL_SIDE ? null : Direction.from3DDataValue(side);
    }

    /** 新绑定记录相对面；side/key 保留绑定时的原值，用于严格校验原端口结构。 */
    public static MachineTarget relative(int side, int index, String key, BlockState state) {
        var absolute = new MachineTarget(side, index, key);
        if (!absolute.bound() || side == INTERNAL_SIDE) return absolute;
        return new MachineTarget(side, index, key, RelativeMachineSides.from(state).face(absolute.direction()));
    }

    /** 只转换方向及本项目定义的方向身份段，不重选槽、不忽略 handler/组件/容量结构。 */
    public MachineTarget resolve(BlockState state) {
        if (!bound() || relativeFace == null) return this;
        Direction current = RelativeMachineSides.from(state).direction(relativeFace);
        Direction original = direction();
        if (current == original) return new MachineTarget(side, index, key);
        String resolvedKey = key.replace("/side/" + original.getName() + "/class/",
                "/side/" + current.getName() + "/class/");
        String[] parts = resolvedKey.split("/", 4);
        if (parts.length == 4 && parts[1].contains(".") && parts[2].equals(original.getName())
                && java.util.Set.of("botania", "pneumaticcraft", "naturesaura", "ars_nouveau", "create_speed", "create_stress").contains(parts[0])) {
            resolvedKey = parts[0] + "/" + parts[1] + "/" + current.getName() + "/" + parts[3];
        }
        return new MachineTarget(current.get3DDataValue(), index, resolvedKey);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (bound()) {
            tag.putInt("side", side);
            tag.putInt("index", index);
            tag.putString("key", key);
            if (side == INTERNAL_SIDE) tag.putBoolean("internal", true);
            if (relativeFace != null) tag.putString("relative_face", relativeFace.key);
        }
        return tag;
    }

    /** 旧配置没有端口或 NBT 非法时保持未绑定，防止默认指向 DOWN/槽 0。 */
    public static MachineTarget load(CompoundTag tag) {
        if (!tag.contains("side", Tag.TAG_INT) || !tag.contains("index", Tag.TAG_INT)
                || !tag.contains("key", Tag.TAG_STRING)) return UNBOUND;
        if (tag.getInt("side") == INTERNAL_SIDE && !tag.getBoolean("internal")) return UNBOUND;
        RelativeMachineSides.Face face = null;
        if (tag.contains("relative_face")) {
            if (!tag.contains("relative_face", Tag.TAG_STRING)) return UNBOUND;
            for (var candidate : RelativeMachineSides.Face.values())
                if (candidate.key.equals(tag.getString("relative_face"))) face = candidate;
            if (face == null) return UNBOUND;
        }
        MachineTarget target = new MachineTarget(tag.getInt("side"), tag.getInt("index"), tag.getString("key"), face);
        return target.bound() ? target : UNBOUND;
    }
}
