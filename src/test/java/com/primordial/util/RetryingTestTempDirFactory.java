/**
 * 【文件职责】为价格/知识持久化测试创建独立临时目录，并有界重试 Windows 删除延迟。
 * 【变更记录 #1】2026-10-10 04:14:04（第 1 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：JUnit TempDirFactory，在 close 中仅清理自己创建的目录，最多 10 次、100ms 间隔。
 * - 修改：使用该工厂的 TempDir 将清理交给工厂；任何持续删除错误仍抛 IOException，不跳过测试或遗留目录。
 * - 用途：解决 Windows 原子替换后的短暂 DirectoryNotEmpty 清理失败；不改变模组交易、存档或断言。
 */
package com.primordial.util;

import org.junit.jupiter.api.io.TempDirFactory;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public final class RetryingTestTempDirFactory implements TempDirFactory {
    private Path created;

    @Override public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context) throws IOException {
        created = Files.createTempDirectory("primordial-junit-").toAbsolutePath().normalize();
        return created;
    }

    /** 不跟随符号链接；只能删除本工厂创建的根及其子项，持续失败仍交给 JUnit 报错。 */
    @Override public void close() throws IOException {
        if (created == null) return;
        IOException last = null;
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                Files.walkFileTree(created, new SimpleFileVisitor<>() {
                    private void delete(Path path) throws IOException {
                        if (!path.toAbsolutePath().normalize().startsWith(created)) throw new IOException("Temp cleanup escaped its root");
                        Files.deleteIfExists(path);
                    }
                    @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                        delete(path);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult postVisitDirectory(Path path, IOException failure) throws IOException {
                        if (failure != null) throw failure;
                        delete(path);
                        return FileVisitResult.CONTINUE;
                    }
                });
                return;
            } catch (NoSuchFileException missing) {
                if (!Files.exists(created)) return;
                last = missing;
            } catch (IOException failure) { last = failure; }
            if (attempt < 9) {
                try { Thread.sleep(100); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while cleaning test directory", interrupted);
                }
            }
        }
        throw last;
    }
}
