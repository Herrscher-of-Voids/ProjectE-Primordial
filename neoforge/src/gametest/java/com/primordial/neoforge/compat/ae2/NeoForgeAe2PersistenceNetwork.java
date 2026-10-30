package com.primordial.neoforge.compat.ae2;

import static com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.check;
import static com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.OWNER;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.Fixture;
import com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** Loaded only by the opt-in restart suite when the real AE2 mod is present. */
public final class NeoForgeAe2PersistenceNetwork implements Network {
    private static final AEItemKey DIAMOND = AEItemKey.of(Items.DIAMOND);
    private static final AEFluidKey WATER = AEFluidKey.of(Fluids.WATER);

    public NeoForgeAe2PersistenceNetwork() {}
    @Override public String id() { return "ae2"; }
    @Override public void place(Fixture f, BlockPos pos) { f.place(pos.east(), "ae2:creative_energy_cell"); }
    private IGridNode node(Fixture f, BlockPos pos, Direction side) { return GridHelper.getExposedNode(f.level, pos, side); }
    @Override public boolean ready(Fixture f, BlockPos pos) {
        IGridNode host = node(f, pos, Direction.EAST), cell = node(f, pos.east(), Direction.WEST);
        return host != null && cell != null && host.isActive() && host.getGrid() == cell.getGrid();
    }
    private MEStorage storage(Fixture f, BlockPos pos) { return node(f, pos, Direction.EAST).getGrid().getStorageService().getInventory(); }
    @Override public long item(Fixture f, BlockPos pos, long count, boolean simulate) {
        return storage(f, pos).extract(DIAMOND, count, simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.ofPlayer(f.player));
    }
    @Override public long fluid(Fixture f, BlockPos pos, long count, boolean simulate) {
        return storage(f, pos).extract(WATER, count, simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.ofPlayer(f.player));
    }
    @Override public long listedItems(Fixture f, BlockPos pos) { return storage(f, pos).getAvailableStacks().get(DIAMOND); }
    @Override public long listedFluids(Fixture f, BlockPos pos) { return storage(f, pos).getAvailableStacks().get(WATER); }
    @Override public void assertRestored(Fixture f, BlockPos pos) {
        IGridNode node = node(f, pos, Direction.EAST);
        check(OWNER.equals(node.getOwningPlayerProfileId()) && node.getConnectedSides().contains(Direction.EAST)
                        && node.getGrid().size() == 2 && node.getGrid().getPathingService().getUsedChannels() == 1,
                "A normally restored AE2 node must retain its owner UUID and its real adjacent, powered one-channel grid");
    }
    @Override public void assertNoSavedVirtualInventory(Fixture f, BlockPos pos) {
        var saved = f.host(pos).saveWithFullMetadata(f.level.registryAccess());
        check(saved.getCompound("NetworkPorts").getCompound("ae2").contains("aeNode")
                        && !saved.toString().contains("minecraft:diamond") && !saved.toString().contains("minecraft:water"),
                "The actual AE2 interface save must retain native node identity without serializing virtual item/fluid stock");
    }
}
