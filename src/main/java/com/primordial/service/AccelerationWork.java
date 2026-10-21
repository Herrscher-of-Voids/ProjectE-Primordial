/**
 * 【文件职责】表示当前服务端 tick 内的一种可付费加速工作。
 * 【变更记录 #1】2026-09-18 01:30:39（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：去重键、即时有效性检查、一次原生调用及实体标记编号。
 * - 修改：方块、实体和世界时钟使用同一预算与扣款入口；本对象不跨 tick 缓存。
 * - 用途：避免各模式另建余额或执行循环，世界时钟用全局种类键防止多玩家重复叠加。
 */
package com.primordial.service;

import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

record AccelerationWork(Object key, ServerLevel level, BlockPos position, BooleanSupplier valid,
                        Runnable tick, int entityId) { }
