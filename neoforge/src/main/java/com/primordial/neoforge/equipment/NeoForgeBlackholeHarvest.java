package com.primordial.neoforge.equipment;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid=PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeBlackholeHarvest {
    private record Context(ServerPlayer player,boolean smelt,BigInteger fortune){ }
    private static final ThreadLocal<Context> HARVEST=new ThreadLocal<>();
    private NeoForgeBlackholeHarvest() { }
    // 只在原版判定成功破坏且可收获后调用；临时附魔仅用于掉落工具副本。
    public static boolean destroy(Block block,Level level,Player player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack original){
        if(!(player instanceof ServerPlayer p)||!NeoForgeBlackholeGear.isGear(original)){
            block.playerDestroy(level,player,pos,state,entity,original);return false;
        }
        ItemStack tool=original.copy();boolean silk=false;BigInteger expanded=BigInteger.ZERO;
        BigInteger silkLevel=NeoForgeBlackholeModules.level(original,Kind.SILK),fortune=NeoForgeBlackholeModules.level(original,Kind.FORTUNE);
        var registry=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var silkEnchantment=registry.getOrThrow(Enchantments.SILK_TOUCH);
        var fortuneEnchantment=registry.getOrThrow(Enchantments.FORTUNE);
        var enchantments=new ItemEnchantments.Mutable(EnchantmentHelper.getEnchantmentsForCrafting(tool));
        if(silkLevel.signum()>0&&NeoForgeBlackholeGear.payLevel(p,Kind.SILK,silkLevel)){
            enchantments.set(fortuneEnchantment,0);enchantments.set(silkEnchantment,1);silk=true;
        }else if(canEvaluateFortune(fortune)&&NeoForgeBlackholeGear.payLevel(p,Kind.FORTUNE,fortune)){
            enchantments.set(silkEnchantment,0);
            fortune=BigInteger.ONE.shiftLeft(fortune.subtract(BigInteger.ONE).intValueExact());
            // 原版随机掉落在有限等级求值，高等级矿物产量按大整数同比扩展并排队交付。
            enchantments.set(fortuneEnchantment,fortune.min(BigInteger.valueOf(128)).intValue());
            if(fortune.compareTo(BigInteger.valueOf(128))>0&&state.is(Tags.Blocks.ORES))expanded=fortune;
        }
        EnchantmentHelper.setEnchantments(tool,enchantments.toImmutable());
        boolean smelt=NeoForgeBlackholeModules.installed(original,Kind.SMELT)&&NeoForgeBlackholeGear.payLevel(p,Kind.SMELT,NeoForgeBlackholeModules.level(original,Kind.SMELT));
        Context previous=HARVEST.get();HARVEST.set(new Context(p,smelt,expanded));
        try{block.playerDestroy(level,player,pos,state,entity,tool);}finally{if(previous==null)HARVEST.remove();else HARVEST.set(previous);}
        return silk;
    }
    // Preserve oversized stored levels; refuse the effect before charging when decimal delivery cannot be persisted.
    public static boolean canEvaluateFortune(BigInteger level){return level!=null&&level.signum()>0&&level.compareTo(BigInteger.valueOf(26000))<=0;}
    @SubscribeEvent public static void drop(EntityJoinLevelEvent event){
        Context context=HARVEST.get();
        if(context==null||!(event.getEntity() instanceof ItemEntity item)||event.getLevel()!=context.player.level())return;
        ServerPlayer player=context.player;ItemStack input=item.getItem(),result=input.copy();
        BigInteger amount=BigInteger.valueOf(input.getCount());
        if(context.fortune.signum()>0)amount=amount.multiply(context.fortune).divide(BigInteger.valueOf(128));
        if(context.smelt){
            var recipe=player.serverLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(input),player.serverLevel());
            if(recipe.isPresent()){
                ItemStack cooked=recipe.get().value().getResultItem(player.serverLevel().registryAccess());
                if(!cooked.isEmpty()){result=cooked.copy();amount=amount.multiply(BigInteger.valueOf(cooked.getCount()));}
            }
        }
        result.setCount(amount.min(BigInteger.valueOf(result.getMaxStackSize())).intValue());item.setItem(result);
        NeoForgeBlackholeWorldActions.output(player,result,amount.subtract(BigInteger.valueOf(result.getCount())));
    }
}
