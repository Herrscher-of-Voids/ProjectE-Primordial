/**
 * 【文件职责】独立发布版 Forge 探针，验证装备 Mixin、原生凝聚器及材料配方，不进入正式模组。
 * 【变更记录 #1】2026-10-14 02:55:08（第 1 次备注 · 1.2.0-singularity-signatures）
 * - 删除：无。
 * - 新增：两代凝聚器循环目标、原初黑洞实际凝聚、材料 EMC 与正式环境类转换检查。
 * - 修改：仅隔离烟测 sourceSet，测试世界不使用玩家存档。
 * - 用途：检测普通 JUnit 无法覆盖的生产 Mixin 和 ProjectE 映射问题。
 */
/**
 * 【变更记录 #2】2026-10-14 03:19:48（第 2 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：发布版实测拉弓2级5刻满蓄力、16EMC与一支箭；实际碰撞生成2/3级黑洞，验证200/400刻寿命和2048/4096扣费。
 * - 修改：统一为等级L对应2^(L-1)效果倍率，使用费与升级价格改为线性，黑洞固定30秒，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
/**
 * 【变更记录 #3】2026-10-14 03:32:08（第 3 次备注 · 1.2.0-singularity-animation）
 * - 删除：黑洞瞬间完整出现、寿命结束直接消失的视觉路径。
 * - 新增：发布版验证新生展开、重载末段进度、百位大整数寿命不误触发结束，以及所有者失效20刻后移除。
 * - 修改：服务端同步出生进度与末段寿命，客户端插值三维模型缩放和透明度。
 * - 用途：黑洞展开、坍缩均随实际寿命；保留指数寿命与原EMC结算。
 */
/**
 * 【变更记录 #4】2026-10-14 05:43:37（第 4 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：两代凝聚器目标循环和凝聚产物断言。
 * - 新增：三代收集器原初黑洞输入、八目标循环与原生升级产物断言。
 * - 修改：检查 ProjectE 收集器燃料映射、同价 EMC 和实际升级槽交付。
 * - 用途：在发布版 Forge 环境验证用户更正后的真实机器流程。
 */
/**
 * 【变更记录 #5】2026-10-14 05:54:38（第 5 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：将八种目标全部视作收集器燃料的测试前提。
 * - 新增：原初黑洞到末端目标的燃料图断言，并禁止其它变体形成额外燃料链。
 * - 修改：八种产物仍分别由原初黑洞和收集器目标虚影实测。
 * - 用途：防止 JEI 与实际机器显示变体到变体的错误路线。
 */
/**
 * 【变更记录 #6】2026-10-14 13:36:00（第 6 次备注 · 1.2.0-blackhole-emc-ladder）
 * - 删除：九种黑洞材料 EMC 全相等的断言。
 * - 新增：逐阶翻倍价格、收集器容量至少十亿 EMC、目标顺序和足额储能后的实际产物断言。
 * - 修改：烟测为每个目标预存其与原初黑洞之间的确切差额，再运行三代收集器原生交易。
 * - 用途：覆盖真实 EMC 梯度与容量扩展，确保高阶材料可支付且不依赖长时间等待发电。
 */
/**
 * 【变更记录 #7】2026-10-14 13:44:23（第 7 次备注 · 1.2.0-blackhole-emc-ladder-generation）
 * - 删除：只检查统一储量下限的收集器验证。
 * - 新增：逐代检查收集器容量为 1/3/6 十亿 EMC，发电速率为 400,000/1,200,000/4,000,000 EMC/刻。
 * - 修改：烟测读取实际 CollectorMK1BlockEntity.emcGen 字段，验证生成速率 Mixin 已应用，而非仅验证配置或源码常量。
 * - 用途：覆盖三代比例扩展后的生产运行时值，防止 ProjectE 映射变化导致容量或发电调整静默失效。
 */
/**
 * 【变更记录 #8】2026-10-14 14:42:00（第 8 次备注 · 1.2.0-collector-sequential-chain）
 * - 删除：原初到任意目标的旧烟测与十万倍发电预期。
 * - 新增：逐阶输入、足额/不足额精确结算和产物不回流检查。
 * - 修改：三代生成字段预期为 40/120/400 EMC/秒，交易检查关闭光照发电以隔离扣费。
 * - 用途：验证真实生产 Mixin、普通燃料隔离与八条唯一升级链。
 */
package com.primordial.smoke;

import com.primordial.equipment.*;
import com.primordial.registry.ModItems;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage.EmcAction;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.gameObjs.block_entities.CollectorMK1BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import java.math.BigInteger;
import static com.primordial.smoke.RuntimeSmoke.check;

