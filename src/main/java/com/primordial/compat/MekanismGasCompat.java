/**
 * 【文件职责】隔离 Mekanism 化学气体 API，向主模组提供不包含 Mekanism 类型的安全入口。
 *
 * 【变更记录 #4】2026-09-16（可选依赖类加载修复）
 * - 删除：主任务、机器端口页、流体样本和价格界面直接引用 GasStack、IGasHandler、ChemicalStack 的路径。
 * - 新增：气体样本 NBT 读写、端口查询、端口快照、输入/输出结算、渲染信息和网络转发的统一兼容入口。
 * - 修改：Mekanism 类型全部移入 Api 内部类；外层公开方法只使用 Object、Minecraft、Forge 和本模组类型，且每个入口在 loaded() 为 false 时立即返回空结果。
 * - 用途：未安装 Mekanism 时，TaskScheduler、配置器、价格编辑页和普通流体规则仍可正常加载；安装后继续使用原生气体接口。
 *
 * 输入边界：气体对象只能来自 JEI、Mekanism capability 或本兼容层的 NBT 读取；服务器端会再次校验注册名和菜单权限。
 * 输出边界：端口输入/输出返回实际移动量，模拟调用不扣 EMC；所有价格结算委托 EmcService，未知气体不会被抽取。
 */
/**
 * 【文件职责】隔离可选气体 API 并执行气体传输。
 * 【变更记录 #5】2026-09-16 19:41:50（第 5 次备注 · 1.0.0-review-ready）
 * - 删除：移除气体供给无凭证即可扣费输出的路径。
 * - 新增：外层supply在访问可选API前校验气体凭证。
 * - 修改：气体回收保持独立于知识门禁；外层不暴露任何Mekanism类型。
 * - 用途：未学凭证不扣EMC，缺少Mekanism仍不解析第三方类型。
 */
/**
 * 【变更记录 #6】2026-10-02 16:08:00（第 6 次备注 · 1.2.0-media-learning）
 * - 删除：气体购买只认类别凭证、回收不学习具体气体的限制。
 * - 新增：购买按气体注册名查知识，实际抽取后记录该气体。
 * - 修改：可选 Mekanism API 仍只在 Api 内解析；零价或零抽取不学习。
 * - 用途：气体逐种卖入解锁，气体凭证继续解锁整类。
 */
/**
 * 【变更记录 #7】2026-10-02 16:24:00（第 7 次备注 · 1.2.0-transmutation-media-input）
 * - 删除：无。
 * - 新增：转化桌光标气体容器的服务端卖入入口。
 * - 修改：仅实际抽出的有价气体入账并学习；可选 API 仍隔离在 Api 内。
 * - 用途：不安装 Mekanism 时入口安全返回，安装后逐种学习气体。
 */
/**
 * 【变更记录 #8】2026-10-02 16:50:00（第 8 次备注 · 1.2.0-transmutation-media-output）
 * - 删除：无。
 * - 新增：已装气体容器的服务端补充入口。
 * - 修改：按逐种知识门禁、模拟可接收量与真实插入量扣除 EMC。
 * - 用途：缺少 Mekanism 时安全跳过；安装后仅补充已选同种气体。
 */
/**
 * 【变更记录 #9】2026-10-02 17:05:00（第 9 次备注 · 1.2.0-transmutation-media-selection）
 * - 删除：无。
 * - 新增：按服务端气体注册名选择并填充光标空容器。
 * - 修改：可选 API 继续封闭在 Api 内；已装其它气体、零价和未学习气体均不成交。
 * - 用途：气体也可从已学习种类直接购买，成交量由 Mekanism 真实插入结果决定。
 */
/**
 * 【变更记录 #10】2026-10-02 17:18:00（第 10 次备注 · 1.2.0-media-catalog）
 * - 删除：无。
 * - 新增：不暴露可选 API 类型的已注册气体 ID 快照。
 * - 修改：缺少 Mekanism 时返回空集合。
 * - 用途：整类凭证解锁后可分页浏览全部可定价气体。
 */
