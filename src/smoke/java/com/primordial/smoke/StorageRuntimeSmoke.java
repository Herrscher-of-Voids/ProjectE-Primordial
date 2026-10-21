/**
 * 【文件职责】发布环境用真实网络验证转化接口直连和准确结算，不进入正式 JAR。
 * 【变更记录 #1】2026-10-14 14:39:00（第 1 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无。
 * - 新增：AE2/RS 原生世界节点、无外部存储方块购买及模拟不扣费检查。
 * - 修改：隔离世界创建假账户，直接学习煤炭；超越维度验证虚拟目录不写入存档。
 * - 用途：覆盖 Mixin 与真实网络发现，避免仅编译 API 误报已可用。
 */
/**
 * 【变更记录 #2】2026-10-14 15:20:00（第 2 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：未注册维度网络和只读在线列表的旧测试初始化。
 * - 新增：原生线缆、真实注册网络以及模糊提取 NBT 保留断言。
 * - 修改：重载相同测试世界后仍执行完整购买和拆除检查，测试方法按原版声明类型反混淆。
 * - 用途：验证节点恢复、准确收费和禁止库存重新包装成请求 NBT；正常停服另由进程退出确认。
 */
/**
 * 【变更记录 #3】2026-10-14 17:26:00（第 3 次备注 · 1.2.0-rs-node-sync-crash-fix）
 * - 删除：无。新增：真实 RS 失效节点返回空栈、有效电缆仍返回图标的发行 Mixin 断言。
 * - 修改：同时验证未安装铁魔法/Goety 时玩家端口不解析可选 API。
 * - 用途：覆盖控制器同步崩溃触发条件与新兼容的缺模组环境。
 */
package com.primordial.smoke;

import com.primordial.blockentity.TransmutationInterfaceBlockEntity;
import com.primordial.registry.ModBlocks;
import com.primordial.service.EmcService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import java.math.BigInteger;
import static com.primordial.smoke.RuntimeSmoke.check;

