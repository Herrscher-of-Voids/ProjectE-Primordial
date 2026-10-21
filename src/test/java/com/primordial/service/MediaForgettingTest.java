/**
 * 【文件职责】验证介质遗忘的隔离、持久化和重新学习。
 * 【变更记录 #1】2026-10-02 18:46:06（第 1 次备注 · 1.2.0-media-forgetting）
 * - 删除：无。
 * - 新增：单种遗忘、玩家及类别隔离、存档往返和无效请求不写脏标记测试。
 * - 修改：运行真实 SavedData 和 NBT，不替代知识增删逻辑。
 * - 用途：避免遗忘一项清空其它知识，确保重启后不重新出现且允许再次学习。
 */
package com.primordial.service;

import com.primordial.data.MediaKnowledgeSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MediaForgettingTest {
    @Test void forgetOnlySelectedPlayerCategoryAndResource() {
        var data = new MediaKnowledgeSavedData();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        var selected = new ResourceLocation("test", "selected");
        var retained = new ResourceLocation("test", "retained");
        data.learn(owner, true, selected);
        data.learn(owner, true, retained);
        data.learn(owner, false, selected);
        data.learn(other, true, selected);
        data.setDirty(false);
        assertTrue(data.forget(owner, true, selected));
        assertTrue(data.isDirty());
        assertFalse(data.knows(owner, true, selected));
        assertTrue(data.knows(owner, true, retained));
        assertTrue(data.knows(owner, false, selected));
        assertTrue(data.knows(other, true, selected));
        assertFalse(data.knownIds(owner, true).contains(selected));
    }

    @Test void forgottenKnowledgeStaysForgottenAcrossSaveAndCanBeRelearned() {
        UUID owner = UUID.randomUUID();
        var id = new ResourceLocation("minecraft", "water");
        var data = new MediaKnowledgeSavedData();
        data.learn(owner, false, id);
        assertTrue(data.forget(owner, false, id));
        var restored = MediaKnowledgeSavedData.load(data.save(new CompoundTag()));
        assertFalse(restored.knows(owner, false, id));
        assertTrue(restored.knownIds(owner, false).isEmpty());
        assertTrue(restored.learn(owner, false, id));
        assertTrue(MediaKnowledgeSavedData.load(restored.save(new CompoundTag())).knows(owner, false, id));
    }

    @Test void invalidOrRepeatedForgettingDoesNotDirtyTheSave() {
        var data = new MediaKnowledgeSavedData();
        UUID owner = UUID.randomUUID();
        var id = new ResourceLocation("minecraft", "water");
        assertFalse(data.forget(null, false, id));
        assertFalse(data.forget(owner, false, null));
        assertFalse(data.isDirty());
        assertTrue(data.forget(owner, false, id));
        assertTrue(data.isForgotten(owner, false, id));
        data.setDirty(false);
        assertFalse(data.forget(owner, false, id));
        assertFalse(data.isDirty());
    }

    @Test void certificateOnlyExclusionsPersistAndOnlySameResourceRelearningClearsThem() {
        UUID owner = UUID.randomUUID();
        var selected = new ResourceLocation("mekanism", "hydrogen");
        var other = new ResourceLocation("mekanism", "oxygen");
        var data = new MediaKnowledgeSavedData();
        assertTrue(data.forget(owner, true, selected));
        var restored = MediaKnowledgeSavedData.load(data.save(new CompoundTag()));
        assertTrue(restored.isForgotten(owner, true, selected));
        assertFalse(restored.isForgotten(owner, false, selected));
        restored.learn(owner, true, other);
        assertTrue(restored.isForgotten(owner, true, selected));
        restored.learn(owner, true, selected);
        assertFalse(restored.isForgotten(owner, true, selected));
        assertFalse(MediaKnowledgeSavedData.load(restored.save(new CompoundTag())).isForgotten(owner, true, selected));
    }
}
