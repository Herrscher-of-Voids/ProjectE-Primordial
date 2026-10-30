/**
 * 【文件职责】转化接口的新档归属、原始节点 NBT 与服务端生命周期入口；没有普通资源库存。
 * 【变更记录 #1】2026-10-22 22:54:44（第 1 次备注 · 2.0.0-neo-dev 转化接口基础）
 * - 删除：无。新增：仅首次绑定 UUID、损坏归属原文保留、读写注册表和幂等挂载/断开。
 * - 修改：构造和 NBT 解析不创建节点；实际 onLoad/服务端 tick 才激活，拆除永久关闭旧引用。
 * - 用途：为后续原生网络联动保存身份；损坏归属不允许重新认领，异常端口不能丢失整个方块存档。
 * 【变更记录 #2】2026-10-22 22:59:40（第 2 次备注 · 归属与节点同代恢复）
 * - 删除：NBT 读写中分开发布归属和节点的时序。新增：挂载实体拒绝异线程载入。
 * - 修改：旧端口断开后一起提交归属和节点；先完成网络快照，再写当前归属，嵌套载入以最新请求为准。
 * - 用途：防止保存/卸载回调中的 NBT 重载拼出不同玩家的归属与网络身份。
 */
package com.primordial.neoforge.transmutation;

import java.util.UUID;
import javax.annotation.Nullable;
import com.primordial.neoforge.NeoForgeModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

public final class NeoForgeTransmutationInterfaceBlockEntity extends BlockEntity {
    @Nullable private UUID owner;
    @Nullable private Tag invalidOwner;
    private final NeoForgeTransmutationNetworkState networks = new NeoForgeTransmutationNetworkState(this);

    public NeoForgeTransmutationInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(NeoForgeModRegistries.TRANSMUTATION_INTERFACE_ENTITY.get(), pos, state);
    }

    @Nullable public UUID owner() { return owner; }

    public void bindOwner(@Nullable UUID candidate) {
        if (candidate != null && owner == null && invalidOwner == null && !networks.busy() && attachedServer()) {
            owner = candidate;
            setChanged();
        }
    }

    boolean serverThread() {
        return level instanceof ServerLevel server && server.getServer().isSameThread();
    }

    /** 只查看当前已加载区块；卸载/保存路径不得调用本方法或触发 FULL 区块查询。 */
    boolean attachedServer() {
        if (!serverThread() || isRemoved()) return false;
        ServerLevel server = (ServerLevel) level;
        LevelChunk chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition, LevelChunk.EntityCreationType.CHECK) == this;
    }

    @Nullable public NeoForgeTransmutationNetworkPort networkPort(String id) { return networks.port(id); }
    public void tickNetworks() { networks.tick(); }
    public void removeFromNetwork() { networks.stop(true); }

    @Override public void onLoad() {
        super.onLoad();
        networks.activate();
    }

    @Override public void onChunkUnloaded() {
        networks.stop(false);
        super.onChunkUnloaded();
    }

    @Override public void setRemoved() {
        networks.stop(false);
        super.setRemoved();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        Tag networkData = networks.snapshot(registries);
        if (invalidOwner != null) tag.put("owner", invalidOwner.copy());
        else if (owner != null) tag.putUUID("owner", owner);
        tag.put("NetworkPorts", networkData);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (level instanceof ServerLevel && !serverThread()) return;
        super.loadAdditional(tag, registries);
        UUID nextOwner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        Tag nextInvalid = nextOwner == null && tag.contains("owner") ? tag.get("owner").copy() : null;
        networks.restore(tag.contains("NetworkPorts") ? tag.get("NetworkPorts") : new CompoundTag(), () -> {
            owner = nextOwner;
            invalidOwner = nextInvalid;
        });
    }
}
