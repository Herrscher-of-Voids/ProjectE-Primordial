/**
 * 【变更记录 #11】2026-09-15 00:40:01（第 11 次备注 · 输入循环安全上限）
 * - 删除：无；保留原有 simulate、扣费、执行、退款流程。
 * - 新增：第三方 IItemHandler 单次写入最多推进 64 轮的保护。
 * - 修改：insertCount 达到轮数上限后立即结束，未写入数量按原流程退款并等待下一 tick。
 * - 用途：兼容补充模式的大数量配置，同时阻止机器每轮只接受 1 个物品时在一个 tick 中执行数十亿次循环。
 */
/**
 * 变更记录 #4（2026-09-14）：接入通用 MachineProxy 注册表，统一按方向读取机器能力。
 * - 新增：专用代理优先、Forge capability 代理回退。
 * - 修改：配置器能力检测和任务执行器改用代理层，保留现有槽位与配置协议。
 */
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
 * 【文件职责】按机器配置对单端口生成/回收物品、流体、FE 并结算 EMC。
 * 【变更记录 #5】2026-09-14 00:55:10（第 5 次备注 · 1.0.0-stage5）
 * - 删除：删除首个 capability 自动择面路径及空样本的无用能力查询。
 * - 新增：每样本端口解析、enabled 判断、单槽模拟请求上限及非正物品价格跳过。
 * - 修改：物品/流体通过 MachineTargetAccess 仅操作绑定目标；供能/回收分别读绑定面，传输日志降为 DEBUG，输入失败提示使用有效计时槽。
 * - 用途：旧未绑定配置暂停；第三方接口异常隔离到当前机器。本阶段不改变水/岩浆 1000 EMC/1000mB 和 FE 换算配置，旧计价服务的精度改造不在此记录中宣称完成。
 */
/**
 * 【变更记录 #6】2026-09-14 13:50:00（第 6 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：物品/流体多目标遍历、Mekanism GasStack 输入输出和统一气体 EMC 结算。
 * - 修改：流体样本同时兼容 FluidStack 与 GasStack，GasStack 不再经过桶或 Forge Fluid 转换。
 * - 用途：每秒执行机器自动化任务；目标失效、未知价格和余额不足均跳过当前目标而不中断其它机器。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
 */
/**
 * 【变更记录 #8】2026-09-14 16:45:00（第 8 次备注 · 1.0.0-stage5-feedback2-hotfix）
 * - 删除：运行时把旧 rate 字段当作固定模式速率的路径；旧字段仅保留给已有存档读取。
 * - 新增：固定模式使用 amount 数量字段，补充模式根据目标容量或缺口计算，不受固定数量影响。
 * - 修改：EnergyConfig、玩家背包规则、配置器界面和任务执行器统一采用“数量 + tick 间隔”语义，并继续限制非法值。
 * - 用途：避免高容量设备因为旧上限或错误速率解释而产生过量循环、配置丢失或跨槽位写入。
 */
/**
 * 【编码修复说明】TaskExecutor 的原备注因错误字符集转换被写成问号，已根据当前实现恢复。
 * - 删除：不可读的问号文本。
 * - 新增：职责、边界条件和失败处理说明。
 * - 修改：只修复注释内容，不改变字段、协议、算法或运行时行为。
 * - 用途：帮助维护者理解本段代码，并确保后续修改可追溯。
 */
/**
 * 变更记录 #10（2026-09-14）：限制配置器物品生成必须来自 ProjectE 已学习知识，并支持回收自动学习。
 * - 新增：物品输入生成前调用 EmcService.hasKnowledge；物品输出成功后按服务端开关调用 learn。
 * - 修改：知识校验位于模拟插入和 EMC 扣除之前，未学习物品不会消耗余额。
 * - 目的：保持转化桌知识与配置器输出一致，并让回收流程可选地扩展知识表。
 */
