/**
 * 【文件职责】机器配置器菜单基础生命周期；参数编辑和端口 Payload 后续接入。
 * 【安全边界】服务端每次 stillValid 都复查所有者、维度、距离、已加载区块、机器实体和能力。
 */
package com.primordial.neoforge.machine;

import com.primordial.data.ConfigLimits;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeMachineConfiguratorMenu extends AbstractContainerMenu {
    private final Player owner;
    private final NeoForgeMachineConfigSavedData data;
    private final String machineKey;
    @Nullable private final BlockEntity openedMachine;
    @Nullable private NeoForgeMachineConfiguratorPayloads.Ports portSnapshot;
    @Nullable private NeoForgeMachineConfiguratorPayloads.BindResult bindResult;
    @Nullable private NeoForgeMachineConfiguratorPayloads.EnergyState energyState;
    @Nullable private CompoundTag clientConfig;
    private final SimpleContainer sampleIcons = new SimpleContainer(36);
    private int configRevision;
    private int requestSequence;
    private int expectedRequestId = -1, expectedSection = -1, expectedSample = -1, expectedSide = -1, expectedPage = -1;
    private boolean expectedGasMode;
    public int activeTab;
    public boolean pickingTarget;

    public NeoForgeMachineConfiguratorMenu(int id, Inventory inventory) {
        this(id, inventory, "");
    }

    public NeoForgeMachineConfiguratorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, extra.readUtf(256));
    }

    public NeoForgeMachineConfiguratorMenu(int id, Inventory inventory, String machineKey) {
        super(NeoForgeModRegistries.MACHINE_CONFIGURATOR_MENU.get(), id);
        this.owner = inventory.player;
        this.machineKey = NeoForgeMachineConfiguratorAccess.validKey(machineKey) ? machineKey : "";
        this.data = owner.getServer() == null ? null : NeoForgeMachineConfigSavedData.get(owner.getServer());
        // Keep an identity witness; port capabilities must be re-resolved at the point of use.
        this.openedMachine = owner instanceof ServerPlayer player
                && NeoForgeMachineConfiguratorAccess.canOpen(player, this.machineKey, data)
                ? NeoForgeMachineConfiguratorAccess.findLoaded(player.server, this.machineKey) : null;
        for (int i = 0; i < sampleIcons.getContainerSize(); i++) {
            int sampleTab = i < 18 ? 0 : 1;
            addSlot(new Slot(sampleIcons, i, 8 + i % 9 * 18, i < 18 ? (i < 9 ? 36 : 72)
                    : i < 27 ? 36 : 72) {
                @Override public boolean isActive() { return activeTab == sampleTab && !pickingTarget; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            int slotIndex = 9 + row * 9 + col;
            addSlot(new Slot(inventory, slotIndex, 8 + col * 18, 132 + row * 18) {
                @Override public boolean isActive() { return activeTab < 2 && !pickingTarget; }
            });
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 190) {
                @Override public boolean isActive() { return activeTab < 2 && !pickingTarget; }
            });
        }
        if (data != null) {
            CompoundTag initial = data.config(this.machineKey);
            if (initial != null) setClientConfig(initial);
        }
    }

    public String machineKey() { return machineKey; }

    @Nullable
    public BlockEntity targetMachine(ServerPlayer player) {
        if (player != owner || openedMachine == null
                || !NeoForgeMachineConfiguratorAccess.canUse(player, machineKey, data)) return null;
        BlockEntity current = NeoForgeMachineConfiguratorAccess.findLoaded(player.server, machineKey);
        return current == openedMachine ? current : null;
    }

    public boolean canEdit(ServerPlayer player) { return targetMachine(player) != null; }

    public void acceptPorts(NeoForgeMachineConfiguratorPayloads.Ports ports) {
        if (ports.containerId() == containerId && ports.machineKey().equals(machineKey)
                && ports.requestId() == expectedRequestId && ports.section() == expectedSection
                && ports.sample() == expectedSample && ports.side() == expectedSide
                && ports.page() == expectedPage && ports.gasMode() == expectedGasMode) portSnapshot = ports;
    }

    public void acceptBindResult(NeoForgeMachineConfiguratorPayloads.BindResult result) {
        if (result.containerId() == containerId) bindResult = result;
    }

    public void acceptEnergyState(NeoForgeMachineConfiguratorPayloads.EnergyState state) {
        if (state.containerId() == containerId && state.machineKey().equals(machineKey)) energyState = state;
    }

    public void acceptSnapshot(NeoForgeMachineConfiguratorPayloads.Snapshot snapshot) {
        if (snapshot.containerId() == containerId && snapshot.machineKey().equals(machineKey))
            setClientConfig(snapshot.config());
    }

    public void setClientConfig(CompoundTag config) {
        clientConfig = config.copy();
        configRevision++;
        for (int section = 0; section < 2; section++) {
            String key = section == 0 ? "ii" : "io";
            var samples = clientConfig.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int sample = 0; sample < 9; sample++) {
                ItemStack filter = sample < samples.size()
                        ? ItemStack.parseOptional(owner.level().registryAccess(), samples.getCompound(sample).getCompound("f"))
                        : ItemStack.EMPTY;
                sampleIcons.setItem(section * 9 + sample, filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1));
            }
        }
    }

    public int configRevision() { return configRevision; }

    public int expectPortQuery(int section, int sample, int side, int page, boolean gasMode) {
        requestSequence = requestSequence + 1 & 0xffff;
        expectedRequestId = requestSequence;
        expectedSection = section;
        expectedSample = sample;
        expectedSide = side;
        expectedPage = page;
        expectedGasMode = gasMode;
        portSnapshot = null;
        return expectedRequestId;
    }

    public int expectPortQuery(int section, int sample, int side, int page) {
        return expectPortQuery(section, sample, side, page, false);
    }

    public void clearExpectedPortQuery() {
        expectedRequestId = expectedSection = expectedSample = expectedSide = expectedPage = -1;
        expectedGasMode = false;
        portSnapshot = null;
    }

    @Nullable public NeoForgeMachineConfiguratorPayloads.Ports portSnapshot() { return portSnapshot; }
    @Nullable public NeoForgeMachineConfiguratorPayloads.BindResult bindResult() { return bindResult; }
    @Nullable public NeoForgeMachineConfiguratorPayloads.EnergyState energyState() { return energyState; }
    @Nullable public CompoundTag clientConfig() { return clientConfig == null ? null : clientConfig.copy(); }
    public ItemStack sampleIcon(int slot) { return sampleIcons.getItem(slot).copy(); }

    /** Reads the same sparse/legacy numeric defaults as the task executor without writing them back. */
    static int parameterValue(CompoundTag entry, int section, int field) {
        if (field == 2) {
            String key = section >= 6 ? "a" : section == 4 ? "sa" : section == 5 ? "ra" : "t";
            int fallback = section >= 6 ? 1000 : section == 4 ? entry.getInt("sr")
                    : section == 5 ? entry.getInt("rr") : 1;
            return ConfigLimits.amount(entry.contains(key, net.minecraft.nbt.Tag.TAG_INT) ? entry.getInt(key) : fallback, 1);
        }
        if (field == 3) {
            String key = section == 4 ? "sit" : section == 5 ? "rit" : "it";
            if (entry.contains(key, net.minecraft.nbt.Tag.TAG_INT)) return ConfigLimits.intervalTicks(entry.getInt(key));
            if (section >= 6) return 20;
            String legacy = section == 4 ? "si" : section == 5 ? "ri" : "i";
            return ConfigLimits.intervalTicks((int) Math.min(Integer.MAX_VALUE,
                    Math.max(1L, (long) entry.getInt(legacy) * 20L)));
        }
        throw new IllegalArgumentException("Unsupported numeric field: " + field);
    }

    @Override
    public boolean stillValid(Player player) {
        if (player != owner) return false;
        if (player.level().isClientSide) return true;
        return player instanceof ServerPlayer serverPlayer && canEdit(serverPlayer);
    }

    // Ghost slots are display-only; filter writes use validated payloads.
    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId == -999) { super.clicked(slotId, button, type, player); return; }
        if (slotId >= sampleIcons.getContainerSize() && slotId < slots.size()
                && slots.get(slotId).isActive()) super.clicked(slotId, button, type, player);
    }

    @Override public boolean canDragTo(Slot slot) { return slots.indexOf(slot) >= sampleIcons.getContainerSize(); }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slots.indexOf(slot) >= sampleIcons.getContainerSize();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < sampleIcons.getContainerSize() || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;
        if (activeTab < 2) {
            ItemStack stack = source.getItem();
            var chemical = NeoForgePriceEditorChemicalBridge.sampleContainer(stack);
            FluidStack fluid = fluidSample(stack);
            boolean chemicalSample = chemical != null;
            int section = chemicalSample || !fluid.isEmpty() ? 2 : 0;
            int sample = firstEmptySample(section);
            if (sample >= 0) {
                if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer && data != null
                        && canEdit(serverPlayer)) {
                    boolean saved = chemicalSample
                            ? data.updateChemicalFilter(machineKey, player.getUUID(), section, sample, chemical)
                            : fluid.isEmpty()
                                    ? data.updateItemFilter(machineKey, player.getUUID(), section, sample, stack,
                                            player.registryAccess())
                                    : data.updateFluidFilter(machineKey, player.getUUID(), section, sample, fluid,
                                            player.registryAccess());
                    if (saved) NeoForgeMachineConfiguratorPayloads.sendSnapshot(serverPlayer, this);
                }
                return ItemStack.EMPTY;
            }
        }
        ItemStack original = source.getItem().copy();
        int inventoryStart = sampleIcons.getContainerSize();
        int hotbarStart = inventoryStart + 27;
        boolean moved = index < hotbarStart
                ? moveItemStackTo(source.getItem(), hotbarStart, slots.size(), false)
                : moveItemStackTo(source.getItem(), inventoryStart, hotbarStart, false);
        if (!moved) return ItemStack.EMPTY;
        if (source.getItem().isEmpty()) source.set(ItemStack.EMPTY);
        else source.setChanged();
        return original;
    }

    @Override public void broadcastChanges() {
        if (owner instanceof ServerPlayer player && player.server.getTickCount() % 10 == 0)
            NeoForgeMachineConfiguratorPayloads.sendEnergyState(player, this);
        super.broadcastChanges();
    }

    private int firstEmptySample(int section) {
        if (clientConfig == null) return -1;
        String key = section == 0 ? "ii" : "fi";
        var samples = clientConfig.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < 9; i++) {
            if (i >= samples.size() || !samples.getCompound(i).contains("f")
                    && !samples.getCompound(i).contains("g")) return i;
        }
        return -1;
    }

    private static FluidStack fluidSample(ItemStack stack) {
        try {
            var handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler != null) for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack fluid = handler.getFluidInTank(i);
                if (!fluid.isEmpty()) return fluid.copy();
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return FluidStack.EMPTY;
    }
}