/**
 * 【变更记录 #11】2026-10-02 18:42:44（第 11 次备注 · 1.2.0-gas-sale-remainder）
 * - 删除：卖入时直接引用储罐内部可变气体栈的处理。
 * - 新增：抽取之前保存独立气体快照，用于价格、抽取类型核对和逐种学习。
 * - 修改：最后一批被抽空时仍按原始气体身份结算并保存空容器，不被已变空的内部栈误判为零成交。
 * - 用途：修复终极储罐残留 512000 mB 和小于单次限额的尾量卖不掉；保留能力接口限流和实际抽取量计费。
 */
/**
 * 【变更记录 #12】2026-10-02 18:54:32（第 12 次备注 · 1.2.0-media-trade-switches）
 * - 删除：无。
 * - 新增：手持气体买卖及机器供应/回收的方向开关检查。
 * - 修改：禁用时在读取可选 API 和能力之前返回，保留价格和知识。
 * - 用途：配置不能被空容器购买、补满已有气体或自动任务绕过。
 */
/** 【变更记录 #13】2026-10-08 03:32:46（1.2.0-media-price-modes）
 * 删除：无。新增：机器气体供给/回收按价格方向状态校验。修改：仅买、仅卖在手持容器和机器端口共用门禁。用途：防止气体自动化入口绕过管理员交易方向。 */
/** 【变更记录 #14】2026-10-10 01:55:50（第 14 次备注 · 1.2.0-gas-blacklist-tags）
 * - 删除：无。新增：不暴露 Mekanism 类型的气体标签查询。
 * - 修改：仅安装 Mekanism 时读取注册气体的实时标签；未知气体和缺失标签不匹配。
 * - 用途：为统一黑名单提供标签匹配，避免可选 API 进入公共配置类。 */
/**
 * 【变更记录 #15】2026-10-10 03:53:52（第 15 次备注 · 1.2.0-resource-system-one）
 * - 删除：兼容类内部三份重复的光标容器气体买卖实现与机器回收重复学习。
 * - 新增：隔离的 CarriedGasTrade 实现及容器提交回调；旧公开入口保留行为。
 * - 修改：容器和机器统一调用 EmcService 气体入口，抽空尾批仍使用复制的气体身份。
 * - 用途：保证缺少 Mekanism 时不会解析新 API 类，同时让堆叠容器、学习、结算和反馈组成完整系统。
 */
