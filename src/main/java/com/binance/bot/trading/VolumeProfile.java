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

        abstract Builder setIsVolAtleastMaintained(boolean isVolAtleastMaintained);

        abstract VolumeProfile build();
    }
}
