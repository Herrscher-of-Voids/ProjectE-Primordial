/**
 * 【文件职责】在可选 FTB Quests 2101.1.37 注册两类原初个人阶段奖励。
 * 【变更记录 #1】2026-10-23 16:19:59（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无；不移植 Forge 的构造器 Mixin。
 * - 新增：流体、气体奖励工厂、图标及幂等注册；目标 API 不兼容时拒绝启用适配器。
 * - 修改：由共同初始化排队工作在 ModList 判断后反射调用，早于 ServerQuestFile 分配类型编号。
 * - 用途：任务文件和编辑器共用原奖励 ID；未安装 FTB 时公共类不解析可选 API。
 */
package com.primordial.neoforge.compat.ftbquests;

import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class NeoForgeFtbQuestRewards {
    public static RewardType FLUID_STAGE;
    public static RewardType GAS_STAGE;
    private static boolean registered;

    private NeoForgeFtbQuestRewards() {}

    public static synchronized void register() {
        if (registered) return;
        try {
            if (Reward.class.getMethod("claim", ServerPlayer.class, boolean.class).getReturnType() != boolean.class
                    || Reward.class.getMethod("tryClaim", ServerPlayer.class, boolean.class).getReturnType() != boolean.class)
                throw new IllegalStateException("Unsupported FTB Quests reward claim API; expected 2101.1.37");
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("Missing FTB Quests 2101.1.37 reward claim API", failure);
        }
        FLUID_STAGE = RewardTypes.register(ResourceLocation.fromNamespaceAndPath("primordial", "fluid_stage"),
                NeoForgeStageReward.Fluid::new, () -> Icon.getIcon("minecraft:item/water_bucket"))
                .setDisplayName(Component.translatable("reward.primordial.fluid_stage"));
        GAS_STAGE = RewardTypes.register(ResourceLocation.fromNamespaceAndPath("primordial", "gas_stage"),
                NeoForgeStageReward.Gas::new, () -> Icon.getIcon("minecraft:item/glass_bottle"))
                .setDisplayName(Component.translatable("reward.primordial.gas_stage"));
        registered = true;
    }
}
