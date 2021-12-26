package com.binance.bot.common;

import com.altfins.ChartPatternSignal;
import com.binance.bot.trading.VolumeProfile;

import java.time.Instant;
import java.util.Date;

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
}
