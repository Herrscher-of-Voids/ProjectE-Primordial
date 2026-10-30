/**
 * Optional AE2 virtual item/fluid storage for an owner's transmutation account.
 */
package com.primordial.neoforge.compat.ae2;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import com.primordial.neoforge.NeoForgeModRegistries;
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
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeAe2StorageAdapter {
    private NeoForgeAe2StorageAdapter() {}

    public static synchronized void register() {
        if (NeoForgeTransmutationNetworkRegistry.registeredIds().contains("ae2")) return;
        NeoForgeTransmutationNetworkRegistry.register("ae2", Port::new);
    }

    public static void registerCapabilityListener(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeAe2StorageAdapter::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                NeoForgeModRegistries.TRANSMUTATION_INTERFACE_ENTITY.get(), (host, ignored) -> new NodeHost(host));
    }

    /** A cached capability follows the current port across NBT reloads instead of retaining a destroyed node. */
    private record NodeHost(NeoForgeTransmutationInterfaceBlockEntity host) implements IInWorldGridNodeHost {
        @Override public IGridNode getGridNode(Direction side) {
            if (host.isRemoved()) return null;
            var port = host.networkPort("ae2");
            return port instanceof Port ae2 ? ae2.managed.getNode() : null;
        }
    }

    private static final class Port implements NeoForgeTransmutationNetworkPort, IStorageProvider {
        private final NeoForgeTransmutationInterfaceBlockEntity host;
        private final MEStorage storage;
        private final IManagedGridNode managed;
        private boolean hasSavedNode, firstTickPassed, closed;
        private CompoundTag loadedNodeData = new CompoundTag();

        Port(NeoForgeTransmutationInterfaceBlockEntity host) {
            this.host = host;
            storage = new Storage(this);
            managed = GridHelper.createManagedNode(host, new IGridNodeListener<NeoForgeTransmutationInterfaceBlockEntity>() {
                @Override public void onSaveChanges(NeoForgeTransmutationInterfaceBlockEntity owner, IGridNode node) {
                    owner.setChanged();
                }

                @Override public void onStateChanged(NeoForgeTransmutationInterfaceBlockEntity owner, IGridNode node, State state) {
                    if (!closed && managed.isReady()) IStorageProvider.requestUpdate(managed);
                }
            }).setTagName("aeNode").setInWorldNode(true)
                    .setExposedOnSides(EnumSet.allOf(Direction.class))
                    .setFlags(GridFlags.REQUIRE_CHANNEL)
                    .setIdlePowerUsage(1)
                    .setVisualRepresentation(host.getBlockState().getBlock())
                    .addService(IStorageProvider.class, this);
        }

        @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
            if (tag.contains("aeNode") && !tag.contains("aeNode", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid AE2 node compound");
            hasSavedNode = tag.contains("aeNode", Tag.TAG_COMPOUND);
            loadedNodeData = tag.copy();
            // An empty load would reset the newly assigned AE2 player ID during create().
            if (hasSavedNode) managed.loadFromNBT(tag);
        }

        @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
            if (managed.isReady()) managed.saveToNBT(tag);
            else tag.merge(loadedNodeData.copy());
        }

        @Override public void onLoad() {
            if (!(host.getLevel() instanceof ServerLevel)) return;
            GridHelper.onFirstTick(host, ignored -> {
                firstTickPassed = true;
                // Creation runs in tick(), inside the host's per-port failure isolation.
            });
        }

        @Override public void tick() {
            if (closed || (!firstTickPassed && !managed.isReady())) return;
            createWhenOwnerAvailable();
            if (managed.isReady() && host.getLevel() instanceof ServerLevel level
                    && level.getGameTime() % 20 == 0) IStorageProvider.requestUpdate(managed);
        }

        private void createWhenOwnerAvailable() {
            if (closed || managed.isReady() || host.isRemoved() || !(host.getLevel() instanceof ServerLevel level)
                    || !level.getServer().isSameThread()) return;
            UUID owner = host.owner();
            if (owner == null) return;
            if (!hasSavedNode) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
                if (player == null) return;
                managed.setOwningPlayer(player);
            }
            managed.create(level, host.getBlockPos());
        }

        @Override public void unload(boolean removed) {
            closed = true;
            managed.destroy();
        }

        @Override public void mountInventories(IStorageMounts mounts) {
            if (!closed && managed.isActive()) mounts.mount(storage, -100);
        }
    }

    private static final class Storage implements MEStorage {
        private final NeoForgeTransmutationInterfaceBlockEntity host;
        private final Port port;

        Storage(Port port) { this.port = port; this.host = port.host; }

        @Override public Component getDescription() {
            return Component.translatable("block.primordial.transmutation_interface");
        }

        @Override public void getAvailableStacks(KeyCounter out) {
            ServerContext context = context();
            if (context == null) return;
            addAvailableItems(out, context);
            addAvailableFluids(out, context);
        }

        private static void addAvailableItems(KeyCounter out, ServerContext context) {
            long limit = NeoForgeNetworkTransferBudget.remainingItems(context.server, context.owner);
            if (limit <= 0) return;
            IKnowledgeProvider knowledge = ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(context.owner);
            if (knowledge == null) return;
            Set<AEItemKey> seen = new HashSet<>();
            try {
                if (knowledge.hasFullKnowledge()) {
                    for (var item : BuiltInRegistries.ITEM) {
                        if (item != net.minecraft.world.item.Items.AIR)
                            addAvailable(out, context, new ItemStack(item), limit, seen);
                    }
                } else {
                    for (ItemInfo info : knowledge.getKnowledge()) {
                        ItemStack sample = info.createStack();
                        if (!sample.isEmpty()) addAvailable(out, context, sample, limit, seen);
                    }
                }
            } catch (RuntimeException | LinkageError unavailable) {
                // A partial knowledge snapshot is safer than exposing unverified generated items.
            }
        }

        private static void addAvailableFluids(KeyCounter out, ServerContext context) {
            long limit = NeoForgeNetworkTransferBudget.remainingFluid(context.server, context.owner);
            if (limit <= 0) return;
            for (var fluid : BuiltInRegistries.FLUID) {
                FluidStack sample = new FluidStack(fluid, 1);
                // The public quote enforces source IDs, knowledge/forget, stages and buy prices.
                long amount = NeoForgeResourceTrades.affordableFluid(context.server, context.owner, sample, limit);
                if (amount > 0) out.add(AEFluidKey.of(sample), amount);
            }
        }

        private static void addAvailable(KeyCounter out, ServerContext context, ItemStack sample,
                                         long limit, Set<AEItemKey> seen) {
            AEItemKey key = AEItemKey.of(sample);
            if (key == null || !seen.add(key)) return;
            long amount = NeoForgeResourceTrades.affordableItem(context.server, context.owner, sample, limit);
            if (amount > 0) out.add(key, amount);
        }

        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            ServerContext context = context();
            if (context == null || mode == null || source == null || amount <= 0) return 0;
            if (key instanceof AEFluidKey fluid) return extractFluid(context, fluid, amount, mode);
            if (!(key instanceof AEItemKey item)) return 0;
            ItemStack sample = item.toStack();
            if (sample.isEmpty()) return 0;
            long requested = Math.min(amount, NeoForgeNetworkTransferBudget.remainingItems(context.server, context.owner));
            if (requested <= 0) return 0;
            if (mode == Actionable.SIMULATE)
                return NeoForgeResourceTrades.affordableItem(context.server, context.owner, sample, requested);
            try (var reservation = NeoForgeNetworkTransferBudget.reserveItems(context.server, context.owner, requested)) {
                long moved = NeoForgeResourceTrades.purchaseItem(context.server, context.owner, sample,
                        reservation.amount(), units -> units);
                reservation.complete(moved);
                return moved;
            }
        }

        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            ServerContext context = context();
            if (context == null || mode == null || source == null || amount <= 0) return 0;
            if (key instanceof AEFluidKey fluid) return insertFluid(context, fluid, amount, mode);
            if (!(key instanceof AEItemKey item)) return 0;
            long requested = Math.min(Math.min(amount, Integer.MAX_VALUE),
                    NeoForgeNetworkTransferBudget.remainingItems(context.server, context.owner));
            if (requested <= 0) return 0;
            ItemStack sample = item.toStack();
            if (!sample.isEmpty()) sample.setCount((int) requested);
            if (sample.isEmpty()) return 0;
            if (mode == Actionable.SIMULATE)
                return NeoForgeResourceTrades.reclaimableItem(context.server, context.owner, sample, requested);
            try (var reservation = NeoForgeNetworkTransferBudget.reserveItems(context.server, context.owner, requested)) {
                long moved = NeoForgeResourceTrades.reclaimItem(context.server, context.owner, sample,
                        reservation.amount(), units -> units);
                reservation.complete(moved);
                return moved;
            }
        }

        private static long extractFluid(ServerContext context, AEFluidKey key, long amount, Actionable mode) {
            FluidStack sample = plainFluid(key);
            if (sample.isEmpty()) return 0;
            long requested = Math.min(amount, NeoForgeNetworkTransferBudget.remainingFluid(context.server, context.owner));
            if (requested <= 0) return 0;
            if (mode == Actionable.SIMULATE)
                return NeoForgeResourceTrades.affordableFluid(context.server, context.owner, sample, requested);
            try (var reservation = NeoForgeNetworkTransferBudget.reserveFluid(context.server, context.owner, requested)) {
                long moved = NeoForgeResourceTrades.purchaseFluid(context.server, context.owner, sample,
                        reservation.amount(), units -> units);
                reservation.complete(moved);
                return moved;
            }
        }

        private static long insertFluid(ServerContext context, AEFluidKey key, long amount, Actionable mode) {
            // Reclaim the actual variant at the base fluid price; its data is consumed, never generated.
            FluidStack sample = key.toStack(1);
            if (sample.isEmpty()) return 0;
            long requested = Math.min(amount, NeoForgeNetworkTransferBudget.remainingFluid(context.server, context.owner));
            if (requested <= 0) return 0;
            if (mode == Actionable.SIMULATE)
                return NeoForgeResourceTrades.reclaimableFluid(context.server, context.owner, sample, requested);
            try (var reservation = NeoForgeNetworkTransferBudget.reserveFluid(context.server, context.owner, requested)) {
                long moved = NeoForgeResourceTrades.reclaimFluid(context.server, context.owner, sample,
                        reservation.amount(), units -> units);
                reservation.complete(moved);
                return moved;
            }
        }

        private static FluidStack plainFluid(AEFluidKey key) {
            FluidStack sample = key.toStack(1);
            // Knowledge/prices identify only a source fluid, never an arbitrary component variant.
            // Compare the actual stack: AE2 19.2.18's hasComponents() reports the inverse.
            return !sample.isEmpty() && FluidStack.isSameFluidSameComponents(sample, new FluidStack(sample.getFluid(), 1))
                    ? sample : FluidStack.EMPTY;
        }

        private ServerContext context() {
            if (port.closed || host.isRemoved() || host.owner() == null || !(host.getLevel() instanceof ServerLevel level)
                    || !level.getServer().isSameThread()) return null;
            if (!port.managed.isActive()) return null;
            LevelChunk chunk = level.getChunkSource().getChunkNow(host.getBlockPos().getX() >> 4, host.getBlockPos().getZ() >> 4);
            if (chunk == null || chunk.getBlockEntity(host.getBlockPos(), LevelChunk.EntityCreationType.CHECK) != host) return null;
            return new ServerContext(level.getServer(), host.owner());
        }
    }

    private record ServerContext(MinecraftServer server, UUID owner) {}
}
