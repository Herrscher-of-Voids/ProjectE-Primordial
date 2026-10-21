/**
 * 【文件职责】实现 Curios 护符槽黑洞饰品，在玩家周围有限范围内吸收有价掉落物并一次性结算 EMC。
 *
 * 【变更记录 #1】2026-09-15 15:12:00（第 1 次备注 · 1.0.0-stage8-blackhole-charm）
 * - 删除：无护符物品只能依赖世界黑洞方块回收掉落物的限制。
 * - 新增：Curios ICurioItem、范围扫描、每 tick 实体/物品预算、筛选规则、自动学习和批量 EMC 入账。
 * - 修改：只在服务端按配置间隔扫描；同一 tick 聚合金额后调用一次 EmcService，部分堆栈保留余量。
 * - 用途：提供可佩戴的移动黑洞，同时以有限 AABB 和预算控制实体列表、NBT 复制、ProjectE 同步及内存峰值。
 */
/**
 * 【变更记录 #2】2026-09-15 15:35:00（第 2 次备注 · 1.0.0-stage10-performance）
 * - 删除：每次护符扫描都发送 actionbar 文本的高频网络路径。
 * - 新增：按物品注册名和 NBT hash 去重自动学习调用。
 * - 修改：回收仍然一次聚合 EMC，客户端反馈改由 GUI/余额 HUD 提供，避免大量掉落物时每 tick 创建消息组件。
 * - 用途：降低多人佩戴护符时的网络包、文本分配和客户端渲染压力，保持吸收和结算语义不变。
 */
/**
 * 【变更记录 #3】2026-09-15 15:10:02（第 3 次备注 · 1.0.0-stage10-performance）
 * - 删除：装饰槽位也执行回收扫描的路径。
 * - 新增：检测 SlotContext.cosmetic() 并跳过装饰槽位回调。
 * - 修改：只有实际装备的 charm 槽执行一次范围扫描，避免双护符视觉槽重复吸收和结算。
 * - 用途：消除装饰槽重复处理造成的 EMC 复制风险和额外实体遍历。
 */
/**
 * 【变更记录 #4】2026-09-16 01:59:37（第 4 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：回收与购买强行共用同一个数值、绕过 ProjectE 损耗规则的假定。
 * - 新增：黑洞护符按 ProjectE 回收价格吸收掉落物，保留数量预算和零价跳过逻辑。
 * - 修改：交易定价统一委托 ProjectE，扩展流体/气体定价和 BigInteger 玩家余额不受影响。
 * - 用途：让本模组回收物品与转化桌回收遵循同一套价值规则。
 */
/**
 * 【文件职责】实现Curios护符回收及筛选入口。
 * 【变更记录 #5】2026-09-16 19:41:50（第 5 次备注 · 1.0.0-review-ready）
 * - 删除：移除筛选后台存在但玩家无法打开编辑器的限制。
 * - 新增：手持右键打开BlackholeCharmMenu，物品提示说明筛选类别。
 * - 修改：服务端绑定打开时手持护符；Curios回收继续调用同一匹配器。
 * - 用途：玩家取下护符即可编辑，样本不会被消耗或在菜单中丢出。
 */
package com.primordial.item;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Curios 会在物品注册后通过 CuriosApi.registerCurio 绑定本实现。 */
public final class BlackholeCharmItem extends Item implements ICurioItem {
    public BlackholeCharmItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** 手持右键打开过滤器，佩戴时先取下；编辑样本不会消耗物品。 */
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            net.minecraftforge.network.NetworkHooks.openScreen(serverPlayer,
                    new net.minecraft.world.SimpleMenuProvider((id, inventory, owner) ->
                            new com.primordial.menu.BlackholeCharmMenu(id, inventory, hand),
                            net.minecraft.network.chat.Component.translatable("screen.primordial.charm.title")),
                    buffer -> buffer.writeEnum(hand));
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
            List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flags) {
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.primordial.charm_filters"));
    }

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return context != null && "charm".equals(context.identifier());
    }

    @Override
    public void curioTick(SlotContext context, ItemStack charm) {
        if (context == null || context.cosmetic() || !(context.entity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        int interval = PrimordialServerConfig.blackholeCharmScanIntervalTicks();
        long gameTime = level.getGameTime();
        if (interval > 1 && Math.floorMod(gameTime + player.getId(), interval) != 0) return;

        int radius = PrimordialServerConfig.blackholeCharmRadius();
        int entityLimit = PrimordialServerConfig.blackholeCharmMaxEntitiesPerTick();
        int itemLimit = PrimordialServerConfig.blackholeCharmMaxItemsPerTick();
        if (radius <= 0 || entityLimit <= 0 || itemLimit <= 0) return;

        List<ItemEntity> entities = level.getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(radius), entity -> entity != null && !entity.isRemoved());
        EmcAmount total = EmcAmount.ZERO;
        Set<String> learned = new HashSet<>();
        int handledEntities = 0;
        int handledItems = 0;
        for (ItemEntity entity : entities) {
            if (handledEntities >= entityLimit || handledItems >= itemLimit) break;
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || !BlackholeCharmFilter.accepts(charm, stack)) continue;
            EmcAmount unitPrice = EmcService.itemRecoveryPrice(stack, level.getServer());
            if (unitPrice.isZero()) continue;
            int accepted = Math.min(stack.getCount(), itemLimit - handledItems);
            if (accepted <= 0) break;

            total = total.add(unitPrice.multiply(accepted));
            handledItems += accepted;
            handledEntities++;
            if (accepted >= stack.getCount()) entity.discard();
            else {
                entity.setItem(stack.copyWithCount(stack.getCount() - accepted));
                entity.setPickUpDelay(10);
            }
            if (PrimordialServerConfig.autoLearnReclaimedItems()) {
                String key = BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":"
                        + (stack.getTag() == null ? 0 : stack.getTag().hashCode());
                if (learned.add(key)) EmcService.learn(level.getServer(), player.getUUID(), stack.copyWithCount(1));
            }
        }
        if (!total.isZero()) {
            EmcService.credit(level.getServer(), player.getUUID(), total);
        }
    }
}
