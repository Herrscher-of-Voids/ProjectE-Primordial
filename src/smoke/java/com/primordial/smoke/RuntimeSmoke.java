/**
 * 【文件职责】独立测试模组在真实 Forge 服务端验证无 Mek 启动和 Create 动力链。
 * 【变更记录 #1】2026-09-16 20:09:32（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：无 Mek 普通机器任务及真实世界 Create 测试入口，结果写入隔离目录后自动停服。
 * - 修改：测试专用 sourceSet/独立 JAR，不进入正式模组。
 * - 用途：以实际启动和首 tick 覆盖替代仅凭 compileOnly 推断可选依赖安全。
 */
/**
 * 【变更记录 #2】2026-09-18 02:21:56（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：primordial.smoke.acceleration 属性选择独立加速运行探针。
 * - 修改：该模式在强制测试区块就绪后执行真实方块、实体及世界时钟断言，结果写盘后停服。
 * - 用途：验证正式 JAR 在无 Mek/AE2 环境的链接、加载与加速调用，探针不进入正式模组。
 */
/**
 * 【变更记录 #3】2026-10-02 19:32:17（第 3 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：primordial.smoke.nbt 属性选择 NBT/ProjectE 实际加载测试。
 * - 修改：NBT 模式在服务端启动后验证并停止，不运行其它探针。
 * - 用途：确认发行格式 Mixin 生效，测试不读取用户存档。
 */
/**
 * 【变更记录 #5】2026-10-14 13:36:00（第 5 次备注 · 1.2.0-blackhole-emc-ladder）
 * - 删除：无。
 * - 新增：独立装备探针结果标记为收集器 EMC 阶梯验证。
 * - 修改：设备类型从凝聚器改为能量收集器，避免烟测结果沿用过期名称。
 * - 用途：让隔离 Forge 探针日志准确对应实际验证的 ProjectE 机器。
 */
/**
 * 【变更记录 #4】2026-10-14 02:55:08（第 4 次备注 · 1.2.0-equipment-runtime-smoke）
 * - 删除：无。
 * - 新增：装备发布 Mixin、材料 EMC 与两代凝聚器实际转化探针入口。
 * - 修改：只有独立烟测参数启用该入口，探针仍不进入正式模组。
 * - 用途：验证正式反混淆产物，避免只依赖 JUnit 的无 Mixin 环境。
 */
/**
 * 【变更记录 #6】2026-10-14 14:40:00（第 6 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无。
 * - 新增：原生存储网络测试入口，预留 100 个实际服务器刻供网络发现。
 * - 修改：该模式完成后写入结果并自动停服。
 * - 用途：隔离验证 AE2、RS、超越维度直连，不进入玩家存档。
 */
/**
 * 【变更记录 #7】2026-10-14 16:08:40（第 7 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：无。新增：实体 EMC 与黑洞融合发布环境探针入口。
 * - 修改：仅 primordial.smoke.entities 开关执行，结果写盘后正常停服。
 * - 用途：隔离验证新系统，不访问测试实例的用户世界。
 */
/**
 * 【变更记录 #8】2026-10-14 17:12:00（第 8 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：两项原生魔法资源及黑洞转化桌销售的隔离启动参数。
 * - 修改：只在 primordial.smoke.magic 启用时执行并写结果停服。
 * - 用途：验证正式 JAR 与真实第三方 API，探针不进入玩家实例。
 */
/**
 * 【变更记录 #9】2026-10-14 21:10:00（第 9 次备注 · 1.2.0-mainstream-mod-native-emc）
 * - 删除：无。新增：compatEmc 隔离价表验证入口。
 * - 修改：仅测试参数开启时验证原生映射、销售及管理员覆盖，然后正常停服。
 * - 用途：验证发布 JAR 的价表实际进入 ProjectE，探针不部署到玩家实例。
 */
package com.primordial.smoke;

