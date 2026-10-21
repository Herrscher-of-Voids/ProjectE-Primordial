/**
 * 变更记录 #3（2026-09-13）：统一通过 EmcService 门面访问余额，并完成 ProjectE/Curios API 审计。
 * - 新增：能量换算配置键 energy_to_emc_rate 与 emc_to_energy_rate。
 * - 修改：业务层不再直接调用 ProjectECompat，保证后续适配器共享同一 EMC 结算规则。
 */
/**
 * 【变更记录 #4】2026-09-14 12:35:00（第 4 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：action=2 为玩家主手物品充能，按实际接收 FE 结算 EMC。
 * - 修改：充能先模拟 capability，再按余额计算可支付 FE，执行失败时不扣除余额。
 * - 用途：玩家背包配置页提供安全的手持物品充能入口，服务端验证玩家上下文并防止客户端伪造物品。
 */
/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
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
/**
 * 【变更记录 #9】2026-09-15 15:07:10（第 9 次备注 · 1.0.0-emc-service）
 * - 删除：double 价格网络字段、手持充能自行取整和仅清除整数余额的实现。
 * - 新增：有界 BigInteger 文本、管理员/价格菜单身份/注册名校验及修改结果翻译。
 * - 修改：充能经 EmcService.supplyEnergy；清零同时清找零；成功编辑广播人工价格快照。
 * - 用途：超 long 价格不损精度，普通玩家不能伪造公共定价请求，充能只操作当前手持实物。
 */
/**
 * 【变更记录 #10】2026-09-15 15:33:53（第 10 次备注 · 1.0.0-emc-service）
 * - 删除：手持充能后重复立即发送余额包的路径，以及清零请求直接显示零的假定。
 * - 新增：清零后读取实际整数余额反馈。
 * - 修改：充能由统一服务标记余额变化，沿用服务端节流同步；背包仍立即广播实物变化。
 * - 用途：减少少量多次充能的重复网络流量，账户拒绝清零时不会误报已清空。
 */
/**
 * 【变更记录 #11】2026-09-16 01:24:55（第 11 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：物品目标不符时静默返回，以及仅聊天提示而不更新价格页面的行为。
 * - 新增：向对应菜单返回服务器报价和保存结果；显式校验物品类型、注册名及 4096 位非负整数边界。
 * - 修改：成功后继续广播人工物品覆盖，再回传保存确认；无权限/保存失败不会伪装成功。
 * - 用途：物品改价同时更新配置器结算和本模组 Tooltip，界面能辨别输入尚未保存与真正生效。
 */
/**
 * 【变更记录 #12】2026-09-16 01:52:17（第 12 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：物品价格写入独立覆盖表和广播 S2CValueOverridesPacket 的路径。
 * - 新增：ProjectE 单件价格 0..Long.MAX_VALUE 校验，委托原生文件、重算和网络同步桥接。
 * - 修改：余额包仍使用 BigInteger；物品报价只有 ProjectE 实际应用成功才回传保存成功。
 * - 用途：配置器改价直接改变转化桌与 ProjectE API，避免超范围价格只在本模组生效。
 */
/**
 * 【变更记录 #13】2026-10-10 03:04:14（第 13 次备注 · 1.2.0-price-change-audit）
 * - 删除：无；清零余额、手持充能和物品价格校验保持。
 * - 新增：物品数字价格保存成功后由 PriceChangeAudit 记录资源、操作者、旧/新价格状态和时间。
 * - 修改：以审计入口包裹 ValueManager.setItemValue，仍由 ProjectE 完成原生保存、重算和回滚。
 * - 用途：独立定价器物品改价与流体/气体、inherit 共用一致的成功日志，失败不留假成功记录。
 */
/**
 * 【变更记录 #14】2026-10-11 04:26:53（第 14 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：物品价格包仅检查管理员权限的保存路径。
 * - 新增：写入前要求创造模式和管理员权限。
 * - 修改：保留菜单 ID、样本身份、ProjectE 范围和审计事务校验。
 * - 用途：保证定价器物品的物品价格写操作与入口规则一致。
 */
package com.primordial.network;

import com.primordial.menu.PriceEditorMenu;
import com.primordial.network.S2CPriceEditorStatePacket.Status;
import java.util.function.Supplier;
import java.math.BigInteger;
import com.primordial.value.ValueTable;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;

