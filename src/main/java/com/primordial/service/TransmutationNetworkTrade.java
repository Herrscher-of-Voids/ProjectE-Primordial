/**
 * 【文件职责】将 AE2、Refined Storage 的物品与流体请求接入放置者的统一 EMC 账户。
 * 【变更记录 #1】2026-10-11 23:44:54（第 1 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；本文件为网络交易建立独立服务入口。
 * - 新增：物品/流体目录、购买知识门禁、真实提取扣款、插入回收入账及每 tick 数量上限。
 * - 修改：只有网络执行阶段才调用 EmcService；模拟与枚举只读，插入按真实接受量计价。
 * - 用途：AE2 和 RS 适配器共用相同知识、价格、NBT、余额与线程规则；气体因两种网络无共同库存类型而不在此交易。
 */
/**
 * 【变更记录 #2】2026-10-13 19:40:05（第 2 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：AE2/RS 流体目录、模拟及执行都检查 SUPPLY/SELL 规则，实际提取走 supplyFluid；禁止请求不扣款或接受输入。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #3】2026-10-13 20:14:16（第 3 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
package com.primordial.service;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.compat.ProjectECompat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransmutationNetworkTrade {
    private static final int MAX_ITEMS_PER_TICK = 4096;
    private static final int CATALOG_REFRESH_TICKS = 20;
    private static final int MAX_FLUID_MB_PER_TICK = 1_000_000;
    private static final java.util.Map<UUID, Catalog> CATALOGS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, FluidCatalog> FLUID_CATALOGS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Long> FLUID_CATALOG_TICKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Budget> BUDGETS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, FluidBudget> FLUID_BUDGETS = new java.util.concurrent.ConcurrentHashMap<>();

    private TransmutationNetworkTrade() { }

    public record Catalog(long revision, List<ItemStack> stacks) { }
    public record FluidCatalog(long revision, List<FluidStack> stacks) { }
    private record Budget(long tick, int used) { }
    private record FluidBudget(long tick, int used) { }

    /** 目录只保留可安全生成的已学物品；余额/价格变化时立刻重算，其它情况最多缓存一秒。 */
    public static Catalog catalog(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return new Catalog(-1, List.of());
        long tick = server.overworld().getGameTime();
        long revision = com.primordial.value.ValueManager.priceRevision();
        Catalog current = CATALOGS.get(owner);
        if (current != null && current.revision == revision && tick - currentTick(owner) < CATALOG_REFRESH_TICKS)
            return current;
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack known : ProjectECompat.knownItems(server, owner)) {
            if (known.isEmpty() || !EmcService.hasKnowledge(server, owner, known)) continue;
            ItemStack sample = known.copyWithCount(1);
            if (!TransmutationNbtPolicy.canGenerate(sample) || EmcService.itemPrice(sample, server).isZero()) continue;
            stacks.add(sample);
        }
        Catalog next = new Catalog(revision, List.copyOf(stacks));
        CATALOGS.put(owner, next);
        CATALOG_TICKS.put(owner, tick);
        return next;
    }

    private static final java.util.Map<UUID, Long> CATALOG_TICKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static long currentTick(UUID owner) { return CATALOG_TICKS.getOrDefault(owner, Long.MIN_VALUE / 2); }

    public static FluidCatalog fluidCatalog(MinecraftServer server, UUID owner) {
        if (server == null || owner == null) return new FluidCatalog(-1, List.of());
        long tick = server.overworld().getGameTime();
        long revision = com.primordial.value.ValueManager.priceRevision();
        FluidCatalog cached = FLUID_CATALOGS.get(owner);
        if (cached != null && cached.revision == revision
                && tick - FLUID_CATALOG_TICKS.getOrDefault(owner, Long.MIN_VALUE / 2) < CATALOG_REFRESH_TICKS)
            return cached;
        boolean all = !com.primordial.config.PrimordialKnowledgeConfig.required()
                || ResourceKnowledge.allows(server, owner, ResourceKnowledge.Kind.FLUID);
        var candidates = all ? BuiltInRegistries.FLUID.keySet()
                : com.primordial.data.MediaKnowledgeSavedData.get(server).knownIds(owner, false);
        List<FluidStack> stacks = new ArrayList<>();
        for (ResourceLocation id : candidates) {
            if (!BuiltInRegistries.FLUID.containsKey(id)) continue;
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            if (!fluid.defaultFluidState().isSource() || !com.primordial.value.ValueManager.canBuyFluid(fluid)
                    || !ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.FLUID, id)
                    || EmcService.fluidPrice(fluid).isZero()) continue;
            stacks.add(new FluidStack(fluid, 1));
        }
        FluidCatalog result = new FluidCatalog(revision, List.copyOf(stacks));
        FLUID_CATALOGS.put(owner, result);
        FLUID_CATALOG_TICKS.put(owner, tick);
        return result;
    }

    public static long fluidAffordable(MinecraftServer server, UUID owner, Fluid fluid, long requested) {
        ResourceLocation id = fluid == null ? null : BuiltInRegistries.FLUID.getKey(fluid);
        if (server == null || owner == null || requested <= 0 || id == null || !fluid.defaultFluidState().isSource()
                || !com.primordial.value.ValueManager.canBuyFluid(fluid)
                || !ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.FLUID, id)) return 0;
        EmcAmount available = EmcAmount.of(EmcService.balance(server, owner))
                .add(com.primordial.data.EmcConversionSavedData.get(server).get(owner));
        return available.affordableUnits(EmcService.fluidPrice(fluid), Math.min(requested, remainingFluidBudget(server, owner)));
    }

    public static long extractFluid(MinecraftServer server, UUID owner, Fluid fluid, long requested, boolean execute) {
        long count = fluidAffordable(server, owner, fluid, requested);
        if (!execute || count <= 0) return count;
        long moved = EmcService.purchaseFluid(server, owner, fluid, count, units -> units);
        if (moved > 0) { recordFluidBudget(server, owner, (int) moved); invalidate(owner); }
        return moved;
    }

    public static long insertFluid(MinecraftServer server, UUID owner, FluidStack input, long requested, boolean execute) {
        if (server == null || owner == null || input == null || input.isEmpty() || requested <= 0) return 0;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(input.getFluid());
        if (id == null || !com.primordial.value.ValueManager.canSellFluid(input.getFluid())
                || EmcService.fluidPrice(input.getFluid()).isZero()) return 0;
        long limit = Math.min(Math.min(requested, input.getAmount()), remainingFluidBudget(server, owner));
        if (limit <= 0) return 0;
        if (!execute) return limit;
        long moved = EmcService.reclaimFluid(server, owner, input.getFluid(), limit, units -> units);
        if (moved > 0) { recordFluidBudget(server, owner, (int) moved); invalidate(owner); }
        return moved;
    }

    public static boolean canInsertFluid(MinecraftServer server, UUID owner, FluidStack input) {
        if (server == null || owner == null || input == null || input.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(input.getFluid());
        return id != null && com.primordial.value.ValueManager.canSellFluid(input.getFluid())
                && !EmcService.fluidPrice(input.getFluid()).isZero();
    }

    public static long affordable(MinecraftServer server, UUID owner, ItemStack sample, long requested) {
        if (requested <= 0 || !authorized(server, owner, sample)) return 0;
        EmcAmount price = EmcService.itemPrice(sample, server);
        EmcAmount available = EmcAmount.of(EmcService.balance(server, owner))
                .add(com.primordial.data.EmcConversionSavedData.get(server).get(owner));
        return available.affordableUnits(price, requested);
    }

    /** 模拟只返回可买数量；执行时从放置者账户扣款，交付数由同一结算事务确认。 */
    public static long extract(MinecraftServer server, UUID owner, ItemStack sample, long requested, boolean execute) {
        long count = affordable(server, owner, sample, Math.min(requested, remainingBudget(server, owner)));
        if (!execute || count <= 0) return count;
        long moved = EmcService.purchaseItem(server, owner, sample, count, units -> units);
        if (moved > 0) {
            recordBudget(server, owner, (int) moved);
            invalidate(owner);
        }
        return moved;
    }

    /** 插入只在执行模式回收物品，回收价按 ProjectE 实际售价，知识按现有服务设置学习。 */
    public static long insert(MinecraftServer server, UUID owner, ItemStack input, long requested, boolean execute) {
        if (server == null || owner == null || input == null || input.isEmpty() || requested <= 0) return 0;
        long limit = Math.min(requested, Math.min(input.getCount(), remainingBudget(server, owner)));
        if (limit <= 0) return 0;
        EmcAmount price = EmcService.itemRecoveryPrice(input, server);
        if (price.isZero()) return 0;
        if (!execute) return limit;
        long moved = EmcService.reclaim(server, owner, price, limit, units -> units);
        if (moved > 0) {
            recordBudget(server, owner, (int) moved);
            if (PrimordialServerConfig.autoLearnReclaimedItems()) EmcService.learn(server, owner, input.copyWithCount((int) moved));
            invalidate(owner);
        }
        return moved;
    }

    public static boolean canInsert(MinecraftServer server, UUID owner, ItemStack input) {
        return server != null && owner != null && input != null && !input.isEmpty()
                && !EmcService.itemRecoveryPrice(input, server).isZero();
    }

    private static boolean authorized(MinecraftServer server, UUID owner, ItemStack sample) {
        return server != null && owner != null && sample != null && !sample.isEmpty()
                && EmcService.hasKnowledge(server, owner, sample) && TransmutationNbtPolicy.canGenerate(sample);
    }

    private static synchronized int remainingBudget(MinecraftServer server, UUID owner) {
        Budget budget = BUDGETS.get(owner);
        long tick = server.overworld().getGameTime();
        return budget == null || budget.tick != tick ? MAX_ITEMS_PER_TICK : Math.max(0, MAX_ITEMS_PER_TICK - budget.used);
    }

    private static synchronized void recordBudget(MinecraftServer server, UUID owner, int moved) {
        long tick = server.overworld().getGameTime();
        Budget previous = BUDGETS.get(owner);
        int used = previous == null || previous.tick != tick ? 0 : previous.used;
        BUDGETS.put(owner, new Budget(tick, Math.min(MAX_ITEMS_PER_TICK, used + moved)));
    }

    private static synchronized int remainingFluidBudget(MinecraftServer server, UUID owner) {
        FluidBudget budget = FLUID_BUDGETS.get(owner);
        long tick = server.overworld().getGameTime();
        return budget == null || budget.tick != tick ? MAX_FLUID_MB_PER_TICK
                : Math.max(0, MAX_FLUID_MB_PER_TICK - budget.used);
    }

    private static synchronized void recordFluidBudget(MinecraftServer server, UUID owner, int moved) {
        long tick = server.overworld().getGameTime();
        FluidBudget previous = FLUID_BUDGETS.get(owner);
        int used = previous == null || previous.tick != tick ? 0 : previous.used;
        FLUID_BUDGETS.put(owner, new FluidBudget(tick, Math.min(MAX_FLUID_MB_PER_TICK, used + moved)));
    }

    public static void invalidate(UUID owner) {
        if (owner != null) {
            CATALOGS.remove(owner); CATALOG_TICKS.remove(owner);
            FLUID_CATALOGS.remove(owner); FLUID_CATALOG_TICKS.remove(owner);
        }
    }
}
