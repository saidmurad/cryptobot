package com.binance.bot.common;

import com.binance.bot.tradesignals.TimeFrame;
import org.apache.commons.lang3.time.DateUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CandlestickUtil {
  public static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  public static final SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
  public static final SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
  public static final SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
  public static final SimpleDateFormat hourFormat = new SimpleDateFormat("HH");
  public static final SimpleDateFormat minuteFormat = new SimpleDateFormat("mm");

  public static Date getCandlestickStart(Date time, TimeFrame timeFrame) throws ParseException {
    int year = getDateComponent(yearFormat, time);
    int month = getDateComponent(monthFormat, time);
    int day = getDateComponent(dayFormat, time);
    int hour = getDateComponent(hourFormat, time);
    int minute = getDateComponent(minuteFormat, time);

    switch(timeFrame) {
      case FIFTEEN_MINUTES:
        return getFifteenMinuteCandlestickStart(year, month, day, hour, minute);
      case HOUR:
        return getHourlyCandlestickStart(year, month, day, hour);
      case FOUR_HOURS:
      default:
        return getFourHourlyCandlestickStart(year, month, day, hour);
    }
  }

  private static int getDateComponent(SimpleDateFormat dateFormat, Date time) {
    return Integer.parseInt(dateFormat.format(time));
  }

  static Date getFifteenMinuteCandlestickStart(int year, int month, int day, int hour, int minute) throws ParseException {
    int roundedMin = minute / 15* 15;
    String candlestickStartTimeStr = String.format("%d-%d-%d %d:%d", year, month, day, hour, roundedMin);
    return df.parse(candlestickStartTimeStr);
  }

  static Date getHourlyCandlestickStart(int year, int month, int day, int hour) throws ParseException {
    String candlestickStartTimeStr = String.format("%d-%d-%d %d:%d", year, month, day, hour, 0);
    return df.parse(candlestickStartTimeStr);
  }

  static Date getFourHourlyCandlestickStart(int year, int month, int day, int hour) throws ParseException {
    int roundedHour = hour / 4 * 4;
    String candlestickStartTimeStr = String.format("%d-%d-%d %d:%d", year, month, day, roundedHour, 0);
    return df.parse(candlestickStartTimeStr);
  }

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
