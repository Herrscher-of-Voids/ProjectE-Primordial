/**
 * 【文件职责】隔离发行环境验证两项玩家魔法兼容和黑洞材料真实卖入。
 * 【变更记录 #1】2026-10-14 17:12:00（第 1 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：原生法力/图腾/方舟、欠费/模拟/满额、知识门禁、存档与原生转化桌销售断言。
 * - 修改：只向隔离世界临时注册假账户，完成清理，不读取玩家存档。
 * - 用途：验证真实第三方 JAR 与反混淆产物，不用反射桩替代运行结果。
 */
package com.primordial.smoke;

import com.primordial.resource.*;
import com.primordial.service.*;
import com.primordial.registry.ModItems;
import com.primordial.compat.ProjectECompat;
import com.primordial.data.PlayerInventoryConfigSavedData;
import com.primordial.equipment.BlackholeCollectorConversion;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import static com.primordial.smoke.RuntimeSmoke.check;

final class MagicRuntimeSmoke {
    static void run(MinecraftServer server) throws Exception {
        ServerPlayer owner = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(server.overworld());
        List<ServerPlayer> online = null; Map<UUID,ServerPlayer> ids = null;
        for (var field : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue; field.setAccessible(true);
            if (field.getType() == List.class && field.get(server.getPlayerList()) instanceof ArrayList<?>) online = (List<ServerPlayer>) field.get(server.getPlayerList());
            if (field.getType() == Map.class && field.getGenericType() instanceof java.lang.reflect.ParameterizedType t
                    && t.getActualTypeArguments()[0] == UUID.class && t.getActualTypeArguments()[1] == ServerPlayer.class) ids = (Map<UUID,ServerPlayer>) field.get(server.getPlayerList());
        }
        check(online != null && ids != null, "Account fixture"); online.add(owner); ids.put(owner.getUUID(), owner);
        try {
            EmcService.clearBalance(server, owner.getUUID()); EmcService.add(server, owner.getUUID(), BigInteger.valueOf(1_000_000));
            Class<?> magic = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Object data = magic.getMethod("getPlayerMagicData", LivingEntity.class).invoke(null, owner);
            magic.getMethod("setMana", float.class).invoke(data, 0f);
            var mana = PlayerResourcePorts.find(owner, 0); check(mana != null && mana.canReceive(), "Real Iron mana port");
            var manaCertificate = new ItemStack(ModItems.CERTIFICATES.get(ResourceKnowledge.Kind.IRON_MANA).get());
            owner.getCapability(moze_intel.projecte.api.capabilities.PECapabilities.KNOWLEDGE_CAPABILITY).orElseThrow(() -> new AssertionError("ProjectE knowledge"))
                    .removeKnowledge(moze_intel.projecte.api.ItemInfo.fromStack(manaCertificate));
            check(EmcService.supplyResource(server, owner.getUUID(), mana, 100) == 0 && mana.amount() == 0, "Unlearned mana must not spend");
            ProjectECompat.learn(server, owner.getUUID(), manaCertificate);
            EmcAmount before = funds(server, owner);
            long missing = mana.capacity() - mana.amount();
            check(mana.receive(missing, true) == missing && mana.amount() == 0, "Mana simulation immutable");
            check(EmcService.supplyResource(server, owner.getUUID(), mana, missing) == missing, "Native mana filled");
            check(funds(server, owner).equals(before.subtract(EmcService.resourcePrice(mana.unit()).multiply(missing))), "Exact fractional mana fee");
            before = funds(server, owner); check(EmcService.supplyResource(server, owner.getUUID(), mana, 100) == 0, "Full mana no transfer");
            check(before.equals(funds(server, owner)), "Full mana no cost");

            Class<?> helper = Class.forName("com.Polarice3.Goety.utils.SEHelper");
            helper.getMethod("setSEActive", Player.class, boolean.class).invoke(null, owner, false);
            owner.getInventory().clearContent();
            var souls = PlayerResourcePorts.find(owner, 1); check(souls != null && !souls.canReceive(), "No totem gives no soul capacity");
            var totemItem = BuiltInRegistries.ITEM.get(new ResourceLocation("goety", "totem_of_souls"));
            var totem = (ItemStack) totemItem.getClass().getMethod("getEmptyTotem").invoke(totemItem);
            check(!totem.isEmpty(), "Goety totem exists"); owner.getInventory().setItem(0, totem);
            Class<?> totemType = Class.forName("com.Polarice3.Goety.api.items.magic.ITotem");
            totemType.getMethod("setSoulsAmount", ItemStack.class, int.class).invoke(null, totem, 0);
            check(souls.capacity() > 0, "Native totem capacity");
            ProjectECompat.learn(server, owner.getUUID(), new ItemStack(ModItems.CERTIFICATES.get(ResourceKnowledge.Kind.SOUL_ENERGY).get()));
            check(souls.receive(7, true) == 7 && souls.amount() == 0, "Soul simulation immutable");
            EmcService.clearBalance(server, owner.getUUID()); EmcService.add(server, owner.getUUID(), BigInteger.ONE);
            check(EmcService.supplyResource(server, owner.getUUID(), souls, 2000) == 1000 && souls.amount() == 1000, "Insufficient funds deliver only paid souls");
            check(funds(server, owner).isZero(), "Exact partial soul charge");
            helper.getMethod("setSEActive", Player.class, boolean.class).invoke(null, owner, true);
            helper.getMethod("setSESouls", Player.class, int.class).invoke(null, owner, 0);
            EmcService.add(server, owner.getUUID(), BigInteger.valueOf(100)); before = funds(server, owner);
            check(EmcService.supplyResource(server, owner.getUUID(), souls, 123) == 123 && souls.amount() == 123, "Native Arca account filled");
            check(((Number) totemType.getMethod("currentSouls", ItemStack.class).invoke(null, totem)).intValue() == 1000, "Arca purchase leaves totem unchanged");
            check(funds(server, owner).equals(before.subtract(EmcService.resourcePrice(souls.unit()).multiply(123))), "Exact Arca fee");
            var config = PlayerInventoryConfigSavedData.get(server);
            for (int index = 0; index < 2; index++) if (!config.resourceEnabled(owner.getUUID(), index)) config.toggleResource(owner.getUUID(), index);
            var saved = PlayerInventoryConfigSavedData.load(((net.minecraft.world.level.saveddata.SavedData) config).save(new CompoundTag()));
            check(saved.resourceEnabled(owner.getUUID(), 0) && saved.resourceEnabled(owner.getUUID(), 1), "Resource toggles persist independently");
            magic.getMethod("setMana", float.class).invoke(data, 0f);
            helper.getMethod("setSESouls", Player.class, int.class).invoke(null, owner, 0);
            EmcService.add(server, owner.getUUID(), BigInteger.valueOf(1_000_000)); before = funds(server, owner);
            var fillCost = EmcService.resourcePrice(mana.unit()).multiply(mana.capacity())
                    .add(EmcService.resourcePrice(souls.unit()).multiply(souls.capacity()));
            com.primordial.task.PlayerInventoryTask.run(server);
            check(mana.amount() == mana.capacity() && souls.amount() == souls.capacity(), "Both enabled resources automatically filled");
            check(funds(server, owner).equals(before.subtract(fillCost)), "Automatic supply exact fee");
            config.toggleResource(owner.getUUID(), 0);
            magic.getMethod("setMana", float.class).invoke(data, 0f); before = funds(server, owner);
            com.primordial.task.PlayerInventoryTask.run(server);
            check(mana.amount() == 0 && before.equals(funds(server, owner)), "Paused mana and full souls cost nothing");
            config.toggleResource(owner.getUUID(), 1);

            var inv = new TransmutationInventory(owner); Slot consume = new SlotConsume(inv, 0, 0, 0);
            long expected = 3_733_899;
            for (String tier : BlackholeCollectorConversion.TIERS) {
                var material = new ItemStack(ModItems.BLACKHOLE_MATERIALS.get(tier).get(), 2);
                check(IEMCProxy.INSTANCE.getValue(material) == expected && IEMCProxy.INSTANCE.getSellValue(material) == expected, "Native black hole price: " + tier);
                check(consume.mayPlace(material), "Transmutation consume accepts " + tier);
                BigInteger balance = inv.getAvailableEmc(); consume.set(material);
                check(inv.getAvailableEmc().equals(balance.add(BigInteger.valueOf(expected).multiply(BigInteger.TWO))), "Native table sale exact stack fee: " + tier);
                check(inv.provider.hasKnowledge(material), "Native table learns " + tier); expected *= 2;
            }
            System.out.println("MAGIC_RUNTIME_CHECKED: native Iron mana, Goety totem/Arca; knowledge/simulation/full/partial/fractional fees; saved toggles; all nine black holes native table sales and knowledge");
        } finally { online.remove(owner); ids.remove(owner.getUUID()); }
    }
    private static EmcAmount funds(MinecraftServer server, ServerPlayer owner) {
        return EmcAmount.of(EmcService.balance(server, owner.getUUID())).add(com.primordial.data.EmcConversionSavedData.get(server).get(owner.getUUID()));
    }
}
