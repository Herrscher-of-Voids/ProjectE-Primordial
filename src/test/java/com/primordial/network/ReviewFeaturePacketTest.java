/**
 * 【文件职责】验证护符动作与世界标记的新协议载荷。
 * 【变更记录 #1】2026-09-16 19:46:19（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：大于255动作编号往返、菜单修订与非法数量倍率测试。
 * - 修改：调用生产编解码并释放Netty缓冲，验证异维度和负坐标。
 * - 用途：防止规则分页删除动作截断及标记异常载荷造成无界分配。
 */
package com.primordial.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 独立动作使用 VarInt，不会截断到 8 位；世界标记拒绝无效数量及非二次幂倍率。 */
class ReviewFeaturePacketTest {
    @Test void charmActionsPreserveHighIndicesAndMenuRevision() {
        for (int action : new int[]{0, 4, 1000, 1255, 2000, 2127}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var packet = new C2SCharmFilterActionPacket(9, 200, action);
                C2SCharmFilterActionPacket.encode(packet, buf);
                assertEquals(packet, C2SCharmFilterActionPacket.decode(buf));
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
        }
    }
    @Test void surfaceMarkersPreserveDimensionPositionAndRate() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new S2CAccelerationMarkersPacket(List.of(new S2CAccelerationMarkersPacket.Marker(
                    new ResourceLocation("minecraft:the_nether"), new BlockPos(-124, 45, 129), 1024)));
            S2CAccelerationMarkersPacket.encode(packet, buf);
            assertEquals(packet, S2CAccelerationMarkersPacket.decode(buf));
        } finally { buf.release(); }
    }
    @Test void malformedMarkerCountsAndRatesAreRejected() {
        for (int count : new int[]{-1, 257}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeVarInt(count);
                assertThrows(DecoderException.class, () -> S2CAccelerationMarkersPacket.decode(buf));
            } finally { buf.release(); }
        }
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1).writeResourceLocation(new ResourceLocation("minecraft:overworld")).writeBlockPos(BlockPos.ZERO).writeVarInt(3);
            assertThrows(DecoderException.class, () -> S2CAccelerationMarkersPacket.decode(buf));
        } finally { buf.release(); }
    }
}
