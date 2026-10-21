/**
 * 【文件职责】验证相对侧面不会改变自动化访问的实际世界方向。
 * 【变更记录 #1】2026-10-02 21:50:03（第 1 次备注 · 1.2.0-relative-sides）
 * - 删除：无；新增：四个水平朝向、六面唯一性、垂直/无朝向回退及旧绑定持久化回归。
 * - 修改：使用真实方块状态与 MachineTarget NBT，不启动客户端渲染器。
 * - 用途：避免左右颠倒、上下重合或把已有绑定迁移到其他机器面。
 */
package com.primordial.util;

import com.primordial.data.MachineTarget;
import com.primordial.machine.AdapterTestBootstrap;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import static com.primordial.util.RelativeMachineSides.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class RelativeMachineSidesTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void leftAndRightMatchAViewerLookingAtTheFrontForEveryHorizontalFacing() {
        Direction[][] expected = {
                {Direction.NORTH, Direction.EAST, Direction.WEST},
                {Direction.SOUTH, Direction.WEST, Direction.EAST},
                {Direction.EAST, Direction.SOUTH, Direction.NORTH},
                {Direction.WEST, Direction.NORTH, Direction.SOUTH}
        };
        for (var row : expected) {
            var state = Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, row[0]);
            var sides = RelativeMachineSides.from(state);
            assertFalse(sides.fallback());
            assertEquals(row[0], sides.direction(FRONT));
            assertEquals(row[0].getOpposite(), sides.direction(BACK));
            assertEquals(row[1], sides.direction(LEFT));
            assertEquals(row[2], sides.direction(RIGHT));
        }
    }

    @Test void sixFacesRemainUniqueAndRoundTripAcrossAllOrientations() {
        for (Direction front : Direction.values()) {
            var sides = new RelativeMachineSides(front, false);
            var actual = EnumSet.noneOf(Direction.class);
            for (var face : RelativeMachineSides.Face.values()) {
                Direction direction = sides.direction(face);
                assertTrue(actual.add(direction));
                assertEquals(face, sides.face(direction));
            }
            assertEquals(6, actual.size());
            assertEquals(Direction.UP, sides.direction(UP));
            assertEquals(Direction.DOWN, sides.direction(DOWN));
        }
    }

    @Test void directionalBlocksUseFacingPropertyWhenHorizontal() {
        var state = Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST);
        assertEquals(new RelativeMachineSides(Direction.WEST, false), RelativeMachineSides.from(state));
    }

    @Test void verticalOrMissingFacingUsesStableNorthAndReportsFallback() {
        for (Direction direction : new Direction[]{Direction.UP, Direction.DOWN}) {
            var state = Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, direction);
            assertEquals(new RelativeMachineSides(Direction.NORTH, true), RelativeMachineSides.from(state));
        }
        assertEquals(new RelativeMachineSides(Direction.NORTH, true), RelativeMachineSides.from(Blocks.STONE.defaultBlockState()));
        assertEquals(new RelativeMachineSides(Direction.NORTH, true), RelativeMachineSides.from(null));
    }

    @Test void savedAbsoluteTargetsStayOnTheSameWorldFaceWhenMachineRotates() {
        for (Direction direction : Direction.values()) {
            var target = new MachineTarget(direction.get3DDataValue(), 7, "test/slot/7");
            var saved = target.save();
            for (Direction front : Direction.Plane.HORIZONTAL) {
                var sides = new RelativeMachineSides(front, false);
                var loaded = MachineTarget.load(saved);
                assertEquals(target, loaded);
                assertEquals(direction, sides.direction(sides.face(loaded.direction())));
                assertEquals(saved, loaded.save());
            }
        }
    }

    @Test void internalTargetKeepsItsNullCapabilitySide() {
        var target = new MachineTarget(MachineTarget.INTERNAL_SIDE, 0, "test/internal");
        assertEquals(target, MachineTarget.load(target.save()));
        assertNull(MachineTarget.load(target.save()).directionOrNull());
    }
}
