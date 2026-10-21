/**
 * 【文件职责】在 Mixin 初始化阶段隔离 Create 可选类，确保没有安装 Create 时只加载主模组功能。
 * 【变更记录 #1】2026-09-16 20:14:18（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：按 Forge 加载列表决定是否应用两个 Create 动力 Mixin；其余 Mixin 回调保持空实现。
 * - 修改：动力配置 required=false，缺少 Create 或版本 API 不匹配时跳过，而不是阻断启动。
 * - 用途：与 Mekanism、Create 的可选依赖隔离规则一致；ProjectE/Curios 仍由 mods.toml 强制声明。
 * 【变更记录 #5】2026-10-13 03:56:00（第 5 次备注 · 1.2.0-ftb-stage-rewards）
 * - 删除：无；保留 JEI、Create 与转化桌现有 Mixin 筛选。
 * - 新增：检查加载列表中的 ftbquests 模组 ID，仅在其存在时应用启动奖励注册 Mixin；链接错误时安全跳过。
 * - 修改：FTB 目标使用精确类名匹配，不让通用筛选意外加载 FTB API 类型。
 * - 用途：维护 FTB Quests 为可选依赖时的加载边界；不安装该模组时不解析其奖励类。
 */
/**
 * 【变更记录 #2】2026-10-02 18:20:45（第 2 次备注 · 1.2.0-media-page-isolation）
 * - 删除：所有 Mixin 均依赖 Create 存在的条件。
 * - 新增：允许客户端转化桌页签 Mixin 独立应用。
 * - 修改：Create 前缀仍按可选依赖判断，转化桌类只在 client 清单中加载。
 * - 用途：没有 Create 也能使用真正隔离的转化桌介质页，专用服务端不加载界面类。
 */
/**
 * 【变更记录 #3】2026-10-08 02:06:10（第 3 次备注 · 1.2.0-jei-counts）
 * - 删除：无；新增：按 JEI 安装状态启用两个客户端数量 Mixin。
 * - 修改：可选类通过字符串目标隔离；用途：未安装 JEI 时不加载其叠加功能。
 */
/**
 * 【变更记录 #6】2026-10-14 01:45:54（第 6 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：允许原版服务端收获 Mixin 独立加载，不依赖 Create/JEI。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #7】2026-10-14 05:43:37（第 7 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：凝聚器目标循环及刷新 Mixin 的白名单入口。
 * - 新增：ProjectE 收集器共同菜单的目标选择 Mixin 白名单入口。
 * - 修改：三代收集器都通过同一菜单父类生效，其它可选联动过滤保持原样。
 * - 用途：让黑洞材料转化只挂接用户指定的能量收集器。
 */
/**
 * 【变更记录 #8】2026-10-14 05:54:38（第 8 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：无。
 * - 新增：ProjectE 收集器燃料表重载后的同价原料排序 Mixin 入口。
 * - 修改：保持原初黑洞为可投入燃料，同时避免开启变体之间的额外升级链。
 * - 用途：服务器与客户端的收集器燃料图在同价情况下稳定一致。
 */
/**
 * 【变更记录 #9】2026-10-14 13:36:00（第 9 次备注 · 1.2.0-blackhole-emc-ladder）
 * - 删除：无。
 * - 新增：允许收集器 EMC 储量 Mixin 在 ProjectE 存在时进入通用 Mixin 清单。
 * - 修改：收集器容量补丁和目标选择共用 ProjectE 收集器目标类，其它可选模组筛选保持不变。
 * - 用途：保证 Forge 客户端及服务端都能把目标 EMC 差额存入收集器。
 */
/**
 * 【变更记录 #10】2026-10-14 14:14:39（第 10 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无。
 * - 新增：AE2 节点宿主和超越维度虚拟存储 Mixin 按精确 modId 检测。
 * - 修改：可选 API 不存在时跳过其 Mixin，收集器与通用转化逻辑仍独立加载。
 * - 用途：直接接线集成不会使可选存储模组成为强制依赖。
 */
/**
 * 【变更记录 #11】2026-10-14 14:44:00（第 11 次备注 · 1.2.0-collector-sequential-chain）
 * - 删除：无。
 * - 新增：ProjectE CollectorTooltipMixin 明确加入公共白名单。
 * - 修改：物品提示重定向也在客户端和服务端注册物品时生效。
 * - 用途：确保界面数字随实际十倍速率和扩容更新，而非被插件筛掉。
 */
/**
 * 【变更记录 #12】2026-10-14 17:10:00（第 12 次备注 · 1.2.0-rs-node-sync-crash-fix）
 * - 删除：无。新增：Refined Storage 节点同步保护的可选模组筛选。
 * - 修改：仅在 refinedstorage 存在时解析该目标类；缺少可选 API 继续跳过。
 * - 用途：防止残留或无物品映射节点在控制器列表同步时创建空 ItemStack。
 */
package com.primordial.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.minecraftforge.fml.loading.FMLLoader;

/** 不引用任何 Create/Mekanism/FTB Quests 类，允许缺少可选联动时安全加载。 */
/** 【变更记录 #4】2026-10-13（第 4 次备注 · FTB 阶段奖励）
 * - 新增：仅在 FTB Quests 存在时应用奖励注册 Mixin。
 * - 用途：不安装 FTB Quests 的环境不解析目标类，也不改变现有联动筛选。
 */
public final class PrimordialMixinPlugin implements IMixinConfigPlugin {
    private static boolean createLoaded() {
        try {
            return FMLLoader.getLoadingModList().getModFileById("create") != null;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean ftbQuestsLoaded() {
        try {
            return FMLLoader.getLoadingModList().getModFileById("ftbquests") != null;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if(mixinClassName.equals("com.primordial.mixin.RefinedStorageNetworkNodeMixin")){
            try{return FMLLoader.getLoadingModList().getModFileById("refinedstorage")!=null;}
            catch(RuntimeException | LinkageError ignored){return false;}
        }
        if(mixinClassName.equals("com.primordial.mixin.Ae2TransmutationHostMixin"))return FMLLoader.getLoadingModList().getModFileById("ae2")!=null;
        if(mixinClassName.startsWith("com.primordial.mixin.BeyondDimensions"))return FMLLoader.getLoadingModList().getModFileById("beyonddimensions")!=null;
        return mixinClassName.equals("com.primordial.mixin.FtbQuestBootstrapMixin")
                ? ftbQuestsLoaded()
                : mixinClassName.equals("com.primordial.mixin.CollectorTargetMixin")
                || mixinClassName.equals("com.primordial.mixin.CollectorFuelOrderMixin")
                || mixinClassName.equals("com.primordial.mixin.CollectorCapacityMixin")
                || mixinClassName.equals("com.primordial.mixin.CollectorTooltipMixin")
                || mixinClassName.equals("com.primordial.mixin.BlackholeArrowMixin")
                || mixinClassName.equals("com.primordial.mixin.BlackholeHarvestMixin")
                || mixinClassName.startsWith("com.primordial.mixin.Transmutation")
                || (mixinClassName.startsWith("com.primordial.mixin.JeiCount")
                    && FMLLoader.getLoadingModList().getModFileById("jei") != null)
                || (createLoaded() && mixinClassName.startsWith("com.primordial.mixin.Create"));
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo) { }
}
