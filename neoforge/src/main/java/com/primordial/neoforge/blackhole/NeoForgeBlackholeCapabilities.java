/**
 * 【文件职责】为黑洞各面及无面查询注册 NeoForge 原生资源能力。
 * 【变更记录 #1】2026-10-22 21:44:20（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：物品、流体、FE 三类方块实体 provider。
 * - 修改：具体接收与模拟由同一方块实体端口处理。
 * - 用途：支持管道接入且由端口实时检查方块是否仍有效。
 * 【变更记录 #2】2026-10-22 22:20:00（第 2 次备注 · 2.0.0-neo-dev 可选化学品能力）
 * - 删除：无。
 * - 新增：仅在 Mekanism 存在后解析独立适配器的反射边界。
 * - 修改：API 缺失或链接不兼容时只跳过化学品端口，保留原生三类端口。
 * - 用途：公共自动订阅器不持有可选 API 类型，支持未安装 Mekanism 的正常启动。
 */
package com.primordial.neoforge.blackhole;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.fml.ModList;
import com.mojang.logging.LogUtils;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeBlackholeCapabilities {
    private NeoForgeBlackholeCapabilities() {}

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, NeoForgeModRegistries.BLACKHOLE_ENTITY.get(),
                (block, side) -> block.itemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, NeoForgeModRegistries.BLACKHOLE_ENTITY.get(),
                (block, side) -> block.fluidHandler());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, NeoForgeModRegistries.BLACKHOLE_ENTITY.get(),
                (block, side) -> block.energyHandler());
        if (ModList.get().isLoaded("mekanism")) {
            try {
                Class.forName("com.primordial.neoforge.compat.NeoForgeMekanismRecovery")
                        .getMethod("register", RegisterCapabilitiesEvent.class).invoke(null, event);
            } catch (ReflectiveOperationException | LinkageError failure) {
                LogUtils.getLogger().warn("[Primordial] Optional Mekanism chemical recovery unavailable; native ports remain active", failure);
            }
        }
    }
}
