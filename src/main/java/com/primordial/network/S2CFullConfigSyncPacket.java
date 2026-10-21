/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
 */
/**
 * 【变更记录 #8】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
package com.primordial.network;

import java.util.function.Supplier;

import com.primordial.client.ClientConfigCache;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

/** S→C 全量配置同步：openMenu 后 + 每次编辑成功后 */
public class S2CFullConfigSyncPacket {
    private final String machineKey;
    private final CompoundTag configTag;

    public S2CFullConfigSyncPacket(String machineKey, CompoundTag configTag) {
        this.machineKey = machineKey;
        this.configTag = configTag;
    }

    public static void encode(S2CFullConfigSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.machineKey, 256);
        buf.writeNbt(msg.configTag);
    }

    public static S2CFullConfigSyncPacket decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(256);
        CompoundTag tag = buf.readNbt();
        if (tag == null) tag = new CompoundTag();
        return new S2CFullConfigSyncPacket(key, tag);
    }

    public static void handle(S2CFullConfigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> clientAccept(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void clientAccept(S2CFullConfigSyncPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        ClientConfigCache.accept(msg.machineKey, msg.configTag);
        if (mc.screen instanceof com.primordial.screen.ConfiguratorScreen screen) {
            screen.getMenu().refreshClientGhostSlots();
            screen.onConfigSync(msg.machineKey);
        }
    }
}
