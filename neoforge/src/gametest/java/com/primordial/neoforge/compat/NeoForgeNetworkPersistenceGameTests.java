package com.primordial.neoforge.compat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.primordial.data.MachineTarget;
import com.primordial.util.MachineKey;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.machine.NeoForgeCapabilityProxy;
import com.primordial.neoforge.machine.NeoForgeMachineConfigSavedData;
import com.primordial.neoforge.machine.NeoForgeMachinePortPage;
import com.primordial.neoforge.machine.NeoForgeMachineTaskExecutor;
import com.primordial.neoforge.machine.NeoForgeMachineTaskScheduler;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationInterfaceBlockEntity;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** The same isolated world must be normally stopped between write, read and verify. */
@GameTestHolder("primordial_network_persistence")
@PrefixGameTestTemplate(false)
public final class NeoForgeNetworkPersistenceGameTests {
    public static final UUID OWNER = UUID.fromString("79100aea-61c4-414f-b892-6211001063ae");
    private static final UUID NETWORK_OWNER = UUID.fromString("79100aea-61c4-414f-b892-6211002063ae");
    public static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final BigInteger FUNDING = BigInteger.TEN.pow(12);

    private NeoForgeNetworkPersistenceGameTests() {}

    @GameTestGenerator
    public static List<TestFunction> controlledRestarts() {
        String phase = System.getProperty("primordial.test.networkPersistencePhase", "off");
        if (phase.equals("off")) return List.of();
        if (!Set.of("write", "read", "verify").contains(phase))
            throw new IllegalArgumentException("Unknown network persistence phase: " + phase);
        return List.of(new TestFunction("network_persistence", "network_persistence." + phase,
                "primordial_network_persistence:capability_proxy_empty", 650, 0L, true,
                helper -> new Fixture(helper, phase).start()));
    }

    /** Implementations use public native graph/storage APIs and normal adjacent block discovery. */
    public interface Network {
        String id();
        void place(Fixture fixture, BlockPos position);
        boolean ready(Fixture fixture, BlockPos position);
        long item(Fixture fixture, BlockPos position, long amount, boolean simulate);
        long fluid(Fixture fixture, BlockPos position, long amount, boolean simulate);
        long listedItems(Fixture fixture, BlockPos position);
        long listedFluids(Fixture fixture, BlockPos position);
        default void assertRestored(Fixture fixture, BlockPos position) {}
        default void assertNoSavedVirtualInventory(Fixture fixture, BlockPos position) {}
        default void revoke(Fixture fixture, BlockPos position) {}
        default void assertRevoked(Fixture fixture, BlockPos position) {}
    }

    public static final class Fixture {
        public final GameTestHelper helper;
        public final ServerLevel level;
        public final MinecraftServer server;
        public final String phase;
        public ServerPlayer player, networkOwner;
        private final List<Network> networks = new ArrayList<>();
        private final List<EmbeddedChannel> channels = new ArrayList<>();
        private final Set<ChunkPos> tickets = new LinkedHashSet<>();
        private ChestBlockEntity outputs, machine;
        private MachineTarget target;
        private boolean closed;

        Fixture(GameTestHelper helper, String phase) {
            this.helper = helper;
            this.phase = phase;
            level = helper.getLevel();
            server = level.getServer();
        }

        public BlockPos position(int index) {
            // GameTest structure origins are random in each JVM; durable fixtures use fixed world coordinates.
            return new BlockPos(512 + index * 8, -59, 512);
        }

        private BlockPos outputsPosition() { return new BlockPos(512, -59, 517); }
        private BlockPos machinePosition() { return new BlockPos(515, -59, 517); }
        private String machineKey() { return MachineKey.of(level.dimension(), machinePosition()); }

