/**
 * 【文件职责】把 FTB Quests 编辑器和任务数据中的阶段 ID 发给实际领奖者 UUID。
 * 【变更记录 #1】2026-10-23 16:19:59（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无；FTB 自身继续管理任务、队伍进度和领奖状态。
 * - 新增：目标版 HolderLookup NBT、RegistryFriendlyByteBuf 网络读写、编辑字段和个人阶段授权。
 * - 修改：统一限定显式命名空间和 128 字符；错误线程、损坏账户或无效 ID 均不授予知识或阶段。
 * - 用途：奖励仅影响阶段存档；通知只发生在实际新增时，流体和气体子类避免父构造器提前读取未初始化类型。
 */
package com.primordial.neoforge.compat.ftbquests;

import java.util.regex.Pattern;

import com.primordial.neoforge.stage.NeoForgePlayerStagesSavedData;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.reward.Reward;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public abstract class NeoForgeStageReward extends Reward {
    private static final Pattern STAGE_FORMAT = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private String stage = "";

    private NeoForgeStageReward(long id, Quest quest) {
        super(id, quest);
    }

    public static boolean validStage(String value) {
        return value != null && value.length() <= 128 && STAGE_FORMAT.matcher(value).matches()
                && NeoForgePlayerStagesSavedData.validStage(ResourceLocation.tryParse(value));
    }

    private void setStage(String value) {
        stage = validStage(value) ? value : "";
    }

    @Override public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("stage", stage);
    }

    @Override public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        setStage(tag.getString("stage"));
    }

    @Override public void writeNetData(RegistryFriendlyByteBuf buffer) {
        super.writeNetData(buffer);
        buffer.writeUtf(stage, 128);
    }

    @Override public void readNetData(RegistryFriendlyByteBuf buffer) {
        super.readNetData(buffer);
        setStage(buffer.readUtf(128));
    }

    @Override public void fillConfigGroup(ConfigGroup group) {
        super.fillConfigGroup(group);
        group.addString("stage", stage, this::setStage, "", STAGE_FORMAT)
                .setNameKey("reward.primordial.stage_id");
    }

    @Override public boolean claim(ServerPlayer player, boolean notify) {
        ResourceLocation id = ResourceLocation.tryParse(stage);
        if (player == null || player.server == null || !player.server.isSameThread()
                || !NeoForgePlayerStagesSavedData.validStage(id)) return false;
        var data = NeoForgePlayerStagesSavedData.get(player.server);
        if (!data.available(player.getUUID())) return false;
        boolean granted = data.grant(player.getUUID(), id);
        if (granted && notify)
            player.sendSystemMessage(Component.translatable(messagePrefix() + ".granted", id.toString()));
        // FTB 1.37 marks a personal claim only on true. An already-held stage is a
        // fulfilled reward, while a refused/invalid grant must stay claimable.
        return granted || data.has(player.getUUID(), id);
    }

    @Override public Component getAltTitle() {
        return Component.translatable(messagePrefix());
    }

    private String messagePrefix() {
        return this instanceof Gas ? "reward.primordial.gas_stage" : "reward.primordial.fluid_stage";
    }

    public static final class Fluid extends NeoForgeStageReward {
        public Fluid(long id, Quest quest) { super(id, quest); }
        @Override public RewardType getType() { return NeoForgeFtbQuestRewards.FLUID_STAGE; }
    }

    public static final class Gas extends NeoForgeStageReward {
        public Gas(long id, Quest quest) { super(id, quest); }
        @Override public RewardType getType() { return NeoForgeFtbQuestRewards.GAS_STAGE; }
    }
}
