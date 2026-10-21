/**
 * 【文件职责】为 Refined Storage 1.12.x 注册转化接口的外部物品与流体存储。
 * 【变更记录 #1】2026-10-11 23:44:54（第 1 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；本类只在 Refined Storage 已加载时由反射边界解析。
 * - 新增：RSAPI 物品/流体 Provider、虚拟库存快照、缓存差异同步和 SIMULATE/PERFORM 交易分流。
 * - 修改：存储访问遵守 RS int 数量合同，交易依赖通用服务的服务端预算和真实价格结算。
 * - 用途：让 RS External Storage 直接访问已学习商品；无 RS 时不解析本类 API。
 */
/**
 * 【变更记录 #2】2026-10-14 14:14:39（第 2 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：必须用 External Storage 方块才可读取转化接口的前提。
 * - 新增：原生 RS NetworkNode、节点注册/恢复、网络代理能力和直接提供物品/流体存储。
 * - 修改：同一虚拟交易实现由原生节点挂载，缓存周期更新；节点不扫描旁边箱子。
 * - 用途：RS 线缆直接识别接口，拆除时删除节点并重建图，模拟不收费。
 */
/**
 * 【变更记录 #3】2026-10-14 14:33:00（第 3 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：旧 External Storage provider 注册。
 * - 新增：无。
 * - 修改：目录只由原生节点挂载，避免外置存储与直连重复统计。
 * - 用途：RS 线缆直接读取转化账户，不再要求外置存储方块。
 */
/**
 * 【变更记录 #4】2026-10-14 14:51:00（第 4 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：查询卸载区块中的方块实体。
 * - 新增：端口 tick 同步首次放置后补入的账户 UUID。
 * - 修改：节点解析先检查区块已加载，再取得有效宿主。
 * - 用途：避免网络请求强制加载区块；RS 节点所有者与交易账户一致。
 */
/**
 * 【变更记录 #5】2026-10-14 14:53:38（第 5 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：未注册的 External Storage provider 和导入。
 * - 新增：无。
 * - 修改：保留统一交易存储实现，由原生节点提供。
 * - 用途：消除需要外置存储的废弃分支。
 */
package com.primordial.compat.storage;

