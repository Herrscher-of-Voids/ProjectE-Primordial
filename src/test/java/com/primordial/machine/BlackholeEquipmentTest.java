/**
 * 【文件职责】验证黑洞装备模块合法性、服务端付款与菜单防误操作。
 * 【变更记录 #1】2026-10-14 01:01:00（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增模块类型/互斥/重复与畸形 NBT 验证、逐笔 EMC 扣费、主线程拒绝和菜单切手关闭测试。
 * - 修改：注册表与账户由隔离替身提供，业务规则和物品容器操作执行真实代码。
 * - 用途：防止模块伪造、免费加成、手持装备移动导致的重复或丢失。
 */
/**
 * 【变更记录 #2】2026-10-14 01:19:38（第 2 次备注 · 1.2.0-equipment-emc-features）
 * - 删除：物品槽安装测试；新增购买、重复请求、余额不足、持久化、开关冲突与旧 NBT 迁移测试。
 * - 修改：执行真实购买/开关服务，账户边界替身；菜单覆盖伪造 ID、换手和其他玩家。
 * - 用途：防止重复扣款、免费解锁和失效菜单修改装备。
 */
/**
 * 【变更记录 #3】2026-10-14 02:06:28（第 3 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：用大整数购买、重复包去重、多功能并启、存档等级钳制、护甲基准和菜单身份替换旧双槽互斥测试。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #4】2026-10-14 03:14:15（第 4 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：购买测试覆盖大整数等级状态和线性批量费用，仍验证状态、重放及并存。
 * - 修改：能力倍率继续使用2^(L-1)，使用费与升级价格改为基础值乘等级/数量，0关闭。
 * - 用途：落实用户新的费用规则；保留历史等级、服务端余额校验和逐刻预算。
 */
package com.primordial.machine;

