/**
 * 【文件职责】在独立 Forge 服务端验证加速器五种模式与真实世界对象、账户的连接。
 * 【变更记录 #1】2026-09-18 02:21:56（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：熔炉、范围、掉落实体、生物、昼夜、天气、规则门禁及到期停止的真实运行断言。
 * - 修改：测试通过反射进入包内调度入口，使用确定性预算时钟；付款仍调用正式 EMC 服务。
 * - 用途：只使用测试世界和假玩家账户，不连接用户整合包；这验证功能和扣款，不是 TPS 压测。
 */
package com.primordial.smoke;

import com.mojang.authlib.GameProfile;
import com.primordial.registry.ModItems;
import com.primordial.service.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.UUID;
import java.util.function.LongSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import static com.primordial.smoke.RuntimeSmoke.check;

final class AccelerationRuntimeSmoke {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000052");
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    private static Constructor<?> budgetConstructor;
    private static Method process;
    private AccelerationRuntimeSmoke() { }

    static void prepare(MinecraftServer server) {
        server.overworld().setChunkForced(0, 0, true);
        server.overworld().setChunkForced(-1, 0, true);
    }

    static void run(MinecraftServer server) throws Exception {
        check(!ModList.get().isLoaded("ae2") && !ModList.get().isLoaded("create"), "optional acceleration APIs absent");
        Class<?> budget = Class.forName("com.primordial.service.AccelerationBudget");
        budgetConstructor = budget.getDeclaredConstructor(int.class, int.class, long.class, LongSupplier.class);
        budgetConstructor.setAccessible(true);
        process = EmcAccelerationService.class.getDeclaredMethod("process", MinecraftServer.class, ServerPlayer.class, ItemStack.class, budget);
        process.setAccessible(true);
        ServerLevel level = server.overworld();
        var player = FakePlayerFactory.get(level, new GameProfile(OWNER, "AccelSmoke"));
        player.setPos(.5, 101, .5);
        EmcService.add(server, OWNER, BigInteger.valueOf(1_000_000));
        check(level.shouldTickBlocksAt(POS), "test chunk must be ticking");

        FurnaceBlockEntity first = furnace(level, POS);
        ItemStack tool = tool(player, AccelerationSettings.Kind.BLOCK, false);
        EmcAccelerationService.bind(tool, player, level, POS, InteractionHand.MAIN_HAND);
        BigInteger balance = EmcService.balance(server, OWNER);
        run(server, player, tool, 20);
        check(first.saveWithoutMetadata().getShort("CookTime") >= 59, "furnace received extra native ticks");
        spent(server, balance, 4000);

        tool = tool(player, AccelerationSettings.Kind.ENTITY, false);
        var item = new ItemEntity(level, .5, 101, .5, new ItemStack(Items.DIAMOND));
        item.setNoGravity(true); item.setDeltaMovement(0, 0, 0); level.addFreshEntity(item);
        AccelerationBindingActions.entity(tool, player, item, InteractionHand.MAIN_HAND);
        balance = EmcService.balance(server, OWNER);
        run(server, player, tool, 20);
        check(item.tickCount == 60, "dropped item tickCount=" + item.tickCount);
        spent(server, balance, 4000);
        item.discard();
        run(server, player, tool, 1);
        check(AccelerationBindings.view(tool).stream().noneMatch(AccelerationBindings::enabled), "removed entity stops");
        spent(server, balance, 4000);

        tool = tool(player, AccelerationSettings.Kind.ENTITY, true);
        var mob = EntityType.PIG.create(level); check(mob != null, "mob creation");
        mob.setPos(.5, 101, .5); mob.setNoGravity(true); mob.setBaby(true); level.addFreshEntity(mob);
        AccelerationBindingActions.entity(tool, player, mob, InteractionHand.MAIN_HAND);
        balance = EmcService.balance(server, OWNER);
        run(server, player, tool, 20);
        check(mob.tickCount == 60 && mob.getAge() == -23940, "mob aging must advance");
        check(!mob.isNoAi(), "temporary AI flag restored");
        spent(server, balance, 4000); mob.discard();

        tool = tool(player, AccelerationSettings.Kind.DAYTIME, false);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);
        level.setDayTime(1000); long gameTime = level.getGameTime();
        check(AccelerationBindingActions.world(tool, player, InteractionHand.MAIN_HAND), "daytime bind");
        balance = EmcService.balance(server, OWNER);
        run(server, player, tool, 20);
        check(level.getDayTime() == 1060 && level.getGameTime() == gameTime, "only daylight clock advances");
        spent(server, balance, 4000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        run(server, player, tool, 1);
        check(AccelerationBindings.view(tool).stream().noneMatch(AccelerationBindings::enabled), "daylight gamerule stops");
        spent(server, balance, 4000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, server);

        tool = tool(player, AccelerationSettings.Kind.WEATHER, false);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(true, server);
        var data = (ServerLevelData) level.getLevelData();
        data.setClearWeatherTime(0); data.setRainTime(1000); data.setThunderTime(1000);
        check(AccelerationBindingActions.world(tool, player, InteractionHand.MAIN_HAND), "weather bind");
        balance = EmcService.balance(server, OWNER);
        run(server, player, tool, 20);
        check(data.getRainTime() == 940 && data.getThunderTime() == 940, "weather timers advance");
        check(level.getGameTime() == gameTime, "weather must not advance game time");
        spent(server, balance, 4000);

        FurnaceBlockEntity second = furnace(level, POS.east());
        tool = tool(player, AccelerationSettings.Kind.AREA, false);
        new AccelerationSettings(AccelerationSettings.Kind.AREA, 4, 3, 1, 1, 0, 0, 0, false, false).write(tool.getOrCreateTag());
        EmcAccelerationService.bind(tool, player, level, POS, InteractionHand.MAIN_HAND);
        balance = EmcService.balance(server, OWNER);
        int before = first.saveWithoutMetadata().getShort("CookTime");
        run(server, player, tool, 20);
        check(first.saveWithoutMetadata().getShort("CookTime") >= before + 59, "area first furnace");
        check(second.saveWithoutMetadata().getShort("CookTime") >= 59, "area second furnace");
        spent(server, balance, 8000);
        tool.getOrCreateTag().putLong("RunDeadline", server.getTickCount());
        run(server, player, tool, 1);
        check(AccelerationBindings.view(tool).stream().noneMatch(AccelerationBindings::enabled), "deadline stops area");
        spent(server, balance, 8000);
        System.out.println("ACCELERATION_SMOKE: furnace, area, item, mob, daytime, weather, billing, gamerules, expiry PASS");
    }

