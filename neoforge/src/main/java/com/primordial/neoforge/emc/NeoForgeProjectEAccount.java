/**
 * 【文件职责】将真实在线玩家的 ProjectE 知识能力适配为 EMC 结算账户，不查询或写入离线快照。
 *
 * 【变更记录 #1】2026-10-22 21:05:30（第 1 次备注 · 2.0.0-neo-dev EMC 结算迁移）
 * - 删除：无。
 * - 新增：按 UUID 校验在线玩家与服务器归属，提供余额读取、写入和 ProjectE 客户端同步适配。
 * - 修改：写入异常交给结算服务保留账本，同步异常由服务单独隔离；缺少能力不伪装为零余额。
 * - 用途：ProjectE 余额随正常玩家 attachment 保存，不声称与原初世界账本构成跨文件原子提交。
 * 【变更记录 #2】2026-10-23 11:37:42（第 2 次备注 · 2.0.0-neo-dev 离线购买账户）
 * - 删除：无；在线 setter/sync 继续只访问当前 PlayerList 的真实能力。
 * - 新增：通过 ProjectE ITransmutationProxy 读取离线基础余额，缺失或负值拒绝报价。
 * - 修改：离线入口只读，实际支出由原初世界借项保存，登录统一合并。
 * - 用途：存储网络后续可在放置者离线时购买，不修改过期玩家对象或只读离线快照。
 */
package com.primordial.neoforge.emc;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class NeoForgeProjectEAccount {
    private NeoForgeProjectEAccount() {}

    @Nullable
    static NeoForgeEmcCredits.Account online(MinecraftServer server, UUID owner) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null || !owner.equals(player.getUUID())
                || player.getServer() != server || player.serverLevel().getServer() != server) {
            return null;
        }
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        if (knowledge == null) return null;
        return new NeoForgeEmcCredits.Account() {
            @Override
            public BigInteger balance() {
                requireCurrent();
                return Objects.requireNonNull(knowledge.getEmc(), "ProjectE returned a null EMC balance");
            }

            @Override
            public void setBalance(BigInteger balance) {
                requireCurrent();
                knowledge.setEmc(balance);
            }

            @Override
            public void sync() {
                requireCurrent();
                knowledge.syncEmc(player);
            }

            private void requireCurrent() {
                if (server.getPlayerList().getPlayer(owner) != player || player.getServer() != server
                        || player.serverLevel().getServer() != server)
                    throw new IllegalStateException("ProjectE account no longer belongs to the current online player");
            }
        };
    }

    static BigInteger onlineBalance(ServerPlayer player) {
        IKnowledgeProvider knowledge = Objects.requireNonNull(
                player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY),
                "Online player has no ProjectE knowledge capability");
        return Objects.requireNonNull(knowledge.getEmc(), "ProjectE returned a null EMC balance");
    }

    static BigInteger baseBalance(MinecraftServer server, UUID owner) {
        if (server.getPlayerList().getPlayer(owner) != null) {
            BigInteger value = Objects.requireNonNull(online(server, owner), "Missing online ProjectE account").balance();
            if (value.signum() < 0) throw new IllegalStateException("Negative ProjectE EMC balance");
            return value;
        }
        IKnowledgeProvider offline = Objects.requireNonNull(
                ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(owner), "No ProjectE offline account");
        BigInteger value = Objects.requireNonNull(offline.getEmc(), "Missing offline EMC balance");
        if (value.signum() < 0) throw new IllegalStateException("Negative ProjectE EMC balance");
        return value;
    }

}
