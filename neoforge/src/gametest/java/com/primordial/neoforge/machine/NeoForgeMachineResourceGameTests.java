package com.primordial.neoforge.machine;

import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.primordial.data.MachineTarget;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.resource.NeoForgeResourcePort;
import com.primordial.neoforge.resource.NeoForgeResourcePortAccess;
import com.primordial.neoforge.resource.NeoForgeResourcePortRegistry;
import com.primordial.neoforge.resource.NeoForgeResourceUnits;
import com.primordial.service.EmcAmount;
import com.primordial.util.MachineKey;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeMachineResourceGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final Direction SIDE = Direction.NORTH;
    private static final Map<BlockEntity, TestPort> PORTS = new IdentityHashMap<>();
    static {
        NeoForgeResourcePortRegistry.register("test_native_source", PORTS::containsKey,
                (machine, side) -> side == SIDE && PORTS.containsKey(machine) ? List.of(PORTS.get(machine)) : List.of());
    }
    private NeoForgeMachineResourceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void resourceTasksUseActualAmountsKnowledgeAndPrecisePrices(GameTestHelper helper) {
        var fixture = open(helper);
        var server = helper.getLevel().getServer();
        var player = fixture.player;
        var port = fixture.port;
        var data = fixture.data;
        try {
            bind(fixture, 6, 0, 1000, 1);
            var before = balance(player);
            sweep(server);
            check(port.amount == 0 && balance(player).equals(before), "Unknown resource must not debit or deliver");
            knowledge(player).addKnowledge(ItemInfo.fromStack(Kind.SOURCE.sample()));
            port.actualLimit = 125;
            sweep(server);
            check(port.amount == 125 && balance(player).equals(before.subtract(EmcAmount.parse("1/8"))),
                    "Partial native delivery must charge actual units and refund undelivered reservation");
            NeoForgeMachineTaskExecutor.runAll(server);
            check(port.amount == 125, "Same tick task sweep must not repeat a native transfer");
            data.updateParameter(fixture.key, player.getUUID(), 6, 3, 4, 0);
            bind(fixture, 7, 0, 125, 1);
            sweep(server);
            check(port.amount == 0 && balance(player).equals(before), "Native fractional supply/reclaim must exactly round-trip");
            var old = List.copyOf(NeoForgeServerConfig.RESOURCE_BLACKLIST.get());
            try {
                port.amount = 100;
                NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of("ars_nouveau:*"));
                sweep(server);
                check(port.amount == 100 && balance(player).equals(before), "Blacklisted native resources must not be extracted");
            } finally { NeoForgeServerConfig.RESOURCE_BLACKLIST.set(old); }
            data.updateParameter(fixture.key, player.getUUID(), 7, 3, 4, 0);
            port.actualLimit = Long.MAX_VALUE;
            port.capacity = 5_000_000_000L;
            bind(fixture, 6, 1, 1, 20);
            sweep(server);
            check(port.amount == port.capacity, "KEEP native task must retain long capacity without int truncation");
            data.updateParameter(fixture.key, player.getUUID(), 6, 3, 4, 0);
            bind(fixture, 7, 1, 1, 20);
            sweep(server);
            check(port.amount == 0, "KEEP native recovery must empty its selected port");
        } finally { close(helper, fixture); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void nativePortQueriesBindAndLongCodecAreMenuAndResourceBound(GameTestHelper helper) {
        var f = open(helper);
        var p = f.player;
        try {
            p.setPos(f.machine.getBlockPos().getX() + .5, f.machine.getBlockPos().getY() + .5, f.machine.getBlockPos().getZ() + .5);
            var menu = new NeoForgeMachineConfiguratorMenu(701, p.getInventory(), f.key);
            p.containerMenu = menu;
            f.port.amount = 3_000_000_000L;
            f.port.capacity = 5_000_000_000L;
            var query = new NeoForgeMachineConfiguratorPayloads.Query(701, 4, 6, 3, SIDE.ordinal(), 0);
            var result = NeoForgeMachineConfiguratorPayloads.query(p, query);
            check(result != null && result.valid() && result.ports().size() == 1
                    && result.ports().getFirst().resourceAmount() == 3_000_000_000L, "Resource query must preserve long native values");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), p.registryAccess());
            try {
                NeoForgeMachineConfiguratorPayloads.Ports.STREAM_CODEC.encode(buffer, result);
                var decoded = NeoForgeMachineConfiguratorPayloads.Ports.STREAM_CODEC.decode(buffer);
                check(decoded.ports().getFirst().resourceCapacity() == 5_000_000_000L
                        && decoded.ports().getFirst().resource().equals(f.port.unit()), "Resource page codec must retain unit and long capacity");
            } finally { buffer.release(); }
            var selected = result.ports().getFirst();
            check(NeoForgeMachineConfiguratorPayloads.bind(p, new NeoForgeMachineConfiguratorPayloads.Bind(701, 5,
                    6, 3, SIDE.ordinal(), 0, 0, selected.key(), false, false)), "Owner must bind current resource port");
            check(!NeoForgeMachineConfiguratorPayloads.bind(p, new NeoForgeMachineConfiguratorPayloads.Bind(701, 6,
                    6, 0, SIDE.ordinal(), 0, 0, selected.key(), false, false)), "Source port must not bind into the Mana sample");
            check(!NeoForgeMachineConfiguratorPayloads.bind(p, new NeoForgeMachineConfiguratorPayloads.Bind(701, 7,
                    6, 6, SIDE.ordinal(), 0, 0, selected.key(), false, false)), "Reserved resource options must reject binding");
            check(!f.data.updateParameter(f.key, UUID.randomUUID(), 6, 3, 4, 1)
                    && !f.data.updateParameter(f.key, p.getUUID(), 6, 3, 1, 1), "Foreign owners and unsupported rate fields must reject writes");
            int request = menu.expectPortQuery(6, 3, SIDE.ordinal(), 0);
            menu.acceptPorts(new NeoForgeMachineConfiguratorPayloads.Ports(701, request, f.key, 7, 3,
                    SIDE.ordinal(), true, "native_resources", 0, 1, result.ports()));
            check(menu.portSnapshot() == null, "Other direction section responses must not replace pending resource page");
            menu.acceptPorts(new NeoForgeMachineConfiguratorPayloads.Ports(701, request, f.key, 6, 3,
                    SIDE.ordinal(), true, "native_resources", 0, 1, result.ports()));
            check(menu.portSnapshot() != null, "Fully matching resource reply must be accepted");
            check(NeoForgeMachineConfiguratorPayloads.query(p, new NeoForgeMachineConfiguratorPayloads.Query(702, 8,
                    6, 3, SIDE.ordinal(), 0)) == null, "Expired menu must not receive resource page");
            var restored = NeoForgeMachineConfigSavedData.load(f.data.save(new net.minecraft.nbt.CompoundTag(), p.registryAccess()), p.registryAccess());
            check(restored.targets(f.key, 6, 3).equals(f.data.targets(f.key, 6, 3)), "Resource target must survive SavedData round trip");
        } finally { close(helper, f); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void nativeSimulationInvalidationNeverWritesOrDebits(GameTestHelper helper) {
        var f = open(helper);
        try {
            knowledge(f.player).addKnowledge(ItemInfo.fromStack(Kind.SOURCE.sample()));
            bind(f, 6, 0, 1000, 1);
            var before = balance(f.player);
            f.port.simulated = () -> f.data.updateParameter(f.key, f.player.getUUID(), 6, 3, 4, 0);
            sweep(f.player.server);
            check(f.port.amount == 0 && balance(f.player).equals(before), "Config disabled from simulation must cancel without debit");
            f.port.simulated = () -> PORTS.remove(f.machine);
            f.data.updateParameter(f.key, f.player.getUUID(), 6, 3, 4, 1);
            sweep(f.player.server);
            check(f.port.amount == 0 && balance(f.player).equals(before), "Closed native capability must be re-resolved before writing");
        } finally { close(helper, f); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_machine_resource_ticks", timeoutTicks = 100)
    public static void serverTickRunsNativeFixedIntervalsWithoutManualSweep(GameTestHelper helper) {
        var f = open(helper);
        knowledge(f.player).addKnowledge(ItemInfo.fromStack(Kind.SOURCE.sample()));
        bind(f, 6, 0, 25, 10);
        helper.runAfterDelay(12, () -> {
            try {
                check(f.port.amount == 25 || f.port.amount == 50, "Native fixed task must run from real server tick at configured intervals");
                f.data.updateParameter(f.key, f.player.getUUID(), 6, 3, 4, 0);
                long stopped = f.port.amount;
                helper.runAfterDelay(12, () -> {
                    try { check(f.port.amount == stopped, "Disabled native rule must stop future ticks"); }
                    finally { close(helper, f); }
                    helper.succeed();
                });
            } catch (RuntimeException | Error failed) { close(helper, f); throw failed; }
        });
    }

    private record Fixture(BlockEntity machine, TestPort port, ServerPlayer player, String key, NeoForgeMachineConfigSavedData data) {}
    private static Fixture open(GameTestHelper helper) {
        helper.setBlock(POS, NeoForgeMachineTargetTestFixture.BLOCK);
        BlockEntity machine = helper.getBlockEntity(POS);
        var server = helper.getLevel().getServer();
        var p = login(server);
        knowledge(p).clearKnowledge(); knowledge(p).setEmc(BigInteger.TEN.pow(10));
        var port = new TestPort(); PORTS.put(machine, port);
        var key = MachineKey.of(helper.getLevel().dimension(), machine.getBlockPos());
        var data = NeoForgeMachineConfigSavedData.get(server);
        check(data.getOrCreate(key, p.getUUID()) != null, "Native test config must have an owner");
        return new Fixture(machine, port, p, key, data);
    }
    private static void bind(Fixture f, int section, int mode, int amount, int interval) {
        var target = new MachineTarget(SIDE.ordinal(), 0, NeoForgeResourcePortAccess.key(f.machine, SIDE, f.port));
        check(f.data.updateTarget(f.key, f.player.getUUID(), section, 3, target, false)
                && f.data.updateParameter(f.key, f.player.getUUID(), section, 3, 0, mode)
                && f.data.updateParameter(f.key, f.player.getUUID(), section, 3, 2, amount)
                && f.data.updateParameter(f.key, f.player.getUUID(), section, 3, 3, interval)
                && f.data.updateParameter(f.key, f.player.getUUID(), section, 3, 4, 1), "Native rule setup must validate");
    }
    private static void sweep(MinecraftServer server) { NeoForgeMachineTaskScheduler.clear(server); NeoForgeMachineTaskExecutor.runAll(server); }
    private static EmcAmount balance(ServerPlayer p) {
        var ledger = NeoForgeEmcSavedData.get(p.server);
        return EmcAmount.of(knowledge(p).getEmc()).add(ledger.pending(p.getUUID())).subtract(EmcAmount.of(ledger.debit(p.getUUID())));
    }
    private static IKnowledgeProvider knowledge(ServerPlayer p) {
        var knowledge = p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        if (knowledge == null) throw new GameTestAssertException("Missing test knowledge");
        return knowledge;
    }
    private static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "r-" + UUID.randomUUID().toString().substring(0, 8));
        var info = ClientInformation.createDefault();
        var p = server.getPlayerList().getPlayerForLogin(profile, info);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, new net.minecraft.server.network.CommonListenerCookie(profile, 0, info, false, ConnectionType.NEOFORGE));
            return p;
        } catch (RuntimeException | Error failed) { channel.finishAndReleaseAll(); throw failed; }
    }
    private static void close(GameTestHelper helper, Fixture f) {
        PORTS.remove(f.machine); helper.setBlock(POS, Blocks.AIR); NeoForgeMachineTaskScheduler.clear(f.player.server);
        try { f.player.server.getPlayerList().remove(f.player); }
        finally { if (f.player.connection != null && f.player.connection.getConnection().channel() instanceof EmbeddedChannel channel) channel.finishAndReleaseAll(); }
    }
    private static final class TestPort implements NeoForgeResourcePort {
        long amount, capacity = 10000, actualLimit = Long.MAX_VALUE;
        Runnable simulated = () -> {};
        public ResourceLocation unit() { return NeoForgeResourceUnits.machineUnit(3); }
        public String key() { return "test/source"; }
        public long amount() { return amount; }
        public long capacity() { return capacity; }
        public boolean canReceive() { return true; }
        public boolean canExtract() { return true; }
        public long receive(long request, boolean simulate) {
            long accepted = Math.min(request, capacity - amount);
            if (simulate) { simulated.run(); return accepted; }
            accepted = Math.min(actualLimit, accepted); amount += accepted; return accepted;
        }
        public long extract(long request, boolean simulate) {
            long extracted = Math.min(request, amount);
            if (simulate) { simulated.run(); return extracted; }
            amount -= extracted; return extracted;
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
