package com.primordial.neoforge.acceleration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.primordial.neoforge.acceleration.S2CAccelerationMarkersPacket.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.List;


@EventBusSubscriber(modid = "primordial", value = Dist.CLIENT)
public final class ClientAccelerationMarkers {
    private static List<Marker> markers = List.of();
    private static long receivedAt;
    private ClientAccelerationMarkers() { }
    public static void replace(List<Marker> values) { markers = List.copyOf(values); receivedAt = System.nanoTime(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { markers = List.of(); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || markers.isEmpty()) return;
        if (System.nanoTime() - receivedAt > 3_000_000_000L) { markers = List.of(); return; }
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        for (Marker marker : markers) {
            if (marker.entityId() >= 0) {
                var entity = mc.level.getEntity(marker.entityId());
                if (entity == null || entity.isRemoved() || !mc.level.dimension().location().equals(marker.dimension())
                        || entity.position().distanceToSqr(camera) > 4096 || !event.getFrustum().isVisible(entity.getBoundingBox())) continue;
                var position = entity.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(false));
                pose.pushPose();
                pose.translate(position.x - camera.x, position.y + entity.getBbHeight() + .35 - camera.y, position.z - camera.z);
                pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
                pose.scale(-.025F, -.025F, .025F);
                draw(mc.font, pose, buffers, "\u00D7" + marker.multiplier());
                pose.popPose();
                continue;
            }
            var pos = marker.position();
            if (!mc.level.dimension().location().equals(marker.dimension()) || pos.distToCenterSqr(camera) > 4096
                    || !event.getFrustum().isVisible(new AABB(pos))) continue;
            double dx = pos.getX() + .5 - camera.x, dy = pos.getY() + .5 - camera.y, dz = pos.getZ() + .5 - camera.z;
            for (Direction face : Direction.values()) {
                if (dx * face.getStepX() + dy * face.getStepY() + dz * face.getStepZ() >= -.5) continue;
                pose.pushPose();
                pose.translate(dx + .503 * face.getStepX(), dy + .503 * face.getStepY(), dz + .503 * face.getStepZ());
                // 从 SOUTH 的局部 +Z 转到各表面法线；负 Y 让字体正立。
                switch (face) {
                    case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
                    case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90));
                    case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
                    case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90));
                    case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90));
                    default -> { }
                }
                pose.scale(.025F, -.025F, .025F);
                draw(mc.font, pose, buffers, "\u00D7" + marker.multiplier());
                pose.popPose();
            }
        }
        buffers.endBatch();
    }
    private static void draw(Font font, PoseStack pose, MultiBufferSource buffers, String text) {
        font.drawInBatch(text, -font.width(text) / 2F, -4F, 0xFFFFFF, true, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0x50000000, LightTexture.FULL_BRIGHT);
    }
}
