package com.binance.bot.common;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.trading.VolumeProfile;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Util {
    /**
     *
     * @param timeInMillis Timein millis.
     * @return
     */
    public static String getFormattedDate(long timeInMillis) {
        return Date.from(Instant.ofEpochMilli(timeInMillis)).toString();
    }

    public static String getCoinFromCoinPair(String coinPair) {
        assert coinPair.length() > 4;
        return coinPair.substring(0, coinPair.length() - 4);
    }

  public static String chartPatternSignalToString(ChartPatternSignal chartPatternSignal) {
      return null;
  }

  public static String volumeProfileToString(VolumeProfile volumeProfile) {
      return null;
  }

  public static double getProfitPercentAtWithPrice(ChartPatternSignal chartPatternSignal, double currPrice) {
    switch (chartPatternSignal.tradeType()) {
      case BUY:
        return (currPrice - chartPatternSignal.priceAtTimeOfSignal()) / chartPatternSignal.priceAtTimeOfSignal() * 100;
      default:
        return (chartPatternSignal.priceAtTimeOfSignal() - currPrice) / chartPatternSignal.priceAtTimeOfSignal() * 100;
    }
  }

  public static  long getTenCandleStickTimeIncrementMillis(ChartPatternSignal chartPatternSignal) {
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
