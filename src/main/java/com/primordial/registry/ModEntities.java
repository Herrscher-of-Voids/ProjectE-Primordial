/**
 * 【变更记录 #1】2026-10-14 02:21:23（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：注册服务端黑洞场及被吸引方块实体，通过原版实体跟踪同步；不使用粒子代替模型。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.registry;
import com.primordial.PrimordialMod;
import com.primordial.equipment.SingularityEntity;
import com.primordial.equipment.SingularityDebrisEntity;
import net.minecraft.world.entity.*;
import net.minecraftforge.registries.*;
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,PrimordialMod.MODID);
    public static final RegistryObject<EntityType<SingularityEntity>> SINGULARITY=ENTITIES.register("singularity",()->
            EntityType.Builder.<SingularityEntity>of(SingularityEntity::new,MobCategory.MISC).sized(1,1).clientTrackingRange(16).updateInterval(1).fireImmune().build("primordial:singularity"));
    public static final RegistryObject<EntityType<SingularityDebrisEntity>> DEBRIS=ENTITIES.register("singularity_debris",()->
            EntityType.Builder.<SingularityDebrisEntity>of(SingularityDebrisEntity::new,MobCategory.MISC).sized(0.7F,0.7F).clientTrackingRange(12).updateInterval(2).fireImmune().build("primordial:singularity_debris"));
    private ModEntities() { }
}
