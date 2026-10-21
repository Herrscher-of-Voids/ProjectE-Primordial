/**
 * 变更记录 #1（2026-09-13）：建立统一 EMC 服务入口。
 * - 删除：业务类直接依赖 ProjectE capability 的分散调用方式。
 * - 新增：物品估值、玩家余额、离线增量、消费和能量换算的统一门面。
 * - 用途：后续机器适配器、黑洞饰品和自动配方计算只依赖本服务，避免重复实现溢出和同步规则。
 */
/**
 * 变更记录 #2（2026-09-14）：统一暴露 ProjectE 知识查询与回收学习入口。
 * - 新增：hasKnowledge、learn，供所有业务路径复用相同的知识规则。
 * - 修改：配置器、玩家背包和黑洞无需直接依赖 ProjectECompat。
 * - 目的：集中维护“只生成已学习物品”和“回收后自动学习”的兼容逻辑。
 */
/**
 * 【变更记录 #3】2026-09-15 14:48:20（第 3 次备注 · 1.0.0-emc-service）
 * - 删除：逐次取整的能源费用/回收接口和 int 比率接口。
 * - 新增：精确单价、购买/回收事务、物品知识门禁、FE 能力传输和完整清零。
 * - 修改：资源交易经 EmcSettlement；只有本服务整数余额接口调用 ProjectE，统一标记 HUD 同步。
 * - 用途：机器任务、玩家背包、手持充能和黑洞共用一份计费规则，模拟和未知价格不改变余额。
 */
/**
 * 【变更记录 #4】2026-09-16 01:59:37（第 4 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：回收与购买强行共用同一个数值、绕过 ProjectE 损耗规则的假定。
 * - 新增：新增物品回收报价入口，委托 ProjectE getSellValue；供货继续使用 getValue，余额和数量乘法保持 BigInteger。
 * - 修改：交易定价统一委托 ProjectE，扩展流体/气体定价和 BigInteger 玩家余额不受影响。
 * - 用途：让本模组回收物品与转化桌回收遵循同一套价值规则。
 */
/**
 * 【文件职责】统一物品与资源的EMC结算入口。
 * 【变更记录 #5】2026-09-16 19:41:50（第 5 次备注 · 1.0.0-review-ready）
 * - 删除：移除流体、FE及独立资源直接输出而未校验凭证的路径。
 * - 新增：purchaseFluid入口与各资源门禁。
 * - 修改：普通物品按知识配置校验，所有新检查发生在扣费与真实写入前。
 * - 用途：拒绝未学习资源时不消耗余额、不调用接收端；回收规则不受凭证限制。
 */
/**
 * 【变更记录 #6】2026-09-18 01:06:25（第 6 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：无。
 * - 新增：purchaseWithReceipt 入口，提供实际交付数量与该笔整数净扣款。
 * - 修改：带消费额度的加速业务也通过 EmcSettlement 使用精确单价和持久化找零。
 * - 用途：统计加速费用时排除机器回调中的其它 EMC 交易，普通资源购买接口保持原有语义。
 */
/**
 * 【变更记录 #7】2026-10-02 00:00:00（1.2.0-core-trading）
 * - 修改：学习入口对资源凭证执行类别归一化；液体/气体凭证学习后可访问对应类别的全部资源。
 * - 用途：保持普通物品的完整 NBT 知识，同时避免凭证显示 NBT 导致类别解锁失效。
 */
/**
 * 【变更记录 #8】2026-10-02 16:07:00（第 8 次备注 · 1.2.0-media-learning）
 * - 删除：流体供给只认类别凭证的门禁。
 * - 新增：流体回收成功后记录具体注册名，供给允许已学习该流体或已学习流体凭证。
 * - 修改：回收按实际抽取数量计价；零数量或无价资源不学习。
 * - 用途：流体按种类经历一次真实卖入后才能购买，同时保留凭证整类解锁。
 */
/**
 * 【变更记录 #9】2026-10-02 18:54:32（第 9 次备注 · 1.2.0-media-trade-switches）
 * - 删除：无。
 * - 新增：流体购买和回收在能力调用及结算前检查对应方向开关。
 * - 修改：拒绝时返回零，不调用传输、入账或学习；报价函数不受开关影响。
 * - 用途：转化桌与机器任务共享开关，不改变配方估值和管理员价格显示。
 */
