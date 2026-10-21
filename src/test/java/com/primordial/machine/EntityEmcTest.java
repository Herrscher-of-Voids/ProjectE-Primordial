/**
 * 【文件职责】验证实体类型基础定价、不受生命值增益影响，以及不可交易实体排除规则。
 * 【变更记录 #1】2026-10-14 16:08:40（第 1 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：无。新增：原版猪/僵尸/凋灵报价，玩家/投射物排除，融合半径与安全引力回归。
 * - 修改：采用已注册基础属性计算，生成与回收事务另在真实 Forge 隔离世界验证。
 * - 用途：防止属性加成抬价与范围合并公式回归。
 */
/**
 * 【变更记录 #2】2026-10-14 16:08:01（第 2 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：纯 JUnit 中初始化 Forge 默认生物属性的测试（缺少注册生命周期）。
 * - 新增：重复吸收拒绝断言；原版基础属性报价改由真实 Forge 探针验证。
 * - 修改：保留排除规则、融合体积和有限位移验证。
 * - 用途：测试边界匹配实际环境，避免未注册 swim_speed 的测试启动错误。
 */
package com.primordial.machine;
import com.primordial.service.EntityEmc;
import com.primordial.equipment.SingularityEntity;
import java.math.BigInteger;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EntityEmcTest {
    @BeforeAll static void bootstrap(){AdapterTestBootstrap.initialize();}
    @Test void entityCanOnlyBeAbsorbedOnce(){
        Entity entity=mock(Entity.class);var removed=new java.util.concurrent.atomic.AtomicBoolean();
        doReturn(EntityType.BOAT).when(entity).getType();when(entity.level()).thenReturn(mock(net.minecraft.server.level.ServerLevel.class));
        when(entity.isAlive()).thenAnswer(call->!removed.get());when(entity.isRemoved()).thenAnswer(call->removed.get());
        doAnswer(call->{removed.set(true);return null;}).when(entity).discard();
        assertEquals(BigInteger.valueOf(1024),EntityEmc.absorb(entity));
        assertEquals(BigInteger.ZERO,EntityEmc.absorb(entity));verify(entity,times(1)).discard();
    }
    @Test void excludedTypesCannotObtainEntityQuotes(){
        Player player=mock(Player.class);doReturn(EntityType.PLAYER).when(player).getType();
        Arrow arrow=mock(Arrow.class);doReturn(EntityType.ARROW).when(arrow).getType();
        assertEquals(BigInteger.ZERO,EntityEmc.price(player));assertEquals(BigInteger.ZERO,EntityEmc.price(arrow));
    }
    @Test void fusionConservesRangeVolumeAndKeepsMotionFinite(){
        float radius=SingularityEntity.mergedRadius(8,8);
        assertEquals(1024,Math.pow(radius,3),0.001);assertTrue(radius>8&&radius<16);
        Vec3 base=SingularityEntity.boundedPullVelocity(Vec3.ZERO,new Vec3(20,0,0),0.16,1);
        Vec3 merged=SingularityEntity.boundedPullVelocity(Vec3.ZERO,new Vec3(20,0,0),0.32,2);
        assertTrue(merged.length()>base.length());
        Vec3 huge=SingularityEntity.boundedPullVelocity(new Vec3(1e20,1e20,1e20),new Vec3(1e8,0,0),1e200,1e12);
        assertTrue(Double.isFinite(huge.lengthSqr()));assertTrue(huge.length()<=8.000001);
    }
}
