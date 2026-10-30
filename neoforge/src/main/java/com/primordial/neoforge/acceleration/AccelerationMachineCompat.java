package com.primordial.neoforge.acceleration;

import java.util.function.Function;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.neoforged.fml.ModList;

public final class AccelerationMachineCompat {

    public interface CheckedTicker extends BlockEntityTicker<BlockEntity> {
        boolean valid();
        boolean idle();
    }
    private static boolean initialized;
    private static Function<BlockEntity, BlockEntityTicker<BlockEntity>> adapter;
    private AccelerationMachineCompat() { }

    @SuppressWarnings("unchecked")
    static BlockEntityTicker<BlockEntity> ticker(BlockEntity entity) {
        try {
            if (!initialized) {
                initialized = true;
                if (ModList.get() != null && ModList.get().isLoaded("ae2")) {
                    adapter = (Function<BlockEntity, BlockEntityTicker<BlockEntity>>) Class.forName(
                            "com.primordial.neoforge.acceleration.Ae2AccelerationAdapter").getConstructor().newInstance();
                }
            }
            return adapter == null ? null : adapter.apply(entity);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            adapter = null;
            return null;
        }
    }
}
