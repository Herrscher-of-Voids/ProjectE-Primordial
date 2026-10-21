/**
 * 【文件职责】发现自然灵气的本地 Aura 储存并映射为 EMC 资源端口。
 * 变更记录 #1（2026-09-16）：新增可选反射适配；兼容 getAura/getCurrentAura 与
 * storeAura/addAura、drainAura/removeAura 的常见接口，缺少写入方法时不暴露端口。
 */
/**
 * 【变更记录 #2】2026-09-16（自然祭坛容器修复）
 * - 删除：只在自然祭坛方块实体本身查找 Aura 数量和读写方法的路径。
 * - 新增：调用公开 getAuraContainer() 取得 BasicAuraContainer/IAuraContainer，再复用统一端口。
 * - 修改：端口 key 同时包含方块实体类型、方向和容器类型，任务执行时仍会重新校验身份。
 * - 用途：自然祭坛的 Aura 储存位于内部容器，确保配置器能够列出并绑定该资源端口。
 */
package com.primordial.resource.adapters;

import com.primordial.resource.ReflectiveResourceAdapter;
import com.primordial.resource.ResourceExchangeConversions;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class NaturesAuraResourceAdapter extends ReflectiveResourceAdapter {
    public NaturesAuraResourceAdapter() {
        super(ResourceExchangeConversions.NATURES_AURA,
                new String[]{"getStoredAura", "getAura", "getCurrentAura", "aura"},
                new String[]{"getMaxAura", "getAuraCapacity", "getCapacity"},
                new String[]{"storeAura", "addAura", "insertAura"},
                new String[]{"drainAura", "removeAura", "extractAura"});
    }
    @Override protected String id() { return "naturesaura"; }
    @Override protected boolean matchesClass(String name) { return packagePrefix(name, "de.ellpeck.naturesaura."); }

    @Override
    public List<com.primordial.resource.EmcResourcePort> find(BlockEntity machine, Direction side) {
        if (machine == null || machine.isRemoved() || !matchesClass(machine.getClass().getName())) return List.of();
        Object container = invokeNoArg(machine, "getAuraContainer");
        if (container == null) return super.find(machine, side);
        String suffix = side == null ? "internal" : side.getName().toLowerCase(Locale.ROOT);
        String key = id() + "/" + machine.getClass().getName() + "/" + suffix + "/aura_container";
        return createPort(container, key);
    }
}
