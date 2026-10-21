/**
 * 变更记录 #3（2026-09-13）：统一通过 EmcService 门面访问余额，并完成 ProjectE/Curios API 审计。
 * - 新增：能量换算配置键 energy_to_emc_rate 与 emc_to_energy_rate。
 * - 修改：业务层不再直接调用 ProjectECompat，保证后续适配器共享同一 EMC 结算规则。
 */
/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
/**
 * 【文件职责】
 * - 处理客户端按键事件和物品 Tooltip 扩展。
 * - Tooltip 从本地已知价值表读取物品价格，并显示 EMC 文本。
 *
 * 【变更记录 #1】2026-09-13 22:52:14（第 1 次备注·EMC 显示改造）
 * - 删除：EMC 标签和 EMC 格式化调用。
 * - 新增：EMC 标签、EmcFormatter 和旧 EMC 表到 EMC 的临时 1000:1 转换。
 * - 修改：仅改变客户端展示单位；空物品、未知价值和按键消费逻辑保持不变。
 * - 用途：使物品 Tooltip 与 HUD 使用同一显示术语；当前价格来源仍是旧价值表，待 ProjectE 服务接入后移除临时换算。
 */
/**
 * 【变更记录 #4】2026-09-15 15:11:36（第 4 次备注 · 1.0.0-emc-service）
 * - 删除：Tooltip 通过 double 和旧本地估价回退。
 * - 新增：使用服务端同步的人工覆盖（零表示禁用）并在断线时清除客户端缓存。
 * - 修改：与 EmcService 同一物品查询，人工价优先后读 ProjectE，格式化全程 BigInteger。
 * - 用途：避免大整数展示舍入、进入其他服务器沿用旧价和本地配置冒充服务端价格。
 */
/**
 * 【变更记录 #5】2026-09-16 01:24:55（第 5 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：与 ProjectE 原生 Tooltip 同名的裸 EMC 标签。
 * - 新增：中英文“原初 EMC”翻译标签，明确本模组的实际结算价格来源。
 * - 修改：仍读取服务器同步的人工价格；零价也显示，标明已经禁用转换。
 * - 用途：价格编辑保存后可直接核对本模组单价，避免把 ProjectE 独立价表的另一行数值当作保存失败。
 */
/**
 * 【变更记录 #6】2026-09-16 01:52:17（第 6 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：额外的“原初 EMC”Tooltip 和独立物品价格缓存的断线清理。
 * - 新增：无；物品 EMC 由必需依赖 ProjectE 的原生 Tooltip 统一绘制。
 * - 修改：本类只处理 HUD 按键，ProjectE 的价格同步直接更新其原有显示。
 * - 用途：避免同一物品出现两行 EMC 或产生两套价格的含义，HUD 的三种显示模式仍保留。
 */
/**
 * 【变更记录 #7】2026-10-14 00:55:00（第 7 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增 H 键在玩家手持黑洞装备时请求服务端打开模块菜单。
 * - 修改：原 HUD 快捷键及 ProjectE 工具右键控制保持不变。
 * - 用途：客户端只提交手信息，服务端复核装备身份。
 */
/**
 * 【变更记录 #8】2026-10-14 02:37:36（第 8 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：B键只发送技能使用意图，参数由服务端计算。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.client;

import com.primordial.PrimordialMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge bus 客户端事件：HUD 按键；物品 EMC 显示完全由 ProjectE 负责。 */
@Mod.EventBusSubscriber(modid = PrimordialMod.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (ClientSetup.TOGGLE_HUD.consumeClick()) {
            ClientBalanceState.hudVisible = !ClientBalanceState.hudVisible;
        }
        while(ClientSetup.SIGNATURE.consumeClick()){
            var minecraft=net.minecraft.client.Minecraft.getInstance();
            if(minecraft.player!=null&&minecraft.screen==null)com.primordial.network.PrimordialNetwork.CHANNEL.sendToServer(new com.primordial.network.C2SSignatureActionPacket());
        }
        while (ClientSetup.EQUIPMENT_MENU.consumeClick()) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) continue;
            var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
            if (!com.primordial.equipment.BlackholeGear.isGear(minecraft.player.getItemInHand(hand)))
                hand = net.minecraft.world.InteractionHand.OFF_HAND;
            if (com.primordial.equipment.BlackholeGear.isGear(minecraft.player.getItemInHand(hand)))
                com.primordial.network.PrimordialNetwork.CHANNEL.sendToServer(
                        new com.primordial.network.C2SOpenBlackholeEquipmentPacket(hand));
        }
    }

}
