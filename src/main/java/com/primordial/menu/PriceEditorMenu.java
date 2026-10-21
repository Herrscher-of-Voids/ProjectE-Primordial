/**
 * 【变更记录 #3】2026-09-16（流体、气体价格编辑与 JEI 样本）
 * - 删除：价格编辑页只能保存 ItemStack、无法选择无桶流体或 Mekanism 气体的限制。
 * - 新增：物品、Forge 流体和气体三种样本状态，服务端通过注册名校验客户端选择；幽灵样本从不消耗玩家物品。
 * - 修改：普通背包点击和 JEI 投放共用同一选择入口，清空样本时同时清除介质类型，避免旧类型残留。
 * - 用途：让配置器空气潜行右键打开的价格页可以修改任意流体/气体的 EMC 单价，并保持菜单状态受服务端包验证。
 */
/**
 * 【文件职责】承载独立定价器打开的单物品自定义价格编辑页。
 * 【变更记录 #1】2026-09-14 12:45:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：一个幽灵物品槽和玩家背包槽位，物品只复制不消耗。
 * - 修改：价格编辑从机器配置菜单移出，避免右键机器时出现自定义价格控件。
 * - 用途：客户端提交物品 ID/价格，服务端仍通过 C2SPlayerActionPacket 写入价值表。
 */
/**
 * 【变更记录 #2】2026-09-15 12:04:51（第 2 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：Inventory Profiles Next 官方 IPNIgnore 注解。
 * - 修改：将价格编辑菜单排除出 IPN 整理范围。
 * - 用途：保护自定义价格样本槽；不改变现有物品价格操作。
 */
/**
 * 【变更记录 #4】2026-09-16 01:24:55（第 4 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：样本槽允许原版拖拽放入实物，以及空手左击误清样本的路径。
 * - 新增：服务器按每件物品/每 mB 介质报价并返回编辑权限；切换样本使旧报价失效，拒收迟到回包。
 * - 修改：背包向下移动，为当前价格和保存结果预留独立行；左击取样、右击清空，样本不消耗实物。
 * - 用途：PriceEditorScreen、JEI 目标包和价格提交包共享同一目标身份与权威报价，防止只显示输入值却未保存。
 */
/**
 * 【变更记录 #5】2026-09-16 01:35:10（第 5 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：无。
 * - 新增：记录上次发送的价格修订号，外部命令/其他管理员修改价格后刷新当前菜单。
 * - 修改：报价查询只在目标、权限或 ValueManager 修订号改变时执行，发送后记住同一修订号。
 * - 用途：保持打开页面的报价与实际结算一致，正常静止菜单不会逐 tick 估价和发包。
 */
/**
 * 【变更记录 #6】2026-09-16 01:52:17（第 6 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：物品样本 NBT 附加价值作为基础价格输入的回显。
 * - 新增：直接读取 ProjectE 基础价，与 custom_emc.json 中编辑的物品注册名对应。
 * - 修改：流体/气体仍按每 mB 报价，物品的储能/耐久等动态价格由 ProjectE 自行处理。
 * - 用途：输入框编辑基础 EMC，实际转化与 Tooltip 按 ProjectE 同一规则计算，不形成独立覆盖价。
 */
/**
 * 【变更记录 #7】2026-10-02 15:15:00（第 7 次备注 · 1.2.0-fluid-price-unit-tests）
 * - 删除：将流体报价描述为每 mB 输入的旧注释。
 * - 新增：明确价格页按 EMC/1000mB 回显，与流体价表单位一致。
 * - 修改：只修正文档，不改网络、存储或结算逻辑；结算服务仍精确除以 1000。
 * - 用途：避免维护者把编辑报价和每 mB 扣款单价混淆。
 */
/** 【变更记录 #8】2026-10-08 03:45:00（1.2.0-media-price-modes）
 * 删除：无。新增：价格状态快照。修改：流体和气体回包同时携带数值与模式。用途：客户端显示服务端权威价格状态。 */
/**
 * 【变更记录 #9】2026-10-10 02:33:01（第 9 次备注 · 1.2.0-item-price-inherit）
 * - 删除：物品价格状态不携带模式、无法区分人工价格与继承默认值的回显。
 * - 新增：从 ProjectE 当前人工基础价映射物品状态为 INHERIT_NATIVE、DISABLED 或 CUSTOM_VALUE。
 * - 修改：状态仍在既有价格快照中发送，不改变报价来源或编辑权限。
 * - 用途：管理员在物品价格页能确认恢复默认计价是否生效；零价继续明确显示为禁用。
 */
