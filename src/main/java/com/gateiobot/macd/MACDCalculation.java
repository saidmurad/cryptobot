package com.gateiobot.macd;

import com.binance.bot.common.CandlestickUtil;
import com.binance.bot.common.Mailer;
import com.binance.bot.tradesignals.TimeFrame;
import com.gateiobot.GateIoClientFactory;
import com.gateiobot.db.*;
import com.google.common.collect.ImmutableList;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.GateApiException;
import io.gate.gateapi.api.SpotApi;
import io.gate.gateapi.models.CurrencyPair;
import io.gate.gateapi.models.MarginCurrencyPair;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.*;

@SpringBootApplication(scanBasePackages = {"com.gateiobot"})
@Configuration
@Component
public class MACDCalculation {
  private static final double MIN_DIFF_FOR_TRENDING = 0.25;
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  static int NUM_SHARDS = 100;
  boolean isTest = false;
  private final SpotApi spotApi;
  private final Mailer mailer;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static final int SMA_NUM_WINDOWS = 30;
  private static final int HISTOGRAM_EMA_NUM_WINDOWS = 5;
  private static final int SMA_NUM_PERIODS_AGO_TO_DIFF = 10;
  private static final int CANDLESTICK_INDEX_START_TIME = 0;
  private static final int CANDLESTICK_INDEX_CLOSING_PRICE = 2;

  int NUM_CANDLESTICKS_MINUS_ONE = 999;

  @Autowired
  public MACDCalculation(GateIoClientFactory gateIoClientFactory, Mailer mailer) throws ParseException {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    spotApi = gateIoClientFactory.getSpotApi();
    this.mailer = mailer;
  }

  public List<MACDData> getMACDData(String coinPair, Date candlestickTime, TimeFrame timeFrame) throws ApiException, ParseException {
    SpotApi.APIlistCandlesticksRequest req = spotApi.listCandlesticks(coinPair);
    Date candlestickFromTime = CandlestickUtil.getIthCandlestickTime(candlestickTime, timeFrame, -NUM_CANDLESTICKS_MINUS_ONE);
    req = req.from(candlestickFromTime.getTime() / 1000);
    req = req.to(candlestickTime.getTime() / 1000);
    req = req.interval(getTimeInterval(timeFrame));
    List<List<String>> candlesticks = req.execute();
    if (candlesticks.isEmpty()) {
      return ImmutableList.of();
    }
    List<MACDData> macdDataList = new ArrayList<>(candlesticks.size());
    double thirtyPeriodSum = 0;
    for (int i = 0; i < candlesticks.size(); i ++) {
      MACDData macdData = new MACDData();
      macdData.coinPair = coinPair;
      macdData.timeFrame = timeFrame;
      macdData.time = new Date(Long.parseLong(candlesticks.get(i).get(CANDLESTICK_INDEX_START_TIME)) * 1000);
      macdDataList.add(macdData);
      macdData.candleClosingPrice = Double.parseDouble(candlesticks.get(i).get(CANDLESTICK_INDEX_CLOSING_PRICE));
      thirtyPeriodSum += macdData.candleClosingPrice;
      // SMA 30 Calculation
      if (i + 1 > SMA_NUM_WINDOWS) {
        thirtyPeriodSum -= Double.parseDouble(candlesticks.get(i - SMA_NUM_WINDOWS).get(CANDLESTICK_INDEX_CLOSING_PRICE));
      }
      double smaSlope = 0;
      TrendType trendType = TrendType.NA;
      if (i + 1 >= SMA_NUM_WINDOWS) {
        macdData.sma = thirtyPeriodSum / SMA_NUM_WINDOWS;
        // Because QUACK_USDT has all prices 0.
        if (macdDataList.get(i - SMA_NUM_PERIODS_AGO_TO_DIFF + 1).sma > 0) {
          double smaTenCandlesticksAgo = macdDataList.get(i - SMA_NUM_PERIODS_AGO_TO_DIFF + 1).sma;
          smaSlope = (macdData.sma - smaTenCandlesticksAgo) / smaTenCandlesticksAgo * 100;
          trendType = getTrendType(smaSlope);
        }
      }
      macdData.smaSlope = smaSlope;
      macdData.trendType = trendType;

      // EMA 12 calculation starts on the 13th period, when 12 day SMA is available
      if (i >= 12) {
        double multipler = 2.0 / (12 + 1);
        double prevPeriodEMA;
        if (i == 12) {
          // SMA for the First 12 windows' values.
          prevPeriodEMA = getSMA(macdDataList, 12);
        } else {
          prevPeriodEMA = macdDataList.get(i - 1).ema12;
        }
        macdData.ema12 = (1 - multipler) * prevPeriodEMA
            + multipler * macdData.candleClosingPrice;
      }

      // EMA 26th calculation starts on the 27th period, when 26 day SMA is available
      if (i >= 26) {
        double multipler = 2.0 / (26 + 1);
        double prevPeriodEMA;
        if (i == 26) {
          prevPeriodEMA = getSMA(macdDataList, 26);
        } else {
          prevPeriodEMA = macdDataList.get(i - 1).ema26;
        }
        macdData.ema26 = (1 - multipler) * prevPeriodEMA
            + multipler * macdDataList.get(i).candleClosingPrice;
        macdData.macd = macdData.ema12 - macdData.ema26;
      }

      double prevPeriodMACD_EMA9 = 0;
      boolean emaHistogramApplicable = false;
      // MACD Signal line starts after 9 SMA of MACD is available.
      // i ==35 && macdDatamergedList.get(34).macd != 0 will happen take care of setting MACD for when there are no pre-existing rows in DB to start with.
      if (i == 35) {
        // since ema26 is available starting from the 27th period.
        double sumMACD9Periods = 0;
        // TODO: Mistake. should have been j = 25; j < 34. actually ok, since sma of first 9 is not used as the ema like ema12 and ima226
        /**
         macd                   N        N    sma9
         ema26      n/a         Y        Y     Y
         n          26          27       34    35    36

         i          25                         34    35
         */
        for (int j = 26; j < 35; j++) {
          sumMACD9Periods += macdDataList.get(j).macd;
        }
        prevPeriodMACD_EMA9 = sumMACD9Periods / 9;
      }
      // i will be > 0 there will always be preexisting rows in DB for this case coz i starts from "numRowsInDB".
      else if (i > 35) {
        prevPeriodMACD_EMA9 = macdDataList.get(i - 1).macdSignal;
      }
      if (i >= 35) {
        double multiplier = 2.0 / (9 + 1);
        macdDataList.get(i).macdSignal = (1 - multiplier) * prevPeriodMACD_EMA9
            + multiplier * macdDataList.get(i).macd;
        macdDataList.get(i).histogram = macdDataList.get(i).macd - macdDataList.get(i).macdSignal;
        double prevPeriodHistogramEMA = 0.0;
        if (i == 35 + HISTOGRAM_EMA_NUM_WINDOWS) {
          double prevPeriodHistogramSum = 0.0;
          for (int j = 35; j < 35 + HISTOGRAM_EMA_NUM_WINDOWS; j++) {
            prevPeriodHistogramSum += macdDataList.get(j).histogram;
          }
          prevPeriodHistogramEMA = prevPeriodHistogramSum / HISTOGRAM_EMA_NUM_WINDOWS;
          emaHistogramApplicable = true;
        } else if (i > 35 + HISTOGRAM_EMA_NUM_WINDOWS) {
          prevPeriodHistogramEMA = macdDataList.get(i - 1).histogramEMA;
          emaHistogramApplicable = true;
        }
        if (emaHistogramApplicable) {
          double multipler2 = 2.0 / (HISTOGRAM_EMA_NUM_WINDOWS + 1);
          macdDataList.get(i).histogramEMA = (1 - multipler2) * prevPeriodHistogramEMA + multipler2
              * macdDataList.get(i).histogram;
          double comp = macdDataList.get(i).histogram - macdDataList.get(i).histogramEMA;
          if (comp == 0.0) {
            macdDataList.get(i).histogramTrendType = HistogramTrendType.PLATEAUED;
          } else if (comp < 0.0) {
            macdDataList.get(i).histogramTrendType = HistogramTrendType.DECELERATING;
          } else {
            macdDataList.get(i).histogramTrendType = HistogramTrendType.ACCELERATING;
          }
        } else {
          macdDataList.get(i).histogramTrendType = HistogramTrendType.NA;
        }
      } else {
        macdDataList.get(i).histogramTrendType = HistogramTrendType.NA;
      }
    }
    return macdDataList;
  }

