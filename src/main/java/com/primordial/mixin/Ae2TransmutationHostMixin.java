/**
 * 【文件职责】仅安装 AE2 时为普通转化接口实体提供 AE2 世界内节点接口。
 * 【变更记录 #1】2026-10-14 14:14:39（第 1 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无。
 * - 新增：IInWorldGridNodeHost 的六面节点和玻璃线缆连接类型。
 * - 修改：实际节点由隔离 Port 管理，公共方块实体不直接引用 AE2 API。
 * - 用途：AE2 线缆自动发现接口，缺少 AE2 时由 Mixin 插件跳过整个可选类。
 */
package com.primordial.mixin;

import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IGridNode;
import appeng.api.util.AECableType;
import com.primordial.blockentity.TransmutationInterfaceBlockEntity;
import com.primordial.compat.storage.AE2StorageAdapter;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=TransmutationInterfaceBlockEntity.class,remap=false)
public abstract class Ae2TransmutationHostMixin implements IInWorldGridNodeHost {
    @Override public IGridNode getGridNode(Direction side){
        var port=((TransmutationInterfaceBlockEntity)(Object)this).networkPort("ae2");
        return port instanceof AE2StorageAdapter.Port ae?ae.node():null;
    }
    @Override public AECableType getCableConnectionType(Direction side){return AECableType.GLASS;}
}
