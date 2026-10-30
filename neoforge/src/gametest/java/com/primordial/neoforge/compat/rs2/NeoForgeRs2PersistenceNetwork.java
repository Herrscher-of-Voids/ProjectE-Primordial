package com.primordial.neoforge.compat.rs2;

import static com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.check;

import com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.Fixture;
import com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.Network;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** A real controller discovers the restored production capability; this test never assigns a graph. */
public final class NeoForgeRs2PersistenceNetwork implements Network {
    private static final ItemResource DIAMOND = new ItemResource(Items.DIAMOND);
    private static final FluidResource WATER = new FluidResource(Fluids.WATER);

    public NeoForgeRs2PersistenceNetwork() {}
    @Override public String id() { return "rs2"; }
    @Override public void place(Fixture f, BlockPos pos) { f.place(pos.east(), "refinedstorage:creative_controller"); }
    private NetworkNode node(Fixture f, BlockPos pos) {
        var provider = f.level.getCapability(RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(), pos, Direction.EAST);
        return provider == null || provider.getContainers().size() != 1 ? null : provider.getContainers().iterator().next().getNode();
    }
    @Override public boolean ready(Fixture f, BlockPos pos) {
        NetworkNode host = node(f, pos), controller = node(f, pos.east());
        return host instanceof AbstractNetworkNode active && active.isActive() && controller != null
                && host.getNetwork() != null && host.getNetwork() == controller.getNetwork();
    }
    private StorageNetworkComponent storage(Fixture f, BlockPos pos) { return node(f, pos).getNetwork().getComponent(StorageNetworkComponent.class); }
    @Override public long item(Fixture f, BlockPos pos, long count, boolean simulate) {
        return storage(f, pos).extract(DIAMOND, count, simulate ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
    }
    @Override public long fluid(Fixture f, BlockPos pos, long count, boolean simulate) {
        return storage(f, pos).extract(WATER, count, simulate ? Action.SIMULATE : Action.EXECUTE, Actor.EMPTY);
    }
    @Override public long listedItems(Fixture f, BlockPos pos) { return storage(f, pos).get(DIAMOND); }
    @Override public long listedFluids(Fixture f, BlockPos pos) { return storage(f, pos).get(WATER); }
    @Override public void assertRestored(Fixture f, BlockPos pos) {
        check(ready(f, pos), "The saved RS2 capability must normally reconnect to its actual adjacent creative controller");
    }
    @Override public void assertNoSavedVirtualInventory(Fixture f, BlockPos pos) {
        String saved = f.host(pos).saveWithFullMetadata(f.level.registryAccess()).toString();
        check(!saved.contains("minecraft:diamond") && !saved.contains("minecraft:water"),
                "The actual saved RS2 interface must not turn a historical virtual catalogue into persistent native stock");
    }
}
