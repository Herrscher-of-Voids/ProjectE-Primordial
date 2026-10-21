/**
 * 变更记录 #3（2026-09-13）：统一通过 EmcService 门面访问余额，并完成 ProjectE/Curios API 审计。
 * - 新增：能量换算配置键 energy_to_emc_rate 与 emc_to_energy_rate。
 * - 修改：业务层不再直接调用 ProjectECompat，保证后续适配器共享同一 EMC 结算规则。
 */
/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
/**
 * 【变更记录 #5】2026-09-14 13:50:00（第 5 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：gas_values 配置表及 Mekanism 气体按 mB EMC 查询；流体查询确保先加载持久化表。
 * - 修改：水/岩浆默认 1000 EMC/1000mB 的表值在首次访问黑洞时也能正确生效。
 * - 用途：物品、流体、气体和 FE 结算的统一价值来源，未知介质返回零并等待管理员定价。
 */
/**
 * 【变更记录 #6】2026-09-15 12:04:51（第 6 次备注 · 1.0.0-configurator-fixes）
 * - 删除：气体值表以 double 解析后取整的存储方式。
 * - 新增：BigInteger 气体价格表、严格非负整数读取和 setGasValue 热更新入口。
 * - 修改：gas_values 明确定义为 EMC/mB，写盘不损失大整数精度，未知气体返回零。
 * - 用途：本模组管理气体定价，换得的 EMC 交给 ProjectE 余额；不需要修改 ProjectE 的物品估值接口。
 */
/**
 * 【变更记录 #11】2026-09-16（流体与气体默认价格）
 * - 删除：未知流体/气体查询返回零、导致默认介质无法进入 EMC 结算的路径。
 * - 新增：所有未被人工覆盖的流体按 1000 EMC/1000mB（即 1 EMC/mB）计价，所有未被人工覆盖的气体按 1 EMC/mB 计价。
 * - 修改：显式写入的零值仍表示管理员主动禁用该介质；流体价格继续以每桶精确分数保存，气体价格继续以每 mB BigInteger 保存。
 * - 用途：让新增模组的流体和气体无需逐项初始化即可安全使用，同时保留配置器修改和零价禁用能力。
 */
/**
 * 【变更记录 #7】2026-09-15 14:10:10（第 7 次备注 · 1.0.0-emc-service）
 * - 删除：double 价格表、PM 缩放种子、10 倍购买成本、反射 EMC 导入和运行中逐件估价写盘。
 * - 新增：ValueTable 精确持久化、原子快照、明确人工覆盖、未定价拒绝和客户端只读快照。
 * - 修改：买卖共享价格；优先级为人工覆盖、ProjectE、兼容旧表、基础配方估价，旧表不再覆盖 ProjectE。
 * - 用途：所有业务由 EmcService 访问本价格源；基础配方在启动时批量计算，不在管道回调中扫描配方。
 */
/**
 * 【变更记录 #8】2026-09-15 14:50:11（第 8 次备注 · 1.0.0-emc-service）
 * - 删除：旧自动估价/稀有度兜底进入交易的路径、double 编辑和按 FE/每 mB 取整接口。
 * - 新增：流体分数快照、统一加载门禁、客户端人工覆盖快照与写盘失败回滚。
 * - 修改：物品优先人工覆盖（含禁用零价），再读 ProjectE；气体整数，流体按桶报价再精确除以 1000。
 * - 用途：EmcService 的唯一价格来源；旧估价器暂存源码留待第 7 阶段替换，不在启动或回收时调用。
 */
/**
 * 【变更记录 #9】2026-09-15 15:40:11（第 9 次备注 · 1.0.0-emc-service）
 * - 删除：加载失败后仍允许用旧内存快照覆盖损坏文件的路径。
 * - 新增：独立写入门禁，只有成功加载或创建价表后才允许编辑持久化。
 * - 修改：加载失败保留当前快照和原文件；修复后执行 reload 恢复写入。
 * - 用途：防止管理员编辑单项价格时丢失尚未修复的其它配置。
 */
