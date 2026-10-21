/**
 * 【变更记录 #6】2026-10-11 23:44:54（第 6 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；保留玩家在线/离线知识查询及 ProjectE capability 读写逻辑。
 * - 新增：knownItems 按账户返回已学堆栈；全知账户才遍历物品注册表，普通账户使用知识集合。
 * - 修改：可选网络目录不再逐项扫描全注册表判断每件物品是否已学；知识/API 异常时安全返回空目录。
 * - 用途：转化接口能列出放置者个人知识，同时避免每次网络刷新扫描全部物品。
 */
/**
 * 变更记录 #3（2026-09-14）：接入 ProjectE 知识查询、自动学习和离线学习队列。
 * - 删除：无。
 * - 新增：hasKnowledge、learn、mergePendingKnowledge；物品输出严格按玩家 ProjectE 知识判断，离线回收通过 SavedData 排队。
 * - 修改：无。
 * - 用途：配置器只能生成转化桌已学习的物品；回收自动学习由 PrimordialServerConfig 控制。
 *
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
/**
 * 【变更记录 #4】2026-09-15 15:22:01（第 4 次备注 · 1.0.0-emc-service）
 * - 删除：同步包抛异常导致已完成余额写入被上层误判失败，以及登录先删除离线增量的顺序。
 * - 新增：余额同步异常隔离、在线积压增量合并和离线负结果保护。
 * - 修改：先成功写 capability 再取走待合并值；网络失败不回滚已完成的资源交易。
 * - 用途：统一结算整数部分使用原生 ProjectE，离线机器增量不会重复结算或登录丢失。
 */
/**
 * 【变更记录 #5】2026-09-16 01:59:37（第 5 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：回收与购买强行共用同一个数值、绕过 ProjectE 损耗规则的假定。
 * - 新增：新增 ProjectE 原生 getSellValue 读取，保留其共价损耗与 NBT 处理；失败返回零，避免错误回收。
 * - 修改：交易定价统一委托 ProjectE，扩展流体/气体定价和 BigInteger 玩家余额不受影响。
 * - 用途：让本模组回收物品与转化桌回收遵循同一套价值规则。
 */
package com.primordial.compat;

import java.math.BigInteger;
import java.util.UUID;

import com.primordial.PrimordialMod;
import com.primordial.data.PrimordialKnowledgeSavedData;
import com.primordial.data.PrimordialEmcSavedData;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.api.proxy.ITransmutationProxy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.ArrayList;
import java.util.List;

/** ProjectE 1.20.1 兼容层：统一读取物品 EMC、读写玩家 BigInteger EMC 并处理离线增量。 */
public final class ProjectECompat {
    private static volatile boolean warned;
    private static volatile boolean knowledgeWarned;
    private static volatile boolean syncWarned;

    private ProjectECompat() {}

    public static BigInteger itemEmc(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return BigInteger.ZERO;
        try {
            return BigInteger.valueOf(IEMCProxy.INSTANCE.getValue(ItemInfo.fromStack(stack)));
        } catch (Throwable e) {
            warnOnce(e);
            return BigInteger.ZERO;
        }
    }

    /** 包含 ProjectE 配置的共价损耗；不能用购买价替代，否则可绕过原生回收限制。 */
    public static BigInteger itemSellEmc(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return BigInteger.ZERO;
        try {
            return BigInteger.valueOf(IEMCProxy.INSTANCE.getSellValue(ItemInfo.fromStack(stack)));
        } catch (RuntimeException | LinkageError failure) {
            warnOnce(failure);
            return BigInteger.ZERO;
        }
    }

    /**
     * 查询玩家是否已经在 ProjectE 转化桌学习了该物品。
     * ItemInfo.fromStack 会保留 NBT；ProjectE 自身负责其持久化 NBT 规则和全知状态判断。
     * API 不可用或玩家数据无法读取时返回 false，避免配置器在不确定时免费生成物品。
     */
    public static boolean hasKnowledge(MinecraftServer server, UUID playerId, ItemStack stack) {
        if (server == null || playerId == null || stack == null || stack.isEmpty()) return false;
        try {
            IKnowledgeProvider knowledge = knowledgeProvider(server, playerId);
            return knowledge != null && knowledge.hasKnowledge(ItemInfo.fromStack(stack));
        } catch (Throwable error) {
            warnKnowledgeOnce(error);
            return false;
        }
    }

