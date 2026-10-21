/**
 * 【文件职责】删除发送者选中的背包补充规则，不读取或移动背包实物。
 * 【变更记录 #1】2026-09-15 11:38:10（第 1 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：批量解绑包，复用开关包的 36 槽范围、数组复制和菜单身份校验。
 * - 修改：服务端删除规则后立即广播状态；重开菜单和重启后不再恢复已删除规则。
 * - 用途：彻底停止空槽或有物品槽的补充，暂停功能仍可用于临时停止。
 */
package com.primordial.network;

import com.primordial.data.PlayerInventoryConfigSavedData;
import com.primordial.menu.PlayerInventoryMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SClearPlayerInventoryRulesPacket(C2SSetPlayerInventoryEnabledPacket selection) {
    public C2SClearPlayerInventoryRulesPacket(int containerId, int[] slots) {
        this(new C2SSetPlayerInventoryEnabledPacket(containerId, slots, false));
    }

    public static void encode(C2SClearPlayerInventoryRulesPacket msg, FriendlyByteBuf buf) {
        C2SSetPlayerInventoryEnabledPacket.encode(msg.selection, buf);
    }

    public static C2SClearPlayerInventoryRulesPacket decode(FriendlyByteBuf buf) {
        return new C2SClearPlayerInventoryRulesPacket(C2SSetPlayerInventoryEnabledPacket.decode(buf));
    }

    public static void handle(C2SClearPlayerInventoryRulesPacket msg, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || !(player.containerMenu instanceof PlayerInventoryMenu menu)
                    || menu.containerId != msg.selection.containerId()) return;
            var data = PlayerInventoryConfigSavedData.get(player.server);
            for (int slot : msg.selection.slots()) data.clear(player.getUUID(), slot);
            menu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