/**
 * 【变更记录 #10】2026-09-15 15:02:00（第 10 次备注 · 1.0.0-stage7-recipe-emc）
 * - 删除：物品查询只返回人工覆盖或 ProjectE 原生值、无法利用已注册配方的路径。
 * - 新增：服务端配方 EMC 只读快照及发布入口；人工零价仍作为明确禁用标记保留。
 * - 修改：物品价格优先级改为客户端人工覆盖、服务端人工覆盖、ProjectE 原生值、配方快照，查询过程不扫描配方。
 * - 用途：把启动阶段的自动配方计算接入统一 EmcService，同时保证管道 tick 内没有配方遍历和文件写入。
 */
/**
 * 【变更记录 #12】2026-09-16 01:24:55（第 12 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：无；保持人工覆盖优先于 ProjectE、统一服务实时查询和原子持久化规则。
 * - 新增：客户端判断样本是否存在人工物品价格的只读入口，包含显式零价。
 * - 修改：Tooltip 可区分“未定价”与“管理员设为零而禁用”，无需暴露可修改的价格表。
 * - 用途：价格编辑的零值结果可见，避免客户端只显示 ProjectE 的另一套非零报价。
 */
/**
 * 【变更记录 #13】2026-09-16 01:35:10（第 13 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：无。
 * - 新增：只读价格修订号，人工价/配方价发布或清理时原子递增。
 * - 修改：打开的价格菜单仅比较修订号，其他管理员或命令改价后也会返回最新报价。
 * - 用途：避免每 tick 调用 ProjectE 重新估值或广播价格，同时保证多个编辑页不长期显示旧价。
 */
/**
 * 【变更记录 #14】2026-09-16 01:52:17（第 14 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：独立物品覆盖、客户端物品价缓存和交易时的配方兜底，不再先于 ProjectE 返回另一套价格。
 * - 新增：物品编辑委托 ProjectEItemPrices；物品查询直接走 ProjectE API，人工快照也取自其原生文件状态。
 * - 修改：本价表只管理扩展的流体/气体；物品及其 NBT 定价由 ProjectE 唯一决定，重算事件使菜单报价失效。
 * - 用途：配置器和黑洞与转化桌共用物品价格，保留精确介质换算与 BigInteger 余额。
 */
/** 【变更记录 #15】2026-10-08 03:32:46（1.2.0-media-price-modes）
 * 删除：无。新增：继承模式的默认价格语义和方向状态查询。修改：显式模式优先于旧数值表。用途：价格编辑、交易和黑洞共享同一价格状态。 */
/**
 * 【变更记录 #12】2026-10-11 04:26:53（第 12 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：价格模式写盘失败后静默回滚的路径。
 * - 新增：记录流体/气体模式保存失败的资源类型和注册名。
 * - 修改：继续恢复内存旧模式并返回失败，不发布错误修订号。
 * - 用途：便于定位管理员定价持久化失败，保持内存、磁盘和客户端一致。
 */
package com.primordial.value;

