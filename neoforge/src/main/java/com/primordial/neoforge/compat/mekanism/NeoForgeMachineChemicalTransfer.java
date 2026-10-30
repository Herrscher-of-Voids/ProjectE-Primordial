package com.primordial.neoforge.compat.mekanism;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.primordial.data.MachineTarget;
import com.primordial.neoforge.compat.NeoForgeChemicalKnowledgeBridge;
import com.primordial.neoforge.knowledge.NeoForgeMediaRegistry;
import com.primordial.neoforge.machine.NeoForgeCapabilityProxy;
import com.primordial.neoforge.machine.NeoForgeMachineConfigSavedData;
import com.primordial.neoforge.machine.NeoForgeMachineProxy;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import com.primordial.util.MachineKey;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Loaded only with Mekanism; writes address the selected tank, never a generic handler-wide insertion. */
public final class NeoForgeMachineChemicalTransfer {
    private record Endpoint(IChemicalHandler handler, int tank, String key) {}

    private NeoForgeMachineChemicalTransfer() {}

    public static long run(MinecraftServer server, UUID owner, CompoundTag sample,
                           NeoForgeMachineProxy proxy, BlockEntity machine, MachineTarget target,
                           boolean supply, long requested) {
        if (server == null || !server.isSameThread() || owner == null || sample == null || proxy == null
                || requested <= 0 || !live(server, machine)) return 0;
        CompoundTag filter = sample.contains("g", Tag.TAG_COMPOUND) ? sample.getCompound("g") : sample;
        ResourceLocation id = filter.contains("gasName", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(filter.getString("gasName")) : null;
        if (!NeoForgeChemicalKnowledgeBridge.valid(id)) return 0;
        var holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id)).orElseThrow();
        ChemicalStack chemical = new ChemicalStack(holder, 1);
        String key = MachineKey.of(machine.getLevel().dimension(), machine.getBlockPos());
        var configs = NeoForgeMachineConfigSavedData.get(server);
        CompoundTag snapshot = configs.config(key);
        if (snapshot == null || !configs.canEdit(key, owner)) return 0;
        var state = machine.getBlockState();
        Endpoint endpoint = endpoint(server, machine, target);
        BooleanSupplier current = () -> {
            if (!live(server, machine) || !state.equals(machine.getBlockState())
                    || !configs.canEdit(key, owner) || !snapshot.equals(configs.config(key))) return false;
            Endpoint now = endpoint(server, machine, target);
            return same(now, endpoint) && live(server, machine) && state.equals(machine.getBlockState())
                    && configs.canEdit(key, owner) && snapshot.equals(configs.config(key));
        };
        if (endpoint == null || !current.getAsBoolean()) return 0;
        ChemicalStack contents = endpoint.handler().getChemicalInTank(endpoint.tank());
        if (!current.getAsBoolean() || !contents.isEmpty() && !contents.is(chemical.getChemical())) return 0;
        long present = contents.isEmpty() ? 0 : Math.max(0, contents.getAmount());
        if (supply) {
            long capacity = Math.max(0, endpoint.handler().getChemicalTankCapacity(endpoint.tank()));
            if (!current.getAsBoolean()) return 0;
            long space = capacity > present ? capacity - present : 0;
            long amount = Math.min(requested, space);
            if (amount <= 0 || !endpoint.handler().isValid(endpoint.tank(), chemical) || !current.getAsBoolean()) return 0;
            long simulated = inserted(amount, chemical, endpoint.handler().insertChemical(
                    endpoint.tank(), chemical.copyWithAmount(amount), Action.SIMULATE));
            if (simulated <= 0 || !current.getAsBoolean()) return 0;
            return NeoForgeResourceTrades.purchaseChemical(server, owner, id, NeoForgeMediaRegistry.chemicalTags(id),
                    simulated, units -> {
                        Endpoint actual = current.getAsBoolean() ? endpoint(server, machine, target) : null;
                        if (actual == null || !current.getAsBoolean()) return 0;
                        return inserted(units, chemical, actual.handler().insertChemical(
                                actual.tank(), chemical.copyWithAmount(units), Action.EXECUTE));
                    });
        }
        long amount = Math.min(requested, present);
        if (amount <= 0) return 0;
        long simulated = extracted(amount, chemical,
                endpoint.handler().extractChemical(endpoint.tank(), amount, Action.SIMULATE));
        if (simulated <= 0 || !current.getAsBoolean()) return 0;
        return NeoForgeResourceTrades.reclaimChemical(server, owner, id, NeoForgeMediaRegistry.chemicalTags(id),
                simulated, units -> {
                    Endpoint actual = current.getAsBoolean() ? endpoint(server, machine, target) : null;
                    if (actual == null || !current.getAsBoolean()) return 0;
                    ChemicalStack now = actual.handler().getChemicalInTank(actual.tank());
                    if (!current.getAsBoolean() || now.isEmpty() || !now.is(chemical.getChemical())) return 0;
                    return extracted(units, chemical,
                            actual.handler().extractChemical(actual.tank(), units, Action.EXECUTE));
                });
    }

    private static Endpoint endpoint(MinecraftServer server, BlockEntity machine, MachineTarget target) {
        if (!live(server, machine) || target == null || !target.bound() || target.side() == MachineTarget.INTERNAL_SIDE) return null;
        var state = machine.getBlockState();
        MachineTarget resolved = target.resolve(state);
        if (!resolved.bound()) return null;
        IChemicalHandler handler = machine.getLevel().getCapability(NeoForgeMachineChemicalAccess.CHEMICAL,
                machine.getBlockPos(), state, machine, resolved.direction());
        if (handler == null) return null;
        int tanks = handler.getChemicalTanks();
        String key = NeoForgeCapabilityProxy.INSTANCE.endpointKey(machine, resolved.direction()) + "/gas/"
                + handler.getClass().getName() + "/" + tanks + "/" + resolved.index();
        return resolved.index() < tanks && resolved.key().equals(key) && live(server, machine)
                && state.equals(machine.getBlockState()) ? new Endpoint(handler, resolved.index(), key) : null;
    }

    private static boolean live(MinecraftServer server, BlockEntity machine) {
        return machine != null && !machine.isRemoved() && machine.getLevel() instanceof ServerLevel level
                && level.getServer() == server && server.isSameThread() && level.hasChunkAt(machine.getBlockPos())
                && level.getBlockEntity(machine.getBlockPos()) == machine;
    }

    private static boolean same(Endpoint current, Endpoint initial) {
        return current != null && initial != null && current.tank() == initial.tank()
                && current.key().equals(initial.key());
    }

    private static long inserted(long authorized, ChemicalStack chemical, ChemicalStack remainder) {
        if (remainder == null || !remainder.isEmpty() && (!remainder.is(chemical.getChemical())
                || remainder.getAmount() < 0 || remainder.getAmount() > authorized))
            throw new IllegalStateException("Chemical insertion returned an invalid remainder");
        return remainder.isEmpty() ? authorized : authorized - remainder.getAmount();
    }

    private static long extracted(long authorized, ChemicalStack chemical, ChemicalStack extracted) {
        if (extracted == null || !extracted.isEmpty() && (!extracted.is(chemical.getChemical())
                || extracted.getAmount() < 0 || extracted.getAmount() > authorized))
            throw new IllegalStateException("Chemical extraction returned an invalid stack");
        return extracted.isEmpty() ? 0 : extracted.getAmount();
    }
}