/**
 * 【变更记录 #12】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
/**
 * 【变更记录 #13】2026-09-15 12:04:51（第 13 次备注 · 1.0.0-configurator-fixes）
 * - 删除：气体未定价时先执行抽取的分支，以及补充必须付得起整个罐容量才执行的要求。
 * - 新增：气体回收前价格校验、按余额计算可购买气体数量。
 * - 修改：供给使用 min(可接收量, 余额/单价)，回收只抽有价气体，并按实际量以 BigInteger 计费。
 * - 用途：防止无价气体被销毁，以及大罐有余额却完全不补充的问题。
 */
/**
 * 【变更记录 #14】2026-09-15 13:42:29（第 14 次备注 · 1.0.0-thermal-item-overflow）
 * - 删除：直接以 Integer.MAX_VALUE 构造补充探测栈和执行栈的本地方法。
 * - 新增：通过 MachineItemTransfer 按槽位真实空位模拟及写入，满槽不进入扣费路径。
 * - 修改：补充每 tick 执行、固定模式数量/间隔不变；执行后记录实际数量及净支出，退款仍按未插入量结算。
 * - 用途：修复热力输入槽数量溢出造成的 64/空槽闪烁与异常 EMC 扣除，并保留原端口绑定和大容量支持。
 */
/**
 * 【变更记录 #15】2026-09-15 14:54:10（第 15 次备注 · 1.0.0-emc-service）
 * - 删除：各资源自行查余额/扣款/退款、流体提前整除、无价流体先抽取及废弃速率辅助。
 * - 新增：调用 EmcService 精确购买与回收；所有生成按实际接受数量收费。
 * - 修改：物品保留热力容量保护；流体和气体先定价后抽取，FE 与手持充能共用同一能力服务。
 * - 用途：消除机器与黑洞/背包计价差异，低于 1 EMC 的流体、FE 分批传输不丢价不重复取整。
 */
/**
 * 【变更记录 #16】2026-09-15 15:44:04（第 16 次备注 · 1.0.0-emc-service）
 * - 删除：固定模式用未经储罐容量裁剪的数量探测第三方接口。
 * - 新增：补充与固定模式共用 long 空位计算，满罐立即返回。
 * - 修改：只提交目标单罐能容纳的数量，执行结算仍以真实接收量为准。
 * - 用途：保护第三方 int 容量加法，避免超大固定数量引发溢出和错误扣费。
 */
/**
 * 【变更记录 #18】2026-09-16（独立资源任务）
 * - 新增：按资源输入/输出规则逐目标执行 EMC 供给和回收，补充模式每 tick 运行，固定模式按 intervalTicks 运行。
 * - 修改：资源数量先按端口容量或当前存量裁剪，再交给 EmcService 结算；端口失效或单位不匹配时跳过当前目标。
 * - 用途：保护第三方资源接口不被超量调用，并使六种资源与物品、流体使用相同的 EMC 余额和找零规则。
 */
/**
 * 【变更记录 #17】2026-09-16 01:59:37（第 17 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：回收与购买强行共用同一个数值、绕过 ProjectE 损耗规则的假定。
 * - 新增：配置器抽取物品改用 ProjectE 回收价格，供货仍读原生购买价，尊重整合包的损耗配置。
 * - 修改：交易定价统一委托 ProjectE，扩展流体/气体定价和 BigInteger 玩家余额不受影响。
 * - 用途：让本模组回收物品与转化桌回收遵循同一套价值规则。
 */
/**
 * 【文件职责】运行配置器机器输入输出任务。
 * 【变更记录 #19】2026-09-16 19:41:50（第 19 次备注 · 1.0.0-review-ready）
 * - 删除：移除流体输入直接调用通用purchase的路径。
 * - 新增：将流体输出交给EmcService.purchaseFluid执行知识前置校验。
 * - 修改：目标罐与方向绑定、真实填充数量及退款沿用原有结算。
 * - 用途：机器流体生成遵守凭证知识且仍只写指定端口。
 */
