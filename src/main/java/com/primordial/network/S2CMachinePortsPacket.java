/**
 * 【文件职责】把一页机器端口快照同步给当前配置器界面。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增 S2C 协议。
 * - 新增：最多 27 条、512 字符 key、完整整数数量及流体/物品图标的编解码。
 * - 修改：客户端只接收对应 containerId 的页面，客户端类封装在专用接收器中。
 * - 用途：仅注册为 PLAY_TO_CLIENT；不传整个机器 NBT，界面离开后忽略响应。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
 */
/**
 * 【变更记录 #8】2026-09-14 16:45:00（第 8 次备注 · 1.0.0-stage5-feedback2-hotfix）
 * - 删除：运行时把旧 rate 字段当作固定模式速率的路径；旧字段仅保留给已有存档读取。
 * - 新增：固定模式使用 amount 数量字段，补充模式根据目标容量或缺口计算，不受固定数量影响。
 * - 修改：EnergyConfig、玩家背包规则、配置器界面和任务执行器统一采用“数量 + tick 间隔”语义，并继续限制非法值。
 * - 用途：避免高容量设备因为旧上限或错误速率解释而产生过量循环、配置丢失或跨槽位写入。
 */
/**
 * 【变更记录 #9】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
/**
 * 【变更记录 #10】2026-10-13 22:57:15（第 10 次备注 · 1.2.0-relative-target-system）
 * - 删除：新绑定只保存世界方向、机器旋转后仍操作旧面的路径；所有历史备注保留。
 * - 新增/修改：端口回包追加 referenceFront，供客户端排除旧朝向页面；保留原 27 项与 key 长度边界。
 * - 用途：第 9 系统相对面绑定与显示、网络、存档、执行保持一致；已有绝对目标重新绑定才转为相对，避免误改输入输出。
 */
package com.primordial.network;

import com.primordial.data.MachineTarget;
import com.primordial.machine.MachinePortPage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.function.Supplier;

/** 单页端口快照，只发送实际内容和绑定身份；不复制第三方 GUI 或向客户端暴露方块完整 NBT。 */
public record S2CMachinePortsPacket(int containerId, int section, int sample, int side, MachinePortPage page, boolean gas, int referenceFront) {
    public S2CMachinePortsPacket(int containerId, int section, int sample, int side, MachinePortPage page) {
        this(containerId, section, sample, side, page, page.ports().stream().anyMatch(MachinePortPage.Port::gas), -1);
    }
    public static void encode(S2CMachinePortsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeByte(msg.section); buf.writeByte(msg.sample); buf.writeByte(msg.side); buf.writeBoolean(msg.gas);
        buf.writeByte(msg.referenceFront);
        buf.writeUtf(msg.page.proxyId(), 64); buf.writeVarInt(msg.page.page()); buf.writeVarInt(msg.page.total());
        buf.writeVarInt(msg.page.ports().size());
        for (var port : msg.page.ports()) {
            buf.writeVarInt(port.index()); buf.writeUtf(port.key(), MachineTarget.MAX_KEY_LENGTH);
            buf.writeItem(port.item()); port.fluid().writeToPacket(buf);
            buf.writeVarInt(port.amount()); buf.writeVarInt(port.capacity()); buf.writeBoolean(port.gas());
            buf.writeUtf(port.resourceUnit(), 128);
        }
    }

    public static S2CMachinePortsPacket decode(FriendlyByteBuf buf) {
        int container = buf.readVarInt(), section = buf.readByte(), sample = buf.readByte(), side = buf.readByte();
        boolean gas = buf.readBoolean();
        int referenceFront = buf.readByte();
        String proxy = buf.readUtf(64);
        int page = buf.readVarInt(), total = buf.readVarInt(), count = buf.readVarInt();
        if (count < 0 || count > MachinePortPage.PAGE_SIZE || page < 0 || total < 0) throw new IllegalArgumentException("Invalid port page");
        var ports = new ArrayList<MachinePortPage.Port>(count);
        for (int i = 0; i < count; i++) {
            ports.add(new MachinePortPage.Port(buf.readVarInt(), buf.readUtf(MachineTarget.MAX_KEY_LENGTH),
                    buf.readItem(), FluidStack.readFromPacket(buf), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readUtf(128)));
        }
        return new S2CMachinePortsPacket(container, section, sample, side, new MachinePortPage(proxy, page, total, java.util.List.copyOf(ports)), gas, referenceFront);
    }

    public static void handle(S2CMachinePortsPacket msg, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientHandler.accept(msg));
        context.get().setPacketHandled(true);
    }

    /** 嵌套类仅由 PLAY_TO_CLIENT 的处理器调用，独立服务端不加载 Minecraft 客户端类。 */
    private static final class ClientHandler {
        static void accept(S2CMachinePortsPacket msg) {
            if (Minecraft.getInstance().screen instanceof com.primordial.screen.ConfiguratorScreen screen
                    && screen.getMenu().containerId == msg.containerId) screen.acceptPorts(msg);
        }
    }
}
