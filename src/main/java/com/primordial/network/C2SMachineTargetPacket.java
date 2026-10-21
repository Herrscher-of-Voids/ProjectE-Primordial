/**
 * 【文件职责】处理客户端端口查询、绑定与解绑请求。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增独立 C2S 协议。
 * - 新增：菜单 ID/所有者/距离/机器有效性和字段范围校验，服务器重读端口身份后再绑定。
 * - 修改：旧页面身份不符时拒绝绑定；API 异常通知玩家，查询返回空页结束等待。
 * - 用途：读写均 enqueue 到服务端线程；不信任客户端提交的端口 key，成功后发送全量配置。
 */
/**
 * 【变更记录 #2】2026-09-14 12:10:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：side=6 的能量内部端口绑定和查询路径。
 * - 修改：服务器拒绝物品/流体使用内部端口；能量和独立资源允许绑定明确声明的 null Direction。
 * - 用途：防止客户端伪造 null 面绕过侧面限制，同时兼容无方向 FE Utility 与机器级资源储存。
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
 * - 新增/修改：请求新增 referenceFront，服务端验证页面朝向仍匹配机器；合法绑定保存相对面，旋转后的陈旧请求不读端口或写配置。
 * - 用途：第 9 系统相对面绑定与显示、网络、存档、执行保持一致；已有绝对目标重新绑定才转为相对，避免误改输入输出。
 */
/**
 * 【变更记录 #11】2026-10-13 23:01:02（第 11 次备注 · 1.2.0-relative-target-system）
 * - 删除：主页面解绑也要求朝向的限制。新增：无朝向解绑构造兼容现有按钮。
 * - 修改：仅 bind 且 index=-1 允许省略朝向，仍验证菜单、所有者和机器；查询与新绑定不豁免。
 * - 用途：旋转后仍能撤销规则，解绑不会访问任何方向或新端口。
 */
package com.primordial.network;

import com.primordial.PrimordialMod;
import com.primordial.data.MachineConfig;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.data.MachineTarget;
import com.primordial.machine.MachinePortPage;
import com.primordial.menu.ConfiguratorMenu;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

/**
 * 【变更记录 #4】2026-09-16（资源端口绑定）
 * - 新增：section 6/7 绑定资源输入/输出，并按当前资源单位过滤端口页。
 * - 修改：绑定前同时校验稳定 key、方向、索引和资源单位；资源目标支持多选与解除绑定。
 * - 用途：避免把转速、应力等不同资源写入同一条规则，第三方端口变化时安全拒绝。
 */