  private double getSMA(List<MACDData> macdDataList, int n) {
    double sum = 0;
    for (int i = 0; i < n; i++) {
      sum += macdDataList.get(i).candleClosingPrice;
    }
    return sum / n;
  }

  private TrendType getTrendType(double differencePercent) {
    if (Math.abs(differencePercent) < MIN_DIFF_FOR_TRENDING) {
      return TrendType.RANGING;
    }
    if (differencePercent < 0) {
      return TrendType.BEARISH;
    }
    return TrendType.BULLISH;
  }

  private String getTimeInterval(TimeFrame timeFrame) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return "15m";
      case HOUR:
        return "1h";
      case FOUR_HOURS:
        return "4h";
      case DAY:
      default:
        return "1d";
    }
  }

  private Date getCandlestickStart(Date time, TimeFrame timeFrame) throws ParseException {
    SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
    SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
    SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
    SimpleDateFormat hourFormat = new SimpleDateFormat("HH");
    SimpleDateFormat minuteFormat = new SimpleDateFormat("mm");
    yearFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    monthFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    hourFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    minuteFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
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
        return getFourHourlyCandlestickStart(year, month, day, hour);
      case DAY:
      default:
        return getDailyCandlestickStart(year, month, day);
    }
  }

  private static int getDateComponent(SimpleDateFormat dateFormat, Date time) {
    return Integer.parseInt(dateFormat.format(time));
  }

  static Date getFifteenMinuteCandlestickStart(int year, int month, int day, int hour, int minute) throws ParseException {
    int roundedMin = minute / 15* 15;
    String candlestickStartTimeStr = String.format("%d-%d-%d %d:%d", year, month, day, hour, roundedMin);
    return dateFormat.parse(candlestickStartTimeStr);
  }

  static Date getHourlyCandlestickStart(int year, int month, int day, int hour) throws ParseException {
    String candlestickStartTimeStr = String.format("%d-%d-%d %d:%d", year, month, day, hour, 0);
    try {
      return dateFormat.parse(candlestickStartTimeStr);
    } catch (NumberFormatException ex) {
      throw ex;
    }
  }

  Date getFourHourlyCandlestickStart(int year, int month, int day, int hour) throws ParseException {
    int roundedHour = hour / 4 * 4;
    String candlestickStartTimeStr = String.format("%d-%d-%d %d:%d", year, month, day, roundedHour, 0);
    if (candlestickStartTimeStr.isEmpty()) {
      logger.error("candlestickStartTimeStr is empty");
    }
    return dateFormat.parse(candlestickStartTimeStr);
  }

  static Date getDailyCandlestickStart(int year, int month, int day) throws ParseException {
    String candlestickStartTimeStr = String.format("%d-%d-%d %d:%d", year, month, day, 0, 0);
    return dateFormat.parse(candlestickStartTimeStr);
  }
}
