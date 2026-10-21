/**
 * 【变更记录 #5】2026-09-15 00:40:01（第 5 次备注 · 性能紧急修复）
 * - 删除：物品、流体、能量每次接收立即写入 ProjectE、逐堆 INFO 日志以及 IItemHandler 无条件返回 EMPTY 的实现。
 * - 新增：接收预算检查、输入余量返回、流体定价校验和黑洞 pendingEmc 队列调用。
 * - 修改：Forge 能力只报告实际接收量；能量余数继续持久化，流体和物品金额交由 BlackholeBlockEntity 在 tick 尾部合并。
 * - 用途：遵守 Forge 能力合约，避免管道反馈循环、超大堆复制和每次操作触发网络同步；未知流体安全拒绝且不产生免费 EMC。
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
 * 变更记录 #4（2026-09-14）：黑洞回收物品后接入 ProjectE 自动学习开关。
 * - 新增：回收成功后按 PrimordialServerConfig 调用 EmcService.learn。
 * - 修改：学习发生在物品已经确认被接收并结算 EMC 之后，不影响模拟填充结果。
 * - 目的：黑洞与配置器回收路径使用同一套可选的自动学习规则。
 */
/**
 * 【变更记录 #6】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
/**
 * 【变更记录 #7】2026-09-15 14:58:00（第 7 次备注 · 1.0.0-emc-service）
 * - 删除：流体/FE 直接整除、各处理器绕过服务查价以及模拟接收无价物品。
 * - 新增：EmcService 报价后精确入黑洞队列；服务端、无主、已移除边界一致拒绝。
 * - 修改：模拟不扣预算或入账，实际数量才累加；FE 余数统一为 EMC 分数。
 * - 用途：保护管道返回值与低价资源，队列每 tick 批量提交，避免高频余额写入。
 */
/**
 * 【变更记录 #8】2026-09-15 15:44:04（第 8 次备注 · 1.0.0-emc-service）
 * - 删除：描述无条件全收和方块独立 FE 余数的过时方法说明。
 * - 新增：有限预算、有价校验和精确 EMC 队列的接口契约。
 * - 修改：清理旧计费方式遗留的未使用导入。
 * - 用途：备注准确说明模拟、执行与实际结算的边界。
 */
/**
 * 【变更记录 #9】2026-09-16 01:59:37（第 9 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：回收与购买强行共用同一个数值、绕过 ProjectE 损耗规则的假定。
 * - 新增：黑洞物品入账与有效性检查改用 ProjectE 回收价格，回收所得和转化桌一致。
 * - 修改：交易定价统一委托 ProjectE，扩展流体/气体定价和 BigInteger 玩家余额不受影响。
 * - 用途：让本模组回收物品与转化桌回收遵循同一套价值规则。
 */
/**
 * 【变更记录 #10】2026-10-02 16:08:00（第 10 次备注 · 1.2.0-media-learning）
 * - 删除：黑洞实际吞入流体却不记录其资源知识的路径。
 * - 新增：确认有价流体和已接受数量后，按注册名记录玩家知识。
 * - 修改：模拟填充、无主和零价格仍不能学习；EMC 队列结算保持原样。
 * - 用途：回收流体后允许玩家购买该流体，不依赖类别凭证。
 */
/**
 * 【变更记录 #11】2026-10-02 18:54:32（第 11 次备注 · 1.2.0-media-trade-switches）
 * - 删除：无。
 * - 新增：黑洞流体有效性、模拟和回收入口检查流体卖出开关。
 * - 修改：禁用时不接收流体、不占预算且不计 EMC；已有队列不删除。
 * - 用途：阻止通过管道和直接回收绕过整合包的卖出限制。
 */
/** 【变更记录 #12】2026-10-08 03:32:46（1.2.0-media-price-modes）
 * 删除：无。新增：黑洞流体回收按价格方向状态校验。修改：实际回收和能力探测都尊重仅买状态。用途：避免黑洞旁路价格编辑器的卖出限制。 */
