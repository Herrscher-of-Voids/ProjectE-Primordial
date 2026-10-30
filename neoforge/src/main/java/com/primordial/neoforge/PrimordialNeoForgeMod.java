/**
 * 【文件职责】NeoForge 模组入口及配置、网络和注册生命周期接线。
 * 【变更记录 #1】2026-10-22 21:44:20（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：独立 SERVER 回收配置注册。
 * - 修改：服务端以世界配置控制预算、学习与回收比例。
 * - 用途：沿用 Forge 行为参数，不混用客户端配置。
 * 【变更记录 #2】2026-10-23 13:30:35（第 2 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：知识 COMMON 快照与知识配方条件的 mod 总线接线。
 * - 修改：在数据包加载前注册共享规则，不引入可选模组或客户端类。
 * - 用途：凭证配方、EMC 与服务端授权在同一次启动使用相同规则。
 * 【变更记录 #3】2026-10-23 16:19:59（第 3 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无；保留必需依赖与既有生命周期。
 * - 新增：仅 FTB Quests 存在时反射注册阶段奖励，缺失或不兼容适配器记录警告后隔离。
 * - 修改：共同初始化排队工作在服务器任务数据创建前执行；公共入口没有可选 API 签名。
 * - 用途：可选任务奖励不把 FTB 变成强制依赖，也不需要额外启动 Mixin。
 */
package com.primordial.neoforge;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.config.NeoForgeClientConfig;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.knowledge.NeoForgeKnowledgeRequiredCondition;
import com.primordial.neoforge.network.NeoForgePayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(PrimordialNeoForgeMod.MOD_ID)
public final class PrimordialNeoForgeMod {
    public static final String MOD_ID = "primordial";
    private static final Logger LOGGER = LogUtils.getLogger();

    public PrimordialNeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForgeModRegistries.register(modEventBus);
        NeoForgeKnowledgeRequiredCondition.register(modEventBus);
        NeoForgeKnowledgeConfig.register(modContainer, modEventBus);
        NeoForgeClientConfig.register(modContainer);
        NeoForgeServerConfig.register(modContainer);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                com.primordial.neoforge.acceleration.AccelerationConfig.SPEC, "primordial-acceleration-server.toml");
        NeoForgePayloads.register(modEventBus);
        if (ModList.get().isLoaded("ae2")) {
            try {
                Class.forName("com.primordial.neoforge.compat.ae2.NeoForgeAe2StorageAdapter")
                        .getMethod("registerCapabilityListener", IEventBus.class).invoke(null, modEventBus);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                LOGGER.warn("[Primordial] AE2 node capability was skipped: incompatible optional API", failure);
            }
        }
        if (ModList.get().isLoaded("refinedstorage")) {
            try {
                Class.forName("com.primordial.neoforge.compat.rs2.NeoForgeRs2StorageAdapter")
                        .getMethod("registerCapabilityListener", IEventBus.class).invoke(null, modEventBus);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                LOGGER.warn("[Primordial] RS2 node capability was skipped: incompatible optional API", failure);
            }
        }
        modEventBus.addListener(this::onCommonSetup);
        LOGGER.info("[Primordial] NeoForge mod loading");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            top.theillusivec4.curios.api.CuriosApi.registerCurio(
                    NeoForgeModRegistries.BLACKHOLE_CHARM_ITEM.get(), NeoForgeModRegistries.BLACKHOLE_CHARM_ITEM.get());
            if (ModList.get().isLoaded("ftbquests")) {
                try {
                    Class.forName("com.primordial.neoforge.compat.ftbquests.NeoForgeFtbQuestRewards")
                            .getMethod("register").invoke(null);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    LOGGER.warn("[Primordial] FTB Quests stage rewards were skipped: incompatible optional API", failure);
                }
            }
            if (ModList.get().isLoaded("ae2")) {
                try {
                    Class.forName("com.primordial.neoforge.compat.ae2.NeoForgeAe2StorageAdapter")
                            .getMethod("register").invoke(null);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    LOGGER.warn("[Primordial] AE2 storage adapter was skipped: incompatible optional API", failure);
                }
            }
            if (ModList.get().isLoaded("refinedstorage")) {
                try {
                    Class.forName("com.primordial.neoforge.compat.rs2.NeoForgeRs2StorageAdapter")
                            .getMethod("register").invoke(null);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    LOGGER.warn("[Primordial] RS2 storage adapter was skipped: incompatible optional API", failure);
                }
            }
            if (ModList.get().isLoaded("beyonddimensions")) {
                try {
                    Class.forName("com.primordial.neoforge.compat.beyonddimensions.NeoForgeBeyondDimensionsStorageAdapter")
                            .getMethod("register").invoke(null);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                    LOGGER.warn("[Primordial] Beyond Dimensions storage adapter was skipped: incompatible optional API", failure);
                }
            }
            LOGGER.info("[Primordial] NeoForge common setup complete");
        });
    }
}
