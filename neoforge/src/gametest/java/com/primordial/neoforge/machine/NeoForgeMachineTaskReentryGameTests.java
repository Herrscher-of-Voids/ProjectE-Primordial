package com.primordial.neoforge.machine;

import java.util.UUID;

import com.primordial.data.MachineTarget;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.machine.NeoForgeMachineTargetTestFixture.TargetBlockEntity;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.util.MachineKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeMachineTaskReentryGameTests {
    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void reclaimGetterCannotReenterToDisableRotateOrInvalidateTheTarget(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, NeoForgeMachineTargetTestFixture.BLOCK);
        TargetBlockEntity machine = (TargetBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        var server = helper.getLevel().getServer();
        String key = MachineKey.of(helper.getLevel().dimension(), machine.getBlockPos());
        UUID owner = UUID.randomUUID();
        var data = NeoForgeMachineConfigSavedData.get(server);
        ItemStack filter = new ItemStack(Items.STONE);
        check(data.getOrCreate(key, owner) != null && !NeoForgeRecoveryPrices.item(filter).isZero(),
                "The test requires a valid owner and a priced reclaim sample");
        try {
            for (int variant = 0; variant < 3; variant++) {
                machine.enabled = true;
                helper.getLevel().setBlockAndUpdate(machine.getBlockPos(), machine.getBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
                int action = variant;
                ReenteringItems items = new ReenteringItems(() -> {
                    if (action == 0) data.updateParameter(key, owner, 9, 0, 0, 0);
                    else if (action == 1) helper.getLevel().setBlockAndUpdate(machine.getBlockPos(), machine.getBlockState()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
                    else { machine.enabled = false; machine.invalidatePorts(); }
                });
                items.setStackInSlot(1, filter.copyWithCount(5));
                machine.items[Direction.NORTH.ordinal()] = items;
                machine.invalidatePorts();
                var proxy = NeoForgeCapabilityProxy.INSTANCE;
                MachineTarget target = MachineTarget.relative(Direction.NORTH.ordinal(), 1,
                        proxy.itemSlotKey(machine, Direction.NORTH, items, 1), machine.getBlockState());
                check(data.updateItemFilter(key, owner, 1, 0, filter, server.registryAccess())
                                && data.updateTarget(key, owner, 1, 0, target, false)
                                && data.updateParameter(key, owner, 1, 0, 0, 1)
                                && data.updateParameter(key, owner, 9, 0, 0, 1),
                        "The reclaim task must bind a real relative item slot");
                var before = NeoForgeEmcSavedData.get(server).pending(owner);
                NeoForgeMachineTaskScheduler.clear(server);
                NeoForgeMachineTaskExecutor.runAll(server);
                check(items.reads >= 2 && items.extractions == 0 && items.getStackInSlot(1).getCount() == 5
                                && NeoForgeEmcSavedData.get(server).pending(owner).equals(before),
                        "A getter that changes configuration, orientation or capability availability must prevent extraction and credit");
            }
        } finally {
            data.updateParameter(key, owner, 9, 0, 0, 0);
            NeoForgeMachineTaskScheduler.clear(server);
            helper.setBlock(pos, Blocks.AIR);
        }
        helper.succeed();
    }

    private static final class ReenteringItems extends ItemStackHandler {
        private final Runnable reenter;
        int reads;
        int extractions;

        ReenteringItems(Runnable reenter) { super(3); this.reenter = reenter; }

        @Override public ItemStack getStackInSlot(int slot) {
            if (++reads == 2) reenter.run();
            return super.getStackInSlot(slot);
        }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!simulate) extractions++;
            return super.extractItem(slot, amount, simulate);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
