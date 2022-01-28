package com.binance.bot.signalsuccessfailure;

import com.binance.bot.tradesignals.ChartPatternSignal;

import java.util.concurrent.TimeUnit;

public class Util {
  static double getProfitPercentAtTenCandlestickTime(ChartPatternSignal chartPatternSignal, double tenCandleStickTimePrice) {
    switch (chartPatternSignal.tradeType()) {
      case BUY:
        return (tenCandleStickTimePrice - chartPatternSignal.priceAtTimeOfSignal()) / chartPatternSignal.priceAtTimeOfSignal() * 100;
      default:
        return (chartPatternSignal.priceAtTimeOfSignal() - tenCandleStickTimePrice) / chartPatternSignal.priceAtTimeOfSignal() * 100;
    }
  }

  static  long getTenCandleStickTimeIncrementMillis(ChartPatternSignal chartPatternSignal) {
    switch (chartPatternSignal.timeFrame()) {
      case FIFTEEN_MINUTES:
        return TimeUnit.MINUTES.toMillis(150);
      case HOUR:
        return TimeUnit.HOURS.toMillis(10);
      case FOUR_HOURS:
        return TimeUnit.HOURS.toMillis(40);
      default:
        return TimeUnit.DAYS.toMillis(10);
    }
  }

}
