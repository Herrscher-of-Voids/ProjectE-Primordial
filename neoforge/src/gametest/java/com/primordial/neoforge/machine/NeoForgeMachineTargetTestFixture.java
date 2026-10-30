package com.primordial.neoforge.machine;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Rotatable, independently sided real capabilities, registered only in the GameTest source set. */
@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeMachineTargetTestFixture {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            PrimordialNeoForgeMod.MOD_ID, "machine_target_test_fixture");
    static TargetBlock BLOCK;
    static BlockEntityType<TargetBlockEntity> TYPE;

    private NeoForgeMachineTargetTestFixture() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            event.register(Registries.BLOCK, ID, () -> BLOCK = new TargetBlock());
        } else if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) {
            event.register(Registries.BLOCK_ENTITY_TYPE, ID,
                    () -> TYPE = BlockEntityType.Builder.of(TargetBlockEntity::new, BLOCK).build(null));
        }
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TYPE,
                (be, side) -> side != null && !be.chemicalOnly && be.available(side) ? be.items[side.ordinal()] : null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TYPE,
                (be, side) -> side != null && !be.chemicalOnly && be.available(side) ? be.fluids[side.ordinal()] : null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, TYPE,
                (be, side) -> !be.chemicalOnly && be.available(side) ? be.energy[side == null ? 6 : side.ordinal()] : null);
        if (ModList.get().isLoaded("mekanism")) registerChemical(event);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerChemical(RegisterCapabilitiesEvent event) {
        try {
            var capability = (net.neoforged.neoforge.capabilities.BlockCapability) Class.forName(
                    "com.primordial.neoforge.compat.mekanism.NeoForgeMachineChemicalAccess")
                    .getField("CHEMICAL").get(null);
            event.registerBlockEntity(capability, TYPE,
                    (be, side) -> be.available((Direction) side) ? be.chemicalHandler : null);
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Cannot register optional machine chemical test capability", failure);
        }
    }

    static final class TargetBlock extends Block implements EntityBlock {
        TargetBlock() {
            super(BlockBehaviour.Properties.of().strength(1));
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.HORIZONTAL_FACING);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TargetBlockEntity(pos, state);
        }
    }

    static final class TargetBlockEntity extends BlockEntity {
        final ItemStackHandler[] items = new ItemStackHandler[6];
        final IFluidHandler[] fluids = new IFluidHandler[6];
        final TestEnergyStorage[] energy = new TestEnergyStorage[7];
        Object chemicalHandler;
        boolean chemicalOnly;
        boolean enabled = true;
        Direction closedSide;
        int failure;

        TargetBlockEntity(BlockPos pos, BlockState state) {
            super(TYPE, pos, state);
            for (int side = 0; side < 6; side++) {
                items[side] = new ItemStackHandler(3);
                fluids[side] = new FluidTank(1000);
                energy[side] = new TestEnergyStorage(1000);
            }
            energy[6] = new TestEnergyStorage(2000);
        }

        boolean available(Direction side) {
            if (failure == 1) throw new IllegalStateException("Deliberate test provider failure");
            if (failure == 2) throw new NoClassDefFoundError("Deliberate optional API failure");
            return enabled && !isRemoved() && (closedSide == null || side != closedSide);
        }

        void invalidatePorts() {
            invalidateCapabilities();
        }
    }

    static class TestEnergyStorage extends EnergyStorage {
        boolean failReads;

        TestEnergyStorage(int capacity) {
            super(capacity);
        }

        @Override public int getEnergyStored() {
            if (failReads) throw new IllegalStateException("Deliberate test energy read failure");
            return super.getEnergyStored();
        }

        @Override public int getMaxEnergyStored() {
            if (failReads) throw new IllegalStateException("Deliberate test energy read failure");
            return super.getMaxEnergyStored();
        }
    }

    /** An aggregate cannot guarantee that fill/drain addresses its selected tank. */
    static final class TwoTanks implements IFluidHandler {
        private final FluidTank first = new FluidTank(1000);
        private final FluidTank second = new FluidTank(1000);

        @Override public int getTanks() { return 2; }
        @Override public FluidStack getFluidInTank(int tank) { return tank(tank).getFluid(); }
        @Override public int getTankCapacity(int tank) { return tank(tank).getCapacity(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return tank(tank).isFluidValid(stack); }
        @Override public int fill(FluidStack stack, FluidAction action) { return first.fill(stack, action); }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return first.drain(stack, action); }
        @Override public FluidStack drain(int amount, FluidAction action) { return first.drain(amount, action); }

        private FluidTank tank(int index) {
            if (index < 0 || index > 1) throw new IndexOutOfBoundsException(index);
            return index == 0 ? first : second;
        }
    }
}
