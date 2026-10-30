/**
 * 【文件职责】转化接口端口状态机：离线 NBT 快照、服务端门禁、重入隔离与独立故障降级。
 * 【变更记录 #1】2026-10-22 22:54:44（第 1 次备注 · 2.0.0-neo-dev 转化接口基础）
 * - 删除：无。新增：原样保留未知/错类型 NBT，generation 防旧回调，暂停保留身份与拆除永久关闭。
 * - 修改：先移走在线集合再保存/断开；回调期间请求延后清理，重入保存只读上次稳定快照。
 * - 用途：避免重复节点、卸载重载区块及单个可选适配器异常造成其它节点或方块存档丢失。
 * 【变更记录 #2】2026-10-22 22:59:40（第 2 次备注 · 同代恢复提交）
 * - 删除：仅排队网络根数据的载入。新增：与根数据绑定的归属提交回调。
 * - 修改：先断开整批旧节点，最后发布最新一次排队的节点与归属；保存不混合不同载入请求的数据。
 * - 用途：嵌套保存/卸载回调改变 NBT 时，旧节点仍按旧账户清理，新节点与新账户保持配对。
 * 【变更记录 #3】2026-10-22 23:01:25（第 3 次备注 · 中断初始化保留原始身份）
 * - 删除：对尚未完成激活的候选节点执行最终保存。新增：已完成 load/onLoad 的节点集合。
 * - 修改：仅同代成功初始化的节点可以提交快照；工厂或初始化中断仍断开候选但不覆盖旧 NBT。
 * - 用途：防止未载入或部分载入的默认随机节点身份替换真实存档。
 */