/**
 * 【变更记录 #10】2026-10-02 19:15:08（第 10 次备注 · 1.2.0-item-supply-stages）
 * - 删除：hasKnowledge 方法说明中关闭知识开关即可无条件输出的过时描述。
 * - 新增：注明查询同时包含物品任务阶段门禁。
 * - 修改：仅更新注释，行为由 ResourceKnowledge 统一实现。
 * - 用途：明确机器、背包及 purchaseItem 均须通过阶段与知识检查。
 */
/**
 * 【变更记录 #11】2026-10-10 03:53:52（第 11 次备注 · 1.2.0-resource-system-one）
 * - 删除：气体容器和机器各自调用底层结算器、重复维护知识与模式门禁的入口缺口。
 * - 新增：purchaseGas/reclaimGas 统一服务，卖入只在真实抽取并提交后学习原气体 ID。
 * - 修改：气体沿用独立 EMC/mB 价格、分数找零和买卖开关；通用签名仅使用 ResourceLocation。
 * - 用途：与物品、流体和 FE 共用准确结算链，避免容器与机器交易规则不同。
 */
/**
 * 【变更记录 #12】2026-10-10 03:53:52（第 12 次备注 · 1.2.0-resource-system-one）
 * - 删除：独立资源服务绕过端口模拟、FE 报价绕过资源黑名单的差异。
 * - 新增：端口实际可接收/可抽取模拟结果裁剪，forge:fe 黑名单统一影响 FE/RF 报价。
 * - 修改：零价先返回，模拟数量只在已通过知识检查后进入预扣；机器、手持及转化桌共用报价。
 * - 用途：统一第 1 系统容量检查和禁用行为，未知容量或限流接口不因请求数直接扣满费用。
 */
/**
 * 【变更记录 #13】2026-10-13 19:40:05（第 13 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：购买与卖出在能力回调和 EMC 结算前检查独立操作规则，新增 supplyFluid/supplyGas 叠加自动补充门禁。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #14】2026-10-13 20:14:16（第 14 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
package com.primordial.service;

import java.math.BigInteger;
import java.util.UUID;

import com.primordial.compat.ProjectECompat;
import com.primordial.value.ValueManager;
import com.primordial.energy.EnergyConversions;
import com.primordial.util.BalanceSyncer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.energy.IEnergyStorage;
import java.util.function.LongUnaryOperator;
import com.primordial.resource.EmcResourcePort;
import com.primordial.resource.ResourceExchangeConversions;
import com.primordial.resource.ResourcePortRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** EMC 业务统一入口；所有写入都会经过 ProjectE capability 或离线增量存储。 */
public final class EmcService {
    private EmcService() {}

    public static BigInteger itemValue(ItemStack stack, MinecraftServer server) {
        return ValueManager.itemValueEmc(stack, server);
    }

    public static BigInteger balance(MinecraftServer server, UUID player) {
        return ProjectECompat.get(server, player);
    }

    public static void add(MinecraftServer server, UUID player, BigInteger amount) {
        if (amount.signum() <= 0) return;
        ProjectECompat.add(server, player, amount);
        BalanceSyncer.markDirty(player);
    }

    public static boolean spend(MinecraftServer server, UUID player, BigInteger amount) {
        boolean spent = ProjectECompat.spend(server, player, amount);
        if (spent && amount.signum() > 0) BalanceSyncer.markDirty(player);
        return spent;
    }

    /** 物品输出资格：始终检查任务阶段，知识开关只决定是否额外要求 ProjectE 知识。 */
    public static boolean hasKnowledge(MinecraftServer server, UUID player, ItemStack stack) {
        return ResourceKnowledge.allowsItem(server, player, stack);
    }

    /** 回收后学习物品；在线立即同步，离线写入持久化队列。 */
    public static boolean learn(MinecraftServer server, UUID player, ItemStack stack) {
        return ProjectECompat.learn(server, player, ResourceKnowledge.knowledgeSample(stack));
    }

    public static void mergePending(ServerPlayer player) {
        ProjectECompat.mergePending(player);
    }

