package com.binance.bot.tradesignals;

import com.google.auto.value.AutoValue;

import java.util.Date;

@AutoValue
public abstract class ChartPatternSignal {
  abstract String coinPair();

  abstract TradeType tradeType();

  abstract double priceAtTimeOfSignal();

  abstract Date timeOfSignal();

  abstract double priceRelatedToPattern();

  abstract double priceTarget();

  abstract double profitPotential();

  public Builder newBuilder() {
    return new AutoValue_ChartPatternSignal.Builder()
        .setPriceRelatedToPattern(0);
  }

  @AutoValue.Builder
  public abstract class Builder {
    public abstract Builder setCoinPair(String coinPair);

    public abstract Builder setTradeType(TradeType tradeType);

    public abstract Builder setPriceAtTimeOfSignal(double priceAtTimeOfSignal);

    public abstract Builder setTimeOfSignal(Date timeOfSignal);

    public abstract Builder setPriceRelatedToPattern(double priceRelatedToPattern);

    public abstract Builder setPriceTarget(double priceTarget);

    public abstract Builder setProfitPotential(double profitPotential);

    public abstract ChartPatternSignal build();
  }
}
