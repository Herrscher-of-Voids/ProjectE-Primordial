/**
 * 【文件职责】发现植物魔法方块实体上的 Mana 接口，并映射为 EMC 资源端口。
 * 变更记录 #1（2026-09-16）：新增可选反射适配；覆盖 getCurrentMana/getMaxMana 与
 * receiveMana/extractMana 常见命名，未安装 Botania 或 API 改名时自动跳过。
 */
package com.primordial.resource.adapters;

import com.primordial.resource.ReflectiveResourceAdapter;
import com.primordial.resource.ResourceExchangeConversions;

public final class BotaniaResourceAdapter extends ReflectiveResourceAdapter {
    public BotaniaResourceAdapter() {
        super(ResourceExchangeConversions.BOTANIA_MANA,
                new String[]{"getCurrentMana", "getMana", "currentMana"},
                new String[]{"getMaxMana", "getManaCapacity", "getCapacity"},
                new String[]{"receiveMana", "addMana", "insertMana"},
                new String[]{"extractMana", "removeMana", "drainMana", "receiveMana"}, true);
    }
    @Override protected String id() { return "botania"; }
    @Override protected boolean matchesClass(String name) { return packagePrefix(name, "vazkii.botania.", "net.botania."); }
}