import com.primordial.PrimordialMod;
import com.primordial.service.EmcService;
import com.primordial.service.PriceChangeAudit;
import com.primordial.value.ValueManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** C→S 玩家操作：0=清零余额，1=设置物品价格，2=给主手物品充能。 */
public class C2SPlayerActionPacket {
    private final int action;
    private final String itemId;
    private final BigInteger value;
    private final int containerId;

    public C2SPlayerActionPacket(int action) {
        this.action = action;
        this.itemId = "";
        this.value = BigInteger.ZERO;
        this.containerId = -1;
    }

    public C2SPlayerActionPacket(String itemId, BigInteger value, int containerId) {
        this.action = 1;
        this.itemId = itemId;
        this.value = value;
        this.containerId = containerId;
    }

    public static void encode(C2SPlayerActionPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
        if (msg.action == 1) {
            buf.writeUtf(msg.itemId, 256);
            buf.writeUtf(msg.value.toString(), ValueTable.MAX_DIGITS);
            buf.writeVarInt(msg.containerId);
        }
    }

    public static C2SPlayerActionPacket decode(FriendlyByteBuf buf) {
        int action = buf.readByte();
        if (action == 1) {
            String id = buf.readUtf(256);
            String price = buf.readUtf(ValueTable.MAX_DIGITS);
            if (!price.matches("[0-9]+")) throw new io.netty.handler.codec.DecoderException("Invalid integer EMC");
            return new C2SPlayerActionPacket(id, new BigInteger(price), buf.readVarInt());
        }
        return new C2SPlayerActionPacket(action);
    }

    public static void handle(C2SPlayerActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            switch (msg.action) {
                case 0 -> { // 清零余额
                    EmcService.clearBalance(player.server, player.getUUID());
                    PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new S2CSyncBalancePacket(EmcService.balance(player.server, player.getUUID())));
                    PrimordialMod.LOGGER.info("[Primordial] {} cleared their balance", player.getName());
                }
                case 1 -> { // 热加载设置物品价格
                    if (!(player.containerMenu instanceof PriceEditorMenu menu)
                            || menu.containerId != msg.containerId) return;
                    if (!com.primordial.service.PriceEditorAccess.canEdit(player)) {
                        menu.sendPriceState(player, Status.PERMISSION_DENIED);
                        player.sendSystemMessage(Component.translatable("message.primordial.price_permission"));
                        return;
                    }
                    ResourceLocation id = msg.itemId == null || msg.itemId.length() > 256
                            ? null : ResourceLocation.tryParse(msg.itemId);
                    if (!menu.matchesSelection(PriceEditorMenu.KIND_ITEM, id) || menu.selectedItem().isEmpty()
                            || !BuiltInRegistries.ITEM.containsKey(id)) {
                        menu.sendPriceState(player, Status.INVALID_TARGET);
                        return;
                    }
                    if (!com.primordial.compat.ProjectEItemPrices.validItemPrice(msg.value)) {
                        menu.sendPriceState(player, Status.INVALID_PRICE);
                        return;
                    }
                    boolean saved = PriceChangeAudit.change(player, PriceChangeAudit.Kind.ITEM, id,
                            () -> ValueManager.setItemValue(player.server, id, msg.value));
                    player.sendSystemMessage(Component.translatable(saved ? "commands.primordial.set_value" : "message.primordial.price_save_failed",
                            "item", id, msg.value.toString()));
                    menu.sendPriceState(player, saved ? Status.SAVED : Status.SAVE_FAILED);
                }
                case 2 -> {
                    if (player.containerMenu instanceof com.primordial.menu.PlayerInventoryMenu) chargeHeld(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void chargeHeld(ServerPlayer player) {
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return;
        held.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).ifPresent(storage -> {
            int actual = EmcService.supplyEnergy(player.server, player.getUUID(), storage, Integer.MAX_VALUE);
            if (actual <= 0) return;
            // 仅更新这件实物的 capability，不写任何自动补充规则。
            player.getInventory().setChanged();
            if (player.containerMenu != null) player.containerMenu.broadcastChanges();
            // EMC 服务已标记余额变化，由统一同步器发送，避免重复发包。
        });
    }
}
