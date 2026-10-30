/**
 * 【文件职责】验收转化接口的新档身份、原生方块生命周期与公共网络端口隔离，不注册普通资源能力或第三方交易。
 * 【变更记录 #1】2026-10-22 22:48:37（第 1 次备注 · 2.0.0-neo-dev 转化接口基础）
 * - 删除：无。
 * - 新增：真实 BlockItem 放置、首次归属绑定、未知/损坏 NBT、节点身份和挂载/卸载/拆除测试。
 * - 修改：测试工厂只匹配本测试的实体对象；回调故障、重入、失效引用和异线程均走正式公共入口。
 * - 用途：避免把生命周期桩等同 AE2/RS 商品交易；保存卸载后的缓存不查询世界或重新创建节点。
 * 【变更记录 #2】2026-10-22 22:53:38（第 2 次备注 · 2.0.0-neo-dev 回调计数边界）
 * - 删除：对端口历史保存次数必须为零的隐含假设。新增：显式记录调用前保存次数。
 * - 修改：嵌套保存测试只验证本次外层保存增加一次，允许生产服务先保存已确认的节点快照。
 * - 用途：锁定递归保存必须关闭的交易边界，避免测试依赖内部快照捕获时机。
 * 【变更记录 #3】2026-10-22 22:54:56（第 3 次备注 · 2.0.0-neo-dev 损坏归属保护）
 * - 删除：把损坏 owner 和缺失 owner 都允许重新绑定的断言。新增：损坏标签原文保存和拒绝接管断言。
 * - 修改：只有真正缺失 owner 的新接口允许首次绑定；已有但不可解析的归属保持封锁。
 * - 用途：避免错误 UUID 被当作无主机器，导致其他玩家接管存档内的个人网络节点。
 * 【变更记录 #4】2026-10-22 23:02:39（第 4 次备注 · 2.0.0-neo-dev 异线程恢复保护）
 * - 删除：无。新增：工作线程尝试加载另一个 owner 与网络根的拒绝断言。
 * - 修改：比较真实账户、端口引用和存档快照，确认没有触发卸载、保存或新的工厂调用。
 * - 用途：防止外部异步 NBT 恢复在服务端 tick 之外更换个人网络账户。
 */
