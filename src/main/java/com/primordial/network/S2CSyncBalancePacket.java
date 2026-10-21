/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
/**
 * 【文件职责】
 * - 解码服务端发送的余额同步包，并在客户端线程更新显示缓存。
 * - 当前保留旧 VarLong 载荷，作为 ProjectE BigInteger 同步层接入前的兼容桥接。
 *
 * 【变更记录 #1】2026-09-13 22:52:14（第 1 次备注·EMC 显示改造）
 * - 删除：无；保留包编号、VarLong 编码和解码结构，避免破坏现有网络通道。
 * - 新增：直接写入 ClientBalanceState 的 BigInteger EMC 值。
 * - 修改：包处理目标从直接写入 long 字段改为通过客户端状态服务写入 BigInteger。
 * - 用途：在 ProjectE EMC 网络包替换前保持客户端 HUD 可用；下一阶段会替换载荷而不是继续扩大旧协议。
 */
/**
 * 【变更记录 #3】2026-09-16 14:41:00（第 3 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：无。
 * - 新增：余额包携带最近 10 tick 的实际加速消费，默认构造消费为零用于登录同步。
 * - 修改：两个 BigInteger 以受限字符串编码；接收后一起更新客户端，协议版本同步提升。
 * - 用途：保留余额精度并显示扣款明细，不增加每台机器各自的同步包。
 */
package com.primordial.network;

import java.math.BigInteger;
import java.util.function.Supplier;

import com.primordial.client.ClientBalanceState;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** S→C EMC 同步。使用长度受控的十进制字符串同步 BigInteger EMC。 */
public class S2CSyncBalancePacket {
    private final BigInteger emc;
    private final BigInteger accelerationCharge;

    public S2CSyncBalancePacket(BigInteger emc) {
        this(emc, BigInteger.ZERO);
    }

    public S2CSyncBalancePacket(BigInteger emc, BigInteger accelerationCharge) {
        this.emc = emc == null || emc.signum() < 0 ? BigInteger.ZERO : emc;
        this.accelerationCharge = accelerationCharge.max(BigInteger.ZERO);
    }

    public BigInteger emc() { return emc; }
    public BigInteger accelerationCharge() { return accelerationCharge; }

    public static void encode(S2CSyncBalancePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.emc.toString(), 2048);
        buf.writeUtf(msg.accelerationCharge.toString(), 2048);
    }

    public static S2CSyncBalancePacket decode(FriendlyByteBuf buf) {
        try {
            return new S2CSyncBalancePacket(new BigInteger(buf.readUtf(2048)), new BigInteger(buf.readUtf(2048)));
        } catch (RuntimeException malformed) {
            return new S2CSyncBalancePacket(BigInteger.ZERO);
        }
    }

    public static void handle(S2CSyncBalancePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientBalanceState.setEmc(msg.emc);
            ClientBalanceState.setAccelerationCharge(msg.accelerationCharge);
        });
        ctx.get().setPacketHandled(true);
    }
}
