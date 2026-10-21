/**
 * 【文件职责】统一物品及九种资源的 ProjectE 知识校验。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：移除资源输出绕过知识授权的路径。
 * - 新增：流体、气体、FE与六种独立单位的凭证映射和物品输出策略。
 * - 修改：缓存只读凭证样本，授权每次查玩家真实知识；关闭配置时统一放行。
 * - 用途：普通物品检查自身知识，不把凭证背包持有或其它类别凭证当成已学习。
 * 【变更记录 #2】2026-10-02 00:00:00（1.2.0-core-trading）
 * - 新增：凭证识别与类别归一化学习入口。
 * - 修改：液体、气体凭证回收后学习固定类别样本，确保可解锁该类别的全部资源；普通物品仍保留完整 NBT 知识。
 * - 用途：凭证可由整合包作者自行提供配方，但必须实际卖入转化桌后才授予类别访问权。
 */
/**
 * 【变更记录 #3】2026-10-02 16:07:00（第 3 次备注 · 1.2.0-media-learning）
 * - 删除：流体和气体购买仅能依靠类别凭证的限制。
 * - 新增：按注册名查询与记录玩家直接回收的流体、气体知识。
 * - 修改：类别凭证继续优先授权全部同类资源；世界存储不可用时跳过附加学习，不中断回收结算。
 * - 用途：直接卖入的介质只解锁自身，凭证保留整类解锁能力。
 */
/**
 * 【变更记录 #4】2026-10-02 18:48:00（第 4 次备注 · 1.2.0-media-forgetting）
 * - 删除：凭证及免知识配置无条件覆盖单种遗忘的授权顺序。
 * - 新增：显式遗忘优先判断和服务端单种遗忘入口。
 * - 修改：重新卖入同种介质清除排除记录；非法、未解锁资源不得新增任意遗忘记录。
 * - 用途：目录、转化桌购买和机器供给共用知识门禁，遗忘后不会从其它入口继续生成。
 */
/**
 * 【变更记录 #5】2026-10-02 19:05:26（第 5 次备注 · 1.2.0-resource-stages）
 * - 删除：凭证与免知识配置不经过任务阶段校验的购买路径。
 * - 新增：allowsMedia 在知识判断前检查 ResourceStages。
 * - 修改：目录和实际购买、机器补充共用阶段门禁；学习仍不受阶段限制。
 * - 用途：未达阶段或撤销阶段后不得生成资源，原知识及显式遗忘记录保留。
 */
/**
 * 【变更记录 #6】2026-10-02 19:15:08（第 6 次备注 · 1.2.0-item-supply-stages）
 * - 删除：物品自动补充只检查知识而绕过任务阶段的路径。
 * - 新增：allowsItem 在知识检查前按物品注册名查询阶段限制。
 * - 修改：已有知识或关闭知识要求也须满足阶段；不改变物品学习、回收和凭证类别授权。
 * - 用途：背包和机器公共补充入口统一拒绝未达阶段的物品，不扣费、不修改原库存。
 */
/**
 * 【变更记录 #7】2026-10-02 19:35:00（第 7 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：带容器内容或额外能力状态的样本可凭空壳知识自动补充的路径。
 * - 新增：物品补充前调用 NBT 安全生成检查。
 * - 修改：空容器仍按知识/阶段/价格补充，危险样本拒绝时不消耗资源。
 * - 用途：统一保护背包及机器，不让清除容器内容的学习规则被自动化绕过。
 */
/**
 * 【变更记录 #8】2026-10-13 19:40:05（第 8 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：购买授权前检查 BUY 规则；真实学习前检查 LEARN 规则，学习禁用不删除旧知识，任务阶段仍不限制卖入学习。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #9】2026-10-13 20:14:16（第 9 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
/**
 * 【变更记录 #10】2026-10-13 23:59:40（第 10 次备注 · 1.2.0-discovery-learning-system）
 * - 删除：卖入学习或界面遗忘后存储网络继续缓存旧目录的路径。
 * - 新增：知识实际变化时失效该玩家的网络目录，重复学习不触发重建。
 * - 修改：手动交易、机器、黑洞共用此入口，与开放知识 API 保持刷新行为一致。
 * - 用途：下次目录查询立即反映学习/遗忘，保留个人存档和作者凭证授权语义。
 */
/**
 * 【变更记录 #11】2026-10-14 17:12:00（第 11 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：铁魔法法力、Goety 灵魂凭证与原生单位一一映射。
 * - 修改：继续用 ProjectE 已学习凭证检查授权，持有凭证不算学习。
 * - 用途：新增玩家资源遵守已有知识开关及开放阶段接口。
 */
package com.primordial.service;

import com.primordial.compat.ProjectECompat;
import com.primordial.config.PrimordialKnowledgeConfig;
import com.primordial.data.MediaKnowledgeSavedData;
import com.primordial.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

