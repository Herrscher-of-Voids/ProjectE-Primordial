/**
 * 【变更记录 #1】2026-10-14 02:22:31（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：使用被破坏方块自己的原版三维模型和材质，旋转表现方块被吸入，而非粒子贴片。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.client;
import com.primordial.equipment.SingularityDebrisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
public final class SingularityDebrisRenderer extends EntityRenderer<SingularityDebrisEntity> {
    private final BlockRenderDispatcher blocks;
    public SingularityDebrisRenderer(EntityRendererProvider.Context context){super(context);blocks=context.getBlockRenderDispatcher();}
    @Override public ResourceLocation getTextureLocation(SingularityDebrisEntity entity){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(SingularityDebrisEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount+partial)*9));pose.mulPose(Axis.ZP.rotationDegrees((entity.tickCount+partial)*5));
        pose.scale(0.7F,0.7F,0.7F);pose.translate(-0.5,-0.5,-0.5);
        blocks.renderSingleBlock(entity.block(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
    }
}