package com.primordial.neoforge.transmutation;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class NeoForgeTransmutationNetworkState {
    private final NeoForgeTransmutationInterfaceBlockEntity host;
    private final Map<String, NeoForgeTransmutationNetworkPort> online = new ConcurrentHashMap<>();
    private final Set<String> attempted = ConcurrentHashMap.newKeySet();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final Set<String> initialized = ConcurrentHashMap.newKeySet();
    private final Set<String> warned = ConcurrentHashMap.newKeySet();
    /** 发布后不再修改；异线程或回调中的保存只能复制这个稳定值。 */
    private volatile Tag root = new CompoundTag();
    private boolean active, removed, busy, resumeOnTick;
    private boolean pendingDetach, pendingRemoval, skipCheckpoint;
    private long generation;
    @Nullable private Tag pendingRoot;
    @Nullable private Runnable pendingIdentity;
    @Nullable private HolderLookup.Provider registries;

    NeoForgeTransmutationNetworkState(NeoForgeTransmutationInterfaceBlockEntity host) { this.host = host; }
    boolean busy() { return busy; }

    private boolean ready() {
        return active && !removed && host.owner() != null && root instanceof CompoundTag && host.attachedServer();
    }

    void activate() {
        if (busy || removed || !host.attachedServer()) return;
        if (!active) {
            active = true;
            resumeOnTick = false;
            generation++;
            attempted.clear();
            failed.clear();
            registries = host.getLevel().registryAccess();
        }
        discover();
    }

    private void discover() {
        for (String id : NeoForgeTransmutationNetworkRegistry.registeredIds()) {
            if (!ready()) break;
            port(id);
        }
    }

    @Nullable NeoForgeTransmutationNetworkPort port(String id) {
        if (busy || !ready() || !NeoForgeTransmutationNetworkRegistry.contains(id) || failed.contains(id)) return null;
        NeoForgeTransmutationNetworkPort existing = online.get(id);
        if (existing != null) return existing;
        if (!attempted.add(id)) return null;
        CompoundTag data = (CompoundTag) root;
        Tag raw = data.get(id);
        if (raw != null && !(raw instanceof CompoundTag)) {
            failed.add(id);
            warn(id, "invalid stored payload", null);
            return null;
        }
        long epoch = generation;
        busy = true;
        try {
            NeoForgeTransmutationNetworkPort candidate = NeoForgeTransmutationNetworkRegistry.create(id, host);
            if (candidate != null) {
                online.put(id, candidate);
                if (epoch == generation && ready()) candidate.load(raw == null ? new CompoundTag() : ((CompoundTag) raw).copy(), registries);
                if (epoch == generation && ready()) candidate.onLoad();
                if (epoch == generation && ready()) {
                    initialized.add(id);
                    host.setChanged();
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            disable(id, "activation", failure);
        } finally {
            busy = false;
            drain();
        }
        return ready() && !failed.contains(id) ? online.get(id) : null;
    }

    void tick() {
        if (busy || !host.serverThread()) return;
        if (!active && resumeOnTick) activate();
        if (!ready()) return;
        discover();
        long epoch = generation;
        for (var entry : entries()) {
            if (epoch != generation || !ready()) break;
            if (online.get(entry.getKey()) != entry.getValue()) continue;
            busy = true;
            try { entry.getValue().tick(); }
            catch (RuntimeException | LinkageError failure) { disable(entry.getKey(), "tick", failure); }
            finally { busy = false; drain(); }
        }
    }

    Tag snapshot(HolderLookup.Provider provider) {
        if (!busy && host.serverThread()) {
            registries = provider;
            long epoch = generation;
            for (var entry : entries()) {
                if (epoch != generation) break;
                busy = true;
                try {
                    if (initialized.contains(entry.getKey())) checkpoint(entry.getKey(), entry.getValue(), provider, epoch);
                }
                catch (RuntimeException | LinkageError failure) { disable(entry.getKey(), "save", failure); }
                finally { busy = false; drain(); }
            }
        }
        return root.copy();
    }

    /** 新读入的 NBT 是权威来源；不让旧节点的最终保存覆盖它，也不在解析中创建节点。 */
    void restore(Tag replacement, Runnable identity) {
        boolean resume = active || resumeOnTick;
        pendingRoot = replacement.copy();
        pendingIdentity = identity;
        skipCheckpoint = true;
        requestStop(false);
        resumeOnTick = resume && !removed;
        if (!busy) drain();
    }

    void stop(boolean deleting) {
        // 本地未挂载对象没有网络；已挂载对象的生命周期只在服务器主线程处理。
        if (!host.serverThread()) return;
        requestStop(deleting);
        resumeOnTick = false;
        if (!busy) drain();
    }

    private void requestStop(boolean deleting) {
        active = false;
        generation++;
        removed |= deleting;
        pendingDetach = true;
        pendingRemoval |= deleting;
    }

    private void drain() {
        if (busy) return;
        while (pendingDetach) {
            boolean deleting = pendingRemoval || removed;
            boolean save = !skipCheckpoint;
            pendingDetach = pendingRemoval = skipCheckpoint = false;
            var closing = entries();
            Set<String> saveable = Set.copyOf(initialized);
            online.clear();
            initialized.clear();
            attempted.clear();
            failed.clear();
            long epoch = generation;
            busy = true;
            try {
                for (var entry : closing) {
                    if (save && saveable.contains(entry.getKey()) && registries != null) {
                        try { checkpoint(entry.getKey(), entry.getValue(), registries, epoch); }
                        catch (RuntimeException | LinkageError failure) { warn(entry.getKey(), "final save", failure); }
                    }
                    try { entry.getValue().unload(deleting || removed); }
                    catch (RuntimeException | LinkageError failure) { warn(entry.getKey(), "unload", failure); }
                }
            } finally { busy = false; }
            if (pendingRoot != null) {
                root = pendingRoot;
                pendingRoot = null;
                Runnable identity = pendingIdentity;
                pendingIdentity = null;
                identity.run();
            }
        }
    }

    private void checkpoint(String id, NeoForgeTransmutationNetworkPort port, HolderLookup.Provider provider, long epoch) {
        CompoundTag saved = new CompoundTag();
        port.save(saved, provider);
        if (epoch == generation && pendingRoot == null && root instanceof CompoundTag data) {
            CompoundTag next = data.copy();
            next.put(id, saved.copy());
            root = next;
        }
    }

    private void disable(String id, String phase, Throwable failure) {
        failed.add(id);
        warn(id, phase, failure);
        if (pendingDetach) return; // 拆除请求的回调结束后统一处理，保留真正删除的原因。
        NeoForgeTransmutationNetworkPort port = online.remove(id);
        initialized.remove(id);
        if (port != null) {
            try { port.unload(false); }
            catch (RuntimeException | LinkageError cleanupFailure) { warn(id, "failed-port cleanup", cleanupFailure); }
        }
    }

    private ArrayList<Map.Entry<String, NeoForgeTransmutationNetworkPort>> entries() {
        var entries = new ArrayList<>(online.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        return entries;
    }

    private void warn(String id, String phase, @Nullable Throwable failure) {
        if (warned.add(id)) LogUtils.getLogger().warn("[Primordial] Network port {} disabled during {} at {}",
                id, phase, host.getBlockPos(), failure);
    }
}
