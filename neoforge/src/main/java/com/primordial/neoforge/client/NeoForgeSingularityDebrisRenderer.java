package com.primordial.neoforge.client;
import com.primordial.neoforge.equipment.NeoForgeSingularityDebrisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
public final class NeoForgeSingularityDebrisRenderer extends EntityRenderer<NeoForgeSingularityDebrisEntity> {
    private final BlockRenderDispatcher blocks;
    public NeoForgeSingularityDebrisRenderer(EntityRendererProvider.Context context){super(context);blocks=context.getBlockRenderDispatcher();}
    @Override public ResourceLocation getTextureLocation(NeoForgeSingularityDebrisEntity entity){return TextureAtlas.LOCATION_BLOCKS;}
    @Override public void render(NeoForgeSingularityDebrisEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount+partial)*9));pose.mulPose(Axis.ZP.rotationDegrees((entity.tickCount+partial)*5));
        pose.scale(0.7F,0.7F,0.7F);pose.translate(-0.5,-0.5,-0.5);
        blocks.renderSingleBlock(entity.block(),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
    }
}
