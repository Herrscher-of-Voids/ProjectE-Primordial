/**
 * 【变更记录 #1】2026-10-14 02:11:39（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：收获精准优先/付款失败/原物品不变与超大输出满背包持久化回归测试。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 03:14:15（第 2 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：精准按真实启用等级收费，仍不收时运费且不修改实物附魔。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.machine;
import com.primordial.equipment.*;
import com.primordial.equipment.BlackholeModules.Kind;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlackholeHarvestTest {
    @BeforeAll static void bootstrap(){AdapterTestBootstrap.initialize();}
    @Test void silkTakesPriorityOnlyPaysForSilkAndNeverChangesRealTool(){
        var tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.enchant(Enchantments.BLOCK_FORTUNE,3);
        var before=tool.save(new CompoundTag());var p=mock(ServerPlayer.class);var level=mock(ServerLevel.class);
        var block=mock(Block.class);var state=mock(BlockState.class);
        try(var gear=mockStatic(BlackholeGear.class);var modules=mockStatic(BlackholeModules.class)){
            gear.when(()->BlackholeGear.isGear(tool)).thenReturn(true);
            modules.when(()->BlackholeModules.level(tool,Kind.SILK)).thenReturn(BigInteger.TEN);
            modules.when(()->BlackholeModules.level(tool,Kind.FORTUNE)).thenReturn(BigInteger.valueOf(10000));
            gear.when(()->BlackholeGear.payLevel(p,Kind.SILK,BigInteger.TEN)).thenReturn(true);
            assertTrue(BlackholeHarvest.destroy(block,level,p,BlockPos.ZERO,state,null,tool));
            var used=ArgumentCaptor.forClass(ItemStack.class);
            verify(block).playerDestroy(eq(level),eq(p),eq(BlockPos.ZERO),eq(state),isNull(),used.capture());
            assertEquals(1,EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH,used.getValue()));
            assertEquals(0,EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE,used.getValue()));
            assertEquals(before,tool.save(new CompoundTag()));
            gear.verify(()->BlackholeGear.payLevel(eq(p),eq(Kind.FORTUNE),any()),never());
        }
    }
    @Test void failedPaymentLeavesOriginalLootEnchantments(){
        var tool=new ItemStack(Items.DIAMOND_PICKAXE);tool.enchant(Enchantments.BLOCK_FORTUNE,3);
        var p=mock(ServerPlayer.class);var level=mock(ServerLevel.class);var block=mock(Block.class);var state=mock(BlockState.class);
        try(var gear=mockStatic(BlackholeGear.class);var modules=mockStatic(BlackholeModules.class)){
            gear.when(()->BlackholeGear.isGear(tool)).thenReturn(true);
            modules.when(()->BlackholeModules.level(eq(tool),any())).thenReturn(BigInteger.TEN);
            assertFalse(BlackholeHarvest.destroy(block,level,p,BlockPos.ZERO,state,null,tool));
            var used=ArgumentCaptor.forClass(ItemStack.class);
            verify(block).playerDestroy(eq(level),eq(p),eq(BlockPos.ZERO),eq(state),isNull(),used.capture());
            assertEquals(3,EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE,used.getValue()));
        }
    }
    @Test void hugePendingOutputSurvivesFullInventoryAndResumesWithoutDuplicates(){
        var p=mock(ServerPlayer.class);var inventory=mock(Inventory.class);var data=new CompoundTag();
        when(p.getPersistentData()).thenReturn(data);when(p.getInventory()).thenReturn(inventory);
        BigInteger count=BigInteger.TEN.pow(60);
        BlackholeWorldActions.output(p,new ItemStack(Items.DIAMOND),count);
        BlackholeWorldActions.tick(p);
        ListTag queue=data.getCompound(Player.PERSISTED_NBT_TAG).getList("BlackholePendingDrops",Tag.TAG_COMPOUND);
        assertEquals(count.toString(),queue.getCompound(0).getString("Count"));
        when(inventory.add(any(ItemStack.class))).thenAnswer(call->{call.getArgument(0,ItemStack.class).setCount(0);return true;});
        BlackholeWorldActions.tick(p);
        assertEquals(count.subtract(BigInteger.valueOf(8*64)).toString(),queue.getCompound(0).getString("Count"));
        BlackholeWorldActions.output(p,new ItemStack(Items.DIAMOND),BigInteger.TEN);
        assertEquals(1,queue.size());assertEquals(count.subtract(BigInteger.valueOf(502)).toString(),queue.getCompound(0).getString("Count"));
    }
}
