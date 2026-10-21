/**
 * 【文件职责】持久化转化接口的放置者账户 UUID，供 AE2 / RS 适配器读取。
 * 【变更记录 #1】2026-10-11 23:44:54（第 1 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；本方块实体不创建实体库存，虚拟商品不落入可绕过结算的普通容器。
 * - 新增：owner UUID 的安全读写、NBT 保存及旧/损坏数据的空主处理。
 * - 修改：外部适配器仅在服务端且存在 owner 时提供交易；未绑定接口返回空库存。
 * - 用途：个人知识和 EMC 明确绑定放置者，区块重载后仍保持同一账户，不把余额转移到机器。
 */
/**
 * 【变更记录 #2】2026-10-14 14:14:39（第 2 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：只持久化账户、不管理网络节点的生命周期。
 * - 新增：可选网络端口、节点存档、onLoad/tick/unload/移除处理和能力转发。
 * - 修改：端口仅在世界有效时初始化；可选 API 通过隔离工厂解析，owner 仍绑定放置者。
 * - 用途：接线后整个已学目录进入网络；卸载、拆除时断开节点，禁止虚拟商品写入实体库存。
 */
/**
 * 【变更记录 #3】2026-10-14 14:33:00（第 3 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：卸载区块也按拆除删除 RS 节点的路径。
 * - 新增：断开前保存各端口数据；实际更换方块才删除节点记录。
 * - 修改：区块卸载只断开在线端口，重新加载恢复节点存档。
 * - 用途：防止卸载重载丢失 AE2 节点身份与 RS 连接数据。
 */
/**
 * 【变更记录 #4】2026-10-14 15:15:00（第 4 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：setRemoved 中读取世界方块状态判断拆除的路径。
 * - 新增：实际方块替换通过块 onRemove 显式通知移除网络。
 * - 修改：setRemoved 与区块卸载均只断开在线端口，不访问正在卸载的区块。
 * - 用途：修复停服保存时重新加载当前区块造成的循环加载和存档竞争。
 */
package com.primordial.blockentity;

import com.primordial.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;
import java.util.UUID;

public final class TransmutationInterfaceBlockEntity extends BlockEntity {
    @Nullable private UUID owner;
    private final java.util.Map<String,com.primordial.network.TransmutationNetworkPort> ports=new java.util.HashMap<>();
    private final java.util.Set<String> attempted=new java.util.HashSet<>();
    private CompoundTag networkData=new CompoundTag();

    public TransmutationInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRANSMUTATION_INTERFACE.get(), pos, state);
    }

    @Nullable public UUID getOwner() { return owner; }
    @Nullable public com.primordial.network.TransmutationNetworkPort networkPort(String id){
        if(level==null||isRemoved())return null;
        if(attempted.add(id)){
            var port=com.primordial.network.StorageNetworkAdapterBootstrap.createPort(id,this);
            if(port!=null){ports.put(id,port);port.load(networkData.getCompound(id));}
        }
        return ports.get(id);
    }
    @Override public void onLoad(){
        super.onLoad();
        for(String id:com.primordial.network.StorageNetworkAdapterBootstrap.portIds()){
            var port=networkPort(id);if(port!=null)port.onLoad();
        }
    }
    public void tickNetworks(){if(level!=null&&!level.isClientSide)for(var port:ports.values())port.tick();}
    private void disconnectNetworks(boolean removed){
        for(var entry:ports.entrySet()){
            var data=new CompoundTag();entry.getValue().save(data);networkData.put(entry.getKey(),data);
            entry.getValue().unload(removed);
        }
        ports.clear();attempted.clear();
    }
    @Override public void onChunkUnloaded(){disconnectNetworks(false);super.onChunkUnloaded();}
    public void removeFromNetwork(){disconnectNetworks(true);}
    @Override public void setRemoved(){
        disconnectNetworks(false);
        super.setRemoved();
    }
    @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,net.minecraft.core.Direction side){
        if(level!=null&&!isRemoved())for(String id:com.primordial.network.StorageNetworkAdapterBootstrap.portIds()){
            var port=networkPort(id);if(port!=null){var result=port.capability(cap,side);if(result.isPresent())return result;}
        }
        return super.getCapability(cap,side);
    }

    public void setOwner(@Nullable UUID owner) {
        if (this.owner == null && owner != null) {
            this.owner = owner;
            setChanged();
        }
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("owner", owner);
        for(var entry:ports.entrySet()){var data=new CompoundTag();entry.getValue().save(data);networkData.put(entry.getKey(),data);}
        tag.put("NetworkPorts",networkData.copy());
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        networkData=tag.getCompound("NetworkPorts").copy();
        for(var entry:ports.entrySet())entry.getValue().load(networkData.getCompound(entry.getKey()));
    }
}
