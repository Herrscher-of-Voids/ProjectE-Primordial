/**
 * 【文件职责】用真实网络生命周期回调验证转化接口的延后拆除及 owner/节点 NBT 同代恢复。
 * 【变更记录 #1】2026-10-22 23:00:23（第 1 次备注 · 2.0.0-neo-dev 回调恢复一致性）
 * - 删除：无。新增：load/onLoad 回调拆方块、恢复新 UUID，以及 save/unload 内嵌套恢复的四项 GameTest。
 * - 修改：端口仅测试源注册且按宿主对象匹配；不同归属与节点 UUID 均通过正式 NBT 入口写入。
 * - 用途：旧节点清理完成前不得改变账户；嵌套的新快照必须整体胜出，禁止保存 owner 与节点属于不同代的数据。
 * 【变更记录 #2】2026-10-22 23:02:39（第 2 次备注 · 2.0.0-neo-dev 初始化中断保护）
 * - 删除：无。新增：factory/load 回调暂停后，不保存未初始化候选、保留旧节点身份的回归。
 * - 修改：候选清理次数与 save 次数分别验证；恢复后必须从原身份创建完整的新节点。
 * - 用途：防止尚未读入存档的空端口覆盖已持久保存的 AE2/RS 节点身份。
 */
package com.primordial.neoforge.transmutation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeTransmutationRestoreGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeTransmutationRestoreGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesOldIdentityWhenFactoryOrLoadSuspendsInitialization(GameTestHelper helper) {
        for (String phase : List.of("factory", "load")) {
            UUID owner = UUID.randomUUID();
            UUID stableNode = UUID.randomUUID();
            NeoForgeTransmutationInterfaceBlockEntity block = place(helper, owner);
            Registration registration = new Registration();
            AtomicBoolean interrupted = new AtomicBoolean();
            NeoForgeTransmutationNetworkRegistry.register(registration.id, candidate -> {
                if (candidate != block) return null;
                Probe probe = new Probe(registration);
                registration.created.add(probe);
                if (interrupted.compareAndSet(false, true)) {
                    if (phase.equals("factory")) block.onChunkUnloaded();
                    else probe.loadAction = block::onChunkUnloaded;
                }
                return probe;
            });
            block.loadWithComponents(state(owner, registration.id, stableNode), helper.getLevel().registryAccess());
            block.onLoad();
            Probe interruptedPort = registration.created.getFirst();
            check(interruptedPort.loads == (phase.equals("factory") ? 0 : 1) && interruptedPort.starts == 0
                            && interruptedPort.saves == 0 && interruptedPort.unloads.equals(List.of(false))
                            && block.networkPort(registration.id) == null,
                    "A " + phase + " interruption must clean up its candidate once without saving its unconfirmed identity");
            assertPair(save(helper, block), registration.id, owner, stableNode);
            check(interruptedPort.saves == 0, "Saving the suspended host must not call its discarded incomplete candidate");
            block.onLoad();
            Probe resumed = (Probe) block.networkPort(registration.id);
            check(resumed != null && resumed != interruptedPort && stableNode.equals(resumed.node)
                            && resumed.loads == 1 && resumed.starts == 1 && registration.created.size() == 2,
                    "A complete activation must resume with the original saved identity after an initialization interruption");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void defersActualRemovalDuringNativePortLoadAndOnLoadCallbacks(GameTestHelper helper) {
        for (String phase : List.of("load", "onLoad")) {
            NeoForgeTransmutationInterfaceBlockEntity block = place(helper, UUID.randomUUID());
            List<String> failures = new ArrayList<>();
            Registration registration = register(block, probe -> {
                Runnable remove = () -> {
                    helper.setBlock(POSITION, Blocks.AIR);
                    if (!probe.unloads.isEmpty()) failures.add("Node cleanup ran before " + phase + " returned");
                    if (block.networkPort(probe.id) != null) failures.add("Removed callback exposed a live node");
                };
                if (phase.equals("load")) probe.loadAction = remove;
                else probe.startAction = remove;
            });
            block.onLoad();
            Probe port = registration.created.getFirst();
            check(failures.isEmpty() && block.isRemoved() && registration.created.size() == 1
                            && port.loads == 1 && port.starts == (phase.equals("load") ? 0 : 1)
                            && port.unloads.equals(List.of(true)) && block.networkPort(registration.id) == null,
                    "Actual removal inside " + phase + " must stop initialization and delete its in-flight node exactly once");
            block.onLoad();
            block.tickNetworks();
            check(registration.created.size() == 1 && port.unloads.equals(List.of(true)),
                    "Callbacks remaining after real removal must not restart or downgrade the deletion");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void restoresOwnerAndNodeAfterLoadAndOnLoadCallbacksFinishCleaningTheOldOwner(GameTestHelper helper) {
        for (String phase : List.of("load", "onLoad")) {
            UUID oldOwner = UUID.randomUUID();
            UUID nextOwner = UUID.randomUUID();
            UUID nextNode = UUID.randomUUID();
            NeoForgeTransmutationInterfaceBlockEntity block = place(helper, oldOwner);
            AtomicBoolean changed = new AtomicBoolean();
            List<String> failures = new ArrayList<>();
            Registration registration = register(block, probe -> {
                Runnable restore = () -> {
                    if (!changed.compareAndSet(false, true)) return;
                    block.loadWithComponents(state(nextOwner, probe.id, nextNode), helper.getLevel().registryAccess());
                    if (!oldOwner.equals(block.owner())) failures.add("The active callback observed the next owner too early");
                    if (block.networkPort(probe.id) != null) failures.add("Restore recursively exposed a node");
                };
                probe.unloadAction = () -> {
                    if (probe == registrationFirst(probe) && !oldOwner.equals(block.owner()))
                        failures.add("The old node was unloaded under the next owner's account");
                };
                if (phase.equals("load")) probe.loadAction = restore;
                else probe.startAction = restore;
            });
            block.onLoad();
            Probe first = registration.created.getFirst();
            check(failures.isEmpty() && first.unloads.equals(List.of(false))
                            && first.starts == (phase.equals("load") ? 0 : 1)
                            && registration.created.size() == 1 && block.networkPort(registration.id) == null,
                    "Restore during " + phase + " must invalidate initialization and finish old-owner cleanup before publication");
            assertPair(save(helper, block), registration.id, nextOwner, nextNode);
            block.tickNetworks();
            Probe restored = (Probe) block.networkPort(registration.id);
            check(restored != null && restored != first && nextNode.equals(restored.node)
                            && nextOwner.equals(block.owner()) && registration.created.size() == 2 && restored.starts == 1,
                    "A fresh legitimate tick must resume exactly one node from the newly published owner/node pair");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void savesMatchingOwnerAndNodeWhenPortSaveTriggersRestore(GameTestHelper helper) {
        UUID oldOwner = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();
        UUID nextNode = UUID.randomUUID();
        NeoForgeTransmutationInterfaceBlockEntity block = place(helper, oldOwner);
        Registration registration = register(block, ignored -> {});
        block.onLoad();
        Probe first = (Probe) block.networkPort(registration.id);
        AtomicBoolean changed = new AtomicBoolean();
        List<String> failures = new ArrayList<>();
        first.saveAction = () -> {
            if (!changed.compareAndSet(false, true)) return;
            block.loadWithComponents(state(nextOwner, registration.id, nextNode), helper.getLevel().registryAccess());
            if (!oldOwner.equals(block.owner())) failures.add("Save callback saw a premature owner change");
        };
        first.unloadAction = () -> {
            if (!oldOwner.equals(block.owner())) failures.add("Final cleanup observed the new owner");
        };
        CompoundTag outerSave = save(helper, block);
        assertPair(outerSave, registration.id, nextOwner, nextNode);
        check(failures.isEmpty() && first.unloads.equals(List.of(false)) && nextOwner.equals(block.owner())
                        && registration.created.size() == 1 && block.networkPort(registration.id) == null,
                "An outer save must contain one complete restored owner/node pair and leave old ports detached");
        block.tickNetworks();
        check(((Probe) block.networkPort(registration.id)).node.equals(nextNode),
                "The saved restored identity must also be the next live identity");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void keepsTheInnermostOwnerNodePairWhenUnloadRestoresAgain(GameTestHelper helper) {
        UUID oldOwner = UUID.randomUUID();
        UUID outerOwner = UUID.randomUUID();
        UUID outerNode = UUID.randomUUID();
        UUID innerOwner = UUID.randomUUID();
        UUID innerNode = UUID.randomUUID();
        NeoForgeTransmutationInterfaceBlockEntity block = place(helper, oldOwner);
        Registration registration = register(block, ignored -> {});
        block.onLoad();
        Probe first = (Probe) block.networkPort(registration.id);
        AtomicBoolean changed = new AtomicBoolean();
        List<String> failures = new ArrayList<>();
        first.unloadAction = () -> {
            if (!changed.compareAndSet(false, true)) return;
            if (!oldOwner.equals(block.owner())) failures.add("Outer restore changed owner before old cleanup");
            block.loadWithComponents(state(innerOwner, registration.id, innerNode), helper.getLevel().registryAccess());
            if (!oldOwner.equals(block.owner())) failures.add("Nested restore changed owner during old cleanup");
        };
        block.loadWithComponents(state(outerOwner, registration.id, outerNode), helper.getLevel().registryAccess());
        check(failures.isEmpty() && changed.get() && first.unloads.equals(List.of(false)) && innerOwner.equals(block.owner()),
                "Cleanup-triggered restore must finish under the old owner and then publish the latest nested owner");
        assertPair(save(helper, block), registration.id, innerOwner, innerNode);
        block.onLoad();
        Probe restored = (Probe) block.networkPort(registration.id);
        check(restored != null && innerNode.equals(restored.node) && registration.created.size() == 2,
                "The outer restore must never overwrite the inner owner's corresponding node identity");
        helper.succeed();
    }

    private static Probe registrationFirst(Probe probe) {
        return probe.registration.created.isEmpty() ? null : probe.registration.created.getFirst();
    }

    private static CompoundTag state(UUID owner, String id, UUID node) {
        CompoundTag data = new CompoundTag();
        data.putUUID("node_identity", node);
        CompoundTag ports = new CompoundTag();
        ports.put(id, data);
        CompoundTag state = new CompoundTag();
        state.putUUID("owner", owner);
        state.put("NetworkPorts", ports);
        return state;
    }

    private static void assertPair(CompoundTag saved, String id, UUID owner, UUID node) {
        check(saved.hasUUID("owner") && owner.equals(saved.getUUID("owner"))
                        && node.equals(saved.getCompound("NetworkPorts").getCompound(id).getUUID("node_identity")),
                "Persisted owner and network node must come from the same complete restored snapshot");
    }

    private static Registration register(NeoForgeTransmutationInterfaceBlockEntity block, Consumer<Probe> prepare) {
        Registration registration = new Registration();
        NeoForgeTransmutationNetworkRegistry.register(registration.id, candidate -> {
            if (candidate != block) return null;
            Probe probe = new Probe(registration);
            prepare.accept(probe);
            registration.created.add(probe);
            return probe;
        });
        return registration;
    }

    private static NeoForgeTransmutationInterfaceBlockEntity place(GameTestHelper helper, UUID owner) {
        helper.setBlock(POSITION, Blocks.AIR);
        helper.setBlock(POSITION, NeoForgeModRegistries.TRANSMUTATION_INTERFACE.get());
        if (!(helper.getBlockEntity(POSITION) instanceof NeoForgeTransmutationInterfaceBlockEntity block))
            throw new GameTestAssertException("Expected a real registered transmutation interface");
        block.bindOwner(owner);
        return block;
    }

    private static CompoundTag save(GameTestHelper helper, NeoForgeTransmutationInterfaceBlockEntity block) {
        return block.saveWithFullMetadata(helper.getLevel().registryAccess());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class Registration {
        final String id = "restore_" + UUID.randomUUID().toString().replace("-", "");
        final List<Probe> created = new ArrayList<>();
    }

    private static final class Probe implements NeoForgeTransmutationNetworkPort {
        final Registration registration;
        final String id;
        UUID node = UUID.randomUUID();
        int loads, starts, saves;
        Runnable loadAction, startAction, saveAction, unloadAction;
        final List<Boolean> unloads = new ArrayList<>();

        Probe(Registration registration) { this.registration = registration; id = registration.id; }
        @Override public void load(CompoundTag tag, HolderLookup.Provider registries) {
            loads++;
            if (tag.hasUUID("node_identity")) node = tag.getUUID("node_identity");
            if (loadAction != null) loadAction.run();
        }
        @Override public void onLoad() { starts++; if (startAction != null) startAction.run(); }
        @Override public void save(CompoundTag tag, HolderLookup.Provider registries) {
            saves++;
            tag.putUUID("node_identity", node);
            if (saveAction != null) saveAction.run();
        }
        @Override public void unload(boolean removed) { unloads.add(removed); if (unloadAction != null) unloadAction.run(); }
    }
}
