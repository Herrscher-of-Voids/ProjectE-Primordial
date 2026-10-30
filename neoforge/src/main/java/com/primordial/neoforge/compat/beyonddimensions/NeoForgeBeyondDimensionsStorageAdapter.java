/** Optional Beyond Dimensions virtual item/fluid catalogue on authorized adjacent networks. */
package com.primordial.neoforge.compat.beyonddimensions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import com.primordial.neoforge.transmutation.NeoForgeNetworkTransferBudget;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationInterfaceBlockEntity;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationNetworkPort;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationNetworkRegistry;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;

/** Bridges the optional API through reflection so installations without the mod stay link-safe. */
public final class NeoForgeBeyondDimensionsStorageAdapter {
    private static final String ROOT = "com.wintercogs.beyonddimensions.";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Object, Set<Port>> MOUNTS = new IdentityHashMap<>();
    private static Class<?> nodeClass;
    private static Constructor<?> itemKey, fluidKey, keyAmount, bucket;
    private static Method getNet, getStorage, getOwner, getManagers, getPlayers, onChange;
    private static Method copyStack, key, amount, keyTypeId, bucketAdd, bucketSize, bucketGet;

    private NeoForgeBeyondDimensionsStorageAdapter() {}

    public static synchronized void register() throws ReflectiveOperationException {
        if (NeoForgeTransmutationNetworkRegistry.registeredIds().contains("beyonddimensions")) return;
        nodeClass = Class.forName(ROOT + "common.block.entity.NetedBlockEntity");
        Class<?> net = Class.forName(ROOT + "api.dimensionnet.DimensionsNet");
        Class<?> storage = Class.forName(ROOT + "api.dimensionnet.UnifiedStorage");
        Class<?> keyType = Class.forName(ROOT + "api.storage.key.IStackKey");
        Class<?> pair = Class.forName(ROOT + "api.storage.key.KeyAmount");
        getNet = nodeClass.getMethod("getNet");
        getStorage = net.getMethod("getUnifiedStorage");
        getOwner = net.getMethod("getOwner");
        getManagers = net.getMethod("getManagers");
        getPlayers = net.getMethod("getPlayers");
        onChange = storage.getMethod("onChange");
        copyStack = keyType.getMethod("copyStack");
        key = pair.getMethod("key");
        amount = pair.getMethod("amount");
        keyAmount = pair.getConstructor(keyType, long.class);
        itemKey = Class.forName(ROOT + "api.storage.key.impl.ItemStackKey").getConstructor(ItemStack.class);
        fluidKey = Class.forName(ROOT + "api.storage.key.impl.FluidStackKey").getConstructor(FluidStack.class);
        Class<?> bucketClass = Class.forName(ROOT + "api.storage.handler.impl.AbstractUnorderedStackHandler$TypeBucket");
        bucket = bucketClass.getDeclaredConstructor();
        bucket.setAccessible(true);
        bucketAdd = bucketClass.getDeclaredMethod("add", keyType);
        bucketAdd.setAccessible(true);
        bucketSize = bucketClass.getMethod("size");
        bucketGet = bucketClass.getMethod("get", int.class);
        keyTypeId = keyType.getMethod("getTypeId");
        NeoForgeTransmutationNetworkRegistry.register("beyonddimensions", Port::new);
    }

    private static final class Port implements NeoForgeTransmutationNetworkPort {
        private final NeoForgeTransmutationInterfaceBlockEntity host;
        private final Set<Object> bound = Collections.newSetFromMap(new IdentityHashMap<>());
        private Map<Object, Long> snapshot = Map.of();

        private Port(NeoForgeTransmutationInterfaceBlockEntity host) { this.host = host; }

        @Override public void onLoad() { refresh(); }

        @Override public void tick() {
            if (!currentHost()) { unload(false); return; }
            if (((ServerLevel) host.getLevel()).getGameTime() % 20 == 0) refresh();
        }

