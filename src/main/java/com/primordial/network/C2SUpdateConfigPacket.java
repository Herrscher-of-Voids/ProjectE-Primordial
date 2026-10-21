/**
 * 【文件职责】接收当前配置器的样本速率、阈值和开关编辑。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：删除允许脱离当前菜单或越界字段继续进入 apply 的路径。
 * - 新增：当前菜单、machineKey、所有者/距离、section/slot/field 范围校验。
 * - 修改：非法编辑在修改配置前返回，避免越界抛错及远程修改其它机器。
 * - 用途：与新端口绑定包使用相同的 ConfiguratorMenu.canEdit 权限入口。
 */
/**
 * 【变更记录 #2】2026-09-14 12:00:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：删除物品、流体和 FE 速率/目标的 1,000,000 网络限制。
 * - 新增：统一调用 ConfigLimits，继续拒绝负数并限制间隔为秒级安全范围。
 * - 修改：服务端先验证菜单与所有者，再把客户端整数钳制到 Forge API 可表示的 int 范围。
 * - 用途：补充模式可设置最高上限，固定模式可直接输入所需速度，避免客户端与服务端数值不一致。
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
package com.primordial.network;

import java.util.function.Supplier;

import com.primordial.PrimordialMod;
import com.primordial.data.EnergyConfig;
import com.primordial.data.ConfigLimits;
import com.primordial.data.FluidSampleConfig;
import com.primordial.data.ItemSampleConfig;
import com.primordial.data.MachineConfig;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.data.SampleMode;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * 【变更记录 #10】2026-09-16（资源参数协议）
 * - 新增：section 6/7 接收独立资源的模式、数量、tick 间隔和启用状态；8-11 保留四个介质总开关。
 * - 修改：资源数量复用 ConfigLimits，服务端先校验菜单所有者和槽位范围再写入 MachineConfig。
 * - 用途：资源页控件与 TaskExecutor 共享同一套持久化参数，防止客户端伪造跨槽位修改。
 */
/**
 * C→S 参数编辑。
 * section: 0=itemIn 1=itemOut 2=fluidIn 3=fluidOut 4=energySupply 5=energyRecover
 *          6=resourceIn 7=resourceOut 8=itemInMaster 9=itemOutMaster
 *          10=fluidInMaster 11=fluidOutMaster
 * field:   0=mode 1=rate 2=threshold/target 3=interval 4=enabled
 */
public class C2SUpdateConfigPacket {
    private final String machineKey;
    private final int section;
    private final int slot;
    private final int field;
    private final int value;

    public C2SUpdateConfigPacket(String machineKey, int section, int slot, int field, int value) {
        this.machineKey = machineKey;
        this.section = section;
        this.slot = slot;
        this.field = field;
        this.value = value;
    }

