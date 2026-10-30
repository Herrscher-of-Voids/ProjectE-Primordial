package com.primordial.neoforge.machine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.annotation.Nullable;

import com.primordial.data.MachineTarget;
import com.primordial.util.RelativeMachineSides;
import com.primordial.neoforge.compat.NeoForgeMachineChemicalBridge;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server-authoritative machine port queries and target bindings. */
public final class NeoForgeMachineConfiguratorPayloads {
    private static final int MAX_REQUEST_ID = 65_535;
    private static final int MAX_PAGE = 1_000_000;

    private NeoForgeMachineConfiguratorPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Query.TYPE, Query.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleQuery(context, payload)));
        registrar.playToServer(Bind.TYPE, Bind.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleBind(context, payload)));
        registrar.playToServer(BindBatch.TYPE, BindBatch.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleBindBatch(context, payload)));
        registrar.playToServer(UpdateParameters.TYPE, UpdateParameters.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleParameterUpdate(context, payload)));
        registrar.playToServer(ItemFilter.TYPE, ItemFilter.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleItemFilter(context, payload)));
        registrar.playToServer(FluidFilter.TYPE, FluidFilter.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleFluidFilter(context, payload)));
        registrar.playToServer(ChemicalFilter.TYPE, ChemicalFilter.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> handleChemicalFilter(context, payload)));
        registrar.playToClient(Ports.TYPE, Ports.STREAM_CODEC, NeoForgeMachineConfiguratorPayloads::handlePorts);
        registrar.playToClient(BindResult.TYPE, BindResult.STREAM_CODEC,
                NeoForgeMachineConfiguratorPayloads::handleBindResult);
        registrar.playToClient(Snapshot.TYPE, Snapshot.STREAM_CODEC,
                NeoForgeMachineConfiguratorPayloads::handleSnapshot);
        registrar.playToClient(EnergyState.TYPE, EnergyState.STREAM_CODEC,
                NeoForgeMachineConfiguratorPayloads::handleEnergyState);
    }

    public record Query(int containerId, int requestId, int section, int sample, int side, int page,
                        boolean gasMode, int referenceFront)
            implements CustomPacketPayload {
        public Query(int containerId, int requestId, int section, int sample, int side, int page) {
            this(containerId, requestId, section, sample, side, page, false);
        }
        public Query(int containerId, int requestId, int section, int sample, int side, int page, boolean gasMode) {
            this(containerId, requestId, section, sample, side, page, gasMode, Direction.NORTH.get3DDataValue());
        }
        public static final Type<Query> TYPE = new Type<>(id("machine_config_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Query> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.requestId);
                    buf.writeVarInt(msg.section); buf.writeVarInt(msg.sample); buf.writeVarInt(msg.side);
                    buf.writeVarInt(msg.page); buf.writeBoolean(msg.gasMode); buf.writeByte(msg.referenceFront); },
                buf -> new Query(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readByte()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Bind(int containerId, int requestId, int section, int sample, int side,
                       int page, int index, String key, boolean append, boolean clear, boolean gasMode)
            implements CustomPacketPayload {
        public Bind(int containerId, int requestId, int section, int sample, int side,
                    int page, int index, String key, boolean append, boolean clear) {
            this(containerId, requestId, section, sample, side, page, index, key, append, clear, false);
        }
        public static final Type<Bind> TYPE = new Type<>(id("machine_config_bind"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Bind> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.requestId);
                    buf.writeVarInt(msg.section); buf.writeVarInt(msg.sample); buf.writeVarInt(msg.side);
                    buf.writeVarInt(msg.page); buf.writeVarInt(msg.index);
                    buf.writeUtf(msg.key == null ? "" : msg.key, MachineTarget.MAX_KEY_LENGTH);
                    buf.writeBoolean(msg.append); buf.writeBoolean(msg.clear); buf.writeBoolean(msg.gasMode); },
                buf -> new Bind(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readUtf(MachineTarget.MAX_KEY_LENGTH), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record PortTarget(int side, int index, String key) {
        public PortTarget { key = key == null ? "" : key; }
    }

    public record BindBatch(int containerId, int requestId, int section, int sample, List<PortTarget> targets,
                            boolean gasMode, int referenceFront)
            implements CustomPacketPayload {
        public BindBatch(int containerId, int requestId, int section, int sample, List<PortTarget> targets) {
            this(containerId, requestId, section, sample, targets, false);
        }
        public BindBatch(int containerId, int requestId, int section, int sample, List<PortTarget> targets, boolean gasMode) {
            this(containerId, requestId, section, sample, targets, gasMode, Direction.NORTH.get3DDataValue());
        }
        public static final Type<BindBatch> TYPE = new Type<>(id("machine_config_bind_batch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BindBatch> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.requestId);
                    buf.writeVarInt(msg.section); buf.writeVarInt(msg.sample); buf.writeBoolean(msg.gasMode);
                    buf.writeByte(msg.referenceFront);
                    buf.writeVarInt(msg.targets.size());
                    for (PortTarget target : msg.targets) {
                        buf.writeVarInt(target.side()); buf.writeVarInt(target.index());
                        buf.writeUtf(target.key(), MachineTarget.MAX_KEY_LENGTH);
                    }
                },
                buf -> {
                    int containerId = buf.readVarInt(), requestId = buf.readVarInt();
                    int section = buf.readVarInt(), sample = buf.readVarInt();
                    boolean gasMode = buf.readBoolean(); int referenceFront = buf.readByte(), count = buf.readVarInt();
                    if (count < 1 || count > 512) throw new io.netty.handler.codec.DecoderException("Invalid machine target batch size");
                    List<PortTarget> targets = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) targets.add(new PortTarget(
                            buf.readVarInt(), buf.readVarInt(), buf.readUtf(MachineTarget.MAX_KEY_LENGTH)));
                    return new BindBatch(containerId, requestId, section, sample, targets, gasMode, referenceFront);
                });
        public BindBatch { targets = List.copyOf(targets); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateParameters(int containerId, int requestId, int section, int sample, int field, int value)
            implements CustomPacketPayload {
        public static final Type<UpdateParameters> TYPE = new Type<>(id("machine_config_update_parameters"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateParameters> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.requestId);
                    buf.writeVarInt(msg.section); buf.writeVarInt(msg.sample); buf.writeVarInt(msg.field);
                    buf.writeVarInt(msg.value); },
                buf -> new UpdateParameters(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ItemFilter(int containerId, int section, int sample, ItemStack filter)
            implements CustomPacketPayload {
        public static final Type<ItemFilter> TYPE = new Type<>(id("machine_config_item_filter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemFilter> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.section);
                    buf.writeVarInt(msg.sample); ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.filter); },
                buf -> new ItemFilter(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
        public ItemFilter { filter = filter == null ? ItemStack.EMPTY : filter.copy(); }
        @Override public ItemStack filter() { return filter.copy(); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record FluidFilter(int containerId, int section, int sample, FluidStack filter)
            implements CustomPacketPayload {
        public static final Type<FluidFilter> TYPE = new Type<>(id("machine_config_fluid_filter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FluidFilter> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.section);
                    buf.writeVarInt(msg.sample); FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.filter); },
                buf -> new FluidFilter(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        FluidStack.OPTIONAL_STREAM_CODEC.decode(buf)));
        public FluidFilter { filter = filter == null ? FluidStack.EMPTY : filter.copy(); }
        @Override public FluidStack filter() { return filter.copy(); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ChemicalFilter(int containerId, int section, int sample, @Nullable ResourceLocation chemical)
            implements CustomPacketPayload {
        public static final Type<ChemicalFilter> TYPE = new Type<>(id("machine_config_chemical_filter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChemicalFilter> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.section);
                    buf.writeVarInt(msg.sample); buf.writeBoolean(msg.chemical != null);
                    if (msg.chemical != null) buf.writeResourceLocation(msg.chemical); },
                buf -> new ChemicalFilter(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean() ? buf.readResourceLocation() : null));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Port(int index, String key, net.minecraft.world.item.ItemStack item, FluidStack fluid,
                       @Nullable ResourceLocation chemical, int amount, int capacity,
                       @Nullable ResourceLocation resource, long resourceAmount, long resourceCapacity) {
        public Port(int index, String key, net.minecraft.world.item.ItemStack item, FluidStack fluid,
                    @Nullable ResourceLocation chemical, int amount, int capacity) {
            this(index, key, item, fluid, chemical, amount, capacity, null, 0, 0);
        }
        public Port(int index, String key, net.minecraft.world.item.ItemStack item, FluidStack fluid,
                    int amount, int capacity) {
            this(index, key, item, fluid, null, amount, capacity);
        }
        public Port {
            key = key == null ? "" : key;
            item = item == null ? net.minecraft.world.item.ItemStack.EMPTY : item.copy();
            fluid = fluid == null ? FluidStack.EMPTY : fluid.copy();
            amount = Math.max(0, amount);
            capacity = Math.max(0, capacity);
            resourceAmount = Math.max(0, resourceAmount);
            resourceCapacity = Math.max(0, resourceCapacity);
        }
        @Override public net.minecraft.world.item.ItemStack item() { return item.copy(); }
        @Override public FluidStack fluid() { return fluid.copy(); }

        private static void encode(RegistryFriendlyByteBuf buf, Port port) {
            buf.writeVarInt(port.index);
            buf.writeUtf(port.key, MachineTarget.MAX_KEY_LENGTH);
            net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, port.item);
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, port.fluid);
            buf.writeBoolean(port.chemical != null);
            if (port.chemical != null) buf.writeResourceLocation(port.chemical);
            buf.writeVarInt(port.amount);
            buf.writeVarInt(port.capacity);
            buf.writeBoolean(port.resource != null);
            if (port.resource != null) buf.writeResourceLocation(port.resource);
            buf.writeVarLong(port.resourceAmount);
            buf.writeVarLong(port.resourceCapacity);
        }

        private static Port decode(RegistryFriendlyByteBuf buf) {
            return new Port(buf.readVarInt(), buf.readUtf(MachineTarget.MAX_KEY_LENGTH),
                    net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    FluidStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readBoolean() ? buf.readResourceLocation() : null, buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean() ? buf.readResourceLocation() : null, buf.readVarLong(), buf.readVarLong());
        }
    }

    public record Ports(int containerId, int requestId, String machineKey, int section, int sample,
                        int side, boolean valid, String proxyId, int page, int total, List<Port> ports,
                        boolean gasMode)
            implements CustomPacketPayload {
        public Ports(int containerId, int requestId, String machineKey, int section, int sample,
                     int side, boolean valid, String proxyId, int page, int total, List<Port> ports) {
            this(containerId, requestId, machineKey, section, sample, side, valid, proxyId, page, total, ports, false);
        }
        public static final Type<Ports> TYPE = new Type<>(id("machine_config_ports"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ports> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.requestId);
                    buf.writeUtf(msg.machineKey, 256); buf.writeVarInt(msg.section); buf.writeVarInt(msg.sample);
                    buf.writeVarInt(msg.side); buf.writeBoolean(msg.valid); buf.writeUtf(msg.proxyId, 128);
                    buf.writeVarInt(msg.page); buf.writeVarInt(msg.total); buf.writeByte(msg.ports.size());
                    buf.writeBoolean(msg.gasMode);
                    msg.ports.forEach(port -> Port.encode(buf, port));
                },
                buf -> {
                    int containerId = buf.readVarInt(), requestId = buf.readVarInt();
                    String machineKey = buf.readUtf(256);
                    int section = buf.readVarInt(), sample = buf.readVarInt(), side = buf.readVarInt();
                    boolean valid = buf.readBoolean(); String proxyId = buf.readUtf(128);
                    int page = buf.readVarInt(), total = buf.readVarInt(), count = buf.readUnsignedByte();
                    boolean gasMode = buf.readBoolean();
                    if (count > NeoForgeMachinePortPage.PAGE_SIZE || page < 0 || total < 0)
                        throw new io.netty.handler.codec.DecoderException("Invalid machine port page");
                    java.util.ArrayList<Port> ports = new java.util.ArrayList<>(count);
                    for (int i = 0; i < count; i++) ports.add(Port.decode(buf));
                    return new Ports(containerId, requestId, machineKey, section, sample, side,
                            valid, proxyId, page, total, ports, gasMode);
                });
        public Ports { ports = List.copyOf(ports); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BindResult(int containerId, int requestId, boolean saved) implements CustomPacketPayload {
        public static final Type<BindResult> TYPE = new Type<>(id("machine_config_bind_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BindResult> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.requestId); buf.writeBoolean(msg.saved); },
                buf -> new BindResult(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Snapshot(int containerId, String machineKey, net.minecraft.nbt.CompoundTag config)
            implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(id("machine_config_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeUtf(msg.machineKey, 256); buf.writeNbt(msg.config); },
                buf -> new Snapshot(buf.readVarInt(), buf.readUtf(256),
                        java.util.Objects.requireNonNullElseGet(buf.readNbt(), net.minecraft.nbt.CompoundTag::new)));
        public Snapshot { config = config.copy(); }
        @Override public net.minecraft.nbt.CompoundTag config() { return config.copy(); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EnergyState(int containerId, String machineKey, int stored, int capacity)
            implements CustomPacketPayload {
        public static final Type<EnergyState> TYPE = new Type<>(id("machine_config_energy_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EnergyState> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeVarInt(msg.containerId); buf.writeUtf(msg.machineKey, 256);
                    buf.writeVarInt(msg.stored); buf.writeVarInt(msg.capacity);
                },
                buf -> new EnergyState(buf.readVarInt(), buf.readUtf(256), buf.readVarInt(), buf.readVarInt()));
        public EnergyState {
            stored = Math.max(0, stored);
            capacity = Math.max(0, capacity);
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @Nullable
    public static EnergyState energyState(ServerPlayer player, NeoForgeMachineConfiguratorMenu menu) {
        if (menu == null || !menu.canEdit(player)) return null;
        EnergyState state = readEnergy(menu, liveEnergy(player, menu, 4));
        if (state == null) state = readEnergy(menu, liveEnergy(player, menu, 5));
        return state == null ? new EnergyState(menu.containerId, menu.machineKey(), 0, 0) : state;
    }

    @Nullable
    private static EnergyState readEnergy(NeoForgeMachineConfiguratorMenu menu, @Nullable IEnergyStorage energy) {
        if (energy == null) return null;
        try {
            return new EnergyState(menu.containerId, menu.machineKey(), energy.getEnergyStored(),
                    energy.getMaxEnergyStored());
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    public static void sendEnergyState(ServerPlayer player, NeoForgeMachineConfiguratorMenu menu) {
        EnergyState state = energyState(player, menu);
        if (state != null) PacketDistributor.sendToPlayer(player, state);
    }

    @Nullable
    private static IEnergyStorage liveEnergy(ServerPlayer player, NeoForgeMachineConfiguratorMenu menu, int section) {
        BlockEntity machine = menu.targetMachine(player);
        if (machine == null) return null;
        List<MachineTarget> targets = NeoForgeMachineConfigSavedData.get(player.server)
                .targets(menu.machineKey(), section, 0);
        for (MachineTarget target : targets) {
            IEnergyStorage energy = NeoForgeMachineTargetAccess.energy(NeoForgeCapabilityProxy.INSTANCE, machine, target);
            if (energy != null) return energy;
        }
        return null;
    }

    @Nullable
    public static Ports query(ServerPlayer player, Query query) {
        Session session = session(player, query.containerId());
        if (session == null || !validRequest(query.requestId()) || query.page() < 0 || query.page() > MAX_PAGE)
            return null;
        if (!validAddress(query.section(), query.sample(), query.side(), query.gasMode())
                || !matchesOrientation(session.machine, query.referenceFront()))
            return empty(session, query);

        NeoForgeMachinePortPage page = readPortPage(session.machine, query.section(), query.sample(), direction(query.side()),
                query.page(), query.gasMode());
        if (session.menu.targetMachine(player) != session.machine
                || !matchesOrientation(session.machine, query.referenceFront())
                || !NeoForgeMachineConfiguratorAccess.hasAnyCapability(session.machine)) return null;
        return new Ports(query.containerId(), query.requestId(), session.menu.machineKey(), query.section(),
                query.sample(), query.side(), true, page.proxyId(), page.page(), page.total(),
                page.ports().stream().map(port -> new Port(port.index(), port.key(), port.item(), port.fluid(),
                        port.chemical(), port.amount(), port.capacity(), port.resource(), port.resourceAmount(),
                        port.resourceCapacity())).toList(), query.gasMode());
    }

    /** Returns true only when the exact port still exists on the live machine and its key matches. */
    public static boolean bind(ServerPlayer player, Bind bind) {
        Session session = session(player, bind.containerId());
        if (session == null || !validRequest(bind.requestId()) || bind.page() < 0 || bind.page() > MAX_PAGE
                || !validAddress(bind.section(), bind.sample(), bind.side(), bind.gasMode())) return false;

        NeoForgeMachineConfigSavedData data = NeoForgeMachineConfigSavedData.get(player.server);
        if (bind.clear()) {
            if (bind.append() || bind.index() != -1 || bind.key() == null || !bind.key().isEmpty()) return false;
            return data.updateTarget(session.menu.machineKey(), player.getUUID(), bind.section(), bind.sample(),
                    MachineTarget.UNBOUND, false);
        }

        if (bind.key() == null || bind.key().isEmpty() || bind.key().length() > MachineTarget.MAX_KEY_LENGTH
                || bind.index() < 0) return false;
        NeoForgeMachinePortPage page = readPortPage(session.machine, bind.section(), bind.sample(), direction(bind.side()),
                bind.page(), bind.gasMode());
        if (page.page() != bind.page()) return false;
        boolean exactPort = page.ports().stream().anyMatch(port -> port.index() == bind.index()
                && port.bindable() && port.key().equals(bind.key()));
        if (!exactPort || session.menu.targetMachine(player) != session.machine
                || !NeoForgeMachineConfiguratorAccess.hasAnyCapability(session.machine)) return false;

        MachineTarget target = MachineTarget.relative(bind.side(), bind.index(), bind.key(), session.machine.getBlockState());
        return target.bound() && data.updateTarget(session.menu.machineKey(), player.getUUID(), bind.section(),
                bind.sample(), target, bind.append());
    }

    public static boolean bindBatch(ServerPlayer player, BindBatch bind) {
        Session session = session(player, bind.containerId());
        if (session == null || !validRequest(bind.requestId()) || bind.targets().isEmpty()
                || bind.targets().size() > 512 || !validAddress(bind.section(), bind.sample(),
                bind.targets().getFirst().side(), bind.gasMode())
                || !matchesOrientation(session.machine, bind.referenceFront())) return false;

        var state = session.machine.getBlockState();
        List<MachineTarget> targets = new ArrayList<>(bind.targets().size());
        LinkedHashSet<MachineTarget> unique = new LinkedHashSet<>();
        for (PortTarget selected : bind.targets()) {
            int pageIndex = selected.index() < 0 ? -1 : selected.index() / NeoForgeMachinePortPage.PAGE_SIZE;
            if (selected.key().isEmpty() || selected.key().length() > MachineTarget.MAX_KEY_LENGTH
                    || !validAddress(bind.section(), bind.sample(), selected.side(), bind.gasMode())
                    || pageIndex < 0 || pageIndex > MAX_PAGE) return false;
            NeoForgeMachinePortPage page = readPortPage(session.machine, bind.section(), bind.sample(), direction(selected.side()),
                    pageIndex, bind.gasMode());
            boolean exactPort = page.page() == pageIndex && page.ports().stream().anyMatch(port ->
                    port.index() == selected.index() && port.bindable() && port.key().equals(selected.key()));
            if (!exactPort) return false;
            MachineTarget target = MachineTarget.relative(selected.side(), selected.index(), selected.key(), state);
            if (!target.bound() || !unique.add(target)) return false;
            targets.add(target);
        }
        if (session.machine.getBlockState() != state || session.menu.targetMachine(player) != session.machine
                || !NeoForgeMachineConfiguratorAccess.hasAnyCapability(session.machine)) return false;
        return NeoForgeMachineConfigSavedData.get(player.server).replaceTargets(session.menu.machineKey(),
                player.getUUID(), bind.section(), bind.sample(), targets);
    }

    public static boolean updateParameters(ServerPlayer player, UpdateParameters update) {
        Session session = session(player, update.containerId());
        if (session == null || !validRequest(update.requestId())) return false;
        return NeoForgeMachineConfigSavedData.get(player.server).updateParameter(session.menu.machineKey(),
                player.getUUID(), update.section(), update.sample(), update.field(), update.value());
    }

    public static boolean updateItemFilter(ServerPlayer player, ItemFilter update) {
        Session session = session(player, update.containerId());
        return session != null && NeoForgeMachineConfigSavedData.get(player.server).updateItemFilter(
                session.menu.machineKey(), player.getUUID(), update.section(), update.sample(),
                update.filter(), player.registryAccess());
    }

    public static boolean updateFluidFilter(ServerPlayer player, FluidFilter update) {
        Session session = session(player, update.containerId());
        return session != null && NeoForgeMachineConfigSavedData.get(player.server).updateFluidFilter(
                session.menu.machineKey(), player.getUUID(), update.section(), update.sample(),
                update.filter(), player.registryAccess());
    }

    public static boolean updateChemicalFilter(ServerPlayer player, ChemicalFilter update) {
        Session session = session(player, update.containerId());
        return session != null && update.chemical() != null
                && NeoForgeMachineConfigSavedData.get(player.server).updateChemicalFilter(
                session.menu.machineKey(), player.getUUID(), update.section(), update.sample(), update.chemical());
    }

    public static void sendSnapshot(ServerPlayer player, NeoForgeMachineConfiguratorMenu menu) {
        if (menu.canEdit(player)) {
            var config = NeoForgeMachineConfigSavedData.get(player.server).config(menu.machineKey());
            if (config != null) {
                menu.setClientConfig(config);
                PacketDistributor.sendToPlayer(player, new Snapshot(menu.containerId, menu.machineKey(), config));
                sendEnergyState(player, menu);
            }
        }
    }

    private static void handleQuery(IPayloadContext context, Query query) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        Ports ports = query(player, query);
        if (ports != null) PacketDistributor.sendToPlayer(player, ports);
    }

    private static void handleBind(IPayloadContext context, Bind bind) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        boolean saved = bind(player, bind);
        if (player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu
                && menu.containerId == bind.containerId()) {
            PacketDistributor.sendToPlayer(player, new BindResult(menu.containerId, bind.requestId(), saved));
            if (saved) sendSnapshot(player, menu);
        }
    }

    private static void handleBindBatch(IPayloadContext context, BindBatch bind) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        boolean saved = bindBatch(player, bind);
        if (saved && player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu
                && menu.containerId == bind.containerId() && menu.canEdit(player)) sendSnapshot(player, menu);
    }

    private static void handleParameterUpdate(IPayloadContext context, UpdateParameters update) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        boolean saved = updateParameters(player, update);
        if (saved && player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu
                && menu.containerId == update.containerId() && menu.canEdit(player)) sendSnapshot(player, menu);
    }

    private static void handleItemFilter(IPayloadContext context, ItemFilter update) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        boolean saved = updateItemFilter(player, update);
        if (saved && player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu
                && menu.containerId == update.containerId() && menu.canEdit(player)) sendSnapshot(player, menu);
    }

    private static void handleFluidFilter(IPayloadContext context, FluidFilter update) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        boolean saved = updateFluidFilter(player, update);
        if (saved && player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu
                && menu.containerId == update.containerId() && menu.canEdit(player)) sendSnapshot(player, menu);
    }

    private static void handleChemicalFilter(IPayloadContext context, ChemicalFilter update) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        boolean saved = updateChemicalFilter(player, update);
        if (saved && player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu
                && menu.containerId == update.containerId() && menu.canEdit(player)) sendSnapshot(player, menu);
    }

    private static void handlePorts(Ports ports, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.accept(ports));
    }

    private static void handleBindResult(BindResult result, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.accept(result));
    }

    private static void handleSnapshot(Snapshot snapshot, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.accept(snapshot));
    }

    private static void handleEnergyState(EnergyState state, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.accept(state));
    }

    private static final class ClientHandler {
        private static void accept(Ports ports) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu)
                menu.acceptPorts(ports);
        }
        private static void accept(BindResult result) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu)
                menu.acceptBindResult(result);
        }
        private static void accept(Snapshot snapshot) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu)
                menu.acceptSnapshot(snapshot);
        }
        private static void accept(EnergyState state) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu)
                menu.acceptEnergyState(state);
        }
    }

    private record Session(NeoForgeMachineConfiguratorMenu menu, BlockEntity machine) {}

    @Nullable
    private static Session session(ServerPlayer player, int containerId) {
        if (player == null || !player.server.isSameThread()
                || !(player.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu)
                || menu.containerId != containerId || !menu.canEdit(player) || !menu.stillValid(player)) return null;
        BlockEntity machine = menu.targetMachine(player);
        return machine != null && NeoForgeMachineConfiguratorAccess.hasAnyCapability(machine)
                ? new Session(menu, machine) : null;
    }

    private static Ports empty(Session session, Query query) {
        return new Ports(query.containerId(), query.requestId(), session.menu.machineKey(), query.section(),
                query.sample(), query.side(), false, "", 0, 0, List.of(), query.gasMode());
    }

    private static NeoForgeMachinePortPage readPortPage(BlockEntity machine, int section, int sample, @Nullable Direction side,
                                                        int page, boolean gasMode) {
        if (section == 6 || section == 7) return NeoForgeMachinePortPage.resources(machine, sample, side, page);
        return gasMode ? NeoForgeMachineChemicalBridge.read(machine, side, page)
                : NeoForgeMachinePortPage.read(NeoForgeCapabilityProxy.INSTANCE, machine, section, side, page);
    }

    private static boolean validRequest(int id) { return id >= 0 && id <= MAX_REQUEST_ID; }

    private static boolean matchesOrientation(BlockEntity machine, int front) {
        return front >= 2 && front <= 5
                && RelativeMachineSides.from(machine.getBlockState()).front().get3DDataValue() == front;
    }

    private static boolean validAddress(int section, int sample, int side) {
        return validAddress(section, sample, side, false);
    }

    private static boolean validAddress(int section, int sample, int side, boolean gasMode) {
        if (section < 0 || section > 7 || sample < 0 || section < 4 && sample >= 9
                || (section == 4 || section == 5) && sample != 0 || section >= 6 && sample >= 6)
            return false;
        if (gasMode && (section < 2 || section > 3 || !NeoForgeMachineChemicalBridge.available())) return false;
        return section >= 4 ? side >= 0 && side <= MachineTarget.INTERNAL_SIDE : side >= 0 && side < 6;
    }

    @Nullable
    private static Direction direction(int side) {
        return side == MachineTarget.INTERNAL_SIDE ? null : Direction.from3DDataValue(side);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path);
    }
}
