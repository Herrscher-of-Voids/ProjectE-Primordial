package com.primordial.neoforge.machine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.primordial.data.MachineTarget;
import com.primordial.util.MachineKey;
import com.primordial.util.RelativeMachineSides;
import com.primordial.neoforge.compat.NeoForgeMachineChemicalBridge;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import com.primordial.neoforge.client.NeoForgeFluidSampleRenderer;
import com.primordial.neoforge.resource.NeoForgeResourceUnits;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Forge-compatible 320x250 machine configurator. */
@yalter.mousetweaks.api.MouseTweaksIgnore
public final class NeoForgeMachineConfiguratorScreen extends AbstractContainerScreen<NeoForgeMachineConfiguratorMenu> {
    private static final int W = 320, H = 250;
    private static final int PORT_COLUMNS = 6, PORT_STEP = 28, PORT_SIZE = 26;
    private static final int PANEL = 0xFF24282C, BORDER = 0xFF656D73, SLOT = 0xFF111519, SELECTED = 0xFFFFD050;
    private int selectedZone = -1, selectedSample = -1;
    private int configRevision = -1;
    private String layoutState = "";
    private EditBox amountBox, intervalBox;
    private boolean syncingNumbers;
    private final EditBox[] energyBoxes = new EditBox[4];
    private int targetSection, targetSample, targetSide = Direction.NORTH.get3DDataValue(), targetPage;
    private boolean targetGasMode;
    private NeoForgeMachineConfiguratorPayloads.Ports shownPorts;
    private RelativeMachineSides targetSides = new RelativeMachineSides(Direction.NORTH, true);
    private final Map<Integer, String> selectedPorts = new LinkedHashMap<>();
    private final Map<String, NeoForgeMachineConfiguratorPayloads.PortTarget> selectedTargets = new LinkedHashMap<>();

    public NeoForgeMachineConfiguratorScreen(NeoForgeMachineConfiguratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        titleLabelY = -9999;
        inventoryLabelY = -9999;
    }

    @Override protected void init() {
        super.init();
        amountBox = intervalBox = null;
        java.util.Arrays.fill(energyBoxes, null);
        if (menu.pickingTarget) {
            initTargetPicker();
            return;
        }
        for (int i = 0; i < 4; i++) {
            int tab = i;
            addRenderableWidget(Button.builder(Component.translatable("screen.primordial.tab_" + i), b -> switchTab(tab))
                    .bounds(leftPos + 4 + i * 62, topPos + 5, 58, 15).build());
        }
        if (menu.activeTab < 2) {
            addMasterButton(0, leftPos + 180, topPos + 36);
            addMasterButton(1, leftPos + 180, topPos + 72);
            initSamplePanel();
        } else if (menu.activeTab == 2) initEnergyPanel();
        else initResourcePanel();
        configRevision = menu.configRevision();
        layoutState = layoutSignature();
    }

    private void switchTab(int tab) {
        menu.activeTab = tab;
        selectedZone = -1;
        selectedSample = -1;
        rebuildWidgets();
    }

    private CompoundTag config() { return menu.clientConfig(); }

    private CompoundTag sample(int section, int index) {
        CompoundTag root = config();
        if (root == null) return null;
        String key = switch (section) {
            case 0 -> "ii"; case 1 -> "io"; case 2 -> "fi"; case 3 -> "fo";
            case 6 -> "ri"; case 7 -> "ro"; default -> "";
        };
        if (key.isEmpty() || !root.contains(key, Tag.TAG_LIST)) return new CompoundTag();
        ListTag samples = root.getList(key, Tag.TAG_COMPOUND);
        return index >= 0 && index < samples.size() ? samples.getCompound(index).copy() : new CompoundTag();
    }

    private void addMasterButton(int direction, int x, int y) {
        int section = menu.activeTab == 0 ? direction : direction + 2;
        int configIndex = section + 8;
        String key = switch (configIndex) { case 8 -> "iie"; case 9 -> "ioe"; case 10 -> "fie"; default -> "foe"; };
        boolean enabled = config() != null && config().getBoolean(key);
        addRenderableWidget(Button.builder(stateText(enabled), b -> {
            sendParameter(configIndex, 0, 0, enabled ? 0 : 1);
            b.setMessage(stateText(!enabled));
        }).bounds(x, y, 38, 16).build()).active = config() != null;
    }

