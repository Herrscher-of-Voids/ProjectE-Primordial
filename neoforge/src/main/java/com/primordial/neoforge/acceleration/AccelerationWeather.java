package com.primordial.neoforge.acceleration;

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
