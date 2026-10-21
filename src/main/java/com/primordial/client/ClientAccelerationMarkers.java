/**
 * 【文件职责】在方块表面渲染服务器确认的倍率标记。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：移除全世界扫描或透墙悬浮标签的需要。
 * - 新增：六个面的朝向计算、正常深度测试、64格距离与视锥裁剪。
 * - 修改：只渲染朝向相机的面；3秒未收到快照或退出服务器清空缓存。
 * - 用途：显示倍率设置与实际运行位置；显示层不执行机器、不计算或扣除费用。
 */
/**
 * 【变更记录 #2】2026-09-18 01:44:57（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：实体倍率只能贴在固定方块上的限制。
 * - 新增：按同步实体编号在当前实体上方绘制朝向相机的倍率，移除或超距立即跳过。
 * - 修改：方块六面绘制保持，实体采用插值位置与正常深度测试，不扫描世界实体列表。
 * - 用途：让实体移动时倍率跟随其位置，服务端快照仍是唯一显示依据。
 */
package com.primordial.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.primordial.network.S2CAccelerationMarkersPacket.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

/** 倍率贴在方块表面，只绘制朝向相机的一侧，正常深度遮挡；快照最长保留 3 秒。 */
@Mod.EventBusSubscriber(modid = "primordial", value = Dist.CLIENT)
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
                var position = entity.getPosition(event.getPartialTick());
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