    public static void encode(C2SUpdateConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.machineKey, 256);
        buf.writeByte(msg.section);
        buf.writeByte(msg.slot);
        buf.writeByte(msg.field);
        buf.writeVarInt(msg.value);
    }

    public static C2SUpdateConfigPacket decode(FriendlyByteBuf buf) {
        return new C2SUpdateConfigPacket(buf.readUtf(256), buf.readByte(), buf.readByte(), buf.readByte(),
                buf.readVarInt());
    }

    public static void handle(C2SUpdateConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof com.primordial.menu.ConfiguratorMenu menu)
                    || !menu.machineKey.equals(msg.machineKey) || !menu.canEdit(player)
                    || msg.section < 0 || msg.section > 11 || msg.slot < 0
                    || msg.slot >= ((msg.section >= 6 && msg.section <= 7) ? 6 : 9)
                    || msg.field < 0 || msg.field > 4) return;
            MachineConfigSavedData store = MachineConfigSavedData.get(player.server);
            MachineConfig cfg = store.get(msg.machineKey);
            if (cfg == null || cfg.owner == null || !cfg.owner.equals(player.getUUID())) {
                PrimordialMod.LOGGER.warn("[Primordial] rejected config edit from {} on {}", player, msg.machineKey);
                return;
            }
            apply(cfg, msg.section, msg.slot, msg.field, msg.value);
            store.setDirty();
            PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CFullConfigSyncPacket(msg.machineKey, cfg.save()));
        });
        ctx.get().setPacketHandled(true);
    }

    static void apply(MachineConfig cfg, int section, int slot, int field, int value) {
        switch (section) {
            case 0 -> applyItem(cfg.itemIn[toSlot(slot)], field, value);
            case 1 -> applyItem(cfg.itemOut[toSlot(slot)], field, value);
            case 2 -> applyFluid(cfg.fluidIn[toSlot(slot)], field, value);
            case 3 -> applyFluid(cfg.fluidOut[toSlot(slot)], field, value);
            case 4 -> applyEnergy(cfg, field, value, true);
            case 5 -> applyEnergy(cfg, field, value, false);
            case 6 -> applyResource(cfg.resourceIn[toResourceSlot(slot)], field, value);
            case 7 -> applyResource(cfg.resourceOut[toResourceSlot(slot)], field, value);
            case 8 -> cfg.itemInEnabled = value != 0;
            case 9 -> cfg.itemOutEnabled = value != 0;
            case 10 -> cfg.fluidInEnabled = value != 0;
            case 11 -> cfg.fluidOutEnabled = value != 0;
            default -> throw new IllegalStateException("bad section " + section);
        }
    }

    private static int toSlot(int slot) {
        if (slot < 0 || slot > 8) throw new IllegalStateException("bad slot " + slot);
        return slot;
    }

    private static int toResourceSlot(int slot) {
        if (slot < 0 || slot >= 6) throw new IllegalStateException("bad resource slot " + slot);
        return slot;
    }

    private static void applyItem(ItemSampleConfig s, int field, int value) {
        switch (field) {
            case 0 -> s.mode = SampleMode.VALUES[value & 1];
            case 1 -> s.rate = ConfigLimits.amount(value, 1);
            case 2 -> s.threshold = ConfigLimits.amount(value, 1);
            case 3 -> s.intervalTicks = ConfigLimits.intervalTicks(value);
            case 4 -> s.enabled = value != 0;
            default -> throw new IllegalStateException("bad field " + field);
        }
    }

    private static void applyFluid(FluidSampleConfig s, int field, int value) {
        switch (field) {
            case 0 -> s.mode = SampleMode.VALUES[value & 1];
            case 1 -> s.rate = ConfigLimits.amount(value, 1);
            case 2 -> s.threshold = ConfigLimits.amount(value, 1);
            case 3 -> s.intervalTicks = ConfigLimits.intervalTicks(value);
            case 4 -> s.enabled = value != 0;
            default -> throw new IllegalStateException("bad field " + field);
        }
    }

    private static void applyEnergy(MachineConfig cfg, int field, int value, boolean supply) {
        EnergyConfig e = cfg.energy;
        if (supply) {
            switch (field) {
                case 0 -> e.supplyMode = SampleMode.VALUES[value & 1];
                case 1 -> e.supplyRate = ConfigLimits.amount(value, 1);
                case 2 -> e.supplyAmount = ConfigLimits.amount(value, 1);
                case 3 -> e.supplyIntervalTicks = ConfigLimits.intervalTicks(value);
                case 4 -> e.supplyEnabled = value != 0;
                default -> throw new IllegalStateException("bad field " + field);
            }
        } else {
            switch (field) {
                case 0 -> e.recoverMode = SampleMode.VALUES[value & 1];
                case 1 -> e.recoverRate = ConfigLimits.amount(value, 1);
                case 2 -> e.recoverAmount = ConfigLimits.amount(value, 1);
                case 3 -> e.recoverIntervalTicks = ConfigLimits.intervalTicks(value);
                case 4 -> e.recoverEnabled = value != 0;
                default -> throw new IllegalStateException("bad field " + field);
            }
        }
    }

    private static void applyResource(com.primordial.data.ResourceSampleConfig sample, int field, int value) {
        switch (field) {
            case 0 -> sample.mode = SampleMode.VALUES[value & 1];
            case 2 -> sample.amount = ConfigLimits.amount(value, 1);
            case 3 -> sample.intervalTicks = ConfigLimits.intervalTicks(value);
            case 4 -> sample.enabled = value != 0;
            default -> throw new IllegalStateException("bad resource field " + field);
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