import com.primordial.compat.MekanismGasCompat;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.registry.ModBlocks;
import com.primordial.task.TaskExecutor;
import com.primordial.util.MachineKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod("primordial_smoke")
public class RuntimeSmoke {
    private static int ticks;
    private static boolean finished;
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000051");

    public RuntimeSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::started);
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void started(ServerStartedEvent event) {
        try {
            if (Boolean.getBoolean("primordial.smoke.compatEmc")) {
                CompatEmcRuntimeSmoke.run(event.getServer()); finish("PASS_NATIVE_COMPAT_EMC", null); return;
            }
            if (Boolean.getBoolean("primordial.smoke.magic")) {
                MagicRuntimeSmoke.run(event.getServer()); finish("PASS_PLAYER_MAGIC_AND_BLACKHOLE_SALES", null); return;
            }
            if(Boolean.getBoolean("primordial.smoke.entities")){
                EntityRuntimeSmoke.run(event.getServer());finish("PASS_ENTITY_EMC_FUSION",null);return;
            }
            if(Boolean.getBoolean("primordial.smoke.storage")){
                StorageRuntimeSmoke.prepare(event.getServer());return;
            }
            if (Boolean.getBoolean("primordial.smoke.equipment")) {
                EquipmentRuntimeSmoke.run(event.getServer());
                finish("PASS_EQUIPMENT_COLLECTOR_MIXINS", null);
                return;
            }
            if (Boolean.getBoolean("primordial.smoke.nbt")) {
                NbtRuntimeSmoke.run(event.getServer());
                finish("PASS_PROJECTE_NBT_MIXINS", null);
                return;
            }
            check(!ModList.get().isLoaded("mekanism"), "Mekanism must be absent");
            check(!MekanismGasCompat.loaded(), "Optional gas API must be disabled");
            var level = event.getServer().overworld();
            BlockPos chest = new BlockPos(0, 90, 0);
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            var cfg = MachineConfigSavedData.get(event.getServer()).getOrCreate(MachineKey.of(level.dimension(), chest), OWNER);
            cfg.fluidInEnabled = true;
            cfg.fluidIn[0].enabled = true;
            cfg.fluidIn[0].save();
            level.setBlockAndUpdate(chest.offset(0, 0, 2), ModBlocks.BLACKHOLE.get().defaultBlockState());
            TaskExecutor.runAll(event.getServer());
            if (Boolean.getBoolean("primordial.smoke.acceleration")) AccelerationRuntimeSmoke.prepare(event.getServer());
            if (ModList.get().isLoaded("create")) CreateRuntimeSmoke.start(event.getServer(), OWNER);
        } catch (Throwable failure) { finish("FAIL", failure); }
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.START) return;
        try {
            ticks++;
            if(Boolean.getBoolean("primordial.smoke.storage")){
                if(ticks>=100){StorageRuntimeSmoke.run(ServerLifecycleHooks.getCurrentServer());finish("PASS_NATIVE_STORAGE_NETWORKS",null);}
                return;
            }
            if (Boolean.getBoolean("primordial.smoke.acceleration")) {
                if (ticks >= 40) {
                    AccelerationRuntimeSmoke.run(ServerLifecycleHooks.getCurrentServer());
                    finish("PASS_ACCELERATION_WITHOUT_MEK_AE2_CREATE", null);
                }
            } else if (ModList.get().isLoaded("create")) {
                if (CreateRuntimeSmoke.tick(ServerLifecycleHooks.getCurrentServer(), ticks)) finish("PASS_CREATE_WITHOUT_MEK", null);
            } else if (ticks >= 40) finish("PASS_WITHOUT_MEK_AND_CREATE", null);
        } catch (Throwable failure) { finish("FAIL", failure); }
    }

    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static void finish(String result, Throwable failure) {
        finished = true;
        if (failure != null) failure.printStackTrace();
        try {
            Files.writeString(Path.of("smoke-result.txt"), result + " ticks=" + ticks + (failure == null ? "" : " " + failure));
        } catch (Exception io) { io.printStackTrace(); }
        ServerLifecycleHooks.getCurrentServer().halt(false);
    }
}
