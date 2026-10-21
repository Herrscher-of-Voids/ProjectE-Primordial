/**
 * 【文件职责】将转化接口物品与流体暴露为 AE2 15.x 的 MEStorage capability。
 * 【变更记录 #1】2026-10-11 23:44:54（第 1 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；本类只由主类在 AE2 已加载时反射解析。
 * - 新增：AE2 STORAGE capability 附加、物品/流体目录、模拟查询和 MODULATE 买卖结算。
 * - 修改：支持 AEItemKey 与 AEFluidKey；SIMULATE 不改 EMC，真实交易委托 TransmutationNetworkTrade。
 * - 用途：通过 AE2 Storage Bus 连接接口，缺少 AE2 时该类不会进入公共启动类加载链。
 */
/**
 * 【变更记录 #2】2026-10-14 14:14:39（第 2 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：必须使用 Storage Bus 才能挂载虚拟存储的前提。
 * - 新增：世界内 Managed Grid Node 和 IStorageProvider 服务，六面线缆接入，节点 NBT 和拆除生命周期。
 * - 修改：原 MEStorage 交易实现同时用于原生节点；每秒刷新目录，模拟仍只读。
 * - 用途：直接挂载放置者转化桌物品和流体，遵守 AE2 供电及通道规则；可选类仅在 AE2 安装时加载。
 */
/**
 * 【变更记录 #3】2026-10-14 14:33:00（第 3 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：旧 Storage Bus capability 注册，避免同一目录重复挂载。
 * - 新增：旧无主接口初始化时的空 UUID 检查。
 * - 修改：仅通过原生世界节点提供存储服务，账户无效时目录为空。
 * - 用途：接口直接接线而不再作为箱子暴露；避免旧无主数据查询玩家时崩溃。
 */
/**
 * 【变更记录 #4】2026-10-14 14:53:38（第 4 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：未注册的旧箱子能力代码及其导入。
 * - 新增：无。
 * - 修改：MEStorage 仅由世界内节点挂载。
 * - 用途：移除废弃挂载路径，避免维护时误用两套注册方式。
 */
/**
 * 【变更记录 #5】2026-10-14 15:05:00（第 5 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：用于部件末端的 CANNOT_CARRY 标志。
 * - 新增：无。
 * - 修改：完整方块按 AE2 原生磁盘驱动器使用 REQUIRE_CHANNEL，保留六面连接和供电要求。
 * - 用途：使世界内节点正常分配通道，避免有供电却无法激活存储服务。
 */
package com.primordial.compat.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.primordial.blockentity.TransmutationInterfaceBlockEntity;
import com.primordial.service.TransmutationNetworkTrade;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

public final class AE2StorageAdapter {
    private AE2StorageAdapter() { }

