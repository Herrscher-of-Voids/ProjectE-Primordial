/**
 * 【文件职责】验证高倍率警告及确认回包的线缆格式能完整保留令牌、目标和大整数报价。
 * 【变更记录 #1】2026-09-16 13:47:00（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无。
 * - 新增：三档高倍率、负坐标、128 位基础费用及确认/取消布尔值的往返验证。
 * - 修改：调用生产 encode/decode，释放 Netty 缓冲区，不依赖客户端屏幕启动。
 * - 用途：防止客户端与服务端字段次序、报价长度或令牌类型不一致造成无法确认。
 */
/**
 * 【变更记录 #2】2026-09-16 14:41:00（第 2 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：旧的半倍率价格生成。
 * - 新增：256 个目标最大报价和大余额携带精确小额扣款的往返测试。
 * - 修改：警告载荷加入目标数，余额包加入净消费，保留拒绝与确认令牌测试。
 * - 用途：验证消费明细不会因大数余额压缩或新增字段错位而丢失。
 */
package com.primordial.network;

import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccelerationPacketTest {
    @Test void hugeBalancePreservesSmallAccelerationCharge() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            BigInteger balance = BigInteger.TEN.pow(100).subtract(BigInteger.valueOf(4000));
            var original = new S2CSyncBalancePacket(balance, BigInteger.valueOf(4000));
            S2CSyncBalancePacket.encode(original, buffer);
            var decoded = S2CSyncBalancePacket.decode(buffer);
            assertEquals(balance, decoded.emc());
            assertEquals(BigInteger.valueOf(4000), decoded.accelerationCharge());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void warningPreservesAllRatesAndMaximumConfiguredPrice() {
        for (int rate : new int[]{256, 512, 1024}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                String cost = new BigInteger("9".repeat(128)).multiply(BigInteger.valueOf(rate * 256L)).toString();
                var original = new S2CAccelerationWarningPacket(UUID.randomUUID(), rate, new BlockPos(-500, -20, 200), 256, cost);
                S2CAccelerationWarningPacket.encode(original, buffer);
                assertEquals(original, S2CAccelerationWarningPacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test void confirmationAndCancellationPreserveOneTimeToken() {
        for (boolean accepted : new boolean[]{false, true}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var original = new C2SConfirmAccelerationPacket(UUID.randomUUID(), accepted);
                C2SConfirmAccelerationPacket.encode(original, buffer);
                assertEquals(original, C2SConfirmAccelerationPacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }
}
