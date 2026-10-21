/**
 * 【文件职责】将已付款的一 tick 额度接入 Create 原生旋转传播和应力网络。
 * 【变更记录 #1】2026-09-16 19:59:10（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：真实动力源启停、原生网络容量刷新、存档临时容量剔除和可驱动机器校验。
 * - 修改：转速通过 detach/setNetwork/attach 传播，应力加入当前网络总容量；稳定供给不重复重建网络。
 * - 用途：由可选 Create Mixin 调用；停付下一 tick 撤销，仅保留原生发电机和其它已付款端口的贡献。
 */
/**
 * 【变更记录 #2】2026-09-16 20:30:43（第 2 次备注 · 1.0.0-create-kinetics）
 * - 删除：依赖机器本身 tick 决定动力期限的路径。
 * - 新增：由统一动力调度器传入下一 tick 时间，空闲机器提前退出。
 * - 修改：只有确实需要转速时读取原生最大转速，无需建立空状态来保存普通机器。
 * - 用途：暂停/欠费与目标区块是否继续 ticking 无关，失效额度及时清理。
 */
package com.primordial.resource.create;

import com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.nbt.CompoundTag;

/** 此类含可选 API，只能从安装了 Create 的适配器/Mixin 内解析。 */
public final class CreateKinetics {
    private CreateKinetics() {}

    private static final ClassValue<Boolean> DIRECT_DRIVE = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            // 齿轮箱/离合器要求真实的上游方向；原生发电机有自己的发电逻辑，均不改写。
            if (DirectionalShaftHalvesBlockEntity.class.isAssignableFrom(type)) return false;
            try {
                return type.getMethod("getGeneratedSpeed").getDeclaringClass() == KineticBlockEntity.class;
            } catch (NoSuchMethodException invalidApi) { return false; }
        }
    };

    public static boolean canDrive(KineticBlockEntity machine) { return DIRECT_DRIVE.get(machine.getClass()); }

    public static int maxSpeed() { return Math.max(1, AllConfigs.server().kinetics.maxRotationSpeed.get()); }

    /** 本 tick 结算完成后准备下一 tick 的动力；全程不强制加载区块。 */
    public static void advance(KineticBlockEntity machine, KineticLease lease, long tick) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide || machine.isRemoved()) return;
        if (!lease.beginTick(tick)) return;
        long stress = lease.due(tick, true);
        long dueSpeed = lease.due(tick, false);
        if (dueSpeed == 0 && stress == 0 && lease.activeSpeed == 0 && lease.activeStress == 0) return;
        float speed = dueSpeed > 0 && canDrive(machine) ? Math.min(maxSpeed(), dueSpeed) : 0;
        float previous = machine.getTheoreticalSpeed();
        // 沿用所在轴当前方向，避免反向动力源冲突；停止的轴从正方向启动。
        if (previous < 0) speed = -speed;
        boolean stressChanged = lease.activeStress != stress;
        lease.activeStress = stress;
        boolean speedChanged = lease.activeSpeed != speed;
        if (speedChanged || (speed != 0 && !machine.hasSource() && previous != speed)) {
            applySpeed(machine, lease, speed);
        }
        if ((stressChanged || speedChanged) && machine.hasNetwork()) machine.getOrCreateNetwork().updateNetwork();
    }

    /** 遵循原生发电机的拓扑更新顺序；较强的原生上游继续供能，关闭时重新寻找原生上游。 */
    private static void applySpeed(KineticBlockEntity machine, KineticLease lease, float requested) {
        float previous = machine.getTheoreticalSpeed();
        if (requested == 0 && machine.hasSource()) {
            if (machine.hasNetwork()) machine.getOrCreateNetwork().sources.remove(machine);
            lease.activeSpeed = 0;
            return;
        }
        if (requested != 0 && machine.hasSource() && Math.abs(previous) >= Math.abs(requested)) {
            lease.activeSpeed = requested;
            if (machine.hasNetwork()) machine.getOrCreateNetwork().sources.put(machine, 0f);
            return;
        }
        // 旧源仍有效时先解绑，确保 Create 删除旧 sources 条目。
        machine.detachKinetics();
        machine.setNetwork(null);
        machine.source = null;
        lease.activeSpeed = requested;
        machine.setSpeed(requested);
        if (requested != 0) machine.setNetwork(machine.getBlockPos().asLong());
        machine.attachKinetics();
        machine.onSpeedChanged(previous);
        machine.sendData();
    }

    /** 持久化只保留原生容量；客户端包仍展示 EMC 供给后的真实总容量和转速。 */
    public static void sanitizeSavedNetwork(KineticBlockEntity machine, KineticLease lease, CompoundTag tag) {
        if (machine.hasNetwork() && machine.getOrCreateNetwork() instanceof KineticNetworkAccess network
                && tag.contains("Network")) {
            CompoundTag saved = tag.getCompound("Network");
            saved.putFloat("Capacity", Math.max(0, saved.getFloat("Capacity") - network.primordial$paidCapacity()));
        }
        if (lease != null && lease.activeSpeed != 0 && !machine.hasSource()) {
            tag.putFloat("Speed", 0);
            tag.remove("Network");
            tag.putBoolean("NeedsSpeedUpdate", true);
        }
    }
}