    private static ItemStack tool(ServerPlayer player, AccelerationSettings.Kind kind, boolean noAi) {
        ItemStack previous = player.getMainHandItem();
        if (previous.getItem() == ModItems.EMC_ACCELERATOR.get()) AccelerationBindingActions.pauseAll(previous, player);
        var tool = new ItemStack(ModItems.EMC_ACCELERATOR.get()); player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        new AccelerationSettings(kind, 4, 3, 3, 3, 0, 0, 0, false, noAi).write(tool.getOrCreateTag());
        return tool;
    }

    private static FurnaceBlockEntity furnace(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        var furnace = (FurnaceBlockEntity) level.getBlockEntity(pos);
        check(furnace != null, "furnace block entity");
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 32)); furnace.setItem(1, new ItemStack(Items.COAL, 64));
        return furnace;
    }

    private static void run(MinecraftServer server, ServerPlayer player, ItemStack tool, int ticks) throws Exception {
        for (int i = 0; i < ticks; i++) {
            Object budget = budgetConstructor.newInstance(64, 4096, 2000L, (LongSupplier) () -> 0L);
            process.invoke(null, server, player, tool, budget);
        }
    }
    private static void spent(MinecraftServer server, BigInteger before, int expected) {
        BigInteger actual = before.subtract(EmcService.balance(server, OWNER));
        check(actual.equals(BigInteger.valueOf(expected)), "EMC expected=" + expected + " actual=" + actual);
    }
}
