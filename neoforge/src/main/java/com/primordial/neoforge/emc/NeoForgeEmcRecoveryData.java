/**
 * 【文件职责】保存被拆除但无法正常结算的黑洞原始 NBT，供恢复数据，不自动估算或发放 EMC。
 * 【变更记录 #1】2026-10-22 21:13:00（第 1 次备注 · 2.0.0-neo-dev 精确 EMC 入账）
 * - 删除：无。
 * - 新增：独立主世界恢复日志及原始根数据保留。
 * - 修改：损坏队列或被阻断账本的拆除快照独立保存，不覆盖正常金额账本。
 * - 用途：拆除异常方块仍保留归属、位置、维度与待结算原文；不宣称磁盘崩溃原子性。
 */
package com.primordial.neoforge.emc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class NeoForgeEmcRecoveryData extends SavedData {
    private static final Factory<NeoForgeEmcRecoveryData> FACTORY =
            new Factory<>(NeoForgeEmcRecoveryData::new, NeoForgeEmcRecoveryData::load);
    private CompoundTag root = new CompoundTag();

    public static void preserve(MinecraftServer server, CompoundTag snapshot) {
        NeoForgeEmcCredits.requireServerThread(server);
        NeoForgeEmcRecoveryData data = server.overworld().getDataStorage()
                .computeIfAbsent(FACTORY, "primordial_emc_recovery");
        data.append(snapshot);
    }

    private synchronized void append(CompoundTag snapshot) {
        if (root.contains("blocks") && (!root.contains("blocks", Tag.TAG_LIST)
                || ((ListTag) root.get("blocks")).getElementType() != Tag.TAG_COMPOUND
                && !((ListTag) root.get("blocks")).isEmpty())) {
            CompoundTag preserved = root;
            root = new CompoundTag();
            root.put("previous_raw_data", preserved);
        }
        ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
        blocks.add(snapshot.copy());
        root.put("blocks", blocks);
        setDirty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        return root.copy();
    }

    private static NeoForgeEmcRecoveryData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        NeoForgeEmcRecoveryData data = new NeoForgeEmcRecoveryData();
        data.root = tag.copy();
        return data;
    }
}