/**
 * 【变更记录 #10】2026-10-10 02:49:08（第 10 次备注 · 1.2.0-dedicated-price-editor）
 * - 删除：无；菜单槽位、价格快照和网络状态逻辑保持。
 * - 新增：用途说明更新为独立定价器入口；该工具调用方在服务端打开菜单前验证管理员等级。
 * - 修改：无；价格编辑能力和既有服务端逐包权限校验不变。
 * - 用途：该菜单继续承载 Item、Fluid 和 Gas 的管理价格编辑状态，与 ConfiguratorItem 解耦。
 */
/**
 * 【变更记录 #11】2026-10-10 03:15:39（第 11 次备注 · 1.2.0-price-mode-controls）
 * - 删除：无；价格样本与玩家库存槽位身份保持。
 * - 新增：共享 GUI_HEIGHT=228 布局常量，供 PriceEditorScreen 与菜单使用。
 * - 修改：玩家库存与快捷栏整体下移 24 像素，顶部为状态行和明确价格操作按钮预留区域。
 * - 用途：避免新增定价模式控件与实物背包槽位重叠；不修改网络、交易和价格持久化格式。
 */
/**
 * 【变更记录 #15】2026-10-11 04:26:53（第 15 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：菜单状态只依据管理员权限、且失去权限后仍保持有效的路径。
 * - 新增：编辑状态与菜单有效期同时检查创造模式和管理员权限。
 * - 修改：价格广播、样本状态和 stillValid 使用统一门禁，客户端只能显示服务端确认状态。
 * - 用途：防止打开定价器后切换生存继续提交价格。
 */
package com.primordial.menu;

import com.primordial.network.PrimordialNetwork;
import com.primordial.network.S2CPriceEditorStatePacket;
import com.primordial.network.S2CPriceEditorStatePacket.Status;
import com.primordial.service.EmcService;
import com.primordial.value.ValueManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

@org.anti_ad.mc.ipn.api.IPNIgnore
public class PriceEditorMenu extends AbstractContainerMenu {
    public static final byte KIND_ITEM = 0;
    public static final byte KIND_FLUID = 1;
    public static final byte KIND_GAS = 2;
    public static final int GUI_HEIGHT = 228;
    private final ItemStackHandler sample = new ItemStackHandler(1);
    private byte selectionKind = KIND_ITEM;
    private ResourceLocation selectionId;
    private FluidStack fluidSelection = FluidStack.EMPTY;
    private final Player owner;
    private S2CPriceEditorStatePacket priceState;
    private byte lastSentKind = -1;
    private ResourceLocation lastSentId;
    private boolean lastEditable;
    private long lastPriceRevision = -1;

    public PriceEditorMenu(int id, Inventory inventory) {
        super(com.primordial.registry.ModMenus.PRICE_EDITOR.get(), id);
        owner = inventory.player;
        addSlot(new SlotItemHandler(sample, 0, 80, 25) {
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 146 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 204));
    }

    /** 当前选择的物品；选择流体或气体时返回空，防止物品价格包误写介质价格。 */
    public ItemStack selectedItem() { return selectionKind == KIND_ITEM ? sample.getStackInSlot(0) : ItemStack.EMPTY; }

    public byte selectedKind() { return selectionKind; }
    public ResourceLocation selectedId() { return selectionId; }
    public FluidStack selectedFluid() { return fluidSelection.isEmpty() ? FluidStack.EMPTY : fluidSelection.copy(); }
    public S2CPriceEditorStatePacket priceState() { return priceState; }

    /** 回包只能更新它对应的样本；快速连续 JEI 投放时，旧结果不能覆盖新目标的报价。 */
    public void acceptPriceState(S2CPriceEditorStatePacket state) {
        if (state.containerId() == containerId && state.kind() == selectionKind
                && state.id().equals(selectionId == null ? "" : selectionId.toString())) priceState = state;
    }

    public boolean matchesSelection(byte kind, ResourceLocation id) {
        return id != null && kind == selectionKind && id.equals(selectionId);
    }

