package com.primordial.neoforge.resource.create;

import java.util.ArrayList;
import java.util.List;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.resource.NeoForgeResourcePort;
import com.primordial.neoforge.resource.NeoForgeResourcePortRegistry;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Real native kinetics purchased for one tick; never a reclaimable resource tank. */
public final class NeoForgeCreateResourceAdapter {
    public static final ResourceLocation SPEED = ResourceLocation.parse("create:rotation_speed");
    public static final ResourceLocation STRESS = ResourceLocation.parse("create:stress");
    private NeoForgeCreateResourceAdapter() {}

    public static void register() {
        if (!KineticAccess.class.isAssignableFrom(KineticBlockEntity.class)
                || !KineticNetworkAccess.class.isAssignableFrom(KineticNetwork.class))
            throw new IllegalStateException("Create kinetic mixins are unavailable");
        CreateKineticTicker.register();
        NeoForgeResourcePortRegistry.register("create", be -> be instanceof KineticBlockEntity,
                NeoForgeCreateResourceAdapter::find);
    }

    private static List<NeoForgeResourcePort> find(BlockEntity machine, Direction side) {
        if (!(machine instanceof KineticBlockEntity kinetic) || !(machine instanceof KineticAccess access)
                || !live(kinetic)) return List.of();
        List<NeoForgeResourcePort> ports = new ArrayList<>();
        if (CreateKinetics.canDrive(kinetic)) ports.add(port(kinetic, access, false));
        ports.add(port(kinetic, access, true));
        return List.copyOf(ports);
    }

    private static boolean live(KineticBlockEntity machine) {
        var level = machine.getLevel();
        return !machine.isRemoved() && level != null && !level.isClientSide
                && level.hasChunkAt(machine.getBlockPos()) && level.getBlockEntity(machine.getBlockPos()) == machine;
    }

    private static NeoForgeResourcePort port(KineticBlockEntity machine, KineticAccess access, boolean stress) {
        return new NeoForgeResourcePort() {
            @Override public ResourceLocation unit() { return stress ? STRESS : SPEED; }
            @Override public String key() { return "create/kinetic/" + unit(); }
            @Override public long amount() {
                KineticLease lease = access.primordial$leaseIfPresent();
                return !live(machine) || lease == null ? 0
                        : lease.reserved(machine.getLevel().getGameTime(), stress);
            }
            @Override public long capacity() {
                return stress ? NeoForgeServerConfig.createStressPerTick() : CreateKinetics.maxSpeed();
            }
            @Override public boolean canReceive() { return live(machine); }
            @Override public boolean canExtract() { return false; }
            @Override public long extract(long requested, boolean simulate) { return 0; }
            @Override public long receive(long requested, boolean simulate) {
                if (requested <= 0 || !canReceive()) return 0;
                if (simulate) return Math.min(Math.max(0, requested), Math.max(0, capacity() - amount()));
                KineticLease lease = access.primordial$lease();
                long accepted = lease.receive(machine.getLevel().getGameTime(), stress, requested, capacity(), false);
                if (accepted > 0) CreateKineticTicker.track(machine, lease);
                return accepted;
            }
        };
    }
}
