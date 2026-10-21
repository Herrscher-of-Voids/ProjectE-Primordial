/**
 * 【文件职责】FTB Quests 主模组构造完成后注册原初流体/气体阶段奖励类型。
 * 【变更记录 #1】2026-10-13 04:09:00（第 1 次备注 · 1.2.0-ftb-stage-rewards）
 * - 删除：移除将普通类 Mixin 错误指向 RewardTypes 接口的注入器；该版本 Mixin 报告目标类型不匹配。
 * - 新增：在 FTBQuests 构造器尾部调用原初奖励注册，早于任务文件解析和编辑器展示。
 * - 修改：目标类通过字符串和 @Pseudo 表达为可选依赖，并由 PrimordialMixinPlugin 检测 ftbquests 后加载。
 * - 用途：确保带奖励和空白任务文件都能在编辑器中选择原初阶段奖励；没有 FTB Quests 时主模组照常运行。
 */
package com.primordial.mixin;

import com.primordial.compat.ftbquest.PrimordialFtbQuestRewards;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.FTBQuests", remap = false)
public abstract class FtbQuestBootstrapMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void primordial$registerRewards(CallbackInfo callback) {
        PrimordialFtbQuestRewards.register();
    }
}
