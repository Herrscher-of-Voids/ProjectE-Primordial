/**
 * 【变更记录 #6】2026-10-11 23:44:54（第 6 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；原创造物品及定价器权限展示规则不变。
 * - 新增：转化接口方块物品作为可测试内容加入创造标签。
 * - 修改：放置后必须由外部存储网络主动请求，标签展示本身不触发交易。
 * - 用途：支持用户在指定实例中搭建 AE2/RS 网络验收接口行为。
 */
/**
 * 【文件职责】定义 Primordial 创造模式标签及其展示顺序。
 *
 * 【变更记录 #1】2026-09-15 15:12:00（第 1 次备注 · 1.0.0-stage8-blackhole-charm）
 * - 删除：无；保留配置器和黑洞方块图标及展示。
 * - 新增：黑洞护符展示项，便于测试 Curios 装备和范围回收功能。
 * - 修改：展示顺序按配置器、黑洞方块、黑洞护符排列；不改变注册 ID。
 * - 用途：创造标签直接暴露阶段 8 新物品，避免玩家必须使用命令获取测试样本。
 */
/**
 * 【变更记录 #2】2026-09-16 13:42:00（第 2 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无。
 * - 新增：EMC 加速器展示项。
 * - 修改：在现有配置器、黑洞和护符后展示加速器，不改变其它物品注册顺序。
 * - 用途：玩家可以从创造标签和 JEI 中找到新增工具与合成配方。
 */
/**
 * 【文件职责】生成本模组创造物品列表。
 * 【变更记录 #3】2026-09-16 19:41:50（第 3 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：按资源类别展示九种凭证。
 * - 修改：凭证只在注册完成后解析，仍可在创造模式检查资源。
 * - 用途：关闭开关取消的是配方与EMC，已有物品与注册身份仍保留。
 */
/**
 * 【变更记录 #4】2026-10-10 02:49:08（第 4 次备注 · 1.2.0-dedicated-price-editor）
 * - 删除：无；现有创造模式物品展示顺序和标签身份保留。
 * - 新增：独立定价器作为创造物品栏展示项。
 * - 修改：无；定价器仅通过创造物品栏/管理员命令获得，不创建生存配方。
 * - 用途：管理员在创造模式工具栏可以直接取用定价器，实际打开菜单仍由服务端权限校验。
 */
/**
 * 【变更记录 #5】2026-10-10 02:52:33（第 5 次备注 · 1.2.0-dedicated-price-editor）
 * - 删除：无；其他创造物品展示条件和次序不变。
 * - 新增：定价器展示项增加 ItemDisplayParameters.hasPermissions() 条件，仅向有管理员权限的创造模式玩家提供。
 * - 修改：普通玩家仍可看见模组创造栏中的其它物品，但不会获得定价器的创造栏入口；管理员命令获取仍可用。
 * - 用途：将“仅创造模式且管理员可获得”同时落实到创造栏展示，并与定价器 use 和价格包的服务端权限校验共同构成入口保护。
 */
/**
 * 【变更记录 #7】2026-10-14 00:52:40（第 7 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增四件黑洞护甲、十件工具及七种实体模块的创造栏入口。
 * - 修改：保留原配置器、方块、护符和凭证的展示顺序，装备集中排列。
 * - 用途：游戏内可立即取得整套物品测试合成、模块安装与 EMC 能力。
 */
/**
 * 【变更记录 #8】2026-10-14 01:18:08（第 8 次备注 · 1.2.0-equipment-emc-features）
 * - 删除：实体模块物品关联。
 * - 新增：装备内购买功能关联。
 * - 修改：移除七种实体模块的创造栏入口。
 * - 用途：功能由装备界面 EMC 解锁，避免继续提供旧实物安装入口。
 */
/**
 * 【变更记录 #9】2026-10-14 02:04:37（第 9 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：创造栏显示九色黑洞材料，便于 JEI 与游戏验收。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #10】2026-10-14 16:01:56（第 10 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：旧实体无报价/无入账路径的相关限制（其它历史功能保留）。
 * - 新增：创造栏加入实体生成器，生存配方由资源定义。
 * - 修改：服务端校验、报价与统一 EMC 结算共同执行；不信任客户端价格或 NBT。
 * - 用途：完成实体生成、黑洞回收与同账户融合系统，失败不重复收费或产出。
 */
/**
 * 【变更记录 #11】2026-10-23 17:01:51（第 11 次备注 · 2.0.0-neoforge-pricing 双版本权限对齐）
 * - 删除：定价器仅凭创造目录 hasPermissions 展示，导致未开作弊的创造存档主人看不见工具的限制。
 * - 新增：保留原权限展示，另允许本机单人集成服务器的创造模式主人显示定价器。
 * - 修改：先核对物理客户端，再反射独立 client helper；专用服务器不解析 Minecraft 客户端类。
 * - 用途：只修正创造目录入口，不给 LAN 访客继承主人身份，实际打开和保存继续由服务端 PriceEditorAccess 核验。
 */
package com.primordial.registry;

import com.primordial.PrimordialMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PrimordialMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.primordial"))
                    .icon(() -> new ItemStack(ModItems.CONFIGURATOR.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.CONFIGURATOR.get());
                        if (params.hasPermissions() || localCreativeOwner()) output.accept(ModItems.PRICE_EDITOR.get());
                        output.accept(ModItems.BLACKHOLE.get());
                        output.accept(ModItems.TRANSMUTATION_INTERFACE.get());
                        output.accept(ModItems.ENTITY_GENERATOR.get());
                        output.accept(ModItems.BLACKHOLE_CHARM.get());
                        output.accept(ModItems.EMC_ACCELERATOR.get());
                        for (String name : new String[]{"helmet", "chestplate", "leggings", "boots", "pick", "axe",
                                "shovel", "hoe", "shears", "sword", "hammer", "katar", "morning_star", "bow"})
                            output.accept(ModItems.BLACKHOLE_GEAR.get(name).get());
                        ModItems.BLACKHOLE_MATERIALS.values().forEach(entry -> output.accept(entry.get()));
                        ModItems.CERTIFICATES.values().forEach(entry -> output.accept(entry.get()));
                    })
                    .build());

    /** 创造目录参数还受客户端“管理员物品”显示设置影响，不能用作存档主人或服务端权限判据。 */
    private static boolean localCreativeOwner() {
        if (FMLEnvironment.dist != Dist.CLIENT) return false;
        try {
            return Boolean.TRUE.equals(Class.forName("com.primordial.client.CreativePriceEditorVisibility")
                    .getMethod("isLocalCreativeOwner").invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return false;
        }
    }
}
