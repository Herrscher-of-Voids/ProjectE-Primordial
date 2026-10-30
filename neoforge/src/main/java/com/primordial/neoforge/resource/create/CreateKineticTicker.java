/**
 * 【文件职责】在配置器结算完成后，为已购买动力的方块统一准备下一 tick 动力状态。
 * 【变更记录 #1】2026-09-16 20:30:43（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：仅跟踪已付款方块的服务端 END/LOWEST 回调，以及失效、卸载和停服清理。
 * - 修改：动力到期不依赖目标方块是否仍在执行自身 tick，稳定供给不重建网络。
 * - 用途：防止区块停止 ticking 后残留免费应力；不扫描所有 Create 方块，不持久化付款授权。
 */
package com.primordial.neoforge.resource.create;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.IdentityHashMap;
import java.util.Map;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;

/** 所有跟踪表读写都发生在服务端线程；可选适配器构造时才注册事件。 */
public final class CreateKineticTicker {
    private static final Map<KineticBlockEntity, KineticLease> ACTIVE = new IdentityHashMap<>();
    private static boolean registered;
    private CreateKineticTicker() {}

    public static synchronized void register() {
        if (registered) return;
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CreateKineticTicker::tick);
        NeoForge.EVENT_BUS.addListener(CreateKineticTicker::unload);
        NeoForge.EVENT_BUS.addListener(CreateKineticTicker::stopped);
        registered = true;
    }

    public static void track(KineticBlockEntity machine, KineticLease lease) { ACTIVE.put(machine, lease); }

    private static void tick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        // Native onSpeedChanged callbacks may reserve another machine while this pass is running.
        for (var entry : new IdentityHashMap<>(ACTIVE).entrySet()) {
            var machine = entry.getKey();
            var level = machine.getLevel();
            if (machine.isRemoved() || level == null || !level.hasChunkAt(machine.getBlockPos())
                    || level.getBlockEntity(machine.getBlockPos()) != machine) {
                ACTIVE.remove(machine);
                continue;
            }
            try {
                CreateKinetics.advance(machine, entry.getValue(), level.getGameTime() + 1);
                if (entry.getValue().activeSpeed == 0 && entry.getValue().activeStress == 0) ACTIVE.remove(machine);
            } catch (RuntimeException | LinkageError failure) {
                // 隔离单台机器的第三方异常；移除失败目标，避免每 tick 同一异常刷屏。
                entry.getValue().activeSpeed = 0;
                entry.getValue().activeStress = 0;
                ACTIVE.remove(machine);
                LogUtils.getLogger().error("[Primordial] Create kinetic update failed at {}", machine.getBlockPos(), failure);
            }
        }
    }

    private static void unload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) ACTIVE.keySet().removeIf(machine -> machine.getLevel() == event.getLevel());
    }

    private static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}