/**
 * 【变更记录 #13】2026-10-10 04:18:06（第 13 次备注 · 1.2.0-resource-system-one）
 * - 删除：FE 统一黑名单后黑洞仍接收零价能源、销毁却不入账的路径。
 * - 新增：FE 接收能力、模拟及执行都在预算修改前检查统一报价。
 * - 修改：每次使用同一价格快照记录真实接受量；零价返回零，不触碰预算、粒子和待结算队列。
 * - 用途：第 1 系统所有 FE 入口一致拒绝黑名单资源，避免自动化接口吞掉不可交易能源。
 */
/**
 * 【变更记录 #14】2026-10-13 19:40:05（第 14 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：流体黑洞有效性、模拟、直接回收在排队 EMC 前检查 SELL 规则，拒绝时输入原样保留。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #15】2026-10-13 20:14:16（第 15 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
/**
 * 【变更记录 #16】2026-10-13 23:59:40（第 16 次备注 · 1.2.0-discovery-learning-system）
 * - 删除：非主线程流体回收先排队 EMC、随后学习抛错的部分提交路径。
 * - 新增：实际回收在入队前检查服务器主线程，不满足时拒收。
 * - 修改：与气体黑洞保持相同提交边界，模拟查询和正常主线程交易不变。
 * - 用途：卖入结算和知识写入在同一线程完成，拒收不占预算或学习。
 */
package com.primordial.blockentity;

import java.util.UUID;

import com.primordial.PrimordialMod;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.service.EmcService;
import com.primordial.service.EmcAmount;
import com.primordial.energy.EnergyConversions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * 黑洞的虚无接收端：收到即销毁并计价入池。
 * 物品与流体仅接收有价资源，所有输入遵守每 tick 预算并返回实际数量或剩余量。
 * 物品显示为空且不可抽出；流体 drain 和能源 extract 同样不提供输出。
 */
public final class VoidHandlers {
    private VoidHandlers() {}

    /**
     * 将已确认接收的物品按当前 EMC 价值加入黑洞的 pendingEmc 队列，并记录本 tick 的回收数量。
     * 价值读取、自动学习和余额提交均通过服务端服务完成；能力回调只执行有限计算并返回成功状态。
     */
    public static boolean reclaimItem(BlackholeBlockEntity be, ItemStack stack) {
        UUID owner = be.getOwner();
        if (owner == null || stack == null || stack.isEmpty()) return false;
        MinecraftServer server = be.getLevel() == null ? null : be.getLevel().getServer();
        if (server == null) return false;
        EmcAmount perItem = EmcService.itemRecoveryPrice(stack, server);
        if (perItem.isZero()) return false;
        EmcAmount emc = perItem.multiply(stack.getCount());
        be.queueEmc(emc);
        be.recordReclaimedItems(stack.getCount());
        if (PrimordialServerConfig.autoLearnReclaimedItems()) {
            EmcService.learn(server, owner, stack);
        }
        return true;
    }

