/**
 * 变更记录 #4（2026-09-14）：接入通用 MachineProxy 注册表，统一按方向读取机器能力。
 * - 新增：专用代理优先、Forge capability 代理回退。
 * - 修改：配置器能力检测和任务执行器改用代理层，保留现有槽位与配置协议。
 */
/**
 * 【文件职责】维护服务端幽灵样本、机器配置、端口有效性和能量预览。
 * 【变更记录 #5】2026-09-14 00:55:10（第 5 次备注 · 1.0.0-stage5）
 * - 删除：删除长期缓存的能量 capability、重复样本写回和幽灵槽真实移动路径。
 * - 新增：canEdit/targetMachine、端口选择时隐藏库存、样本变化全量同步与大能量拆分数据入口。
 * - 修改：回填抑制写配置避免覆盖流体 NBT；样本启用跟随放置/清空；预览每 10 tick 重读已绑定能力，修正背包 shift 移动范围。
 * - 用途：数据变更仅服务端权威菜单执行；每次检查区块/机器/所有者/距离，不主动加载区块；接口读取错误只首次记录，恢复后可重新记录。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
 */
/**
 * 【变更记录 #8】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
/**
 * 【变更记录 #9】2026-09-15 12:04:51（第 9 次备注 · 1.0.0-configurator-fixes）
 * - 删除：把流体桶当作唯一显示样本、仅接受 Forge 流体容器及按图标判断空样本的路径。
 * - 新增：气体容器复制取样、IPNIgnore、权威样本回填入口和真实过滤器空值判断。
 * - 修改：JEI 包和实物取样后同步服务端幽灵槽；回填期间禁止反向改写配置；所有流体/气体图标从过滤数据绘制。
 * - 用途：修复保存后消失、无桶气体被空图标覆盖，以及气体容器无法放入两行流体样本的问题。
 */
/**
 * 【变更记录 #10】2026-09-16（资源规则客户端读取）
 * - 新增：clientResourceSample 按输入/输出方向返回六种独立资源的客户端缓存配置。
 * - 修改：第四个资源页不使用幽灵物品槽，参数控件直接读取同步后的 ri/ro 规则。
 * - 用途：服务端全量同步后，资源按钮、模式、数量、间隔和端口文字立即反映权威配置。
 */
/**
 * 【变更记录 #11】2026-09-16（资源页布局修复）
 * - 删除：能量页和独立资源页仍显示玩家背包槽位的路径；这些槽位会覆盖资源参数和底部说明。
 * - 新增：playerSlot 根据当前分页只在物品/流体页激活，并保留端口选择页的全量隐藏行为。
 * - 修改：资源页与能量页不再渲染玩家物品图标，避免文字与槽位叠加造成“歪字”或不可读界面。
 * - 用途：参数页只呈现当前页实际可操作的控件，幽灵样本仍可从物品/流体页和 JEI 投放。
 */
package com.primordial.menu;

import javax.annotation.Nullable;

import com.primordial.data.EnergyConfig;
import com.primordial.data.FluidSampleConfig;
import com.primordial.data.ItemSampleConfig;
import com.primordial.data.MachineConfig;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.data.ResourceSampleConfig;
import com.primordial.util.MachineKey;
import com.primordial.machine.MachineProxy;
import com.primordial.machine.MachineProxyRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/** 配置器菜单：36 样本槽（编辑媒介）+ 玩家背包。filter 权威数据在 MachineConfig。 */
@org.anti_ad.mc.ipn.api.IPNIgnore
public class ConfiguratorMenu extends AbstractContainerMenu {
    public static final int ITEM_IN_START = 0;    // 0-8
    public static final int ITEM_OUT_START = 9;   // 9-17
    public static final int FLUID_IN_START = 18;  // 18-26
    public static final int FLUID_OUT_START = 27; // 27-35
    public static final int SAMPLE_SLOTS = 36;
    public static final int INV_START = 36;       // 36-62 背包
    public static final int HOTBAR_START = 63;    // 63-71 热栏
    public static final int SLOT_COUNT = 72;

    /** 当前 tab：0=物品、1=流体、2=能量、3=独立资源（Screen 切换，控制 isActive）。 */
    public int activeTab = 0;
    /** 选择机器端口时隐藏库存，避免按钮与原菜单槽点击区域重叠。 */
    public boolean pickingTarget = false;

    public final String machineKey;
    public final ResourceKey<Level> dim;
    public final BlockPos pos;

