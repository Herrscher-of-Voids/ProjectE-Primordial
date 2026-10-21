/**
 * 【文件职责】为机械动力登记转速和应力单位的原生适配入口。
 * 变更记录 #1（2026-09-16）：新增安全探测。Create 的转速和应力是动力网络状态而非
 * 普通储罐，只有方块实体同时公开增减方法时才返回可交易端口；仅有 getSpeed 或应力
 * 容量的只读动力网络不会被强行改写，避免破坏整网拓扑。换算比例仍可在配置中调整。
 */
/**
 * 【变更记录 #2】2026-09-16 19:59:10（第 2 次备注 · 1.0.0-create-kinetics）
 * - 删除：寻找 addSpeed/insertStress 等不存在的储罐接口。
 * - 新增：基于原生 KineticBlockEntity 的转速、应力端口及已付款单 tick 供给。
 * - 修改：按继承关系识别附属机器，多方向共享额度；转速上限读取 Create，应力上限读取本模组配置。
 * - 用途：EmcService 真实扣款后预约，Create tick 兑现；禁止将循环动力回收 EMC。
 */
/**
 * 【变更记录 #3】2026-09-16 20:30:43（第 3 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：仅实际付款后登记机器到动力调度器；注册前验证两个动力 Mixin 均可用。
 * - 修改：端口模拟和读取不创建付款状态，避免打开界面影响世界或普通方块内存占用。
 * - 用途：无效兼容版本不会暴露只收钱的端口，成功付款的期限由服务端统一管理。
 */
package com.primordial.resource.adapters;

import com.primordial.resource.ReflectiveResourceAdapter;
import com.primordial.resource.ResourceExchangeConversions;
import com.primordial.resource.EmcResourcePort;
import com.primordial.resource.create.CreateKinetics;
import com.primordial.resource.create.KineticAccess;
import com.primordial.config.PrimordialServerConfig;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class CreateResourceAdapter extends ReflectiveResourceAdapter {
    private final boolean stress;

    public CreateResourceAdapter(boolean stress) {
        super(stress ? ResourceExchangeConversions.CREATE_STRESS : ResourceExchangeConversions.CREATE_SPEED,
                new String[0], new String[0], new String[0], new String[0]);
        this.stress = stress;
        if (!KineticAccess.class.isAssignableFrom(KineticBlockEntity.class)
                || !com.primordial.resource.create.KineticNetworkAccess.class.isAssignableFrom(
                        com.simibubi.create.content.kinetics.KineticNetwork.class)) {
            throw new IllegalStateException("Create kinetic mixins are unavailable");
        }
        com.primordial.resource.create.CreateKineticTicker.register();
    }

    @Override protected String id() { return stress ? "create_stress" : "create_speed"; }
    @Override protected boolean matchesClass(String name) { return packagePrefix(name, "com.simibubi.create."); }

    @Override public Predicate<BlockEntity> matcher() { return machine -> machine instanceof KineticBlockEntity; }

    @Override public List<EmcResourcePort> find(BlockEntity machine, Direction side) {
        if (!(machine instanceof KineticBlockEntity kinetic) || !(machine instanceof KineticAccess access)
                || machine.isRemoved() || (!stress && !CreateKinetics.canDrive(kinetic))) return List.of();
        // 动力是整台机器/整根轴的属性，不冒充每个面的独立储罐；key 与方向无关以便去重。
        return List.of(new EmcResourcePort() {
            @Override public ResourceLocation unit() {
                return stress ? ResourceExchangeConversions.CREATE_STRESS : ResourceExchangeConversions.CREATE_SPEED;
            }
            @Override public String key() { return id() + "/kinetic/" + unit(); }
            @Override public long amount() {
                var lease = access.primordial$leaseIfPresent();
                return machine.getLevel() == null || lease == null ? 0 : lease.reserved(machine.getLevel().getGameTime(), stress);
            }
            @Override public long capacity() {
                return stress ? PrimordialServerConfig.createStressPerTick() : CreateKinetics.maxSpeed();
            }
            @Override public boolean canReceive() { return !machine.isRemoved(); }
            @Override public boolean canExtract() { return false; }
            @Override public long extract(long amount, boolean simulate) { return 0; }
            @Override public long receive(long amount, boolean simulate) {
                if (!canReceive() || machine.getLevel() == null || machine.getLevel().isClientSide) return 0;
                if (simulate) return Math.min(Math.max(0, amount), Math.max(0, capacity() - amount()));
                var lease = access.primordial$lease();
                long accepted = lease.receive(machine.getLevel().getGameTime(), stress, amount, capacity(), false);
                if (accepted > 0) com.primordial.resource.create.CreateKineticTicker.track(kinetic, lease);
                return accepted;
            }
        });
    }
}
