/**
 * 【变更记录 #1】2026-10-11 23:44:54（第 1 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；黑洞方块实体注册保留。
 * - 新增：转化接口方块实体类型与对应方块绑定。
 * - 修改：不引入 AE2/RS 类型到注册表，第三方 capability 在隔离适配器注册。
 * - 用途：保持可选网络未安装时方块仍可正常加载，未绑定时不会提供交易。
 */
package com.primordial.registry;

import com.primordial.PrimordialMod;
import com.primordial.blockentity.BlackholeBlockEntity;
import com.primordial.blockentity.TransmutationInterfaceBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PrimordialMod.MODID);

    public static final RegistryObject<BlockEntityType<BlackholeBlockEntity>> BLACKHOLE =
            BLOCK_ENTITIES.register("blackhole",
                    () -> BlockEntityType.Builder.of(BlackholeBlockEntity::new, ModBlocks.BLACKHOLE.get())
                            .build(null));

    public static final RegistryObject<BlockEntityType<TransmutationInterfaceBlockEntity>> TRANSMUTATION_INTERFACE =
            BLOCK_ENTITIES.register("transmutation_interface",
                    () -> BlockEntityType.Builder.of(TransmutationInterfaceBlockEntity::new,
                            ModBlocks.TRANSMUTATION_INTERFACE.get()).build(null));
}
