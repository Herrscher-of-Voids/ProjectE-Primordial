package com.primordial.neoforge.client;
import com.primordial.neoforge.equipment.NeoForgeSingularityEntity;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class NeoForgeSingularityRenderer extends EntityRenderer<NeoForgeSingularityEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("primordial","textures/entity/singularity.png");
    public NeoForgeSingularityRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(NeoForgeSingularityEntity entity){return TEXTURE;}
    @Override public void render(NeoForgeSingularityEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        float open=smooth(entity.openingAge(partial)/NeoForgeSingularityEntity.OPEN_TICKS);
        float end=smooth(entity.endingTicks(partial)/NeoForgeSingularityEntity.CLOSE_TICKS);
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
        v.addVertex(matrix.pose(),(float)x,(float)y,(float)z).setColor(255,255,255,alpha).setUv((float)u,(float)t)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(matrix,0,1,0);
    }
}