/**
 * 【变更记录 #20】2026-10-02 16:08:00（第 20 次备注 · 1.2.0-media-learning）
 * - 删除：流体回收只入账、不记录已卖出种类的路径。
 * - 新增：实际抽取成功的流体通过 EmcService.reclaimFluid 学习注册名。
 * - 修改：模拟和零抽取不写知识；现有任务调度与结算数量保持不变。
 * - 用途：机器回收的流体可按种类解锁后续购买。
 */
/**
 * 【变更记录 #21】2026-10-13 19:40:05（第 21 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：每条流体输入规则在查能力前检查 SUPPLY，禁止时跳过此样本并继续其它配置，实际补充使用统一供货服务。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #22】2026-10-13 20:14:16（第 22 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
package com.primordial.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;


import com.primordial.PrimordialMod;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.EnergyConfig;
import com.primordial.data.FluidSampleConfig;
import com.primordial.data.ItemSampleConfig;
import com.primordial.data.MachineConfig;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.service.EmcService;
import com.primordial.machine.MachineProxy;
import com.primordial.machine.MachineProxyRegistry;
import com.primordial.machine.MachineTargetAccess;
import com.primordial.data.SampleMode;
import com.primordial.data.ResourceSampleConfig;
import com.primordial.resource.EmcResourcePort;
import com.primordial.util.BalanceSyncer;
import com.primordial.util.MachineKey;

import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** 全介质任务执行：物品/流体/能量的输入与输出，simulate-扣费-执行-退款防泄漏 */
public final class TaskExecutor {
    // 计时槽位
    private static final int T_ITEM_IN = 0;
    private static final int T_FLUID_IN = 9;
    private static final int T_ITEM_OUT = 18;
    private static final int T_FLUID_OUT = 27;
    private static final int T_ENERGY_SUPPLY = 36;
    private static final int T_ENERGY_RECOVER = 37;
    private static final int T_WARNING = 40;
    /** 独立资源规则的计时槽；每种方向六个资源，避免与物品/流体计时互相覆盖。 */
    private static final int T_RESOURCE_IN = 48;
    private static final int T_RESOURCE_OUT = 54;

    private TaskExecutor() {}

    public static void runAll(MinecraftServer server) {
        MachineConfigSavedData store = MachineConfigSavedData.get(server);
        List<String> orphans = new ArrayList<>();
        for (Entry<String, MachineConfig> entry : store.allEntries()) {
            String key = entry.getKey();
            MachineConfig cfg = entry.getValue();
            if (cfg.owner == null) {
                orphans.add(key);
                continue;
            }
            ServerLevel level = server.getLevel(MachineKey.parseDim(key));
            if (level == null) {
                orphans.add(key);
                continue;
            }
            BlockPos pos = MachineKey.parsePos(key);
            if (!level.isLoaded(pos)) continue; // 区块未加载：跳过本轮，不清配置
            if (level.getBlockState(pos).isAir()) {
                orphans.add(key);
                continue;
            }
            try {
                execute(server, level, pos, key, cfg);
            } catch (Exception | LinkageError e) {
                PrimordialMod.LOGGER.error("[Primordial] task error at {}", key, e);
            }
        }
        for (String key : orphans) {
            store.remove(key);
            TaskScheduler.dropTimers(key);
            PrimordialMod.LOGGER.info("[Primordial] removed orphan config {}", key);
        }
    }

    private static void execute(MinecraftServer server, ServerLevel level, BlockPos pos,
            String key, MachineConfig cfg) {
        UUID owner = cfg.owner;
        long gameTime = level.getGameTime();

        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        MachineProxy proxy = MachineProxyRegistry.resolve(be);
        if (cfg.itemInEnabled) runItemInput(owner, cfg, proxy, be, server, key, gameTime, pos);
        if (cfg.itemOutEnabled) runItemOutput(owner, cfg, proxy, be, server, key, gameTime);
        if (cfg.fluidInEnabled) runFluidInput(owner, cfg, proxy, be, server, key, gameTime, pos);
        if (cfg.fluidOutEnabled) runFluidOutput(owner, cfg, proxy, be, server, key, gameTime);
        runEnergy(owner, cfg, proxy, be, server, key, gameTime, pos);
        runResourceInput(owner, cfg, proxy, be, server, key, gameTime);
        runResourceOutput(owner, cfg, proxy, be, server, key, gameTime);
    }