        private void refresh() {
            if (!currentHost() || host.owner() == null || nodeClass == null) { unload(false); return; }
            try {
                Set<Object> next = authorizedStorages();

                Map<Object, Long> current = catalogue(host);
                Set<Object> changed = Collections.newSetFromMap(new IdentityHashMap<>());
                for (Object storage : new ArrayList<>(bound)) {
                    if (!next.contains(storage)) { detach(storage); changed.add(storage); }
                }
                for (Object storage : next) {
                    if (bound.add(storage)) {
                        MOUNTS.computeIfAbsent(storage, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(this);
                        changed.add(storage);
                    }
                }
                if (!snapshot.equals(current)) changed.addAll(bound);
                snapshot = current;
                changed.forEach(NeoForgeBeyondDimensionsStorageAdapter::notifyChange);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                unload(false);
                LOGGER.warn("[Primordial] Beyond Dimensions virtual storage mount failed", failure);
            }
        }

        private boolean currentHost() {
            if (host.isRemoved() || !(host.getLevel() instanceof ServerLevel level)
                    || !level.getServer().isSameThread()) return false;
            LevelChunk chunk = level.getChunkSource().getChunkNow(host.getBlockPos().getX() >> 4,
                    host.getBlockPos().getZ() >> 4);
            return chunk != null && chunk.getBlockEntity(host.getBlockPos(), LevelChunk.EntityCreationType.CHECK) == host;
        }

        private Set<Object> authorizedStorages() throws ReflectiveOperationException {
            Set<Object> result = Collections.newSetFromMap(new IdentityHashMap<>());
            if (!currentHost() || host.owner() == null) return result;
            ServerLevel level = (ServerLevel) host.getLevel();
            for (Direction side : Direction.values()) {
                var pos = host.getBlockPos().relative(side);
                LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                Object adjacent = chunk == null ? null : chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
                if (!nodeClass.isInstance(adjacent)) continue;
                Object net = getNet.invoke(adjacent);
                if (net != null && authorized(net, host.owner())) result.add(getStorage.invoke(net));
            }
            return result;
        }

        private boolean authorizedFor(Object storage) {
            try { return authorizedStorages().contains(storage); }
            catch (ReflectiveOperationException | RuntimeException unavailable) { return false; }
        }

        private void detach(Object storage) {
            bound.remove(storage);
            Set<Port> ports = MOUNTS.get(storage);
            if (ports != null) {
                ports.remove(this);
                if (ports.isEmpty()) MOUNTS.remove(storage);
            }
        }

        @Override public void unload(boolean removed) {
            for (Object storage : new ArrayList<>(bound)) {
                detach(storage);
                notifyChange(storage);
            }
            snapshot = Map.of();
        }
    }

    private static boolean authorized(Object net, UUID owner) throws ReflectiveOperationException {
        return owner.equals(getOwner.invoke(net)) || ((Set<?>) getManagers.invoke(net)).contains(owner)
                || ((Set<?>) getPlayers.invoke(net)).contains(owner);
    }

    private static Map<Object, Long> catalogue(NeoForgeTransmutationInterfaceBlockEntity host) {
        if (!(host.getLevel() instanceof ServerLevel level) || host.owner() == null) return Map.of();
        MinecraftServer server = level.getServer();
        UUID owner = host.owner();
        Map<Object, Long> result = new LinkedHashMap<>();
        long itemLimit = NeoForgeNetworkTransferBudget.remainingItems(server, owner);
        IKnowledgeProvider knowledge = ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(owner);
        if (knowledge != null && itemLimit > 0) {
            try {
                if (knowledge.hasFullKnowledge()) {
                    for (var item : BuiltInRegistries.ITEM) addItem(result, server, owner, new ItemStack(item), itemLimit);
                } else {
                    for (ItemInfo info : knowledge.getKnowledge())
                        addItem(result, server, owner, info.createStack(), itemLimit);
                }
            } catch (RuntimeException | LinkageError unavailable) {
                LOGGER.debug("Could not read all Beyond Dimensions item knowledge", unavailable);
            }
        }
        long fluidLimit = NeoForgeNetworkTransferBudget.remainingFluid(server, owner);
        if (fluidLimit > 0) for (var fluid : BuiltInRegistries.FLUID) {
            FluidStack sample = new FluidStack(fluid, 1);
            long available = NeoForgeResourceTrades.affordableFluid(server, owner, sample, fluidLimit);
            if (available > 0) result.put(fluidKey(sample), available);
        }
        return result;
    }

    private static void addItem(Map<Object, Long> result, MinecraftServer server, UUID owner,
                                ItemStack sample, long limit) {
        if (sample.isEmpty() || sample.is(net.minecraft.world.item.Items.AIR)) return;
        long available = NeoForgeResourceTrades.affordableItem(server, owner, sample, limit);
        if (available > 0) result.put(itemKey(sample), available);
    }

