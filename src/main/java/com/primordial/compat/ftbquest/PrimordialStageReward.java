/**
 * 【文件职责】实现 FTB Quests 可编辑奖励，领取后向领取者发放一个原初阶段。
 * 【变更记录 #1】2026-10-13（第 1 次备注 · FTB 阶段奖励）
 * - 删除：无。新增：阶段编辑、NBT/网络序列化、流体/气体奖励类型和个人阶段授予。
 * - 修改：任务进度和奖励领取仍由 FTB Quests 管理；阶段仅写入领取者 UUID。
 * - 用途：任务奖励授权与 ResourceStages 共用阶段 ID；买入受阶段限制，卖入学习不受影响。
 * 【变更记录 #2】2026-10-13 03:56:00（第 2 次备注 · 1.2.0-ftb-stage-rewards）
 * - 删除：无；保留旧备注和 FTB Quests 原有领奖、任务进度管理。
 * - 新增：限制阶段 ID 为有效命名空间格式且不超过 128 字符；配置编辑、NBT 读写及网络读写都走同一校验。
 * - 修改：领奖只在服务器主线程执行，并把 stage 发给领奖者 UUID；无效 ID、错误线程或未绑定服务器均提前返回。
 * - 用途：由 FTB 任务奖励解锁现有 ResourceStages 阶段；仅改变流体/气体购买和自动补充资格，不限制卖入学习。
 */
package com.primordial.compat.ftbquest;

import com.primordial.data.PlayerStagesSavedData;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.regex.Pattern;

public final class PrimordialStageReward extends Reward {
    private static final Pattern STAGE_FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private final boolean gas;
    private String stage = "";

    public PrimordialStageReward(long id, Quest quest, boolean gas) {
        super(id, quest);
        this.gas = gas;
    }

    public static boolean validStage(String value) {
        return value != null && value.length() <= 128 && STAGE_FORMAT.matcher(value).matches()
                && PlayerStagesSavedData.validStage(ResourceLocation.tryParse(value));
    }

    private void setStage(String value) {
        stage = validStage(value) ? value : "";
    }

    @Override public RewardType getType() {
        return gas ? PrimordialFtbQuestRewards.GAS_STAGE : PrimordialFtbQuestRewards.FLUID_STAGE;
    }

    @Override public void writeData(CompoundTag tag) {
        super.writeData(tag);
        tag.putString("stage", stage);
    }

    @Override public void readData(CompoundTag tag) {
        super.readData(tag);
        setStage(tag.getString("stage"));
    }

    @Override public void writeNetData(FriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(stage, 128);
    }

    @Override public void readNetData(FriendlyByteBuf buffer) {
        super.readNetData(buffer);
        setStage(buffer.readUtf(128));
    }

    @Override public void fillConfigGroup(ConfigGroup group) {
        super.fillConfigGroup(group);
        group.addString("stage", stage, this::setStage, "")
                .setNameKey("reward.primordial.stage_id");
    }

    @Override public void claim(ServerPlayer player, boolean notify) {
        ResourceLocation id = ResourceLocation.tryParse(stage);
        if (player == null || player.server == null || !player.server.isSameThread()
                || !PlayerStagesSavedData.validStage(id)) return;
        boolean changed = PlayerStagesSavedData.get(player.server).grant(player.getUUID(), id);
        if (changed && notify) {
            player.sendSystemMessage(Component.translatable(
                    gas ? "reward.primordial.gas_stage.granted" : "reward.primordial.fluid_stage.granted", id));
        }
    }

    @Override public Component getAltTitle() {
        return Component.translatable(gas ? "reward.primordial.gas_stage" : "reward.primordial.fluid_stage");
    }
}
