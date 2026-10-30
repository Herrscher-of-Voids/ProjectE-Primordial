/**
 * 【文件职责】转化接口的平台无关网络生命周期；第三方能力只允许在各自兼容层内注册。
 * 【变更记录 #1】2026-10-22 22:54:44（第 1 次备注 · 2.0.0-neo-dev 转化接口基础）
 * - 删除：无。新增：带注册表上下文的节点读写、启动、服务端 tick 和卸载/删除回调。
 * - 修改：不沿用 Forge Capability/LazyOptional；公共签名只引用 Minecraft 类型。
 * - 用途：保存第三方节点身份并隔离生命周期故障；此接口本身不实现 AE2/RS 商品交易。
 */
package com.primordial.neoforge.transmutation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

public interface NeoForgeTransmutationNetworkPort {
    default void load(CompoundTag tag, HolderLookup.Provider registries) {}
    default void save(CompoundTag tag, HolderLookup.Provider registries) {}
    default void onLoad() {}
    /** 改变需要持久化的数据时，适配器应标记宿主 setChanged；不得在回调中递归交易。 */
    default void tick() {}
    /** false 暂停并保留身份，true 真正拆除；清理不得查询世界或重新加载区块。 */
    default void unload(boolean removed) {}
}