/** 九类资源共用同一知识门禁；只查 ProjectE，背包持有凭证不等于已学习。 */
public final class ResourceKnowledge {
    public enum Kind {
        FLUID("fluid", null), GAS("gas", null), ENERGY("energy", "forge:fe"),
        MANA("mana", "botania:mana"), PRESSURE("pressure", "pneumaticcraft:pressure"),
        AURA("aura", "naturesaura:aura"), SOURCE("source", "ars_nouveau:source"),
        SPEED("speed", "create:rotation_speed"), STRESS("stress", "create:stress"),
        IRON_MANA("iron_mana", "irons_spellbooks:mana"), SOUL_ENERGY("soul_energy", "goety:soul_energy");
        private final String id;
        private final ResourceLocation unit;
        private ItemStack sample;
        Kind(String id, String unit) { this.id = id; this.unit = unit == null ? null : new ResourceLocation(unit); }
        public String itemId() { return id + "_certificate"; }
        ItemStack sample() {
            if (sample == null) sample = new ItemStack(ModItems.CERTIFICATES.get(this).get());
            return sample;
        }
    }
    private ResourceKnowledge() { }
    public static boolean allowsItem(MinecraftServer server, UUID owner, ItemStack sample) {
        return sample != null && !sample.isEmpty()
                && TransmutationNbtPolicy.canGenerate(sample)
                && ResourceStages.allowsItem(server, owner, net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(sample.getItem()))
                && (!PrimordialKnowledgeConfig.required()
                || ProjectECompat.hasKnowledge(server, owner, sample));
    }
    public static boolean allows(MinecraftServer server, UUID owner, Kind kind) {
        if (kind == null) return false;
        if (kind.unit != null && !ResourceStages.allowsResource(server, owner, kind.unit)) return false;
        if (!PrimordialKnowledgeConfig.required()) return true;
        return ProjectECompat.hasKnowledge(server, owner, kind.sample());
    }
    public static boolean allowsMedia(MinecraftServer server, UUID owner, Kind kind, ResourceLocation id) {
        if (kind != Kind.FLUID && kind != Kind.GAS || id == null || owner == null) return false;
        if (!ResourceStages.allowsMedia(server, owner, kind == Kind.GAS, id)) return false;
        if (server != null && server.overworld() != null
                && MediaKnowledgeSavedData.get(server).isForgotten(owner, kind == Kind.GAS, id)) return false;
        if (!PrimordialKnowledgeConfig.required()) return true;
        if (allows(server, owner, kind)) return true;
        return server != null && server.overworld() != null
                && MediaKnowledgeSavedData.get(server).knows(owner, kind == Kind.GAS, id);
    }

    public static boolean forgetMedia(MinecraftServer server, UUID owner, Kind kind, ResourceLocation id) {
        if (server == null || owner == null || id == null || (kind != Kind.FLUID && kind != Kind.GAS)
                || server.overworld() == null) return false;
        if (!server.isSameThread()) throw new IllegalStateException("Media forgetting requires the server thread");
        boolean valid = kind == Kind.GAS ? com.primordial.compat.MekanismGasCompat.validGas(id)
                : net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id)
                && net.minecraft.core.registries.BuiltInRegistries.FLUID.get(id).defaultFluidState().isSource();
        if (!valid || !allowsMedia(server, owner, kind, id)) return false;
        boolean changed = MediaKnowledgeSavedData.get(server).forget(owner, kind == Kind.GAS, id);
        if (changed) TransmutationNetworkTrade.invalidate(owner);
        return changed;
    }

    /** 仅在真实回收成功后调用；持有容器或模拟传输不授予知识。 */
    public static void learnMedia(MinecraftServer server, UUID owner, Kind kind, ResourceLocation id) {
        if (server == null || owner == null || id == null || (kind != Kind.FLUID && kind != Kind.GAS)) return;
        if (server.overworld() == null) return;
        if (!server.isSameThread()) throw new IllegalStateException("Media learning requires the server thread");
        if (MediaKnowledgeSavedData.get(server).learn(owner, kind == Kind.GAS, id))
            TransmutationNetworkTrade.invalidate(owner);
    }
    public static boolean allows(MinecraftServer server, UUID owner, ResourceLocation unit) {
        for (Kind kind : Kind.values()) {
            if (unit != null && unit.equals(kind.unit)) {
                return ResourceStages.allowsResource(server, owner, unit)
                        && allows(server, owner, kind);
            }
        }
        return false;
    }

    /** 返回堆栈对应的资源凭证类别；非凭证物品返回 null。 */
    public static Kind certificateKind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Kind kind : Kind.values()) {
            var registered = ModItems.CERTIFICATES.get(kind);
            if (registered != null && registered.isPresent() && stack.is(registered.get())) return kind;
        }
        return null;
    }

    /** 将回收物品转换为应写入 ProjectE 的知识样本。 */
    public static ItemStack knowledgeSample(ItemStack stack) {
        Kind kind = certificateKind(stack);
        return kind == null ? stack : kind.sample();
    }
}