    /** 玩家登录时合并离线回收产生的 ProjectE 知识。 */
    public static void mergePendingKnowledge(ServerPlayer player) {
        ProjectECompat.mergePendingKnowledge(player);
    }

    public static EmcAmount itemPrice(ItemStack stack, MinecraftServer server) {
        return EmcAmount.of(itemValue(stack, server));
    }

    public static EmcAmount itemRecoveryPrice(ItemStack stack, MinecraftServer server) {
        return EmcAmount.of(ProjectECompat.itemSellEmc(stack));
    }

    public static EmcAmount fluidPrice(Fluid fluid) { return ValueManager.fluidPricePerMB(fluid); }
    public static EmcAmount gasPrice(ResourceLocation gas) { return EmcAmount.of(ValueManager.gasValueEmcPerMB(gas)); }
    public static EmcAmount energyPrice(ResourceLocation unit) {
        return com.primordial.config.PrimordialServerConfig.resourceBlocked(unit)
                || (EnergyConversions.FE.equals(unit) || EnergyConversions.RF.equals(unit))
                && com.primordial.config.PrimordialServerConfig.resourceBlocked(EnergyConversions.FE)
                ? EmcAmount.ZERO : EnergyConversions.emcPerUnit(unit);
    }

    /** 返回独立资源单位的 EMC/单位报价；所有适配器和未来配置器共用这一入口。 */
    public static EmcAmount resourcePrice(ResourceLocation unit) {
        return ResourceExchangeConversions.emcPerUnit(unit);
    }

    /** 读取机器指定方向的独立资源端口。查询失败时返回空列表，不会影响物品/流体任务。 */
    public static java.util.List<EmcResourcePort> resourcePorts(BlockEntity machine, Direction side) {
        return ResourcePortRegistry.find(machine, side);
    }

    /** 用 EMC 购买独立资源并写入端口；价格、扣款和真实接收量由统一结算事务控制。 */
    public static long supplyResource(MinecraftServer server, UUID owner, EmcResourcePort port, long requested) {
        if (port == null || !port.canReceive() || requested <= 0) return 0;
        if (!ResourceKnowledge.allows(server, owner, port.unit())) return 0;
        EmcAmount price = resourcePrice(port.unit());
        if (price.isZero()) return 0;
        long accepted = Math.max(0, Math.min(requested, port.receive(requested, true)));
        return purchase(server, owner, price, accepted, units -> port.receive(units, false));
    }

    /** 从独立资源端口回收为 EMC；只有端口实际抽取数量会被计入余额。 */
    public static long reclaimResource(MinecraftServer server, UUID owner, EmcResourcePort port, long requested) {
        if (port == null || !port.canExtract() || requested <= 0) return 0;
        EmcAmount price = resourcePrice(port.unit());
        if (price.isZero()) return 0;
        long available = Math.max(0, Math.min(requested, port.extract(requested, true)));
        return reclaim(server, owner, price, available, units -> port.extract(units, false));
    }

    /** transfer 只执行已授权数量，返回实际交付数；不得排异步任务。 */
    public static long purchase(MinecraftServer server, UUID owner, EmcAmount price, long requested, LongUnaryOperator transfer) {
        return EmcSettlement.purchase(server, owner, price, requested, transfer);
    }

    /** 需要消费上限或扣款明细时使用；回执仅包含本次预扣和退款，不包含嵌套交易。 */
    public static EmcSettlement.Purchase purchaseWithReceipt(MinecraftServer server, UUID owner, EmcAmount price,
                                                            long requested, LongUnaryOperator transfer) {
        return EmcSettlement.purchaseWithReceipt(server, owner, price, requested, transfer);
    }

    public static long purchaseItem(MinecraftServer server, UUID owner, ItemStack sample, long requested, LongUnaryOperator transfer) {
        if (!hasKnowledge(server, owner, sample)) return 0;
        return purchase(server, owner, itemPrice(sample, server), requested, transfer);
    }

