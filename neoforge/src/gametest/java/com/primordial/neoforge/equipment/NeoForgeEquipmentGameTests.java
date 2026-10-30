package com.primordial.neoforge.equipment;

import java.math.BigInteger;
import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Result;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeEquipmentGameTests {
    private static final BigInteger FUNDING = BigInteger.valueOf(10_000_000);
    private NeoForgeEquipmentGameTests() {}

    @GameTest(template="capability_proxy_empty", templateNamespace=PrimordialNeoForgeMod.MOD_ID)
    public static void purchasedLevelsPersistAndReplayDoesNotCharge(GameTestHelper helper) {
        ServerPlayer player = login(helper);
        try {
            ItemStack pick = gear("pick");
            BigInteger before = NeoForgeBlackholeGear.available(player);
            check(NeoForgeBlackholeModules.buy(player,pick,Kind.FORTUNE,BigInteger.TWO,BigInteger.ZERO)==Result.OK,"Two levels must be purchased");
            check(NeoForgeBlackholeGear.available(player).equals(before.subtract(BigInteger.valueOf(Kind.FORTUNE.price*2L))),"Batch cost must match current Forge base price times count");
            check(NeoForgeBlackholeModules.buy(player,pick,Kind.FORTUNE,BigInteger.TWO,BigInteger.ZERO)==Result.STALE,"Replay must be rejected");
            check(NeoForgeBlackholeModules.setLevel(pick,Kind.FORTUNE,BigInteger.TWO)==Result.OK,"Purchased level must be selectable");
            check(NeoForgeBlackholeModules.setLevel(pick,Kind.FORTUNE,BigInteger.valueOf(3))==Result.ABOVE_OWNED,"Active strength cannot exceed purchased strength");
            ItemStack restored = ItemStack.parse(player.registryAccess(),pick.save(player.registryAccess())).orElseThrow();
            check(NeoForgeBlackholeModules.level(restored,Kind.FORTUNE).equals(BigInteger.TWO),"Active level must survive item serialization");
            BigInteger charge = NeoForgeBlackholeGear.charge(player,Kind.FORTUNE,BigInteger.TWO);
            check(charge.equals(BigInteger.valueOf(32)),"Use price must match current Forge base price times level");
            NeoForgeBlackholeGear.refund(player,charge);
            check(NeoForgeBlackholeGear.available(player).equals(before.subtract(BigInteger.valueOf(Kind.FORTUNE.price*2L))),"Refund must exactly restore a real ProjectE charge");
            check(!NeoForgeBlackholeHarvest.canEvaluateFortune(BigInteger.TEN.pow(100)),"Unrepresentable Fortune output must be refused before charging");
            helper.succeed();
        } finally { logout(player); }
    }

    @GameTest(template="capability_proxy_empty", templateNamespace=PrimordialNeoForgeMod.MOD_ID)
    public static void equipmentMenuRejectsOtherOwnerSwappedHandAndWrongWindow(GameTestHelper helper) {
        ServerPlayer owner = login(helper), stranger = login(helper);
        try {
            ItemStack pick = gear("pick"); owner.setItemInHand(InteractionHand.MAIN_HAND,pick);
            NeoForgeBlackholeEquipmentMenu menu = new NeoForgeBlackholeEquipmentMenu(7,owner.getInventory(),InteractionHand.MAIN_HAND);
            owner.containerMenu=menu; stranger.containerMenu=menu;
            BigInteger initial = NeoForgeBlackholeGear.available(owner);
            check(!menu.action(stranger,"fortune",true,"1","0"),"Another player cannot operate the owner's menu");
            NeoForgeBlackholeEquipmentPayloads.handle(owner,new NeoForgeBlackholeEquipmentPayloads.Action(8,"fortune",true,"1","0"));
            check(NeoForgeBlackholeGear.available(owner).equals(initial),"Wrong window must not charge");
            owner.setItemInHand(InteractionHand.MAIN_HAND,pick.copy());
            check(!menu.action(owner,"fortune",true,"1","0")&&NeoForgeBlackholeGear.available(owner).equals(initial),"Replacing gear with an identical copy must invalidate the bound instance");
            helper.succeed();
        } finally { owner.containerMenu=owner.inventoryMenu; stranger.containerMenu=stranger.inventoryMenu; logout(stranger); logout(owner); }
    }

    @GameTest(template="capability_proxy_empty", templateNamespace=PrimordialNeoForgeMod.MOD_ID)
    public static void failedArrowSpawnRefundsEveryEffectAndPreservesAmmo(GameTestHelper helper) {
        ServerPlayer player = login(helper);
        var rejected = new java.util.concurrent.atomic.AtomicBoolean();
        Consumer<EntityJoinLevelEvent> reject = event -> { if(event.getEntity() instanceof AbstractArrow arrow&&arrow.getOwner()==player){rejected.set(true);event.setCanceled(true);} };
        try {
            ItemStack bow = gear("bow"); player.setItemInHand(InteractionHand.MAIN_HAND,bow);
            buy(player,bow,Kind.INFINITY); buy(player,bow,Kind.RAPID_DRAW); buy(player,bow,Kind.PIERCE); buy(player,bow,Kind.ARROW_SPEED);
            player.getInventory().setItem(1,new ItemStack(Items.ARROW,3));
            BigInteger before = NeoForgeBlackholeGear.available(player);
            NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,reject);
            ((NeoForgeBlackholeBowItem)bow.getItem()).releaseUsing(bow,player.level(),player,71990);
            check(rejected.get(),"The refund check must execute an actual canceled arrow spawn");
            check(NeoForgeBlackholeGear.available(player).equals(before),"Canceled spawn must refund Infinity, rapid draw, pierce and speed");
            check(player.getInventory().getItem(1).getCount()==3,"Canceled arrow spawn must preserve ammunition");
            helper.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(reject); logout(player); }
    }

    @GameTest(template="capability_proxy_empty", templateNamespace=PrimordialNeoForgeMod.MOD_ID)
    public static void paidBowProducesFastPiercingArrowWithActualDamage(GameTestHelper helper) {
        ServerPlayer player = login(helper);
        try {
            ItemStack bow=gear("bow"); player.setItemInHand(InteractionHand.MAIN_HAND,bow);
            buy(player,bow,Kind.INFINITY); buy(player,bow,Kind.PIERCE); buy(player,bow,Kind.ARROW_SPEED);
            BigInteger before=NeoForgeBlackholeGear.available(player);
            ((NeoForgeBlackholeBowItem)bow.getItem()).releaseUsing(bow,player.level(),player,71980);
            var arrows=player.serverLevel().getEntitiesOfClass(AbstractArrow.class,player.getBoundingBox().inflate(3),arrow->arrow.getOwner()==player);
            check(arrows.size()==1,"Paid Infinity must emit an arrow without physical ammunition");
            AbstractArrow arrow=arrows.getFirst();
            check(arrow.getBaseDamage()==32&&arrow.getDeltaMovement().length()>3.5&&arrow.getPierceLevel()>0,"Damage, speed and piercing must reach the actual projectile");
            check("2".equals(arrow.getPersistentData().getString(NeoForgeBlackholeBowItem.PIERCE_REMAINING)),"Piercing remaining count must be persisted as an integer");
            check("1".equals(arrow.getPersistentData().getString("BlackholeImpactLevel")),"The innate singularity level must use the Forge arrow key");
            check(NeoForgeBlackholeGear.available(player).equals(before.subtract(BigInteger.valueOf(28))),"Only the three paid arrow effects must charge on release");
            arrow.discard(); helper.succeed();
        } finally { logout(player); }
    }

    @GameTest(template="capability_proxy_empty", templateNamespace=PrimordialNeoForgeMod.MOD_ID)
    public static void realHarvestAppliesSilkAndSmeltingThroughGameMode(GameTestHelper helper) {
        ServerPlayer player=login(helper);
        BlockPos silk=helper.absolutePos(new BlockPos(1,1,1)), smelt=silk.east();
        try {
            ItemStack pick=gear("pick"); player.setItemInHand(InteractionHand.MAIN_HAND,pick);
            NeoForgeBlackholeModules.setLevel(pick,Kind.AREA,BigInteger.ZERO); NeoForgeBlackholeModules.setLevel(pick,Kind.VEIN,BigInteger.ZERO);
            buy(player,pick,Kind.SILK); player.serverLevel().setBlockAndUpdate(silk,Blocks.DIAMOND_ORE.defaultBlockState());
            check(player.gameMode.destroyBlock(silk),"Game-mode harvest must succeed");
            check(player.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(silk).inflate(1),drop->drop.getItem().is(Items.DIAMOND_ORE)).size()==1,"Paid silk must change the real ore loot");
            NeoForgeBlackholeModules.setLevel(pick,Kind.SILK,BigInteger.ZERO); buy(player,pick,Kind.SMELT);
            player.serverLevel().setBlockAndUpdate(smelt,Blocks.IRON_ORE.defaultBlockState());
            check(player.gameMode.destroyBlock(smelt),"Second game-mode harvest must succeed");
            check(player.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(smelt).inflate(1),drop->drop.getItem().is(Items.IRON_INGOT)).size()==1,"Auto-smelt must cook real item drops");
            helper.succeed();
        } finally { player.serverLevel().setBlockAndUpdate(silk,Blocks.AIR.defaultBlockState()); player.serverLevel().setBlockAndUpdate(smelt,Blocks.AIR.defaultBlockState()); logout(player); }
    }

    @GameTest(template="capability_proxy_empty", templateNamespace=PrimordialNeoForgeMod.MOD_ID,timeoutTicks=100)
    public static void paidArmorMitigatesRealDamageAndZeroBalanceDoesNot(GameTestHelper helper) {
        ServerPlayer player=login(helper);
        // A real ServerPlayer starts with 60 ticks of spawn protection.
        helper.runAfterDelay(61,()->{
        try {
            ItemStack helmet=gear("helmet"); buy(player,helmet,Kind.GUARD); player.setItemSlot(EquipmentSlot.HEAD,helmet);
            player.setHealth(20); player.invulnerableTime=0; BigInteger before=NeoForgeBlackholeGear.available(player);
            player.hurt(player.damageSources().magic(),10);
            float paidLoss=20-player.getHealth();
            check(paidLoss>0&&paidLoss<10&&NeoForgeBlackholeGear.available(player).equals(before.subtract(BigInteger.valueOf(16))),"Paid guard must reduce actual magic damage and charge once");
            player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(BigInteger.ZERO);
            player.setHealth(20); player.invulnerableTime=0; player.hurt(player.damageSources().magic(),10);
            check(20-player.getHealth()>paidLoss,"Unfunded guard must not retain its paid reduction");
            helper.succeed();
        } finally { logout(player); }
        });
    }

    private static ItemStack gear(String name) { return new ItemStack(NeoForgeModRegistries.BLACKHOLE_GEAR.get(name).get()); }
    private static void buy(ServerPlayer player,ItemStack stack,Kind kind) {
        check(NeoForgeBlackholeModules.buy(player,stack,kind,BigInteger.ONE,NeoForgeBlackholeModules.owned(stack,kind))==Result.OK,"Feature purchase failed: "+kind);
    }
    private static ServerPlayer login(GameTestHelper helper) {
        var server=helper.getLevel().getServer(); var profile=new GameProfile(UUID.randomUUID(),"equipment-test");
        var cookie=new CommonListenerCookie(profile,0,ClientInformation.createDefault(),false,ConnectionType.NEOFORGE);
        ServerPlayer player=server.getPlayerList().getPlayerForLogin(profile,cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND); var channel=new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection,player,cookie);
            BlockPos pos=helper.absolutePos(new BlockPos(1,3,1)); player.teleportTo(helper.getLevel(),pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0,0);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(FUNDING);
            return player;
        } catch(RuntimeException|Error failure) { channel.finishAndReleaseAll(); throw failure; }
    }
    private static void logout(ServerPlayer player) {
        try { player.server.getPlayerList().remove(player); }
        finally { if(player.connection.getConnection().channel() instanceof EmbeddedChannel channel)channel.finishAndReleaseAll(); }
    }
    private static void check(boolean success,String message) { if(!success)throw new GameTestAssertException(message); }
}
