/**
 * 【文件职责】保存价格编辑页提交的 Forge 流体或 Mekanism 气体 EMC 单价。
 *
 * 【变更记录 #1】2026-09-16（流体、气体价格编辑）
 * - 删除：流体和气体只能通过服务器命令修改、普通配置器价格页无法提交的限制。
 * - 新增：管理员权限、菜单身份、介质类型、注册名和有界价格文本的服务端校验。
 * - 修改：气体继续使用非负整数 EMC/mB，流体支持整数、小数和分数；保存失败时回滚并返回统一错误提示。
 * - 用途：把配置器空气潜行右键价格页接入 ValueManager，确保所有修改都在服务端线程和原子价表写入路径执行。
 */
/**
 * 【变更记录 #2】2026-09-16 01:24:55（第 2 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：权限不足、目标不匹配时静默丢弃，页面无法判断提交是否执行的处理。
 * - 新增：返回权限/目标/格式/写盘错误以及保存后的权威报价，保留非负整数气体和精确流体校验。
 * - 修改：所有结果通过 PriceEditorMenu 回传，流体输入直接作为 EMC/1000mB 保存。
 * - 用途：配置器价格页能确认真实服务端结果，只有写盘成功才显示新价，非法请求不能改动其它目标。
 */
/**
 * 【变更记录 #3】2026-10-10 02:33:01（第 3 次备注 · 1.2.0-item-price-inherit）
 * - 删除：无。
 * - 新增：接收物品 KIND_ITEM 的 inherit 请求，并沿用菜单身份、目标和权限校验。
 * - 修改：重置只允许精确的 inherit 指令；数字价格仍走原有物品保存包，流体/气体格式不变。
 * - 用途：把物品恢复原生计价接入服务器线程及 ProjectE 原生回滚/同步路径，阻止客户端越权或改错样本。
 */
/**
 * 【变更记录 #4】2026-10-10 03:04:14（第 4 次备注 · 1.2.0-price-change-audit）
 * - 删除：无；保留菜单、注册名、权限和有界价格文本校验。
 * - 新增：物品 inherit、流体和气体改价调用 PriceChangeAudit，成功后记录操作者和实际前后价格状态。
 * - 修改：原保存事务包在审计入口中执行；失败与越权请求仍只返回错误而不记成功日志。
 * - 用途：为独立定价器的恢复默认值、模式切换和数字价格建立服务端可追踪记录。
 */
/**
 * 【变更记录 #3】2026-10-11 04:26:53（第 3 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：流体/气体价格包仅检查管理员权限的保存路径。
 * - 新增：模式和数值保存前统一检查创造模式管理员权限；模式保存反馈区分模式与数值。
 * - 修改：保留注册名、菜单绑定、精确数值、原子回滚和审计日志。
 * - 用途：完整保护三类资源的定价写入，并让管理员看到真实保存结果。
 */
package com.primordial.network;

