package com.binance.bot.trading;

import com.binance.api.client.domain.market.Candlestick;
import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
abstract class VolumeProfile {
    abstract List<Candlestick> recentCandlesticks();

    abstract Candlestick currentCandlestick();

    abstract double minVol();

    abstract double maxVol();

    abstract double avgVol();

    abstract boolean isVolSurged();

    static Builder builder() {
        return new AutoValue_VolumeProfile.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        abstract Builder setRecentCandlesticks(List<Candlestick> recentCandlesticks);

        abstract Builder setCurrentCandlestick(Candlestick currentCandlestick);

        abstract Builder setMinVol(double minVol);

        abstract Builder setMaxVol(double maxVol);

        abstract Builder setAvgVol(double avgVol);

        abstract Builder setIsVolSurged(boolean isVolSurged);

        abstract VolumeProfile build();
    }
}
