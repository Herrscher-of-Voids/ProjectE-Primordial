package com.primordial.neoforge.machine;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.RegisterEvent;

import com.primordial.neoforge.PrimordialNeoForgeMod;

/**
 * 【文件职责】仅在 GameTest 源集注册测试方块实体，以生产代理查询标准资源能力。
 * 测试端口为北面物品、南面流体、无方向能量；开关影响能力可用性，内容与 tick 可存档。
 *
 * 【变更记录 #1】2026-10-22 10:50:42（第 1 次备注 · 2.0.0-neo-dev 机器平台验收）
 * - 删除：无。
 * - 新增：测试专用注册、三资源容器、服务端 ticker、NBT 和端口开关主动失效。
 * - 修改：无生产机器行为；所有测试注册均随独立 gametest 源集隔离。
 * - 用途：验证目标版方块实体生命周期与 provider 契约，不替代黑洞结算或游戏验收。
 *
 * 【变更记录 #2】2026-10-22 10:56:31（第 2 次备注 · 2.0.0-neo-dev 机器平台验收）
 * - 删除：删除类初始化阶段直接构造测试方块和方块实体类型的静态实例。
 * - 新增：在对应 RegisterEvent 中延迟创建并注册测试方块，再创建方块实体类型。
 * - 修改：调整注册时序，避免类被自动订阅器加载时注册表已经冻结而触发初始化异常。
 * - 用途：让 GameTest 测试夹具遵守 NeoForge 注册生命周期；生产注册和 Forge 代码不受影响。
 */
@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeMachineTestFixture {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            PrimordialNeoForgeMod.MOD_ID, "capability_test_fixture");
    static FixtureBlock BLOCK;
    static BlockEntityType<FixtureBlockEntity> BLOCK_ENTITY_TYPE;

    private NeoForgeMachineTestFixture() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            event.register(Registries.BLOCK, ID, () -> {
                BLOCK = new FixtureBlock();
                return BLOCK;
            });
        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            event.register(Registries.BLOCK_ENTITY_TYPE, ID, () -> {
                BLOCK_ENTITY_TYPE = BlockEntityType.Builder.of(FixtureBlockEntity::new, BLOCK).build(null);
                return BLOCK_ENTITY_TYPE;
            });
        }
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BLOCK_ENTITY_TYPE,
                (be, side) -> be.hasOpenPort(side, Direction.NORTH) ? be.items : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BLOCK_ENTITY_TYPE,
                (be, side) -> be.hasOpenPort(side, Direction.SOUTH) ? be.fluids : null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BLOCK_ENTITY_TYPE,
                (be, side) -> be.hasOpenPort(side, null) ? be.energy : null);
    }

    static final class FixtureBlock extends Block implements EntityBlock {
        private FixtureBlock() {
            super(BlockBehaviour.Properties.of().strength(1.0F));
        }

        @Override
        public FixtureBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new FixtureBlockEntity(pos, state);
        }

        @Override
        @Nullable
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
                Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide || type != BLOCK_ENTITY_TYPE) return null;
            return (world, pos, blockState, blockEntity) ->
                    ((FixtureBlockEntity) blockEntity).serverTick();
        }
    }

    static final class FixtureBlockEntity extends BlockEntity {
        private boolean portsEnabled = true;
        private int serverTicks;
        private final ItemStackHandler items = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };
        private final FluidTank fluids = new FluidTank(1000) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        private final EnergyStorage energy = new EnergyStorage(1000) {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                int received = super.receiveEnergy(amount, simulate);
                if (!simulate && received > 0) setChanged();
                return received;
            }

            @Override
            public int extractEnergy(int amount, boolean simulate) {
                int extracted = super.extractEnergy(amount, simulate);
                if (!simulate && extracted > 0) setChanged();
                return extracted;
            }
        };

        FixtureBlockEntity(BlockPos pos, BlockState state) {
            super(BLOCK_ENTITY_TYPE, pos, state);
        }

        private boolean hasOpenPort(@Nullable Direction requested, @Nullable Direction allowed) {
            return portsEnabled && !isRemoved() && requested == allowed;
        }

        boolean portsEnabled() {
            return portsEnabled;
        }

        int serverTicks() {
            return serverTicks;
        }

        void setPortsEnabled(boolean enabled) {
            if (portsEnabled == enabled) return;
            portsEnabled = enabled;
            setChanged();
            // 能力可用性改变时通知外部缓存；普通容器内容改变仅标记存档。
            invalidateCapabilities();
        }

        private void serverTick() {
            serverTicks++;
            setChanged();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putBoolean("PortsEnabled", portsEnabled);
            tag.putInt("ServerTicks", serverTicks);
            tag.put("Items", items.serializeNBT(registries));
            tag.put("Fluids", fluids.writeToNBT(registries, new CompoundTag()));
            tag.put("Energy", energy.serializeNBT(registries));
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            boolean enabled = !tag.contains("PortsEnabled", Tag.TAG_BYTE) || tag.getBoolean("PortsEnabled");
            serverTicks = tag.getInt("ServerTicks");
            items.deserializeNBT(registries, tag.getCompound("Items"));
            fluids.readFromNBT(registries, tag.getCompound("Fluids"));
            if (tag.contains("Energy", Tag.TAG_INT)) energy.deserializeNBT(registries, tag.get("Energy"));
            setPortsEnabled(enabled);
        }
    }
}