    private static List<Port> ports(Object storage) {
        Set<Port> mounted = MOUNTS.get(storage);
        if (mounted == null || mounted.isEmpty()) return List.of();
        Set<UUID> owners = new HashSet<>();
        List<Port> result = new ArrayList<>();
        for (Port port : mounted) {
            UUID owner = port.host.owner();
            if (owner != null && port.authorizedFor(storage) && owners.add(owner)) result.add(port);
        }
        return result;
    }

    /** Adds purchasable amounts only to the returned view; the mod's saved inventory remains untouched. */
    public static List<?> overlay(Object storage, List<?> actual) {
        List<Port> ports = ports(storage);
        if (ports.isEmpty()) return actual;
        try {
            Map<Object, Long> totals = new LinkedHashMap<>();
            for (Object pair : actual) totals.put(key.invoke(pair), ((Number) amount.invoke(pair)).longValue());
            for (Port port : ports) port.snapshot.forEach((id, count) -> totals.merge(id, count, NeoForgeBeyondDimensionsStorageAdapter::add));
            List<Object> view = new ArrayList<>(totals.size());
            for (var entry : totals.entrySet()) view.add(keyAmount.newInstance(entry.getKey(), entry.getValue()));
            return view;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Beyond Dimensions storage view API changed", failure);
        }
    }

    public static Optional<?> bucket(Object storage, ResourceLocation type, Optional<?> actual) {
        List<Port> ports = ports(storage);
        if (ports.isEmpty()) return actual;
        try {
            Object view = bucket.newInstance();
            if (actual.isPresent()) {
                Object current = actual.get();
                for (int index = 0; index < ((Number) bucketSize.invoke(current)).intValue(); index++)
                    bucketAdd.invoke(view, bucketGet.invoke(current, index));
            }
            for (Port port : ports) for (Object resource : port.snapshot.keySet())
                if (type.equals(keyTypeId.invoke(resource))) bucketAdd.invoke(view, resource);
            return ((Number) bucketSize.invoke(view)).intValue() == 0 ? actual : Optional.of(view);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Beyond Dimensions storage bucket API changed", failure);
        }
    }