    public static void register() {
        // AE2 使用世界内节点；不再同时挂载旧 Storage Bus capability。
    }
    public static com.primordial.network.TransmutationNetworkPort createPort(TransmutationInterfaceBlockEntity be){return new Port(be);}
    public static final class Port implements com.primordial.network.TransmutationNetworkPort,appeng.api.storage.IStorageProvider {
        private final TransmutationInterfaceBlockEntity be;
        private final appeng.api.networking.IManagedGridNode managed;
        private final MEStorage storage;
        public Port(TransmutationInterfaceBlockEntity be){
            this.be=be;this.storage=new Storage(be);
            this.managed=appeng.api.networking.GridHelper.createManagedNode(be,new appeng.api.networking.IGridNodeListener<TransmutationInterfaceBlockEntity>(){
                @Override public void onSaveChanges(TransmutationInterfaceBlockEntity host,appeng.api.networking.IGridNode node){host.setChanged();}
                @Override public void onStateChanged(TransmutationInterfaceBlockEntity host,appeng.api.networking.IGridNode node,State state){appeng.api.storage.IStorageProvider.requestUpdate(managed);}
            }).setTagName("aeNode").setInWorldNode(true).setExposedOnSides(java.util.EnumSet.allOf(net.minecraft.core.Direction.class))
                    .setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL)
                    .setIdlePowerUsage(1).setVisualRepresentation(be.getBlockState().getBlock())
                    .addService(appeng.api.storage.IStorageProvider.class,this);
        }
        public appeng.api.networking.IGridNode node(){return managed.getNode();}
        @Override public void load(net.minecraft.nbt.CompoundTag tag){if(!managed.isReady())managed.loadFromNBT(tag);}
        @Override public void save(net.minecraft.nbt.CompoundTag tag){managed.saveToNBT(tag);}
        @Override public void onLoad(){
            if(be.getLevel()!=null&&!be.getLevel().isClientSide)appeng.api.networking.GridHelper.onFirstTick(be,host->{
                if(!host.isRemoved()&&!managed.isReady()){
                    var player=host.getOwner()==null?null:host.getLevel().getServer().getPlayerList().getPlayer(host.getOwner());
                    if(player!=null)managed.setOwningPlayer(player);
                    managed.create(host.getLevel(),host.getBlockPos());
                }
            });
        }
        @Override public void tick(){if(managed.isReady()&&be.getLevel().getGameTime()%20==0)appeng.api.storage.IStorageProvider.requestUpdate(managed);}
        @Override public void unload(boolean removed){managed.destroy();}
        @Override public void mountInventories(appeng.api.storage.IStorageMounts mounts){if(managed.isActive())mounts.mount(storage,-100);}
    }

    private record Storage(TransmutationInterfaceBlockEntity blockEntity) implements MEStorage {
        @Override public Component getDescription() {
            return Component.translatable("block.primordial.transmutation_interface");
        }

        @Override public void getAvailableStacks(KeyCounter out) {
            var owner = blockEntity.getOwner();
            if (owner == null || blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide) return;
            var server = blockEntity.getLevel().getServer();
            if (server == null) return;
            for (var sample : TransmutationNetworkTrade.catalog(server, owner).stacks()) {
                long amount = TransmutationNetworkTrade.affordable(server, owner, sample, Long.MAX_VALUE);
                if (amount > 0) out.add(AEItemKey.of(sample), amount);
            }
            for (var sample : TransmutationNetworkTrade.fluidCatalog(server, owner).stacks()) {
                long amount = TransmutationNetworkTrade.fluidAffordable(server, owner, sample.getFluid(), Long.MAX_VALUE);
                if (amount > 0) out.add(AEFluidKey.of(sample), amount);
            }
        }

        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (amount <= 0) return 0;
            var owner = blockEntity.getOwner();
            var level = blockEntity.getLevel();
            if (owner == null || level == null || level.isClientSide || level.getServer() == null) return 0;
            if (key instanceof AEItemKey item)
                return TransmutationNetworkTrade.extract(level.getServer(), owner, item.toStack(1), amount,
                        mode == Actionable.MODULATE);
            if (key instanceof AEFluidKey fluid)
                return TransmutationNetworkTrade.extractFluid(level.getServer(), owner, fluid.getFluid(), amount,
                        mode == Actionable.MODULATE);
            return 0;
        }

        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (amount <= 0) return 0;
            var owner = blockEntity.getOwner();
            var level = blockEntity.getLevel();
            if (owner == null || level == null || level.isClientSide || level.getServer() == null) return 0;
            if (key instanceof AEItemKey item) {
                var stack = item.toStack((int) Math.min(amount, Integer.MAX_VALUE));
                return TransmutationNetworkTrade.insert(level.getServer(), owner, stack, amount,
                        mode == Actionable.MODULATE);
            }
            if (key instanceof AEFluidKey fluid) {
                FluidStack stack = fluid.toStack((int) Math.min(amount, Integer.MAX_VALUE));
                return TransmutationNetworkTrade.insertFluid(level.getServer(), owner, stack, amount,
                        mode == Actionable.MODULATE);
            }
            return 0;
        }
    }
}
