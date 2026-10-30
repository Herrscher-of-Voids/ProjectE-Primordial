/** Optional RS 2 native item/fluid storage backed by the placing owner's EMC account. */
package com.primordial.neoforge.compat.rs2;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import com.primordial.neoforge.transmutation.NeoForgeNetworkTransferBudget;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationInterfaceBlockEntity;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationNetworkPort;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationNetworkRegistry;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageProvider;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.api.storage.composite.CompositeAwareChild;
import com.refinedmods.refinedstorage.api.storage.composite.ParentComposite;
import com.refinedmods.refinedstorage.api.storage.composite.PriorityProvider;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.ConnectionSink;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeRs2StorageAdapter {
    private NeoForgeRs2StorageAdapter() {}

    public static synchronized void register() {
        if (!NeoForgeTransmutationNetworkRegistry.registeredIds().contains("refinedstorage"))
            NeoForgeTransmutationNetworkRegistry.register("refinedstorage", Port::new);
    }

    public static void registerCapabilityListener(IEventBus bus) {
        bus.addListener(NeoForgeRs2StorageAdapter::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
                NeoForgeModRegistries.TRANSMUTATION_INTERFACE_ENTITY.get(), (host, side) -> new Provider(host));
    }

    /** RS caches capabilities; resolve the current port after every suspend or NBT replacement. */
    private record Provider(NeoForgeTransmutationInterfaceBlockEntity host) implements NetworkNodeContainerProvider {
        private Port port() {
            if (host.isRemoved()) return null;
            var candidate = host.networkPort("refinedstorage");
            return candidate instanceof Port port && !port.closed ? port : null;
        }
        @Override public Set<InWorldNetworkNodeContainer> getContainers() {
            Port port = port();
            return port == null ? Set.of() : port.provider.getContainers();
        }
        @Override public boolean canBuild(ServerPlayer player) {
            Port port = port();
            return port != null && port.provider.canBuild(player);
        }
        @Override public void addContainer(InWorldNetworkNodeContainer container) {
            throw new UnsupportedOperationException("The transmutation interface owns its RS node");
        }
    }

    private static final class Port implements NeoForgeTransmutationNetworkPort {
        private final NeoForgeTransmutationInterfaceBlockEntity host;
        private final Source storage;
        private final Node node;
        private final NetworkNodeContainerProvider provider;
        private boolean initialized, closed;
        private CompoundTag saved = new CompoundTag();

        Port(NeoForgeTransmutationInterfaceBlockEntity host) {
            this.host = host;
            storage = new Source(this);
            node = new Node();
            provider = RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
            var delegate = RefinedStorageApi.INSTANCE.createNetworkNodeContainer(host, node)
                    .name("primordial_transmutation_interface").build();
            provider.addContainer(new Container(this, delegate));
        }

        @Override public void load(CompoundTag tag, HolderLookup.Provider registries) { saved = tag.copy(); }
        @Override public void save(CompoundTag tag, HolderLookup.Provider registries) { tag.merge(saved.copy()); }
        @Override public void tick() {
            if (closed || !(host.getLevel() instanceof ServerLevel level)) return;
            // Publish the port before RS queues discovery; capability probes must see this same node.
            if (!initialized) {
                initialized = true;
                provider.initialize(level, host::setChanged);
            }
            boolean active = currentHost() && host.owner() != null && node.getNetwork() != null
                    && (!RefinedStorageApi.INSTANCE.isEnergyRequired()
                    || node.getNetwork().getComponent(EnergyNetworkComponent.class).getStored() >= node.getEnergyUsage());
            if (active != node.isActive()) node.setActive(active);
            storage.refresh();
            node.doWork();
        }

        private boolean currentHost() {
            if (closed || host.isRemoved() || !(host.getLevel() instanceof ServerLevel level)
                    || !level.getServer().isSameThread()) return false;
            LevelChunk chunk = level.getChunkSource().getChunkNow(host.getBlockPos().getX() >> 4, host.getBlockPos().getZ() >> 4);
            return chunk != null && chunk.getBlockEntity(host.getBlockPos(), LevelChunk.EntityCreationType.CHECK) == host;
        }

        @Override public void unload(boolean removed) {
            if (closed) return;
            closed = true;
            // Notify parents before native removeSource reads getAll(), so no phantom catalogue survives.
            storage.refresh();
            node.setActive(false);
            provider.remove(host.getLevel());
        }

        private final class Node extends AbstractNetworkNode implements StorageProvider {
            @Override public Storage getStorage() { return storage; }
            @Override public long getEnergyUsage() { return 1; }
        }
    }

    /** RS initialization is queued: cancelled ports must not be resurrected on the same block entity. */
    private record Container(Port port, InWorldNetworkNodeContainer delegate) implements InWorldNetworkNodeContainer {
        @Override public NetworkNode getNode() { return delegate.getNode(); }
        @Override public Object createKey() { return delegate.createKey(); }
        @Override public int getPriority() { return delegate.getPriority(); }
        @Override public BlockState getBlockState() { return delegate.getBlockState(); }
        @Override public boolean isRemoved() { return port.closed || delegate.isRemoved(); }
        @Override public GlobalPos getPosition() { return delegate.getPosition(); }
        @Override public BlockPos getLocalPosition() { return delegate.getLocalPosition(); }
        @Override public String getName() { return delegate.getName(); }
        @Override public void addOutgoingConnections(ConnectionSink sink) { delegate.addOutgoingConnections(sink); }
        @Override public boolean canAcceptIncomingConnection(Direction side, BlockState state) {
            return delegate.canAcceptIncomingConnection(side, state);
        }
    }

    private static final class Source implements CompositeAwareChild, PriorityProvider {
        private final Port port;
        private final Set<ParentComposite> parents = new HashSet<>();
        private Map<ResourceKey, Long> snapshot = Map.of();

        Source(Port port) { this.port = port; }
        @Override public int getInsertPriority() { return -100; }
        @Override public int getExtractPriority() { return -100; }
        @Override public void onAddedIntoComposite(ParentComposite parent) { parents.add(parent); }
        @Override public void onRemovedFromComposite(ParentComposite parent) { parents.remove(parent); }
        @Override public boolean contains(Storage storage) { return storage == this; }
        @Override public Collection<ResourceAmount> getAll() {
            refresh();
            return snapshot.entrySet().stream().map(entry -> new ResourceAmount(entry.getKey(), entry.getValue())).toList();
        }
        @Override public long getStored() {
            refresh();
            return snapshot.values().stream().reduce(0L, (a, b) -> a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b);
        }

        private void refresh() {
            Map<ResourceKey, Long> next = catalogue();
            Set<ResourceKey> keys = new HashSet<>(snapshot.keySet());
            keys.addAll(next.keySet());
            Map<ResourceKey, Long> previous = snapshot;
            snapshot = next;
            for (ResourceKey key : keys) {
                long delta = next.getOrDefault(key, 0L) - previous.getOrDefault(key, 0L);
                if (delta != 0) for (ParentComposite parent : Set.copyOf(parents)) {
                    if (delta > 0) parent.addToCache(key, delta);
                    else parent.removeFromCache(key, -delta);
                }
            }
        }

        private Map<ResourceKey, Long> catalogue() {
            Context context = context();
            if (context == null) return Map.of();
            Map<ResourceKey, Long> result = new HashMap<>();
            long itemLimit = NeoForgeNetworkTransferBudget.remainingItems(context.server, context.owner);
            IKnowledgeProvider knowledge = ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(context.owner);
            if (knowledge != null && itemLimit > 0) try {
                if (knowledge.hasFullKnowledge()) {
                    for (var item : BuiltInRegistries.ITEM) addItem(result, context, new ItemStack(item), itemLimit);
                } else {
                    for (ItemInfo info : knowledge.getKnowledge()) addItem(result, context, info.createStack(), itemLimit);
                }
            } catch (RuntimeException | LinkageError unavailable) {
                // Never advertise resources whose knowledge could not be verified.
            }
            long fluidLimit = NeoForgeNetworkTransferBudget.remainingFluid(context.server, context.owner);
            if (fluidLimit > 0) for (var fluid : BuiltInRegistries.FLUID) {
                long amount = NeoForgeResourceTrades.affordableFluid(context.server, context.owner, new FluidStack(fluid, 1), fluidLimit);
                if (amount > 0) result.put(new FluidResource(fluid), amount);
            }
            return result;
        }

        private static void addItem(Map<ResourceKey, Long> result, Context context, ItemStack sample, long limit) {
            if (sample.isEmpty()) return;
            long amount = NeoForgeResourceTrades.affordableItem(context.server, context.owner, sample, limit);
            if (amount > 0) result.put(ItemResource.ofItemStack(sample), amount);
        }

        @Override public Amount compositeInsert(ResourceKey key, long amount, Action action, Actor actor) {
            return new Amount(insert(key, amount, action, actor), 0);
        }
        @Override public Amount compositeExtract(ResourceKey key, long amount, Action action, Actor actor) {
            return new Amount(extract(key, amount, action, actor), 0);
        }
        @Override public long insert(ResourceKey key, long amount, Action action, Actor actor) {
            return transfer(key, amount, action, actor, false);
        }
        @Override public long extract(ResourceKey key, long amount, Action action, Actor actor) {
            return transfer(key, amount, action, actor, true);
        }

        private long transfer(ResourceKey key, long amount, Action action, Actor actor, boolean purchase) {
            Context context = context();
            if (context == null || action == null || actor == null || amount <= 0) return 0;
            long moved;
            if (key instanceof ItemResource item) {
                long requested = Math.min(Math.min(amount, Integer.MAX_VALUE),
                        NeoForgeNetworkTransferBudget.remainingItems(context.server, context.owner));
                if (requested <= 0) return 0;
                ItemStack sample = item.toItemStack(purchase ? 1 : requested);
                if (sample.isEmpty()) return 0;
                if (action == Action.SIMULATE) return purchase
                        ? NeoForgeResourceTrades.affordableItem(context.server, context.owner, sample, requested)
                        : NeoForgeResourceTrades.reclaimableItem(context.server, context.owner, sample, requested);
                try (var reservation = NeoForgeNetworkTransferBudget.reserveItems(context.server, context.owner, requested)) {
                    moved = purchase
                            ? NeoForgeResourceTrades.purchaseItem(context.server, context.owner, sample, reservation.amount(), units -> units)
                            : NeoForgeResourceTrades.reclaimItem(context.server, context.owner, sample, reservation.amount(), units -> units);
                    reservation.complete(moved);
                }
            } else if (key instanceof FluidResource fluid) {
                FluidStack sample = new FluidStack(fluid.fluid(), 1);
                sample.applyComponents(fluid.components());
                if (sample.isEmpty() || (purchase && !FluidStack.isSameFluidSameComponents(sample, new FluidStack(fluid.fluid(), 1))))
                    return 0;
                long requested = Math.min(amount, NeoForgeNetworkTransferBudget.remainingFluid(context.server, context.owner));
                if (requested <= 0) return 0;
                if (action == Action.SIMULATE) return purchase
                        ? NeoForgeResourceTrades.affordableFluid(context.server, context.owner, sample, requested)
                        : NeoForgeResourceTrades.reclaimableFluid(context.server, context.owner, sample, requested);
                try (var reservation = NeoForgeNetworkTransferBudget.reserveFluid(context.server, context.owner, requested)) {
                    moved = purchase
                            ? NeoForgeResourceTrades.purchaseFluid(context.server, context.owner, sample, reservation.amount(), units -> units)
                            : NeoForgeResourceTrades.reclaimFluid(context.server, context.owner, sample, reservation.amount(), units -> units);
                    reservation.complete(moved);
                }
            } else return 0;
            // EMC and the shared transfer allowance can change every advertised resource, not just this key.
            refresh();
            return moved;
        }

        private Context context() {
            if (!port.currentHost() || port.host.owner() == null || !port.node.isActive() || port.node.getNetwork() == null)
                return null;
            return new Context(((ServerLevel) port.host.getLevel()).getServer(), port.host.owner());
        }
    }

    private record Context(MinecraftServer server, UUID owner) {}
}
