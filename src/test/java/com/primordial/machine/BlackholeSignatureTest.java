/**
 * 【变更记录 #1】2026-10-14 02:40:10（第 1 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：覆盖内置等级关闭后其他购买不重启、存档恢复、精确扣费救命、超大范围游标去重和黑洞半径溢出。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 03:14:15（第 2 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：基础能力关闭与指数购买迁移回归使用足额大整数账户预算。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.machine;
import com.primordial.equipment.*;
import com.primordial.equipment.BlackholeModules.Kind;
import com.primordial.registry.ModItems;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlackholeSignatureTest {
    @BeforeAll static void bootstrap(){AdapterTestBootstrap.initialize();}
    @Test void cubeVisitsEveryBlockExactlyOnceFromInsideOut(){
        for(int radius:new int[]{1,2,3,8}){
            var cursor=new BlackholeBlockCursor(radius,Direction.Axis.Y,true);Set<BlockPos> seen=new HashSet<>();BlockPos pos;
            while((pos=cursor.next())!=null){assertTrue(seen.add(pos),"duplicate "+pos);assertTrue(Math.max(Math.abs(pos.getX()),Math.max(Math.abs(pos.getY()),Math.abs(pos.getZ())))<=radius);}
            assertEquals((int)Math.pow(radius*2+1,3)-1,seen.size());assertFalse(seen.contains(BlockPos.ZERO));
        }
    }
    @Test void planarCursorFacesAllThreeAxesAndLargeRangesStartNearby(){
        for(Direction.Axis axis:Direction.Axis.values()){
            var cursor=new BlackholeBlockCursor(2,axis,false);Set<BlockPos> positions=new HashSet<>();BlockPos pos;
            while((pos=cursor.next())!=null){assertEquals(0,pos.get(axis));assertTrue(positions.add(pos));}
            assertEquals(25,positions.size());
        }
        var huge=new BlackholeBlockCursor(60000000,Direction.Axis.Y,true);
        for(int i=0;i<26;i++){var pos=huge.next();assertTrue(pos.distSqr(BlockPos.ZERO)<=3);}
    }
    @Test void innateLevelCanBeDisabledAndStaysOffAfterBuyingOtherFeatures() throws Exception {
        var field=RegistryObject.class.getDeclaredField("value");field.setAccessible(true);var previous=field.get(ModItems.BLACKHOLE_HELMET);
        field.set(ModItems.BLACKHOLE_HELMET,Items.DIAMOND_HELMET);
        try(var gear=mockStatic(BlackholeGear.class,CALLS_REAL_METHODS)){
            var stack=new ItemStack(Items.DIAMOND_HELMET);var p=mock(ServerPlayer.class);var server=mock(MinecraftServer.class);
            var serverField=ServerPlayer.class.getDeclaredField("server");serverField.setAccessible(true);serverField.set(p,server);when(server.isSameThread()).thenReturn(true);
            gear.when(()->BlackholeGear.isGear(any(ItemStack.class))).thenReturn(true);gear.when(()->BlackholeGear.isArmor(any(ItemStack.class))).thenReturn(true);
            gear.when(()->BlackholeGear.available(any())).thenReturn(BigInteger.ONE.shiftLeft(2000));
            gear.when(()->BlackholeGear.pay(eq(p),any(BigInteger.class))).thenReturn(true);
            assertEquals(BigInteger.ONE,BlackholeModules.level(stack,Kind.INSIGHT));
            assertEquals(BlackholeModules.Result.OK,BlackholeModules.setLevel(stack,Kind.INSIGHT,BigInteger.ZERO));
            assertEquals(BlackholeModules.Result.OK,BlackholeModules.buy(p,stack,Kind.GUARD,BigInteger.ONE,BigInteger.ZERO));
            assertEquals(BigInteger.ZERO,BlackholeModules.level(stack,Kind.INSIGHT));
            assertEquals(BlackholeModules.Result.OK,BlackholeModules.buy(p,stack,Kind.INSIGHT,BigInteger.TEN,BigInteger.ONE));
            assertEquals(BigInteger.valueOf(11),BlackholeModules.owned(stack,Kind.INSIGHT));
            var reloaded=ItemStack.of(stack.save(new CompoundTag()));assertEquals(BigInteger.ZERO,BlackholeModules.level(reloaded,Kind.INSIGHT));
            assertEquals(BlackholeModules.Result.OK,BlackholeModules.setLevel(reloaded,Kind.INSIGHT,BigInteger.TEN));
        }finally{field.set(ModItems.BLACKHOLE_HELMET,previous);}
    }
    @Test void deathProtectionRequiresSuccessfulPaymentAndSetsFiniteGrace(){
        var player=mock(ServerPlayer.class);var world=mock(ServerLevel.class);var data=new CompoundTag();var chest=new ItemStack(Items.DIAMOND_CHESTPLATE);
        when(player.getItemBySlot(EquipmentSlot.CHEST)).thenReturn(chest);when(player.getPersistentData()).thenReturn(data);when(player.level()).thenReturn(world);
        when(world.getGameTime()).thenReturn(100L);when(player.getMaxHealth()).thenReturn(20F);
        var source=mock(DamageSource.class);
        try(var modules=mockStatic(BlackholeModules.class);var gear=mockStatic(BlackholeGear.class)){
            modules.when(()->BlackholeModules.level(chest,Kind.UNDYING)).thenReturn(BigInteger.ONE);
            gear.when(()->BlackholeGear.payLevel(player,Kind.UNDYING,BigInteger.ONE)).thenReturn(false,true);
            var first=new LivingDeathEvent(player,source);SignatureArmor.fatal(first);assertFalse(first.isCanceled());
            var second=new LivingDeathEvent(player,source);SignatureArmor.fatal(second);assertTrue(second.isCanceled());verify(player).setHealth(20F);
            assertEquals(140,data.getLong("BlackholeDeathGrace"));
        }
    }
    @Test void singularityRadiusMatchesLevelAndNeverOverflows(){
        assertEquals(8,SingularityEntity.radius(BigInteger.ONE));
        assertTrue(SingularityEntity.radius(BigInteger.TEN)>8);
        assertTrue(Float.isFinite(SingularityEntity.radius(BigInteger.TEN.pow(2047))));
    }
}
