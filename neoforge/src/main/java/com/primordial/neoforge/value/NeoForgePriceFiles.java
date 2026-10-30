/**
 * 【文件职责】价格文件以 UTF-8 在同目录临时文件完整写入后替换。
 * 【变更记录 #1】2026-10-23 16:59:12（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：原子替换、无原子移动支持时回退及临时文件清理。
 * - 修改：清理异常附加到原始保存异常，避免失败被误报成功。
 * - 用途：价格内存发布必须发生在本方法成功返回之后。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class NeoForgePriceFiles {
    private NeoForgePriceFiles() {}

    static void atomicWrite(Path file, byte[] bytes) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "primordial-price-", ".tmp");
        IOException failure = null;
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            failure = exception;
            throw exception;
        } finally {
            try { Files.deleteIfExists(temporary); }
            catch (IOException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup); else throw cleanup;
            }
        }
    }
}
