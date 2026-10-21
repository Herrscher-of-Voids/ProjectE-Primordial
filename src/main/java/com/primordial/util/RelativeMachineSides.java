/**
 * 【文件职责】将机器水平朝向与界面相对侧面相互转换，不改变网络或存档中的绝对方向。
 * 【变更记录 #1】2026-10-02 21:47:28（第 1 次备注 · 1.2.0-relative-sides）
 * - 删除：无。
 * - 新增：六面映射、标准朝向属性解析及无水平朝向时以北为前的稳定参照。
 * - 修改：左右以玩家面对机器正面时的视角定义，上下保持世界垂直方向。
 * - 用途：供配置器名称、面贴图与点击查询共用，避免显示和实际端口方向不一致。
 */
package com.primordial.util;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public record RelativeMachineSides(Direction front, boolean fallback) {
    public RelativeMachineSides {
        if (front == null || front.getAxis().isVertical()) {
            front = Direction.NORTH;
            fallback = true;
        }
    }

    public enum Face {
        UP("up", 1, 0), LEFT("left", 0, 1), FRONT("front", 1, 1),
        RIGHT("right", 2, 1), BACK("back", 3, 1), DOWN("down", 1, 2);

        public final String key;
        public final int column, row;
        Face(String key, int column, int row) {
            this.key = key;
            this.column = column;
            this.row = row;
        }
    }

    public static RelativeMachineSides from(BlockState state) {
        if (state != null) {
            for (String name : new String[]{"facing", "horizontal_facing", "front"}) {
                for (var property : state.getProperties()) {
                    if (property instanceof DirectionProperty direction && property.getName().equals(name)) {
                        Direction value = state.getValue(direction);
                        if (value.getAxis().isHorizontal()) return new RelativeMachineSides(value, false);
                    }
                }
            }
        }
        return new RelativeMachineSides(Direction.NORTH, true);
    }

    public Direction direction(Face face) {
        return switch (face) {
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case FRONT -> front;
            case BACK -> front.getOpposite();
            case LEFT -> front.getClockWise();
            case RIGHT -> front.getCounterClockWise();
        };
    }

    public Face face(Direction direction) {
        for (Face face : Face.values()) if (direction(face) == direction) return face;
        throw new IllegalArgumentException("Missing machine side: " + direction);
    }
}