    private void initSamplePanel() {
        if (selectedZone < 0 || selectedSample < 0) return;
        int section = menu.activeTab * 2 + selectedZone;
        CompoundTag sample = sample(section, selectedSample);
        if (sample == null) {
            addLabel(leftPos + 8, topPos + 100, Component.translatable("screen.primordial.loading"));
            return;
        }
        String mode = sample.getString("m");
        boolean keep = "KEEP_LEVEL".equals(mode);
        if (keep) addLabel(leftPos + 6, topPos + 101, Component.translatable("screen.primordial.keep_unlimited"));
        else {
            addLabel(leftPos + 6, topPos + 94, Component.translatable("screen.primordial.amount"));
            amountBox = addNumberBox(leftPos + 6, topPos + 110, 54,
                    NeoForgeMachineConfiguratorMenu.parameterValue(sample, section, 2),
                    value -> sendParameter(section, selectedSample, 2, value));
            addLabel(leftPos + 66, topPos + 94, Component.translatable("screen.primordial.interval_ticks"));
            intervalBox = addNumberBox(leftPos + 66, topPos + 110, 54,
                    NeoForgeMachineConfiguratorMenu.parameterValue(sample, section, 3),
                    value -> sendParameter(section, selectedSample, 3, value));
        }
        addRenderableWidget(Button.builder(modeText(keep), b -> {
            sendParameter(section, selectedSample, 0, keep ? 0 : 1);
        }).bounds(leftPos + 140, topPos + 109, 60, 16).build());
        if (selectedZone == 0) {
            addRenderableWidget(Button.builder(stateText(sample.getBoolean("e")), b ->
                    sendParameter(section, selectedSample, 4, sample.getBoolean("e") ? 0 : 1))
                    .bounds(leftPos + 204, topPos + 109, 46, 16).build());
        }
        MachineTarget current = MachineTarget.load(sample.getCompound("target"));
        addRenderableWidget(Button.builder(targetText(current), b -> openTargetPicker(section, selectedSample))
                .bounds(leftPos + 182, topPos + 142, 128, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.unbind"), b -> clearTarget(section, selectedSample))
                .bounds(leftPos + 182, topPos + 166, 128, 18).build());
    }

    public java.util.List<net.minecraft.client.renderer.Rect2i> itemSampleAreas() {
        return sampleAreas(false);
    }

    public java.util.List<net.minecraft.client.renderer.Rect2i> fluidSampleAreas() {
        return sampleAreas(true);
    }

    private java.util.List<net.minecraft.client.renderer.Rect2i> sampleAreas(boolean fluid) {
        java.util.List<net.minecraft.client.renderer.Rect2i> result = new java.util.ArrayList<>(18);
        if (menu.pickingTarget || menu.activeTab != (fluid ? 1 : 0)) return result;
        int start = fluid ? 18 : 0;
        for (int i = 0; i < 18; i++) {
            var slot = menu.slots.get(start + i);
            if (slot.isActive()) result.add(new net.minecraft.client.renderer.Rect2i(
                    leftPos + slot.x, topPos + slot.y, 16, 16));
        }
        return result;
    }

    public void acceptJeiItem(ItemStack ingredient, net.minecraft.client.renderer.Rect2i area) {
        acceptJeiSample(ingredient, FluidStack.EMPTY, area, false);
    }

    public void acceptJeiFluid(FluidStack ingredient, net.minecraft.client.renderer.Rect2i area) {
        acceptJeiSample(ItemStack.EMPTY, ingredient, area, true);
    }

    public void acceptJeiChemical(ResourceLocation ingredient, net.minecraft.client.renderer.Rect2i area) {
        if (ingredient == null || !NeoForgePriceEditorChemicalBridge.validChemical(ingredient)) return;
        for (int i = 0; i < 18; i++) {
            var slot = menu.slots.get(18 + i);
            if (slot.isActive() && leftPos + slot.x == area.getX() && topPos + slot.y == area.getY()) {
                setChemicalFilter(i < 9 ? 2 : 3, i % 9, ingredient);
                return;
            }
        }
    }

    private void acceptJeiSample(ItemStack item, FluidStack fluid,
                                 net.minecraft.client.renderer.Rect2i area, boolean isFluid) {
        int start = isFluid ? 18 : 0;
        for (int i = 0; i < 18; i++) {
            var slot = menu.slots.get(start + i);
            if (!slot.isActive() || leftPos + slot.x != area.getX() || topPos + slot.y != area.getY()) continue;
            setFilter(isFluid ? (i < 9 ? 2 : 3) : (i < 9 ? 0 : 1), i % 9, item, fluid);
            return;
        }
    }

    private void initEnergyPanel() {
        CompoundTag root = config();
        if (root == null) {
            addLabel(leftPos + 8, topPos + 42, Component.translatable("screen.primordial.loading"));
            return;
        }
        // Fresh configurations contain only the owner; missing energy fields use disabled defaults.
        CompoundTag energy = root.getCompound("en");
        energyPanel(true, energy);
        energyPanel(false, energy);
    }

    private void energyPanel(boolean supply, CompoundTag energy) {
        int section = supply ? 4 : 5;
        int y = supply ? 24 : 72;
        String enabledKey = supply ? "se" : "re";
        String modeKey = supply ? "sm" : "rm";
        boolean keep = "KEEP_LEVEL".equals(energy.getString(modeKey));
        if (!keep) {
            addLabel(leftPos + 6, topPos + y + 4, Component.translatable("screen.primordial.amount_fe"));
            energyBoxes[supply ? 0 : 2] = addNumberBox(leftPos + 6, topPos + y + 20, 66,
                    NeoForgeMachineConfiguratorMenu.parameterValue(energy, section, 2), value -> sendParameter(section, 0, 2, value));
            addLabel(leftPos + 78, topPos + y + 4, Component.translatable("screen.primordial.interval_ticks"));
            energyBoxes[supply ? 1 : 3] = addNumberBox(leftPos + 78, topPos + y + 20, 54,
                    NeoForgeMachineConfiguratorMenu.parameterValue(energy, section, 3), value -> sendParameter(section, 0, 3, value));
        } else addLabel(leftPos + 6, topPos + y + 20, Component.translatable("screen.primordial.keep_unlimited"));
        addRenderableWidget(Button.builder(modeText(keep), b -> sendParameter(section, 0, 0, keep ? 0 : 1))
                .bounds(leftPos + 176, topPos + y + 18, 56, 16).build());
        addRenderableWidget(Button.builder(stateText(energy.getBoolean(enabledKey)), b ->
                sendParameter(section, 0, 4, energy.getBoolean(enabledKey) ? 0 : 1))
                .bounds(leftPos + 240, topPos + y + 18, 38, 16).build());
        String portKey = supply ? "supplyPort" : "recoverPort";
        MachineTarget target = MachineTarget.load(energy.getCompound(portKey));
        int portY = supply ? 144 : 184;
        addLabel(leftPos + 182, topPos + portY - 11, Component.translatable(supply
                ? "screen.primordial.supply_port" : "screen.primordial.recover_port"));
        addRenderableWidget(Button.builder(targetText(target), b -> openTargetPicker(section, 0))
                .bounds(leftPos + 182, topPos + portY, 128, 18).build());
    }

    /** Matches Forge's fourth tab: six stable units and one selected direction. */
    private void initResourcePanel() {
        if (selectedZone < 0) selectedZone = 0;
        if (config() == null) {
            addLabel(leftPos + 8, topPos + 42, Component.translatable("screen.primordial.loading"));
            return;
        }
        var units = NeoForgeResourceUnits.machineUnits();
        if (selectedSample < 0 || selectedSample >= units.size()) selectedSample = 0;
        int section = selectedZone == 0 ? 6 : 7;
        for (int i = 0; i < units.size(); i++) {
            int index = i;
            ResourceLocation unit = units.get(i);
            String label = (sample(section, i).getBoolean("e") ? "● " : "") + resourceName(unit).getString();
            Button button = addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selectedSample = index;
                rebuildWidgets();
            }).bounds(leftPos + 6 + i % 3 * 104, topPos + 29 + i / 3 * 27, 100, 22).build());
            button.setTooltip(Tooltip.create(i >= 4 ? Component.translatable("screen.primordial.create_resource_hint")
                    : Component.literal(unit.toString())));
        }
        addRenderableWidget(Button.builder(Component.translatable(selectedZone == 0
                ? "screen.primordial.input" : "screen.primordial.output"), b -> {
                    selectedZone = selectedZone == 0 ? 1 : 0;
                    rebuildWidgets();
                }).bounds(leftPos + 214, topPos + 84, 96, 18).build());
        CompoundTag entry = sample(section, selectedSample);
        boolean keep = "KEEP_LEVEL".equals(entry.getString("m"));
        addLabel(leftPos + 6, topPos + 95, resourceName(units.get(selectedSample)));
        if (keep) addLabel(leftPos + 6, topPos + 126, Component.translatable("screen.primordial.keep_unlimited"));
        else {
            addLabel(leftPos + 6, topPos + 110, Component.translatable("screen.primordial.amount"));
            amountBox = addNumberBox(leftPos + 6, topPos + 126, 54,
                    NeoForgeMachineConfiguratorMenu.parameterValue(entry, section, 2),
                    value -> sendParameter(section, selectedSample, 2, value));
            addLabel(leftPos + 66, topPos + 110, Component.translatable("screen.primordial.interval_ticks"));
            intervalBox = addNumberBox(leftPos + 66, topPos + 126, 54,
                    NeoForgeMachineConfiguratorMenu.parameterValue(entry, section, 3),
                    value -> sendParameter(section, selectedSample, 3, value));
        }
        addRenderableWidget(Button.builder(modeText(keep), b -> sendParameter(section, selectedSample, 0, keep ? 0 : 1))
                .bounds(leftPos + 140, topPos + 125, 60, 16).build());
        addRenderableWidget(Button.builder(stateText(entry.getBoolean("e")), b ->
                sendParameter(section, selectedSample, 4, entry.getBoolean("e") ? 0 : 1))
                .bounds(leftPos + 204, topPos + 125, 46, 16).build());
        var targets = currentTargets(section, selectedSample);
        Component targetLabel = targets.size() > 1
                ? Component.translatable("screen.primordial.targets_selected", targets.size())
                : targetText(targets.isEmpty() ? MachineTarget.UNBOUND : targets.getFirst());
        Button target = addRenderableWidget(Button.builder(targetLabel, b -> openTargetPicker(section, selectedSample))
                .bounds(leftPos + 182, topPos + 150, 128, 18).build());
        target.setTooltip(Tooltip.create(Component.translatable("screen.primordial.select_target")));
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.unbind"), b -> clearTarget(section, selectedSample))
                .bounds(leftPos + 182, topPos + 171, 128, 18).build()).active = !targets.isEmpty();
        addReservedResource("starlight", 194);
        addReservedResource("oritech_energy", 214);
    }

    private void addReservedResource(String key, int y) {
        Button button = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.resource." + key), b -> {})
                .bounds(leftPos + 182, topPos + y, 128, 16).build());
        button.active = false;
        button.setTooltip(Tooltip.create(Component.translatable("screen.primordial.resource_reserved")));
    }

    private static Component resourceName(ResourceLocation unit) {
        return Component.translatable("screen.primordial.resource." + (unit == null ? "unknown" : unit.getPath()));
    }

    private EditBox addNumberBox(int x, int y, int width, int value, java.util.function.IntConsumer send) {
        EditBox box = new EditBox(font, x, y, width, 14, CommonComponents.EMPTY);
        box.setMaxLength(10);
        box.setFilter(s -> s.matches("\\d*"));
        box.setValue(Integer.toString(value));
        box.setResponder(text -> {
            try { if (!syncingNumbers && !text.isEmpty()) send.accept(Integer.parseInt(text)); }
            catch (NumberFormatException ignored) { }
        });
        return addRenderableWidget(box);
    }

    private void sendParameter(int section, int sample, int field, int value) {
        PacketDistributor.sendToServer(new NeoForgeMachineConfiguratorPayloads.UpdateParameters(
                menu.containerId, 0, section, sample, field, value));
    }

    private void clearTarget(int section, int sample) {
        PacketDistributor.sendToServer(new NeoForgeMachineConfiguratorPayloads.Bind(menu.containerId, 0,
                section, sample, Direction.NORTH.get3DDataValue(), 0, -1, "", false, true,
                menu.pickingTarget && targetGasMode));
        if (menu.pickingTarget) closeTargetPicker();
    }

    private Component modeText(boolean keep) {
        return Component.translatable(keep ? "screen.primordial.mode_keep" : "screen.primordial.mode_fixed");
    }

    private Component stateText(boolean enabled) {
        return Component.translatable(enabled ? "screen.primordial.on" : "screen.primordial.off");
    }

    private Component targetText(MachineTarget target) {
        target = resolveTarget(target);
        if (!target.bound()) return Component.translatable("screen.primordial.target_unbound");
        String sideKey = target.side() == MachineTarget.INTERNAL_SIDE ? "internal"
                : machineSides().face(Direction.from3DDataValue(target.side())).key;
        Component side = Component.translatable("screen.primordial.side." + sideKey);
        var text = Component.translatable("screen.primordial.target_label", side, target.index() + 1);
        if (target.relativeFace() == null && target.side() != MachineTarget.INTERNAL_SIDE)
            text.append(Component.translatable("screen.primordial.side.legacy"));
        return text;
    }

    private BlockEntity clientMachine() {
        BlockPos pos = machinePos();
        if (stateAt(pos) == null) return null;
        try { return minecraft.level.getBlockEntity(pos); }
        catch (RuntimeException ignored) { return null; }
    }

    private BlockPos machinePos() {
        try { return MachineKey.parsePos(menu.machineKey()); }
        catch (RuntimeException ignored) { return null; }
    }

    private BlockState stateAt(BlockPos pos) {
        try {
            var level = minecraft.level;
            return pos != null && level != null && level.dimension().equals(MachineKey.parseDim(menu.machineKey()))
                    && level.hasChunkAt(pos) ? level.getBlockState(pos) : null;
        } catch (RuntimeException ignored) { return null; }
    }

    private MachineTarget resolveTarget(MachineTarget target) {
        BlockEntity machine = clientMachine();
        return machine == null ? target : target.resolve(machine.getBlockState());
    }

    private RelativeMachineSides machineSides() {
        return RelativeMachineSides.from(stateAt(machinePos()));
    }

    private void openTargetPicker(int section, int sample) {
        targetSection = section;
        targetSample = sample;
        CompoundTag filter = sample(section, sample);
        targetGasMode = section >= 2 && section < 4
                && (filter.contains("g", Tag.TAG_COMPOUND)
                || currentTargets(section, sample).stream().anyMatch(target -> target.key().contains("/gas/")));
        targetSides = machineSides();
        var targets = currentTargets(section, sample).stream().map(this::resolveTarget).toList();
        var first = targets.stream().filter(MachineTarget::bound).findFirst().orElse(MachineTarget.UNBOUND);
        targetSide = first.bound() ? first.side()
                : section >= 6 ? MachineTarget.INTERNAL_SIDE : targetSides.front().get3DDataValue();
        targetPage = first.bound() ? first.index() / NeoForgeMachinePortPage.PAGE_SIZE : 0;
        selectedPorts.clear();
        selectedTargets.clear();
        for (MachineTarget target : targets) {
            if (!target.bound()) continue;
            var port = new NeoForgeMachineConfiguratorPayloads.PortTarget(target.side(), target.index(), target.key());
            selectedTargets.put(target.side() + ":" + target.index(), port);
            if (target.side() == targetSide) selectedPorts.put(target.index(), target.key());
        }
        menu.pickingTarget = true;
        queryPorts();
        rebuildWidgets();
    }

    private java.util.List<MachineTarget> currentTargets(int section, int sample) {
        CompoundTag root = config();
        if (root == null) return java.util.List.of();
        if (section == 4 || section == 5) {
            CompoundTag energy = root.getCompound("en");
            MachineTarget target = MachineTarget.load(energy.getCompound(section == 4 ? "supplyPort" : "recoverPort"));
            return target.bound() ? java.util.List.of(target) : java.util.List.of();
        }
        CompoundTag entry = sample(section, sample);
        if (entry.contains("targets", Tag.TAG_LIST)) {
            ListTag stored = entry.getList("targets", Tag.TAG_COMPOUND);
            java.util.List<MachineTarget> targets = new java.util.ArrayList<>(stored.size());
            for (int i = 0; i < stored.size(); i++) {
                MachineTarget target = MachineTarget.load(stored.getCompound(i));
                if (target.bound()) targets.add(target);
            }
            if (!targets.isEmpty()) return targets;
        }
        MachineTarget target = MachineTarget.load(entry.getCompound("target"));
        return target.bound() ? java.util.List.of(target) : java.util.List.of();
    }

    private void queryPorts() {
        int requestId = menu.expectPortQuery(targetSection, targetSample, targetSide, targetPage, targetGasMode);
        PacketDistributor.sendToServer(new NeoForgeMachineConfiguratorPayloads.Query(menu.containerId,
                requestId, targetSection, targetSample, targetSide, targetPage, targetGasMode,
                targetSides.front().get3DDataValue()));
    }

    private void initTargetPicker() {
        for (RelativeMachineSides.Face face : RelativeMachineSides.Face.values()) {
            Direction direction = targetSides.direction(face);
            int side = direction.get3DDataValue();
            addRenderableWidget(new NeoForgeMachineSideButton(leftPos + 8 + face.column * 31,
                    topPos + 28 + face.row * 35, directionText(side),
                    () -> {
                        BlockPos pos = machinePos();
                        return stateAt(pos == null ? null : pos.relative(direction));
                    }, direction, side == targetSide, b -> {
                        if (targetSide == side) return;
                        targetSide = side; targetPage = 0; loadSelectedSide(); queryPorts(); rebuildWidgets();
                    }));
        }
        if (targetSection >= 4) addRenderableWidget(Button.builder(
                Component.translatable("screen.primordial.side.internal"), b -> {
                    targetSide = MachineTarget.INTERNAL_SIDE; targetPage = 0; loadSelectedSide(); queryPorts(); rebuildWidgets();
                }).bounds(leftPos + 8, topPos + 140, 60, 18).build()).active = targetSide != MachineTarget.INTERNAL_SIDE;
        if (targetSection >= 2 && targetSection < 4 && NeoForgeMachineChemicalBridge.available()) {
            addRenderableWidget(Button.builder(Component.translatable(targetGasMode
                    ? "screen.primordial.fluid_mode" : "screen.primordial.gas_mode"), b -> {
                targetGasMode = !targetGasMode;
                targetPage = 0;
                selectedPorts.clear();
                selectedTargets.clear();
                queryPorts();
                rebuildWidgets();
            }).bounds(leftPos + 244, topPos + 8, 66, 18).build());
        }
        NeoForgeMachineConfiguratorPayloads.Ports ports = menu.portSnapshot();
        shownPorts = ports;
        if (ports != null) for (int i = 0; i < ports.ports().size(); i++) {
            var port = ports.ports().get(i);
            addRenderableWidget(Button.builder(Component.empty(), b -> {
                String identity = targetSide + ":" + port.index();
                if (selectedTargets.remove(identity) != null) selectedPorts.remove(port.index());
                else if (!port.key().isEmpty()) {
                    selectedPorts.put(port.index(), port.key());
                    selectedTargets.put(identity, new NeoForgeMachineConfiguratorPayloads.PortTarget(
                            targetSide, port.index(), port.key()));
                }
                rebuildWidgets();
            }).bounds(portX(i), portY(i), PORT_SIZE, PORT_SIZE).build()).active = ports.valid() && !port.key().isEmpty();
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.previous"), b -> {
            targetPage--; queryPorts(); rebuildWidgets();
        }).bounds(leftPos + 8, topPos + 182, 58, 18).build()).active = ports != null && targetPage > 0;
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.next"), b -> {
            targetPage++; queryPorts(); rebuildWidgets();
        }).bounds(leftPos + 72, topPos + 182, 58, 18).build()).active = ports != null
                && (long) (targetPage + 1) * NeoForgeMachinePortPage.PAGE_SIZE < ports.total();
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.refresh"), b -> {
            queryPorts(); rebuildWidgets();
        }).bounds(leftPos + 136, topPos + 182, 58, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.bind_selected"), b -> bindSelected())
                .bounds(leftPos + 200, topPos + 182, 110, 18).build()).active = ports != null && ports.valid() && !selectedTargets.isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.unbind"), b -> clearTarget(targetSection, targetSample))
                .bounds(leftPos + 200, topPos + 202, 110, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.back"), b -> closeTargetPicker())
                .bounds(leftPos + 230, topPos + 220, 80, 18).build());
    }

    private int portX(int index) { return leftPos + 144 + index % PORT_COLUMNS * PORT_STEP; }
    private int portY(int index) { return topPos + 28 + index / PORT_COLUMNS * PORT_STEP; }

    private Component directionText(int side) {
        return Component.translatable("screen.primordial.side." + (side == MachineTarget.INTERNAL_SIDE
                ? "internal" : targetSides.face(Direction.from3DDataValue(side)).key));
    }

    private boolean refreshTargetOrientation() {
        var current = machineSides();
        if (current.equals(targetSides)) return false;
        if (targetSide != MachineTarget.INTERNAL_SIDE)
            targetSide = current.direction(targetSides.face(Direction.from3DDataValue(targetSide))).get3DDataValue();
        targetSides = current;
        targetPage = 0;
        selectedPorts.clear();
        selectedTargets.clear();
        queryPorts();
        rebuildWidgets();
        return true;
    }

    private void loadSelectedSide() {
        selectedPorts.clear();
        for (var target : selectedTargets.values()) if (target.side() == targetSide)
            selectedPorts.put(target.index(), target.key());
    }

    private void bindSelected() {
        if (refreshTargetOrientation() || selectedTargets.isEmpty()) return;
        var ports = menu.portSnapshot();
        if (ports == null || !ports.valid()) return;
        var targets = List.copyOf(selectedTargets.values());
        PacketDistributor.sendToServer(new NeoForgeMachineConfiguratorPayloads.BindBatch(menu.containerId, 0,
                targetSection, targetSample, targets, targetGasMode, targetSides.front().get3DDataValue()));
        closeTargetPicker();
    }

    private void closeTargetPicker() {
        menu.pickingTarget = false;
        menu.clearExpectedPortQuery();
        rebuildWidgets();
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (menu.pickingTarget && refreshTargetOrientation()) return;
        if (menu.pickingTarget && shownPorts != menu.portSnapshot()) {
            rebuildWidgets();
            return;
        }
        if (configRevision != menu.configRevision()) {
            configRevision = menu.configRevision();
            String nextLayout = layoutSignature();
            if (!nextLayout.equals(layoutState)) {
                layoutState = nextLayout;
                rebuildWidgets();
            } else syncNumberBoxes();
        }
    }

    private String layoutSignature() {
        CompoundTag root = config();
        if (root == null) return "waiting";
        if (menu.activeTab < 2) {
            String[] masters = menu.activeTab == 0 ? new String[] {"iie", "ioe"} : new String[] {"fie", "foe"};
            String selection = selectedZone < 0 ? "none" : sample(menu.activeTab * 2 + selectedZone, selectedSample)
                    .getString("m") + sample(menu.activeTab * 2 + selectedZone, selectedSample).getBoolean("e")
                    + sample(menu.activeTab * 2 + selectedZone, selectedSample).getCompound("target");
            return menu.activeTab + ":" + root.getBoolean(masters[0]) + root.getBoolean(masters[1]) + ":" + selection;
        }
        if (menu.activeTab == 2) {
            CompoundTag energy = root.getCompound("en");
            return "2:" + energy.getString("sm") + energy.getBoolean("se") + energy.getCompound("supplyPort")
                    + energy.getString("rm") + energy.getBoolean("re") + energy.getCompound("recoverPort");
        }
        StringBuilder resources = new StringBuilder("3:").append(selectedZone).append(':').append(selectedSample);
        for (int i = 0; i < NeoForgeResourceUnits.machineUnits().size(); i++)
            resources.append(sample(selectedZone == 0 ? 6 : 7, i).getBoolean("e"));
        CompoundTag selected = sample(selectedZone == 0 ? 6 : 7, selectedSample);
        return resources.append(selected.getString("m")).append(selected.getCompound("target"))
                .append(selected.getList("targets", Tag.TAG_COMPOUND)).toString();
    }

    private void syncNumberBoxes() {
        CompoundTag root = config();
        if (root == null) return;
        syncingNumbers = true;
        try {
            if (amountBox != null && !amountBox.isFocused()) {
                int section = menu.activeTab * 2 + selectedZone;
                amountBox.setValue(Integer.toString(NeoForgeMachineConfiguratorMenu.parameterValue(sample(section, selectedSample), section, 2)));
            }
            if (intervalBox != null && !intervalBox.isFocused()) {
                int section = menu.activeTab * 2 + selectedZone;
                intervalBox.setValue(Integer.toString(NeoForgeMachineConfiguratorMenu.parameterValue(sample(section, selectedSample), section, 3)));
            }
            CompoundTag energy = root.getCompound("en");
            for (int i = 0; i < energyBoxes.length; i++) if (energyBoxes[i] != null && !energyBoxes[i].isFocused())
                energyBoxes[i].setValue(Integer.toString(NeoForgeMachineConfiguratorMenu.parameterValue(energy, 4 + i / 2, 2 + i % 2)));
        } finally { syncingNumbers = false; }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!menu.pickingTarget && menu.activeTab < 2 && button <= 1) {
            int start = menu.activeTab == 0 ? 0 : 18;
            for (int i = 0; i < 18; i++) {
                int slotIndex = start + i;
                var slot = menu.slots.get(slotIndex);
                int x = leftPos + slot.x, y = topPos + slot.y;
                if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 16) continue;
                int zone = i < 9 ? 0 : 1, sampleIndex = i % 9;
                ItemStack carried = menu.getCarried();
                int section = menu.activeTab * 2 + zone;
                if (button == 1) setFilter(section, sampleIndex, ItemStack.EMPTY, FluidStack.EMPTY);
                else if (!carried.isEmpty()) {
                    if (section < 2) setFilter(section, sampleIndex, carried.copyWithCount(1), FluidStack.EMPTY);
                    else setFilterFromContainer(section, sampleIndex, carried);
                } else {
                    selectedZone = zone; selectedSample = sampleIndex; rebuildWidgets();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void setFilterFromContainer(int section, int sample, ItemStack carried) {
        ResourceLocation chemical = NeoForgePriceEditorChemicalBridge.sampleContainer(carried.copy());
        if (chemical != null) {
            setChemicalFilter(section, sample, chemical);
            return;
        }
        try {
            var handler = carried.copy().getCapability(Capabilities.FluidHandler.ITEM);
            if (handler != null) for (int i = 0; i < handler.getTanks(); i++) {
                FluidStack fluid = handler.getFluidInTank(i);
                if (!fluid.isEmpty()) { setFilter(section, sample, ItemStack.EMPTY, fluid.copy()); return; }
            }
        } catch (RuntimeException | LinkageError ignored) { }
    }

    private void setFilter(int section, int sample, ItemStack item, FluidStack fluid) {
        if (section < 2) PacketDistributor.sendToServer(new NeoForgeMachineConfiguratorPayloads.ItemFilter(
                menu.containerId, section, sample, item));
        else PacketDistributor.sendToServer(new NeoForgeMachineConfiguratorPayloads.FluidFilter(
                menu.containerId, section, sample, fluid));
    }

    private void setChemicalFilter(int section, int sample, ResourceLocation chemical) {
        PacketDistributor.sendToServer(new NeoForgeMachineConfiguratorPayloads.ChemicalFilter(
                menu.containerId, section, sample, chemical));
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (menu.pickingTarget && key == 256) { closeTargetPicker(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (menu.pickingTarget) refreshTargetOrientation();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.pickingTarget) {
            graphics.drawString(font, Component.translatable("screen.primordial.select_target"), leftPos + 8, topPos + 8, 0xFFE8EDF0, false);
            NeoForgeMachineConfiguratorPayloads.Ports ports = menu.portSnapshot();
            if (ports == null) graphics.drawString(font, Component.translatable("screen.primordial.loading"), leftPos + 144, topPos + 36, 0xFFB5BEC5, false);
            else if (ports.ports().isEmpty()) graphics.drawString(font, Component.translatable("screen.primordial.no_ports"), leftPos + 144, topPos + 36, 0xFFB5BEC5, false);
            else for (int i = 0; i < ports.ports().size(); i++) {
                var port = ports.ports().get(i);
                int x = portX(i), y = portY(i);
                if (!port.item().isEmpty()) graphics.renderItem(port.item(), x + 5, y + 1);
                else if (!port.fluid().isEmpty()) NeoForgeFluidSampleRenderer.renderFluid(graphics, port.fluid(), x + 5, y + 1);
                else if (port.chemical() != null) renderChemical(graphics, port.chemical(), x + 5, y + 1);
                else if (port.resource() != null) graphics.drawString(font,
                        font.plainSubstrByWidth(resourceName(port.resource()).getString(), 24), x + 1, y + 4, 0xFFB9EEFF, false);
                else if (targetSection >= 4) graphics.drawString(font, "FE", x + 8, y + 4, 0xFFFFFF88, false);
                String number = Integer.toString(port.index() + 1);
                graphics.drawString(font, number, x + 13 - font.width(number) / 2, y + 17,
                        port.key().isEmpty() ? 0xFFCC7777 : 0xFFFFFFFF, false);
                if (selectedPorts.containsKey(port.index())) graphics.renderOutline(x, y, PORT_SIZE, PORT_SIZE, SELECTED);
                if (mouseX >= x && mouseX < x + PORT_SIZE && mouseY >= y && mouseY < y + PORT_SIZE) {
                    List<Component> tooltip = new java.util.ArrayList<>();
                    if (!port.item().isEmpty()) tooltip.add(port.item().getHoverName());
                    else if (!port.fluid().isEmpty()) tooltip.add(port.fluid().getHoverName());
                    else if (port.chemical() != null)
                        tooltip.add(NeoForgePriceEditorChemicalBridge.chemicalName(port.chemical()));
                    else if (port.resource() != null) tooltip.add(resourceName(port.resource()));
                    tooltip.add(Component.translatable("screen.primordial.target_label", directionText(targetSide), port.index() + 1));
                    tooltip.add(Component.translatable("screen.primordial.port_amount",
                            port.resource() == null ? port.amount() : port.resourceAmount(),
                            port.resource() == null ? port.capacity() : port.resourceCapacity()));
                    if (port.key().isEmpty()) tooltip.add(Component.translatable("screen.primordial.port_unsupported"));
                    graphics.renderTooltip(font, tooltip.stream()
                            .flatMap(line -> font.split(line, W - 24).stream()).toList(), mouseX, mouseY);
                }
            }
            if (ports != null && ports.total() > 0) graphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable("screen.primordial.port_page", targetPage + 1,
                            (ports.total() - 1) / NeoForgeMachinePortPage.PAGE_SIZE + 1, ports.total()).getString(), 168),
                    leftPos + 144, topPos + 170, 0xFFB5BEC5, false);
            graphics.drawString(font, Component.translatable("screen.primordial.side.selected", directionText(targetSide)),
                    leftPos + 8, topPos + 164, 0xFFE8EDF0, false);
            graphics.drawWordWrap(font, Component.translatable("screen.primordial.port_hint"),
                    leftPos + 8, topPos + 204, 180, 0xFFB5BEC5);
            if (mouseX >= leftPos + 8 && mouseX < leftPos + 132 && mouseY >= topPos + 164 && mouseY < topPos + 179)
                graphics.renderTooltip(font, List.of(Component.translatable("screen.primordial.side.perspective"),
                        Component.translatable(targetSides.fallback() ? "screen.primordial.side.reference_fallback"
                                : "screen.primordial.side.reference", Component.translatable("screen.primordial.side." + targetSides.front().getName())),
                        Component.translatable("screen.primordial.side.rotation_hint")).stream()
                        .flatMap(line -> font.split(line, W - 24).stream()).toList(), mouseX, mouseY);
            return;
        }
        Component fluidTooltip = menu.activeTab == 1 ? renderFluidSamples(graphics, mouseX, mouseY) : null;
        if (fluidTooltip != null) graphics.renderTooltip(font, fluidTooltip, mouseX, mouseY);
        else renderTooltip(graphics, mouseX, mouseY);
    }

    private Component renderFluidSamples(GuiGraphics graphics, int mouseX, int mouseY) {
        CompoundTag root = config();
        if (root == null) return null;
        Component tooltip = null;
        for (int i = 0; i < 18; i++) {
            int section = i < 9 ? 2 : 3, index = i % 9;
            CompoundTag sample = sample(section, index);
            int slotIndex = 18 + i;
            var slot = menu.slots.get(slotIndex);
            ResourceLocation chemical = chemicalId(sample.getCompound("g"));
            FluidStack fluid = chemical == null ? legacyFluid(sample.getCompound("f")) : FluidStack.EMPTY;
            if (chemical != null) renderChemical(graphics, chemical, leftPos + slot.x, topPos + slot.y);
            else if (!fluid.isEmpty()) NeoForgeFluidSampleRenderer.renderFluid(graphics, fluid, leftPos + slot.x, topPos + slot.y);
            if (mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + 16
                    && mouseY >= topPos + slot.y && mouseY < topPos + slot.y + 16) {
                if (chemical != null) tooltip = NeoForgePriceEditorChemicalBridge.chemicalName(chemical);
                else if (!fluid.isEmpty()) tooltip = fluid.getHoverName();
            }
        }
        return tooltip;
    }

    private FluidStack legacyFluid(CompoundTag tag) {
        if (tag.contains("NeoForge", Tag.TAG_COMPOUND) && minecraft.level != null)
            return FluidStack.parseOptional(minecraft.level.registryAccess(), tag.getCompound("NeoForge"));
        if (!tag.contains("FluidName", Tag.TAG_STRING) || !tag.contains("Amount", Tag.TAG_INT)) return FluidStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("FluidName"));
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return FluidStack.EMPTY;
        return new FluidStack(BuiltInRegistries.FLUID.get(id), Math.max(1, tag.getInt("Amount")));
    }

    private ResourceLocation chemicalId(CompoundTag tag) {
        if (!tag.contains("gasName", Tag.TAG_STRING)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("gasName"));
        return NeoForgePriceEditorChemicalBridge.validChemical(id) ? id : null;
    }

    private void renderChemical(GuiGraphics graphics, ResourceLocation id, int x, int y) {
        ResourceLocation texture = NeoForgePriceEditorChemicalBridge.chemicalTexture(id);
        if (texture != null) NeoForgeFluidSampleRenderer.renderChemical(graphics, texture,
                0xFF000000 | NeoForgePriceEditorChemicalBridge.chemicalTint(id), x, y);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + W, topPos + H, 0xFF171B1F);
        graphics.renderOutline(leftPos, topPos, W, H, BORDER);
        if (menu.pickingTarget) {
            graphics.fill(leftPos + 2, topPos + 24, leftPos + W - 2, topPos + 160, PANEL);
            return;
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (slot.isActive()) {
            int x = leftPos + slot.x, y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, BORDER);
            graphics.fill(x, y, x + 16, y + 16, SLOT);
            }
        }
        if (selectedZone >= 0 && menu.activeTab < 2) {
            int index = (menu.activeTab == 0 ? 0 : 18) + selectedZone * 9 + selectedSample;
            var slot = menu.slots.get(index);
            graphics.renderOutline(leftPos + slot.x - 1, topPos + slot.y - 1, 18, 18, SELECTED);
            panel(graphics, 2, 90, W - 4, 36);
        }
        if (menu.activeTab == 2) { panel(graphics, 2, 24, W - 4, 44); panel(graphics, 2, 72, W - 4, 44); }
        else if (menu.activeTab == 3) {
            panel(graphics, 2, 88, W - 4, 156);
            graphics.renderOutline(leftPos + 5 + Math.max(0, selectedSample) % 3 * 104,
                    topPos + 28 + Math.max(0, selectedSample) / 3 * 27, 102, 24, SELECTED);
        }
    }

    private void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + h, PANEL);
        graphics.renderOutline(leftPos + x, topPos + y, w, h, BORDER);
    }

    private void addLabel(int x, int y, Component text) {
        addRenderableWidget(new net.minecraft.client.gui.components.AbstractWidget(x, y, 0, 0, CommonComponents.EMPTY) {
            @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                graphics.drawString(font, text, getX(), getY(), 0xFFCFD6DB, false);
            }
            @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) { }
        });
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.pickingTarget) return;
        if (menu.activeTab < 2) {
            graphics.drawString(font, Component.translatable("screen.primordial.input"), 8, 27, 0xFFE8EDF0, false);
            graphics.drawString(font, Component.translatable("screen.primordial.output"), 8, 63, 0xFFFFD37F, false);
            if (selectedZone < 0) graphics.drawString(font, Component.translatable("screen.primordial.select_hint"),
                    8, 100, 0xFF6688AA, false);
            return;
        }
        if (menu.activeTab == 3) {
            graphics.drawString(font, Component.translatable("screen.primordial.resource_title"), 8, 204, 0xFFCFD6DB, false);
            graphics.drawWordWrap(font, Component.translatable("screen.primordial.resource_hint"), 8, 218, 176, 0xFFB5BEC5);
            return;
        }
        if (menu.activeTab != 2) return;
        var energy = menu.energyState();
        Component text = energy == null
                ? Component.translatable("screen.primordial.machine_energy_loading")
                : Component.translatable("screen.primordial.machine_energy", energy.stored(), energy.capacity());
        graphics.drawString(font, text, 8, 120, 0xFFCFD6DB, false);
    }
}
