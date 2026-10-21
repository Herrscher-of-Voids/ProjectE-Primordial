/**
 * 【文件职责】为范围放置与位置名单编辑绘制真实世界边界预览。
 * 【变更记录 #1】2026-09-18 01:53:11（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：准星范围外框、已绑定范围的标记编辑外框及最多 64 个附近名单方块描边。
 * - 修改：使用原版线框缓冲和正常深度测试，持物、维度或编辑目标失效立即停止。
 * - 用途：几何与服务端 AccelerationSettings 共用计算，预览不读取服务器区块或执行加速。
 */
package com.primordial.client;

import com.primordial.service.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "primordial", value = Dist.CLIENT)
public final class AccelerationAreaPreview {
    private AccelerationAreaPreview() { }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        var hand = AccelerationClientInput.hand();
        if (hand == null || mc.player == null || mc.level == null || mc.screen != null) return;
        var tool = mc.player.getItemInHand(hand);
        CompoundTag root = tool.getTag();
        if (root == null) return;
        var kind = AccelerationSettings.kind(root);
        CompoundTag source = root;
        BlockPos anchor;
        boolean editing = kind != null && kind.filter();
        if (editing) {
            if (!root.hasUUID("EditingArea")) return;
            source = AccelerationBindings.find(tool, root.getUUID("EditingArea"));
            if (source == null || !source.getString("TargetDimension").equals(mc.level.dimension().location().toString())) return;
            anchor = AccelerationBindings.pos(source);
        } else {
            if (kind != AccelerationSettings.Kind.AREA || !(mc.hitResult instanceof BlockHitResult hit)) return;
            anchor = hit.getBlockPos();
        }
        var camera = event.getCamera().getPosition();
        if (anchor.distToCenterSqr(camera) > 4096) return;
        var pose = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var vertices = buffers.getBuffer(RenderType.lines());
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, vertices, AccelerationSettings.read(source).bounds(anchor), .3F, .9F, .85F, 1F);
        if (editing) {
            long[] marks = source.getLongArray(kind == AccelerationSettings.Kind.IDLE_FILTER ? "IdleMarks" : "AreaMarks");
            for (int i = 0; i < marks.length && i < 64; i++) {
                BlockPos pos = BlockPos.of(marks[i]);
                if (pos.distToCenterSqr(camera) <= 4096)
                    LevelRenderer.renderLineBox(pose, vertices, new AABB(pos).inflate(.003), 1F, .7F, .25F, 1F);
            }
        }
        pose.popPose(); buffers.endBatch(RenderType.lines());
    }
}
