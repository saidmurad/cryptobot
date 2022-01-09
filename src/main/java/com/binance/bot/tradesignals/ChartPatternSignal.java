package com.binance.bot.tradesignals;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.util.Date;

@AutoValue
public abstract class ChartPatternSignal {
  public abstract String coinPair();

  public abstract TimeFrame timeFrame();

  public abstract TradeType tradeType();

  public abstract String pattern();

  public abstract double priceAtTimeOfSignal();

  public abstract Date timeOfSignal();

  public abstract Date priceTargetTime();

  public abstract double priceRelatedToPattern();

  public abstract double priceTarget();

  public abstract double profitPotentialPercent();

  public abstract boolean isSignalOn();

  public abstract long volumeAtSignalCandlestick();

  public abstract double volumeAverage();

  public abstract boolean isVolumeSurge();

  @Nullable
  public abstract Date timeOfSignalInvalidation();

  @Nullable
  public abstract ReasonForSignalInvalidation reasonForSignalInvalidation();

  public abstract double priceAtSignalTargetTime();

  public abstract double priceAtTenCandlestickTime();

  public abstract double priceBestReached();

  public abstract double priceCurrent();

  @Nullable
  public abstract Date currentTime();

  public static Builder newBuilder() {
    return new AutoValue_ChartPatternSignal.Builder()
        .setPriceRelatedToPattern(0)
        .setIsSignalOn(true)
        .setVolumeAtSignalCandlestick(0)
        .setVolumeAverage(0)
        .setIsVolumeSurge(false)
        .setPriceAtSignalTargetTime(0)
        .setPriceAtTenCandlestickTime(0)
        .setPriceBestReached(0)
        .setPriceCurrent(0)
        .setProfitPotentialPercent(0);
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder setCoinPair(String coinPair);

    public abstract Builder setTimeFrame(TimeFrame timeFrame);

    public abstract Builder setTradeType(TradeType tradeType);

    public abstract Builder setPattern(String pattern);

    public abstract Builder setPriceAtTimeOfSignal(double priceAtTimeOfSignal);

    public abstract Builder setPriceTargetTime(Date priceTargetTime);

    public abstract Builder setTimeOfSignal(Date timeOfSignal);

    public abstract Builder setPriceRelatedToPattern(double priceRelatedToPattern);

    public abstract Builder setPriceTarget(double priceTarget);

    public abstract Builder setProfitPotentialPercent(double profitPotentialPercent);

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
