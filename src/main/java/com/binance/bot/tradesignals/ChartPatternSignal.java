package com.binance.bot.tradesignals;

import com.google.auto.value.AutoValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;

@AutoValue
public abstract class ChartPatternSignal {
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public abstract String coinPair();

  public abstract TimeFrame timeFrame();

  public abstract TradeType tradeType();

  public abstract String pattern();

  public abstract Date timeOfSignal();

  @Nullable
  public abstract Date timeOfInsertion();

  public abstract boolean isInsertedLate();

  @Override
  public String toString() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return String.format("CoinPair: %s, TimeFrame: %s, TradeType: %s, Pattern: %s, Time of signal: %s, Price Target: %f",
        coinPair(), timeFrame().name(), tradeType().name(), pattern(), dateFormat.format(timeOfSignal()), priceTarget());
  }
  @Override
  public boolean equals(Object that) {
    ChartPatternSignal other = (ChartPatternSignal) that;
    boolean ret= this.coinPair().equals(other.coinPair()) && this.timeFrame() == other.timeFrame() && this.tradeType() == other.tradeType()
        && this.pattern().equals(other.pattern()) && this.timeOfSignal().equals(other.timeOfSignal());
    /*if (this.coinPair().equals(other.coinPair()) && this.timeFrame() == other.timeFrame() && this.tradeType() == other.tradeType()
        && this.pattern().equals(other.pattern())) {
      logger.info("equals returning " + ret + " for comparison of \n" + toString() + "\nand \n" + that.toString());
    }*/
    return ret;
  }

  @Override
  public int hashCode() {
    int hash = Objects.hash(this.coinPair(), this.timeFrame(), this.tradeType(), this.pattern(), this.timeOfSignal());
    return hash;
  }

  public abstract double priceAtTimeOfSignal();

  // What we saw after receiving    the signal since price may have already moved significantly.
  @Nullable
  public abstract Double priceAtTimeOfSignalReal();

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

  public abstract double profitPercentAtTenCandlestickTime();

  public abstract double priceBestReached();

  public abstract double priceCurrent();

  public abstract boolean failedToGetPriceAtTenCandlestickTime();

  @Nullable
  public abstract Date currentTime();

  public abstract double priceAtTimeOfSignalInvalidation();

  public abstract int numTimesMissingInInput();

  @AutoValue
  public abstract static class Trade {
    public abstract long orderId();

    public abstract double price();

    public abstract double qty();

    public static AutoValue_ChartPatternSignal_Trade create(long orderId, double price, double qty) {
      return new AutoValue_ChartPatternSignal_Trade(orderId, price, qty);
    }
  }

  @Nullable
  public abstract Trade entryTrade();

  @Nullable
  public abstract Trade exitTrade();

  public static Builder newBuilder() {
    return new AutoValue_ChartPatternSignal.Builder()
        .setPriceRelatedToPattern(0)
        .setIsSignalOn(true)
        .setVolumeAtSignalCandlestick(0)
        .setVolumeAverage(0)
        .setIsVolumeSurge(false)
        .setPriceAtTimeOfSignalInvalidation(0)
        .setPriceAtSignalTargetTime(0)
        .setPriceAtTenCandlestickTime(0)
        .setFailedToGetPriceAtTenCandlestickTime(false)
        .setProfitPercentAtTenCandlestickTime(0)
        .setPriceBestReached(0)
        .setPriceCurrent(0)
        .setNumTimesMissingInInput(0)
        .setProfitPotentialPercent(0)
        .setIsInsertedLate(false);
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder setCoinPair(String coinPair);

    public abstract Builder setTimeFrame(TimeFrame timeFrame);

    public abstract Builder setTradeType(TradeType tradeType);

    public abstract Builder setPattern(String pattern);

    public abstract Builder setPriceAtTimeOfSignal(double priceAtTimeOfSignal);

    public abstract Builder setPriceAtTimeOfSignalReal(Double priceAtTimeOfSignalReal);

    public abstract Builder setPriceTargetTime(Date priceTargetTime);

    public abstract Builder setTimeOfSignal(Date timeOfSignal);

    public abstract Builder setTimeOfInsertion(Date timeOfInsertion);

    public abstract Builder setIsInsertedLate(boolean isInsertedLate);

    public abstract Builder setPriceRelatedToPattern(double priceRelatedToPattern);

    public abstract Builder setNumTimesMissingInInput(int numTimesMissingInInput);

    public abstract Builder setPriceTarget(double priceTarget);

    public abstract Builder setProfitPotentialPercent(double profitPotentialPercent);

    public abstract Builder setIsSignalOn(boolean isSignalOn);

    public abstract Builder setVolumeAtSignalCandlestick(long volumeAtSignalCandlestick);

    public abstract Builder setVolumeAverage(double volumeAverage);

    public abstract Builder setIsVolumeSurge(boolean isVolumeSurge);

    public abstract Builder setTimeOfSignalInvalidation(Date timeOfSignalInvalidation);

    public abstract Builder setPriceAtTimeOfSignalInvalidation(double priceAtTimeOfSignalInvalidation);

    public abstract Builder setReasonForSignalInvalidation(ReasonForSignalInvalidation reasonForSignalInvalidation);

    public abstract Builder setPriceAtSignalTargetTime(double priceAtSignalTargetTime);

    public abstract Builder setPriceAtTenCandlestickTime(double priceAtTenCandlestickTime);

    public abstract Builder setFailedToGetPriceAtTenCandlestickTime(boolean failedToGetPriceAtTenCandlestickTime);

    public abstract Builder setPriceBestReached(double priceBestReached);

    public abstract Builder setPriceCurrent(double priceCurrent);

    public abstract Builder setCurrentTime(Date currentTime);

    public abstract ChartPatternSignal build();

    public abstract Builder setProfitPercentAtTenCandlestickTime(double profitPercentAtTenCandlestickTime);

    public abstract Builder setEntryTrade(Trade entryTrade);

    public abstract Builder setExitTrade(Trade exitTrade);

    public Builder copy(ChartPatternSignal that) {
      return ChartPatternSignal.newBuilder()
          .setCoinPair(that.coinPair())
          .setTimeFrame(that.timeFrame())
          .setTradeType(that.tradeType())
          .setPattern(that.pattern())
          .setPriceAtTimeOfSignal(that.priceAtTimeOfSignal())
          .setPriceAtTimeOfSignalReal(that.priceAtTimeOfSignalReal())
          .setPriceTargetTime(that.priceTargetTime())
          .setTimeOfSignal(that.timeOfSignal())
          .setPriceRelatedToPattern(that.priceRelatedToPattern())
          .setNumTimesMissingInInput(that.numTimesMissingInInput())
          .setPriceTarget(that.priceTarget())
          .setProfitPotentialPercent(that.profitPotentialPercent())
          .setIsSignalOn(that.isSignalOn())
          .setVolumeAtSignalCandlestick(that.volumeAtSignalCandlestick())
          .setVolumeAverage(that.volumeAverage())
          .setIsVolumeSurge(that.isVolumeSurge())
          .setTimeOfSignalInvalidation(that.timeOfSignalInvalidation())
          .setPriceAtTimeOfSignalInvalidation(that.priceAtTimeOfSignalInvalidation())
          .setReasonForSignalInvalidation(that.reasonForSignalInvalidation())
          .setPriceAtSignalTargetTime(that.priceAtSignalTargetTime())
          .setPriceAtTenCandlestickTime(that.priceAtTenCandlestickTime())
          .setFailedToGetPriceAtTenCandlestickTime(that.failedToGetPriceAtTenCandlestickTime())
          .setPriceBestReached(that.priceBestReached())
          .setPriceCurrent(that.priceCurrent())
          .setCurrentTime(that.currentTime())
          .setTimeOfInsertion(that.timeOfInsertion())
          .setIsInsertedLate(that.isInsertedLate())
          .setEntryTrade(that.entryTrade())
          .setExitTrade(that.exitTrade());
    }
  }
}