    /** 将 Forge FluidStack 按每 mB EMC 价值加入队列；未知流体、空流体或无 owner 时拒绝 fill。 */
    public static boolean reclaimFluid(BlackholeBlockEntity be, FluidStack fluid) {
        if (PrimordialServerConfig.mediaSaleDisabled(false)) return false;
        UUID owner = be.getOwner();
        if (owner == null || fluid == null || fluid.isEmpty()) return false;
        var mode = com.primordial.value.ValueManager.fluidMode(
                net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
        if (mode != null && !mode.allowsSell()) return false;
        MinecraftServer server = be.getLevel() == null ? null : be.getLevel().getServer();
        if (server == null || !server.isSameThread()) return false;
        EmcAmount perMb = EmcService.fluidPrice(fluid.getFluid());
        if (perMb.isZero()) {
            PrimordialMod.LOGGER.debug("[Primordial][BH] unknown fluid {}, refusing conversion", fluid.getFluid());
            return false;
        }
        be.queueEmc(perMb.multiply(fluid.getAmount()));
        com.primordial.service.ResourceKnowledge.learnMedia(server, owner,
                com.primordial.service.ResourceKnowledge.Kind.FLUID,
                net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
        return true;
    }

    /**
     * Forge Energy 接口按黑洞本 tick 剩余预算接收 FE，并把精确 EMC 分数加入待结算队列。
     * tick 尾部统一入账；simulate 请求只返回可接收量，不改变预算、余额或找零。
     */
    public static final class VoidEnergyHandler implements IEnergyStorage {
        private final BlackholeBlockEntity be;

        public VoidEnergyHandler(BlackholeBlockEntity be) { this.be = be; }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            EmcAmount price = EmcService.energyPrice(EnergyConversions.FE);
            if (price.isZero() || !available(be)) return 0;
            int accepted = Math.min(maxReceive, be.remainingEnergyBudget());
            if (accepted <= 0 || simulate) return accepted;
            MinecraftServer server = be.getLevel() == null ? null : be.getLevel().getServer();
            if (server == null) return 0;
            be.recordEnergyAccepted(accepted);
            be.queueEmc(price.multiply(accepted));
            be.puffParticles();
            return accepted;
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return PrimordialServerConfig.blackholeMaxEnergyPerTick(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return !EmcService.energyPrice(EnergyConversions.FE).isZero() && be.getOwner() != null; }
    }

    /**
     * 虚无物品槽始终为空且不可抽取，getSlotLimit 返回有限的单次接口上限。
     * insertItem 只销毁实际接收数量并返回剩余堆栈，避免 Pipez 因 EMPTY 返回值形成反馈循环。
     */
    public static class VoidItemHandler implements net.minecraftforge.items.IItemHandler {
        private final BlackholeBlockEntity be;

        public VoidItemHandler(BlackholeBlockEntity be) { this.be = be; }
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) return stack == null ? ItemStack.EMPTY : stack.copy();
            int accepted = Math.min(stack.getCount(), be.remainingItemBudget());
            if (accepted <= 0) return stack.copy();
            if (simulate) return stack.copyWithCount(stack.getCount() - accepted);
            return be.pushToBuffer(stack);
        }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) {
            return Math.min(64, PrimordialServerConfig.blackholeMaxItemCountPerTick());
        }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && available(be) && stack != null && !stack.isEmpty()
                    && !EmcService.itemRecoveryPrice(stack, be.getLevel().getServer()).isZero();
        }
    }

    /** 虚无流体槽只实现 fill，按本 tick 的 mB 预算接收并回收；所有 drain 操作都返回空。 */
    public static class VoidFluidHandler implements IFluidHandler {
        private final BlackholeBlockEntity be;
        public VoidFluidHandler(BlackholeBlockEntity be) { this.be = be; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return PrimordialServerConfig.blackholeMaxFluidMbPerTick(); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return !PrimordialServerConfig.mediaSaleDisabled(false)
                    && tank == 0 && available(be) && stack != null && !stack.isEmpty()
                    && (com.primordial.value.ValueManager.fluidMode(
                            net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid())) == null
                        || com.primordial.value.ValueManager.fluidMode(
                            net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid())).allowsSell())
                    && !EmcService.fluidPrice(stack.getFluid()).isZero();
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!isFluidValid(0, resource)) return 0;
            int accepted = Math.min(resource.getAmount(), be.remainingFluidBudget());
            if (accepted <= 0) return 0;
            if (action.simulate()) return accepted;
            FluidStack portion = resource.copy();
            portion.setAmount(accepted);
            if (!reclaimFluid(be, portion)) return 0;
            be.recordFluidAccepted(accepted);
            be.puffParticles();
            return accepted;
        }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return FluidStack.EMPTY; }
    }

    private static boolean available(BlackholeBlockEntity be) {
        return be.getOwner() != null && !be.isRemoved() && be.getLevel() != null
                && !be.getLevel().isClientSide && be.getLevel().getServer() != null;
    }
}
