/**
 * 【文件职责】将知识开关用于 Forge 数据包配方条件。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：primordial:knowledge_required 条件和序列化器。
 * - 修改：通过相同启动快照决定九张凭证配方是否加载，不依赖可选资源模组。
 * - 用途：关闭知识要求时无凭证配方；重新启动恢复规则时可重新加载。
 *
 * 【变更记录 #2】2026-10-02 14:30:00（第 2 次备注 · 1.2.0 凭证配方策略）
 * - 删除：无；液体和气体凭证配方已从资源目录移除，不再经过该条件加载。
 * - 新增：保留其它独立资源凭证的启动级条件判断。
 * - 修改：条件职责限定为仍存在的数据包配方，不再声称控制全部九种凭证。
 * - 用途：让整合包作者可自行添加液体/气体凭证配方，同时保持其它资源配方受知识开关控制。
 */
package com.primordial.recipe;

import com.google.gson.JsonObject;
import com.primordial.config.PrimordialKnowledgeConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/** 数据包加载时控制凭证配方；与服务端输出和原生 EMC 使用同一开关。 */
public final class KnowledgeRequiredCondition implements ICondition {
    public static final ResourceLocation ID = new ResourceLocation("primordial", "knowledge_required");
    public static final IConditionSerializer<KnowledgeRequiredCondition> SERIALIZER = new IConditionSerializer<>() {
        @Override public void write(JsonObject json, KnowledgeRequiredCondition value) { }
        @Override public KnowledgeRequiredCondition read(JsonObject json) { return new KnowledgeRequiredCondition(); }
        @Override public ResourceLocation getID() { return ID; }
    };
    @Override public ResourceLocation getID() { return ID; }
    @Override public boolean test(IContext context) { return PrimordialKnowledgeConfig.required(); }
}
