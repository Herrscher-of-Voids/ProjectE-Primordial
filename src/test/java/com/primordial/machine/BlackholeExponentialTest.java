/**
 * 【变更记录 #1】2026-10-14 03:14:15（第 1 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：时运实际采集1/2/4/8、拳剑双能力、快速拉弓、黑洞范围、指数费用和超大输入、护甲独立指数加算回归。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
/**
 * 【变更记录 #2】2026-10-14 03:42:00（第 2 次备注 · 1.2.0-linear-equipment-cost）
 * - 删除：测试中对费用指数翻倍、几何级数和指数价格文案的断言。
 * - 新增：测试费用按等级线性增长、批量购买按数量计价，同时继续验证能力倍率和大整数等级安全。
 * - 修改：黑洞寿命不再由费用推导，寿命规则改由实体生命周期测试覆盖。
 * - 用途：保证“效果指数成长、费用线性增长”的新规则不会回归。
 */
/**
 * 【变更记录 #3】2026-10-14 05:11:45（第 3 次备注 · 1.2.0-singularity-momentum-crash）
 * - 删除：无。
 * - 新增：高等级黑洞引力与历史高速掉落物加载的速度边界回归。
 * - 修改：覆盖导致末地实体查询溢出的数千万格每刻输入。
 * - 用途：阻止黑洞吸引再次生成损坏实体数据，并验证旧存档可恢复。
 */
