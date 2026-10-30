package com.primordial.neoforge.machine;

import java.util.List;
import java.util.UUID;

import com.primordial.data.MachineTarget;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeMachineConfigSavedDataGameTests {
    private static final String MACHINE = "minecraft:overworld|4|5|6";

    private NeoForgeMachineConfigSavedDataGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesLegacyConfigAndEnforcesPinnedOwner(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        CompoundTag legacy = new CompoundTag();
        CompoundTag root = new CompoundTag();
        CompoundTag config = new CompoundTag();
        config.putUUID("owner", owner);
        ListTag items = new ListTag();
        CompoundTag sample = new CompoundTag();
        sample.putInt("amount", 321);
        items.add(sample);
        config.put("ii", items);
        root.put(MACHINE, config);
        legacy.put("configs", root);

        var data = NeoForgeMachineConfigSavedData.load(legacy, helper.getLevel().registryAccess());
        var first = new MachineTarget(2, 3, "proxy/block/side/north/slot/3");
        var second = new MachineTarget(4, 1, "proxy/block/side/south/slot/1");
        check(data.available() && data.canEdit(MACHINE, owner) && !data.canEdit(MACHINE, attacker),
                "Only the persisted owner may edit an existing machine configuration");
        check(data.replaceTargets(MACHINE, owner, 0, 0, List.of(first, second))
                && data.targets(MACHINE, 0, 0).equals(List.of(first, second))
                && !data.replaceTargets(MACHINE, attacker, 0, 0, List.of(first, second))
                && !data.replaceTargets(MACHINE, owner, 0, 0,
                        List.of(first, new MachineTarget(2, -1, "invalid")))
                && data.targets(MACHINE, 0, 0).equals(List.of(first, second)),
                "Batch target replacement must authorize and validate every target before one atomic write");
        check(!data.updateTarget(MACHINE, attacker, 0, 0, first, false)
                && data.updateTarget(MACHINE, owner, 0, 0, first, false)
                && data.updateTarget(MACHINE, owner, 0, 0, second, true)
                && data.updateTarget(MACHINE, owner, 0, 0, second, true),
                "Unauthorized writes must fail and repeated target append must be idempotent");
        check(data.targets(MACHINE, 0, 0).equals(List.of(first, second))
                && data.config(MACHINE).getList("ii", Tag.TAG_COMPOUND).getCompound(0).getInt("amount") == 321,
                "Target writes must preserve the legacy sample values and order");

        CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var restored = NeoForgeMachineConfigSavedData.load(saved, helper.getLevel().registryAccess());
        check(restored.canEdit(MACHINE, owner) && !restored.canEdit(MACHINE, attacker)
                && restored.targets(MACHINE, 0, 0).equals(List.of(first, second)),
                "Owner and selected targets must survive SavedData NBT reload");
        check(restored.updateTarget(MACHINE, owner, 0, 0, MachineTarget.UNBOUND, false)
                && restored.targets(MACHINE, 0, 0).isEmpty(),
                "Unbinding must clear both the current and legacy-compatible target fields");

        var absent = new NeoForgeMachineConfigSavedData();
        check(absent.getOrCreate(MACHINE, owner) != null
                && absent.getOrCreate(MACHINE, attacker).getUUID("owner").equals(owner),
                "The first editor must remain owner when the same machine is reopened");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void validatesSectionBoundsAndKeepsCorruptRootsReadOnly(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        var data = new NeoForgeMachineConfigSavedData();
        data.getOrCreate(MACHINE, owner);
        MachineTarget internalEnergy = new MachineTarget(MachineTarget.INTERNAL_SIDE, 0, "proxy/internal/energy");
        check(data.updateTarget(MACHINE, owner, 4, 0, internalEnergy, false)
                && data.targets(MACHINE, 4, 0).equals(List.of(internalEnergy))
                && !data.updateTarget(MACHINE, owner, 4, 1, internalEnergy, false)
                && !data.updateTarget(MACHINE, owner, 6, 6, internalEnergy, false)
                && !data.updateTarget(MACHINE, owner, 8, 0, internalEnergy, false),
                "Energy, resource and section indices must follow the legacy bounds");

        CompoundTag malformed = new CompoundTag();
        malformed.putString("configs", "not a compound");
        malformed.putString("opaque", "keep");
        var blocked = NeoForgeMachineConfigSavedData.load(malformed, helper.getLevel().registryAccess());
        check(!blocked.available() && blocked.getOrCreate(MACHINE, owner) == null
                && !blocked.updateTarget(MACHINE, owner, 0, 0, internalEnergy, false)
                && malformed.equals(blocked.save(new CompoundTag(), helper.getLevel().registryAccess())),
                "Malformed roots must remain byte-for-byte readable and reject edits");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void editsLegacyParametersWithoutDroppingUnknownNbt(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        CompoundTag config = new CompoundTag();
        config.putUUID("owner", owner);
        config.putString("future_config", "keep");
        CompoundTag sample = new CompoundTag();
        sample.putString("future_sample", "keep");
        sample.putInt("r", 12);
        ListTag items = new ListTag();
        items.add(sample);
        config.put("ii", items);
        CompoundTag energy = new CompoundTag();
        energy.putString("future_energy", "keep");
        config.put("en", energy);
        CompoundTag root = new CompoundTag();
        root.put(MACHINE, config);
        CompoundTag savedData = new CompoundTag();
        savedData.put("configs", root);
        var data = NeoForgeMachineConfigSavedData.load(savedData, helper.getLevel().registryAccess());

        check(!data.updateParameter(MACHINE, attacker, 0, 0, 1, 99)
                && data.updateParameter(MACHINE, owner, 0, 0, 1, -10)
                && data.updateParameter(MACHINE, owner, 0, 0, 0, 1)
                && data.updateParameter(MACHINE, owner, 4, 0, 2, Integer.MAX_VALUE)
                && data.updateParameter(MACHINE, owner, 5, 0, 3, 0)
                && data.updateParameter(MACHINE, owner, 11, 0, 0, 1),
                "The owner can edit supported parameters and values are clamped to legacy bounds");
        check(!data.updateParameter(MACHINE, owner, 0, 9, 1, 1)
                && !data.updateParameter(MACHINE, owner, 0, 0, 0, 2)
                && !data.updateParameter(MACHINE, owner, 6, 0, 1, 1)
                && !data.updateParameter(MACHINE, owner, 8, 1, 0, 1),
                "Out-of-range, unsupported and malformed parameter addresses are rejected");

        CompoundTag updated = data.config(MACHINE);
        CompoundTag updatedSample = updated.getList("ii", Tag.TAG_COMPOUND).getCompound(0);
        CompoundTag updatedEnergy = updated.getCompound("en");
        check(updatedSample.getInt("r") == 1 && updatedSample.getString("m").equals("KEEP_LEVEL")
                && updatedSample.getString("future_sample").equals("keep")
                && updatedEnergy.getInt("sa") == Integer.MAX_VALUE
                && updatedEnergy.getInt("rit") == 1
                && updatedEnergy.getString("future_energy").equals("keep")
                && updated.getBoolean("foe") && updated.getString("future_config").equals("keep"),
                "Updates must preserve unrelated and unknown NBT fields");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void updatesItemAndFluidFiltersWithLegacyNbtAndPinnedOwner(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        CompoundTag config = new CompoundTag();
        config.putUUID("owner", owner);
        config.putString("future_config", "keep");
        CompoundTag oldSample = new CompoundTag();
        oldSample.putString("future_sample", "keep");
        ListTag inputItems = new ListTag();
        inputItems.add(oldSample);
        config.put("ii", inputItems);
        CompoundTag root = new CompoundTag();
        root.put(MACHINE, config);
        CompoundTag savedData = new CompoundTag();
        savedData.put("configs", root);
        var data = NeoForgeMachineConfigSavedData.load(savedData, helper.getLevel().registryAccess());

        ItemStack diamond = new ItemStack(Items.DIAMOND, 7);
        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        CompoundTag fluidCustomData = new CompoundTag();
        fluidCustomData.putString("sample", "preserved");
        water.set(DataComponents.CUSTOM_DATA, CustomData.of(fluidCustomData));
        check(!data.updateItemFilter(MACHINE, attacker, 0, 0, diamond, helper.getLevel().registryAccess())
                && !data.updateItemFilter(MACHINE, owner, 2, 0, diamond, helper.getLevel().registryAccess())
                && !data.updateFluidFilter(MACHINE, owner, 1, 0, water, helper.getLevel().registryAccess())
                && !data.updateFluidFilter(MACHINE, owner, 2, 9, water, helper.getLevel().registryAccess()),
                "Only the pinned owner can set a filter in its matching section and sample range");
        check(data.updateItemFilter(MACHINE, owner, 0, 0, diamond, helper.getLevel().registryAccess())
                && data.updateFluidFilter(MACHINE, owner, 3, 0, water, helper.getLevel().registryAccess()),
                "Item and fluid sample filters should be accepted for their section groups");

        CompoundTag filtered = data.config(MACHINE);
        CompoundTag itemSample = filtered.getList("ii", Tag.TAG_COMPOUND).getCompound(0);
        CompoundTag fluidSample = filtered.getList("fo", Tag.TAG_COMPOUND).getCompound(0);
        CompoundTag oldFluidFormat = fluidSample.getCompound("f");
        CompoundTag currentFluidFormat = oldFluidFormat.getCompound("NeoForge");
        ItemStack savedItemFilter = ItemStack.parseOptional(helper.getLevel().registryAccess(), itemSample.getCompound("f"));
        check(savedItemFilter.getItem() == Items.DIAMOND && savedItemFilter.getCount() == 1
                && itemSample.getString("future_sample").equals("keep") && itemSample.getBoolean("e")
                && oldFluidFormat.getString("FluidName").equals("minecraft:water")
                && oldFluidFormat.getInt("Amount") == 1000
                && oldFluidFormat.getCompound("Tag").getString("sample").equals("preserved")
                && currentFluidFormat.getString("id").equals("minecraft:water")
                && currentFluidFormat.getInt("amount") == 1000
                && FluidStack.parseOptional(helper.getLevel().registryAccess(), currentFluidFormat).getAmount() == 1000
                && fluidSample.getBoolean("e") && filtered.getString("future_config").equals("keep"),
                "Filters should use the legacy f fields, preserve components and unknown NBT, and enable samples");

        CompoundTag reloadedTag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        var reloaded = NeoForgeMachineConfigSavedData.load(reloadedTag, helper.getLevel().registryAccess());
        check(reloaded.config(MACHINE).equals(filtered), "Filter NBT must survive SavedData roundtrip");
        check(reloaded.updateItemFilter(MACHINE, owner, 1, 0, ItemStack.EMPTY, helper.getLevel().registryAccess())
                && reloaded.updateFluidFilter(MACHINE, owner, 3, 0, FluidStack.EMPTY, helper.getLevel().registryAccess()),
                "An empty filter clears the sample");
        CompoundTag cleared = reloaded.config(MACHINE);
        check(!cleared.getList("io", Tag.TAG_COMPOUND).getCompound(0).contains("f")
                && !cleared.getList("io", Tag.TAG_COMPOUND).getCompound(0).getBoolean("e")
                && !cleared.getList("fo", Tag.TAG_COMPOUND).getCompound(0).contains("f")
                && !cleared.getList("fo", Tag.TAG_COMPOUND).getCompound(0).getBoolean("e"),
                "Clearing output filters must remove f and disable the sample");

        CompoundTag malformedConfig = new CompoundTag();
        malformedConfig.putUUID("owner", owner);
        ListTag malformedSamples = new ListTag();
        CompoundTag malformedSample = new CompoundTag();
        malformedSample.putString("f", "wrong type");
        malformedSamples.add(malformedSample);
        malformedConfig.put("fi", malformedSamples);
        CompoundTag malformedRoot = new CompoundTag();
        malformedRoot.put(MACHINE, malformedConfig);
        CompoundTag malformedSavedData = new CompoundTag();
        malformedSavedData.put("configs", malformedRoot);
        var malformed = NeoForgeMachineConfigSavedData.load(malformedSavedData, helper.getLevel().registryAccess());
        check(!malformed.updateFluidFilter(MACHINE, owner, 2, 0, water, helper.getLevel().registryAccess()),
                "A malformed existing filter must remain unchanged");
        check(BuiltInRegistries.FLUID.getKey(water.getFluid()).toString().equals(oldFluidFormat.getString("FluidName")),
                "The legacy fluid ID must remain registry-resolvable");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