final class StorageRuntimeSmoke {
    private static net.minecraft.server.level.ServerPlayer owner;
    private static TransmutationInterfaceBlockEntity ae,rs,bd;
    private static Object dimensionsNet;
    private static java.util.List<net.minecraft.server.level.ServerPlayer> online;
    private static java.util.Map<java.util.UUID,net.minecraft.server.level.ServerPlayer> onlineById;
    static void prepare(MinecraftServer server) throws Exception {
        var world=server.overworld();owner=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
            field.setAccessible(true);
            if(field.getType()==java.util.List.class&&field.get(server.getPlayerList()) instanceof java.util.ArrayList<?> mutable)
                online=(java.util.List<net.minecraft.server.level.ServerPlayer>)mutable;
            if(field.getGenericType() instanceof java.lang.reflect.ParameterizedType type&&field.getType()==java.util.Map.class
                    &&type.getActualTypeArguments()[0]==java.util.UUID.class&&type.getActualTypeArguments()[1]==net.minecraft.server.level.ServerPlayer.class)
                onlineById=(java.util.Map<java.util.UUID,net.minecraft.server.level.ServerPlayer>)field.get(server.getPlayerList());
        }
        check(online!=null&&onlineById!=null,"Online account fixture fields missing");online.add(owner);onlineById.put(owner.getUUID(),owner);
        check(server.getPlayerList().getPlayer(owner.getUUID())==owner,"Fake account must resolve as online");
        owner.getCapability(moze_intel.projecte.api.capabilities.PECapabilities.KNOWLEDGE_CAPABILITY).orElseThrow(()->new AssertionError("Fake account capability missing"))
                .addKnowledge(moze_intel.projecte.api.ItemInfo.fromStack(new ItemStack(Items.COAL)));
        EmcService.add(server,owner.getUUID(),BigInteger.valueOf(1_000_000));
        ae=place(server,new BlockPos(0,90,32));rs=place(server,new BlockPos(16,90,32));bd=place(server,new BlockPos(32,90,32));
        world.setChunkForced(-1,2,true);
        world.setBlockAndUpdate(pos(ae).west(2),block("ae2:creative_energy_cell"));
        world.destroyBlock(pos(ae).west(),false);
        check(appeng.api.parts.PartHelper.setPart(world,pos(ae).west(),null,owner,
                appeng.api.parts.IPartItem.byId(new ResourceLocation("ae2","fluix_glass_cable")))!=null,"Actual AE2 cable placement");
        world.setBlockAndUpdate(pos(rs).west(2),block("refinedstorage:creative_controller"));
        world.setBlockAndUpdate(pos(rs).west(),block("refinedstorage:cable"));
        var netClass=Class.forName("com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet");
        dimensionsNet=netClass.getMethod("createNewNetForPlayer",net.minecraft.world.entity.player.Player.class,long.class,int.class)
                .invoke(null,owner,1_000_000L,1000);
        check(dimensionsNet!=null,"Registered dimension network must be created");
        world.setBlockAndUpdate(pos(bd).west(),block("beyonddimensions:net_pathway"));
        var pathway=world.getBlockEntity(pos(bd).west());
        check(pathway!=null,"Dimension pathway missing");
        pathway.getClass().getMethod("setNetId",int.class).invoke(pathway,netClass.getMethod("getId").invoke(dimensionsNet));
    }
    private static BlockPos pos(TransmutationInterfaceBlockEntity be){return ((net.minecraft.world.level.block.entity.BlockEntity)be).getBlockPos();}
    private static net.minecraft.world.level.block.state.BlockState block(String id){
        var block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));check(block!=null&&block!=net.minecraft.world.level.block.Blocks.AIR,"Missing block: "+id);return block.defaultBlockState();
    }
    private static TransmutationInterfaceBlockEntity place(MinecraftServer server,BlockPos pos){
        var world=server.overworld();world.getChunkAt(pos);world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        world.setBlockAndUpdate(pos,ModBlocks.TRANSMUTATION_INTERFACE.get().defaultBlockState());
        var be=(TransmutationInterfaceBlockEntity)world.getBlockEntity(pos);be.setOwner(owner.getUUID());return be;
    }
    static void run(MinecraftServer server) throws Exception {
        try{
            check(com.primordial.resource.PlayerResourcePorts.find(owner, 0) == null
                    && com.primordial.resource.PlayerResourcePorts.find(owner, 1) == null, "Absent magic mods expose no player ports");
            var removedPos = new BlockPos(80, 100, 32);
            var world = server.overworld(); world.getChunkAt(removedPos);
            world.setBlockAndUpdate(removedPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            var stale = new com.refinedmods.refinedstorage.apiimpl.network.node.CableNetworkNode(world, removedPos);
            check(stale.getItemStack().isEmpty(), "RS stale node sync must return EMPTY instead of constructing ItemStack(null)");
            var valid = new com.refinedmods.refinedstorage.apiimpl.network.node.CableNetworkNode(world, pos(rs).west());
            check(!valid.getItemStack().isEmpty(), "RS valid cable icon remains available");
            var sample=new ItemStack(Items.COAL);var price=EmcService.itemPrice(sample,server).floor();
            var node=((appeng.api.networking.IInWorldGridNodeHost)(Object)ae).getGridNode(net.minecraft.core.Direction.WEST);
            if(node!=null)System.out.println("AE2_NODE_DIAGNOSTIC: active="+node.isActive()+" powered="+node.isPowered()+" gridSize="+node.getGrid().size()+" connections="+node.getConnections().size());
            check(node!=null&&node.isActive(),"AE2 direct node must be powered and active");
            var inventory=node.getGrid().getStorageService().getInventory();
            var key=appeng.api.stacks.AEItemKey.of(sample);var stacks=new appeng.api.stacks.KeyCounter();inventory.getAvailableStacks(stacks);
            check(stacks.get(key)>0,"AE2 native grid must enumerate personal knowledge");
            var before=EmcService.balance(server,owner.getUUID());var source=appeng.api.networking.security.IActionSource.empty();
            check(inventory.extract(key,2,appeng.api.config.Actionable.SIMULATE,source)==2,"AE2 simulate count");
            check(EmcService.balance(server,owner.getUUID()).equals(before),"AE2 simulate must not charge");
            check(inventory.extract(key,2,appeng.api.config.Actionable.MODULATE,source)==2,"AE2 native purchase");
            check(EmcService.balance(server,owner.getUUID()).equals(before.subtract(price.multiply(BigInteger.TWO))),"AE2 exact purchase fee");
            var proxy=rs.getCapability(com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY).orElseThrow(()->new AssertionError("RS node proxy missing"));
            var rsNode=proxy.getNode();check(rsNode.getNetwork()!=null&&rsNode.isActive(),"RS direct node must be active");
            var net=rsNode.getNetwork();before=EmcService.balance(server,owner.getUUID());
            check(net.extractItem(sample,3,com.refinedmods.refinedstorage.api.util.Action.SIMULATE).getCount()==3,"RS simulate count");
            check(EmcService.balance(server,owner.getUUID()).equals(before),"RS simulate must not charge");
            check(net.extractItem(sample,3,com.refinedmods.refinedstorage.api.util.Action.PERFORM).getCount()==3,"RS native purchase");
            check(EmcService.balance(server,owner.getUUID()).equals(before.subtract(price.multiply(BigInteger.valueOf(3)))),"RS exact fee");
            var storage=dimensionsNet.getClass().getMethod("getUnifiedStorage").invoke(dimensionsNet);
            var keyClass=Class.forName("com.wintercogs.beyonddimensions.api.storage.key.IStackKey");
            var bdKey=Class.forName("com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey").getConstructor(ItemStack.class).newInstance(sample);
            var views=(java.util.List<?>)storage.getClass().getMethod("getStorage").invoke(storage);check(!views.isEmpty(),"BD virtual catalog must be mounted");
            var saved=(net.minecraft.nbt.CompoundTag)storage.getClass().getMethod("serializeNBT").invoke(storage);
            check(!saved.toString().contains("minecraft:coal"),"BD virtual inventory must not be persisted");
            before=EmcService.balance(server,owner.getUUID());
            var extract=storage.getClass().getMethod("extract",keyClass,long.class,boolean.class,boolean.class);
            Object result=extract.invoke(storage,bdKey,4L,true,false);
            check(((Number)result.getClass().getMethod("amount").invoke(result)).longValue()==4,"BD simulate count");
            check(EmcService.balance(server,owner.getUUID()).equals(before),"BD simulate must not charge");
            result=extract.invoke(storage,bdKey,4L,false,false);
            check(((Number)result.getClass().getMethod("amount").invoke(result)).longValue()==4,"BD virtual purchase");
            check(EmcService.balance(server,owner.getUUID()).equals(before.subtract(price.multiply(BigInteger.valueOf(4)))),"BD exact fee");
            storage.getClass().getMethod("insert",keyClass,long.class,boolean.class).invoke(storage,bdKey,1L,false);
            var tagged=sample.copy();tagged.getOrCreateTag().putInt("PrimordialSmoke",1);
            var taggedKey=bdKey.getClass().getConstructor(ItemStack.class).newInstance(tagged);
            before=EmcService.balance(server,owner.getUUID());
            result=extract.invoke(storage,taggedKey,1L,false,true);
            var actualStack=(ItemStack)result.getClass().getMethod("toStack").invoke(result);
            check(ItemStack.isSameItemSameTags(actualStack,sample),"Fuzzy native extraction must preserve actual NBT");
            check(EmcService.balance(server,owner.getUUID()).equals(before),"Native stock must not cost EMC");
            server.overworld().destroyBlock(pos(bd),false);
            check(((java.util.List<?>)storage.getClass().getMethod("getStorage").invoke(storage)).isEmpty(),"BD removal must detach virtual catalog");
            System.out.println("NATIVE_STORAGE_RUNTIME_CHECKED: AE2/RS direct nodes; BD virtual mount; exact 2/3/4 coal purchase charges; simulations do not charge; virtual catalog not persisted");
        }finally{online.remove(owner);onlineById.remove(owner.getUUID());}
    }
}