package com.primordial.machine;
import com.primordial.equipment.*;
import com.primordial.equipment.BlackholeModules.Kind;
import com.primordial.registry.ModItems;
import com.primordial.service.EmcService;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BlackholeExponentialTest {
    @BeforeAll static void bootstrap(){AdapterTestBootstrap.initialize();}
    @Test void exactUsageAndUpgradeCostsGrowLinearlyWithoutLongOverflow(){
        BigInteger budget=BigInteger.ONE.shiftLeft(3000);
        for(int l:new int[]{1,2,3,4,65,1000}){
            assertEquals(BigInteger.valueOf(16L).multiply(BigInteger.valueOf(l)),BlackholeScaling.usage(16,BigInteger.valueOf(l),budget));
            assertEquals(BigInteger.valueOf(65536),BlackholeScaling.purchase(65536,BigInteger.valueOf(l-1),BigInteger.ONE,budget));
        }
        BigInteger batch=BlackholeScaling.purchase(65536,BigInteger.valueOf(3),BigInteger.valueOf(5),budget),sum=BigInteger.ZERO;
        for(int l=4;l<=8;l++)sum=sum.add(BigInteger.valueOf(65536));
        assertEquals(sum,batch);assertNull(BlackholeScaling.purchase(65536,BigInteger.valueOf(3),BigInteger.valueOf(5),batch.subtract(BigInteger.ONE)));
    }
    @Test void UnaffordableAstronomicalLevelNeverExpandsTheExponent(){
        BigInteger huge=BigInteger.TEN.pow(2047),budget=BigInteger.TEN.pow(2047);
        assertNull(BlackholeScaling.usage(8,huge,budget));
        assertNull(BlackholeScaling.purchase(65536,BigInteger.ZERO,huge,budget));
        assertEquals(60000000,BlackholeScaling.radius(huge,8));
        assertTrue(Double.isFinite(BlackholeScaling.power(huge)));
        assertEquals(BigInteger.valueOf(65536).multiply(huge).toString(),BlackholeScaling.purchaseText(65536,BigInteger.ZERO,huge));
        assertEquals(Long.MAX_VALUE,BlackholeScaling.deadline(100,40,huge));
    }
    @Test void fortuneSkillLevelsBecomeVanillaFortuneOneTwoFourEight(){
        for(int l=1;l<=4;l++){
            var tool=new ItemStack(Items.DIAMOND_PICKAXE);var p=mock(ServerPlayer.class);var world=mock(ServerLevel.class);var block=mock(Block.class);var state=mock(BlockState.class);
            BigInteger skill=BigInteger.valueOf(l);
            try(var gear=mockStatic(BlackholeGear.class);var modules=mockStatic(BlackholeModules.class)){
                gear.when(()->BlackholeGear.isGear(tool)).thenReturn(true);
                modules.when(()->BlackholeModules.level(eq(tool),any())).thenReturn(BigInteger.ZERO);
                modules.when(()->BlackholeModules.level(tool,Kind.FORTUNE)).thenReturn(skill);
                gear.when(()->BlackholeGear.payLevel(p,Kind.FORTUNE,skill)).thenReturn(true);
                BlackholeHarvest.destroy(block,world,p,BlockPos.ZERO,state,null,tool);
                var used=ArgumentCaptor.forClass(ItemStack.class);verify(block).playerDestroy(eq(world),eq(p),eq(BlockPos.ZERO),eq(state),isNull(),used.capture());
                assertEquals(1<<(l-1),EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE,used.getValue()));
                assertEquals(0,EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE,tool));
            }
        }
    }
    @Test void drawSpeedAndBlackholeRadiusDoubleAtEachLevel(){
        assertEquals(1,BlackholeBowItem.drawPower(20,BigInteger.ZERO));
        assertEquals(1,BlackholeBowItem.drawPower(10,BigInteger.ONE));
        assertTrue(BlackholeBowItem.drawPower(9,BigInteger.ONE)<1);
        assertEquals(1,BlackholeBowItem.drawPower(5,BigInteger.TWO));
        assertTrue(BlackholeBowItem.drawPower(4,BigInteger.TWO)<1);
        assertEquals(1,BlackholeBowItem.drawPower(1,BigInteger.valueOf(5)));
        for(int l=1;l<=8;l++)assertEquals(8*(1<<(l-1)),SingularityEntity.radius(BigInteger.valueOf(l)));
    }
    @Test void singularityPullAndLegacyItemMomentumRemainSafe(){
        Vec3 toward=new Vec3(100,20,-10),fast=new Vec3(47020227.68,34525813.62,-7841755.03);
        Vec3 pulled=SingularityEntity.boundedPullVelocity(fast,toward,60000000);
        assertTrue(pulled.length()<=2.5+1e-9);
        assertEquals(Vec3.ZERO,SingularityEntity.boundedPullVelocity(fast,new Vec3(0.001,0,0),60000000));
        var event=mock(EntityJoinLevelEvent.class);var world=mock(ServerLevel.class);var item=mock(ItemEntity.class);
        when(event.getLevel()).thenReturn(world);when(event.getEntity()).thenReturn(item);
        when(item.getDeltaMovement()).thenReturn(fast);
        SingularityEntity.restoreItemMomentum(event);
        var velocity=ArgumentCaptor.forClass(Vec3.class);verify(item).setDeltaMovement(velocity.capture());
        assertTrue(velocity.getValue().length()<=2.5+1e-9);
    }
    @Test void katarHasInnateAttackAndRendAndCanDisableEither() throws Exception {
        var field=RegistryObject.class.getDeclaredField("value");field.setAccessible(true);var prior=field.get(ModItems.BLACKHOLE_KATAR);field.set(ModItems.BLACKHOLE_KATAR,Items.DIAMOND_SWORD);
        try(var gear=mockStatic(BlackholeGear.class,CALLS_REAL_METHODS)){
            var stack=new ItemStack(Items.DIAMOND_SWORD);
            gear.when(()->BlackholeGear.isGear(stack)).thenReturn(true);gear.when(()->BlackholeGear.isArmor(stack)).thenReturn(false);
            assertEquals(BigInteger.ONE,BlackholeModules.level(stack,Kind.ASSAULT));assertEquals(BigInteger.ONE,BlackholeModules.level(stack,Kind.REND));
            BlackholeModules.setLevel(stack,Kind.ASSAULT,BigInteger.ZERO);
            assertEquals(BigInteger.ONE,BlackholeModules.level(stack,Kind.REND));assertEquals(BigInteger.ZERO,BlackholeModules.level(stack,Kind.ASSAULT));
        }finally{field.set(ModItems.BLACKHOLE_KATAR,prior);}
    }
    @Test void armorCostsSumPerPiecePowersAndInsufficientBudgetDoesNotPartiallyCharge() throws Exception {
        var p=mock(ServerPlayer.class);var server=mock(MinecraftServer.class);var f=ServerPlayer.class.getDeclaredField("server");f.setAccessible(true);f.set(p,server);
        when(server.isSameThread()).thenReturn(true);when(p.getUUID()).thenReturn(UUID.randomUUID());
        var a=new ItemStack(Items.DIAMOND_CHESTPLATE);var b=new ItemStack(Items.DIAMOND_HELMET);when(p.getArmorSlots()).thenReturn(List.of(a,b));
        try(var gear=mockStatic(BlackholeGear.class,CALLS_REAL_METHODS);var modules=mockStatic(BlackholeModules.class);var emc=mockStatic(EmcService.class)){
            gear.when(()->BlackholeGear.isArmor(any())).thenReturn(true);
            modules.when(()->BlackholeModules.level(a,Kind.GUARD)).thenReturn(BigInteger.valueOf(3));
            modules.when(()->BlackholeModules.level(b,Kind.GUARD)).thenReturn(BigInteger.TWO);
            emc.when(()->EmcService.balance(server,p.getUUID())).thenReturn(BigInteger.valueOf(79),BigInteger.valueOf(80));
            emc.when(()->EmcService.spend(server,p.getUUID(),BigInteger.valueOf(80))).thenReturn(true);
            assertFalse(BlackholeGear.payArmor(p,Kind.GUARD));assertTrue(BlackholeGear.payArmor(p,Kind.GUARD));
            emc.verify(()->EmcService.spend(server,p.getUUID(),BigInteger.valueOf(80)),times(1));
            assertEquals(6,BlackholeGear.armorPower(p,Kind.GUARD));
        }
    }
}