    /** 只在样本、权限或价格修订号改变时同步，静止菜单不重算价格或重复发包。 */
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof ServerPlayer player && (lastSentKind != selectionKind
                || !java.util.Objects.equals(lastSentId, selectionId)
                || lastEditable != com.primordial.service.PriceEditorAccess.canEdit(player)
                || lastPriceRevision != ValueManager.priceRevision())) sendPriceState(player, Status.CURRENT);
    }

    /** 在服务端选择后、保存后或拒绝请求后调用，始终读取实际结算服务的价格。 */
    public void sendPriceState(ServerPlayer player, Status status) {
        String price = "";
        String mode = "";
        if (selectionId != null) {
            price = switch (selectionKind) {
                case KIND_ITEM -> com.primordial.compat.ProjectEItemPrices.basePrice(selectionId).toString();
                case KIND_FLUID -> com.primordial.value.ValueManager.fluidValuePerBucket(fluidSelection.getFluid()).toString();
                case KIND_GAS -> EmcService.gasPrice(selectionId).toString();
                default -> "";
            };
            mode = switch (selectionKind) {
                case KIND_ITEM -> com.primordial.compat.ProjectEItemPrices.customPrices().containsKey(selectionId.toString())
                        ? (com.primordial.compat.ProjectEItemPrices.customPrices().get(selectionId.toString()).signum() == 0
                        ? "DISABLED" : "CUSTOM_VALUE") : "INHERIT_NATIVE";
                case KIND_FLUID -> com.primordial.value.ValueManager.fluidMode(selectionId).name();
                case KIND_GAS -> com.primordial.value.ValueManager.gasMode(selectionId).name();
                default -> "";
            };
        }
        lastSentKind = selectionKind;
        lastSentId = selectionId;
        lastEditable = com.primordial.service.PriceEditorAccess.canEdit(player);
        lastPriceRevision = ValueManager.priceRevision();
        PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CPriceEditorStatePacket(containerId, selectionKind,
                        selectionId == null ? "" : selectionId.toString(), price, mode, lastEditable, status));
    }

    /** 客户端和服务端共用的物品选择入口；只复制一件，不移动玩家背包物品。 */
    public void setItemSelection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) { clearSelection(); return; }
        priceState = null;
        lastSentKind = -1;
        sample.setStackInSlot(0, stack.copyWithCount(1));
        selectionKind = KIND_ITEM;
        selectionId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        fluidSelection = FluidStack.EMPTY;
    }

    /** 保存 Forge 流体样本；价格编辑及价表均按 EMC/1000mB 报价。 */
    public void setFluidSelection(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) { clearSelection(); return; }
        priceState = null;
        lastSentKind = -1;
        sample.setStackInSlot(0, ItemStack.EMPTY);
        fluidSelection = fluid.copy();
        selectionKind = KIND_FLUID;
        selectionId = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
    }

    /** 保存 Mekanism 气体注册名；气体对象只在 JEI/渲染端短暂存在，菜单不持有可选 API 类型。 */
    public void setGasSelection(ResourceLocation id) {
        if (id == null) { clearSelection(); return; }
        priceState = null;
        lastSentKind = -1;
        sample.setStackInSlot(0, ItemStack.EMPTY);
        fluidSelection = FluidStack.EMPTY;
        selectionKind = KIND_GAS;
        selectionId = id;
    }

    public void clearSelection() {
        priceState = null;
        lastSentKind = -1;
        sample.setStackInSlot(0, ItemStack.EMPTY);
        fluidSelection = FluidStack.EMPTY;
        selectionKind = KIND_ITEM;
        selectionId = null;
    }

    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId == 0) {
            if (type != ClickType.PICKUP) return;
            if (button == 1) { clearSelection(); return; }
            ItemStack carried = getCarried();
            if (carried.isEmpty()) return;
            com.primordial.data.FluidSampleConfig gasProbe = new com.primordial.data.FluidSampleConfig();
            if (com.primordial.compat.MekanismGasCompat.sampleContainer(gasProbe, carried)
                    && gasProbe.hasGasFilter()) {
                setGasSelection(com.primordial.compat.MekanismGasCompat.gasId(gasProbe.gasFilter));
                return;
            }
            FluidStack fluid = carried.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                    .map(handler -> handler.getTanks() == 0 ? FluidStack.EMPTY : handler.getFluidInTank(0).copy())
                    .orElse(FluidStack.EMPTY);
            if (!fluid.isEmpty()) setFluidSelection(fluid); else setItemSelection(carried);
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override public boolean stillValid(Player player) {
        return player.level().isClientSide || com.primordial.service.PriceEditorAccess.canEdit(player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
