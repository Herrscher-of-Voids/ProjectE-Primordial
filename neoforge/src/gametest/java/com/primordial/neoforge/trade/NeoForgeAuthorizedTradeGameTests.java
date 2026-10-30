/**
 * 【文件职责】使用真实 ProjectE 凭证知识和账户验收能源购买、类别授权、遗忘优先与关闭知识后的安全边界。
 * 【变更记录 #1】2026-10-23 13:34:22（第 1 次备注 · 2.0.0-neoforge-knowledge-certificates）
 * - 删除：无。
 * - 新增：持有不等于已学、错误凭证、无实体凭证的已学授权、只读报价、取消/部分退款和离线登录结算。
 * - 修改：配置关闭用例仅在主线程临时注入会话快照并 finally 恢复，不冒充 COMMON 冷启动加载验收。
 * - 用途：证明知识配置不会绕过库存安全、显式遗忘或资源黑名单；不包含 FTB、原生网络节点或容器菜单。
 */
package com.primordial.neoforge.trade;

import static com.primordial.neoforge.trade.TradeTestPlayers.check;
import static com.primordial.neoforge.trade.TradeTestPlayers.knowledge;
import static com.primordial.neoforge.trade.TradeTestPlayers.login;
import static com.primordial.neoforge.trade.TradeTestPlayers.logout;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.function.LongUnaryOperator;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.registry.NeoForgeCertificates;
import com.primordial.service.EmcAmount;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeAuthorizedTradeGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final LongUnaryOperator FORBIDDEN_TRANSFER = units -> {
        throw new AssertionError("An unauthorized resource operation must never reach transfer");
    };

    private NeoForgeAuthorizedTradeGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void learnedEnergyCertificateAuthorizesActualPurchaseWithoutPhysicalPossession(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String ratio = NeoForgeServerConfig.FE_PER_EMC.get();
        List<? extends String> blacklist = List.copyOf(NeoForgeServerConfig.RESOURCE_BLACKLIST.get());
        try {
            check(NeoForgeKnowledgeConfig.required(), "Ordinary authorization regression requires the default knowledge snapshot");
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of());
            UUID owner = player.getUUID();
            knowledge(player).clearKnowledge();
            knowledge(player).setEmc(BigInteger.TEN);
            player.getInventory().setItem(0, certificate(Kind.ENERGY));
            check(NeoForgeResourceTrades.affordableEnergy(server, owner, 2000) == 0
                    && NeoForgeResourceTrades.purchaseEnergy(server, owner, 2000, FORBIDDEN_TRANSFER) == 0,
                    "Physically holding an unlearned energy certificate must not authorize FE generation");
            learn(player, Kind.FLUID);
            check(NeoForgeResourceTrades.purchaseEnergy(server, owner, 1, FORBIDDEN_TRANSFER) == 0,
                    "Learning a different resource certificate must not authorize energy");
            learn(player, Kind.ENERGY);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            CompoundTag before = ledger(server);
            check(NeoForgeResourceTrades.affordableEnergy(server, owner, 2000) == 2000
                    && knowledge(player).getEmc().equals(BigInteger.TEN) && ledger(server).equals(before),
                    "Learned energy knowledge must authorize a pure quote without holding a physical certificate");
            check(NeoForgeResourceTrades.purchaseEnergy(server, owner, 2000, units -> 0) == 0
                    && knowledge(player).getEmc().equals(BigInteger.TEN) && ledger(server).equals(before),
                    "A cancelled authorized purchase must refund its entire reservation without changing the account ledger");
            check(NeoForgeResourceTrades.purchaseEnergy(server, owner, 2000, units -> {
                check(units == 2000, "The authorized callback must receive the affordable FE quantity");
                return 1250;
            }) == 1250 && spendable(server, player).equals(new EmcAmount(BigInteger.valueOf(35), BigInteger.valueOf(4))),
                    "1250 actually delivered FE must cost exactly 5/4 EMC after refunding the unfilled tail");
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of("forge:fe"));
            check(NeoForgeResourceTrades.affordableEnergy(server, owner, 1) == 0
                    && NeoForgeResourceTrades.purchaseEnergy(server, owner, 1, FORBIDDEN_TRANSFER) == 0,
                    "A learned energy certificate must not bypass the physical FE blacklist");
        } finally {
            NeoForgeServerConfig.FE_PER_EMC.set(ratio);
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(blacklist);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void savedOfflineEnergyAuthorizationPurchasesAndMergesDebitOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer original = login(server);
        ServerPlayer restored = null;
        String ratio = NeoForgeServerConfig.FE_PER_EMC.get();
        try {
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            knowledge(original).setEmc(BigInteger.valueOf(5));
            learn(original, Kind.ENERGY);
            GameProfile profile = original.getGameProfile();
            UUID owner = original.getUUID();
            logout(server, original);
            check(NeoForgeResourceTrades.affordableEnergy(server, owner, 6000) == 5000,
                    "Offline simulation must use saved native certificate knowledge and the exact saved account");
            CompoundTag before = ledger(server);
            check(NeoForgeResourceTrades.affordableEnergy(server, owner, 6000) == 5000 && ledger(server).equals(before),
                    "Repeated offline quotes must not reserve or acknowledge any debit");
            check(NeoForgeResourceTrades.purchaseEnergy(server, owner, 1000, units -> 400) == 400
                    && ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(owner).getEmc().equals(BigInteger.valueOf(5)),
                    "An authorized offline purchase must reserve only the ledger without mutating ProjectE's read-only snapshot");
            restored = login(server, profile);
            EmcAmount expected = new EmcAmount(BigInteger.valueOf(23), BigInteger.valueOf(5));
            check(spendable(server, restored).equals(expected),
                    "Real login must merge the offline reservation and refund to exactly 5 minus 400/1000 EMC");
            NeoForgeEmcCredits.flush(restored);
            NeoForgeEmcCredits.flush(restored);
            check(spendable(server, restored).equals(expected)
                    && NeoForgeEmcSavedData.get(server).debit(owner).signum() == 0,
                    "The acknowledged offline energy debit must never replay during repeated native flushes");
        } finally {
            NeoForgeServerConfig.FE_PER_EMC.set(ratio);
            logout(server, restored);
            logout(server, original);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void fluidCategoryCertificateHonorsExplicitForgettingUntilActualReclamation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            knowledge(player).clearKnowledge();
            knowledge(player).setEmc(BigInteger.valueOf(10000));
            UUID owner = player.getUUID();
            FluidStack water = new FluidStack(Fluids.WATER, 1);
            player.getInventory().setItem(0, certificate(Kind.FLUID));
            check(NeoForgeResourceTrades.affordableFluid(server, owner, water, 10) == 0,
                    "An unlearned physical fluid certificate must not authorize a media purchase");
            learn(player, Kind.FLUID);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            check(NeoForgeResourceTrades.affordableFluid(server, owner, water, 10) == 10,
                    "A learned class certificate must authorize a registered fluid without per-type discovery");
            check(NeoForgeResourceKnowledge.forgetMedia(server, owner, Kind.FLUID, WATER),
                    "An authorized owner must be able to explicitly forget a registered fluid");
            check(NeoForgeResourceTrades.affordableFluid(server, owner, water, 10) == 0
                    && NeoForgeResourceTrades.purchaseFluid(server, owner, water, 10, FORBIDDEN_TRANSFER) == 0,
                    "Explicit forgetting must take priority over the still-learned fluid certificate");
            check(NeoForgeResourceTrades.affordableFluid(server, owner, new FluidStack(Fluids.LAVA, 1), 10) == 10,
                    "Forgetting one media identity must not erase class authorization for another registered fluid");
            check(NeoForgeResourceTrades.reclaimFluid(server, owner, water, 10, units -> 0) == 0
                    && NeoForgeResourceTrades.affordableFluid(server, owner, water, 10) == 0,
                    "A cancelled resource sale must not clear the explicit forgotten state");
            check(NeoForgeResourceTrades.reclaimFluid(server, owner, water, 10, units -> 1) == 1
                    && NeoForgeResourceTrades.affordableFluid(server, owner, water, 10) == 10,
                    "Actual positive reclamation must clear forgetting and permit the newly learned media again");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void knowledgeDisabledStillEnforcesSafetyForgettingAndResourceRestrictions(GameTestHelper helper) throws ReflectiveOperationException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        List<? extends String> fluidBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        List<? extends String> resources = List.copyOf(NeoForgeServerConfig.RESOURCE_BLACKLIST.get());
        boolean fluidPurchase = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
        try (KnowledgeSnapshot ignored = new KnowledgeSnapshot(false)) {
            check(!NeoForgeKnowledgeConfig.required(), "This test injects only the active session snapshot, not a COMMON loading event");
            UUID owner = player.getUUID();
            knowledge(player).clearKnowledge();
            knowledge(player).setEmc(BigInteger.TEN.pow(10));
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            check(!knowledge(player).hasKnowledge(ItemInfo.fromItem(Items.DIAMOND))
                    && NeoForgeResourceTrades.affordableItem(server, owner, diamond, 1) == 1
                    && NeoForgeResourceTrades.purchaseItem(server, owner, diamond, 1, units -> units) == 1,
                    "Disabling knowledge must authorize safe priced unknown items without teaching them implicitly");
            ItemStack unsafe = new ItemStack(Items.SHULKER_BOX);
            unsafe.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(diamond)));
            check(!NeoForgeTradeItemPolicy.allows(server, owner, unsafe)
                    && NeoForgeResourceTrades.purchaseItem(server, owner, unsafe, 1, FORBIDDEN_TRANSFER) == 0,
                    "Knowledge-free mode must not permit copying container payloads");
            FluidStack water = new FluidStack(Fluids.WATER, 1);
            check(NeoForgeResourceTrades.affordableFluid(server, owner, water, 1) == 1
                    && NeoForgeResourceTrades.affordableEnergy(server, owner, 1000) == 1000,
                    "Knowledge-free mode may authorize valid priced fluid and energy without certificates");
            check(NeoForgeResourceKnowledge.forgetMedia(server, owner, Kind.FLUID, WATER)
                    && NeoForgeResourceTrades.affordableFluid(server, owner, water, 1) == 0
                    && NeoForgeResourceTrades.purchaseFluid(server, owner, water, 1, FORBIDDEN_TRANSFER) == 0,
                    "An explicit forgotten identity must continue to override the disabled knowledge requirement");
            NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.FLUID, WATER);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:water"));
            check(NeoForgeResourceTrades.affordableFluid(server, owner, water, 1) == 0
                    && NeoForgeResourceTrades.purchaseFluid(server, owner, water, 1, FORBIDDEN_TRANSFER) == 0,
                    "Knowledge-free mode must still reject physically blacklisted fluid");
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(false);
            check(NeoForgeResourceTrades.affordableFluid(server, owner, water, 1) == 0,
                    "The independent purchase switch must remain effective when knowledge is disabled");
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of("forge:fe"));
            check(NeoForgeResourceTrades.affordableEnergy(server, owner, 1) == 0
                    && NeoForgeResourceTrades.purchaseEnergy(server, owner, 1, FORBIDDEN_TRANSFER) == 0,
                    "Disabling knowledge must not open physically blocked FE generation");
        } finally {
            NeoForgeServerConfig.FLUID_BLACKLIST.set(fluidBlacklist);
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(resources);
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(fluidPurchase);
            logout(server, player);
        }
        helper.succeed();
    }

    private static ItemStack certificate(Kind kind) {
        return new ItemStack(NeoForgeCertificates.CERTIFICATES.get(kind).get());
    }

    private static void learn(ServerPlayer player, Kind kind) {
        ItemStack certificate = certificate(kind);
        check(IEMCProxy.INSTANCE.getValue(certificate) > 0, "Registered certificates must already have real positive native EMC");
        knowledge(player).addKnowledge(certificate);
        check(knowledge(player).hasKnowledge(certificate), "The real ProjectE knowledge provider must learn the registered certificate");
    }

    private static CompoundTag ledger(MinecraftServer server) {
        return NeoForgeEmcSavedData.get(server).save(new CompoundTag(), server.registryAccess());
    }

    private static EmcAmount spendable(MinecraftServer server, ServerPlayer player) {
        var data = NeoForgeEmcSavedData.get(server);
        check(data.debit(player.getUUID()).signum() == 0, "Online settlement must acknowledge all reserved whole EMC");
        return EmcAmount.of(knowledge(player).getEmc()).add(data.pending(player.getUUID()));
    }

    /** 只用于同步测试注入已生效快照，配置 Loading/Reloading 行为由专门配置回归覆盖。 */
    private static final class KnowledgeSnapshot implements AutoCloseable {
        private final Field field;
        private final boolean before;

        private KnowledgeSnapshot(boolean required) throws ReflectiveOperationException {
            field = NeoForgeKnowledgeConfig.class.getDeclaredField("activeRequirement");
            field.setAccessible(true);
            before = field.getBoolean(null);
            field.setBoolean(null, required);
        }

        @Override
        public void close() throws IllegalAccessException {
            field.setBoolean(null, before);
        }
    }
}