/**
 * 【变更记录 #16】2026-10-13 01:53:18（第 16 次备注 · 1.2.0-media-feedback）
 * - 删除：转化桌流体只能使用自动化能力限速的限制；新增安装检查后的手动储罐桥接入口。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
/**
 * 【变更记录 #17】2026-10-13 19:40:05（第 17 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：气体自动补充先检查 SUPPLY，内部真实交付调用 supplyGas；第三方类型继续隔离在原兼容边界。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #18】2026-10-13 20:14:16（第 18 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
package com.primordial.compat;

import com.primordial.blockentity.BlackholeBlockEntity;
import com.primordial.data.FluidSampleConfig;
import com.primordial.data.SampleMode;
import com.primordial.machine.MachinePortPage;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

public final class MekanismGasCompat {
    private MekanismGasCompat() { }

    public static boolean loaded() {
        return ModList.get() != null && ModList.get().isLoaded("mekanism");
    }

    public static int manualFluid(net.minecraftforge.fluids.capability.IFluidHandler handler,
            net.minecraftforge.fluids.FluidStack sample, boolean buy,
            net.minecraftforge.fluids.capability.IFluidHandler.FluidAction action) {
        return loaded() ? MekanismManualMedia.fluid(handler, sample, buy, action) : -1;
    }

    public static boolean isGas(Object ingredient) { return loaded() && Api.isGas(ingredient); }
    public static boolean hasGas(ItemStack stack) { return loaded() && Api.sample(stack) != null; }
    public static boolean sellCarriedGas(net.minecraft.server.level.ServerPlayer player, ItemStack copy) {
        if (com.primordial.config.PrimordialServerConfig.mediaSaleDisabled(true)) return false;
        return legacyCarriedTrade(player, copy, false, null);
    }
    public static boolean buyCarriedGas(net.minecraft.server.level.ServerPlayer player, ItemStack copy) {
        if (com.primordial.config.PrimordialServerConfig.mediaPurchaseDisabled(true)) return false;
        return legacyCarriedTrade(player, copy, true, null);
    }
    public static boolean buySelectedCarriedGas(net.minecraft.server.level.ServerPlayer player, ItemStack copy,
            ResourceLocation id) {
        if (com.primordial.config.PrimordialServerConfig.mediaPurchaseDisabled(true)) return false;
        return id != null && legacyCarriedTrade(player, copy, true, id);
    }
    /** 容器实现仅在 loaded() 后链接，容器交付由统一副本事务完成。 */
    public static com.primordial.service.ResourceTradeResult tradeCarriedGas(
            net.minecraft.server.level.ServerPlayer player, ItemStack copy, boolean buy,
            ResourceLocation selected, java.util.function.Predicate<ItemStack> commit) {
        if (!loaded()) return com.primordial.service.ResourceTradeResult.failure(
                com.primordial.service.ResourceTradeResult.Status.NO_CONTAINER);
        return CarriedGasTrade.trade(player, copy, buy, selected, commit);
    }

    private static boolean legacyCarriedTrade(net.minecraft.server.level.ServerPlayer player, ItemStack copy,
                                             boolean buy, ResourceLocation selected) {
        return tradeCarriedGas(player, copy, buy, selected, result -> {
            player.containerMenu.setCarried(result);
            player.containerMenu.broadcastChanges();
            return true;
        }).traded();
    }
    public static boolean sampleContainer(FluidSampleConfig sample, ItemStack stack) {
        return loaded() && Api.sampleContainer(sample, stack);
    }
    public static boolean validGas(ResourceLocation id) { return loaded() && Api.validGas(id); }
    public static boolean gasInTag(ResourceLocation id, ResourceLocation tag) {
        if (id == null || tag == null || !loaded()) return false;
        try { return Api.gasInTag(id, tag); }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    public static java.util.Set<ResourceLocation> gasIds() { return loaded() ? Api.gasIds() : java.util.Set.of(); }
    public static Object preview(ResourceLocation id) { return loaded() ? Api.preview(id) : null; }
    public static void sendFilter(int containerId, int section, int slot, Object ingredient) {
        if (isGas(ingredient)) Api.sendFilter(containerId, section, slot, ingredient);
    }
    public static boolean isGasCapability(Capability<?> capability) {
        return loaded() && Api.isGasCapability(capability);
    }
    public static LazyOptional<?> blackholeCapability(BlackholeBlockEntity be) {
        return loaded() ? Api.blackholeCapability(be) : LazyOptional.empty();
    }

    public static CompoundTag writeGas(Object gas) {
        return loaded() && isGas(gas) ? Api.writeGas(gas) : null;
    }
    public static Object readGas(CompoundTag tag) {
        return loaded() && tag != null ? Api.readGas(tag) : null;
    }
    public static boolean gasEmpty(Object gas) { return !isGas(gas) || Api.empty(gas); }
    public static ResourceLocation gasId(Object gas) { return isGas(gas) ? Api.id(gas) : null; }
    public static Object copyGas(Object gas, long amount) { return isGas(gas) ? Api.copy(gas, amount) : null; }
    public static boolean sameGasType(Object left, Object right) {
        return isGas(left) && isGas(right) && Api.sameType(left, right);
    }
    public static long gasAmount(Object gas) { return isGas(gas) ? Api.amount(gas) : 0L; }
    public static ResourceLocation gasIcon(Object gas) { return isGas(gas) ? Api.icon(gas) : null; }
    public static int gasColor(Object gas) { return isGas(gas) ? Api.color(gas) : 0xFFFFFFFF; }
    public static Component gasName(Object gas) { return isGas(gas) ? Api.name(gas) : Component.empty(); }

    /** 按目标 key 返回绑定的 Mekanism 原生气体 handler，返回类型不暴露第三方 API。 */
    public static Object boundHandler(BlockEntity machine, Direction side, int index, String key) {
        return loaded() ? Api.boundHandler(machine, side, index, key) : null;
    }
    public static Object handler(BlockEntity machine, Direction side) {
        return loaded() ? Api.handler(machine, side) : null;
    }
    public static int tankCount(Object handler) { return isHandler(handler) ? Api.tankCount(handler) : 0; }
    public static Object tank(Object handler, int index) { return isHandler(handler) ? Api.tank(handler, index) : null; }
    public static long tankCapacity(Object handler, int index) {
        return isHandler(handler) ? Api.tankCapacity(handler, index) : 0L;
    }
    public static String tankKey(BlockEntity machine, Direction side, Object handler, int index) {
        return isHandler(handler) ? Api.tankKey(machine, side, handler, index) : "";
    }
    public static List<MachinePortPage.Port> pagePorts(BlockEntity machine, Direction side, int start, int end) {
        return loaded() ? Api.pagePorts(machine, side, start, end) : List.of();
    }

    /** 输入气体并按实际插入量结算 EMC。 */
    public static long supply(MinecraftServer server, UUID owner, FluidSampleConfig sample,
            Object handler, int index) {
        if (com.primordial.config.PrimordialServerConfig.mediaPurchaseDisabled(true)) return 0L;
        if (sample == null || !com.primordial.service.ResourceKnowledge.allowsMedia(server, owner,
                com.primordial.service.ResourceKnowledge.Kind.GAS, gasId(sample.gasFilter))) return 0L;
        return loaded() && isHandler(handler) ? Api.supply(server, owner, sample, handler, index) : 0L;
    }

    /** 抽取气体并按实际抽取量回收 EMC。 */
    public static long reclaim(MinecraftServer server, UUID owner, FluidSampleConfig sample,
            Object handler, int index) {
        if (com.primordial.config.PrimordialServerConfig.mediaSaleDisabled(true)) return 0L;
        return loaded() && isHandler(handler) ? Api.reclaim(server, owner, sample, handler, index) : 0L;
    }

    private static boolean isHandler(Object value) {
        return loaded() && value != null && Api.isHandler(value);
    }

    /** 只有本嵌套类的方法体可以解析 Mekanism；未安装时不会执行或初始化它。 */
    private static final class Api {
        static boolean gasInTag(ResourceLocation id, ResourceLocation tag) {
            return validGas(id) && mekanism.api.MekanismAPI.gasRegistry().getValue(id)
                    .getTags().anyMatch(key -> key.location().equals(tag));
        }
        static java.util.Set<ResourceLocation> gasIds() {
            return java.util.Set.copyOf(mekanism.api.MekanismAPI.gasRegistry().getKeys());
        }
        static boolean isGasCapability(Capability<?> capability) {
            return capability == mekanism.common.capabilities.Capabilities.GAS_HANDLER;
        }
        static LazyOptional<?> blackholeCapability(BlackholeBlockEntity be) {
            return LazyOptional.of(() -> new com.primordial.blockentity.MekanismGasSink(be));
        }
        static boolean isGas(Object value) {
            return value instanceof mekanism.api.chemical.gas.GasStack;
        }
        static boolean isHandler(Object value) {
            return value instanceof mekanism.api.chemical.gas.IGasHandler;
        }
        static Object sample(ItemStack stack) {
            if (stack.isEmpty()) return null;
            var handler = stack.getCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER).orElse(null);
            if (handler != null) for (int i = 0; i < handler.getTanks(); i++) {
                var gas = handler.getChemicalInTank(i);
                if (!gas.isEmpty()) return new mekanism.api.chemical.gas.GasStack(gas, 1);
            }
            return null;
        }
        static boolean sampleContainer(FluidSampleConfig target, ItemStack stack) {
            Object gas = sample(stack);
            if (gas == null) return false;
            target.filter = net.minecraftforge.fluids.FluidStack.EMPTY;
            target.gasFilter = gas;
            target.enabled = true;
            return true;
        }
        static boolean validGas(ResourceLocation id) {
            return id != null && mekanism.api.MekanismAPI.gasRegistry().containsKey(id)
                    && !mekanism.api.MekanismAPI.gasRegistry().getValue(id).isEmptyType();
        }
        static Object preview(ResourceLocation id) {
            return validGas(id)
                    ? new mekanism.api.chemical.gas.GasStack(mekanism.api.MekanismAPI.gasRegistry().getValue(id), 1)
                    : null;
        }
        static void sendFilter(int id, int section, int slot, Object value) {
            com.primordial.network.PrimordialNetwork.CHANNEL.sendToServer(
                    new com.primordial.network.C2SSetGasFilterPacket(id, section, slot,
                            (mekanism.api.chemical.gas.GasStack) value));
        }
        static CompoundTag writeGas(Object value) {
            return ((mekanism.api.chemical.gas.GasStack) value).write(new CompoundTag());
        }
        static Object readGas(CompoundTag tag) {
            var value = mekanism.api.chemical.gas.GasStack.readFromNBT(tag);
            return value.isEmpty() ? null : value;
        }
        static boolean empty(Object value) {
            return ((mekanism.api.chemical.gas.GasStack) value).isEmpty();
        }
        static ResourceLocation id(Object value) {
            return ((mekanism.api.chemical.gas.GasStack) value).getTypeRegistryName();
        }
        static Object copy(Object value, long amount) {
            return new mekanism.api.chemical.gas.GasStack(
                    (mekanism.api.chemical.gas.GasStack) value, Math.max(0L, amount));
        }
        static boolean sameType(Object left, Object right) {
            return ((mekanism.api.chemical.gas.GasStack) left).isTypeEqual(
                    (mekanism.api.chemical.gas.GasStack) right);
        }
        static long amount(Object value) {
            return ((mekanism.api.chemical.gas.GasStack) value).getAmount();
        }
        static ResourceLocation icon(Object value) {
            return ((mekanism.api.chemical.gas.GasStack) value).getType().getIcon();
        }
        static int color(Object value) {
            return ((mekanism.api.chemical.gas.GasStack) value).getChemicalColorRepresentation();
        }
        static Component name(Object value) {
            return ((mekanism.api.chemical.gas.GasStack) value).getTextComponent();
        }
        static Object boundHandler(BlockEntity machine, Direction side, int index, String key) {
            if (machine == null || machine.isRemoved() || index < 0) return null;
            var handler = handler(machine, side);
            if (handler == null || index >= handler.getTanks()) return null;
            String actual = com.primordial.machine.adapters.MekanismProxy.INSTANCE
                    .gasTankKey(machine, side, handler, index);
            return actual.equals(key) ? handler : null;
        }
        static mekanism.api.chemical.gas.IGasHandler handler(BlockEntity machine, Direction side) {
            if (machine == null || machine.isRemoved()) return null;
            return com.primordial.machine.adapters.MekanismProxy.INSTANCE.gasHandler(machine, side);
        }
        static int tankCount(Object handler) {
            return ((mekanism.api.chemical.gas.IGasHandler) handler).getTanks();
        }
        static Object tank(Object handler, int index) {
            var typed = (mekanism.api.chemical.gas.IGasHandler) handler;
            return index >= 0 && index < typed.getTanks() ? typed.getChemicalInTank(index) : null;
        }
        static long tankCapacity(Object handler, int index) {
            var typed = (mekanism.api.chemical.gas.IGasHandler) handler;
            return index >= 0 && index < typed.getTanks() ? typed.getTankCapacity(index) : 0L;
        }
        static String tankKey(BlockEntity machine, Direction side, Object handler, int index) {
            return com.primordial.machine.adapters.MekanismProxy.INSTANCE.gasTankKey(machine, side,
                    (mekanism.api.chemical.gas.IGasHandler) handler, index);
        }
        static List<MachinePortPage.Port> pagePorts(BlockEntity machine, Direction side, int start, int end) {
            var handler = handler(machine, side);
            if (handler == null) return List.of();
            int safeStart = Math.max(0, start);
            int safeEnd = Math.min(Math.max(safeStart, end), handler.getTanks());
            List<MachinePortPage.Port> result = new ArrayList<>();
            for (int index = safeStart; index < safeEnd; index++) {
                var gas = handler.getChemicalInTank(index);
                String key = tankKey(machine, side, handler, index);
                result.add(new MachinePortPage.Port(index, key, ItemStack.EMPTY,
                        net.minecraftforge.fluids.FluidStack.EMPTY,
                        (int) Math.min(Integer.MAX_VALUE, Math.max(0L, gas.getAmount())),
                        (int) Math.min(Integer.MAX_VALUE, Math.max(0L, handler.getTankCapacity(index))), true));
            }
            return List.copyOf(result);
        }
        static long supply(MinecraftServer server, UUID owner, FluidSampleConfig sample,
                Object handler, int index) {
            var typed = (mekanism.api.chemical.gas.IGasHandler) handler;
            var filter = (mekanism.api.chemical.gas.GasStack) sample.gasFilter;
            var buyMode = com.primordial.value.ValueManager.gasMode(filter.getTypeRegistryName());
            if (buyMode != null && !buyMode.allowsBuy()) return 0L;
            long want = sample.mode == SampleMode.KEEP_LEVEL ? Long.MAX_VALUE : Math.max(1L, sample.threshold);
            long free = Math.max(0L, typed.getTankCapacity(index) - typed.getChemicalInTank(index).getAmount());
            long capacity = Math.min(want, free);
            if (capacity <= 0 || !typed.isValid(index, new mekanism.api.chemical.gas.GasStack(filter, capacity))) return 0L;
            var probe = new mekanism.api.chemical.gas.GasStack(filter, capacity);
            long amount = capacity - typed.insertChemical(index, probe, mekanism.api.Action.SIMULATE).getAmount();
            if (amount <= 0) return 0L;
            EmcAmount per = EmcService.gasPrice(filter.getTypeRegistryName());
            if (per.isZero()) return 0L;
            return EmcService.purchaseGas(server, owner, filter.getTypeRegistryName(), amount, units -> {
                var inserted = typed.insertChemical(index,
                        new mekanism.api.chemical.gas.GasStack(filter, units), mekanism.api.Action.EXECUTE);
                return units - inserted.getAmount();
            });
        }
        static long reclaim(MinecraftServer server, UUID owner, FluidSampleConfig sample,
                Object handler, int index) {
            var typed = (mekanism.api.chemical.gas.IGasHandler) handler;
            var filter = (mekanism.api.chemical.gas.GasStack) sample.gasFilter;
            var sellMode = com.primordial.value.ValueManager.gasMode(filter.getTypeRegistryName());
            if (sellMode != null && !sellMode.allowsSell()) return 0L;
            EmcAmount per = EmcService.gasPrice(filter.getTypeRegistryName());
            if (per.isZero() || !typed.getChemicalInTank(index).isTypeEqual(filter)) return 0L;
            long available = typed.getChemicalInTank(index).getAmount();
            long amount = sample.mode == SampleMode.KEEP_LEVEL ? available : Math.min(available, Math.max(1L, sample.threshold));
            if (amount <= 0) return 0L;
            long actual = EmcService.reclaimGas(server, owner, filter.getTypeRegistryName(), amount, units -> {
                var got = typed.extractChemical(index, units, mekanism.api.Action.EXECUTE);
                return !got.isEmpty() && got.isTypeEqual(filter) ? got.getAmount() : 0L;
            });
            return actual;
        }
    }
}
