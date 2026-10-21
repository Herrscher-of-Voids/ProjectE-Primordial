/**
 * 【文件职责】把转化接口的只读商品视图挂到相邻已绑定的超越维度网络。
 * 【变更记录 #1】2026-10-14 14:37:00（第 1 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无。
 * - 新增：隔离反射 API、网络视图挂载、去重账户和模拟/执行提取。
 * - 修改：相邻网络节点的 UnifiedStorage 只合并可购买目录；虚拟数量不写入真实 storage 或存档。
 * - 用途：维度终端直接按放置者知识与 EMC 购买，接入不主动搬运；只允许挂到放置者有权限的网络。
 */
/**
 * 【变更记录 #2】2026-10-14 14:46:00（第 2 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：类型槽视图只包含真实存货的限制。
 * - 新增：独立类型桶和资源副本供对方物品/流体能力枚举。
 * - 修改：网络目录、类型索引和按键查询看到同一虚拟数量；仍不修改真实类型索引。
 * - 用途：对方网络终端与能力导出均能读取商品，防止共享显示堆栈被外部改写。
 */
/**
 * 【变更记录 #3】2026-10-14 15:07:00（第 3 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：将模糊提取的实际物品重新包装成请求 NBT 的路径。
 * - 新增：实际返回键不同于请求键时保留原结果。
 * - 修改：只有同键提取才合并虚拟购买的缺额。
 * - 用途：防止模糊提取把普通库存改成另一种 NBT 物品。
 */
package com.primordial.compat.storage;

