/**
 * 【文件职责】使用真实 Forge 注册表和 ProjectE 映射验证导入基础价及人工覆盖。
 * 【变更记录 #1】2026-10-14 21:10:00（第 1 次备注 · 1.2.0-mainstream-mod-native-emc）
 * - 删除：无。新增：重叠优先级、补充价、显式转化、原生销售与人工定价重算断言。
 * - 修改：隔离服务端假玩家销售物品，不访问用户世界；覆盖结束恢复原价。
 * - 用途：避免只校验 JSON 而不确认原生交易，额外模组缺失时只跳过该组检查。
 */
package com.primordial.smoke;

import com.primordial.compat.ProjectEItemPrices;
import java.math.BigInteger;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import static com.primordial.smoke.RuntimeSmoke.check;

final class CompatEmcRuntimeSmoke {
    static void run(MinecraftServer server) {
        verify("ae2:certus_quartz_crystal", 256);
        verify("ae2:charged_certus_quartz_crystal", 256);
        verify("ae2:sky_stone_block", 64);
        verify("ae2:matter_ball", 256);
        verify("ae2:singularity", 256000);
        verify("ae2:fluix_dust", (256 + IEMCProxy.INSTANCE.getValue(item("minecraft:redstone"))
                + IEMCProxy.INSTANCE.getValue(item("minecraft:quartz"))) / 2);
        if (ModList.get().isLoaded("botania")) {
            verify("botania:ender_air_bottle", 1024);
            verify("botania:life_essence", 256);
            verify("botania:black_lotus", 800);
            verify("botania:blacker_lotus", 10000);
        }
        if (ModList.get().isLoaded("ars_nouveau")) {
            verify("ars_nouveau:wilden_horn", 96);
            verify("ars_nouveau:wilden_wing", 72);
        }
        var owner = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(server.overworld());
        var inv = new TransmutationInventory(owner);
        Slot consume = new SlotConsume(inv, 0, 0, 0);
        var stack = item("ae2:certus_quartz_crystal"); stack.setCount(3);
        check(consume.mayPlace(stack), "Native table accepts imported AE2 price");
        var before = inv.getAvailableEmc(); consume.set(stack);
        check(inv.getAvailableEmc().equals(before.add(BigInteger.valueOf(768))), "Native sale stack count exact");
        check(inv.provider.hasKnowledge(stack), "Native sale learns imported item");
        var id = new ResourceLocation("ae2:certus_quartz_crystal");
        check(ProjectEItemPrices.setPrice(server, id, BigInteger.valueOf(777)), "Native admin override succeeds");
        verify(id.toString(), 777);
        check(ProjectEItemPrices.resetPrice(server, id), "Native admin inherit succeeds");
        verify(id.toString(), 256);
        verify("primordial:blackhole_material_primordial", 3733899);
        verify("primordial:blackhole_material_kerr_newman", 955878144);
        System.out.println("COMPAT_EMC_RUNTIME_CHECKED: native defaults/precedence/conversions/sale/knowledge/admin override/inherit/black-hole ladder");
    }
    private static ItemStack item(String id) {
        var key = new ResourceLocation(id);
        check(BuiltInRegistries.ITEM.containsKey(key), "Registered price target: " + id);
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
    private static void verify(String id, long expected) {
        var stack = item(id);
        long actual = IEMCProxy.INSTANCE.getValue(stack);
        check(actual == expected, id + " expected " + expected + " actual " + actual);
        check(IEMCProxy.INSTANCE.getSellValue(stack) == expected, "Native sale value: " + id);
    }
}
