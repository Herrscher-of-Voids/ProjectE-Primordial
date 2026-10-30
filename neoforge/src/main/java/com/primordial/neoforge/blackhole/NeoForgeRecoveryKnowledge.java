/**
 * 【文件职责】回收已接收之后记录知识；在线使用 ProjectE 原生能力，离线只写本世界待学账本。
 * 【变更记录 #1】2026-10-22 21:40:04（第 1 次备注 · 2.0.0-neo-dev 黑洞实际回收）
 * - 删除：无。
 * - 新增：物品组件原生规范化、在线学习、离线持久队列、流体 ID 学习与失败重试入口。
 * - 修改：先持久排队再调用 ProjectE；学习与客户端通知分开确认，通知失败不能触发资源重投。
 * - 用途：供回收服务在收益队列接收成功后调用；登录与服务端十 tick 轮询负责 flush。
 * 【变更记录 #2】2026-10-22 22:14:49（第 2 次备注 · 2.0.0-neo-dev 可选化学品知识）
 * - 删除：无。
 * - 新增：recordChemical 与 knowsChemical，仅传递 owner 和注册 ID，不引用可选模组类型。
 * - 修改：化学品学习复用服务端主线程与异常隔离规则，独立保存而不混入物品或流体知识。
 * - 用途：兼容层在真实资源接收成功后记录世界级化学品知识，离线所有者同样可持久保留。
 * 【变更记录 #3】2026-10-23 13:30:35（第 3 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：凭证将显示组件保留为类别授权样本的路径。
 * - 新增：真实回收凭证前先按资源类别归一化为基础知识样本。
 * - 修改：普通物品仍完整采用 ProjectE 持久组件规范；离线排队与原生同步保持原逻辑。
 * - 用途：命名等显示数据不阻碍类别学习，也不会因仅持有凭证而获得权限。
 * 【变更记录 #4】2026-10-23 13:40:12（第 4 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：只有根级健康检查而继续处理被隔离所有者的物品补学路径。
 * - 新增：available(server, owner) 回收消费前预检。
 * - 修改：物品排队及登录补学使用同一所有者级可用性，损坏遗忘数据不会静默丢失本次应学知识。
 * - 用途：与公共购买、知识 API 和黑洞的损坏账户封锁策略保持一致，保留原始隔离数据。
 */
package com.primordial.neoforge.blackhole;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class NeoForgeRecoveryKnowledge {
    private static final Set<UUID> WRITE_WARNED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SYNC_PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SYNC_WARNED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FLUSHING = ConcurrentHashMap.newKeySet();

    private NeoForgeRecoveryKnowledge() {}

    /** 消费前检查未知账本格式；读取不会修改 ProjectE 离线快照。 */
    public static boolean available(MinecraftServer server) {
        NeoForgeEmcCredits.requireServerThread(server);
        try {
            return NeoForgeRecoveryKnowledgeSavedData.get(server).available();
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    public static boolean available(MinecraftServer server, UUID owner) {
        NeoForgeEmcCredits.requireServerThread(server);
        try {
            return NeoForgeRecoveryKnowledgeSavedData.get(server).available(owner);
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /** 调用方已经接收收益后调用；失败只留下待办或日志，不向调用方抛出重投信号。 */
    public static void recordItem(MinecraftServer server, UUID owner, ItemStack stack) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null || stack == null || stack.isEmpty()) return;
        try {
            NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
            if (!data.available(owner)) return;
            ItemInfo original = ItemInfo.fromStack(com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge
                    .knowledgeSample(stack.copyWithCount(1)));
            ItemInfo info = original;
            try {
                // 完整沿用 ProjectE 持久组件规则；手工清空容器组件会改变知识和内容定价语义。
                info = IEMCProxy.INSTANCE.getPersistentInfo(original);
            } catch (RuntimeException | LinkageError failure) {
                warnWrite(owner, failure);
            }
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) {
                try {
                    IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
                    if (knowledge != null && knowledge.hasKnowledge(info)) return;
                } catch (RuntimeException | LinkageError failure) {
                    warnWrite(owner, failure);
                }
            }
            data.queue(owner, info, server.registryAccess());
            if (player != null) flush(player);
        } catch (RuntimeException | LinkageError failure) {
            warnWrite(owner, failure);
        }
    }

    public static void recordFluid(MinecraftServer server, UUID owner, ResourceLocation id) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null || id == null) return;
        try {
            NeoForgeRecoveryKnowledgeSavedData.get(server).recordFluid(owner, id);
        } catch (RuntimeException | LinkageError failure) {
            warnWrite(owner, failure);
        }
    }

    public static boolean knowsFluid(MinecraftServer server, UUID owner, ResourceLocation id) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null || id == null) return false;
        try {
            return NeoForgeRecoveryKnowledgeSavedData.get(server).knowsFluid(owner, id);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    /** 只在真实接收之后调用；化学品 ID 无须把 Mekanism 类型带入公共服务。 */
    public static void recordChemical(MinecraftServer server, UUID owner, ResourceLocation id) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null || id == null) return;
        try {
            NeoForgeRecoveryKnowledgeSavedData.get(server).recordChemical(owner, id);
        } catch (RuntimeException | LinkageError failure) {
            warnWrite(owner, failure);
        }
    }

    public static boolean knowsChemical(MinecraftServer server, UUID owner, ResourceLocation id) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null || id == null) return false;
        try {
            return NeoForgeRecoveryKnowledgeSavedData.get(server).knowsChemical(owner, id);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    /** 原生学习已修改 attachment 后事件监听器仍可能抛错；保留待办，下次 hasKnowledge 确认即可。 */
    public static void flush(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        NeoForgeEmcCredits.requireServerThread(server);
        UUID owner = player.getUUID();
        if (!FLUSHING.add(owner)) return;
        try {
            if (server.getPlayerList().getPlayer(owner) != player) return;
            NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
            if (!data.available(owner)) return;
            IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            if (knowledge == null) return;
            for (ItemInfo info : data.pending(owner)) {
                try {
                    if (knowledge.hasKnowledge(info) || knowledge.addKnowledge(info) || knowledge.hasKnowledge(info)) {
                        data.acknowledge(owner, info);
                        SYNC_PENDING.add(owner);
                        WRITE_WARNED.remove(owner);
                    }
                } catch (RuntimeException | LinkageError failure) {
                    warnWrite(owner, failure);
                }
            }
            if (SYNC_PENDING.contains(owner)) {
                try {
                    // 原生规范化可能影响组件或全知识状态，使用真实能力全量同步，避免错误增量重建。
                    knowledge.sync(player);
                    SYNC_PENDING.remove(owner);
                    SYNC_WARNED.remove(owner);
                } catch (RuntimeException | LinkageError failure) {
                    if (SYNC_WARNED.add(owner)) LogUtils.getLogger().warn(
                            "[Primordial] Recovery knowledge learned for {}, but client notification failed; will retry", owner, failure);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            warnWrite(owner, failure);
        } finally {
            FLUSHING.remove(owner);
        }
    }

    public static void clearSession() {
        WRITE_WARNED.clear();
        SYNC_PENDING.clear();
        SYNC_WARNED.clear();
        FLUSHING.clear();
    }

    private static void warnWrite(UUID owner, Throwable failure) {
        if (WRITE_WARNED.add(owner)) LogUtils.getLogger().warn(
                "[Primordial] Recovery knowledge unavailable for {}; pending learning will retry", owner, failure);
    }
}
