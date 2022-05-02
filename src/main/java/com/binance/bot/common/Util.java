package com.binance.bot.common;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.trading.VolumeProfile;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class Util {
  private static final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
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

  public static String getBaseAsset(String coinPair) {
      return coinPair.substring(0, coinPair.length() - 4);
  }

  public static double getDoubleValue(String price) throws ParseException {
    return numberFormat.parse(price).doubleValue();
  }

  public static String getGateFormattedCurrencyPair(String currencyPair) {
    return currencyPair.substring(0, currencyPair.length() - 4) + "_USDT";
  }

  public static String getRoundedUpQuantity(double qty, int stepSizeNumDecimalPlaces) {
    String pattern = "#";
    for (int i = 0; i < stepSizeNumDecimalPlaces; i ++) {
      if (i == 0) {
        pattern += ".";
      }
      pattern += "#";
    }
    DecimalFormat df = new DecimalFormat(pattern);
    df.setRoundingMode(RoundingMode.CEILING);
    return df.format(qty);
  }

  public static boolean decimalCompare(double val1, double val2) {
      return Math.abs(val1 - val2) / val1 * 100 < 0.0001;
  }

  public static String getTruncatedQuantity(double qty, Integer stepSizeNumDecimalPlaces) {
    String pattern = "#";
    for (int i = 0; i < stepSizeNumDecimalPlaces; i ++) {
      if (i == 0) {
        pattern += ".";
      }
      pattern += "#";
    }
    DecimalFormat df = new DecimalFormat(pattern);
    df.setRoundingMode(RoundingMode.DOWN);
    return df.format(qty);
  }
}