    public static long purchaseFluid(MinecraftServer server, UUID owner, Fluid fluid, long requested, LongUnaryOperator transfer) {
        if (com.primordial.config.PrimordialServerConfig.mediaPurchaseDisabled(false)) return 0;
        var buyMode = com.primordial.value.ValueManager.fluidMode(fluid == null ? null : BuiltInRegistries.FLUID.getKey(fluid));
        if (buyMode != null && !buyMode.allowsBuy()) return 0;
        if (fluid == null || !ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.FLUID,
                BuiltInRegistries.FLUID.getKey(fluid))) return 0;
        return purchase(server, owner, fluidPrice(fluid), requested, transfer);
    }

    public static long reclaimFluid(MinecraftServer server, UUID owner, Fluid fluid, long requested, LongUnaryOperator extract) {
        if (com.primordial.config.PrimordialServerConfig.mediaSaleDisabled(false)) return 0;
        if (fluid == null) return 0;
        var sellMode = com.primordial.value.ValueManager.fluidMode(BuiltInRegistries.FLUID.getKey(fluid));
        if (sellMode != null && !sellMode.allowsSell()) return 0;
        long actual = reclaim(server, owner, fluidPrice(fluid), requested, extract);
        if (actual > 0) ResourceKnowledge.learnMedia(server, owner, ResourceKnowledge.Kind.FLUID,
                BuiltInRegistries.FLUID.getKey(fluid));
        return actual;
    }

    /** 气体与流体共用模式、知识、价格和真实交付结算门禁；第三方 API 留在兼容层。 */
    public static long purchaseGas(MinecraftServer server, UUID owner, ResourceLocation gas, long requested, LongUnaryOperator transfer) {
        if (gas == null || com.primordial.config.PrimordialServerConfig.mediaPurchaseDisabled(true)) return 0;
        var mode = ValueManager.gasMode(gas);
        if (mode != null && !mode.allowsBuy()) return 0;
        if (!ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.GAS, gas)) return 0;
        return purchase(server, owner, gasPrice(gas), requested, transfer);
    }

    public static long reclaimGas(MinecraftServer server, UUID owner, ResourceLocation gas, long requested, LongUnaryOperator extract) {
        if (gas == null || com.primordial.config.PrimordialServerConfig.mediaSaleDisabled(true)) return 0;
        var mode = ValueManager.gasMode(gas);
        if (mode != null && !mode.allowsSell()) return 0;
        long actual = reclaim(server, owner, gasPrice(gas), requested, extract);
        if (actual > 0) ResourceKnowledge.learnMedia(server, owner, ResourceKnowledge.Kind.GAS, gas);
        return actual;
    }

    /** 抽取前确认有价，按接口实际抽取量入账；零价格不得销毁资源。 */
    public static long reclaim(MinecraftServer server, UUID owner, EmcAmount price, long requested, LongUnaryOperator extract) {
        if (server == null || owner == null || price.isZero() || requested <= 0) return 0;
        if (!server.isSameThread()) throw new IllegalStateException("Resource extraction requires the server thread");
        long actual = Math.max(0, Math.min(requested, extract.applyAsLong(requested)));
        credit(server, owner, price.multiply(actual));
        return actual;
    }

    public static void credit(MinecraftServer server, UUID owner, EmcAmount amount) { EmcSettlement.credit(server, owner, amount); }
    public static void clearBalance(MinecraftServer server, UUID owner) { EmcSettlement.clear(server, owner); }

    /** FE 接口已完成原生能源转换，报价只使用 forge:fe，防止第二次套用 J/AE 比率。 */
    public static int supplyEnergy(MinecraftServer server, UUID owner, IEnergyStorage storage, int requested) {
        if (requested <= 0 || !storage.canReceive()) return 0;
        if (!ResourceKnowledge.allows(server, owner, ResourceKnowledge.Kind.ENERGY)) return 0;
        int accepted = Math.max(0, Math.min(requested, storage.receiveEnergy(requested, true)));
        return (int) purchase(server, owner, energyPrice(EnergyConversions.FE), accepted,
                units -> storage.receiveEnergy((int) units, false));
    }

    public static int reclaimEnergy(MinecraftServer server, UUID owner, IEnergyStorage storage, int requested) {
        if (requested <= 0 || !storage.canExtract()) return 0;
        return (int) reclaim(server, owner, energyPrice(EnergyConversions.FE), requested,
                units -> storage.extractEnergy((int) units, false));
    }
}
