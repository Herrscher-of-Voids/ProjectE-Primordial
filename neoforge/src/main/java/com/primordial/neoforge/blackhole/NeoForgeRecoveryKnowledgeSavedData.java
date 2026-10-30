/**
 * 【文件职责】世界级回收知识账本；原生 ItemInfo 待学队列、已学流体与化学品 ID 分开保存。
 * 【变更记录 #1】2026-10-22 21:40:04（第 1 次备注 · 2.0.0-neo-dev 黑洞实际回收）
 * - 删除：无。
 * - 新增：注册表感知的 ItemInfo 编解码、去重、学习确认、流体知识及损坏原始条目隔离。
 * - 修改：未知根格式保留整个快照并禁止接收；坏物品单独隔离，不使其余玩家知识消失。
 * - 用途：在线能力不可用或玩家离线时持久排队；仅处理本版本新档，不读写旧版玩家文件。
 * 【变更记录 #2】2026-10-22 22:14:49（第 2 次备注 · 2.0.0-neo-dev 可选化学品知识）
 * - 删除：无。
 * - 新增：chemicals 独立去重表、ID 学习查询、原始条目隔离及旧 schema 1 缺字段兼容。
 * - 修改：保持 schema 1 和 items/、fluids/ 隔离键；chemicals 根类型错误封锁整档，子项错误单独保留。
 * - 用途：不依赖 Mekanism API 即可保存离线化学品知识，同 ID 的流体与化学品互不授予知识。
 * 【变更记录 #3】2026-10-23 13:32:30（第 3 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：学习只能增加正面知识、不能清除显式遗忘的限制。
 * - 新增：流体/化学品遗忘表、幂等学习和撤销、逐玩家损坏隔离及持久封锁集合。
 * - 修改：旧 schema 1 缺少新表仍兼容；新表根错误保留整档，损坏子项原文隔离并禁止对应玩家授权。
 * - 用途：遗忘优先于类别凭证和免知识配置，重启后也不能因损坏排除表错误恢复权限。
 * 【变更记录 #4】2026-10-23 13:39:36（第 4 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。新增：逐玩家进程内知识变更序号，真实 learn/forget 变更推进。
 * - 修改：幂等和查询不推进；序号不持久化，也不替代坏档/遗忘授权检查。
 * - 用途：公共报价识别外部标签回调内先忘记再学回的变化，避免混用授权快照。
 */
package com.primordial.neoforge.blackhole;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;

