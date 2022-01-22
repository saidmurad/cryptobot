package com.binance.bot.trading;

import com.binance.api.client.domain.market.Candlestick;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class VolumeProfile {
    public abstract List<Candlestick> recentCandlesticks();

    public abstract Candlestick currentCandlestick();

    public abstract double minVol();

    public abstract double maxVol();

    public abstract double avgVol();

    public abstract boolean isVolSurged();

    public abstract boolean isVolAtleastMaintained();

    public static Builder newBuilder() {
        return new AutoValue_VolumeProfile.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setRecentCandlesticks(List<Candlestick> recentCandlesticks);

        public abstract Builder setCurrentCandlestick(Candlestick currentCandlestick);

        public abstract Builder setMinVol(double minVol);

        public abstract Builder setMaxVol(double maxVol);

        public abstract Builder setAvgVol(double avgVol);

        public abstract Builder setIsVolSurged(boolean isVolSurged);

        public abstract Builder setIsVolAtleastMaintained(boolean isVolAtleastMaintained);

        public abstract VolumeProfile build();
    }
}
