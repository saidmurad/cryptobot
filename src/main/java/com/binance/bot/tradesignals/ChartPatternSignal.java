package com.binance.bot.tradesignals;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.util.Date;

@AutoValue
public abstract class ChartPatternSignal {
  abstract String coinPair();

  abstract TimeFrame timeFrame();

  abstract TradeType tradeType();

  abstract String pattern();

  abstract double priceAtTimeOfSignal();

  abstract Date timeOfSignal();

  abstract double priceRelatedToPattern();

  abstract double priceTarget();

  abstract double profitPotential();

  abstract boolean isSignalOn();

  abstract long volumeAtSignalCandlestick();

  abstract double volumeAverage();

  abstract boolean isVolumeSurge();

  @Nullable
  abstract Date timeOfSignalInvalidation();

  @Nullable
  abstract ReasonForSignalInvalidation reasonForSignalInvalidation();

  abstract double priceAtSignalTargetTime();

  abstract double priceAtTenCandlestickTime();

  abstract double priceBestReached();

  abstract double priceCurrent();

  @Nullable
  abstract Date currentTime();

  public Builder newBuilder() {
    return new AutoValue_ChartPatternSignal.Builder()
        .setPriceRelatedToPattern(0)
        .setIsSignalOn(true)
        .setVolumeAtSignalCandlestick(0)
        .setVolumeAverage(0)
        .setIsVolumeSurge(false)
        .setPriceAtSignalTargetTime(0)
        .setPriceAtTenCandlestickTime(0)
        .setPriceBestReached(0)
        .setPriceCurrent(0);
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder setCoinPair(String coinPair);

    public abstract Builder setTimeFrame(TimeFrame timeFrame);

    public abstract Builder setTradeType(TradeType tradeType);

    public abstract Builder setPattern(String pattern);

    public abstract Builder setPriceAtTimeOfSignal(double priceAtTimeOfSignal);

    public abstract Builder setTimeOfSignal(Date timeOfSignal);

    public abstract Builder setPriceRelatedToPattern(double priceRelatedToPattern);

    public abstract Builder setPriceTarget(double priceTarget);

    public abstract Builder setProfitPotential(double profitPotential);

    public abstract Builder setIsSignalOn(boolean isSignalOn);

    public abstract Builder setVolumeAtSignalCandlestick(long volumeAtSignalCandlestick);

    public abstract Builder setVolumeAverage(double volumeAverage);

    public abstract Builder setIsVolumeSurge(boolean isVolumeSurge);

    public abstract Builder setTimeOfSignalInvalidation(Date timeOfSignalInvalidation);

    public abstract Builder setReasonForSignalInvalidation(ReasonForSignalInvalidation reasonForSignalInvalidation);

    public abstract Builder setPriceAtSignalTargetTime(double priceAtSignalTargetTime);

    public abstract Builder setPriceAtTenCandlestickTime(double priceAtTenCandlestickTime);

    public abstract Builder setPriceBestReached(double priceBestReached);

    public abstract Builder setPriceCurrent(double priceCurrent);

    public abstract Builder setCurrentTime(Date currentTime);

    public abstract ChartPatternSignal build();
  }
}
