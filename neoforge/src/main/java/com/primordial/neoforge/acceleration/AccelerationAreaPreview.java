package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "primordial", value = Dist.CLIENT)
public final class AccelerationAreaPreview {
    private AccelerationAreaPreview() { }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        var hand = AccelerationClientInput.hand();
        if (hand == null || mc.player == null || mc.level == null || mc.screen != null) return;
        var tool = mc.player.getItemInHand(hand);
        CompoundTag root = AccelerationData.get(tool);
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
