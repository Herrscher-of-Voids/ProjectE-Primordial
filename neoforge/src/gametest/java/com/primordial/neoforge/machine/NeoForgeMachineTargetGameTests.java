package com.primordial.neoforge.machine;

import com.primordial.data.MachineTarget;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.machine.NeoForgeMachineTargetTestFixture.TargetBlockEntity;
import com.primordial.util.RelativeMachineSides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeMachineTargetGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);
    private static final NeoForgeMachineProxy PROXY = NeoForgeCapabilityProxy.INSTANCE;
    private static final Direction[] FRONTS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private NeoForgeMachineTargetGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void relativeTargetsFollowEveryFaceAfterNbtRoundTrip(GameTestHelper helper) {
        TargetBlockEntity machine = place(helper);
        for (Direction side : Direction.values()) {
            machine.items[side.ordinal()].setStackInSlot(1, new ItemStack(Items.STONE, side.ordinal() + 1));
        }
        for (Direction initial : FRONTS) {
            rotate(helper, machine, initial);
            for (RelativeMachineSides.Face face : RelativeMachineSides.Face.values()) {
                Direction original = RelativeMachineSides.from(machine.getBlockState()).direction(face);
                MachineTarget item = roundTrip(itemTarget(machine, original, 1));
                MachineTarget fluid = roundTrip(fluidTarget(machine, original, 0));
                MachineTarget energy = roundTrip(energyTarget(machine, original));
                check(item.relativeFace() == face && fluid.relativeFace() == face && energy.relativeFace() == face,
                        "Binding must store the selected relative face");
                for (Direction rotated : FRONTS) {
                    rotate(helper, machine, rotated);
                    Direction expected = RelativeMachineSides.from(machine.getBlockState()).direction(face);
                    IItemHandler selected = require(NeoForgeMachineTargetAccess.items(PROXY, machine, item),
                            "A relative item target must survive rotation");
                    check(selected.getStackInSlot(0).getCount() == expected.ordinal() + 1,
                            "Only the selected relative item side may be read");
                    check(NeoForgeMachineTargetAccess.fluids(PROXY, machine, fluid) == machine.fluids[expected.ordinal()],
                            "Relative fluid target must follow the same face");
                    check(NeoForgeMachineTargetAccess.energy(PROXY, machine, energy) == machine.energy[expected.ordinal()],
                            "Relative energy target must follow the same face");
                }
                rotate(helper, machine, initial);
            }
        }
        MachineTarget absolute = new MachineTarget(Direction.NORTH.ordinal(), 1,
                PROXY.itemSlotKey(machine, Direction.NORTH, machine.items[Direction.NORTH.ordinal()], 1));
        MachineTarget internal = roundTrip(energyTarget(machine, null));
        for (Direction front : FRONTS) {
            rotate(helper, machine, front);
            check(roundTrip(absolute).resolve(machine.getBlockState()).equals(absolute),
                    "Legacy absolute targets must keep their original world side");
            check(require(NeoForgeMachineTargetAccess.items(PROXY, machine, absolute), "Absolute target missing")
                    .getStackInSlot(0).getCount() == Direction.NORTH.ordinal() + 1,
                    "Absolute target must continue reading north");
            check(internal.relativeFace() == null && internal.directionOrNull() == null
                            && NeoForgeMachineTargetAccess.energy(PROXY, machine, internal) == machine.energy[6],
                    "Internal energy must remain a null-side endpoint after rotation");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void selectedSlotAndSingleTankTransfersPreserveNeighborsAndSimulation(GameTestHelper helper) {
        TargetBlockEntity machine = place(helper);
        ItemStackHandler source = machine.items[Direction.NORTH.ordinal()];
        source.setStackInSlot(0, new ItemStack(Items.DIAMOND, 4));
        source.setStackInSlot(1, new ItemStack(Items.STONE, 10));
        source.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 6));
        IItemHandler selected = require(NeoForgeMachineTargetAccess.items(PROXY, machine,
                itemTarget(machine, Direction.NORTH, 1)), "Selected item target missing");
        check(selected.getSlots() == 1 && selected.extractItem(0, 3, true).getCount() == 3
                        && selected.insertItem(0, new ItemStack(Items.STONE, 5), true).isEmpty(),
                "A target must expose one slot and preserve simulation results");
        check(source.getStackInSlot(1).getCount() == 10, "Simulation must leave the selected slot untouched");
        check(selected.extractItem(0, 3, false).getCount() == 3
                        && selected.insertItem(0, new ItemStack(Items.STONE, 5), false).isEmpty()
                        && source.getStackInSlot(1).getCount() == 12,
                "Execution must address precisely the selected source slot");
        check(source.getStackInSlot(0).is(Items.DIAMOND) && source.getStackInSlot(0).getCount() == 4
                        && source.getStackInSlot(2).is(Items.GOLD_INGOT) && source.getStackInSlot(2).getCount() == 6,
                "Target transfers must not modify neighboring slots");
        boolean rejected = false;
        try { selected.extractItem(1, 1, false); }
        catch (IndexOutOfBoundsException expected) { rejected = true; }
        check(rejected, "The single-slot view must reject every nonzero view index");

        IFluidHandler fluids = require(NeoForgeMachineTargetAccess.fluids(PROXY, machine,
                fluidTarget(machine, Direction.NORTH, 0)), "Single tank target missing");
        FluidStack water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 500);
        check(fluids.fill(water, IFluidHandler.FluidAction.SIMULATE) == 500 && fluids.getFluidInTank(0).isEmpty(),
                "Fluid simulation must not mutate the selected tank");
        check(fluids.fill(water, IFluidHandler.FluidAction.EXECUTE) == 500
                        && fluids.drain(125, IFluidHandler.FluidAction.SIMULATE).getAmount() == 125
                        && fluids.getFluidInTank(0).getAmount() == 500
                        && fluids.drain(125, IFluidHandler.FluidAction.EXECUTE).getAmount() == 125
                        && fluids.getFluidInTank(0).getAmount() == 375,
                "Single tank execution must respect actual and simulated amounts");
        check(machine.fluids[Direction.SOUTH.ordinal()].getFluidInTank(0).isEmpty(),
                "Single tank execution must not reach another side");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void invalidNbtTargetsAndAmbiguousMultiTanksStayUnbound(GameTestHelper helper) {
        TargetBlockEntity machine = place(helper);
        MachineTarget valid = itemTarget(machine, Direction.NORTH, 1);
        for (String invalidFace : new String[]{"", "FRONT", "diagonal"}) {
            CompoundTag tag = valid.save();
            tag.putString("relative_face", invalidFace);
            check(MachineTarget.load(tag).equals(MachineTarget.UNBOUND), "Unknown relative faces must not bind");
        }
        CompoundTag wrongType = valid.save();
        wrongType.putInt("relative_face", 0);
        check(MachineTarget.load(wrongType).equals(MachineTarget.UNBOUND), "Wrong face tag type must not bind");
        CompoundTag internal = energyTarget(machine, null).save();
        internal.remove("internal");
        check(MachineTarget.load(internal).equals(MachineTarget.UNBOUND), "Internal NBT requires the explicit marker");
        internal = energyTarget(machine, null).save();
        internal.putString("relative_face", "front");
        check(MachineTarget.load(internal).equals(MachineTarget.UNBOUND), "An internal port cannot have a relative face");
        check(MachineTarget.load(new CompoundTag()).equals(MachineTarget.UNBOUND), "Missing NBT must not become slot zero");

        for (MachineTarget target : new MachineTarget[]{MachineTarget.UNBOUND, null,
                new MachineTarget(-1, 1, valid.key()), new MachineTarget(7, 1, valid.key()),
                new MachineTarget(2, -1, valid.key()), new MachineTarget(2, 3, valid.key()),
                new MachineTarget(2, 1, "forged"), new MachineTarget(2, 1, "x".repeat(513))}) {
            check(NeoForgeMachineTargetAccess.items(PROXY, machine, target) == null,
                    "Malformed, forged and out-of-range item targets must be rejected");
        }
        check(NeoForgeMachineTargetAccess.items(PROXY, machine, energyTarget(machine, null)) == null
                        && NeoForgeMachineTargetAccess.fluids(PROXY, machine, energyTarget(machine, null)) == null,
                "Items and fluids must never treat the internal energy side as a physical side");
        MachineTarget energy = energyTarget(machine, null);
        check(NeoForgeMachineTargetAccess.energy(PROXY, machine, new MachineTarget(6, 1, energy.key())) == null,
                "Energy may only use endpoint index zero");
        machine.fluids[Direction.NORTH.ordinal()] = new NeoForgeMachineTargetTestFixture.TwoTanks();
        machine.invalidatePorts();
        for (int index = 0; index < 2; index++) {
            MachineTarget multi = fluidTarget(machine, Direction.NORTH, index);
            check(NeoForgeMachineTargetAccess.fluids(PROXY, machine, multi) == null,
                    "A generic aggregate with multiple tanks must not claim exact-tank writes");
        }
        NeoForgeMachinePortPage tankPage = NeoForgeMachinePortPage.read(PROXY, machine, 2, Direction.NORTH, 0);
        check(tankPage.ports().size() == 2 && tankPage.ports().stream().noneMatch(NeoForgeMachinePortPage.Port::bindable),
                "Generic multi-tank ports remain visible as read-only entries");
        check(RelativeMachineSides.from(Blocks.STONE.defaultBlockState()).fallback()
                        && RelativeMachineSides.from(Blocks.STONE.defaultBlockState()).front() == Direction.NORTH
                        && RelativeMachineSides.from(Blocks.DISPENSER.defaultBlockState()
                            .setValue(BlockStateProperties.FACING, Direction.UP)).fallback(),
                "Machines without a horizontal facing must retain the legacy north fallback");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void portPagesBoundReadsAndCopySnapshots(GameTestHelper helper) {
        TargetBlockEntity machine = place(helper);
        LazyItems items = new LazyItems(1_000_000);
        NeoForgeMachineProxy lazyProxy = new NeoForgeMachineProxy() {
            @Override public String id() { return "lazy-page-test"; }
            @Override public java.util.Set<Direction> supportedSides(
                    net.minecraft.world.level.block.entity.BlockEntity be) { return java.util.Set.of(Direction.NORTH); }
            @Override public IItemHandler itemHandler(net.minecraft.world.level.block.entity.BlockEntity be, Direction side) {
                return side == Direction.NORTH ? items : null;
            }
            @Override public IFluidHandler fluidHandler(net.minecraft.world.level.block.entity.BlockEntity be, Direction side) {
                return PROXY.fluidHandler(be, side);
            }
            @Override public net.neoforged.neoforge.energy.IEnergyStorage energyHandler(
                    net.minecraft.world.level.block.entity.BlockEntity be, Direction side) {
                return PROXY.energyHandler(be, side);
            }
        };

        NeoForgeMachinePortPage first = NeoForgeMachinePortPage.read(lazyProxy, machine, 0, Direction.NORTH, -50);
        check(first.page() == 0 && first.total() == 1_000_000 && first.ports().size() == 27
                        && items.reads == 27 && items.transfers == 0,
                "A negative page normalizes to zero and inspects only one 27-slot page");
        NeoForgeMachinePortPage.Port sample = first.ports().get(4);
        check(sample.index() == 4 && sample.amount() == 5 && sample.item().getCount() == 1 && sample.bindable(),
                "The first page must return a bindable one-item snapshot with the actual amount");
        sample.item().setCount(32);
        check(items.getStackInSlot(4).getCount() == 5 && items.transfers == 0,
                "Mutating a returned snapshot must not mutate or transfer from the source handler");

        items.reads = 0;
        NeoForgeMachinePortPage last = NeoForgeMachinePortPage.read(
                lazyProxy, machine, 1, Direction.NORTH, Integer.MAX_VALUE);
        check(last.page() == 37_037 && last.total() == 1_000_000 && last.ports().size() == 1
                        && last.ports().get(0).index() == 999_999 && items.reads == 1 && items.transfers == 0,
                "An oversized page normalizes to the final partial page without scanning earlier entries");
        check(NeoForgeMachinePortPage.read(lazyProxy, machine, 0, null, 0).total() == 0
                        && NeoForgeMachinePortPage.read(lazyProxy, machine, 9, Direction.NORTH, 0).total() == 0,
                "Invalid sections and null item sides must return an empty page");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void capabilityChangesAndReplacedBlockEntitiesRejectStaleTargets(GameTestHelper helper) {
        TargetBlockEntity machine = place(helper);
        MachineTarget item = itemTarget(machine, Direction.NORTH, 1);
        MachineTarget fluid = fluidTarget(machine, Direction.NORTH, 0);
        MachineTarget energy = energyTarget(machine, Direction.NORTH);
        checkAllBound(machine, item, fluid, energy);
        machine.closedSide = Direction.NORTH;
        machine.invalidatePorts();
        checkAllRejected(machine, item, fluid, energy);
        machine.closedSide = null;
        machine.enabled = false;
        machine.invalidatePorts();
        checkAllRejected(machine, item, fluid, energy);
        machine.enabled = true;
        machine.invalidatePorts();
        checkAllBound(machine, item, fluid, energy);
        for (int failure : new int[]{1, 2}) {
            machine.failure = failure;
            machine.invalidatePorts();
            checkAllRejected(machine, item, fluid, energy);
        }
        machine.failure = 0;
        machine.items[Direction.NORTH.ordinal()] = new ItemStackHandler(4);
        machine.invalidatePorts();
        check(NeoForgeMachineTargetAccess.items(PROXY, machine, item) == null,
                "Changing slot count must invalidate the bound layout");
        machine.items[Direction.NORTH.ordinal()] = new ItemStackHandler(3) {};
        check(NeoForgeMachineTargetAccess.items(PROXY, machine, item) == null,
                "Changing handler class must invalidate the bound structure");
        machine.setLevel(null);
        checkAllRejected(machine, item, fluid, energy);
        machine.setLevel(helper.getLevel());
        TargetBlockEntity detached = new TargetBlockEntity(machine.getBlockPos(), machine.getBlockState());
        checkAllRejected(detached, item, fluid, energy);
        detached.setLevel(helper.getLevel());
        checkAllRejected(detached, item, fluid, energy);
        TargetBlockEntity unloaded = new TargetBlockEntity(new BlockPos(1024, 1, 1024), machine.getBlockState());
        unloaded.setLevel(helper.getLevel());
        checkAllRejected(unloaded, itemTarget(unloaded, Direction.NORTH, 1),
                fluidTarget(unloaded, Direction.NORTH, 0), energyTarget(unloaded, Direction.NORTH));
        helper.setBlock(POSITION, Blocks.AIR);
        checkAllRejected(machine, item, fluid, energy);
        TargetBlockEntity replacement = place(helper);
        check(replacement != machine, "Replacement must create a new live block entity");
        machine.clearRemoved();
        checkAllRejected(machine, item, fluid, energy);
        checkAllBound(replacement, itemTarget(replacement, Direction.NORTH, 1),
                fluidTarget(replacement, Direction.NORTH, 0), energyTarget(replacement, Direction.NORTH));
        helper.succeed();
    }

    private static MachineTarget itemTarget(TargetBlockEntity machine, Direction side, int slot) {
        return MachineTarget.relative(side.get3DDataValue(), slot,
                PROXY.itemSlotKey(machine, side, machine.items[side.ordinal()], slot), machine.getBlockState());
    }

    private static MachineTarget fluidTarget(TargetBlockEntity machine, Direction side, int tank) {
        return MachineTarget.relative(side.get3DDataValue(), tank,
                PROXY.fluidTankKey(machine, side, machine.fluids[side.ordinal()], tank), machine.getBlockState());
    }

    private static MachineTarget energyTarget(TargetBlockEntity machine, Direction side) {
        return MachineTarget.relative(side == null ? 6 : side.get3DDataValue(), 0,
                NeoForgeMachineTargetAccess.energyKey(PROXY, machine, side,
                        machine.energy[side == null ? 6 : side.ordinal()]), machine.getBlockState());
    }

    private static void checkAllBound(TargetBlockEntity machine, MachineTarget item, MachineTarget fluid, MachineTarget energy) {
        check(NeoForgeMachineTargetAccess.items(PROXY, machine, item) != null
                        && NeoForgeMachineTargetAccess.fluids(PROXY, machine, fluid) != null
                        && NeoForgeMachineTargetAccess.energy(PROXY, machine, energy) != null,
                "Every selected live endpoint must be available");
    }

    private static void checkAllRejected(TargetBlockEntity machine, MachineTarget item, MachineTarget fluid, MachineTarget energy) {
        check(NeoForgeMachineTargetAccess.items(PROXY, machine, item) == null
                        && NeoForgeMachineTargetAccess.fluids(PROXY, machine, fluid) == null
                        && NeoForgeMachineTargetAccess.energy(PROXY, machine, energy) == null,
                "Unavailable or stale selected endpoints must not fall back to another side");
    }

    private static MachineTarget roundTrip(MachineTarget target) {
        MachineTarget restored = MachineTarget.load(target.save());
        check(restored.equals(target), "Target NBT must preserve side, index, key and relative face");
        return restored;
    }

    private static TargetBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POSITION, NeoForgeMachineTargetTestFixture.BLOCK.defaultBlockState());
        return (TargetBlockEntity) helper.getBlockEntity(POSITION);
    }

    private static void rotate(GameTestHelper helper, TargetBlockEntity machine, Direction front) {
        BlockState state = machine.getBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, front);
        helper.getLevel().setBlock(machine.getBlockPos(), state, 3);
        machine.invalidatePorts();
        check(helper.getBlockEntity(POSITION) == machine, "Rotation must preserve the real machine block entity");
    }

    private static final class LazyItems implements IItemHandler {
        private final int slots;
        int reads;
        int transfers;

        private LazyItems(int slots) { this.slots = slots; }
        @Override public int getSlots() { return slots; }
        @Override public ItemStack getStackInSlot(int slot) {
            reads++;
            if (slot < 0 || slot >= slots) throw new IndexOutOfBoundsException(slot);
            return new ItemStack(Items.STONE, slot % 64 + 1);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { transfers++; return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { transfers++; return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }

    private static <T> T require(T value, String message) {
        check(value != null, message);
        return value;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
