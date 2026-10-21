/**
 * 【文件职责】在配置器结算完成后，为已购买动力的方块统一准备下一 tick 动力状态。
 * 【变更记录 #1】2026-09-16 20:30:43（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：仅跟踪已付款方块的服务端 END/LOWEST 回调，以及失效、卸载和停服清理。
 * - 修改：动力到期不依赖目标方块是否仍在执行自身 tick，稳定供给不重建网络。
 * - 用途：防止区块停止 ticking 后残留免费应力；不扫描所有 Create 方块，不持久化付款授权。
 */
package com.primordial.resource.create;

import com.primordial.PrimordialMod;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** 所有跟踪表读写都发生在服务端线程；可选适配器构造时才注册事件。 */
public final class CreateKineticTicker {
    private static final Map<KineticBlockEntity, KineticLease> ACTIVE = new IdentityHashMap<>();
    private static boolean registered;
    private CreateKineticTicker() {}

    public static synchronized void register() {
        if (registered) return;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, CreateKineticTicker::tick);
        MinecraftForge.EVENT_BUS.addListener(CreateKineticTicker::unload);
        MinecraftForge.EVENT_BUS.addListener(CreateKineticTicker::stopped);
        registered = true;
    }

    public static void track(KineticBlockEntity machine, KineticLease lease) { ACTIVE.put(machine, lease); }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var machine = entry.getKey();
            var level = machine.getLevel();
            if (machine.isRemoved() || level == null || !level.hasChunkAt(machine.getBlockPos())
                    || level.getBlockEntity(machine.getBlockPos()) != machine) {
                iterator.remove();
                continue;
            }
            try {
                CreateKinetics.advance(machine, entry.getValue(), level.getGameTime() + 1);
                if (entry.getValue().activeSpeed == 0 && entry.getValue().activeStress == 0) iterator.remove();
            } catch (RuntimeException | LinkageError failure) {
                // 隔离单台机器的第三方异常；移除失败目标，避免每 tick 同一异常刷屏。
                entry.getValue().activeSpeed = 0;
                entry.getValue().activeStress = 0;
                iterator.remove();
                PrimordialMod.LOGGER.error("[Primordial] Create kinetic update failed at {}", machine.getBlockPos(), failure);
            }
        }
    }

    private static void unload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) ACTIVE.keySet().removeIf(machine -> machine.getLevel() == event.getLevel());
    }

    private static void stopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}