        void start() {
            guarded(() -> {
                check(ModList.get().isLoaded("ae2") && ModList.get().isLoaded("refinedstorage"),
                        "Network restart acceptance requires the real AE2 and RS2 runtime mods");
                networks.add(network("ae2.NeoForgeAe2PersistenceNetwork"));
                networks.add(network("rs2.NeoForgeRs2PersistenceNetwork"));
                if (ModList.get().isLoaded("beyonddimensions"))
                    networks.add(network("beyonddimensions.NeoForgeBeyondDimensionsPersistenceNetwork"));
                player = login(OWNER, "restart-trader");
                if (networks.size() == 3) networkOwner = login(NETWORK_OWNER, "restart-netowner");
                // Public vanilla chunk tickets give out-of-template saved fixtures normal block/entity ticks.
                // They never assign native nodes, call port lifecycle methods or replace loaded SavedData.
                BlockPos center = position(1);
                player.teleportTo(level, center.getX() + .5, center.getY() + 2, center.getZ() + .5, 0, 0);
                for (int i = 0; i < networks.size(); i++) { load(position(i)); load(position(i).east()); }
                load(outputsPosition());
                load(machinePosition());
                if (phase.equals("write")) prepare();
                else restore();
                await(240);
            });
        }

        private static Network network(String name) {
            try {
                return (Network) Class.forName("com.primordial.neoforge.compat." + name).getConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Cannot load native restart network " + name, failure);
            }
        }

