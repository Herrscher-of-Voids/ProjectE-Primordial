/**
 * 【文件职责】测试中捕获真实服务器 PriceAudit 日志并解析单行 JSON。
 * 【变更记录 #1】2026-10-10 03:05:29（第 1 次备注 · 1.2.0-price-change-audit）
 * - 删除：无。
 * - 新增：可关闭的 Log4j appender，仅收集 PriceAudit 消息；关闭时恢复 logger 原等级并卸载 appender。
 * - 修改：无；不替代价格事务或审计实现。
 * - 用途：让改价测试验证实际日志内容与失败不输出；日志收集使用线程安全列表，避免后台日志写入影响遍历。
 */
package com.primordial.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class PriceAuditLogCapture extends AbstractAppender implements AutoCloseable {
    final List<JsonObject> entries = new CopyOnWriteArrayList<>();
    private final Logger logger = (Logger) LogManager.getLogger("com.primordial.PrimordialMod");
    private final Level oldLevel = logger.getLevel();
    private static final String PREFIX = "[Primordial][PriceAudit] ";

    PriceAuditLogCapture() {
        super("PriceAuditCapture", null, null, false, Property.EMPTY_ARRAY);
        start();
        logger.setLevel(Level.INFO);
        logger.addAppender(this);
    }

    @Override public void append(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();
        if (message.startsWith(PREFIX)) entries.add(JsonParser.parseString(message.substring(PREFIX.length())).getAsJsonObject());
    }

    @Override public void close() {
        logger.removeAppender(this);
        logger.setLevel(oldLevel);
        stop();
    }
}
