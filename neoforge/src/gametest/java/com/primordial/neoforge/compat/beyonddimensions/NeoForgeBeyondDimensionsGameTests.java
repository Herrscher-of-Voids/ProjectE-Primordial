/** Exercises the virtual item/fluid view against real Beyond Dimensions storage objects. */
package com.primordial.neoforge.compat.beyonddimensions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.transmutation.NeoForgeNetworkTransferBudget;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationInterfaceBlockEntity;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class NeoForgeBeyondDimensionsGameTests {
    private static final BlockPos INTERFACE = new BlockPos(1, 1, 1);
    private static final BlockPos OWNER_PATH = new BlockPos(2, 1, 1);
    private static final BlockPos OTHER_PATH = new BlockPos(1, 1, 2);
    private static final BigInteger FUNDING = BigInteger.TEN.pow(12);
    private static final String ROOT = "com.wintercogs.beyonddimensions.";

    private NeoForgeBeyondDimensionsGameTests() {}

    public static List<TestFunction> tests() {
        return List.of(new TestFunction("beyond_dimensions", "beyond_dimensions_authorized_virtual_item_fluid_trade",
                "primordial:capability_proxy_empty", Rotation.NONE, 650, 0, true,
                helper -> new Fixture(helper).run()));
    }

    private static final class Fixture {
        private final GameTestHelper helper;
        private final MinecraftServer server;
        private final List<Session> sessions = new ArrayList<>();
        private final List<BlockPos> placed = new ArrayList<>();
        private final FluidPriceSnapshot fluidPrices = new FluidPriceSnapshot();
        private Session owner, other;
        private NeoForgeTransmutationInterfaceBlockEntity host;
        private Object ownerNet, otherNet, ownerStorage, otherStorage, itemKey, fluidKey;
        private Class<?> keyType;
        private boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            server = helper.getLevel().getServer();
        }

        void run() {
            try {
                owner = login("bd-interface");
                other = login("bd-network");
                knowledge(owner).clearKnowledge();
                knowledge(owner).addKnowledge(ItemInfo.fromItem(Items.COAL));
                knowledge(owner).setEmc(FUNDING);
                knowledge(other).clearKnowledge();
                knowledge(other).addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
                knowledge(other).setEmc(FUNDING);
                check(NeoForgeResourceKnowledge.learnMedia(server, owner.player.getUUID(), Kind.FLUID,
                                net.minecraft.resources.ResourceLocation.parse("minecraft:water")),
                        "Owner water knowledge must be accepted");

                ownerNet = placeNetwork(OWNER_PATH, owner.player);
                otherNet = placeNetwork(OTHER_PATH, other.player);
                ownerStorage = invoke(ownerNet, "getUnifiedStorage");
                otherStorage = invoke(otherNet, "getUnifiedStorage");
                keyType = Class.forName(ROOT + "api.storage.key.IStackKey");
                itemKey = Class.forName(ROOT + "api.storage.key.impl.ItemStackKey")
                        .getConstructor(ItemStack.class).newInstance(new ItemStack(Items.COAL));
                fluidKey = Class.forName(ROOT + "api.storage.key.impl.FluidStackKey")
                        .getConstructor(FluidStack.class).newInstance(new FluidStack(Fluids.WATER, 1));

                placed.add(INTERFACE);
                helper.setBlock(INTERFACE, NeoForgeModRegistries.TRANSMUTATION_INTERFACE.get());
                host = (NeoForgeTransmutationInterfaceBlockEntity) helper.getBlockEntity(INTERFACE);
                check(host != null, "Production transmutation interface must be placed");
                host.bindOwner(owner.player.getUUID());
                host.onLoad();
                helper.runAfterDelay(1, () -> guarded(this::verify));
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            } catch (ReflectiveOperationException failure) {
                close();
                throw new IllegalStateException("Beyond Dimensions test API changed", failure);
            }
        }

        private void verify() {
            try {
                check(has(ownerStorage, itemKey), "Authorized dimension storage must expose learned items");
                check(has(ownerStorage, fluidKey), "Authorized dimension storage must expose learned fluids");
                check(!has(otherStorage, itemKey) && !has(otherStorage, fluidKey),
                        "An unauthorized adjacent network must not expose the interface owner's catalogue");

                Method insert = ownerStorage.getClass().getMethod("insert", keyType, long.class, boolean.class);
                Method extract = ownerStorage.getClass().getMethod("extract", keyType, long.class,
                        boolean.class, boolean.class);
                check(amount(invoke(otherStorage, extract, itemKey, 1L, false, false)) == 0,
                        "An unauthorized adjacent network must not purchase from the interface owner");
                invoke(ownerStorage, insert, itemKey, 2L, false);
                IKnowledgeProvider account = knowledge(owner);
                BigInteger itemUnit = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.COAL)).floor();
                check(itemUnit.signum() > 0, "ProjectE must price coal");
                BigInteger itemBefore = account.getEmc();
                long itemBudget = NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID());
                check(amount(invoke(ownerStorage, extract, itemKey, 4L, true, false)) == 4
                                && account.getEmc().equals(itemBefore)
                                && NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID()) == itemBudget,
                        "Item simulation must include virtual shortfall without charging or reserving bandwidth");
                check(amount(invoke(ownerStorage, extract, itemKey, 4L, false, false)) == 4
                                && account.getEmc().equals(itemBefore.subtract(itemUnit.multiply(BigInteger.TWO)))
                                && NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID()) == itemBudget - 2,
                        "Actual stock must be free, with exact EMC and bandwidth charged only for the purchased shortfall");

                FluidStack water = new FluidStack(Fluids.WATER, 1);
                EmcAmount fluidUnit = NeoForgeRecoveryPrices.fluidPurchase(water);
                EmcAmount fluidBefore = spendable(owner);
                long fluidBudget = NeoForgeNetworkTransferBudget.remainingFluid(server, owner.player.getUUID());
                check(!fluidUnit.isZero(), "ProjectE fluid price fixture must be positive");
                check(amount(invoke(ownerStorage, extract, fluidKey, 1000L, true, false)) == 1000
                                && spendable(owner).equals(fluidBefore)
                                && NeoForgeNetworkTransferBudget.remainingFluid(server, owner.player.getUUID()) == fluidBudget,
                        "Fluid simulation must be read-only");
                check(amount(invoke(ownerStorage, extract, fluidKey, 1000L, false, false)) == 1000
                                && spendable(owner).equals(fluidBefore.subtract(fluidUnit.multiply(1000)))
                                && NeoForgeNetworkTransferBudget.remainingFluid(server, owner.player.getUUID()) == fluidBudget - 1000,
                        "Fluid purchase must charge exact fractional EMC and mB bandwidth");
                check(has(ownerStorage, itemKey) && has(ownerStorage, fluidKey),
                        "Purchased virtual resources must remain visible in the live catalogue");
                String saved = ownerStorage.getClass().getMethod("serializeNBT",
                        net.minecraft.core.HolderLookup.Provider.class)
                        .invoke(ownerStorage, helper.getLevel().registryAccess()).toString();
                check(!saved.contains("minecraft:coal") && !saved.contains("minecraft:water"),
                        "Virtual catalogue entries must not be written to Beyond Dimensions storage NBT");

                verifyExtractRules(extract, insert, itemUnit);

                otherNet.getClass().getMethod("addPlayer", UUID.class).invoke(otherNet, owner.player.getUUID());
                host.networkPort("beyonddimensions").onLoad();
                check(has(otherStorage, itemKey), "A permitted adjacent member network must mount the catalogue");
                otherNet.getClass().getMethod("removePlayer", UUID.class).invoke(otherNet, owner.player.getUUID());
                EmcAmount beforeRevoked = spendable(owner);
                check(!has(otherStorage, itemKey)
                                && amount(invoke(otherStorage, extract, itemKey, 1L, false, false)) == 0
                                && spendable(owner).equals(beforeRevoked),
                        "Membership revocation must block catalogue and purchases in the same tick");
                Object pathway = helper.getBlockEntity(OWNER_PATH);
                Method setNetId = pathway.getClass().getMethod("setNetId", int.class);
                setNetId.invoke(pathway, ((Number) invoke(otherNet, "getId")).intValue());
                check(!has(ownerStorage, itemKey)
                                && amount(invoke(ownerStorage, extract, itemKey, 1L, false, false)) == 0
                                && spendable(owner).equals(beforeRevoked),
                        "Rebinding a pathway must immediately invalidate the former storage's virtual purchases");
                setNetId.invoke(pathway, ((Number) invoke(ownerNet, "getId")).intValue());
                check(has(ownerStorage, itemKey), "Restoring the original binding must retain the valid catalogue");

                helper.setBlock(INTERFACE, Blocks.AIR);
                check(!has(ownerStorage, itemKey) && !has(ownerStorage, fluidKey),
                        "Removing the interface must detach its virtual catalogue");
                check(helper.getLevel().getBlockEntity(helper.absolutePos(OWNER_PATH)) != null,
                        "The native network remains available after interface removal");
                close();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            } catch (ReflectiveOperationException failure) {
                close();
                throw new IllegalStateException("Beyond Dimensions test API changed", failure);
            }
        }

        private void verifyExtractRules(Method extract, Method insert, BigInteger itemUnit) throws ReflectiveOperationException {
            Class<?> rules = Class.forName(ROOT + "api.dimensionnet.rule.UnifiedStorageRules");
            var ruleId = net.minecraft.resources.ResourceLocation.parse("primordial:gametest_extract_" + UUID.randomUUID());
            var effects = new AtomicLong();
            try {
                setExtractRule(rules, ruleId, () -> plan(false, 3, null, effects));
                EmcAmount before = spendable(owner);
                long budget = NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID());
                check(amount(invoke(ownerStorage, extract, itemKey, 4L, true, false)) == 3
                                && effects.get() == 0 && spendable(owner).equals(before)
                                && NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID()) == budget,
                        "Custom rule simulation must honor limits without EMC, effects, or bandwidth changes");
                check(amount(invoke(ownerStorage, extract, itemKey, 4L, false, false)) == 3 && effects.get() == 3
                                && spendable(owner).equals(before.subtract(EmcAmount.of(itemUnit).multiply(3)))
                                && NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID()) == budget - 3,
                        "A native rule can purchase only its authorized shortfall, with its commit effect exactly once");

                setExtractRule(rules, ruleId, () -> plan(true, -1, null, effects));
                before = spendable(owner);
                budget = NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID());
                check(amount(invoke(ownerStorage, extract, itemKey, 4L, false, false)) == 0
                                && amount(invoke(ownerStorage, extract, itemKey, 4L, true, false)) == 0
                                && spendable(owner).equals(before) && effects.get() == 3
                                && NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID()) == budget,
                        "A rejecting native rule cannot be bypassed by the virtual catalogue or charge EMC");

                Object ingredient = Class.forName(ROOT + "api.storage.key.impl.ItemStackKey")
                        .getConstructor(ItemStack.class).newInstance(new ItemStack(Items.STICK));
                knowledge(owner).addKnowledge(ItemInfo.fromItem(Items.STICK));
                invoke(ownerStorage, insert, ingredient, 1L, false);
                setExtractRule(rules, ruleId, () -> plan(false, -1, ingredient, effects));
                before = spendable(owner);
                check(amount(invoke(ownerStorage, extract, itemKey, 4L, false, false)) == 1 && effects.get() == 4
                                && spendable(owner).equals(before.subtract(EmcAmount.of(itemUnit)))
                                && physical(ownerStorage, ingredient) == 0,
                        "Native fit and rollback preserve extra ingredient costs while purchasing the root key");
                before = spendable(owner);
                check(amount(invoke(ownerStorage, extract, itemKey, 1L, false, false)) == 0
                                && spendable(owner).equals(before) && effects.get() == 4,
                        "A missing extra rule ingredient is never replaced with an unplanned virtual purchase");

                invoke(ownerStorage, insert, ingredient, 1L, false);
                rejectOnEmcSync(extract, effects, () -> {
                    try { invoke(ownerStorage, extract, ingredient, 1L, false, false); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                }, () -> {});
                check(physical(ownerStorage, ingredient) == 0,
                        "The actual EMC sync callback must remove the physical extra ingredient before commit");
                setExtractRule(rules, ruleId, () -> plan(false, -1, null, effects));
                Object pathway = helper.getBlockEntity(OWNER_PATH);
                Method setNetId = pathway.getClass().getMethod("setNetId", int.class);
                int originalNet = ((Number) invoke(ownerNet, "getId")).intValue();
                int unauthorizedNet = ((Number) invoke(otherNet, "getId")).intValue();
                rejectOnEmcSync(extract, effects, () -> {
                    try { setNetId.invoke(pathway, unauthorizedNet); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                }, () -> {
                    try { setNetId.invoke(pathway, originalNet); }
                    catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                });

                var fluidEffects = new AtomicLong();
                setExtractRule(rules, ruleId, fluidKey, () -> plan(false, 750, null, fluidEffects));
                before = spendable(owner);
                long fluidBudget = NeoForgeNetworkTransferBudget.remainingFluid(server, owner.player.getUUID());
                check(amount(invoke(ownerStorage, extract, fluidKey, 1000L, true, false)) == 750
                                && spendable(owner).equals(before) && fluidEffects.get() == 0
                                && NeoForgeNetworkTransferBudget.remainingFluid(server, owner.player.getUUID()) == fluidBudget,
                        "A native fluid rule simulation preserves fractional EMC, effects, and fluid bandwidth");
                check(amount(invoke(ownerStorage, extract, fluidKey, 1000L, false, false)) == 750
                                && spendable(owner).equals(before.subtract(NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).multiply(750)))
                                && fluidEffects.get() == 750 && physical(ownerStorage, fluidKey) == 0
                                && NeoForgeNetworkTransferBudget.remainingFluid(server, owner.player.getUUID()) == fluidBudget - 750,
                        "A native fluid rule purchases only its limited amount at the exact fractional price without materializing virtual stock");

                invoke(ownerStorage, insert, itemKey, 2L, false);
                setExtractRule(rules, ruleId, () -> {
                    knowledge(owner).removeKnowledge(ItemInfo.fromItem(Items.COAL));
                    return plan(false, -1, null, effects);
                });
                before = spendable(owner);
                budget = NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID());
                check(amount(invoke(ownerStorage, extract, itemKey, 4L, false, false)) == 0
                                && spendable(owner).equals(before) && effects.get() == 4 && physical(ownerStorage, itemKey) == 2
                                && NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID()) == budget,
                        "Knowledge revoked during rule planning must cancel payment, physical removals, and commit effects together");
            } finally {
                rules.getMethod("removeExtract", net.minecraft.resources.ResourceLocation.class).invoke(null, ruleId);
                knowledge(owner).addKnowledge(ItemInfo.fromItem(Items.COAL));
                invoke(ownerStorage, extract, itemKey, physical(ownerStorage, itemKey), false, false);
            }
        }

        private void rejectOnEmcSync(Method extract, AtomicLong effects, Runnable mutation, Runnable restore)
                throws ReflectiveOperationException {
            EmcAmount before = spendable(owner);
            long budget = NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID());
            var armed = new AtomicBoolean(true);
            owner.channel.pipeline().addLast("bd-rule-emc-callback", new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof ClientboundCustomPayloadPacket packet
                            && packet.payload().getClass().getName().endsWith(".KnowledgeSyncEmcPKT")
                            && armed.compareAndSet(true, false)) mutation.run();
                    context.write(message, promise);
                }
            });
            try {
                check(amount(invoke(ownerStorage, extract, itemKey, 1L, false, false)) == 0 && !armed.get()
                                && spendable(owner).equals(before) && effects.get() == 4 && physical(ownerStorage, itemKey) == 0
                                && NeoForgeNetworkTransferBudget.remainingItems(server, owner.player.getUUID()) == budget,
                        "An actual prepayment sync callback changing network authority or physical costs must cancel the rule transaction and refund EMC");
            } finally {
                owner.channel.pipeline().remove("bd-rule-emc-callback");
                restore.run();
            }
        }

        private void setExtractRule(Class<?> rules, net.minecraft.resources.ResourceLocation id,
                                    java.util.function.Supplier<Object> plan) throws ReflectiveOperationException {
            setExtractRule(rules, id, itemKey, plan);
        }

        private void setExtractRule(Class<?> rules, net.minecraft.resources.ResourceLocation id, Object resourceKey,
                                    java.util.function.Supplier<Object> plan) throws ReflectiveOperationException {
            Class<?> api = Class.forName(ROOT + "api.dimensionnet.rule.ExtractRule");
            Object rule = Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] { api }, (proxy, method, arguments) -> {
                if (method.getName().equals("plan")) return plan.get();
                if (method.getName().equals("toString")) return "Primordial extract rule test";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                return arguments != null && arguments.length == 1 && proxy == arguments[0];
            });
            rules.getMethod("removeExtract", net.minecraft.resources.ResourceLocation.class).invoke(null, id);
            rules.getMethod("extract", net.minecraft.resources.ResourceLocation.class, keyType, api)
                    .invoke(null, id, resourceKey, rule);
        }

        private Object plan(boolean reject, long limit, Object ingredient, AtomicLong effects) {
            try {
                Class<?> type = Class.forName(ROOT + "api.dimensionnet.rule.ExtractPlan");
                Object plan = type.getMethod(reject ? "reject" : "allow").invoke(null);
                if (reject) return plan;
                if (limit >= 0) type.getMethod("limit", long.class).invoke(plan, limit);
                if (ingredient != null) type.getMethod("take", keyType, long.class).invoke(plan, ingredient, 1L);
                Class<?> effect = Class.forName(ROOT + "api.dimensionnet.rule.StorageEffect");
                Object callback = Proxy.newProxyInstance(effect.getClassLoader(), new Class<?>[] { effect }, (proxy, method, args) -> {
                    if (method.getName().equals("run")) { effects.addAndGet(((Number) args[1]).longValue()); return null; }
                    if (method.getName().equals("toString")) return "Primordial commit effect test";
                    if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                    return args != null && args.length == 1 && proxy == args[0];
                });
                return type.getMethod("onCommit", effect).invoke(plan, callback);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Build actual BD extraction plan", failure); }
        }

        private long physical(Object storage, Object requested) throws ReflectiveOperationException {
            Method stored = storage.getClass().getDeclaredMethod("storedAmount", keyType);
            stored.setAccessible(true);
            return ((Number) stored.invoke(storage, requested)).longValue();
        }

        private Object placeNetwork(BlockPos position, ServerPlayer player) throws ReflectiveOperationException {
            Class<?> netClass = Class.forName(ROOT + "api.dimensionnet.DimensionsNet");
            Object network = netClass.getMethod("createNewNetForPlayer", net.minecraft.world.entity.player.Player.class,
                    long.class, int.class).invoke(null, player, 1_000_000L, 1000);
            check(network != null, "Beyond Dimensions must create a real player network");
            Block pathway = BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse(
                    "beyonddimensions:net_pathway"));
            check(pathway != Blocks.AIR, "Installed Beyond Dimensions pathway block must be registered");
            placed.add(position);
            helper.setBlock(position, pathway);
            Object blockEntity = helper.getBlockEntity(position);
            check(blockEntity != null, "Beyond Dimensions pathway block entity must be present");
            blockEntity.getClass().getMethod("setNetId", int.class)
                    .invoke(blockEntity, ((Number) netClass.getMethod("getId").invoke(network)).intValue());
            return network;
        }

        private Session login(String name) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);
            CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(),
                    false, ConnectionType.NEOFORGE);
            ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            try {
                NetworkRegistry.configureMockConnection(connection);
                server.getPlayerList().placeNewPlayer(connection, player, cookie);
                Session session = new Session(player, channel);
                sessions.add(session);
                return session;
            } catch (RuntimeException | Error failure) {
                try {
                    if (server.getPlayerList().getPlayer(player.getUUID()) == player)
                        server.getPlayerList().remove(player);
                } finally { channel.finishAndReleaseAll(); }
                throw failure;
            }
        }

        private IKnowledgeProvider knowledge(Session session) {
            IKnowledgeProvider account = session.player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(account != null, "ProjectE player capability must provide EMC and knowledge");
            return account;
        }

        private EmcAmount spendable(Session session) {
            UUID id = session.player.getUUID();
            NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);
            check(data.debit(id).signum() == 0, "Online EMC debit must be acknowledged");
            return EmcAmount.of(knowledge(session).getEmc()).add(data.pending(id));
        }

        private boolean has(Object storage, Object requested) throws ReflectiveOperationException {
            Object pairs = invoke(storage, "getStorage");
            Method key = Class.forName(ROOT + "api.storage.key.KeyAmount").getMethod("key");
            for (Object pair : (List<?>) pairs) if (requested.equals(key.invoke(pair))) return true;
            return false;
        }

        private long amount(Object pair) throws ReflectiveOperationException {
            return ((Number) Class.forName(ROOT + "api.storage.key.KeyAmount").getMethod("amount").invoke(pair))
                    .longValue();
        }

        private Object invoke(Object target, String name) throws ReflectiveOperationException {
            return target.getClass().getMethod(name).invoke(target);
        }

        private Object invoke(Object target, Method method, Object... arguments) throws ReflectiveOperationException {
            return method.invoke(target, arguments);
        }

        private void guarded(Runnable action) {
            try { action.run(); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }

        private void close() {
            if (closed) return;
            closed = true;
            try {
                for (BlockPos position : placed) helper.setBlock(position, Blocks.AIR);
            } finally {
                for (Session session : sessions) {
                    try {
                        if (server.getPlayerList().getPlayer(session.player.getUUID()) == session.player)
                            server.getPlayerList().remove(session.player);
                    } finally { session.channel.finishAndReleaseAll(); }
                }
                fluidPrices.close();
            }
        }
    }

    private record Session(ServerPlayer player, EmbeddedChannel channel) {}

    private static final class FluidPriceSnapshot {
        private final Field snapshot;
        private final Field loaded;
        private final Object before;
        private final boolean wasLoaded;

        FluidPriceSnapshot() {
            try {
                snapshot = NeoForgeRecoveryPrices.class.getDeclaredField("snapshot");
                loaded = NeoForgeRecoveryPrices.class.getDeclaredField("loaded");
                snapshot.setAccessible(true);
                loaded.setAccessible(true);
                before = snapshot.get(null);
                wasLoaded = loaded.getBoolean(null);
                JsonObject root = new JsonObject();
                JsonObject fluids = new JsonObject();
                fluids.addProperty("minecraft:water", "1/3");
                root.add("fluid_values", fluids);
                Method prepare = NeoForgeRecoveryPrices.class.getDeclaredMethod("prepareEdited", JsonObject.class,
                        boolean.class);
                prepare.setAccessible(true);
                Object prepared = prepare.invoke(null, root, true);
                Method publish = NeoForgeRecoveryPrices.class.getDeclaredMethod("publishEdited", prepared.getClass());
                publish.setAccessible(true);
                publish.invoke(null, prepared);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot prepare Beyond Dimensions fluid-price fixture", failure);
            }
        }

        void close() {
            try {
                snapshot.set(null, before);
                loaded.setBoolean(null, wasLoaded);
                NeoForgeRecoveryPrices.invalidatePrices();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot restore Beyond Dimensions fluid prices", failure);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
