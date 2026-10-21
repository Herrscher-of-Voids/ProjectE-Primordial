/**
 * 【文件职责】推进原版天气计时器，保持原版晴天、降雨、雷暴的状态转换与随机时长。
 * 【变更记录 #1】2026-09-18 01:30:39（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：晴天保护倒计时、降雨及雷暴阶段的一次额外逻辑步进。
 * - 修改：天气强度渐变与发包仍由世界正常 tick 完成，不为每次额外调用发送天气包。
 * - 用途：由世界加速目标在校验 doWeatherCycle 后付费调用，不修改游戏总时间或强制天气类型。
 */
package com.primordial.service;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.ServerLevelData;

final class AccelerationWeather {
    private AccelerationWeather() { }

    static void tick(ServerLevelData data, RandomSource random) {
        int clear = data.getClearWeatherTime(), thunder = data.getThunderTime(), rain = data.getRainTime();
        boolean thundering = data.isThundering(), raining = data.isRaining();
        if (clear > 0) {
            clear--;
            thunder = thundering ? 0 : 1;
            rain = raining ? 0 : 1;
            thundering = false;
            raining = false;
        } else {
            if (thunder > 0) {
                if (--thunder == 0) thundering = !thundering;
            } else thunder = thundering ? ServerLevel.THUNDER_DURATION.sample(random) : 12000 + random.nextInt(168001);
            if (rain > 0) {
                if (--rain == 0) raining = !raining;
            } else rain = (raining ? ServerLevel.RAIN_DURATION : ServerLevel.RAIN_DELAY).sample(random);
        }
        data.setClearWeatherTime(clear);
        data.setThunderTime(thunder); data.setRainTime(rain);
        data.setThundering(thundering); data.setRaining(raining);
    }
}