import com.primordial.PrimordialMod;
import com.primordial.compat.ProjectECompat;
import com.primordial.compat.ProjectEItemPrices;
import com.primordial.service.EmcAmount;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fml.loading.FMLPaths;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** 变更在服务端线程提交，查询只读不可变快照；客户端使用服务器下发数据，不读取本地价格文件。 */
public final class ValueManager {
    private static final java.util.concurrent.atomic.AtomicLong PRICE_REVISION = new java.util.concurrent.atomic.AtomicLong();
    private static ValueTable table = new ValueTable();
    private static volatile Map<String, BigInteger> gases = Map.of();
    private static volatile Map<String, EmcAmount> fluids = Map.of();
    private static volatile Map<String, PriceMode> gasModes = Map.of();
    private static volatile Map<String, PriceMode> fluidModes = Map.of();
    private static volatile boolean loaded;
    private static boolean writable;
    private ValueManager() {}
    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("primordial_values.json"); }

    public static void loadIfNeeded() { if (!loaded) reload(); }

    public static synchronized boolean reload() {
        try {
            ValueTable next = ValueTable.read(file());
            if (!java.nio.file.Files.exists(file())) next.write(file());
            table = next;
            publish();
            loaded = true;
            writable = true;
            return true;
        } catch (Exception failure) {
            writable = false;
            PrimordialMod.LOGGER.error("[Primordial] Cannot load EMC values; retaining current values and original file", failure);
            if (!loaded) { publish(); loaded = true; }
            return false;
        }
    }

    private static void publish() {
        fluids = Map.copyOf(table.fluids);
        gases = Map.copyOf(table.gases);
        fluidModes = Map.copyOf(table.fluidModes);
        gasModes = Map.copyOf(table.gasModes);
        PRICE_REVISION.incrementAndGet();
    }

    public static long priceRevision() { return PRICE_REVISION.get(); }
    public static void invalidatePrices() { PRICE_REVISION.incrementAndGet(); }

    public static BigInteger itemValueEmc(ItemStack stack, MinecraftServer server) {
        if (stack == null || stack.isEmpty()) return BigInteger.ZERO;
        return ProjectECompat.itemEmc(stack);
    }

    public static boolean setItemValue(MinecraftServer server, ResourceLocation id, BigInteger value) {
        return ProjectEItemPrices.setPrice(server, id, value);
    }

    /** 未覆盖流体的默认单价：1000 EMC/1000mB，内部按每桶保存以兼容现有价表格式。 */
    public static EmcAmount fluidValuePerBucket(Fluid fluid) {
        loadIfNeeded();
        if (fluid == null) return EmcAmount.ZERO;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        return id == null || com.primordial.config.PrimordialServerConfig.fluidBlocked(fluid)
                || fluidMode(id) == PriceMode.DISABLED ? EmcAmount.ZERO
                : fluidMode(id) == PriceMode.INHERIT_NATIVE ? EmcAmount.of(BigInteger.valueOf(1000))
                : fluids.getOrDefault(id.toString(), EmcAmount.of(BigInteger.valueOf(1000)));
    }

    /** 每桶价格在此保留分母；例如 1 EMC/桶得到 1/1000 EMC/mB。 */
    public static EmcAmount fluidPricePerMB(Fluid fluid) {
        return fluidValuePerBucket(fluid).divide(1000);
    }

    /** 未覆盖气体的默认单价为 1 EMC/mB；显式零值仍由 gas_values 保留并表示禁用。 */
    public static BigInteger gasValueEmcPerMB(ResourceLocation gas) {
        loadIfNeeded();
        return gas == null || com.primordial.config.PrimordialServerConfig.gasBlocked(gas)
                || gasMode(gas) == PriceMode.DISABLED
                ? BigInteger.ZERO : gasMode(gas) == PriceMode.INHERIT_NATIVE ? BigInteger.ONE
                : gases.getOrDefault(gas.toString(), BigInteger.ONE);
    }

    public static PriceMode fluidMode(ResourceLocation id) { loadIfNeeded(); return id == null ? PriceMode.DISABLED : fluidModes.getOrDefault(id.toString(), PriceMode.INHERIT_NATIVE); }
    public static PriceMode gasMode(ResourceLocation id) { loadIfNeeded(); return id == null ? PriceMode.DISABLED : gasModes.getOrDefault(id.toString(), PriceMode.INHERIT_NATIVE); }
    public static boolean canBuyFluid(Fluid fluid) { ResourceLocation id = fluid == null ? null : BuiltInRegistries.FLUID.getKey(fluid); return fluidMode(id).allowsBuy(); }
    public static boolean canSellFluid(Fluid fluid) { ResourceLocation id = fluid == null ? null : BuiltInRegistries.FLUID.getKey(fluid); return fluidMode(id).allowsSell(); }
    public static boolean canBuyGas(ResourceLocation id) { return gasMode(id).allowsBuy(); }
    public static boolean canSellGas(ResourceLocation id) { return gasMode(id).allowsSell(); }

    public static synchronized boolean setGasValue(ResourceLocation id, BigInteger value) { return setValue("gas", id, value); }

    public static synchronized boolean setFluidMode(ResourceLocation id, PriceMode mode) { return setMode(true, id, mode); }
    public static synchronized boolean setGasMode(ResourceLocation id, PriceMode mode) { return setMode(false, id, mode); }
    private static boolean setMode(boolean fluid, ResourceLocation id, PriceMode mode) {
        if (id == null || mode == null) return false;
        loadIfNeeded();
        if (!writable) return false;
        Map<String, PriceMode> target = fluid ? table.fluidModes : table.gasModes;
        PriceMode previous = target.put(id.toString(), mode);
        try { table.write(file()); }
        catch (Exception failure) {
            if (previous == null) target.remove(id.toString()); else target.put(id.toString(), previous);
            PrimordialMod.LOGGER.error("[Primordial] Cannot save {} price mode for {}", fluid ? "fluid" : "gas", id, failure);
            return false;
        }
        publish();
        return true;
    }

    public static synchronized boolean setValue(String type, ResourceLocation id, BigInteger value) {
        if (id == null || value == null || value.signum() < 0 || value.toString().length() > ValueTable.MAX_DIGITS) return false;
        if ("fluid".equals(type)) return setFluidValue(id, EmcAmount.of(value));
        if ("item".equals(type))
            return setItemValue(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer(), id, value);
        loadIfNeeded();
        if (!writable) return false;
        Map<String, BigInteger> target = switch (type) {
            case "gas" -> table.gases;
            default -> null;
        };
        if (target == null) return false;
        BigInteger previous = target.put(id.toString(), value);
        PriceMode previousMode = table.gasModes.put(id.toString(), value.signum() == 0 ? PriceMode.DISABLED : PriceMode.CUSTOM_VALUE);
        try { table.write(file()); }
        catch (Exception failure) {
            if (previous == null) target.remove(id.toString()); else target.put(id.toString(), previous);
            if (previousMode == null) table.gasModes.remove(id.toString()); else table.gasModes.put(id.toString(), previousMode);
            PrimordialMod.LOGGER.error("[Primordial] Cannot save EMC price", failure);
            return false;
        }
        publish();
        return true;
    }

    public static synchronized boolean setFluidValue(ResourceLocation id, EmcAmount value) {
        if (id == null || value == null) return false;
        try { EmcAmount.parse(value.toString()); } catch (RuntimeException invalid) { return false; }
        loadIfNeeded();
        if (!writable) return false;
        EmcAmount previous = table.fluids.put(id.toString(), value);
        PriceMode previousMode = table.fluidModes.put(id.toString(), value.isZero() ? PriceMode.DISABLED : PriceMode.CUSTOM_VALUE);
        try { table.write(file()); }
        catch (Exception failure) {
            if (previous == null) table.fluids.remove(id.toString()); else table.fluids.put(id.toString(), previous);
            if (previousMode == null) table.fluidModes.remove(id.toString()); else table.fluidModes.put(id.toString(), previousMode);
            PrimordialMod.LOGGER.error("[Primordial] Cannot save fluid EMC price", failure);
            return false;
        }
        publish();
        return true;
    }

    public static Map<String, BigInteger> itemSnapshot() {
        Map<String, BigInteger> result = new HashMap<>();
        for (var entry : moze_intel.projecte.emc.EMCMappingHandler.createPacketData())
            if (entry.nbt() == null) result.put(BuiltInRegistries.ITEM.getKey(entry.item()).toString(), BigInteger.valueOf(entry.emc()));
        return Map.copyOf(result);
    }

    /** 旧估价器的兼容快照；新结算路径使用 itemSnapshot 的 BigInteger 版本。 */
    static Map<String, Double> snapshotItemValues() {
        Map<String, Double> result = new HashMap<>();
        itemSnapshot().forEach((id, value) -> result.put(id, value.doubleValue()));
        return result;
    }

    public static Map<String, BigInteger> overrideSnapshot() { return ProjectEItemPrices.customPrices(); }
    public static Map<String, EmcAmount> fluidSnapshot() { return fluids; }
    public static Map<String, BigInteger> gasSnapshot() { return gases; }

    public static double peekItemValue(String id) {
        BigInteger value = itemSnapshot().get(id);
        return value == null ? -1 : value.doubleValue();
    }

    public static synchronized void clear() {
        table = new ValueTable(); loaded = false; writable = false;
        gases = Map.of();
        fluids = Map.of();
        fluidModes = Map.of();
        gasModes = Map.of();
        ProjectEItemPrices.clear();
        PRICE_REVISION.incrementAndGet();
    }
}