import com.primordial.blockentity.TransmutationInterfaceBlockEntity;
import com.primordial.network.TransmutationNetworkPort;
import com.primordial.service.TransmutationNetworkTrade;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public final class BeyondDimensionsStorageAdapter {
    private static final String ROOT="com.wintercogs.beyonddimensions.";
    private static final Map<Object,Set<Port>> MOUNTS=new IdentityHashMap<>();
    private static Class<?> nodeClass;
    private static Constructor<?> itemKey,fluidKey,keyAmount,bucket;
    private static Method getNet,getStorage,getOwner,getManagers,getPlayers,onChange,copyStack,key,amount,keyTypeId,bucketAdd,bucketSize,bucketGet;
    private BeyondDimensionsStorageAdapter(){}

    public static void register() throws ReflectiveOperationException {
        nodeClass=Class.forName(ROOT+"common.block.entity.NetedBlockEntity");
        var net=Class.forName(ROOT+"api.dimensionnet.DimensionsNet");
        var storage=Class.forName(ROOT+"api.dimensionnet.UnifiedStorage");
        var keyType=Class.forName(ROOT+"api.storage.key.IStackKey");
        var pair=Class.forName(ROOT+"api.storage.key.KeyAmount");
        getNet=nodeClass.getMethod("getNet");getStorage=net.getMethod("getUnifiedStorage");
        getOwner=net.getMethod("getOwner");getManagers=net.getMethod("getManagers");getPlayers=net.getMethod("getPlayers");
        onChange=storage.getMethod("onChange");copyStack=keyType.getMethod("copyStack");
        key=pair.getMethod("key");amount=pair.getMethod("amount");keyAmount=pair.getConstructor(keyType,long.class);
        itemKey=Class.forName(ROOT+"api.storage.key.impl.ItemStackKey").getConstructor(ItemStack.class);
        fluidKey=Class.forName(ROOT+"api.storage.key.impl.FluidStackKey").getConstructor(FluidStack.class);
        var bucketClass=Class.forName(ROOT+"api.storage.handler.impl.AbstractUnorderedStackHandler$TypeBucket");
        bucket=bucketClass.getDeclaredConstructor();bucket.setAccessible(true);
        bucketAdd=bucketClass.getDeclaredMethod("add",keyType);bucketAdd.setAccessible(true);
        bucketSize=bucketClass.getMethod("size");bucketGet=bucketClass.getMethod("get",int.class);
        keyTypeId=keyType.getMethod("getTypeId");
    }
    public static TransmutationNetworkPort createPort(TransmutationInterfaceBlockEntity be){return new Port(be);}

    private static final class Port implements TransmutationNetworkPort {
        private final TransmutationInterfaceBlockEntity host;
        private final Set<Object> bound=Collections.newSetFromMap(new IdentityHashMap<>());
        private Map<Object,Long> snapshot=Map.of();
        private Port(TransmutationInterfaceBlockEntity be){host=be;}
        @Override public void onLoad(){refresh();}
        @Override public void tick(){if(host.getLevel().getGameTime()%20==0)refresh();}
        /** 只读取相邻已绑定节点；既不拉取实体库存，也不自动购买。 */
        private void refresh(){
            if(host.getLevel()==null||host.getLevel().isClientSide||host.getOwner()==null||nodeClass==null)return;
            try{
                Set<Object> next=Collections.newSetFromMap(new IdentityHashMap<>());
                for(Direction side:Direction.values()){
                    var pos=host.getBlockPos().relative(side);
                    if(!host.getLevel().hasChunkAt(pos))continue;
                    var be=host.getLevel().getBlockEntity(pos);
                    if(!nodeClass.isInstance(be))continue;
                    var net=getNet.invoke(be);
                    if(net==null||!authorized(net,host.getOwner()))continue;
                    next.add(getStorage.invoke(net));
                }
                var current=buildSnapshot(host);
                Set<Object> changed=Collections.newSetFromMap(new IdentityHashMap<>());
                for(Object storage:new ArrayList<>(bound))if(!next.contains(storage)){detach(storage);changed.add(storage);}
                for(Object storage:next)if(bound.add(storage)){
                    MOUNTS.computeIfAbsent(storage,k->Collections.newSetFromMap(new IdentityHashMap<>())).add(this);changed.add(storage);
                }
                if(!snapshot.equals(current))changed.addAll(bound);
                snapshot=current;
                for(Object storage:changed)onChange.invoke(storage);
            }catch(ReflectiveOperationException failure){
                unload(false);
                com.primordial.PrimordialMod.LOGGER.error("BeyondDimensions transmutation mount failed",failure);
            }
        }
        private void detach(Object storage){
            bound.remove(storage);var ports=MOUNTS.get(storage);
            if(ports!=null){ports.remove(this);if(ports.isEmpty())MOUNTS.remove(storage);}
        }
        @Override public void unload(boolean removed){
            for(Object storage:new ArrayList<>(bound)){
                detach(storage);
                try{onChange.invoke(storage);}catch(ReflectiveOperationException ignored){}
            }
            snapshot=Map.of();
        }
    }
    private static boolean authorized(Object net,UUID owner) throws ReflectiveOperationException {
        return owner.equals(getOwner.invoke(net))||((Set<?>)getManagers.invoke(net)).contains(owner)||((Set<?>)getPlayers.invoke(net)).contains(owner);
    }
    private static Map<Object,Long> buildSnapshot(TransmutationInterfaceBlockEntity be) throws ReflectiveOperationException {
        var server=be.getLevel().getServer();var owner=be.getOwner();Map<Object,Long> result=new LinkedHashMap<>();
        for(ItemStack sample:TransmutationNetworkTrade.catalog(server,owner).stacks()){
            long n=TransmutationNetworkTrade.affordable(server,owner,sample,Long.MAX_VALUE);
            if(n>0)result.put(itemKey.newInstance(sample),n);
        }
        for(FluidStack sample:TransmutationNetworkTrade.fluidCatalog(server,owner).stacks()){
            long n=TransmutationNetworkTrade.fluidAffordable(server,owner,sample.getFluid(),Long.MAX_VALUE);
            if(n>0)result.put(fluidKey.newInstance(sample),n);
        }
        return result;
    }
    private static List<Port> ports(Object storage){
        var mounts=MOUNTS.get(storage);if(mounts==null)return List.of();
        var seen=new HashSet<UUID>();var result=new ArrayList<Port>();
        for(Port port:mounts)if(!port.host.isRemoved()&&port.host.getOwner()!=null&&seen.add(port.host.getOwner()))result.add(port);
        return result;
    }
    /** 合并视图，不改变对方真实库存；同一账户的多个接口只算一次。 */
    public static List<?> overlay(Object storage,List<?> actual){
        var ports=ports(storage);if(ports.isEmpty())return actual;
        try{
            Map<Object,Long> totals=new LinkedHashMap<>();
            for(Object pair:actual)totals.put(key.invoke(pair),((Number)amount.invoke(pair)).longValue());
            for(Port port:ports)port.snapshot.forEach((k,v)->totals.merge(k,v,BeyondDimensionsStorageAdapter::add));
            List<Object> result=new ArrayList<>();
            for(var entry:totals.entrySet())result.add(keyAmount.newInstance(entry.getKey(),entry.getValue()));
            return result;
        }catch(ReflectiveOperationException e){throw new IllegalStateException("BeyondDimensions view API changed",e);}
    }
    private static long add(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    public static Optional<?> bucket(Object storage,net.minecraft.resources.ResourceLocation type,Optional<?> actual){
        var ports=ports(storage);if(ports.isEmpty())return actual;
        try{
            Object view=bucket.newInstance();
            if(actual.isPresent())for(int i=0;i<((Number)bucketSize.invoke(actual.get())).intValue();i++)bucketAdd.invoke(view,bucketGet.invoke(actual.get(),i));
            for(Port port:ports)for(Object k:port.snapshot.keySet())if(type.equals(keyTypeId.invoke(k)))bucketAdd.invoke(view,k);
            return ((Number)bucketSize.invoke(view)).intValue()==0?actual:Optional.of(view);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static Object outputStack(Object storage,Object requested,Object actual){
        if(requested==null||ports(storage).isEmpty())return actual;
        try{return copyStack.invoke(requested);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static Object stack(Object storage,Object requested,Object actual){
        if(ports(storage).isEmpty())return actual;
        try{
            long n=((Number)amount.invoke(actual)).longValue();
            for(Port port:ports(storage))n=add(n,port.snapshot.getOrDefault(requested,0L));
            return keyAmount.newInstance(requested,n);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    /** 对方实际库存优先；缺额才从账户购买，模拟请求绝不写余额。 */
    public static Object extract(Object storage,Object requested,long wanted,boolean simulate,Object actual){
        var ports=ports(storage);if(ports.isEmpty()||wanted<=0)return actual;
        try{
            long moved=((Number)amount.invoke(actual)).longValue();
            if(moved>0&&!Objects.equals(key.invoke(actual),requested))return actual;
            Object sample=copyStack.invoke(requested);
            for(Port port:ports){
                if(moved>=wanted)break;
                var host=port.host;var server=host.getLevel().getServer();
                long n=sample instanceof ItemStack item?TransmutationNetworkTrade.extract(server,host.getOwner(),item,wanted-moved,!simulate)
                        :sample instanceof FluidStack fluid?TransmutationNetworkTrade.extractFluid(server,host.getOwner(),fluid.getFluid(),wanted-moved,!simulate):0;
                moved+=n;
            }
            if(!simulate)for(Port port:ports)port.refresh();
            return moved==0?actual:keyAmount.newInstance(requested,moved);
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}