    public static Object outputStack(Object storage, Object requested, Object actual) {
        if (requested == null || ports(storage).isEmpty()) return actual;
        try { return copyStack.invoke(requested); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static Object stack(Object storage, Object requested, Object actual) {
        List<Port> ports = ports(storage);
        if (ports.isEmpty()) return actual;
        try {
            long total = actual == null ? 0 : ((Number) amount.invoke(actual)).longValue();
            for (Port port : ports) total = add(total, port.snapshot.getOrDefault(requested, 0L));
            return keyAmount.newInstance(requested, total);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Actual dimension stock is extracted first; only the same-key shortfall is purchased. */
    public static Object extract(Object storage, Object requested, long wanted, boolean simulate, Object actual) {
        if (NeoForgeBeyondDimensionsRuleBridge.committing(storage)) try {
            long moved = actual == null ? 0 : ((Number) amount.invoke(actual)).longValue();
            long prepaid = NeoForgeBeyondDimensionsRuleBridge.consume(requested, Math.max(0, wanted - moved), simulate);
            return prepaid == 0 ? actual : keyAmount.newInstance(requested, moved + prepaid);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Consume authorized BD virtual stock", failure); }
        List<Port> ports = ports(storage);
        if (ports.isEmpty() || wanted <= 0) return actual;
        try {
            long moved = actual == null ? 0 : ((Number) amount.invoke(actual)).longValue();
            if (moved > 0 && !java.util.Objects.equals(key.invoke(actual), requested)) return actual;
            Object sample = copyStack.invoke(requested);
            for (Port port : ports) {
                if (moved >= wanted) break;
                ServerLevel level = (ServerLevel) port.host.getLevel();
                MinecraftServer server = level.getServer();
                UUID owner = port.host.owner();
                long missing = wanted - moved;
                if (sample instanceof ItemStack item) {
                    long requestedAmount = Math.min(missing,
                            NeoForgeNetworkTransferBudget.remainingItems(server, owner));
                    if (requestedAmount <= 0) continue;
                    long bought;
                    if (simulate) bought = NeoForgeResourceTrades.affordableItem(server, owner, item, requestedAmount);
                    else try (var reservation = NeoForgeNetworkTransferBudget.reserveItems(server, owner, requestedAmount)) {
                        bought = NeoForgeResourceTrades.purchaseItem(server, owner, item, reservation.amount(), units -> units);
                        reservation.complete(bought);
                    }
                    moved += bought;
                } else if (sample instanceof FluidStack fluid) {
                    if (!FluidStack.isSameFluidSameComponents(fluid, new FluidStack(fluid.getFluid(), 1))) break;
                    long requestedAmount = Math.min(missing,
                            NeoForgeNetworkTransferBudget.remainingFluid(server, owner));
                    if (requestedAmount <= 0) continue;
                    long bought;
                    if (simulate) bought = NeoForgeResourceTrades.affordableFluid(server, owner, fluid, requestedAmount);
                    else try (var reservation = NeoForgeNetworkTransferBudget.reserveFluid(server, owner, requestedAmount)) {
                        bought = NeoForgeResourceTrades.purchaseFluid(server, owner, fluid, reservation.amount(), units -> units);
                        reservation.complete(bought);
                    }
                    moved += bought;
                }
            }
            if (!simulate) for (Port port : ports) port.refresh();
            return moved == 0 ? actual : keyAmount.newInstance(requested, moved);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Beyond Dimensions extraction API changed", failure);
        }
    }

    record RuleFunding(MinecraftServer server, UUID owner, Object sample, long available, Port port) {}

    static RuleFunding ruleFunding(Object storage, Object requested) {
        List<Port> ports = ports(storage);
        if (ports.isEmpty() || requested == null) return null;
        try {
            Object sample = copyStack.invoke(requested);
            RuleFunding best = null;
            // Each rule transaction uses one payer; additional rule ingredients remain physical inventory.
            for (Port port : ports) {
                MinecraftServer server = ((ServerLevel) port.host.getLevel()).getServer();
                UUID owner = port.host.owner();
                long available = sample instanceof ItemStack item
                        ? NeoForgeResourceTrades.affordableItem(server, owner, item, NeoForgeNetworkTransferBudget.remainingItems(server, owner))
                        : sample instanceof FluidStack fluid && FluidStack.isSameFluidSameComponents(fluid, new FluidStack(fluid.getFluid(), 1))
                        ? NeoForgeResourceTrades.affordableFluid(server, owner, fluid, NeoForgeNetworkTransferBudget.remainingFluid(server, owner)) : 0;
                if (available > 0 && (best == null || best.available() < available)) best = new RuleFunding(server, owner, sample, available, port);
            }
            return best;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Quote BD rule transaction", failure); }
    }

    static long payRule(RuleFunding funding, Object storage, long needed, java.util.function.LongUnaryOperator transfer) {
        if (!validFunding(funding, storage)) return 0;
        boolean item = funding.sample() instanceof ItemStack;
        try (var reservation = item ? NeoForgeNetworkTransferBudget.reserveItems(funding.server(), funding.owner(), needed)
                : NeoForgeNetworkTransferBudget.reserveFluid(funding.server(), funding.owner(), needed)) {
            if (reservation.amount() != needed) return 0;
            java.util.function.LongUnaryOperator guarded = authorized -> validFunding(funding, storage) ? transfer.applyAsLong(authorized) : 0;
            long paid = item ? NeoForgeResourceTrades.purchaseItem(funding.server(), funding.owner(), (ItemStack) funding.sample(), needed, guarded)
                    : NeoForgeResourceTrades.purchaseFluid(funding.server(), funding.owner(), (FluidStack) funding.sample(), needed, guarded);
            reservation.complete(paid);
            return paid;
        }
    }

    private static boolean validFunding(RuleFunding funding, Object storage) {
        Port port = funding.port();
        return funding.owner().equals(port.host.owner()) && port.currentHost() && port.bound.contains(storage)
                && MOUNTS.getOrDefault(storage, Set.of()).contains(port) && port.authorizedFor(storage);
    }

    private static void notifyChange(Object storage) {
        if (onChange == null) return;
        try { onChange.invoke(storage); }
        catch (ReflectiveOperationException failure) {
            LOGGER.warn("[Primordial] Could not refresh a Beyond Dimensions catalogue", failure);
        }
    }

    private static Object itemKey(ItemStack sample) {
        try { return itemKey.newInstance(sample); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static Object fluidKey(FluidStack sample) {
        try { return fluidKey.newInstance(sample); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static long add(long first, long second) {
        return first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }
}