        private void prepare() {
            check(level.getBlockState(outputsPosition()).isAir() && level.getBlockState(machinePosition()).isAir(),
                    "Write phase requires a fresh isolated world; existing real fixtures must not be overwritten");
            check(knowledge().getKnowledge().isEmpty(), "Write phase requires a fresh native player attachment");
            knowledge().setEmc(FUNDING);
            knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
            check(NeoForgeResourceKnowledge.learnMedia(server, OWNER, Kind.FLUID, WATER), "Learn the exact water identity");
            level.setBlockAndUpdate(outputsPosition(), Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(machinePosition(), Blocks.CHEST.defaultBlockState());
            outputs = chest(outputsPosition());
            machine = chest(machinePosition());
            outputs.setItem(20, new ItemStack(Items.DIRT, 7));
            for (int i = 0; i < networks.size(); i++) {
                BlockPos pos = position(i);
                check(level.getBlockState(pos).isAir() && level.getBlockState(pos.east()).isAir(), "Native restart positions must start empty");
                level.setBlockAndUpdate(pos, NeoForgeModRegistries.TRANSMUTATION_INTERFACE.get().defaultBlockState());
                host(pos).bindOwner(OWNER);
                networks.get(i).place(this, pos);
                outputs.setItem(i + 6, new ItemStack(Items.BUCKET));
            }
            CompoundTag expected = outputs.getPersistentData();
            expected.putInt("Networks", networks.size());
            expected.putString("ItemUnit", itemUnit().toString());
            expected.putString("FluidUnit", fluidUnit().toString());
            check(!itemUnit().isZero() && !fluidUnit().isZero(), "Actual loaded native item/fluid prices must be positive");
            var configs = configs();
            check(configs.getOrCreate(machineKey(), OWNER) != null, "Create the owned real chest machine configuration");
            target = currentTarget();
            check(configs.updateItemFilter(machineKey(), OWNER, 0, 0, new ItemStack(Items.DIAMOND), server.registryAccess())
                            && configs.updateTarget(machineKey(), OWNER, 0, 0, target, false)
                            && configs.updateParameter(machineKey(), OWNER, 0, 0, 2, 2)
                            && configs.updateParameter(machineKey(), OWNER, 0, 0, 3, 20)
                            && configs.updateParameter(machineKey(), OWNER, 8, 0, 0, 0)
                            && configs.updateFluidFilter(machineKey(), OWNER, 2, 0, new FluidStack(Fluids.WATER, 1000), server.registryAccess())
                            && configs.updateParameter(machineKey(), OWNER, 2, 0, 2, 250)
                            && configs.updateParameter(machineKey(), OWNER, 4, 0, 1, 321),
                    "Persist actual filters, bound port identity, item/fluid amounts, interval, master switch and FE configuration");
            outputs.setChanged();
        }

        private void restore() {
            outputs = chest(outputsPosition());
            machine = chest(machinePosition());
            check(outputs.getPersistentData().getInt("Networks") == networks.size(), "All phases must use the same optional mod set");
            check(outputs.getPersistentData().getString("ItemUnit").equals(itemUnit().toString())
                            && outputs.getPersistentData().getString("FluidUnit").equals(fluidUnit().toString()),
                    "The fresh JVM must load the same real price files; tests must not install a synthetic quote snapshot");
            target = currentTarget();
            assertConfiguration(phase.equals("verify") ? 3 : 2, phase.equals("verify") ? 40 : 20);
            assertPhysical(phase.equals("verify") ? 3 : 1, phase.equals("verify"));
            check(balance().equals(phase.equals("verify") ? finalBalance() : afterWrite()),
                    "A fresh native login must restore the exact prior whole/fractional account without adding historical virtual stock");
            boolean allowed = !phase.equals("verify");
            check(NeoForgeResourceKnowledge.allowsItem(server, OWNER, new ItemStack(Items.DIAMOND)) == allowed
                            && NeoForgeResourceKnowledge.allowsMedia(server, OWNER, Kind.FLUID, WATER) == allowed,
                    "Current item knowledge and fluid authorization must load from the actual saved player/knowledge stores");
        }

        private void await(int remaining) {
            guarded(() -> {
                boolean ready = true;
                for (int i = 0; i < networks.size(); i++) {
                    if (phase.equals("verify") && i == 0) continue;
                    ready &= networks.get(i).ready(this, position(i));
                }
                if (ready) transact();
                else if (remaining == 0) {
                    List<String> pending = new ArrayList<>();
                    for (int i = 0; i < networks.size(); i++)
                        if (!(phase.equals("verify") && i == 0) && !networks.get(i).ready(this, position(i))) pending.add(networks.get(i).id());
                    throw new GameTestAssertException("Saved native networks never normally rejoined their real powered grids: " + pending);
                }
                else helper.runAfterDelay(1, () -> await(remaining - 1));
            });
        }

        private void transact() {
            if (phase.equals("verify")) {
                check(level.getBlockState(position(0)).isAir(), "The removed AE2 interface must remain absent after a second JVM restart");
                for (int i = 1; i < networks.size(); i++) {
                    Network network = networks.get(i);
                    check(OWNER.equals(host(position(i)).owner()), network.id() + " must retain the same pinned interface owner after the second restart");
                    network.assertRestored(this, position(i));
                    network.assertRevoked(this, position(i));
                    check(network.listedItems(this, position(i)) == 0 && network.listedFluids(this, position(i)) == 0
                                    && network.item(this, position(i), 1, false) == 0
                                    && network.fluid(this, position(i), 1, false) == 0,
                            network.id() + " must reject current revoked authorization rather than resurrecting a saved virtual inventory");
                    network.assertNoSavedVirtualInventory(this, position(i));
                }
                NeoForgeMachineTaskScheduler.clear(server);
                NeoForgeMachineTaskExecutor.runAll(server);
                assertPhysical(3, true);
                check(balance().equals(finalBalance()), "Rejected post-restart requests and disabled machine tasks must not charge or duplicate anything");
            } else {
                for (int i = 0; i < networks.size(); i++) {
                    Network network = networks.get(i);
                    BlockPos pos = position(i);
                    check(OWNER.equals(host(pos).owner()), network.id() + " interface owner must survive a real chunk save/load");
                    network.assertRestored(this, pos);
                    check(network.listedItems(this, pos) > 0 && network.listedFluids(this, pos) > 0,
                            network.id() + " must remount a live catalogue derived from current knowledge and balance");
                    EmcAmount before = balance();
                    long count = phase.equals("write") ? 1 : 2;
                    check(network.item(this, pos, count, true) == count && balance().equals(before), network.id() + " simulation must remain read-only");
                    check(network.item(this, pos, count, false) == count && balance().equals(before.subtract(itemUnit().multiply(count))),
                            network.id() + " real item extraction must charge once after native initialization");
                    outputs.setItem(i, new ItemStack(Items.DIAMOND, phase.equals("write") ? 1 : 3));
                    if (phase.equals("read")) {
                        before = balance();
                        check(network.fluid(this, pos, 1000, true) == 1000 && balance().equals(before), network.id() + " fluid simulation cannot mutate persisted accounting");
                        check(network.fluid(this, pos, 1000, false) == 1000 && balance().equals(before.subtract(fluidUnit().multiply(1000))),
                                network.id() + " real fluid extraction must charge exactly one delivered bucket");
                        check(outputs.getItem(i + 6).is(Items.BUCKET), "Only the existing physical empty bucket may be filled");
                        outputs.setItem(i + 6, new ItemStack(Items.WATER_BUCKET));
                    }
                    network.assertNoSavedVirtualInventory(this, pos);
                }
                if (phase.equals("read")) {
                    check(configs().updateParameter(machineKey(), OWNER, 8, 0, 0, 1), "The restored owner must be able to enable the restored machine task");
                    NeoForgeMachineTaskScheduler.clear(server);
                    NeoForgeMachineTaskExecutor.runAll(server);
                    check(machine.getItem(0).is(Items.DIAMOND) && machine.getItem(0).getCount() == 2,
                            "A restored real port binding must deliver exactly two actual diamonds into the persisted chest");
                    check(configs().updateParameter(machineKey(), OWNER, 8, 0, 0, 0)
                                    && configs().updateParameter(machineKey(), OWNER, 0, 0, 2, 3)
                                    && configs().updateParameter(machineKey(), OWNER, 0, 0, 3, 40), "Persist the second owner edit and disabled switch");
                    // BD membership is revoked independently while knowledge is still valid.
                    for (int i = 0; i < networks.size(); i++) networks.get(i).revoke(this, position(i));
                    knowledge().removeKnowledge(ItemInfo.fromItem(Items.DIAMOND));
                    check(NeoForgeResourceKnowledge.forgetMedia(server, OWNER, Kind.FLUID, WATER), "Explicit fluid revocation must persist");
                    level.setBlockAndUpdate(position(0), Blocks.AIR.defaultBlockState());
                    assertConfiguration(3, 40);
                    assertPhysical(3, true);
                    check(balance().equals(finalBalance()), "Native network and restored machine deliveries must conserve the exact combined account");
                } else {
                    assertConfiguration(2, 20);
                    assertPhysical(1, false);
                    check(balance().equals(afterWrite()), "Initial native purchases must be the only write-phase debits");
                }
            }
            outputs.setChanged();
            machine.setChanged();
            close();
            helper.succeed();
        }

        private MachineTarget currentTarget() {
            var page = NeoForgeMachinePortPage.read(NeoForgeCapabilityProxy.INSTANCE, machine, 0, Direction.NORTH, 0);
            check(!page.ports().isEmpty() && page.ports().getFirst().bindable(), "The ordinary chest must expose its actual public sided item port");
            var port = page.ports().getFirst();
            return MachineTarget.relative(Direction.NORTH.get3DDataValue(), port.index(), port.key(), machine.getBlockState());
        }

        private void assertConfiguration(int amount, int interval) {
            var data = configs();
            CompoundTag config = data.config(machineKey());
            check(config != null && data.canEdit(machineKey(), OWNER) && !data.canEdit(machineKey(), NETWORK_OWNER)
                            && data.targets(machineKey(), 0, 0).equals(List.of(target)) && !config.getBoolean("iie"),
                    "A normally loaded machine config must retain its pinned owner, relative target identity and disabled master switch");
            CompoundTag item = config.getList("ii", Tag.TAG_COMPOUND).getCompound(0);
            CompoundTag fluid = config.getList("fi", Tag.TAG_COMPOUND).getCompound(0);
            check(ItemStack.parseOptional(server.registryAccess(), item.getCompound("f")).is(Items.DIAMOND)
                            && item.getInt("t") == amount && item.getInt("it") == interval && item.getBoolean("e")
                            && fluid.getInt("t") == 250 && fluid.getCompound("f").getString("FluidName").equals(WATER.toString())
                            && config.getCompound("en").getInt("sr") == 321,
                    "Item/fluid filters, amounts, tick intervals and independent FE settings must survive independent JVMs");
            check(!data.updateParameter(machineKey(), NETWORK_OWNER, 0, 0, 2, 999), "Restart must never transfer edit authority to a different network owner");
        }

        private void assertPhysical(int diamonds, boolean filled) {
            for (int i = 0; i < networks.size(); i++) {
                check(outputs.getItem(i).is(Items.DIAMOND) && outputs.getItem(i).getCount() == diamonds,
                        "The saved vanilla chest must contain exactly the materialized native-network item outputs");
                check(outputs.getItem(i + 6).is(filled ? Items.WATER_BUCKET : Items.BUCKET)
                                && outputs.getItem(i + 6).getCount() == 1, "The exact physical fluid container must survive normally");
            }
            check(outputs.getItem(20).is(Items.DIRT) && outputs.getItem(20).getCount() == 7, "Unrelated saved inventory must remain unchanged");
            check(filled ? machine.getItem(0).is(Items.DIAMOND) && machine.getItem(0).getCount() == 2 : machine.getItem(0).isEmpty(),
                    "The disabled persisted machine task must not generate extra stock while other networks load");
        }

        public NeoForgeTransmutationInterfaceBlockEntity host(BlockPos pos) {
            check(level.getBlockEntity(pos) instanceof NeoForgeTransmutationInterfaceBlockEntity, "Expected the actual saved production transmutation interface");
            return (NeoForgeTransmutationInterfaceBlockEntity) level.getBlockEntity(pos);
        }
        public void place(BlockPos pos, String id) {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
            check(block != Blocks.AIR, "The native runtime must register " + id);
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
        private ChestBlockEntity chest(BlockPos pos) {
            check(level.getBlockEntity(pos) instanceof ChestBlockEntity, "The ordinary saved vanilla chest must exist without being recreated by read/verify");
            return (ChestBlockEntity) level.getBlockEntity(pos);
        }
        private void load(BlockPos position) {
            ChunkPos chunk = new ChunkPos(position);
            if (tickets.add(chunk)) level.setChunkForced(chunk.x, chunk.z, true);
            level.getChunk(position);
        }
        private NeoForgeMachineConfigSavedData configs() { return NeoForgeMachineConfigSavedData.get(server); }
        private IKnowledgeProvider knowledge() {
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge != null && !knowledge.hasFullKnowledge(), "Use the real non-tome ProjectE player attachment");
            return knowledge;
        }
        public EmcAmount balance() {
            var data = NeoForgeEmcSavedData.get(server);
            check(data.debit(OWNER).signum() == 0, "Native online account must acknowledge every whole debit");
            return EmcAmount.of(knowledge().getEmc()).add(data.pending(OWNER));
        }
        private EmcAmount itemUnit() { return NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND)); }
        private EmcAmount fluidUnit() { return NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)); }
        private EmcAmount afterWrite() { return EmcAmount.of(FUNDING).subtract(itemUnit().multiply(networks.size())); }
        private EmcAmount finalBalance() { return afterWrite().subtract(itemUnit().multiply(2L * networks.size() + 2)).subtract(fluidUnit().multiply(1000L * networks.size())); }

        private ServerPlayer login(UUID id, String name) {
            check(server.getPlayerList().getPlayer(id) == null, "Every fixed persistence player must start offline in a new JVM");
            GameProfile profile = new GameProfile(id, name);
            CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
            ServerPlayer result = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            channels.add(channel);
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, result, cookie);
            return result;
        }
        private void guarded(Runnable action) {
            if (closed) return;
            try { action.run(); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        private void close() {
            if (closed) return;
            closed = true;
            try {
                if (player != null && server.getPlayerList().getPlayer(OWNER) == player) server.getPlayerList().remove(player);
                if (networkOwner != null && server.getPlayerList().getPlayer(NETWORK_OWNER) == networkOwner) server.getPlayerList().remove(networkOwner);
            } finally {
                channels.forEach(EmbeddedChannel::finishAndReleaseAll);
                tickets.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            }
            // Keep the actual chunks, native networks, inventories and SavedData for the next process.
        }
    }

    public static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
