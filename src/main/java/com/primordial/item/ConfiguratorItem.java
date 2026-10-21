/**
 * 变更记录 #4（2026-09-14）：接入通用 MachineProxy 注册表，统一按方向读取机器能力。
 * - 新增：专用代理优先、Forge capability 代理回退。
 * - 修改：配置器能力检测和任务执行器改用代理层，保留现有槽位与配置协议。
 */
/**
 * 【文件职责】玩家右键机器时检测外部能力并打开配置菜单。
 * 【变更记录 #5】2026-09-14 00:55:10（第 5 次备注 · 1.0.0-stage5）
 * - 删除：删除非配置所有者仍可打开编辑界面的路径。
 * - 新增：所有者拒绝提示和适配器能力检测异常处理。
 * - 修改：当前用户必须拥有既有机器配置才能打开；错误接口记录日志并安全返回。
 * - 用途：与菜单幽灵槽/网络编辑权限一致，不直接改变第三方机器的所有权或侧面设置。
 */
/**
 * 【变更记录 #6】2026-09-14 13:50:00（第 6 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：空气普通右键打开玩家背包菜单，潜行右键打开价格编辑菜单。
 * - 修改：机器方块右键仍只打开机器配置器，不再携带价格页脚。
 * - 用途：按交互场景分流菜单，保持黑洞提示和机器所有者校验不变。
 */
/**
 * 【变更记录 #7】2026-09-16（独立资源机器打开修复）
 * - 删除：仅通过 MachineProxy 的物品、流体和 FE capability 判断方块是否可配置的限制。
 * - 新增：能力检查同时查询独立资源端口注册表，允许没有 Forge capability 的魔力池、魔源罐、自然祭坛和空气设备打开配置器。
 * - 修改：资源端口检查先查询内部方向，再查询六个外部方向；查询异常仍安全返回 false。
 * - 用途：资源型方块可以进入配置器，后续端口选择页再按具体资源单位和方向完成绑定。
 */
/**
 * 【变更记录 #8】2026-10-10 02:49:08（第 8 次备注 · 1.2.0-dedicated-price-editor）
 * - 删除：空气潜行右键配置器打开 PriceEditorMenu 的分支。
 * - 新增：无；定价器入口由独立 PriceEditorItem 承接。
 * - 修改：空气普通右键仍打开玩家背包配置页；潜行右键不再打开任何定价界面，机器方块交互保持不变。
 * - 用途：将公共价格管理从常用配置器中移出，避免普通配置操作意外进入管理员价格编辑页。
 */
package com.primordial.item;



import com.primordial.block.BlackholeBlock;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.menu.ConfiguratorMenu;
import com.primordial.network.PrimordialNetwork;
import com.primordial.network.S2CFullConfigSyncPacket;
import com.primordial.util.MachineKey;
import com.primordial.machine.MachineProxyRegistry;
import com.primordial.resource.ResourcePortRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

/** 配置器：右键第三方机器打开配置 GUI（黑洞不可配置） */
public class ConfiguratorItem extends Item {

    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    /** 空气非潜行右键打开玩家背包配置页；价格编辑由独立定价器物品打开。 */
    @Override
    public InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && !player.isShiftKeyDown()) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inv, p) -> new com.primordial.menu.PlayerInventoryMenu(id, inv),
                            Component.translatable("screen.primordial.player_inventory")));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (level.getBlockState(pos).getBlock() instanceof BlackholeBlock) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.primordial.blackhole_no_config"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!hasAnyCapability((ServerLevel) level, pos)) {
                player.displayClientMessage(Component.translatable("message.primordial.no_machine"), true);
                return InteractionResult.PASS;
            }
            String machineKey = MachineKey.of(level.dimension(), pos);
            var config = MachineConfigSavedData.get(level.getServer()).getOrCreate(machineKey, player.getUUID());
            if (!player.getUUID().equals(config.owner)) {
                player.displayClientMessage(Component.translatable("message.primordial.not_owner"), true);
                return InteractionResult.FAIL;
            }
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (id, inv, p) -> new ConfiguratorMenu(id, inv, machineKey),
                            Component.translatable("item.primordial.configurator")),
                    buf -> buf.writeUtf(machineKey, 256));
            // 打开后立即推送全量配置
            PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new S2CFullConfigSyncPacket(machineKey,
                            MachineConfigSavedData.get(level.getServer()).get(machineKey).save()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    static boolean hasAnyCapability(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        try {
            if (be == null) return false;
            if (MachineProxyRegistry.resolve(be).hasAny(be)) return true;
            // Botania ManaPool、Ars Nouveau SourceJar 和 Nature's Aura 祭坛通常只暴露独立资源，
            // 不提供 Forge 物品/流体/FE capability，因此必须单独查询资源端口注册表。
            if (!ResourcePortRegistry.find(be, null).isEmpty()) return true;
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.values()) {
                if (!ResourcePortRegistry.find(be, side).isEmpty()) return true;
            }
            return false;
        } catch (RuntimeException | LinkageError failure) {
            com.primordial.PrimordialMod.LOGGER.warn("[Primordial] Cannot inspect machine at {}", pos, failure);
            return false;
        }
    }

}