    // ---------------- 物品 ----------------

    private static void runItemInput(UUID owner, MachineConfig cfg,
            MachineProxy proxy, BlockEntity machine, MinecraftServer server, String key, long gameTime, BlockPos pos) {
        for (int i = 0; i < 9; i++) {
            ItemSampleConfig s = cfg.itemIn[i];
            if (s.filter.isEmpty() || !s.enabled) continue;
            // 配置器输出必须遵守 ProjectE 转化桌知识；查询失败时安全拒绝生成。
            if (!EmcService.hasKnowledge(server, owner, s.filter)) continue;
            if (s.mode == SampleMode.FIXED_RATE
                    && !TaskScheduler.intervalDue(key, T_ITEM_IN + i, s.intervalTicks, gameTime)) continue;
            for (com.primordial.data.MachineTarget target : s.effectiveTargets()) {
            IItemHandler items = MachineTargetAccess.items(proxy, machine, target);
            if (items == null) continue;
            int want;
            if (s.mode == SampleMode.KEEP_LEVEL) {
                // 表示尽量补满；真正交给接口的数量由 MachineItemTransfer 按目标空位裁剪。
                want = Integer.MAX_VALUE;
            } else {
                want = Math.max(1, s.threshold);
            }
            int space = MachineItemTransfer.simulateInsert(items, s.filter, want);
            int count = Math.min(want, space);
            if (count <= 0) continue;

            long real = EmcService.purchaseItem(server, owner, s.filter, count,
                    units -> MachineItemTransfer.insertCount(items, s.filter, (int) units));
            if (real > 0) {
                BalanceSyncer.markDirty(owner);
                // 只记录实际插入后的净支出；关闭 DEBUG 时不解析物品名或构造日志参数。
                if (PrimordialMod.LOGGER.isDebugEnabled()) {
                    PrimordialMod.LOGGER.debug("[Primordial][Task] Item generation spent {} EMC at {} (owner={}, item={}x{})",
                            EmcService.itemPrice(s.filter, server).multiply(real), pos, owner,
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.filter.getItem()), real);
                }
            }
            }
        }
    }

    private static void runItemOutput(UUID owner, MachineConfig cfg,
            MachineProxy proxy, BlockEntity machine, MinecraftServer server, String key, long gameTime) {
        for (int i = 0; i < 9; i++) {
            ItemSampleConfig s = cfg.itemOut[i];
            if (s.filter.isEmpty() || !s.enabled) continue;
            if (s.mode == SampleMode.FIXED_RATE
                    && !TaskScheduler.intervalDue(key, T_ITEM_OUT + i, s.intervalTicks, gameTime)) continue;
            for (com.primordial.data.MachineTarget target : s.effectiveTargets()) {
            IItemHandler items = MachineTargetAccess.items(proxy, machine, target);
            if (items == null) continue;
            int max = s.mode == SampleMode.KEEP_LEVEL ? Integer.MAX_VALUE : Math.max(1, s.threshold);
            var valuePer = EmcService.itemRecoveryPrice(s.filter, server);
            long extracted = EmcService.reclaim(server, owner, valuePer, max,
                    units -> extractMatching(items, s.filter, (int) units));
            if (extracted <= 0) continue;
            if (PrimordialServerConfig.autoLearnReclaimedItems()) {
                EmcService.learn(server, owner, s.filter);
            }
            // 诊断日志：记录物品回收增加 EMC
            if (PrimordialMod.LOGGER.isDebugEnabled()) PrimordialMod.LOGGER.debug("[Primordial][Task] Item reclaim added {} EMC (owner={}, item={}x{}, valuePer={})",
                    valuePer.multiply(extracted), owner, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.filter.getItem()), extracted, valuePer);
            BalanceSyncer.markDirty(owner);
            }
        }
    }

    /** 提取匹配（完整 NBT）物品，返回实际提取数 */
    private static int extractMatching(IItemHandler h, ItemStack filter, int max) {
        int extracted = 0;
        for (int slot = 0; slot < h.getSlots() && extracted < max; slot++) {
            ItemStack inSlot = h.getStackInSlot(slot);
            if (!ItemStack.isSameItemSameTags(inSlot, filter)) continue;
            int take = Math.min(inSlot.getCount(), max - extracted);
            ItemStack got = h.extractItem(slot, take, false);
            if (ItemStack.isSameItemSameTags(got, filter)) extracted += Math.min(take, got.getCount());
        }
        return extracted;
    }

    // ---------------- 流体 ----------------

    private static void runFluidInput(UUID owner, MachineConfig cfg,
            MachineProxy proxy, BlockEntity machine, MinecraftServer server, String key, long gameTime, BlockPos pos) {
        for (int i = 0; i < 9; i++) {
            FluidSampleConfig s = cfg.fluidIn[i];
            if ((s.filter.isEmpty() && !s.hasGasFilter()) || !s.enabled) continue;
            if (s.hasGasFilter()) { runGasInput(owner, s, proxy, machine, server, key, gameTime, pos, i); continue; }
            if (s.mode == SampleMode.FIXED_RATE
                    && !TaskScheduler.intervalDue(key, T_FLUID_IN + i, s.intervalTicks, gameTime)) continue;
            for (com.primordial.data.MachineTarget target : s.effectiveTargets()) {
            IFluidHandler fluids = MachineTargetAccess.fluids(proxy, machine, target);
            if (fluids == null) continue;
            // 两种模式均先按单罐空位裁剪，避免固定数量为最大 int 时第三方加法溢出。
            int free = (int) Math.max(0, Math.min(Integer.MAX_VALUE,
                    (long) fluids.getTankCapacity(0) - fluids.getFluidInTank(0).getAmount()));
            int want = s.mode == SampleMode.KEEP_LEVEL ? free : Math.min(free, Math.max(1, s.threshold));
            if (want <= 0) continue;
            FluidStack toFill = new FluidStack(s.filter.getFluid(), want, s.filter.getTag());
            int space = fluids.fill(toFill, IFluidHandler.FluidAction.SIMULATE);
            int amount = Math.min(want, space);
            if (amount <= 0) continue;

            long filled = EmcService.purchaseFluid(server, owner, s.filter.getFluid(), amount,
                    units -> fluids.fill(new FluidStack(s.filter.getFluid(), (int) units, s.filter.getTag()), IFluidHandler.FluidAction.EXECUTE));
            if (filled > 0) BalanceSyncer.markDirty(owner);
            }
        }
    }

    private static void runFluidOutput(UUID owner, MachineConfig cfg,
            MachineProxy proxy, BlockEntity machine, MinecraftServer server, String key, long gameTime) {
        for (int i = 0; i < 9; i++) {
            FluidSampleConfig s = cfg.fluidOut[i];
            if ((s.filter.isEmpty() && !s.hasGasFilter()) || !s.enabled) continue;
            if (s.hasGasFilter()) { runGasOutput(owner, s, proxy, machine, server, key, gameTime, i); continue; }
            if (s.mode == SampleMode.FIXED_RATE
                    && !TaskScheduler.intervalDue(key, T_FLUID_OUT + i, s.intervalTicks, gameTime)) continue;
            for (com.primordial.data.MachineTarget target : s.effectiveTargets()) {
            IFluidHandler fluids = MachineTargetAccess.fluids(proxy, machine, target);
            if (fluids == null) continue;
            int amount = s.mode == SampleMode.KEEP_LEVEL ? Integer.MAX_VALUE : Math.max(1, s.threshold);
            EmcService.reclaimFluid(server, owner, s.filter.getFluid(), amount, units -> {
                FluidStack got = fluids.drain(new FluidStack(s.filter.getFluid(), (int) units, s.filter.getTag()), IFluidHandler.FluidAction.EXECUTE);
                return got.isFluidEqual(s.filter) ? got.getAmount() : 0;
            });
            }
        }
    }

    private static void runGasInput(UUID owner, FluidSampleConfig s, MachineProxy proxy, BlockEntity machine,
            MinecraftServer server, String key, long gameTime, BlockPos pos, int sampleIndex) {
        if (s.mode == SampleMode.FIXED_RATE
                && !TaskScheduler.intervalDue(key, T_FLUID_IN + sampleIndex, s.intervalTicks, gameTime)) return;
        for (com.primordial.data.MachineTarget target : s.effectiveTargets()) {
            Object gases = MachineTargetAccess.gases(proxy, machine, target);
            if (gases == null) continue;
            long actual = com.primordial.compat.MekanismGasCompat.supply(server, owner, s, gases, target.index());
            if (actual > 0) BalanceSyncer.markDirty(owner);
        }
    }

    private static void runGasOutput(UUID owner, FluidSampleConfig s, MachineProxy proxy, BlockEntity machine,
            MinecraftServer server, String key, long gameTime, int sampleIndex) {
        // 必须在抽取前确认有价，避免先消耗未知气体却没有 EMC 可结算。
        if (!com.primordial.compat.MekanismGasCompat.isGas(s.gasFilter)) return;
        if (s.mode == SampleMode.FIXED_RATE
                && !TaskScheduler.intervalDue(key, T_FLUID_OUT + sampleIndex, s.intervalTicks, gameTime)) return;
        for (com.primordial.data.MachineTarget target : s.effectiveTargets()) {
            Object gases = MachineTargetAccess.gases(proxy, machine, target);
            if (gases == null) continue;
            long actual = com.primordial.compat.MekanismGasCompat.reclaim(server, owner, s, gases, target.index());
            if (actual > 0) BalanceSyncer.markDirty(owner);
        }
    }

    // ---------------- 能量 ----------------

    private static void runEnergy(UUID owner, MachineConfig cfg,
            MachineProxy proxy, BlockEntity machine, MinecraftServer server, String key, long gameTime, BlockPos pos) {
        EnergyConfig e = cfg.energy;
        IEnergyStorage energy = e.supplyEnabled ? MachineTargetAccess.energy(proxy, machine, e.supplyPort) : null;
        if (e.supplyEnabled && energy != null) {
            int want;
            if (e.supplyMode == SampleMode.KEEP_LEVEL) {
                want = Integer.MAX_VALUE; // FE 包装器可能把超大内部余额裁剪为 int，交由模拟报告接收量。
            } else {
                if (!TaskScheduler.intervalDue(key, T_ENERGY_SUPPLY, e.supplyIntervalTicks, gameTime)) want = 0;
                else want = Math.max(1, e.supplyAmount);
            }
            if (want > 0) {
                EmcService.supplyEnergy(server, owner, energy, want);
            }
        }
        energy = e.recoverEnabled ? MachineTargetAccess.energy(proxy, machine, e.recoverPort) : null;
        if (e.recoverEnabled && energy != null) {
            int want;
            if (e.recoverMode == SampleMode.KEEP_LEVEL) {
                want = Integer.MAX_VALUE;
            } else {
                if (!TaskScheduler.intervalDue(key, T_ENERGY_RECOVER, e.recoverIntervalTicks, gameTime)) want = 0;
                else want = Math.max(1, e.recoverAmount);
            }
            if (want > 0) {
                EmcService.reclaimEnergy(server, owner, energy, want);
            }
        }
    }

    // ---------------- 独立资源（魔力、气压、灵气、魔源、转速和应力） ----------------

    /**
     * 用 EMC 向已绑定的独立资源端口供给资源。补充模式每 tick 按端口容量缺口运行；
     * 固定模式只在 intervalTicks 到期时传输 amount，且每个目标端口独立裁剪容量。
     */
    private static void runResourceInput(UUID owner, MachineConfig cfg,
            MachineProxy proxy, BlockEntity machine, MinecraftServer server, String key, long gameTime) {
        for (int i = 0; i < cfg.resourceIn.length; i++) {
            ResourceSampleConfig sample = cfg.resourceIn[i];
            if (!sample.enabled || sample.effectiveTargets().isEmpty()) continue;
            if (sample.mode == SampleMode.FIXED_RATE
                    && !TaskScheduler.intervalDue(key, T_RESOURCE_IN + i, sample.intervalTicks, gameTime)) continue;
            for (com.primordial.data.MachineTarget target : sample.effectiveTargets()) {
                EmcResourcePort port = MachineTargetAccess.resource(machine, target);
                if (port == null || !sample.unit.equals(port.unit()) || !port.canReceive()) continue;
                long free = availableCapacity(port);
                long requested = sample.mode == SampleMode.KEEP_LEVEL
                        ? free : Math.min(free, Math.max(1L, sample.amount));
                if (requested <= 0) continue;
                long moved = EmcService.supplyResource(server, owner, port, requested);
                if (moved > 0) BalanceSyncer.markDirty(owner);
            }
        }
    }

    /**
     * 从已绑定的独立资源端口回收 EMC。补充模式每 tick 抽空当前端口；固定模式按 amount
     * 和 tick 间隔执行。EmcService 会在抽取前检查价格并按实际抽取量结算。
     */
    private static void runResourceOutput(UUID owner, MachineConfig cfg,
            MachineProxy proxy, BlockEntity machine, MinecraftServer server, String key, long gameTime) {
        for (int i = 0; i < cfg.resourceOut.length; i++) {
            ResourceSampleConfig sample = cfg.resourceOut[i];
            if (!sample.enabled || sample.effectiveTargets().isEmpty()) continue;
            if (sample.mode == SampleMode.FIXED_RATE
                    && !TaskScheduler.intervalDue(key, T_RESOURCE_OUT + i, sample.intervalTicks, gameTime)) continue;
            for (com.primordial.data.MachineTarget target : sample.effectiveTargets()) {
                EmcResourcePort port = MachineTargetAccess.resource(machine, target);
                if (port == null || !sample.unit.equals(port.unit()) || !port.canExtract()) continue;
                long available = nonNegative(port.amount());
                long requested = sample.mode == SampleMode.KEEP_LEVEL
                        ? available : Math.min(available, Math.max(1L, sample.amount));
                if (requested <= 0) continue;
                long moved = EmcService.reclaimResource(server, owner, port, requested);
                if (moved > 0) BalanceSyncer.markDirty(owner);
            }
        }
    }

    /** 防御第三方 API 返回负数或溢出值，避免 EMC 结算收到非法数量。 */
    private static long nonNegative(long value) { return value <= 0 ? 0 : value; }

    /** 计算端口可接收数量；Long.MAX_VALUE 表示未知容量时保持可用的 long 上限。 */
    private static long availableCapacity(EmcResourcePort port) {
        long amount = nonNegative(port.amount());
        long capacity = port.capacity();
        if (capacity <= 0 || capacity == Long.MAX_VALUE) return capacity == Long.MAX_VALUE ? Long.MAX_VALUE : 0;
        return capacity <= amount ? 0 : capacity - amount;
    }

    /** 判断指定规则经过 intervalTicks 个服务端 tick 后是否到期，并更新下一次执行时间。 */
    private static void warnPlayerOnce(MinecraftServer server, UUID owner, String key, int timerSlot,
            long gameTime, String msg) {
        if (!TaskScheduler.intervalDue(key, timerSlot, 10, gameTime)) return;
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) {
            p.sendSystemMessage(Component.literal("§c[原初] " + msg));
        }
        PrimordialMod.LOGGER.warn("[Primordial][Task] {}", msg);
    }

}

