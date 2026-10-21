/**
 * 【文件职责】发现新生魔艺 Source 储存并映射为 EMC 资源端口。
 * 变更记录 #1（2026-09-16）：新增可选反射适配；使用 Source 常见 getSource/getMaxSource
 * 和 addSource/removeSource 命名，不把仅有 setSource 的绝对值方法当作增量写入。
 */
package com.primordial.resource.adapters;

import com.primordial.resource.ReflectiveResourceAdapter;
import com.primordial.resource.ResourceExchangeConversions;

public final class ArsNouveauResourceAdapter extends ReflectiveResourceAdapter {
    public ArsNouveauResourceAdapter() {
        super(ResourceExchangeConversions.ARS_SOURCE,
                new String[]{"getSource", "getCurrentSource", "source"},
                new String[]{"getMaxSource", "getSourceCapacity", "getCapacity"},
                new String[]{"addSource", "receiveSource", "insertSource"},
                new String[]{"removeSource", "extractSource", "drainSource"});
    }
    @Override protected String id() { return "ars_nouveau"; }
    @Override protected boolean matchesClass(String name) { return packagePrefix(name, "com.hollingsworth.arsnouveau."); }
}
