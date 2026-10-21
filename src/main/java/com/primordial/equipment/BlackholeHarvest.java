/**
 * 【变更记录 #1】2026-10-14 01:45:54（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：真实收获时付费的精准/时运选择及熔炼，临时工具副本不修改装备附魔，保持原方块掉落与保护事件。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 03:10:28（第 2 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：时运购买等级1/2/3/4映射原版时运1/2/4/8；付款成功后才展开指数，精准优先且全部费用翻倍。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;
import com.primordial.PrimordialMod;
import com.primordial.equipment.BlackholeModules.Kind;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=PrimordialMod.MODID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackholeHarvest {
    private record Context(ServerPlayer player,boolean smelt,BigInteger fortune){ }
    private static final ThreadLocal<Context> HARVEST=new ThreadLocal<>();
    private BlackholeHarvest() { }
    // 只在原版判定成功破坏且可收获后调用；临时附魔仅用于掉落工具副本。
    public static boolean destroy(Block block,Level level,Player player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack original){
        if(!(player instanceof ServerPlayer p)||!BlackholeGear.isGear(original)){
            block.playerDestroy(level,player,pos,state,entity,original);return false;
        }
        ItemStack tool=original.copy();boolean silk=false;BigInteger expanded=BigInteger.ZERO;
        BigInteger silkLevel=BlackholeModules.level(original,Kind.SILK),fortune=BlackholeModules.level(original,Kind.FORTUNE);
        var enchantments=EnchantmentHelper.getEnchantments(tool);
        if(silkLevel.signum()>0&&BlackholeGear.payLevel(p,Kind.SILK,silkLevel)){
            enchantments.remove(Enchantments.BLOCK_FORTUNE);enchantments.put(Enchantments.SILK_TOUCH,1);silk=true;
        }else if(BlackholeGear.payLevel(p,Kind.FORTUNE,fortune)){
            enchantments.remove(Enchantments.SILK_TOUCH);
            fortune=BigInteger.ONE.shiftLeft(fortune.subtract(BigInteger.ONE).intValueExact());
            // 原版随机掉落在有限等级求值，高等级矿物产量按大整数同比扩展并排队交付。
            enchantments.put(Enchantments.BLOCK_FORTUNE,fortune.min(BigInteger.valueOf(128)).intValue());
            if(fortune.compareTo(BigInteger.valueOf(128))>0&&state.is(Tags.Blocks.ORES))expanded=fortune;
        }
        EnchantmentHelper.setEnchantments(enchantments,tool);
        boolean smelt=BlackholeModules.installed(original,Kind.SMELT)&&BlackholeGear.payLevel(p,Kind.SMELT,BlackholeModules.level(original,Kind.SMELT));
        Context previous=HARVEST.get();HARVEST.set(new Context(p,smelt,expanded));
        try{block.playerDestroy(level,player,pos,state,entity,tool);}finally{if(previous==null)HARVEST.remove();else HARVEST.set(previous);}
        return silk;
    }
    @SubscribeEvent public static void drop(EntityJoinLevelEvent event){
        Context context=HARVEST.get();
        if(context==null||!(event.getEntity() instanceof ItemEntity item)||event.getLevel()!=context.player.level())return;
        ServerPlayer player=context.player;ItemStack input=item.getItem(),result=input.copy();
        BigInteger amount=BigInteger.valueOf(input.getCount());
        if(context.fortune.signum()>0)amount=amount.multiply(context.fortune).divide(BigInteger.valueOf(128));
        if(context.smelt){
            var recipe=player.serverLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SimpleContainer(input),player.serverLevel());
            if(recipe.isPresent()){
                ItemStack cooked=recipe.get().getResultItem(player.serverLevel().registryAccess());
                if(!cooked.isEmpty()){result=cooked.copy();amount=amount.multiply(BigInteger.valueOf(cooked.getCount()));}
            }
        }
        result.setCount(amount.min(BigInteger.valueOf(result.getMaxStackSize())).intValue());item.setItem(result);
        BlackholeWorldActions.output(player,result,amount.subtract(BigInteger.valueOf(result.getCount())));
    }
}
