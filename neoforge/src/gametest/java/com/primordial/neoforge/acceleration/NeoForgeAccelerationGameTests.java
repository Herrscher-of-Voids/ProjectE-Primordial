package com.primordial.neoforge.acceleration;

import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.test.NeoForgeTestPlayers;
import com.primordial.service.EmcAmount;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeAccelerationGameTests {
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;
    private NeoForgeAccelerationGameTests() {}

    @GameTest(template="capability_proxy_empty", templateNamespace="primordial")
    public static void componentMutationIsScopedAndSurvivesSerialization(GameTestHelper helper) {
        ItemStack tool = tool();
        try (var edit = AccelerationData.edit(tool)) {
            AccelerationData.get(tool).putString("OtherMod", "preserved");
            AccelerationBindings.editable(tool);
            try (var nested = AccelerationData.edit(tool)) {
                AccelerationData.get(tool).putInt(EmcAcceleratorItem.MULTIPLIER_KEY, 8);
            }
        }
        var original = tool.get(DataComponents.CUSTOM_DATA);
        AccelerationData.get(tool).putInt(EmcAcceleratorItem.MULTIPLIER_KEY, 1024);
        helper.assertTrue(EmcAccelerationService.multiplier(tool) == 8 && original.equals(tool.get(DataComponents.CUSTOM_DATA)),
                "Read snapshots must not mutate ItemStack component values");
        try (var edit = AccelerationData.edit(tool)) { AccelerationData.get(tool).putInt(EmcAcceleratorItem.MULTIPLIER_KEY, 16); }
        helper.assertTrue(original.copyTag().getInt(EmcAcceleratorItem.MULTIPLIER_KEY) == 8,
                "Editing components must preserve old immutable component instances");
        var restored = ItemStack.parse(helper.getLevel().registryAccess(), tool.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(EmcAccelerationService.multiplier(restored) == 16
                && AccelerationData.get(restored).getString("OtherMod").equals("preserved"), "Selection and unknown fields must survive the real item codec");
        helper.succeed();
    }

    @GameTest(template="capability_proxy_empty", templateNamespace="primordial")
    public static void entityWorkChargesExactlyAndStopsWithoutFunds(GameTestHelper helper) {
        ServerPlayer player = player(helper); Pig pig = pig(helper);
        try {
            var tool = player.getMainHandItem(); bindEntity(tool, player, pig, 2);
            helper.assertTrue(AccelerationBindings.view(tool).size() == 1 && AccelerationBindings.enabled(AccelerationBindings.view(tool).get(0)),
                    "Low-rate entity binding must persist and start without a warning");
            var before = NeoForgeTestPlayers.balance(player); int ticks = pig.tickCount;
            EmcAccelerationService.process(player.server, player, tool, budget());
            helper.assertTrue(pig.tickCount == ticks + 1 && NeoForgeTestPlayers.balance(player).equals(before.subtract(EmcAccelerationService.costPerExtraTick(2))),
                    "One successful extra entity tick must charge exactly its rational EMC price");
            player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(BigInteger.ZERO);
            ticks = pig.tickCount;
            EmcAccelerationService.process(player.server, player, tool, budget());
            helper.assertTrue(pig.tickCount == ticks && !AccelerationBindings.enabled(AccelerationBindings.view(tool).get(0)),
                    "No funds must pause a binding without free entity work");
        } finally { pig.discard(); NeoForgeTestPlayers.logout(player); }
        helper.succeed();
    }

    @GameTest(template="capability_proxy_empty", templateNamespace="primordial")
    public static void failedWorkChargesAttemptOnlyAndRefundsUnattemptedTicks(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        try (var edit = AccelerationData.edit(player.getMainHandItem())) {
            var tool = player.getMainHandItem(); AccelerationRun.begin(tool, player.server);
            CompoundTag entry = new CompoundTag(); entry.putInt(EmcAcceleratorItem.MULTIPLIER_KEY, 8);
            var calls = new AtomicInteger(); var before = NeoForgeTestPlayers.balance(player);
            var work = new AccelerationWork(UUID.randomUUID(), helper.getLevel(), helper.absolutePos(BlockPos.ZERO), () -> true,
                    () -> { calls.incrementAndGet(); throw new IllegalStateException("test-work-failure"); }, -1);
            EmcAccelerationService.execute(player.server, player, tool, entry, budget(), work, 8);
            helper.assertTrue(calls.get() == 1 && NeoForgeTestPlayers.balance(player).equals(before.subtract(EmcAccelerationService.costPerExtraTick(8))),
                    "A throwing callback counts one attempted tick and refunds all other prepaid ticks");
            helper.assertTrue(!AccelerationBindings.enabled(entry), "A failed callback must pause its target");
        } finally { NeoForgeTestPlayers.logout(player); }
        helper.succeed();
    }

    @GameTest(template="capability_proxy_empty", templateNamespace="primordial")
    public static void confirmationBindsCurrentComponentsAndCannotReplay(GameTestHelper helper) throws Exception {
        ServerPlayer player = player(helper); Pig pig = pig(helper);
        try {
            var tool = player.getMainHandItem(); bindEntity(tool, player, pig, 256);
            UUID token = token(player);
            helper.assertTrue(AccelerationBindings.pending(AccelerationBindings.view(tool).get(0)), "High-rate work must wait for a warning confirmation");
            EmcAccelerationService.confirm(player, UUID.randomUUID(), true);
            helper.assertTrue(!AccelerationBindings.enabled(AccelerationBindings.view(tool).get(0)), "A forged token cannot start work");
            EmcAccelerationService.confirm(player, token, true);
            helper.assertTrue(AccelerationBindings.enabled(AccelerationBindings.view(tool).get(0)),
                    "A valid warning must rebind the current component tree after the original write scope closed");
            var before = AccelerationData.get(tool);
            EmcAccelerationService.confirm(player, token, true);
            helper.assertTrue(before.equals(AccelerationData.get(tool)), "A consumed confirmation token cannot replay");
            try (var edit = AccelerationData.edit(tool)) { AccelerationBindings.pauseAll(tool); }
            EmcAccelerationService.toggle(tool, player, HAND); token = token(player);
            try (var edit = AccelerationData.edit(tool)) { AccelerationBindings.view(tool).get(0).putBoolean("DisableEntityAi", true); }
            EmcAccelerationService.confirm(player, token, true);
            helper.assertTrue(!AccelerationBindings.enabled(AccelerationBindings.view(tool).get(0)), "A changed quote/configuration must invalidate pending authorization");
        } finally { pig.discard(); NeoForgeTestPlayers.logout(player); }
        helper.succeed();
    }

    @GameTest(template="capability_proxy_empty", templateNamespace="primordial")
    public static void expiredAndInvalidTargetsStopBeforeCharging(GameTestHelper helper) {
        ServerPlayer player = player(helper); Pig pig = pig(helper);
        try {
            var tool = player.getMainHandItem(); bindEntity(tool, player, pig, 2);
            var before = NeoForgeTestPlayers.balance(player); int ticks = pig.tickCount;
            try (var edit = AccelerationData.edit(tool)) { AccelerationData.get(tool).putLong("RunDeadline", player.server.getTickCount()); }
            EmcAccelerationService.process(player.server, player, tool, budget());
            helper.assertTrue(pig.tickCount == ticks && NeoForgeTestPlayers.balance(player).equals(before)
                    && !AccelerationBindings.enabled(AccelerationBindings.view(tool).get(0)), "Expired 600-tick runs must pause without work or charges");
            helper.assertTrue(!AccelerationTargets.entityValid(helper.getLevel(), player), "Players must never be acceleration targets");
            helper.assertTrue(AccelerationTargets.block(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))) == null,
                    "Air and other blocks without extra-tick work cannot be bound");
        } finally { pig.discard(); NeoForgeTestPlayers.logout(player); }
        helper.succeed();
    }

    @GameTest(template="capability_proxy_empty", templateNamespace="primordial")
    public static void budgetDeduplicatesAndCountsFailures(GameTestHelper helper) {
        var budget = new AccelerationBudget(2, 3, 100, () -> 0);
        helper.assertTrue(budget.claim("one") && !budget.claim("one") && budget.claim("two") && !budget.claim("three"),
                "Global budget must reject duplicate targets and excess target slots");
        AtomicInteger ticks = new AtomicInteger();
        var result = budget.run(20, () -> true, ticks::incrementAndGet);
        helper.assertTrue(ticks.get() == 3 && result.attempted() == 3 && !budget.available(), "Extra-tick budget must cap callbacks without backlog");
        var failing = new AccelerationBudget(1, 5, 100, () -> 0).run(5, () -> true, () -> { throw new IllegalStateException("expected"); });
        helper.assertTrue(failing.attempted() == 1 && failing.failure() != null, "Throwing work still consumes its attempted slot");
        helper.succeed();
    }

    @GameTest(template="capability_proxy_empty", templateNamespace="primordial")
    public static void entityAccelerationHonorsTickEventsAndStasis(GameTestHelper helper) {
        ServerPlayer player = player(helper); Pig pig = pig(helper);
        var pre = new AtomicInteger(); var post = new AtomicInteger();
        java.util.function.Consumer<net.neoforged.neoforge.event.tick.EntityTickEvent.Pre> cancel = event -> {
            if (event.getEntity() == pig) { pre.incrementAndGet(); event.setCanceled(true); }
        };
        java.util.function.Consumer<net.neoforged.neoforge.event.tick.EntityTickEvent.Post> after = event -> {
            if (event.getEntity() == pig) post.incrementAndGet();
        };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre.class, cancel);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.EntityTickEvent.Post.class, after);
        try {
            var tool = player.getMainHandItem(); bindEntity(tool, player, pig, 2);
            EmcAccelerationService.process(player.server, player, tool, budget());
            helper.assertTrue(pre.get() == 1 && post.get() == 0, "An extra tick must honor NeoForge Pre cancellation and skip the body/Post");
            CompoundTag freeze = new CompoundTag(); freeze.putLong("Until", helper.getLevel().getGameTime() + 200);
            freeze.putString("Dimension", helper.getLevel().dimension().location().toString());
            freeze.putDouble("X", pig.getX()); freeze.putDouble("Y", pig.getY()); freeze.putDouble("Z", pig.getZ());
            pig.getPersistentData().put("BlackholeStasis", freeze);
            var before = NeoForgeTestPlayers.balance(player); int ticks = pig.tickCount;
            EmcAccelerationService.process(player.server, player, tool, budget());
            helper.assertTrue(pig.tickCount == ticks && pre.get() == 1 && NeoForgeTestPlayers.balance(player).equals(before)
                    && !AccelerationBindings.enabled(AccelerationBindings.view(tool).get(0)), "A frozen target must stop before extra work, events and EMC charges");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(after);
            pig.discard(); NeoForgeTestPlayers.logout(player);
        }
        helper.succeed();
    }

    private static ItemStack tool() { return new ItemStack(NeoForgeModRegistries.EMC_ACCELERATOR.get()); }
    private static ServerPlayer player(GameTestHelper helper) {
        var player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5)));
        player.setItemInHand(HAND, tool()); return player;
    }
    private static Pig pig(GameTestHelper helper) {
        var pig = EntityType.PIG.create(helper.getLevel());
        pig.setNoAi(true); pig.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        helper.getLevel().addFreshEntity(pig); return pig;
    }
    private static void bindEntity(ItemStack tool, ServerPlayer player, Pig pig, int rate) {
        try (var edit = AccelerationData.edit(tool)) {
            var settings = new AccelerationSettings(AccelerationSettings.Kind.ENTITY, rate, 3, 3, 3, 0, 0, 0, true, false);
            settings.write(AccelerationData.get(tool));
            AccelerationBindingActions.entity(tool, player, pig, HAND);
        }
    }
    private static AccelerationBudget budget() { return new AccelerationBudget(8, 32, 1_000_000, () -> 0); }
    private static UUID token(ServerPlayer player) throws Exception {
        var field = AccelerationConfirmation.class.getDeclaredField("PENDING"); field.setAccessible(true);
        var pending = ((java.util.Map<?, ?>) field.get(null)).get(player.getUUID());
        var method = pending.getClass().getDeclaredMethod("token"); method.setAccessible(true);
        return (UUID) method.invoke(pending);
    }
}