import com.primordial.network.S2CPriceEditorStatePacket.Status;
import com.primordial.menu.PriceEditorMenu;
import com.primordial.service.EmcAmount;
import com.primordial.service.PriceChangeAudit;
import com.primordial.value.ValueManager;
import com.primordial.value.ValueTable;
import com.primordial.value.PriceMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SSetMediaPricePacket(int containerId, byte kind, String id, String value) {
    /** EmcAmount 允许 4096 位分子和 128 位小数；斜杠分数的总文本长度上限为 4354。 */
    private static final int MAX_PRICE_TEXT = 4354;

    public static void encode(C2SSetMediaPricePacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.containerId);
        buffer.writeByte(message.kind);
        buffer.writeUtf(message.id == null ? "" : message.id, 256);
        buffer.writeUtf(message.value == null ? "" : message.value, MAX_PRICE_TEXT);
    }

    public static C2SSetMediaPricePacket decode(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        byte kind = buffer.readByte();
        if (kind != PriceEditorMenu.KIND_ITEM && kind != PriceEditorMenu.KIND_FLUID && kind != PriceEditorMenu.KIND_GAS)
            throw new io.netty.handler.codec.DecoderException("Invalid media price kind");
        return new C2SSetMediaPricePacket(containerId, kind,
                buffer.readUtf(256), buffer.readUtf(MAX_PRICE_TEXT));
    }

    public static void handle(C2SSetMediaPricePacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof PriceEditorMenu menu)
                    || menu.containerId != message.containerId) return;
            if (!com.primordial.service.PriceEditorAccess.canEdit(player)) {
                menu.sendPriceState(player, Status.PERMISSION_DENIED);
                return;
            }
            ResourceLocation id = message.id == null || message.id.length() > 256
                    ? null : ResourceLocation.tryParse(message.id);
            if (!menu.matchesSelection(message.kind, id)) {
                menu.sendPriceState(player, Status.INVALID_TARGET);
                return;
            }

            try {
                if (message.value == null || message.value.length() > MAX_PRICE_TEXT)
                    throw new IllegalArgumentException("Price too long");
                if (message.kind == PriceEditorMenu.KIND_ITEM) {
                    if (!"inherit".equalsIgnoreCase(message.value) || !BuiltInRegistries.ITEM.containsKey(id)
                            || menu.selectedItem().isEmpty()) {
                        menu.sendPriceState(player, Status.INVALID_PRICE);
                        return;
                    }
                    boolean saved = PriceChangeAudit.change(player, PriceChangeAudit.Kind.ITEM, id,
                            () -> com.primordial.compat.ProjectEItemPrices.resetPrice(player.server, id));
                    if (!saved) {
                        menu.sendPriceState(player, Status.SAVE_FAILED);
                        player.sendSystemMessage(Component.translatable("message.primordial.price_save_failed"));
                        return;
                    }
                } else if (message.kind == PriceEditorMenu.KIND_FLUID) {
                    if (!BuiltInRegistries.FLUID.containsKey(id)) {
                        menu.sendPriceState(player, Status.INVALID_TARGET);
                        return;
                    }
                    PriceMode mode = modeOrNull(message.value);
                    EmcAmount price = mode == null ? EmcAmount.parse(message.value) : null;
                    boolean saved = PriceChangeAudit.change(player, PriceChangeAudit.Kind.FLUID, id,
                            () -> mode != null ? ValueManager.setFluidMode(id, mode) : ValueManager.setFluidValue(id, price));
                    if (!saved) {
                        menu.sendPriceState(player, Status.SAVE_FAILED);
                        player.sendSystemMessage(Component.translatable("message.primordial.price_save_failed"));
                        return;
                    }
                    sendSavedMessage(player, id, mode, "commands.primordial.set_fluid_mb_value", message.value);
                } else if (message.kind == PriceEditorMenu.KIND_GAS) {
                    if (!com.primordial.compat.MekanismGasCompat.validGas(id)) {
                        menu.sendPriceState(player, Status.INVALID_TARGET);
                        return;
                    }
                    PriceMode mode = modeOrNull(message.value);
                    var price = mode == null ? ValueTable.parseInteger(message.value) : null;
                    boolean saved = PriceChangeAudit.change(player, PriceChangeAudit.Kind.GAS, id,
                            () -> mode != null ? ValueManager.setGasMode(id, mode) : ValueManager.setGasValue(id, price));
                    if (!saved) {
                        menu.sendPriceState(player, Status.SAVE_FAILED);
                        player.sendSystemMessage(Component.translatable("message.primordial.price_save_failed"));
                        return;
                    }
                    sendSavedMessage(player, id, mode, "commands.primordial.set_gas_value", message.value);
                } else {
                    menu.sendPriceState(player, Status.INVALID_TARGET);
                    return;
                }
                menu.sendPriceState(player, Status.SAVED);
            } catch (IllegalArgumentException invalid) {
                menu.sendPriceState(player, Status.INVALID_PRICE);
                player.sendSystemMessage(Component.translatable("message.primordial.invalid_media_price"));
            }
        });
        context.setPacketHandled(true);
    }

    private static PriceMode modeOrNull(String value) {
        try { return PriceMode.parse(value); }
        catch (RuntimeException ignored) { return null; }
    }

    private static void sendSavedMessage(ServerPlayer player, ResourceLocation id, PriceMode mode,
                                         String priceKey, String value) {
        player.sendSystemMessage(mode == null ? Component.translatable(priceKey, id, value)
                : Component.translatable("message.primordial.price_mode_saved", id,
                Component.translatable("screen.primordial.price_mode_"
                        + mode.name().toLowerCase(java.util.Locale.ROOT))));
    }
}
