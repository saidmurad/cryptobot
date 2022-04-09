package com.binance.bot.common;

import com.binance.bot.tradesignals.TimeFrame;
import org.apache.commons.lang3.time.DateUtils;

import java.util.Date;

public class CandlestickUtil {
  public Date getIthCandlestickTime(Date beginCandlestick, TimeFrame timeFrame, int i) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return DateUtils.addMinutes(beginCandlestick, 15 * i);
      case HOUR:
        return DateUtils.addHours(beginCandlestick, i);
      case FOUR_HOURS:
        return DateUtils.addHours(beginCandlestick, 4 * i);
      case DAY:
      default:
        return DateUtils.addDays(beginCandlestick, i);
    }
  }
}