    /** 服务端：权威配置引用；客户端：null（渲染用 ClientConfigCache） */
    @Nullable
    public final MachineConfig config;
    @Nullable
    private final MinecraftServer server;

    /** 服务端每帧刷新的机器能量（ContainerData 同步） */
    public int machineEnergyFE = 0;
    public int machineEnergyMax = 0;
    /** 当前机器代理标识，客户端用于显示调试信息，服务端由注册表解析。 */
    public String proxyId = "forge_capabilities";

    /** 回填显示槽时抑制写回；真实过滤数据只能由玩家编辑或经过校验的包改变。 */
    private boolean closing = false;

    private final ItemStackHandler sampleItems = new ItemStackHandler(SAMPLE_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            onSampleChanged(slot);
        }
    };

    // ---- 服务端构造 ----
    public ConfiguratorMenu(int id, Inventory inv, String machineKey) {
        super(com.primordial.registry.ModMenus.CONFIGURATOR.get(), id);
        this.machineKey = machineKey;
        this.dim = MachineKey.parseDim(machineKey);
        this.pos = MachineKey.parsePos(machineKey);
        this.server = inv.player.getServer();
        this.config = server == null ? null
                : MachineConfigSavedData.get(server).getOrCreate(machineKey, inv.player.getUUID());
        buildSlots(inv);
        if (config != null) refillFromConfig();
    }

    // ---- 客户端构造 ----
    public ConfiguratorMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(com.primordial.registry.ModMenus.CONFIGURATOR.get(), id);
        this.machineKey = buf.readUtf(256);
        this.dim = MachineKey.parseDim(machineKey);
        this.pos = MachineKey.parsePos(machineKey);
        this.server = null;
        this.config = null;
        buildSlots(inv);
    }

    private void buildSlots(Inventory inv) {
        for (int i = 0; i < SAMPLE_SLOTS; i++) {
            int x = 8 + (i % 9) * 18;
            int y = (i < 18) ? ((i < 9) ? 36 : 72) : ((i < 27) ? 36 : 72);
            addSlot(new ConfigSampleSlot(sampleItems, i, x, y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(playerSlot(inv, 9 + row * 9 + col, 8 + col * 18, 132 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(playerSlot(inv, col, 8 + col * 18, 190));
        }
        addDataSlots(new ConfigMenuData(this));
    }

    private Slot playerSlot(Inventory inventory, int index, int x, int y) {
        return new Slot(inventory, index, x, y) {
            /**
             * 物品/流体页需要玩家背包作为幽灵样本来源；能量页和独立资源页没有
             * 玩家物品操作，因此完全隐藏背包槽位，避免槽位图标覆盖参数面板和说明文字。
             */
            @Override public boolean isActive() { return !pickingTarget && activeTab < 2; }
        };
    }

    /** 服务端从权威配置回填显示槽；流体和气体由客户端直接绘制，不需要容器。 */
    public void refillFromConfig() {
        if (config == null) return;
        closing = true; // 回填仅更新幽灵图标；不能覆盖带 NBT 的流体过滤或已关闭的样本。
        try {
            for (int i = 0; i < 18; i++) {
                ItemSampleConfig s = (i < 9) ? config.itemIn[i] : config.itemOut[i - 9];
                sampleItems.setStackInSlot(i, s.filter.copyWithCount(1));
            }
            clearFluidIcons(FLUID_IN_START);
            clearFluidIcons(FLUID_OUT_START);
        } finally { closing = false; }
    }

    private void clearFluidIcons(int start) {
        for (int i = 0; i < 9; i++) {
            sampleItems.setStackInSlot(start + i, ItemStack.EMPTY);
            // 流体/气体统一由 FluidSampleRenderer 绘制；桶图标会遮挡原生材质并误导为空桶物品。
        }
    }

    /** 样本槽内容变化（服务端）→ 写回 config filter。输出样本放入即启用，清空即停用 */
    private void onSampleChanged(int slot) {
        if (closing || config == null) return;
        ItemStack stack = sampleItems.getStackInSlot(slot);
        if (slot < 18) {
            ItemSampleConfig s = (slot < 9) ? config.itemIn[slot] : config.itemOut[slot - 9];
            s.filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            s.enabled = !stack.isEmpty();
        } else {
            FluidSampleConfig s = (slot < 27) ? config.fluidIn[slot - 18] : config.fluidOut[slot - 27];
            if (!com.primordial.compat.MekanismGasCompat.sampleContainer(s, stack)) {
                s.filter = extractFluid(stack);
                s.gasFilter = null;
                s.enabled = !s.filter.isEmpty();
            }
        }
        refillFromConfig();
        MachineConfigSavedData.get(server).setDirty();
        ServerPlayer editor = config.owner == null ? null : server.getPlayerList().getPlayer(config.owner);
        if (editor != null && editor.containerMenu == this) {
            com.primordial.network.PrimordialNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> editor),
                    new com.primordial.network.S2CFullConfigSyncPacket(machineKey, config.save()));
        }
    }

    static FluidStack extractFluid(ItemStack container) {
        if (container.isEmpty()) return FluidStack.EMPTY;
        return container.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                .map(h -> h.getTanks() == 0 ? FluidStack.EMPTY : h.getFluidInTank(0).copy())
                .orElse(FluidStack.EMPTY);
    }

    /** 关 GUI：样本槽是幽灵物品（仅过滤数据），配置已保存，无需退还 */
    @Override
    public void removed(Player player) {
        super.removed(player);
    }

    /** 服务端预览绑定目标的物品、流体和能量状态；读取失败只影响当前页面，不修改机器配置。 */
    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (!player.level().isClientSide && !canEdit(player)) return;
        if (slotId >= 0 && slotId < SAMPLE_SLOTS) {
            if (type == ClickType.PICKUP && button == 0) {
                ItemStack cursor = getCarried();
                if (!cursor.isEmpty() && slots.get(slotId).mayPlace(cursor)) {
                    sampleItems.setStackInSlot(slotId, cursor.copyWithCount(1));
                }
                return;
            }
            if (type == ClickType.PICKUP && button == 1) {
                sampleItems.setStackInSlot(slotId, ItemStack.EMPTY);
                return;
            }
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    /** 接收 JEI Ghost Ingredient 时只更新当前样本过滤器，并通过网络包让服务端校验所有者和槽位范围。 */
    public void refreshClientGhostSlots() {
        if (server != null) return;
        closing = true;
        var c = com.primordial.client.ClientConfigCache.matches(machineKey)
                ? com.primordial.client.ClientConfigCache.get() : null;
        if (c != null) {
            for (int i = 0; i < 9; i++) {
                sampleItems.setStackInSlot(i, c.itemIn[i].filter.copyWithCount(1));
                sampleItems.setStackInSlot(9 + i, c.itemOut[i].filter.copyWithCount(1));
            }
            clearFluidIcons(18);
            clearFluidIcons(27);
        }
        closing = false;
    }

    public boolean stillValid(Player player) {
        if (player.level().dimension() != dim) return false;
        if (!player.level().isClientSide && (config == null || config.owner == null
                || !config.owner.equals(player.getUUID()) || targetMachine() == null)) return false;
        double d = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return d < 64.0;
    }

    public boolean canEdit(Player player) {
        return config != null && config.owner != null && config.owner.equals(player.getUUID())
                && player.containerMenu == this && stillValid(player);
    }

    /** 区块卸载或机器移除后返回 null，不加载区块，也不继续使用旧 capability。 */
    @Nullable
    public BlockEntity targetMachine() {
        if (server == null) return null;
        Level level = server.getLevel(dim);
        if (level == null || !level.isLoaded(pos)) return null;
        BlockEntity machine = level.getBlockEntity(pos);
        return machine == null || machine.isRemoved() ? null : machine;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || (!player.level().isClientSide && !canEdit(player))) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        if (index < SAMPLE_SLOTS) {
            return ItemStack.EMPTY; // 幽灵槽：无真实物品可移
        }
        // shift 自背包 → 幽灵放入空样本槽（不消耗物品）
        boolean isFluidContainer = !extractFluid(stack).isEmpty() || com.primordial.compat.MekanismGasCompat.hasGas(stack);
        int start = isFluidContainer ? FLUID_IN_START : ITEM_IN_START;
        for (int i = start; i < start + 9; i++) {
            // 气体和无桶流体可能没有 ItemStack 图标，空槽必须根据权威过滤数据判断。
            if (isSampleEmpty(i)) {
                ItemStack sample = stack.copy();
                sample.setCount(1);
                sampleItems.setStackInSlot(i, sample);
                return ItemStack.EMPTY; // 幽灵放入，不消耗背包物品
            }
        }
        // 无空样本槽 → 背包内移动
        ItemStack original = stack.copy();
        if (index < HOTBAR_START ? !moveItemStackTo(stack, HOTBAR_START, SLOT_COUNT, false)
                : !moveItemStackTo(stack, INV_START, HOTBAR_START, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** 服务端每帧同步机器能量（broadcastChanges 触发） */
    @Override
    public void broadcastChanges() {
        try {
            refreshEnergyData();
        } catch (RuntimeException | LinkageError failure) {
            machineEnergyFE = machineEnergyMax = 0;
            if (!energyReadFailed) com.primordial.PrimordialMod.LOGGER.warn(
                    "[Primordial] Cannot read configured energy port at {}", machineKey, failure);
            energyReadFailed = true;
        }
        super.broadcastChanges();
    }

    private boolean energyReadFailed;

    private void refreshEnergyData() {
        if (server == null || server.getTickCount() % 10 != 0) return;
        BlockEntity machine = targetMachine();
        IEnergyStorage energy = null;
        if (machine != null) {
            MachineProxy proxy = MachineProxyRegistry.resolve(machine);
            proxyId = proxy.id();
            energy = com.primordial.machine.MachineTargetAccess.energy(proxy, machine, config.energy.supplyPort);
            if (energy == null) energy = com.primordial.machine.MachineTargetAccess.energy(proxy, machine, config.energy.recoverPort);
        }
        machineEnergyFE = energy == null ? 0 : energy.getEnergyStored();
        machineEnergyMax = energy == null ? 0 : energy.getMaxEnergyStored();
        energyReadFailed = false;
    }

    /** 样本槽：mayPlace 按区校验，isActive 按 tab */
    public class ConfigSampleSlot extends SlotItemHandler {
        public ConfigSampleSlot(ItemStackHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (getContainerSlot() >= FLUID_IN_START) {
                FluidStack fluid = extractFluid(stack);
                return !fluid.isEmpty() || com.primordial.compat.MekanismGasCompat.hasGas(stack);
            }
            return true;
        }

        @Override public boolean mayPickup(Player player) { return false; }

        @Override
        public boolean isActive() {
            if (pickingTarget) return false;
            int idx = getContainerSlot();
            if (idx < FLUID_IN_START) return activeTab == 0;
            return activeTab == 1;
        }
    }

    public ItemStack getSampleItem(int index) {
        return sampleItems.getStackInSlot(index);
    }

    private boolean isSampleEmpty(int index) {
        MachineConfig c = config != null ? config : clientConfig();
        if (c == null) return false;
        if (index < 18) return (index < 9 ? c.itemIn[index] : c.itemOut[index - 9]).filter.isEmpty();
        var sample = index < 27 ? c.fluidIn[index - 18] : c.fluidOut[index - 27];
        return sample.filter.isEmpty() && !sample.hasGasFilter();
    }

    /** 客户端渲染辅助：当前 tab 对应的样本配置 */
    @Nullable
    public ItemSampleConfig clientItemSample(int index) {
        MachineConfig c = clientConfig();
        if (c == null) return null;
        return (index < 9) ? c.itemIn[index] : c.itemOut[index - 9];
    }

    @Nullable
    public FluidSampleConfig clientFluidSample(int index) {
        MachineConfig c = clientConfig();
        if (c == null) return null;
        return (index < 9) ? c.fluidIn[index] : c.fluidOut[index - 9];
    }

    /**
     * 客户端读取独立资源规则。index 0-5 对应 ResourceExchangeConversions 的稳定单位顺序；
     * input 参数选择输入或输出数组，保证屏幕修改的规则与服务端任务读取同一条配置。
     */
    @Nullable
    public ResourceSampleConfig clientResourceSample(boolean input, int index) {
        MachineConfig c = clientConfig();
        if (c == null || index < 0 || index >= c.resourceIn.length) return null;
        return input ? c.resourceIn[index] : c.resourceOut[index];
    }

    @Nullable
    public EnergyConfig clientEnergy() {
        MachineConfig c = clientConfig();
        return c == null ? null : c.energy;
    }

    /** 主开关：which 0=物品输入 1=物品输出 2=流体输入 3=流体输出 */
    public boolean clientMaster(int which) {
        MachineConfig c = clientConfig();
        if (c == null) return false;
        return switch (which) {
            case 0 -> c.itemInEnabled;
            case 1 -> c.itemOutEnabled;
            case 2 -> c.fluidInEnabled;
            default -> c.fluidOutEnabled;
        };
    }

    @Nullable
    private MachineConfig clientConfig() {
        return com.primordial.client.ClientConfigCache.matches(machineKey)
                ? com.primordial.client.ClientConfigCache.get() : null;
    }
}
