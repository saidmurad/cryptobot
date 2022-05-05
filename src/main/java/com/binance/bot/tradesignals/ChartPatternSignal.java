package com.binance.bot.tradesignals;

import com.binance.api.client.domain.OrderStatus;
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

  public abstract int attempt();

  @Nullable
  public abstract TradeExitType tradeExitType();

  @Nullable
  public abstract Date timeOfInsertion();

  public abstract boolean isInsertedLate();

  @Nullable
  public abstract Double stopLossPrice();

  @Override
  public String toString() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    return String.format("CoinPair: %s, TimeFrame: %s, TradeType: %s, Pattern: %s, Time of signal: %s, " +
            "Attempt: %d, Price Target: %f " +
            "Price Target Time: %s, Attempt: %d",
        coinPair(), timeFrame().name(), tradeType().name(), pattern(), dateFormat.format(timeOfSignal()),
        attempt(), priceTarget(),
        dateFormat.format(priceTargetTime()), attempt());
  }

  public String toStringOrderValues() {
    return toString() + String.format("\nEntry Order: %s\nExit Stop Limit Order: %s\nExit Market Order: %s.",
        entryOrder() != null ? entryOrder() : "", exitStopLimitOrder() != null ? exitStopLimitOrder() : "",
        exitOrder() != null ? exitOrder() : "");
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
    int hash = Objects.hash(this.coinPair(), this.timeFrame(), this.tradeType(), this.pattern(),
        this.timeOfSignal());
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

  @Nullable
  public abstract Double profitPercentAtSignalTargetTime();

  public abstract double priceBestReached();

  public abstract double priceCurrent();

  public abstract boolean failedToGetPriceAtTenCandlestickTime();

  public abstract boolean failedToGetPriceAtSignalTargetTime();

  @Nullable
  public abstract Date currentTime();

  public abstract double priceAtTimeOfSignalInvalidation();

  @Nullable
  public abstract Double profitPercentAtTimeOfSignalInvalidation();

  public abstract int numTimesMissingInInput();

  @Nullable
  public abstract Date tenCandlestickTime();

  @Nullable
  public abstract Double maxLoss();

  @Nullable
  public abstract Double maxLossPercent();

  @Nullable
  public abstract Date maxLossTime();

  @Nullable
  public abstract Date twoPercentLossTime();

  @Nullable
  public abstract Date fivePercentLossTime();

  @Nullable
  public abstract Boolean isPriceTargetMet();

  @Nullable
  public abstract Date priceTargetMetTime();

  @AutoValue
  public abstract static class Order {
    public static enum OrderStatusInt {
     OPEN,
     PARTIALLY_FILLED,
     FILLED,
      CANCELED
    }

    public abstract long orderId();
    public abstract double executedQty();

    public abstract double avgPrice();

    public abstract OrderStatusInt status();

    // For use by the Dao mapper.
    public static AutoValue_ChartPatternSignal_Order create(
        long orderId, double executedQty, double avgPrice, OrderStatusInt status) {
      return new AutoValue_ChartPatternSignal_Order(orderId, executedQty, avgPrice, status);
    }
    public static AutoValue_ChartPatternSignal_Order create(
        long orderId, double executedQty, double avgPrice, OrderStatus status) {
      return new AutoValue_ChartPatternSignal_Order(orderId, executedQty, avgPrice,
          convertBinancOrderStatus(status));
    }

    public static AutoValue_ChartPatternSignal_Order create(
        long orderId, double executedQty, double avgPrice, io.gate.gateapi.models.Order.StatusEnum status) {
      return new AutoValue_ChartPatternSignal_Order(orderId, executedQty, avgPrice,
          convertGateIoOrderStatus(status));
    }

    public static OrderStatusInt convertGateIoOrderStatus(io.gate.gateapi.models.Order.StatusEnum status) {
      switch (status) {
        case OPEN:
          return OrderStatusInt.OPEN;
        case CANCELLED:
          return OrderStatusInt.CANCELED;
        case CLOSED:
        default:
          return OrderStatusInt.FILLED;
      }
    }

    private static OrderStatusInt convertBinancOrderStatus(OrderStatus status) {
      switch (status) {
        case NEW:
          return OrderStatusInt.OPEN;
        case CANCELED:
          return OrderStatusInt.CANCELED;
        case PARTIALLY_FILLED:
          return OrderStatusInt.PARTIALLY_FILLED;
        case FILLED:
          // TODO: Untested for binance, error prone.
        default:
          return OrderStatusInt.FILLED;
      }
    }

    @Override
    public String toString() {
      return String.format("Order Id: %d Executed Qty: %f Average Price: %f Order Status: %s.",
          orderId(), executedQty(), avgPrice(), status().name());
    }
  }

  @Nullable
  public abstract Order entryOrder();

  @Nullable
  public abstract Order exitStopLimitOrder();

  @Nullable
  public abstract Order exitOrder();

  @Nullable
  public abstract Boolean isPositionExited();

  @Nullable
  public abstract Double realized();

  @Nullable
  public abstract Double realizedPercent();

  @Nullable
  public abstract Double unRealized();

  @Nullable
  public abstract Double unRealizedPercent();

  public static Builder newBuilder() {
    return new AutoValue_ChartPatternSignal.Builder()
        .setAttempt(1)
        .setPriceRelatedToPattern(0)
        .setIsSignalOn(true)
        .setVolumeAtSignalCandlestick(0)
        .setVolumeAverage(0)
        .setIsVolumeSurge(false)
        .setPriceAtTimeOfSignalInvalidation(0)
        .setPriceAtSignalTargetTime(0)
        .setPriceAtTenCandlestickTime(0)
        .setFailedToGetPriceAtTenCandlestickTime(false)
        .setFailedToGetPriceAtSignalTargetTime(false)
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

    public abstract Builder setAttempt(int attempt);

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

    public abstract Builder setProfitPercentAtTimeOfSignalInvalidation(Double profitPercentAtTimeOfSignalInvalidation);

    public abstract Builder setReasonForSignalInvalidation(ReasonForSignalInvalidation reasonForSignalInvalidation);

    public abstract Builder setPriceAtSignalTargetTime(double priceAtSignalTargetTime);

    public abstract Builder setPriceAtTenCandlestickTime(double priceAtTenCandlestickTime);

    public abstract Builder setFailedToGetPriceAtTenCandlestickTime(boolean failedToGetPriceAtTenCandlestickTime);

    public abstract Builder setFailedToGetPriceAtSignalTargetTime(boolean failedToGetPriceAtSignalTargetTime);

    public abstract Builder setPriceBestReached(double priceBestReached);

    public abstract Builder setPriceCurrent(double priceCurrent);

    public abstract Builder setCurrentTime(Date currentTime);

    public abstract ChartPatternSignal build();

    public abstract Builder setProfitPercentAtTenCandlestickTime(double profitPercentAtTenCandlestickTime);

    public abstract Builder setProfitPercentAtSignalTargetTime(Double profitPercentAtSignalTargetTime);

    public abstract Builder setEntryOrder(Order entryOrder);

    public abstract Builder setExitStopLimitOrder(Order exitStopLimitOrder);

    public abstract Builder setExitOrder(Order exitOrder);

    public abstract Builder setIsPositionExited(Boolean isPositionExited);

    public abstract Builder setTenCandlestickTime(Date tenCandlestickTime);

    public abstract Builder setRealized(Double realized);

    public abstract Builder setRealizedPercent(Double realizedPercent);

    public abstract Builder setUnRealized(Double unRealized);

    public abstract Builder setUnRealizedPercent(Double unRealizedPercent);

    public abstract Builder setMaxLoss(Double maxLoss);

    public abstract Builder setMaxLossPercent(Double maxLossPercent);

    public abstract Builder setMaxLossTime(Date maxLossTime);

    public abstract Builder setTwoPercentLossTime(Date twoPercentLossTime);

    public abstract Builder setFivePercentLossTime(Date fivePercentLossTime);

    public abstract Builder setIsPriceTargetMet(Boolean isPriceTargetMet);

    public abstract Builder setPriceTargetMetTime(Date priceTargetMetTime);

    public abstract Builder setTradeExitType(TradeExitType tradeExitType);

    public abstract Builder setStopLossPrice(Double stopLossPrice);

    public Builder copy(ChartPatternSignal that) {
      return ChartPatternSignal.newBuilder()
          .setCoinPair(that.coinPair())
          .setTimeFrame(that.timeFrame())
          .setTradeType(that.tradeType())
          .setPattern(that.pattern())
          .setAttempt(that.attempt())
          .setPriceAtTimeOfSignal(that.priceAtTimeOfSignal())
          .setPriceAtTimeOfSignalReal(that.priceAtTimeOfSignalReal())
          .setPriceTargetTime(that.priceTargetTime())
          .setTimeOfSignal(that.timeOfSignal())
          .setPriceRelatedToPattern(that.priceRelatedToPattern())
          .setNumTimesMissingInInput(that.numTimesMissingInInput())
          .setPriceTarget(that.priceTarget())
          .setProfitPotentialPercent(that.profitPotentialPercent())
          .setProfitPercentAtTenCandlestickTime(that.profitPercentAtTenCandlestickTime())
          .setProfitPercentAtSignalTargetTime(that.priceAtSignalTargetTime())
          .setIsSignalOn(that.isSignalOn())
          .setVolumeAtSignalCandlestick(that.volumeAtSignalCandlestick())
          .setVolumeAverage(that.volumeAverage())
          .setIsVolumeSurge(that.isVolumeSurge())
          .setTimeOfSignalInvalidation(that.timeOfSignalInvalidation())
          .setPriceAtTimeOfSignalInvalidation(that.priceAtTimeOfSignalInvalidation())
          .setProfitPercentAtTimeOfSignalInvalidation(that.profitPercentAtTimeOfSignalInvalidation())
          .setReasonForSignalInvalidation(that.reasonForSignalInvalidation())
          .setPriceAtSignalTargetTime(that.priceAtSignalTargetTime())
          .setPriceAtTenCandlestickTime(that.priceAtTenCandlestickTime())
          .setFailedToGetPriceAtTenCandlestickTime(that.failedToGetPriceAtTenCandlestickTime())
          .setFailedToGetPriceAtSignalTargetTime(that.failedToGetPriceAtSignalTargetTime())
          .setPriceBestReached(that.priceBestReached())
          .setPriceCurrent(that.priceCurrent())
          .setCurrentTime(that.currentTime())
          .setTimeOfInsertion(that.timeOfInsertion())
          .setIsInsertedLate(that.isInsertedLate())
          .setEntryOrder(that.entryOrder())
          .setExitStopLimitOrder(that.exitStopLimitOrder())
          .setExitOrder(that.exitOrder())
          .setIsPositionExited(that.isPositionExited())
          .setTenCandlestickTime(that.tenCandlestickTime())
          .setMaxLoss(that.maxLoss())
          .setMaxLossPercent(that.maxLossPercent())
          .setMaxLossTime(that.maxLossTime())
              .setTwoPercentLossTime(that.twoPercentLossTime())
              .setFivePercentLossTime(that.fivePercentLossTime())
          .setIsPriceTargetMet(that.isPriceTargetMet())
          .setPriceTargetMetTime(that.priceTargetMetTime())
          .setRealized(that.realized())
          .setRealizedPercent(that.realizedPercent())
          .setUnRealized(that.unRealized())
          .setUnRealizedPercent(that.unRealizedPercent())
          .setTradeExitType(that.tradeExitType())
          .setStopLossPrice(that.stopLossPrice());
    }
  }
}
