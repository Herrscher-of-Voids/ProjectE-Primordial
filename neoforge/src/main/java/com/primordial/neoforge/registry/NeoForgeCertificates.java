/**
 * 【文件职责】按资源种类注册 11 张凭证和原初创造模式页；不引用可选资源模组的类型。
 * 【变更记录 #1】2026-10-23 13:30:35（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：与 Forge 相同的 11 个凭证注册 ID 和 main 创造模式页。
 * - 修改：凭证在物品注册器绑定事件总线之前注册，延迟 Supplier 只在注册完成后解析。
 * - 用途：确保缺少任意可选资源模组时仍可学习类别；物理资源输出另由相应兼容系统迁移。
 * 【变更记录 #2】2026-10-23 17:01:51（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无；现有凭证和方块顺序保持。
 * - 新增：定价器创造目录项，原生权限参数或本机创造存档主人任一满足时可见。
 * - 修改：本机身份通过物理客户端门禁后的独立 client helper 查询，专服不解析 Minecraft 客户端类。
 * - 用途：支持不开作弊的单人主人直接取用工具；仅控制目录，实际菜单和保存另走服务端授权。
 */
package com.primordial.neoforge.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.knowledge.NeoForgeCertificateItem;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class NeoForgeCertificates {
    public static final Map<Kind, DeferredHolder<Item, NeoForgeCertificateItem>> CERTIFICATES;

    static {
        Map<Kind, DeferredHolder<Item, NeoForgeCertificateItem>> entries = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            entries.put(kind, NeoForgeModRegistries.ITEMS.register(kind.itemId(), NeoForgeCertificateItem::new));
        }
        CERTIFICATES = Collections.unmodifiableMap(entries);
        NeoForgeModRegistries.CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.primordial"))
                .icon(() -> new ItemStack(NeoForgeModRegistries.BLACKHOLE_ITEM.get()))
                .displayItems((parameters, output) -> {
                    if (parameters.hasPermissions() || localCreativeOwner())
                        output.accept(NeoForgeModRegistries.PRICE_EDITOR_ITEM.get());
                        output.accept(NeoForgeModRegistries.CONFIGURATOR_ITEM.get());
                    output.accept(NeoForgeModRegistries.BLACKHOLE_ITEM.get());
                    output.accept(NeoForgeModRegistries.BLACKHOLE_CHARM_ITEM.get());
                    output.accept(NeoForgeModRegistries.TRANSMUTATION_INTERFACE_ITEM.get());
                    output.accept(NeoForgeModRegistries.EMC_ACCELERATOR.get());
                    output.accept(NeoForgeModRegistries.ENTITY_GENERATOR_ITEM.get());
                    NeoForgeModRegistries.BLACKHOLE_MATERIALS.values().forEach(entry -> output.accept(entry.get()));
                    NeoForgeModRegistries.BLACKHOLE_GEAR.values().forEach(entry -> output.accept(entry.get()));
                    CERTIFICATES.values().forEach(entry -> output.accept(entry.get()));
                }).build());
    }

    private NeoForgeCertificates() {}

    /** 显式启动静态注册；只从统一注册入口调用一次。 */
    public static void register() {}

    /** 原生参数是管理员物品的显示偏好；主人无作弊的本地创造目录需另行补充展示。 */
    private static boolean localCreativeOwner() {
        if (FMLEnvironment.dist != Dist.CLIENT) return false;
        try {
            return Boolean.TRUE.equals(Class.forName("com.primordial.neoforge.client.NeoForgeCreativePriceEditorVisibility")
                    .getMethod("isLocalCreativeOwner").invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return false;
        }
    }
}
