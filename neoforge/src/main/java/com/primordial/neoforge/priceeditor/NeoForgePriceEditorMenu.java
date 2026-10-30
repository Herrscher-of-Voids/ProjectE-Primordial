/**
 * 【文件职责】承载物品、流体与统一化学品幽灵样本，向当前容器发送实际价格和权限。
 * 【变更记录 #1】2026-10-23 16:56:25（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：不可拿取的单件样本、背包只读取样、价格修订刷新和服务端权限有效期。
 * - 修改：所有样本保留在菜单内；点击、拖动、双击和关闭均不能生成或吞掉实物。
 * - 用途：界面与 JEI 共用目标身份；有界选择序号阻止旧目标回包及撤销后旧保存请求覆盖当前样本。
 */
package com.primordial.neoforge.priceeditor;

import java.util.Objects;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import com.primordial.neoforge.value.NeoForgePriceEditing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NeoForgePriceEditorMenu extends AbstractContainerMenu {
    public static final byte KIND_ITEM = 0, KIND_FLUID = 1, KIND_GAS = 2;
    public static final int GUI_HEIGHT = 228;
    private final SimpleContainer sample = new SimpleContainer(1);
    private final Player owner;
    private byte selectionKind = KIND_ITEM;
    private ResourceLocation selectionId;
    private FluidStack fluidSelection = FluidStack.EMPTY;
    private long selectionVersion;
    private NeoForgePriceEditorPayloads.State priceState;
    private long lastSentSelection = -1, lastPriceRevision = -1;
    private boolean lastEditable;

    public NeoForgePriceEditorMenu(int id, Inventory inventory) {
        super(NeoForgeModRegistries.PRICE_EDITOR_MENU.get(), id);
        owner = inventory.player;
        addSlot(new Slot(sample, 0, 80, 25) {
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 146 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 204));
    }

    public ItemStack selectedItem() { return selectionKind == KIND_ITEM ? sample.getItem(0).copy() : ItemStack.EMPTY; }
    public byte selectedKind() { return selectionKind; }
    public ResourceLocation selectedId() { return selectionId; }
    public FluidStack selectedFluid() { return fluidSelection.copy(); }
    public long selectionVersion() { return selectionVersion; }
    public NeoForgePriceEditorPayloads.State priceState() { return priceState; }

    public boolean matchesSelection(byte kind, ResourceLocation id, long version) {
        return id != null && kind == selectionKind && id.equals(selectionId) && version == selectionVersion;
    }

    /** 客户端不推测权限和保存结果，且不接收旧菜单、旧样本或相同目标的旧选择代。 */
    public void acceptPriceState(NeoForgePriceEditorPayloads.State state) {
        if (state.containerId() == containerId && state.version() == selectionVersion
                && state.kind() == selectionKind && Objects.equals(state.id(), selectionId == null ? "" : selectionId.toString()))
            priceState = state;
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof ServerPlayer player) {
            boolean editable = NeoForgePriceEditorAccess.canEdit(player);
            if (lastSentSelection != selectionVersion || lastEditable != editable || lastPriceRevision != NeoForgePriceEditing.revision())
                sendPriceState(player, NeoForgePriceEditorPayloads.Status.CURRENT, "");
            if (!editable && player.containerMenu == this) player.closeContainer();
        }
    }

    public void sendPriceState(ServerPlayer player, NeoForgePriceEditorPayloads.Status status, String messageKey) {
        var state = selectionId == null ? null : NeoForgePriceEditing.snapshot(selectionKind, selectionId);
        boolean editable = NeoForgePriceEditorAccess.canEdit(player);
        lastSentSelection = selectionVersion;
        lastEditable = editable;
        lastPriceRevision = NeoForgePriceEditing.revision();
        priceState = new NeoForgePriceEditorPayloads.State(containerId, selectionKind,
                selectionId == null ? "" : selectionId.toString(), selectionVersion,
                state == null ? "" : state.price(), state == null || state.mode() == null ? "" : state.mode().name(),
                editable, state != null && state.available(), lastPriceRevision, status, messageKey);
        PacketDistributor.sendToPlayer(player, priceState);
    }

    /** 新样本只复制一件；价格是注册名基础价，不把样本组件传给编辑服务。 */
    public void setItemSelection(ItemStack stack) {
        if (stack == null || stack.isEmpty()) { clearSelection(); return; }
        beginSelection(KIND_ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        sample.setItem(0, stack.copyWithCount(1));
    }

    public void setFluidSelection(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) { clearSelection(); return; }
        beginSelection(KIND_FLUID, BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
        fluidSelection = fluid.copy();
    }

    public void setGasSelection(ResourceLocation id) {
        if (id == null) { clearSelection(); return; }
        beginSelection(KIND_GAS, id);
    }

    public void clearSelection() { beginSelection(KIND_ITEM, null); }

    private void beginSelection(byte kind, ResourceLocation id) {
        sample.setItem(0, ItemStack.EMPTY);
        fluidSelection = FluidStack.EMPTY;
        selectionKind = kind;
        selectionId = id;
        selectionVersion = selectionVersion == Long.MAX_VALUE ? 1 : selectionVersion + 1;
        priceState = null;
    }

    /** JEI 包只允许前进的有限序号；身份与注册表验证通过后才更新服务端样本。 */
    boolean acceptTarget(byte kind, ResourceLocation id, long version) {
        if (version <= selectionVersion || version - selectionVersion > 128 || version == Long.MAX_VALUE || !validTarget(kind, id)) return false;
        switch (kind) {
            case KIND_ITEM -> setItemSelection(new ItemStack(BuiltInRegistries.ITEM.get(id)));
            case KIND_FLUID -> setFluidSelection(new FluidStack(BuiltInRegistries.FLUID.get(id), 1000));
            case KIND_GAS -> setGasSelection(id);
            default -> { return false; }
        }
        selectionVersion = version;
        return true;
    }

    public static boolean validTarget(byte kind, ResourceLocation id) {
        if (id == null || id.toString().length() > NeoForgePriceEditorPayloads.MAX_ID) return false;
        return switch (kind) {
            case KIND_ITEM -> BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != net.minecraft.world.item.Items.AIR;
            case KIND_FLUID -> BuiltInRegistries.FLUID.containsKey(id) && BuiltInRegistries.FLUID.get(id) != Fluids.EMPTY;
            case KIND_GAS -> NeoForgePriceEditorChemicalBridge.validChemical(id);
            default -> false;
        };
    }

    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId == 0) {
            if (type != ClickType.PICKUP || button < 0 || button > 1) return;
            if (!player.level().isClientSide && !NeoForgePriceEditorAccess.canEdit(player)) return;
            if (button == 1) { clearSelection(); return; }
            ItemStack carried = getCarried();
            if (carried.isEmpty()) return;
            ResourceLocation chemical = NeoForgePriceEditorChemicalBridge.sampleContainer(carried.copy());
            if (chemical != null) { setGasSelection(chemical); return; }
            try {
                var handler = carried.copy().getCapability(Capabilities.FluidHandler.ITEM);
                if (handler != null) for (int tank = 0; tank < Math.min(handler.getTanks(), 128); tank++) {
                    FluidStack fluid = handler.getFluidInTank(tank).copy();
                    if (!fluid.isEmpty()) { setFluidSelection(fluid); return; }
                }
            } catch (RuntimeException | LinkageError unavailable) {
                // 容器能力故障只回退到物品样本，不调用转移，也不修改原手持物。
            }
            setItemSelection(carried);
            return;
        }
        super.clicked(slotId, button, type, player);
    }

    @Override public boolean canDragTo(Slot slot) { return slot.index != 0 && super.canDragTo(slot); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.index != 0 && super.canTakeItemForPickAll(stack, slot); }
    @Override public boolean stillValid(Player player) {
        return player == owner && (player.level().isClientSide || NeoForgePriceEditorAccess.canEdit(player));
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
