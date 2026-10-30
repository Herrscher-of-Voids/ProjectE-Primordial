package com.primordial.neoforge.compat.beyonddimensions;

import static com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.OWNER;
import static com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.check;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.Fixture;
import com.primordial.neoforge.compat.NeoForgeNetworkPersistenceGameTests.Network;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Reflection is restricted to the optional mod's public API, never fields or private node connections. */
public final class NeoForgeBeyondDimensionsPersistenceNetwork implements Network {
    private static final String ROOT = "com.wintercogs.beyonddimensions.";
    private final Class<?> netType, keyType;
    private final Object item, fluid;
    private final Method amount, pairKey;

    public NeoForgeBeyondDimensionsPersistenceNetwork() throws ReflectiveOperationException {
        netType = Class.forName(ROOT + "api.dimensionnet.DimensionsNet");
        keyType = Class.forName(ROOT + "api.storage.key.IStackKey");
        Class<?> pair = Class.forName(ROOT + "api.storage.key.KeyAmount");
        amount = pair.getMethod("amount");
        pairKey = pair.getMethod("key");
        item = Class.forName(ROOT + "api.storage.key.impl.ItemStackKey").getConstructor(ItemStack.class).newInstance(new ItemStack(Items.DIAMOND));
        fluid = Class.forName(ROOT + "api.storage.key.impl.FluidStackKey").getConstructor(FluidStack.class).newInstance(new FluidStack(Fluids.WATER, 1));
    }
    @Override public String id() { return "beyonddimensions"; }
    @Override public void place(Fixture f, BlockPos pos) {
        try {
            Object network = netType.getMethod("createNewNetForPlayer", Player.class, long.class, int.class)
                    .invoke(null, f.networkOwner, 1_000_000L, 1000);
            call(network, "addPlayer", new Class<?>[]{UUID.class}, OWNER);
            f.place(pos.east(), "beyonddimensions:net_pathway");
            call(f.level.getBlockEntity(pos.east()), "setNetId", new Class<?>[]{int.class}, ((Number) call(network, "getId")).intValue());
        } catch (ReflectiveOperationException failure) { throw api(failure); }
    }
    private Object network(Fixture f, BlockPos pos) throws ReflectiveOperationException {
        Object pathway = f.level.getBlockEntity(pos.east());
        return pathway == null ? null : call(pathway, "getNet");
    }
    private Object storage(Fixture f, BlockPos pos) throws ReflectiveOperationException { return call(network(f, pos), "getUnifiedStorage"); }
    @Override public boolean ready(Fixture f, BlockPos pos) {
        try { return network(f, pos) != null && (f.phase.equals("verify") || listedItems(f, pos) > 0); }
        catch (ReflectiveOperationException failure) { throw api(failure); }
    }
    private long extract(Fixture f, BlockPos pos, Object key, long count, boolean simulate) {
        try {
            Object pair = call(storage(f, pos), "extract", new Class<?>[]{keyType, long.class, boolean.class, boolean.class}, key, count, simulate, false);
            return ((Number) amount.invoke(pair)).longValue();
        } catch (ReflectiveOperationException failure) { throw api(failure); }
    }
    @Override public long item(Fixture f, BlockPos pos, long count, boolean simulate) { return extract(f, pos, item, count, simulate); }
    @Override public long fluid(Fixture f, BlockPos pos, long count, boolean simulate) { return extract(f, pos, fluid, count, simulate); }
    private long listed(Fixture f, BlockPos pos, Object key) {
        try {
            for (Object pair : (List<?>) call(storage(f, pos), "getStorage"))
                if (key.equals(pairKey.invoke(pair))) return ((Number) amount.invoke(pair)).longValue();
            return 0;
        } catch (ReflectiveOperationException failure) { throw api(failure); }
    }
    @Override public long listedItems(Fixture f, BlockPos pos) { return listed(f, pos, item); }
    @Override public long listedFluids(Fixture f, BlockPos pos) { return listed(f, pos, fluid); }
    @Override public void assertRestored(Fixture f, BlockPos pos) {
        try {
            Object net = network(f, pos);
            check(f.networkOwner.getUUID().equals(call(net, "getOwner")), "The true Beyond Dimensions network owner must persist independently of the interface owner");
            check(((Set<?>) call(net, "getPlayers")).contains(OWNER) != f.phase.equals("verify"),
                    "The actual Beyond Dimensions member set must load its current persisted grant/revocation");
        } catch (ReflectiveOperationException failure) { throw api(failure); }
    }
    @Override public void revoke(Fixture f, BlockPos pos) {
        try {
            var before = f.balance();
            call(network(f, pos), "removePlayer", new Class<?>[]{UUID.class}, OWNER);
            check(listedItems(f, pos) == 0 && listedFluids(f, pos) == 0 && item(f, pos, 1, false) == 0 && f.balance().equals(before),
                    "Native membership revocation must block virtual purchases while owner knowledge and funding remain valid");
        } catch (ReflectiveOperationException failure) { throw api(failure); }
    }
    @Override public void assertRevoked(Fixture f, BlockPos pos) { assertRestored(f, pos); }
    @Override public void assertNoSavedVirtualInventory(Fixture f, BlockPos pos) {
        try {
            String saved = call(storage(f, pos), "serializeNBT", new Class<?>[]{HolderLookup.Provider.class}, f.level.registryAccess()).toString();
            check(!saved.contains("minecraft:diamond") && !saved.contains("minecraft:water"),
                    "Beyond Dimensions must persist only its physical inventory and never the overlaid virtual catalogue");
        } catch (ReflectiveOperationException failure) { throw api(failure); }
    }
    private static Object call(Object target, String method, Class<?>[] parameters, Object... arguments) throws ReflectiveOperationException {
        return target.getClass().getMethod(method, parameters).invoke(target, arguments);
    }
    private static Object call(Object target, String method) throws ReflectiveOperationException { return call(target, method, new Class<?>[0]); }
    private static IllegalStateException api(ReflectiveOperationException failure) { return new IllegalStateException("Beyond Dimensions public persistence API changed", failure); }
}
