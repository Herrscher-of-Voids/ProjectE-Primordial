/**
 * 【文件职责】转化接口可选网络节点的通用生命周期，签名不引用可选模组 API。
 * 【变更记录 #1】2026-10-14 14:14:39（第 1 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无。
 * - 新增：创建、存档、逐刻刷新、区块卸载与拆除回调，能力按 Forge 公共类型返回。
 * - 修改：真实买卖继续走 TransmutationNetworkTrade，仅网络连接状态由适配器持有。
 * - 用途：无 AE2/RS/超越维度时方块实体仍安全加载，所有节点由服务端世界生命周期管理。
 */
package com.primordial.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public interface TransmutationNetworkPort {
    default void load(CompoundTag tag) { }
    default void save(CompoundTag tag) { }
    default void onLoad() { }
    default void tick() { }
    default void unload(boolean removed) { }
    default <T> LazyOptional<T> capability(Capability<T> cap, Direction side) { return LazyOptional.empty(); }
}
