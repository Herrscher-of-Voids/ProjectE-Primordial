/**
 * 【变更记录 #1】2026-10-11 23:44:54（第 1 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；原黑洞注册和 properties 不变。
 * - 新增：transmutation_interface 独立注册对象，使用金属外观、工具采集与标准爆炸抗性。
 * - 修改：无；网络和结算行为由方块实体 capability 完成。
 * - 用途：为可选 AE2/RS 外部存储提供稳定方块注册 ID。
 */
/**
 * 【变更记录 #2】2026-10-14 16:01:56（第 2 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：旧实体无报价/无入账路径的相关限制（其它历史功能保留）。
 * - 新增：注册实体生成器普通方块，无实体库存或可转移账户。
 * - 修改：服务端校验、报价与统一 EMC 结算共同执行；不信任客户端价格或 NBT。
 * - 用途：完成实体生成、黑洞回收与同账户融合系统，失败不重复收费或产出。
 */
package com.primordial.registry;

import com.primordial.PrimordialMod;
import com.primordial.block.BlackholeBlock;
import com.primordial.block.TransmutationInterfaceBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, PrimordialMod.MODID);

    public static final RegistryObject<Block> ENTITY_GENERATOR = BLOCKS.register("entity_generator",
            () -> new com.primordial.block.EntityGeneratorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3,6).sound(SoundType.METAL)));

    public static final RegistryObject<Block> BLACKHOLE = BLOCKS.register("blackhole",
            () -> new BlackholeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 600.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final RegistryObject<Block> TRANSMUTATION_INTERFACE = BLOCKS.register("transmutation_interface",
            () -> new TransmutationInterfaceBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));
}