import com.primordial.blockentity.TransmutationInterfaceBlockEntity;
import com.primordial.service.TransmutationNetworkTrade;
import com.refinedmods.refinedstorage.api.IRSAPI;
import com.refinedmods.refinedstorage.api.RSAPIInject;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.storage.AccessType;
import com.refinedmods.refinedstorage.api.storage.IStorage;
import com.refinedmods.refinedstorage.api.storage.cache.IStorageCache;
import com.refinedmods.refinedstorage.api.storage.cache.InvalidateCause;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorage;
import com.refinedmods.refinedstorage.api.storage.externalstorage.IExternalStorageContext;
import com.refinedmods.refinedstorage.api.util.Action;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fluids.FluidStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RSStorageAdapter {
    @RSAPIInject public static IRSAPI API;
    private RSStorageAdapter() { }

    public static void register() {
        if (API != null) {
            API.getNetworkNodeRegistry().add(Node.ID,(tag,level,pos)->{var node=new Node(level,pos);node.read(tag);return node;});
        }
    }
    public static com.primordial.network.TransmutationNetworkPort createPort(TransmutationInterfaceBlockEntity be){return new Port(be);}
    private static final class Port implements com.primordial.network.TransmutationNetworkPort {
        private final TransmutationInterfaceBlockEntity be;
        private final net.minecraftforge.common.util.LazyOptional<com.refinedmods.refinedstorage.api.network.node.INetworkNodeProxy<Node>> proxy;
        private Port(TransmutationInterfaceBlockEntity be){this.be=be;proxy=net.minecraftforge.common.util.LazyOptional.of(()->this::node);}
        private Node node(){
            if(!(be.getLevel() instanceof ServerLevel level))return new Node(be.getLevel(),be.getBlockPos());
            var manager=com.refinedmods.refinedstorage.apiimpl.API.instance().getNetworkNodeManager(level);
            var existing=manager.getNode(be.getBlockPos());
            if(existing instanceof Node current)return current;
            var current=new Node(level,be.getBlockPos());current.setOwner(be.getOwner());
            manager.setNode(be.getBlockPos(),current);manager.markForSaving();return current;
        }
        @Override public void onLoad(){if(be.getLevel() instanceof ServerLevel){node();refreshNeighbors();}}
        @Override public void tick(){if(be.getLevel() instanceof ServerLevel){var current=node();if(!java.util.Objects.equals(current.getOwner(),be.getOwner()))current.setOwner(be.getOwner());}}
        private void refreshNeighbors(){
            if(!(be.getLevel() instanceof ServerLevel level))return;
            var manager=com.refinedmods.refinedstorage.apiimpl.API.instance().getNetworkNodeManager(level);
            var networks=new java.util.HashSet<INetwork>();
            for(Direction side:Direction.values()){
                var adjacent=manager.getNode(be.getBlockPos().relative(side));
                if(adjacent!=null&&adjacent.getNetwork()!=null)networks.add(adjacent.getNetwork());
            }
            for(var network:networks)network.getNodeGraph().invalidate(Action.PERFORM,network.getLevel(),network.getPosition());
        }
        @Override public void unload(boolean removed){
            if(removed&&be.getLevel() instanceof ServerLevel level){
                var manager=com.refinedmods.refinedstorage.apiimpl.API.instance().getNetworkNodeManager(level);
                var current=manager.getNode(be.getBlockPos());manager.removeNode(be.getBlockPos());manager.markForSaving();
                if(current!=null&&current.getNetwork()!=null){var network=current.getNetwork();network.getNodeGraph().invalidate(Action.PERFORM,network.getLevel(),network.getPosition());}
            }
            proxy.invalidate();
        }
        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> capability(net.minecraftforge.common.capabilities.Capability<T> cap,Direction side){
            return cap==com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY?proxy.cast():net.minecraftforge.common.util.LazyOptional.empty();
        }
    }
    public static final class Node extends com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode implements com.refinedmods.refinedstorage.api.storage.IStorageProvider,IExternalStorageContext {
        public static final net.minecraft.resources.ResourceLocation ID=new net.minecraft.resources.ResourceLocation("primordial","transmutation_interface");
        private TransmutationInterfaceBlockEntity host;
        private Storage items;
        private FluidStorage fluids;
        public Node(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos){super(level,pos);}
        private boolean resolve(){
            if(level==null||!level.hasChunkAt(pos))return false;
            var current=level==null?null:level.getBlockEntity(pos);
            if(!(current instanceof TransmutationInterfaceBlockEntity be)||be.isRemoved())return false;
            if(host!=be){host=be;items=new Storage(this,be);fluids=new FluidStorage(this,be);}
            return true;
        }
        @Override public int getEnergyUsage(){return 1;}
        @Override public net.minecraft.resources.ResourceLocation getId(){return ID;}
        @Override public ItemStack getItemStack(){return new ItemStack(com.primordial.registry.ModItems.TRANSMUTATION_INTERFACE.get());}
        @Override public void addItemStorages(List<IStorage<ItemStack>> out){if(isActive()&&resolve())out.add(items);}
        @Override public void addFluidStorages(List<IStorage<FluidStack>> out){if(isActive()&&resolve())out.add(fluids);}
        @Override protected void onConnectedStateChange(INetwork net,boolean state,com.refinedmods.refinedstorage.apiimpl.network.node.ConnectivityStateChangeCause cause){
            net.getNodeGraph().runActionWhenPossible(n->{n.getItemStorageCache().invalidate(InvalidateCause.CONNECTED_STATE_CHANGED);n.getFluidStorageCache().invalidate(InvalidateCause.CONNECTED_STATE_CHANGED);});
        }
        @Override public void update(){super.update();if(network!=null&&isActive()&&resolve()&&level.getGameTime()%20==0){items.update(network);fluids.update(network);}}
        @Override public int getPriority(){return -100;}
        @Override public AccessType getAccessType(){return AccessType.INSERT_EXTRACT;}
        @Override public boolean acceptsItem(ItemStack stack){return true;}
        @Override public boolean acceptsFluid(FluidStack stack){return true;}
    }

    private static final class Storage implements IExternalStorage<ItemStack> {
        private final IExternalStorageContext context;
        private final TransmutationInterfaceBlockEntity be;
        private List<ItemStack> snapshot = List.of();

        private Storage(IExternalStorageContext context, TransmutationInterfaceBlockEntity be) {
            this.context = context;
            this.be = be;
        }

        private List<ItemStack> current() {
            if (!(be.getLevel() instanceof ServerLevel level) || be.getOwner() == null) return List.of();
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack sample : TransmutationNetworkTrade.catalog(level.getServer(), be.getOwner()).stacks()) {
                int amount = (int) Math.min(Integer.MAX_VALUE, TransmutationNetworkTrade.affordable(
                        level.getServer(), be.getOwner(), sample, Integer.MAX_VALUE));
                if (amount > 0) result.add(sample.copyWithCount(amount));
            }
            return List.copyOf(result);
        }

        @Override public Collection<ItemStack> getStacks() { snapshot = current(); return snapshot; }

        @Override public ItemStack insert(ItemStack stack, int size, Action action) {
            if (stack.isEmpty() || size <= 0 || !(be.getLevel() instanceof ServerLevel level) || be.getOwner() == null)
                return stack.copy();
            ItemStack offered = stack.copyWithCount(Math.min(size, Integer.MAX_VALUE));
            long moved = TransmutationNetworkTrade.insert(level.getServer(), be.getOwner(), offered, size,
                    action == Action.PERFORM);
            return moved >= size ? ItemStack.EMPTY : stack.copyWithCount((int) (size - moved));
        }

        @Override public ItemStack extract(ItemStack stack, int size, int flags, Action action) {
            if (stack.isEmpty() || size <= 0 || !(be.getLevel() instanceof ServerLevel level) || be.getOwner() == null)
                return ItemStack.EMPTY;
            long moved = TransmutationNetworkTrade.extract(level.getServer(), be.getOwner(), stack, size,
                    action == Action.PERFORM);
            return moved <= 0 ? ItemStack.EMPTY : stack.copyWithCount((int) Math.min(Integer.MAX_VALUE, moved));
        }

        @Override public int getStored() {
            long total = 0;
            for (ItemStack stack : current()) total = Math.min(Integer.MAX_VALUE, total + stack.getCount());
            return (int) total;
        }
        @Override public int getPriority() { return context.getPriority(); }
        @Override public AccessType getAccessType() { return context.getAccessType(); }
        @Override public int getCacheDelta(int stored, int size, ItemStack remainder) {
            return size - (remainder == null ? 0 : remainder.getCount());
        }
        @Override public long getCapacity() { return Long.MAX_VALUE; }

        @Override public void update(INetwork network) {
            List<ItemStack> next = current();
            Map<String, ItemStack> before = index(snapshot), after = index(next);
            var cache = network.getItemStorageCache();
            for (var entry : before.entrySet()) {
                ItemStack updated = after.get(entry.getKey());
                int oldCount = entry.getValue().getCount();
                int newCount = updated == null ? 0 : updated.getCount();
                if (oldCount > newCount) cache.remove(entry.getValue(), oldCount - newCount, true);
            }
            for (var entry : after.entrySet()) {
                ItemStack old = before.get(entry.getKey());
                int oldCount = old == null ? 0 : old.getCount();
                if (entry.getValue().getCount() > oldCount)
                    cache.add(entry.getValue(), entry.getValue().getCount() - oldCount, false, true);
            }
            cache.flush();
            snapshot = next;
        }

        private Map<String, ItemStack> index(List<ItemStack> stacks) {
            Map<String, ItemStack> result = new HashMap<>();
            for (ItemStack stack : stacks) result.put(stack.getItem() + "|" + stack.getTag(), stack);
            return result;
        }
    }

    private static final class FluidStorage implements IExternalStorage<FluidStack> {
        private final IExternalStorageContext context;
        private final TransmutationInterfaceBlockEntity be;
        private List<FluidStack> snapshot = List.of();
        private FluidStorage(IExternalStorageContext context, TransmutationInterfaceBlockEntity be) {
            this.context = context; this.be = be;
        }
        private List<FluidStack> current() {
            if (!(be.getLevel() instanceof ServerLevel level) || be.getOwner() == null) return List.of();
            List<FluidStack> result = new ArrayList<>();
            for (FluidStack sample : TransmutationNetworkTrade.fluidCatalog(level.getServer(), be.getOwner()).stacks()) {
                int amount = (int) Math.min(Integer.MAX_VALUE, TransmutationNetworkTrade.fluidAffordable(
                        level.getServer(), be.getOwner(), sample.getFluid(), Integer.MAX_VALUE));
                if (amount > 0) result.add(new FluidStack(sample, amount));
            }
            return List.copyOf(result);
        }
        @Override public Collection<FluidStack> getStacks() { snapshot = current(); return snapshot; }
        @Override public FluidStack insert(FluidStack stack, int size, Action action) {
            if (stack.isEmpty() || size <= 0 || !(be.getLevel() instanceof ServerLevel level) || be.getOwner() == null)
                return stack.copy();
            FluidStack offered = new FluidStack(stack, Math.min(size, Integer.MAX_VALUE));
            long moved = TransmutationNetworkTrade.insertFluid(level.getServer(), be.getOwner(), offered, size,
                    action == Action.PERFORM);
            return moved >= size ? FluidStack.EMPTY : new FluidStack(stack, (int) (size - moved));
        }
        @Override public FluidStack extract(FluidStack stack, int size, int flags, Action action) {
            if (stack.isEmpty() || size <= 0 || !(be.getLevel() instanceof ServerLevel level) || be.getOwner() == null)
                return FluidStack.EMPTY;
            long moved = TransmutationNetworkTrade.extractFluid(level.getServer(), be.getOwner(), stack.getFluid(), size,
                    action == Action.PERFORM);
            return moved <= 0 ? FluidStack.EMPTY : new FluidStack(stack, (int) Math.min(Integer.MAX_VALUE, moved));
        }
        @Override public int getStored() {
            long total = 0; for (FluidStack stack : current()) total = Math.min(Integer.MAX_VALUE, total + stack.getAmount());
            return (int) total;
        }
        @Override public int getPriority() { return context.getPriority(); }
        @Override public AccessType getAccessType() { return context.getAccessType(); }
        @Override public int getCacheDelta(int stored, int size, FluidStack remainder) {
            return size - (remainder == null ? 0 : remainder.getAmount());
        }
        @Override public long getCapacity() { return Long.MAX_VALUE; }
        @Override public void update(INetwork network) {
            List<FluidStack> next = current();
            Map<String, FluidStack> before = indexFluid(snapshot), after = indexFluid(next);
            IStorageCache<FluidStack> cache = network.getFluidStorageCache();
            for (var entry : before.entrySet()) {
                FluidStack updated = after.get(entry.getKey());
                int oldCount = entry.getValue().getAmount(), newCount = updated == null ? 0 : updated.getAmount();
                if (oldCount > newCount) cache.remove(entry.getValue(), oldCount - newCount, true);
            }
            for (var entry : after.entrySet()) {
                FluidStack old = before.get(entry.getKey()); int oldCount = old == null ? 0 : old.getAmount();
                if (entry.getValue().getAmount() > oldCount)
                    cache.add(entry.getValue(), entry.getValue().getAmount() - oldCount, false, true);
            }
            cache.flush(); snapshot = next;
        }
        private Map<String, FluidStack> indexFluid(List<FluidStack> stacks) {
            Map<String, FluidStack> result = new HashMap<>();
            for (FluidStack stack : stacks) result.put(stack.getFluid() + "|" + stack.getTag(), stack);
            return result;
        }
    }
}