package com.primordial.neoforge.transmutation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeTransmutationInterfaceGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeTransmutationInterfaceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void optionalAe2FactoryMatchesInstalledMod(GameTestHelper helper) {
        check(NeoForgeTransmutationNetworkRegistry.registeredIds().contains("ae2")
                        == ModList.get().isLoaded("ae2"),
                "The AE2 port factory must register only when AE2 is present");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void networkItemBudgetIsSharedAcrossInterfaces(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        var emptyFirst = NeoForgeNetworkTransferBudget.reserveItems(helper.getLevel().getServer(), owner, 0);
        var emptySecond = NeoForgeNetworkTransferBudget.reserveItems(helper.getLevel().getServer(), owner, 0);
        emptyFirst.complete(0);
        emptySecond.complete(0);
        var first = NeoForgeNetworkTransferBudget.reserveItems(helper.getLevel().getServer(), owner, 3000);
        var second = NeoForgeNetworkTransferBudget.reserveItems(helper.getLevel().getServer(), owner, 3000);
        check(first.amount() == 3000 && second.amount() == 1096
                        && NeoForgeNetworkTransferBudget.remainingItems(helper.getLevel().getServer(), owner) == 0,
                "All interfaces for an owner must share the 4096 item per-tick limit, including active reservations");
        first.complete(2500);
        second.complete(1096);
        check(NeoForgeNetworkTransferBudget.remainingItems(helper.getLevel().getServer(), owner) == 500,
                "Only actually transferred items should consume the shared per-tick limit");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void networkFluidBudgetReleasesPartialAndCancelledTransfersWithoutUsingItemBudget(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        try (var first = NeoForgeNetworkTransferBudget.reserveFluid(server, owner, 700_000);
             var second = NeoForgeNetworkTransferBudget.reserveFluid(server, owner, Long.MAX_VALUE)) {
            check(first.amount() == 700_000 && second.amount() == 300_000
                            && NeoForgeNetworkTransferBudget.remainingFluid(server, owner) == 0,
                    "In-flight fluid transfers on other interfaces must not overbook the owner's shared limit");
            check(NeoForgeNetworkTransferBudget.remainingItems(server, owner) == 4096
                            && NeoForgeNetworkTransferBudget.remainingFluid(server, UUID.randomUUID()) == 1_000_000,
                    "Fluid reservations must leave item throughput and other owners' fluid throughput independent");
            first.complete(500_000);
            check(NeoForgeNetworkTransferBudget.remainingFluid(server, owner) == 200_000,
                    "A partial fluid transfer must release its undelivered quantity before another reservation closes");
        }
        check(NeoForgeNetworkTransferBudget.remainingFluid(server, owner) == 500_000,
                "A cancelled transfer must release its entire fluid reservation");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void bindsPlacingPlayerOnceAndExposesNoOrdinaryResourceInventory(GameTestHelper helper) {
        helper.setBlock(POSITION.below(), Blocks.STONE);
        helper.setBlock(POSITION, Blocks.AIR);
        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockItem item = NeoForgeModRegistries.TRANSMUTATION_INTERFACE_ITEM.get();
        placer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        BlockPos absolute = helper.absolutePos(POSITION);
        BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(absolute), Direction.UP, absolute.below(), false);
        check(item.place(new BlockPlaceContext(new UseOnContext(placer, InteractionHand.MAIN_HAND, hit))).consumesAction(),
                "The registered transmutation interface item must use real BlockItem placement");
        NeoForgeTransmutationInterfaceBlockEntity block = require(helper);
        check(placer.getUUID().equals(block.owner()), "Placement must bind the actual placing player's UUID");
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        check(!placer.getUUID().equals(other.getUUID()), "The second placement fixture must have a different UUID");
        NeoForgeModRegistries.TRANSMUTATION_INTERFACE.get().setPlacedBy(helper.getLevel(), absolute,
                helper.getBlockState(POSITION), other, new ItemStack(item));
        block.bindOwner(other.getUUID());
        block.bindOwner(null);
        check(placer.getUUID().equals(block.owner()), "Neither another placement callback nor direct binding may steal the existing owner");
        for (Direction side : Direction.values()) noOrdinaryPorts(helper, side);
        noOrdinaryPorts(helper, null);
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesUnknownNetworkPayloadsAndClearsMissingOrMalformedOwners(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeTransmutationInterfaceBlockEntity block = place(helper, owner);
        CompoundTag unknown = new CompoundTag();
        CompoundTag node = new CompoundTag();
        node.putUUID("node_identity", UUID.randomUUID());
        node.putByteArray("opaque_bytes", new byte[]{-1, 0, 3, 127});
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(123));
        node.put("opaque_list", list);
        unknown.put("missing_mod", node);
        unknown.put("future_scalar", StringTag.valueOf("keep this non-compound payload verbatim"));
        CompoundTag input = new CompoundTag();
        input.putUUID("owner", owner);
        input.put("NetworkPorts", unknown.copy());
        block.loadWithComponents(input, helper.getLevel().registryAccess());
        CompoundTag saved = save(helper, block);
        check(owner.equals(block.owner()) && saved.hasUUID("owner") && owner.equals(saved.getUUID("owner"))
                        && unknown.equals(saved.get("NetworkPorts")),
                "Target-version saves must retain the owner and arbitrary unknown network payload types exactly");
        block.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(unknown.equals(save(helper, block).get("NetworkPorts")), "A second save/load must not normalize unknown adapter data");
        block.loadWithComponents(new CompoundTag(), helper.getLevel().registryAccess());
        check(block.owner() == null, "A truly missing owner must clear the previously loaded owner");
        block.bindOwner(owner);
        check(owner.equals(block.owner()), "An interface with no stored owner must allow its first binding");
        for (CompoundTag malformed : malformedOwnerTags()) {
            block.loadWithComponents(malformed, helper.getLevel().registryAccess());
            check(block.owner() == null, "Malformed owner tags must expose no usable owner UUID");
            block.bindOwner(owner);
            check(block.owner() == null && malformed.get("owner").equals(save(helper, block).get("owner")),
                    "An invalid stored owner must remain blocked and keep its exact original tag instead of allowing takeover");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesMalformedNetworkRootsAndDisablesOnlyMalformedKnownPorts(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeTransmutationInterfaceBlockEntity block = place(helper, owner);
        Registration bad = register(block, Probe::new);
        Registration good = register(block, Probe::new);
        CompoundTag input = new CompoundTag();
        input.putUUID("owner", owner);
        StringTag root = StringTag.valueOf("future or corrupt network root must not be overwritten");
        input.put("NetworkPorts", root);
        block.loadWithComponents(input, helper.getLevel().registryAccess());
        block.onLoad();
        block.tickNetworks();
        check(block.networkPort(bad.id) == null && block.networkPort(good.id) == null
                        && root.equals(save(helper, block).get("NetworkPorts")),
                "A malformed network root must preserve its raw type and block every adapter");
        CompoundTag ports = new CompoundTag();
        StringTag badPayload = StringTag.valueOf("known adapter data with an invalid type");
        ports.put(bad.id, badPayload);
        CompoundTag stable = new CompoundTag();
        UUID identity = UUID.randomUUID();
        stable.putUUID("node_identity", identity);
        ports.put(good.id, stable);
        input.put("NetworkPorts", ports);
        block.loadWithComponents(input, helper.getLevel().registryAccess());
        block.onLoad();
        check(block.networkPort(bad.id) == null && block.networkPort(good.id) instanceof Probe,
                "A malformed known entry must close only its adapter and leave a healthy adapter usable");
        CompoundTag resaved = save(helper, block).getCompound("NetworkPorts");
        check(badPayload.equals(resaved.get(bad.id)) && identity.equals(resaved.getCompound(good.id).getUUID("node_identity")),
                "Known invalid raw data and the healthy node identity must both survive another save");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void keepsNodeIdentityAcrossSuspendResumeAndReallyRemovesItOnBlockReplacement(GameTestHelper helper) {
        NeoForgeTransmutationInterfaceBlockEntity block = place(helper, UUID.randomUUID());
        Registration registration = register(block, Probe::new);
        check(block.networkPort(registration.id) == null && registration.created.isEmpty(),
                "A placed but not loaded interface must not create a network node");
        block.onLoad();
        Probe first = (Probe) block.networkPort(registration.id);
        check(first != null && first.loads == 1 && first.starts == 1 && registration.created.size() == 1,
                "The first activation must construct, load and start exactly one node");
        block.onLoad();
        check(block.networkPort(registration.id) == first && first.starts == 1 && registration.created.size() == 1,
                "Repeated activation and lookups must not duplicate factories or onLoad callbacks");
        var ticker = NeoForgeModRegistries.TRANSMUTATION_INTERFACE.get().getTicker(helper.getLevel(), block.getBlockState(),
                NeoForgeModRegistries.TRANSMUTATION_INTERFACE_ENTITY.get());
        check(ticker != null, "The registered interface must expose a production server ticker");
        ticker.tick(helper.getLevel(), block.getBlockPos(), block.getBlockState(), block);
        check(first.ticks == 1, "The production block ticker must reach the active network node");
        UUID identity = first.node;
        block.onChunkUnloaded();
        block.onChunkUnloaded();
        block.tickNetworks();
        check(first.unloads.equals(List.of(false)) && block.networkPort(registration.id) == null
                        && registration.created.size() == 1,
                "Chunk suspension must disconnect once without deletion or query-driven node recreation");
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        block.setLevel(null);
        try {
            CompoundTag cached = block.saveWithFullMetadata(registries);
            check(identity.equals(cached.getCompound("NetworkPorts").getCompound(registration.id).getUUID("node_identity"))
                            && registration.created.size() == 1 && first.unloads.equals(List.of(false)),
                    "Saving an unloaded interface must preserve cached identity without consulting or loading its world");
        } finally { block.setLevel(helper.getLevel()); }
        block.onLoad();
        Probe second = (Probe) block.networkPort(registration.id);
        check(second != null && second != first && identity.equals(second.node)
                        && registration.created.size() == 2 && second.loads == 1 && second.starts == 1,
                "Resumption must create one fresh live port using the saved native node identity");
        helper.setBlock(POSITION, Blocks.AIR);
        block.removeFromNetwork();
        block.setRemoved();
        block.onChunkUnloaded();
        block.onLoad();
        check(block.isRemoved() && second.unloads.equals(List.of(true)) && block.networkPort(registration.id) == null
                        && registration.created.size() == 2,
                "Actual block replacement must delete once and permanently reject stale node creation");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void disconnectsBeforeNbtReloadAndDoesNotRecreatePortsDuringTheLoadCallback(GameTestHelper helper) {
        NeoForgeTransmutationInterfaceBlockEntity block = place(helper, UUID.randomUUID());
        Registration registration = register(block, Probe::new);
        block.onLoad();
        Probe first = (Probe) block.networkPort(registration.id);
        CompoundTag saved = save(helper, block);
        UUID nextIdentity = UUID.randomUUID();
        saved.getCompound("NetworkPorts").getCompound(registration.id).putUUID("node_identity", nextIdentity);
        block.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(first.unloads.equals(List.of(false)) && registration.created.size() == 1
                        && block.networkPort(registration.id) == null,
                "In-place NBT load must suspend its old node without query-triggered callback duplication");
        block.tickNetworks();
        Probe restored = (Probe) block.networkPort(registration.id);
        check(restored != null && restored != first && nextIdentity.equals(restored.node)
                        && registration.created.size() == 2 && restored.starts == 1,
                "The next legitimate tick must resume from newly loaded node data exactly once");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void isolatesFactoryAndLifecycleFailuresWhilePreservingStableNodeData(GameTestHelper helper) {
        for (String phase : List.of("factory", "load", "onLoad", "tick", "save", "unload")) {
            NeoForgeTransmutationInterfaceBlockEntity block = place(helper, UUID.randomUUID());
            Registration good = register(block, Probe::new);
            Registration bad = register(block, () -> {
                if (phase.equals("factory")) throw new IllegalStateException("deliberate factory failure");
                Probe port = new Probe();
                port.failure = phase;
                return port;
            });
            UUID stableIdentity = UUID.randomUUID();
            CompoundTag original = save(helper, block);
            CompoundTag data = new CompoundTag();
            data.putUUID("node_identity", stableIdentity);
            CompoundTag network = new CompoundTag();
            network.put(bad.id, data);
            original.put("NetworkPorts", network);
            block.loadWithComponents(original, helper.getLevel().registryAccess());
            block.onLoad();
            block.tickNetworks();
            CompoundTag saved = save(helper, block);
            Probe healthy = (Probe) block.networkPort(good.id);
            check(healthy != null && healthy.starts == 1 && healthy.ticks == 1,
                    "A " + phase + " failure must not interrupt another adapter's lifecycle");
            if (!phase.equals("unload")) {
                check(block.networkPort(bad.id) == null && bad.attempts == 1,
                        "A failed " + phase + " adapter must remain disabled without repeated factory attempts");
            }
            check(stableIdentity.equals(saved.getCompound("NetworkPorts").getCompound(bad.id).getUUID("node_identity"))
                            && !saved.getCompound("NetworkPorts").getCompound(bad.id).contains("partial_failed_save"),
                    "A " + phase + " failure must retain stable node identity and discard partially written output");
            helper.setBlock(POSITION, Blocks.AIR);
            check(healthy.unloads.equals(List.of(true)), "Even a failing neighbour must not prevent healthy node deletion");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void defersRemovalDuringCallbacksAndRejectsRecursiveLifecycleQueries(GameTestHelper helper) {
        NeoForgeTransmutationInterfaceBlockEntity block = place(helper, UUID.randomUUID());
        Registration first = register(block, Probe::new);
        Registration other = register(block, Probe::new);
        block.onLoad();
        Probe port = (Probe) block.networkPort(first.id);
        Probe neighbour = (Probe) block.networkPort(other.id);
        int savesBefore = port.saves;
        List<String> failures = new ArrayList<>();
        port.saveAction = () -> {
            CompoundTag stable = save(helper, block);
            if (!block.owner().equals(stable.getUUID("owner"))) failures.add("Nested save lost owner identity");
            if (block.networkPort(first.id) != null) failures.add("Nested save exposed a live port");
            block.tickNetworks();
        };
        save(helper, block);
        check(port.saves == savesBefore + 1 && port.ticks == 0 && neighbour.ticks == 0 && failures.isEmpty(),
                "A nested save must use a stable snapshot without recursively saving or ticking live nodes");
        port.tickAction = () -> {
            if (block.networkPort(first.id) != null || block.networkPort(other.id) != null)
                failures.add("A lifecycle callback exposed another live port");
            block.tickNetworks();
            block.removeFromNetwork();
            if (!port.unloads.isEmpty() || !neighbour.unloads.isEmpty()) failures.add("Removal ran before its callback finished");
        };
        block.tickNetworks();
        check(failures.isEmpty() && port.ticks == 1 && neighbour.ticks <= 1
                        && port.unloads.equals(List.of(true)) && neighbour.unloads.equals(List.of(true)),
                "Recursive ticking must stop and callback-triggered deletion must run once after the active callback returns");
        check(block.networkPort(first.id) == null && first.attempts == 1 && other.attempts == 1,
                "A manually removed network must not be resurrected by an outstanding interface reference");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void refusesDetachedUnboundAndWorkerThreadPortsWithoutExternalCallbacks(GameTestHelper helper) {
        NeoForgeTransmutationInterfaceBlockEntity block = place(helper, null);
        Registration registration = register(block, Probe::new);
        block.onLoad();
        block.tickNetworks();
        check(block.networkPort(registration.id) == null && registration.attempts == 0,
                "An ownerless interface must not construct, activate or tick a network adapter");
        block.bindOwner(UUID.randomUUID());
        block.onLoad();
        Probe port = (Probe) block.networkPort(registration.id);
        check(port != null, "The owned main-thread fixture must expose its active test port before worker rejection");
        UUID ownerBefore = block.owner();
        CompoundTag before = save(helper, block);
        CompoundTag workerReplacement = new CompoundTag();
        workerReplacement.putUUID("owner", UUID.randomUUID());
        workerReplacement.putString("NetworkPorts", "worker must not replace this root");
        int saves = port.saves;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                if (block.networkPort(registration.id) != null) throw new AssertionError("Worker thread received a live node");
                block.tickNetworks();
                block.onLoad();
                block.loadWithComponents(workerReplacement, helper.getLevel().registryAccess());
            } catch (Throwable caught) { failure.set(caught); }
        }, "primordial-transmutation-lifecycle-test");
        worker.setDaemon(true);
        worker.start();
        try { worker.join(3000); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GameTestAssertException("Interrupted while checking network worker-thread rejection");
        }
        check(!worker.isAlive() && failure.get() == null && port.starts == 1 && port.ticks == 0
                        && port.saves == saves && registration.attempts == 1,
                "Off-thread calls must not run factories or any network lifecycle callbacks");
        check(ownerBefore.equals(block.owner()) && block.networkPort(registration.id) == port && port.unloads.isEmpty()
                        && before.equals(save(helper, block)),
                "Off-thread NBT loading must preserve the existing account, live node and exact stored network payload");
        NeoForgeTransmutationInterfaceBlockEntity detached = new NeoForgeTransmutationInterfaceBlockEntity(
                helper.absolutePos(POSITION), NeoForgeModRegistries.TRANSMUTATION_INTERFACE.get().defaultBlockState());
        check(detached.networkPort(registration.id) == null, "An unattached block entity must not discover network ports");
        block.setRemoved();
        block.tickNetworks();
        block.onLoad();
        check(block.networkPort(registration.id) == null && port.unloads.equals(List.of(false)) && registration.attempts == 1,
                "setRemoved must suspend once and subsequent stale calls must not recreate nodes");
        helper.succeed();
    }

    private static List<CompoundTag> malformedOwnerTags() {
        CompoundTag string = new CompoundTag();
        string.putString("owner", "not-a-uuid");
        CompoundTag array = new CompoundTag();
        array.putIntArray("owner", new int[]{1, 2, 3});
        return List.of(string, array);
    }

    private static Registration register(NeoForgeTransmutationInterfaceBlockEntity target, Supplier<Probe> factory) {
        Registration registration = new Registration();
        NeoForgeTransmutationNetworkRegistry.register(registration.id, candidate -> {
            if (candidate != target) return null;
            registration.attempts++;
            Probe port = factory.get();
            registration.created.add(port);
            return port;
        });
        check(NeoForgeTransmutationNetworkRegistry.registeredIds().contains(registration.id), "The test adapter must use the production registry");
        return registration;
    }

    private static NeoForgeTransmutationInterfaceBlockEntity place(GameTestHelper helper, UUID owner) {
        helper.setBlock(POSITION, Blocks.AIR);
        helper.setBlock(POSITION, NeoForgeModRegistries.TRANSMUTATION_INTERFACE.get());
        NeoForgeTransmutationInterfaceBlockEntity block = require(helper);
        if (owner != null) block.bindOwner(owner);
        return block;
    }

    private static NeoForgeTransmutationInterfaceBlockEntity require(GameTestHelper helper) {
        if (!(helper.getBlockEntity(POSITION) instanceof NeoForgeTransmutationInterfaceBlockEntity block))
            throw new GameTestAssertException("Expected the registered transmutation interface block entity");
        return block;
    }

    private static CompoundTag save(GameTestHelper helper, NeoForgeTransmutationInterfaceBlockEntity block) {
        return block.saveWithFullMetadata(helper.getLevel().registryAccess());
    }

    private static void noOrdinaryPorts(GameTestHelper helper, Direction side) {
        BlockPos position = helper.absolutePos(POSITION);
        check(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, position, side) == null
                        && helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, position, side) == null
                        && helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, position, side) == null,
                "A virtual catalogue interface must not expose an ordinary item/fluid/FE inventory on side " + side);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class Registration {
        final String id = "test_" + UUID.randomUUID().toString().replace("-", "");
        final List<Probe> created = new ArrayList<>();
        int attempts;
    }

    private static final class Probe implements NeoForgeTransmutationNetworkPort {
        UUID node = UUID.randomUUID();
        int loads, starts, ticks, saves;
        String failure;
        Runnable tickAction, saveAction;
        final List<Boolean> unloads = new ArrayList<>();

        @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
            loads++;
            if (tag.hasUUID("node_identity")) node = tag.getUUID("node_identity");
            fail("load");
        }
        @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
            saves++;
            tag.putUUID("node_identity", node);
            if (saveAction != null) saveAction.run();
            if ("save".equals(failure)) {
                tag.putString("partial_failed_save", "discard this unconfirmed mutation");
                throw new NoClassDefFoundError("deliberate incompatible port save");
            }
        }
        @Override public void onLoad() { starts++; fail("onLoad"); }
        @Override public void tick() {
            ticks++;
            if (tickAction != null) tickAction.run();
            fail("tick");
        }
        @Override public void unload(boolean removed) { unloads.add(removed); fail("unload"); }
        private void fail(String phase) {
            if (phase.equals(failure)) throw new IllegalStateException("deliberate port " + phase + " failure");
        }
    }
}
