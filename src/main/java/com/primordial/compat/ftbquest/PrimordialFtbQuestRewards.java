/**
 * 【文件职责】在 FTB Quests 注册原初流体与气体阶段奖励。
 * 【变更记录 #1】2026-10-13（第 1 次备注 · FTB 阶段奖励）
 * - 删除：无。新增：通过 FTB Quests 2001.4.22 的 RewardTypes API 注册两种可编辑奖励。
 * - 修改：无。由可选 Mixin 在 FTB 注册表初始化尾部调用。
 * - 用途：整合包作者可在任务奖励中发放原初阶段，不再手工维护任务 ID 映射。
 * 【变更记录 #2】2026-10-13 03:56:00（第 2 次备注 · 1.2.0-ftb-stage-rewards）
 * - 删除：无；保留 FTB Quests 注册表原有奖励。
 * - 新增：两种奖励工厂分别创建流体和气体阶段奖励，图标和显示名按类型区分。
 * - 修改：由 RewardTypes 的可选 Mixin 调用 register；未安装 FTB Quests 时不加载本类。
 * - 用途：让整合包作者将阶段作为 FTB 奖励配置，而资源对应阶段仍由服务器流体/气体规则决定。
 * 【变更记录 #3】2026-10-13 04:09:00（第 3 次备注 · 1.2.0-ftb-stage-rewards）
 * - 删除：无；保留 FTB Quests 内建奖励注册。
 * - 新增：注册方法幂等保护，启动 Mixin 重复触发时不会重复更换奖励类型对象。
 * - 修改：注册入口由 FTB 主模组构造器调用，早于任务奖励解析和编辑器列表读取。
 * - 用途：让可选奖励类型稳定进入 FTB RewardTypes 注册表；奖励领取逻辑仍委托对应 StageReward。
 */
package com.primordial.compat.ftbquest;

import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import dev.ftb.mods.ftblibrary.icon.Icon;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class PrimordialFtbQuestRewards {
    public static RewardType FLUID_STAGE;
    public static RewardType GAS_STAGE;

    private PrimordialFtbQuestRewards() { }

    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        FLUID_STAGE = RewardTypes.register(new ResourceLocation("primordial", "fluid_stage"),
                (id, quest) -> new PrimordialStageReward(id, quest, false),
                () -> Icon.getIcon("minecraft:item/water_bucket"))
                .setDisplayName(Component.translatable("reward.primordial.fluid_stage"));
        GAS_STAGE = RewardTypes.register(new ResourceLocation("primordial", "gas_stage"),
                (id, quest) -> new PrimordialStageReward(id, quest, true),
                () -> Icon.getIcon("minecraft:item/glass_bottle"))
                .setDisplayName(Component.translatable("reward.primordial.gas_stage"));
        registered = true;
    }
}
