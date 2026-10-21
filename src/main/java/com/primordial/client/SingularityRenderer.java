/**
 * 【变更记录 #1】2026-10-14 02:22:31（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：黑洞球面＋厚度吸积盘的3D网格实体渲染，独立64×32贴图自发光；不注册或生成粒子。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 03:32:08（第 2 次备注 · 1.2.0-singularity-animation）
 * - 删除：黑洞瞬间完整出现、寿命结束直接消失的视觉路径。
 * - 新增：球体与厚吸积盘独立缩放、平滑淡入淡出与坍缩加速旋转，不新增粒子或替换黑洞贴图。
 * - 修改：服务端同步出生进度与末段寿命，客户端插值三维模型缩放和透明度。
 * - 用途：黑洞展开、坍缩均随实际寿命；保留指数寿命与原EMC结算。
 */
package com.primordial.client;
import com.primordial.equipment.SingularityEntity;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class SingularityRenderer extends EntityRenderer<SingularityEntity> {
    private static final ResourceLocation TEXTURE=new ResourceLocation("primordial","textures/entity/singularity.png");
    public SingularityRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(SingularityEntity entity){return TEXTURE;}
    @Override public void render(SingularityEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float open=smooth(entity.openingAge(partial)/SingularityEntity.OPEN_TICKS);
        float end=smooth(entity.endingTicks(partial)/SingularityEntity.CLOSE_TICKS);
        float core=open*end,disc=smooth(entity.openingAge(partial)/9F)*(float)Math.pow(end,1.6);
        int alpha=Math.round(255*Math.min(open,end));
        if(alpha<=0||core<=0.0001F)return;
        float size=(float)(0.75+Math.log1p(entity.radius()/8)*0.4);
        VertexConsumer vertices=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        pose.pushPose();pose.mulPose(Axis.ZP.rotationDegrees(16));
        // 球体从奇点展开；末段吸积盘先向内卷缩，随后核心收成一点并淡出，始终使用原三维网格。
        pose.pushPose();pose.scale(size*core,size*core,size*core);
        for(int lat=0;lat<12;lat++)for(int lon=0;lon<24;lon++){
            sphere(vertices,pose,(lat+1)/12.0,lon/24.0,alpha);sphere(vertices,pose,(lat+1)/12.0,(lon+1)/24.0,alpha);
            sphere(vertices,pose,lat/12.0,(lon+1)/24.0,alpha);sphere(vertices,pose,lat/12.0,lon/24.0,alpha);
        }
        pose.popPose();
        if(disc>0.0001F){
            pose.pushPose();pose.scale(size*disc,size*core*(0.3F+0.7F*open),size*disc);
            float spin=((entity.level().getGameTime()%180)+partial)*2+90*(1-open)*(1-open)+240*(1-end)*(1-end);
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            for(int ring=0;ring<40;ring++)for(int cross=0;cross<12;cross++){
                torus(vertices,pose,ring/40.0,cross/12.0,alpha);torus(vertices,pose,(ring+1)/40.0,cross/12.0,alpha);
                torus(vertices,pose,(ring+1)/40.0,(cross+1)/12.0,alpha);torus(vertices,pose,ring/40.0,(cross+1)/12.0,alpha);
            }
            pose.popPose();
        }
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static void sphere(VertexConsumer v,PoseStack p,double lat,double lon,int alpha){
        double a=lat*Math.PI,b=lon*Math.PI*2;
        emit(v,p,Math.sin(a)*Math.cos(b),Math.cos(a),Math.sin(a)*Math.sin(b),lon*0.49,lat,alpha);
    }
    private static void torus(VertexConsumer v,PoseStack p,double ring,double cross,int alpha){
        double a=ring*Math.PI*2,b=cross*Math.PI*2,rad=1.65+0.62*Math.cos(b);
        emit(v,p,rad*Math.cos(a),0.09*Math.sin(b),rad*Math.sin(a),0.51+0.49*cross,ring,alpha);
    }
    private static void emit(VertexConsumer v,PoseStack p,double x,double y,double z,double u,double t,int alpha){
        var matrix=p.last();
        v.vertex(matrix.pose(),(float)x,(float)y,(float)z).color(255,255,255,alpha).uv((float)u,(float)t)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(matrix.normal(),0,1,0).endVertex();
    }
}