import com.primordial.equipment.BlackholeGear;
import com.primordial.equipment.BlackholeModules;
import com.primordial.equipment.BlackholeModules.Kind;
import com.primordial.equipment.BlackholeModules.Result;
import com.primordial.menu.BlackholeEquipmentMenu;
import com.primordial.registry.ModMenus;
import com.primordial.service.EmcService;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlackholeEquipmentTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    private static ServerPlayer player() throws Exception {
        var p = mock(ServerPlayer.class);
        var server = mock(MinecraftServer.class);
        var field = ServerPlayer.class.getDeclaredField("server"); field.setAccessible(true); field.set(p, server);
        when(server.isSameThread()).thenReturn(true);
        return p;
    }
    @Test void unboundedPurchaseChargesExactBigIntegerAndRejectsReplay() throws Exception {
        var gear=new ItemStack(Items.DIAMOND_CHESTPLATE);var p=player();
        BigInteger count=BigInteger.valueOf(1000),fee=BigInteger.valueOf(Kind.GUARD.price).multiply(count);
        try(var identity=mockStatic(BlackholeGear.class,CALLS_REAL_METHODS)){
            identity.when(()->BlackholeGear.available(any())).thenReturn(BigInteger.ONE.shiftLeft(2000));
            identity.when(()->BlackholeGear.isGear(any(ItemStack.class))).thenReturn(true);
            identity.when(()->BlackholeGear.isArmor(any(ItemStack.class))).thenReturn(true);
            identity.when(()->BlackholeGear.pay(p,fee)).thenReturn(true);
            assertEquals(Result.OK,BlackholeModules.buy(p,gear,Kind.GUARD,count,BigInteger.ZERO));
            assertEquals(Result.STALE,BlackholeModules.buy(p,gear,Kind.GUARD,count,BigInteger.ZERO));
            assertEquals(count,BlackholeModules.owned(gear,Kind.GUARD));
            assertEquals(BigInteger.ONE,BlackholeModules.level(gear,Kind.GUARD));
            assertEquals(Result.OK,BlackholeModules.setLevel(gear,Kind.GUARD,count));
            var copy=ItemStack.of(gear.save(new CompoundTag()));
            assertEquals(count,BlackholeModules.level(copy,Kind.GUARD));
            assertEquals(Result.OK,BlackholeModules.setLevel(copy,Kind.GUARD,BigInteger.ZERO));
            assertEquals(count,BlackholeModules.owned(copy,Kind.GUARD));
            identity.verify(()->BlackholeGear.pay(p,fee),times(1));
        }
    }
    @Test void everyArmorFeatureCanBeEnabledTogetherAndRepurchased() throws Exception {
        var gear=new ItemStack(Items.DIAMOND_CHESTPLATE);var p=player();
        try(var identity=mockStatic(BlackholeGear.class,CALLS_REAL_METHODS)){
            identity.when(()->BlackholeGear.available(any())).thenReturn(BigInteger.ONE.shiftLeft(2000));
            identity.when(()->BlackholeGear.isGear(gear)).thenReturn(true);
            identity.when(()->BlackholeGear.isArmor(gear)).thenReturn(true);
            identity.when(()->BlackholeGear.pay(eq(p),any(BigInteger.class))).thenReturn(true);
            int expected=0;
            for(Kind kind:Kind.values())if(kind.scope==BlackholeModules.Scope.ARMOR){
                expected++;
                assertEquals(Result.OK,BlackholeModules.buy(p,gear,kind,BigInteger.ONE,BigInteger.ZERO));
                assertEquals(Result.OK,BlackholeModules.buy(p,gear,kind,BigInteger.TEN,BigInteger.ONE));
                assertEquals(BigInteger.valueOf(11),BlackholeModules.owned(gear,kind));
                assertEquals(BigInteger.ONE,BlackholeModules.level(gear,kind));
                assertEquals(Result.OK,BlackholeModules.setLevel(gear,kind,BigInteger.TEN));
            }
            assertEquals(expected,BlackholeModules.active(gear).size());
            assertTrue(expected>2);assertTrue(BlackholeModules.installed(gear,Kind.FLIGHT));
            assertTrue(BlackholeModules.installed(gear,Kind.SWIFT));
            assertNull(Kind.byId("repair"));
            assertEquals(Result.ABOVE_OWNED,BlackholeModules.setLevel(gear,Kind.GUARD,BigInteger.valueOf(12)));
            assertEquals(BigInteger.TEN,BlackholeModules.level(gear,Kind.GUARD));
        }
    }
    @Test void insufficientBalanceInvalidAmountWrongGearAndAsyncNeverMutate() throws Exception {
        var gear=new ItemStack(Items.DIAMOND_CHESTPLATE);var p=player();gear.getOrCreateTag().putInt("marker",7);
        CompoundTag before=gear.getTag().copy();
        try(var identity=mockStatic(BlackholeGear.class,CALLS_REAL_METHODS)){
            identity.when(()->BlackholeGear.available(any())).thenReturn(BigInteger.ONE.shiftLeft(2000));
            identity.when(()->BlackholeGear.isGear(gear)).thenReturn(true);
            identity.when(()->BlackholeGear.isArmor(gear)).thenReturn(true);
            identity.when(()->BlackholeGear.pay(eq(p),any(BigInteger.class))).thenReturn(false);
            assertEquals(Result.NO_EMC,BlackholeModules.buy(p,gear,Kind.GUARD,BigInteger.ONE,BigInteger.ZERO));
            assertEquals(Result.INVALID,BlackholeModules.buy(p,gear,Kind.ASSAULT,BigInteger.ONE,BigInteger.ZERO));
            assertEquals(Result.INVALID,BlackholeModules.buy(p,gear,Kind.GUARD,BigInteger.ZERO,BigInteger.ZERO));
            assertEquals(Result.INVALID,BlackholeModules.buy(p,gear,Kind.GUARD,BigInteger.valueOf(-1),BigInteger.ZERO));
            assertEquals(Result.INVALID,BlackholeModules.buy(p,gear,null,BigInteger.ONE,BigInteger.ZERO));
            when(p.server.isSameThread()).thenReturn(false);
            assertEquals(Result.INVALID,BlackholeModules.buy(p,gear,Kind.GUARD,BigInteger.ONE,BigInteger.ZERO));
            assertEquals(before,gear.getTag());
            identity.verify(()->BlackholeGear.pay(p,BigInteger.valueOf(65536)),times(1));
        }
    }
    @Test void maliciousSavedLevelsAreClampedAndInputsBounded(){
        var gear=new ItemStack(Items.DIAMOND_CHESTPLATE);
        var root=new CompoundTag();var owned=new CompoundTag();var active=new CompoundTag();
        owned.putString("guard","10");active.putString("guard","99999");
        owned.putString("repair","900");active.putString("repair","900");
        root.put("Levels",owned);root.put("Active",active);gear.getOrCreateTag().put(BlackholeModules.KEY,root);
        try(var identity=mockStatic(BlackholeGear.class,CALLS_REAL_METHODS)){
            identity.when(()->BlackholeGear.available(any())).thenReturn(BigInteger.ONE.shiftLeft(2000));
            identity.when(()->BlackholeGear.isGear(gear)).thenReturn(true);identity.when(()->BlackholeGear.isArmor(gear)).thenReturn(true);
            assertEquals(BigInteger.TEN,BlackholeModules.level(gear,Kind.GUARD));
            assertEquals(1,BlackholeModules.active(gear).size());
            assertEquals(Result.INVALID,BlackholeModules.setLevel(gear,Kind.GUARD,BigInteger.valueOf(-1)));
        }
        for(String bad:new String[]{"","-1","1.1","1e50","NaN"," 1","9".repeat(2049)})assertNull(BlackholeModules.parse(bad));
        assertEquals(BigInteger.TEN.pow(100),BlackholeModules.parse("1"+"0".repeat(100)));
    }
    @Test void gemArmorBaselineIsExceededAndMaterialHasNoDurability(){
        var material=com.primordial.equipment.BlackholeArmorItem.Material.INSTANCE;
        int defense=0;
        for(var type:net.minecraft.world.item.ArmorItem.Type.values()){
            defense+=material.getDefenseForType(type);assertEquals(0,material.getDurabilityForType(type));
        }
        assertEquals(50,defense);assertEquals(8,material.getToughness());
    }
    @Test void legacyInstalledModuleMigratesButArbitraryItemDoesNot() {
        var gear = new ItemStack(Items.DIAMOND_CHESTPLATE);
        var saved = new ListTag(); var valid = new CompoundTag();
        valid.putString("id", "primordial:blackhole_module_guard"); valid.putByte("Count", (byte)1);
        saved.add(valid); saved.add(new ItemStack(Items.DIAMOND).save(new CompoundTag()));
        gear.getOrCreateTag().put("BlackholeModules", saved);
        try (var identity = mockStatic(BlackholeGear.class, CALLS_REAL_METHODS)) {
            identity.when(() -> BlackholeGear.isGear(gear)).thenReturn(true);
            identity.when(() -> BlackholeGear.isArmor(gear)).thenReturn(true);
            assertEquals(java.util.EnumSet.of(Kind.GUARD), BlackholeModules.active(gear));
            BlackholeModules.setLevel(gear, Kind.GUARD, BigInteger.ZERO);
            assertTrue(BlackholeModules.purchased(gear, Kind.GUARD));
            assertFalse(gear.getTag().contains("BlackholeModules"));
            assertFalse(BlackholeModules.installed(gear, Kind.GUARD));
        }
    }
    @Test void paymentIsServerThreadAuthoritativeAndCannotExceedAvailableEmc() throws Exception {
        var server = mock(MinecraftServer.class);
        var player = mock(ServerPlayer.class);
        var owner = UUID.randomUUID();
        when(player.getUUID()).thenReturn(owner);
        var field = ServerPlayer.class.getDeclaredField("server"); field.setAccessible(true); field.set(player, server);
        when(server.isSameThread()).thenReturn(true);
        try (var service = mockStatic(EmcService.class)) {
            service.when(() -> EmcService.spend(server, owner, BigInteger.valueOf(12))).thenReturn(true, false);
            assertTrue(BlackholeGear.pay(player, 12));
            assertFalse(BlackholeGear.pay(player, 12));
            when(server.isSameThread()).thenReturn(false);
            assertFalse(BlackholeGear.pay(player, 12));
            service.verify(() -> EmcService.spend(server, owner, BigInteger.valueOf(12)), times(2));
        }
    }

    @Test void menuRejectsForgedIdsOtherPlayerAndChangedHand() throws Exception {
        var field = RegistryObject.class.getDeclaredField("value"); field.setAccessible(true);
        var previous = field.get(ModMenus.BLACKHOLE_EQUIPMENT);
        field.set(ModMenus.BLACKHOLE_EQUIPMENT, mock(MenuType.class));
        try (var identity = mockStatic(BlackholeGear.class, CALLS_REAL_METHODS)) {
            var player = player(); var inventory = new Inventory(player);
            var gear = new ItemStack(Items.DIAMOND_CHESTPLATE); inventory.setItem(0, gear);
            gear.getOrCreateTag().putInt("marker", 7);
            identity.when(() -> BlackholeGear.isGear(gear)).thenReturn(true);
            identity.when(() -> BlackholeGear.isArmor(gear)).thenReturn(true);
            when(player.isAlive()).thenReturn(true); when(player.getInventory()).thenReturn(inventory);
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(gear);
            var menu = new BlackholeEquipmentMenu(1, inventory, InteractionHand.MAIN_HAND);
            player.containerMenu = menu;
            assertTrue(menu.stillValid(player));
            assertFalse(menu.action(player,"forged",true,"1","0"));
            assertFalse(menu.action(player,"guard",true,"-1","0"));
            assertFalse(menu.action(player(),"guard",true,"1","0"));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            assertSame(gear, inventory.getItem(0));
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(ItemStack.EMPTY);
            assertFalse(menu.action(player,"guard",true,"1","0"));
            assertEquals(7, gear.getTag().getInt("marker"));
            assertTrue(menu.quickMoveStack(player, 0).isEmpty());
            identity.verify(() -> BlackholeGear.pay(any(), any(BigInteger.class)), never());
        } finally { field.set(ModMenus.BLACKHOLE_EQUIPMENT, previous); }
    }
}