import moze_intel.projecte.api.ItemInfo;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class NeoForgeRecoveryKnowledgeSavedData extends SavedData {
    private static final String DATA_NAME = "primordial_recovery_knowledge";
    private static final Factory<NeoForgeRecoveryKnowledgeSavedData> FACTORY = new Factory<>(
            NeoForgeRecoveryKnowledgeSavedData::new, NeoForgeRecoveryKnowledgeSavedData::load);
    // 已编码的快照避免保存阶段再次执行第三方组件 codec，ItemInfo 作为键保证组件级去重。
    private final Map<UUID, Map<ItemInfo, CompoundTag>> items = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> fluids = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> chemicals = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> forgottenFluids = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ResourceLocation>> forgottenChemicals = new ConcurrentHashMap<>();
    private final Set<UUID> blockedOwners = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();
    private final CompoundTag quarantined = new CompoundTag();
    private CompoundTag blockedSnapshot;

    public static NeoForgeRecoveryKnowledgeSavedData get(MinecraftServer server) {
        NeoForgeEmcCredits.requireServerThread(server);
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized boolean available() {
        return blockedSnapshot == null;
    }

    public synchronized boolean available(UUID owner) {
        return owner != null && available() && !blockedOwners.contains(owner);
    }

    public synchronized long revision(UUID owner) {
        return available(owner) ? revisions.getOrDefault(owner, 0L) : -1L;
    }

    synchronized void queue(UUID owner, ItemInfo info, HolderLookup.Provider registries) {
        if (!available(owner)) return;
        Map<ItemInfo, CompoundTag> pending = items.get(owner);
        if (pending != null && pending.containsKey(info)) return;
        Tag encoded = ItemInfo.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), info)
                .getOrThrow();
        if (!(encoded instanceof CompoundTag compound)) throw new IllegalArgumentException("Invalid ItemInfo encoding");
        items.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>()).putIfAbsent(info, compound.copy());
        setDirty();
    }

    synchronized Set<ItemInfo> pending(UUID owner) {
        Map<ItemInfo, CompoundTag> pending = items.get(owner);
        return pending == null ? Set.of() : Set.copyOf(pending.keySet());
    }

    public synchronized int pendingItemCount(UUID owner) {
        Map<ItemInfo, CompoundTag> pending = items.get(owner);
        return pending == null ? 0 : pending.size();
    }

    synchronized void acknowledge(UUID owner, ItemInfo info) {
        Map<ItemInfo, CompoundTag> pending = items.get(owner);
        if (pending != null && pending.remove(info) != null) {
            if (pending.isEmpty()) items.remove(owner);
            setDirty();
        }
    }

    synchronized void recordFluid(UUID owner, ResourceLocation id) {
        learn(owner, false, id);
    }

    public synchronized boolean knowsFluid(UUID owner, ResourceLocation id) {
        Set<ResourceLocation> known = fluids.get(owner);
        return available(owner) && known != null && known.contains(id);
    }

    synchronized void recordChemical(UUID owner, ResourceLocation id) {
        learn(owner, true, id);
    }

    public synchronized boolean knowsChemical(UUID owner, ResourceLocation id) {
        Set<ResourceLocation> known = chemicals.get(owner);
        return available(owner) && known != null && known.contains(id);
    }

    public synchronized boolean isForgotten(UUID owner, boolean chemical, ResourceLocation id) {
        Set<ResourceLocation> excluded = (chemical ? forgottenChemicals : forgottenFluids).get(owner);
        return excluded != null && excluded.contains(id);
    }

    /** 本层只保存已验证 ID；服务/API 必须先核对真实注册资源。 */
    public synchronized boolean learn(UUID owner, boolean chemical, ResourceLocation id) {
        if (!available(owner) || id == null) return false;
        Map<UUID, Set<ResourceLocation>> known = chemical ? chemicals : fluids;
        Map<UUID, Set<ResourceLocation>> forgotten = chemical ? forgottenChemicals : forgottenFluids;
        boolean changed = known.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(id);
        changed |= removeId(forgotten, owner, id);
        if (changed) { revisions.merge(owner, 1L, Long::sum); setDirty(); }
        return changed;
    }

    public synchronized boolean forget(UUID owner, boolean chemical, ResourceLocation id) {
        if (!available(owner) || id == null) return false;
        Map<UUID, Set<ResourceLocation>> known = chemical ? chemicals : fluids;
        Map<UUID, Set<ResourceLocation>> forgotten = chemical ? forgottenChemicals : forgottenFluids;
        boolean changed = removeId(known, owner, id);
        changed |= forgotten.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(id);
        if (changed) { revisions.merge(owner, 1L, Long::sum); setDirty(); }
        return changed;
    }

    private static boolean removeId(Map<UUID, Set<ResourceLocation>> source, UUID owner, ResourceLocation id) {
        Set<ResourceLocation> ids = source.get(owner);
        if (ids == null || !ids.remove(id)) return false;
        if (ids.isEmpty()) source.remove(owner);
        return true;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!available()) return blockedSnapshot.copy();
        CompoundTag itemOwners = new CompoundTag();
        items.forEach((owner, pending) -> {
            ListTag list = new ListTag();
            pending.values().forEach(encoded -> list.add(encoded.copy()));
            if (!list.isEmpty()) itemOwners.put(owner.toString(), list);
        });
        tag.putInt("schema", 1);
        tag.put("items", itemOwners);
        tag.put("fluids", saveIds(fluids));
        tag.put("chemicals", saveIds(chemicals));
        tag.put("forgotten_fluids", saveIds(forgottenFluids));
        tag.put("forgotten_chemicals", saveIds(forgottenChemicals));
        ListTag blocked = new ListTag();
        blockedOwners.stream().map(UUID::toString).sorted().forEach(owner -> blocked.add(StringTag.valueOf(owner)));
        if (!blocked.isEmpty()) tag.put("blocked_owners", blocked);
        if (!quarantined.isEmpty()) tag.put("quarantine", quarantined.copy());
        return tag;
    }

    public static NeoForgeRecoveryKnowledgeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NeoForgeRecoveryKnowledgeSavedData data = new NeoForgeRecoveryKnowledgeSavedData();
        // 加载器会在异常后建空档，因此根格式异常必须以只读快照返回，不能抛出后丢失旧数据。
        if (!tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1
                || !tag.contains("items", Tag.TAG_COMPOUND) || !tag.contains("fluids", Tag.TAG_COMPOUND)
                || tag.contains("chemicals") && !tag.contains("chemicals", Tag.TAG_COMPOUND)
                || tag.contains("forgotten_fluids") && !tag.contains("forgotten_fluids", Tag.TAG_COMPOUND)
                || tag.contains("forgotten_chemicals") && !tag.contains("forgotten_chemicals", Tag.TAG_COMPOUND)
                || tag.contains("blocked_owners") && !tag.contains("blocked_owners", Tag.TAG_LIST)
                || tag.contains("quarantine") && !tag.contains("quarantine", Tag.TAG_COMPOUND)) {
            data.blockedSnapshot = tag.copy();
            LogUtils.getLogger().error("[Primordial] Unsupported recovery knowledge ledger; original data preserved, recovery blocked");
            return data;
        }
        if (tag.contains("quarantine", Tag.TAG_COMPOUND)) data.quarantined.merge(tag.getCompound("quarantine").copy());
        if (tag.contains("blocked_owners")) {
            for (Tag raw : (ListTag) tag.get("blocked_owners")) {
                try {
                    if (!(raw instanceof StringTag)) throw new IllegalArgumentException("Expected UUID string");
                    UUID owner = UUID.fromString(raw.getAsString());
                    if (!owner.toString().equals(raw.getAsString())) throw new IllegalArgumentException("Noncanonical UUID");
                    data.blockedOwners.add(owner);
                } catch (RuntimeException invalid) {
                    data.blockedSnapshot = tag.copy();
                    return data;
                }
            }
        }
        data.loadOwners(tag.getCompound("items"), registries, "items");
        data.loadOwners(tag.getCompound("fluids"), registries, "fluids");
        // 早期本版本 schema 1 没有 chemicals；缺字段只表示尚未记录化学品知识。
        if (tag.contains("chemicals", Tag.TAG_COMPOUND)) data.loadOwners(tag.getCompound("chemicals"), registries, "chemicals");
        if (tag.contains("forgotten_fluids", Tag.TAG_COMPOUND)) data.loadOwners(tag.getCompound("forgotten_fluids"), registries, "forgotten_fluids");
        if (tag.contains("forgotten_chemicals", Tag.TAG_COMPOUND)) data.loadOwners(tag.getCompound("forgotten_chemicals"), registries, "forgotten_chemicals");
        return data;
    }

    private static CompoundTag saveIds(Map<UUID, Set<ResourceLocation>> source) {
        CompoundTag owners = new CompoundTag();
        source.forEach((owner, known) -> {
            ListTag list = new ListTag();
            known.stream().map(ResourceLocation::toString).sorted().forEach(id -> list.add(StringTag.valueOf(id)));
            if (!list.isEmpty()) owners.put(owner.toString(), list);
        });
        return owners;
    }

    private void loadOwners(CompoundTag owners, HolderLookup.Provider registries, String channel) {
        boolean itemEntries = channel.equals("items");
        Map<UUID, Set<ResourceLocation>> ids = switch (channel) {
            case "chemicals" -> chemicals;
            case "forgotten_fluids" -> forgottenFluids;
            case "forgotten_chemicals" -> forgottenChemicals;
            default -> fluids;
        };
        String section = channel + "/";
        for (String key : owners.getAllKeys()) {
            Tag raw = Objects.requireNonNull(owners.get(key));
            UUID owner;
            try {
                owner = UUID.fromString(key);
                if (!owner.toString().equals(key) || !(raw instanceof ListTag)) throw new IllegalArgumentException("Invalid owner entry");
            } catch (RuntimeException invalid) {
                quarantine(section + key, raw);
                try { if (channel.startsWith("forgotten_")) blockedOwners.add(UUID.fromString(key)); }
                catch (IllegalArgumentException ignored) { /* 无法识别的 UUID 只保留原文，不误伤其余玩家。 */ }
                continue;
            }
            ListTag list = (ListTag) raw;
            for (int index = 0; index < list.size(); index++) {
                Tag entry = list.get(index);
                try {
                    if (itemEntries) {
                        if (!(entry instanceof CompoundTag compound)) throw new IllegalArgumentException("Expected ItemInfo compound");
                        ItemInfo info = ItemInfo.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), entry).getOrThrow();
                        items.computeIfAbsent(owner, ignored -> new ConcurrentHashMap<>()).putIfAbsent(info, compound.copy());
                    } else {
                        if (!(entry instanceof StringTag)) throw new IllegalArgumentException("Expected resource ID string");
                        ResourceLocation id = ResourceLocation.parse(entry.getAsString());
                        ids.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(id);
                    }
                } catch (RuntimeException | LinkageError invalid) {
                    quarantine(section + key + "/" + index, entry);
                    if (channel.startsWith("forgotten_")) blockedOwners.add(owner);
                }
            }
        }
    }

    private void quarantine(String path, Tag raw) {
        // 重复损坏路径也不覆盖以前隔离的数据。
        String key = path;
        for (int suffix = 1; quarantined.contains(key); suffix++) key = path + "#" + suffix;
        quarantined.put(key, raw.copy());
        LogUtils.getLogger().warn("[Primordial] Preserving invalid recovery knowledge entry {} without learning it", path);
    }
}