    /** 返回账户中实际记录的知识物品；全知状态才遍历注册表，避免网络库存每次扫描所有物品。 */
    public static List<ItemStack> knownItems(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) return List.of();
        try {
            IKnowledgeProvider knowledge = knowledgeProvider(server, playerId);
            if (knowledge == null) return List.of();
            List<ItemStack> result = new ArrayList<>();
            if (knowledge.hasFullKnowledge()) {
                for (var item : BuiltInRegistries.ITEM) {
                    if (item != net.minecraft.world.item.Items.AIR)
                        result.add(new ItemStack(item));
                }
            } else {
                for (ItemInfo info : knowledge.getKnowledge()) {
                    ItemStack stack = info.createStack();
                    if (!stack.isEmpty()) result.add(stack);
                }
            }
            return List.copyOf(result);
        } catch (Throwable error) {
            warnKnowledgeOnce(error);
            return List.of();
        }
    }

    /**
     * 将物品写入玩家 ProjectE 知识。在线玩家立即同步；离线玩家写入持久化队列，登录后再写入真实 capability。
     * 返回 true 表示物品已经学习或已成功排队，false 表示 API/数据不可用。
     */
    public static boolean learn(MinecraftServer server, UUID playerId, ItemStack stack) {
        if (server == null || playerId == null || stack == null || stack.isEmpty()) return false;
        try {
            ItemInfo info = ItemInfo.fromStack(stack);
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                java.util.Optional<IKnowledgeProvider> optional = provider(player);
                if (optional.isEmpty()) return false;
                IKnowledgeProvider knowledge = optional.get();
                if (knowledge.hasKnowledge(info)) return true;
                if (!knowledge.addKnowledge(info)) return knowledge.hasKnowledge(info);
                knowledge.syncKnowledgeChange(player, info, true);
                return true;
            }

            // ProjectE 的离线代理是只读快照，直接 addKnowledge 不会写回玩家文件。
            IKnowledgeProvider offline = ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(playerId);
            if (offline != null && offline.hasKnowledge(info)) return true;
            PrimordialKnowledgeSavedData.get(server).queue(playerId, info);
            return true;
        } catch (Throwable error) {
            warnKnowledgeOnce(error);
            return false;
        }
    }

    /** 玩家登录后把离线回收期间排队的知识写入 ProjectE capability。 */
    public static void mergePendingKnowledge(ServerPlayer player) {
        if (player == null || player.server == null) return;
        PrimordialKnowledgeSavedData queue = PrimordialKnowledgeSavedData.get(player.server);
        for (ItemInfo info : queue.take(player.getUUID())) {
            try {
                java.util.Optional<IKnowledgeProvider> optional = provider(player);
                if (optional.isEmpty()) {
                    queue.queue(player.getUUID(), info);
                    continue;
                }
                IKnowledgeProvider knowledge = optional.get();
                if (!knowledge.hasKnowledge(info) && knowledge.addKnowledge(info)) {
                    knowledge.syncKnowledgeChange(player, info, true);
                }
            } catch (Throwable error) {
                // capability 暂不可用时保留条目，避免登录瞬间丢失学习状态。
                queue.queue(player.getUUID(), info);
                warnKnowledgeOnce(error);
            }
        }
    }

    public static BigInteger get(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return provider(player).map(cap -> cap.getEmc().add(PrimordialEmcSavedData.get(server).get(playerId)).max(BigInteger.ZERO))
                    .orElse(BigInteger.ZERO);
        }
        return offlineBase(playerId).add(PrimordialEmcSavedData.get(server).get(playerId)).max(BigInteger.ZERO);
    }

    public static void add(MinecraftServer server, UUID playerId, BigInteger delta) {
        if (delta == null || delta.signum() == 0) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            boolean[] applied = {false};
            provider(player).ifPresent(cap -> {
                PrimordialEmcSavedData pending = PrimordialEmcSavedData.get(server);
                cap.setEmc(cap.getEmc().add(pending.get(playerId)).add(delta).max(BigInteger.ZERO));
                pending.take(playerId);
                applied[0] = true;
                syncEmc(cap, player);
            });
            if (!applied[0]) PrimordialEmcSavedData.get(server).add(playerId, delta);
        } else {
            PrimordialEmcSavedData.get(server).add(playerId, delta);
        }
    }

    public static boolean spend(MinecraftServer server, UUID playerId, BigInteger amount) {
        if (amount == null || amount.signum() <= 0) return true;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            return provider(player).map(cap -> {
                PrimordialEmcSavedData pending = PrimordialEmcSavedData.get(server);
                BigInteger current = cap.getEmc().add(pending.get(playerId));
                if (current.compareTo(amount) < 0) return false;
                cap.setEmc(current.subtract(amount));
                pending.take(playerId);
                syncEmc(cap, player);
                return true;
            }).orElse(false);
        }
        PrimordialEmcSavedData data = PrimordialEmcSavedData.get(server);
        BigInteger current = get(server, playerId);
        if (current.compareTo(amount) < 0) return false;
        data.add(playerId, amount.negate());
        return true;
    }

    /** 登录时把离线机器产生的增量并入 ProjectE capability。 */
    public static void mergePending(ServerPlayer player) {
        MinecraftServer server = player.server;
        provider(player).ifPresent(cap -> {
            PrimordialEmcSavedData data = PrimordialEmcSavedData.get(server);
            BigInteger pending = data.get(player.getUUID());
            if (pending.signum() == 0) return;
            cap.setEmc(cap.getEmc().add(pending).max(BigInteger.ZERO));
            data.take(player.getUUID());
            syncEmc(cap, player);
        });
    }

    /** 同步只是通知客户端；失败不能让调用方重复执行已成功的整数入账。 */
    private static void syncEmc(IKnowledgeProvider cap, ServerPlayer player) {
        try { cap.syncEmc(player); }
        catch (RuntimeException | LinkageError failure) {
            if (!syncWarned) {
                syncWarned = true;
                PrimordialMod.LOGGER.warn("[Primordial] EMC balance updated, but ProjectE client sync failed", failure);
            }
        }
    }

    private static java.util.Optional<IKnowledgeProvider> provider(ServerPlayer player) {
        return player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).resolve();
    }

    private static IKnowledgeProvider knowledgeProvider(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) return provider(player).orElse(null);
        return ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(playerId);
    }

    private static BigInteger offlineBase(UUID playerId) {
        try {
            return ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(playerId).getEmc();
        } catch (Throwable ignored) {
            return BigInteger.ZERO;
        }
    }

    private static void warnOnce(Throwable error) {
        if (!warned) {
            warned = true;
            PrimordialMod.LOGGER.error("ProjectE EMC API is unavailable", error);
        }
    }

    private static void warnKnowledgeOnce(Throwable error) {
        if (!knowledgeWarned) {
            knowledgeWarned = true;
            PrimordialMod.LOGGER.error("ProjectE knowledge API is unavailable; item generation is disabled", error);
        }
    }
}