final class EquipmentRuntimeSmoke {
    static void run(MinecraftServer server) throws Exception {
        for(String name:new String[]{"net.minecraft.server.level.ServerPlayerGameMode",
                "net.minecraft.world.entity.projectile.AbstractArrow",
                "moze_intel.projecte.gameObjs.container.CollectorMK1Container"}){
            Class<?> type=Class.forName(name);
            check(java.util.Arrays.stream(type.getDeclaredMethods()).anyMatch(m->m.getName().contains("primordial")),"Missing applied mixin: "+name);
        }
        ItemStack base=new ItemStack(ModItems.BLACKHOLE_MATERIALS.get("primordial").get());
        long emc=IEMCProxy.INSTANCE.getValue(base);check(emc>0,"Primordial black hole has no EMC");
        check(emc==3_733_899,"Primordial black hole base EMC changed unexpectedly: "+emc);
        long expected=emc;
        for(String variant:BlackholeCollectorConversion.VARIANTS){
            expected=Math.multiplyExact(expected,2);
            long actual=IEMCProxy.INSTANCE.getValue(new ItemStack(ModItems.BLACKHOLE_MATERIALS.get(variant).get()));
            check(actual==expected,"Black-hole EMC must double at each ordered tier: "+variant+" expected="+expected+" actual="+actual);
        }
        check(FuelMapper.isStackFuel(base)&&!FuelMapper.isStackMaxFuel(base),"Primordial black hole must be upgradeable collector fuel");
        for(int i=0;i<BlackholeCollectorConversion.TIERS.size();i++){
            ItemStack input=BlackholeCollectorConversion.material(BlackholeCollectorConversion.TIERS.get(i));
            check(FuelMapper.isStackFuel(input),"Every black-hole tier must be collector fuel");
            check(ItemStack.isSameItemSameTags(FuelMapper.getFuelUpgrade(input),BlackholeCollectorConversion.next(input)),"Only adjacent tier may upgrade");
            check(FuelMapper.isStackMaxFuel(input)==(i==8),"Only final black hole is terminal");
        }
        var ordered=FuelMapper.getFuelMap();
        int first=ordered.indexOf(base.getItem());
        check(first>0,"Ordinary fuel chain must remain present");
        var ordinaryEnd=new ItemStack(ordered.get(first-1));
        check(FuelMapper.isStackMaxFuel(ordinaryEnd)&&FuelMapper.getFuelUpgrade(ordinaryEnd).isEmpty(),"Ordinary fuel must not enter black-hole chain");
        for(var gear:ModItems.BLACKHOLE_GEAR.values()){
            ItemStack stack=new ItemStack(gear.get());check(!stack.isDamageableItem(),"Gear must be unbreakable: "+gear.getId());
            check(!BlackholeModules.active(stack).isEmpty(),"Missing innate ability: "+gear.getId());
            check(server.getRecipeManager().byKey(gear.getId()).isPresent(),"Missing gear recipe: "+gear.getId());
        }
        var world=server.overworld();var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        var generationField=CollectorMK1BlockEntity.class.getDeclaredField("emcGen");
        generationField.setAccessible(true);
        var forceInsert=moze_intel.projecte.api.block_entity.BaseEmcBlockEntity.class.getDeclaredMethod("forceInsertEmc",long.class,EmcAction.class);
        var forceExtract=moze_intel.projecte.api.block_entity.BaseEmcBlockEntity.class.getDeclaredMethod("forceExtractEmc",long.class,EmcAction.class);
        forceInsert.setAccessible(true);forceExtract.setAccessible(true);
        int index=0;
        String[] collectorIds={"collector_mk1","collector_mk2","collector_mk3"};
        long[] expectedCapacity={1_000_000_000L,3_000_000_000L,6_000_000_000L};
        long[] expectedGeneration={40L,120L,400L};
        for(int tierIndex=0;tierIndex<collectorIds.length;tierIndex++){
            String id=collectorIds[tierIndex];
            BlockPos pos=new BlockPos(index++*4,90,0);world.getChunkAt(pos);
            var block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("projecte",id));
            check(block!=null,"Collector block ID "+id);world.setBlockAndUpdate(pos,block.defaultBlockState());
            var be=(CollectorMK1BlockEntity)world.getBlockEntity(pos);
            check(be.getMaximumEmc()==expectedCapacity[tierIndex],"Collector proportional storage mismatch: "+id+" actual="+be.getMaximumEmc());
            check(generationField.getLong(be)==expectedGeneration[tierIndex],"Collector proportional generation mismatch: "+id+" actual="+generationField.getLong(be));
            player.setPos(pos.getX()+0.5,91,0.5);
            var aux=(IItemHandlerModifiable)be.getAux();
            var menu=be.createMenu(7,player.getInventory(),player);player.containerMenu=menu;
            generationField.setLong(be,0); // Isolate native billing from sunlight generation.
            for(String variant:BlackholeCollectorConversion.VARIANTS){
                int nextIndex=BlackholeCollectorConversion.TIERS.indexOf(variant);
                ItemStack input=BlackholeCollectorConversion.material(BlackholeCollectorConversion.TIERS.get(nextIndex-1));
                ItemStack output=BlackholeCollectorConversion.material(variant);
                long difference=IEMCProxy.INSTANCE.getValue(output)-IEMCProxy.INSTANCE.getValue(input);
                forceExtract.invoke(be,be.getStoredEmc(),EmcAction.EXECUTE);
                aux.setStackInSlot(0,input.copy());aux.setStackInSlot(1,ItemStack.EMPTY);
                aux.setStackInSlot(2,BlackholeCollectorConversion.material("kerr_newman")); // Legacy jump target must be corrected.
                check(((Long)forceInsert.invoke(be,difference-1,EmcAction.EXECUTE))==difference-1,"Collector accepts adjacent difference");
                for(int tick=0;tick<40;tick++)CollectorMK1BlockEntity.tickServer(world,pos,world.getBlockState(pos),be);
                check(aux.getStackInSlot(0).is(input.getItem())&&aux.getStackInSlot(1).isEmpty(),"Insufficient EMC must not consume material");
                check(ItemStack.isSameItemSameTags(aux.getStackInSlot(2),output),"Legacy jump lock must become adjacent target");
                forceInsert.invoke(be,1L,EmcAction.EXECUTE);
                for(int tick=0;tick<80&&!aux.getStackInSlot(0).isEmpty();tick++)CollectorMK1BlockEntity.tickServer(world,pos,world.getBlockState(pos),be);
                check(aux.getStackInSlot(0).isEmpty(),"Collector did not consume preceding tier");
                check(aux.getStackInSlot(1).is(output.getItem())&&aux.getStackInSlot(1).getCount()==1,"Collector must produce exactly one adjacent tier");
                check(be.getStoredEmc()==0,"Adjacent EMC difference must be charged exactly");
                forceInsert.invoke(be,999_999_999L,EmcAction.EXECUTE);
                for(int tick=0;tick<80;tick++)CollectorMK1BlockEntity.tickServer(world,pos,world.getBlockState(pos),be);
                check(aux.getStackInSlot(0).isEmpty()&&aux.getStackInSlot(1).is(output.getItem()),"Black-hole output must not re-upgrade even with excess EMC");
            }
            aux.setStackInSlot(0,new ItemStack(net.minecraft.world.item.Items.COAL));aux.setStackInSlot(1,ItemStack.EMPTY);
            aux.setStackInSlot(2,BlackholeCollectorConversion.material("kerr_newman"));
            for(int tick=0;tick<80;tick++)CollectorMK1BlockEntity.tickServer(world,pos,world.getBlockState(pos),be);
            check(!BlackholeCollectorConversion.isMaterial(aux.getStackInSlot(0))&&!BlackholeCollectorConversion.isMaterial(aux.getStackInSlot(1)),"Ordinary fuel must never become black holes");
            generationField.setLong(be,expectedGeneration[tierIndex]);
            player.containerMenu=player.inventoryMenu;
        }
        // Real server arrows: rapid draw consumes the linear level fee, updates ammo, then impact creates a fixed-lifetime black hole.
        com.primordial.service.EmcService.add(server,player.getUUID(),BigInteger.valueOf(1000000));
        ItemStack bow=new ItemStack(ModItems.BLACKHOLE_BOW.get());
        var root=new net.minecraft.nbt.CompoundTag();var levels=new net.minecraft.nbt.CompoundTag();var active=new net.minecraft.nbt.CompoundTag();
        levels.putString("rapid_draw","2");active.putString("rapid_draw","2");
        levels.putString("singularity","2");active.putString("singularity","2");
        root.put("Levels",levels);root.put("Active",active);bow.getOrCreateTag().put(BlackholeModules.KEY,root);
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,bow);
        player.getInventory().setItem(1,new ItemStack(net.minecraft.world.item.Items.ARROW,4));
        BigInteger before=com.primordial.service.EmcService.balance(server,player.getUUID());
        bow.getItem().releaseUsing(bow,world,player,bow.getUseDuration()-5);
        check(com.primordial.service.EmcService.balance(server,player.getUUID()).equals(before.subtract(BigInteger.valueOf(16))),"Rapid draw should charge 16 EMC at level 2");
        check(player.getInventory().getItem(1).getCount()==3,"Rapid draw should consume exactly one arrow");
        net.minecraft.world.entity.projectile.AbstractArrow fired=null;
        for(var entity:world.getAllEntities())if(entity instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow&&arrow.getOwner()==player)fired=arrow;
        check(fired!=null&&fired.isCritArrow(),"Level 2 rapid draw must fully charge at 5 ticks");
        for(int skill:new int[]{2,3}){
            fired.getPersistentData().putString(SingularityEntity.ARROW_LEVEL,Integer.toString(skill));
            before=com.primordial.service.EmcService.balance(server,player.getUUID());
            java.util.Set<java.util.UUID> prior=new java.util.HashSet<>();
            for(var entity:world.getAllEntities())if(entity instanceof SingularityEntity)prior.add(((net.minecraft.world.entity.Entity)entity).getUUID());
            SingularityEntity.impact(new net.minecraftforge.event.entity.ProjectileImpactEvent(fired,new net.minecraft.world.phys.BlockHitResult(
                new net.minecraft.world.phys.Vec3(0,92,0),net.minecraft.core.Direction.UP,new BlockPos(0,91,0),false)));
            check(com.primordial.service.EmcService.balance(server,player.getUUID()).equals(before.subtract(BigInteger.valueOf(1024L*skill))),"Impact linear fee");
            SingularityEntity hole=null;
            for(var entity:world.getAllEntities())if(entity instanceof SingularityEntity found&&!prior.contains(((net.minecraft.world.entity.Entity)found).getUUID()))hole=found;
            check(hole!=null,"Black hole must spawn on impact");
            var saved=((net.minecraft.world.entity.Entity)hole).saveWithoutId(new net.minecraft.nbt.CompoundTag());
            check(saved.getString("RemainingTicks").equals("600"),"Black hole lifetime is fixed at 30 seconds");
            check(hole.radius()==(8<<(skill-1)),"Black hole radius doubles");
            check(hole.openingAge(0)==0&&hole.endingTicks(0)==20,"Fresh black hole starts opening with no premature collapse");
            var restored=new SingularityEntity(com.primordial.registry.ModEntities.SINGULARITY.get(),world);
            saved.putInt("Age",50);saved.putString("RemainingTicks","10");
            ((net.minecraft.world.entity.Entity)restored).load(saved);
            check(restored.openingAge(0)==12&&restored.endingTicks(0)==10,"Late tracking/load must show current closing phase");
            saved.putString("RemainingTicks",BigInteger.TEN.pow(100).toString());
            ((net.minecraft.world.entity.Entity)restored).load(saved);
            check(restored.endingTicks(0)==20,"Malformed oversized lifetime must clamp to the fixed 30-second lifecycle");
            ((net.minecraft.world.entity.Entity)restored).tick(); // Fake player is not in the online player list: only visual closure may remain.
            var closing=((net.minecraft.world.entity.Entity)restored).saveWithoutId(new net.minecraft.nbt.CompoundTag());
            check(closing.getBoolean("Closing")&&closing.getString("RemainingTicks").equals("20"),"Owner loss enters a 20-tick visual closure");
            for(int tick=0;tick<20;tick++)((net.minecraft.world.entity.Entity)restored).tick();
            check(((net.minecraft.world.entity.Entity)restored).isRemoved(),"Visual closure must finish and remove the entity");
        }
        System.out.println("LINEAR_RUNTIME_CHECKED: level 2 rapid draw full charge at 5 ticks, exact 16 EMC and one arrow; black holes level 2/3 have fixed 600 tick lifetime and exact 2048/3072 EMC fees");
        var sword=new ItemStack(ModItems.BLACKHOLE_SWORD.get());
        check(BlackholeModules.level(sword,BlackholeModules.Kind.EXECUTE).equals(BigInteger.ONE),"Sword innate execution");
        System.out.println("EQUIPMENT_RUNTIME_CHECKED: black-hole EMC doubles from "+emc+" to "+expected+", 14 recipes/unbreakable/signatures, three collectors store and pay all eight target differences, production mixins");
    }
}
