/**
 * 变更记录 #3（2026-09-13）：统一通过 EmcService 门面访问余额，并完成 ProjectE/Curios API 审计。
 * - 新增：能量换算配置键 energy_to_emc_rate 与 emc_to_energy_rate。
 * - 修改：业务层不再直接调用 ProjectECompat，保证后续适配器共享同一 EMC 结算规则。
 */
/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
/**
 * 变更记录 #4（2026-09-14）：玩家登录时合并离线回收产生的 ProjectE 知识队列。
 * - 新增：调用 ProjectECompat.mergePendingKnowledge，并继续执行 EMC 队列同步。
 * - 目的：离线玩家也能在再次登录后获得自动学习结果。
 */
/**
 * 【变更记录 #5】2026-09-15 15:11:36（第 5 次备注 · 1.0.0-emc-service）
 * - 删除：服务器启动时旧自动估价扫描。
 * - 新增：登录同步人工价格、服务端关闭清理价格缓存。
 * - 修改：每个服务器启动重读价表；ProjectE 自己管理配方价格。
 * - 用途：切换存档不带入上个世界快照，客户端 Tooltip 与服务端人工覆盖一致。
 */
/**
 * 【变更记录 #6】2026-09-15 15:02:00（第 6 次备注 · 1.0.0-stage7-recipe-emc）
 * - 删除：服务器启动只加载人工价格而不计算未知物品的路径。
 * - 新增：在服务端即将启动时扫描 RecipeManager，并把配方价格发布到 ValueManager 只读快照。
 * - 修改：停服时连同配方快照一起清理，避免不同存档的注册表和配方价格互相泄漏。
 * - 用途：配方计算只发生在生命周期边界；运行中的机器、黑洞和 Tooltip 继续使用 O(1) 价格查询。
 */
/**
 * 【变更记录 #7】2026-09-16 01:52:17（第 7 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：启动时建立独立物品估价快照和登录时发送本模组物品覆盖包。
 * - 新增：无；ProjectE 自身的重载监听器负责物品重算、登录同步及知识更新。
 * - 修改：本启动入口只加载流体/气体扩展价格，停服同时释放当前原生重算上下文。
 * - 用途：防止独立估价遮蔽 ProjectE，避免额外启动扫描及两套同步流量。
 */
package com.primordial;

import com.primordial.command.PrimordialCommands;
import com.primordial.service.EmcService;
import com.primordial.network.PrimordialNetwork;
import com.primordial.network.S2CSyncBalancePacket;
import com.primordial.task.TaskScheduler;
import com.primordial.value.ValueManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Forge bus 事件桥 */
@Mod.EventBusSubscriber(modid = PrimordialMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModEvents {
    private ModEvents() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ValueManager.reload();
    }

    /** 停服释放当前服务端价表，下一存档重新加载。 */
    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        ValueManager.clear();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PrimordialCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EmcService.mergePending(player);
            EmcService.mergePendingKnowledge(player);
            java.math.BigInteger emc = EmcService.balance(player.server, player.getUUID());
            PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CSyncBalancePacket(emc));
        }
    }

    // TaskScheduler.onServerTick 自行订阅（见 TaskScheduler）
    @SuppressWarnings("unused")
    private static void unusedRef() {
        TaskScheduler.class.getName();
    }
}
