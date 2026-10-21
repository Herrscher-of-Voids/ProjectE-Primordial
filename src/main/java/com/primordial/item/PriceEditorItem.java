/**
 * 【文件职责】提供独立的公共 EMC 价格编辑工具入口。
 * - 关键调用链：玩家空气右键 -> 服务端管理员权限校验 -> PriceEditorMenu -> PriceEditorScreen 与受权限保护的价格网络包。
 *
 * 【变更记录 #1】2026-10-10 02:49:08（第 1 次备注 · 1.2.0-dedicated-price-editor）
 * - 删除：无；此前价格编辑入口位于 ConfiguratorItem 的潜行右键分支。
 * - 新增：独立单件定价器物品；服务端仅允许 permission level 2 及以上玩家打开价格菜单，拒绝时显示管理员提示。
 * - 修改：价格编辑界面和服务器存储/网络提交链路继续复用，不改变已存在的逐包管理员校验。
 * - 用途：将价格管理与机器/玩家背包配置入口分离；客户端只负责交互表现，服务端负责权限判定，阻止非管理员伪造菜单入口。
 */
/**
 * 【变更记录 #2】2026-10-11 04:26:53（第 2 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：仅 permission level 2 即可打开定价器的生存模式入口。
 * - 新增：创造模式与管理员权限双重门禁，拒绝时返回失败交互结果。
 * - 修改：入口调用统一 PriceEditorAccess；原有服务端开屏和消息保持不变。
 * - 用途：落实定价器仅创造模式管理员可获得和使用的规则。
 */
package com.primordial.item;

import com.primordial.menu.PriceEditorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

public class PriceEditorItem extends Item {
    public PriceEditorItem(Properties properties) {
        super(properties);
    }

    /** 定价器只在服务端开启菜单；非管理员请求被拒绝，避免仅依赖客户端创造栏隐藏达到权限控制。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!com.primordial.service.PriceEditorAccess.canEdit(player)) {
                player.displayClientMessage(Component.translatable("message.primordial.price_permission"), true);
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            } else {
                NetworkHooks.openScreen(serverPlayer,
                        new SimpleMenuProvider((id, inv, p) -> new PriceEditorMenu(id, inv),
                                Component.translatable("screen.primordial.custom_price")));
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
