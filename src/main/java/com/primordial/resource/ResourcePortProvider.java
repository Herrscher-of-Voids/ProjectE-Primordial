/**
 * 【文件职责】定义第三方资源适配器的最小发现接口。
 *
 * 变更记录 #1（2026-09-16）：新增资源端口提供器；适配器只在对应模组已加载且找到完整
 * 读写方法时返回端口，异常会被注册表隔离，不影响通用 Forge 能力。
 */
package com.primordial.resource;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 为一个方块实体和方向发现可交易资源端口。 */
@FunctionalInterface
public interface ResourcePortProvider {
    List<EmcResourcePort> find(BlockEntity machine, @Nullable Direction side);
}