/** 查询/绑定明确端口。客户端 key 仅用于检测旧页面，最终身份始终由服务器重新读取。 */
public record C2SMachineTargetPacket(int containerId, int section, int sample, int side,
        int index, boolean bind, String expectedKey, boolean append, boolean gas, int referenceFront) {
    public C2SMachineTargetPacket(int containerId, int section, int sample, int side,
            int index, boolean bind, String expectedKey) {
        this(containerId, section, sample, side, index, bind, expectedKey, false, false, -1);
    }
    public C2SMachineTargetPacket(int containerId, int section, int sample, int side,
            int index, boolean bind, String expectedKey, boolean append) {
        this(containerId, section, sample, side, index, bind, expectedKey, append, false, -1);
    }
    /** 主页面解绑无需方向参照；没有参照的端口查询/新绑定仍由服务器拒绝。 */
    public C2SMachineTargetPacket(int containerId, int section, int sample, int side,
            int index, boolean bind, String expectedKey, boolean append, boolean gas) {
        this(containerId, section, sample, side, index, bind, expectedKey, append, gas, -1);
    }
    public static void encode(C2SMachineTargetPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeByte(msg.section); buf.writeByte(msg.sample);
        buf.writeByte(msg.side); buf.writeVarInt(msg.index); buf.writeBoolean(msg.bind);
        buf.writeUtf(msg.expectedKey, MachineTarget.MAX_KEY_LENGTH); buf.writeBoolean(msg.append); buf.writeBoolean(msg.gas);
        buf.writeByte(msg.referenceFront);
    }
    public static C2SMachineTargetPacket decode(FriendlyByteBuf buf) {
        return new C2SMachineTargetPacket(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readByte(),
                buf.readVarInt(), buf.readBoolean(), buf.readUtf(MachineTarget.MAX_KEY_LENGTH), buf.readBoolean(), buf.readBoolean(), buf.readByte());
    }

    public static void handle(C2SMachineTargetPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof ConfiguratorMenu menu)
                    || menu.containerId != msg.containerId || !menu.canEdit(player)
                    || msg.section < 0 || msg.section > 7
                    || msg.sample < 0 || msg.sample >= (msg.section >= 6 ? 6 : 9)
                    || msg.side < 0 || msg.side > MachineTarget.INTERNAL_SIDE || msg.index < -1
                    || (msg.side == MachineTarget.INTERNAL_SIDE && msg.section < 4)) return;
            var machine = menu.targetMachine();
            if (machine == null) return;
            if (!(msg.bind && msg.index == -1) && !matchesOrientation(machine.getBlockState(), msg.referenceFront)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.primordial.orientation_changed"), true);
                return;
            }
            try {
                if (msg.bind && msg.index == -1) {
                    setTarget(menu.config, msg.section, msg.sample, MachineTarget.UNBOUND, false);
                } else {
                    int pageIndex = msg.bind ? msg.index / MachinePortPage.PAGE_SIZE : msg.index;
                    Direction selectedSide = msg.side == MachineTarget.INTERNAL_SIDE ? null
                            : Direction.from3DDataValue(msg.side);
                    net.minecraft.resources.ResourceLocation resourceUnit = msg.section == 6
                            ? menu.config.resourceIn[msg.sample].unit
                            : msg.section == 7 ? menu.config.resourceOut[msg.sample].unit : null;
                    MachinePortPage page = MachinePortPage.read(machine, msg.section, selectedSide, pageIndex, msg.gas, resourceUnit);
                    if (!msg.bind) {
                        PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                                new S2CMachinePortsPacket(menu.containerId, msg.section, msg.sample, msg.side, page, msg.gas, msg.referenceFront));
                        return;
                    }
                    String requiredResource = msg.section == 6 ? menu.config.resourceIn[msg.sample].unit.toString()
                            : msg.section == 7 ? menu.config.resourceOut[msg.sample].unit.toString() : "";
                    var port = page.ports().stream().filter(p -> p.index() == msg.index && p.bindable()
                            && p.key().equals(msg.expectedKey)
                            && (msg.section < 6 || requiredResource.equals(p.resourceUnit()))).findFirst();
                    if (port.isEmpty()) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.primordial.target_changed"), true);
                        return;
                    }
                    setTarget(menu.config, msg.section, msg.sample,
                            MachineTarget.relative(msg.side, msg.index, port.get().key(), machine.getBlockState()), msg.append);
                }
                MachineConfigSavedData.get(player.server).setDirty();
                PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CFullConfigSyncPacket(menu.machineKey, menu.config.save()));
            } catch (RuntimeException | LinkageError failure) {
                PrimordialMod.LOGGER.warn("[Primordial] Cannot access machine ports at {}", menu.machineKey, failure);
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.primordial.target_failed"), true);
                if (!msg.bind) PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CMachinePortsPacket(menu.containerId, msg.section, msg.sample, msg.side,
                                new MachinePortPage("forge_capabilities", 0, 0, java.util.List.of()), msg.gas, msg.referenceFront));
            }
        });
        context.setPacketHandled(true);
    }

    /** 拒绝旧朝向页面，包括非法方向；不能把客户端看见的前面绑定到旋转后的侧面。 */
    public static boolean matchesOrientation(net.minecraft.world.level.block.state.BlockState state, int front) {
        return front >= 2 && front <= 5
                && com.primordial.util.RelativeMachineSides.from(state).front().get3DDataValue() == front;
    }

    private static void setTarget(MachineConfig config, int section, int sample, MachineTarget target, boolean append) {
        switch (section) {
            case 0 -> update(config.itemIn[sample], target, append);
            case 1 -> update(config.itemOut[sample], target, append);
            case 2 -> update(config.fluidIn[sample], target, append);
            case 3 -> update(config.fluidOut[sample], target, append);
            case 4 -> config.energy.supplyPort = target;
            case 5 -> config.energy.recoverPort = target;
            case 6 -> update(config.resourceIn[sample], target, append);
            case 7 -> update(config.resourceOut[sample], target, append);
            default -> throw new IllegalArgumentException("Invalid section");
        }
    }

    private static void update(com.primordial.data.ItemSampleConfig sample, MachineTarget target, boolean append) {
        if (!append || !target.bound()) sample.setTargets(target.bound() ? java.util.List.of(target) : java.util.List.of());
        else { var values = new java.util.ArrayList<>(sample.effectiveTargets()); values.add(target); sample.setTargets(values); }
    }

    private static void update(com.primordial.data.FluidSampleConfig sample, MachineTarget target, boolean append) {
        if (!append || !target.bound()) sample.setTargets(target.bound() ? java.util.List.of(target) : java.util.List.of());
        else { var values = new java.util.ArrayList<>(sample.effectiveTargets()); values.add(target); sample.setTargets(values); }
    }

    private static void update(com.primordial.data.ResourceSampleConfig sample, MachineTarget target, boolean append) {
        if (!append || !target.bound()) sample.setTargets(target.bound() ? java.util.List.of(target) : java.util.List.of());
        else { var values = new java.util.ArrayList<>(sample.effectiveTargets()); values.add(target); sample.setTargets(values); }
    }
}

