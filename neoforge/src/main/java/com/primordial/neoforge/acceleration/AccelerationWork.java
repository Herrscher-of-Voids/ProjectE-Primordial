package com.primordial.neoforge.acceleration;

import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

record AccelerationWork(Object key, ServerLevel level, BlockPos position, BooleanSupplier valid,
                        Runnable tick, int entityId) { }
