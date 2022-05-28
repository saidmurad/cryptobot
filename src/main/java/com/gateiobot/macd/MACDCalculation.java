package com.gateiobot.macd;

import com.binance.bot.common.Mailer;
import com.binance.bot.tradesignals.TimeFrame;
import com.gateiobot.GateIoClientFactory;
import com.gateiobot.db.*;
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
@ConditionalOnProperty(
    prefix = "command.line.runner",
    value = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Component
public class MACDCalculation implements CommandLineRunner {
  private static final double MIN_DIFF_FOR_TRENDING = 0.25;
  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  static int NUM_SHARDS = 100;
  boolean isTest = false;
  private final SpotApi spotApi;
  private final Date START_TIME;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private Clock clock;
  private final MACDDataDao macdDataDao;
  private static final int SMA_NUM_WINDOWS = 30;
  private static final int HISTOGRAM_EMA_NUM_WINDOWS = 5;
  private static final int SMA_NUM_PERIODS_AGO_TO_DIFF = 10;
  private static final int CANDLESTICK_INDEX_START_TIME = 0;
  private static final int CANDLESTICK_INDEX_CLOSING_PRICE = 2;
  private final Set<String> invalidCurrencyPairs = new HashSet<>();

  int NUM_CANDLESTICKS_MINUS_ONE = 999;

  @Autowired
  public MACDCalculation(GateIoClientFactory gateIoClientFactory, MACDDataDao macdDataDao) throws ParseException {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    START_TIME = dateFormat.parse("2021-12-01 00:00");
    spotApi = gateIoClientFactory.getSpotApi();
    this.macdDataDao = macdDataDao;
    this.clock = Clock.systemDefaultZone();
  }

  void setClock(Clock mockClock) {
    this.clock = mockClock;
  }
  /*
    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_0() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 0);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_1() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 1);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_2() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 2);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_3() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 3);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_4() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 4);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_5() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 5);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_6() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 6);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_7() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 7);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_8() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 8);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataFifteenMinutesTimeFrame_9() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 9);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_0() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 0);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_1() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 1);
    }


    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_2() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 2);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_3() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 3);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_4() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 4);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_5() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 5);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_6() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 6);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_7() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 7);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_8() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 8);
    }

    @Scheduled(fixedDelay=60000, initialDelayString = "${timing.initialDelay}")
    public void fillMACDDataHourlyTimeFrame_9() throws MessagingException, ApiException {
      fillMACDDataPartitioned(TimeFrame.HOUR, 9);
    }
    */
  public void fillMACDDataPartitioned(TimeFrame timeFrame, int i, List<CurrencyPair> marginPairs)
      throws MessagingException, ApiException, InterruptedException {
    int numMarginPairs = marginPairs.size();
    int chunkSize = numMarginPairs / NUM_SHARDS;

    int startIndex = i * chunkSize;
    int chunkSizeForItr = chunkSize;
    if (i == NUM_SHARDS - 1) {
      chunkSizeForItr = numMarginPairs - (NUM_SHARDS - 1) * chunkSize;
    }
    while (true) {
      fillMACDData(timeFrame, startIndex, chunkSizeForItr, marginPairs);
      if (isTest) {
        return;
      }
      Thread.sleep(60000);
    }
  }

  boolean exitAfterOneIteration = false;
  void fillMACDData(TimeFrame timeFrame, int startIndex, int chunkSize, List<CurrencyPair> marginPairs) throws MessagingException {
    try {
      boolean allDone;
      do {
        allDone = true;
        Set<String> seen = new HashSet<>();
        for (int i = startIndex; i < startIndex + chunkSize; i ++) {
          CurrencyPair currencyPair = marginPairs.get(i);
          if (invalidCurrencyPairs.contains(currencyPair.getId()) || seen.contains(currencyPair.getId())) {
            continue;
          }
          seen.add(currencyPair.getId());
          boolean ret;
          try {
            //logger.info(String.format("Calling fillMACDData for CoinPair %s and Timeframe %s.", currencyPair.getId(), timeFrame.name()));
            ret = fillMACDData(currencyPair.getId(), timeFrame);
          } catch (GateApiException ex) {
            if (ex.getErrorLabel().equals("INVALID_CURRENCY") || ex.getErrorLabel().equals("INVALID_CURRENCY_PAIR")) {
              //logger.info(currencyPair.getId() + " is invalid");
              invalidCurrencyPairs.add(currencyPair.getId());
              ret = true;
            } else {
              throw ex;
            }
          }
          allDone &= ret;
        }
      } while (!allDone && !exitAfterOneIteration);
    } catch (Exception ex) {
      logger.error("Exception", ex);
      mailer.sendEmail("MACDCalculation exception", ex.getMessage());
    }
  }

  private Mailer mailer = new Mailer();
  /*
   * @return whether no more data to fill for now.
   * @throws ApiException
   */
  boolean fillMACDData(String coinPair, TimeFrame timeFrame) throws ParseException, ApiException {
    // Query for SMA_NUM_WINDOWS -1 because current candle stick from the exchange will add to the list.
    List<MACDData> macdRowsFromDB = macdDataDao.getMACDDataList(coinPair, timeFrame, SMA_NUM_WINDOWS - 1);
    Pair<Date, Date> firstAndLastCandlesticksToQuery = getStartDateAndEndDateForCandlesticksRequest(
        macdRowsFromDB, timeFrame);
    if (firstAndLastCandlesticksToQuery == null) {
      return true;
    }
    boolean firstIteration = firstAndLastCandlesticksToQuery.getFirst().equals(START_TIME);
    int numRowsFromDB = macdRowsFromDB.size();
    SpotApi.APIlistCandlesticksRequest req = spotApi.listCandlesticks(coinPair);
    req = req.from(firstAndLastCandlesticksToQuery.getFirst().getTime() / 1000);
    req = req.to(firstAndLastCandlesticksToQuery.getSecond().getTime() / 1000);
    req = req.interval(getTimeInterval(timeFrame));
    List<List<String>> candlesticksReturned = req.execute();
    List<MACDData> candlesticks = convertToMACDData(candlesticksReturned, coinPair, timeFrame);
    // However it may happen
    if (candlesticks.isEmpty()) {
      return true;
    }
    List<MACDData> macdDatamergedList = new ArrayList();
    macdDatamergedList.addAll(macdRowsFromDB);
    macdDatamergedList.addAll(candlesticks);

    double thirtyPeriodSum = 0;
    for (int i = 0; i < numRowsFromDB; i++) {
      thirtyPeriodSum += macdDatamergedList.get(i).candleClosingPrice;
    }
    int windowStartIndex = 0;
    for (int i = numRowsFromDB; i < macdDatamergedList.size(); i ++) {
      double candleClosePrice = candlesticks.get(i - numRowsFromDB).candleClosingPrice;
      // SMA 30 Calculation
      if (i - windowStartIndex + 1 > SMA_NUM_WINDOWS) {
        thirtyPeriodSum -= macdDatamergedList.get(windowStartIndex).candleClosingPrice;
        windowStartIndex++;
      }
      int windowSize = i - windowStartIndex + 1;
      thirtyPeriodSum += candleClosePrice;
      double sma = thirtyPeriodSum / windowSize;
      double smaSlope = 0;
      TrendType trendType = TrendType.NA;
      // Because QUACK_USDT has all prices 0.
      if (windowSize >= SMA_NUM_PERIODS_AGO_TO_DIFF && macdDatamergedList.get(i - SMA_NUM_PERIODS_AGO_TO_DIFF + 1).sma >0) {
        double smaTenCandlesticksAgo = macdDatamergedList.get(i - SMA_NUM_PERIODS_AGO_TO_DIFF + 1).sma;
        smaSlope = (sma - smaTenCandlesticksAgo) / smaTenCandlesticksAgo * 100;
        trendType = getTrendType(smaSlope);
      }
      macdDatamergedList.get(i).sma = sma;
      macdDatamergedList.get(i).smaSlope = smaSlope;
      macdDatamergedList.get(i).trendType = trendType;

      // EMA 12 calculation starts on the 13th period, when 12 day SMA is available
      if (i >= 12) {
        double multipler = 2.0 / (12 + 1);
        double prevPeriodEMA;
        if (i == 12) {
          prevPeriodEMA = macdDatamergedList.get(11).sma; // sma's window size was not 30 yet but 26.
        } else {
          prevPeriodEMA = macdDatamergedList.get(i - 1).ema12;
        }
        macdDatamergedList.get(i).ema12 = (1 - multipler) * prevPeriodEMA
            + multipler * macdDatamergedList.get(i).candleClosingPrice;
      }

      // EMA 26th calculation starts on the 27th period, when 26 day SMA is available
      if (i >= 26) {
        double multipler = 2.0 / (26 + 1);
        double prevPeriodEMA;
        if (i == 26) {
          prevPeriodEMA = macdDatamergedList.get(25).sma;
        } else {
          prevPeriodEMA = macdDatamergedList.get(i - 1).ema26;
        }
        macdDatamergedList.get(i).ema26 = (1 - multipler) * prevPeriodEMA
            + multipler * macdDatamergedList.get(i).candleClosingPrice;
        macdDatamergedList.get(i).macd = macdDatamergedList.get(i).ema12 - macdDatamergedList.get(i).ema26;
      }

      double prevPeriodMACD_EMA9 = 0;
      boolean ema26Applicable = false;
      boolean emaHistogramApplicable = false;
      // MACD Signal line starts after 9 SMA of MACD is available.
      // i ==35 && macdDatamergedList.get(34).macd != 0 will happen take care of setting MACD for when there are no pre-existing rows in DB to start with.
      if (i == 35 && firstIteration) {
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
          sumMACD9Periods += macdDatamergedList.get(j).macd;
        }
        prevPeriodMACD_EMA9 = sumMACD9Periods / 9;
        ema26Applicable = true;
      }
      // i will be > 0 there will always be preexisting rows in DB for this case coz i starts from "numRowsInDB".
      else if (firstIteration && i > 35 || !firstIteration) {
        prevPeriodMACD_EMA9 = macdDatamergedList.get(i - 1).macdSignal;
        ema26Applicable = true;
      }
      if (ema26Applicable) {
        double multiplier = 2.0 / (9 + 1);
        macdDatamergedList.get(i).macdSignal = (1 - multiplier) * prevPeriodMACD_EMA9
            + multiplier * macdDatamergedList.get(i).macd;
        macdDatamergedList.get(i).histogram = macdDatamergedList.get(i).macd - macdDatamergedList.get(i).macdSignal;
        double prevPeriodHistogramEMA = 0.0;
        if (i == 35 + HISTOGRAM_EMA_NUM_WINDOWS && firstIteration) {
          double prevPeriodHistogramSum = 0.0;
          for (int j = 35; j < 35 + HISTOGRAM_EMA_NUM_WINDOWS; j++) {
            prevPeriodHistogramSum += macdDatamergedList.get(j).histogram;
          }
          prevPeriodHistogramEMA = prevPeriodHistogramSum / HISTOGRAM_EMA_NUM_WINDOWS;
          emaHistogramApplicable = true;
        } else if (firstIteration && i > 35 + HISTOGRAM_EMA_NUM_WINDOWS || !firstIteration) {
          prevPeriodHistogramEMA = macdDatamergedList.get(i - 1).histogramEMA;
          emaHistogramApplicable = true;
        }
        if (emaHistogramApplicable) {
          double multipler2 = 2.0 / (HISTOGRAM_EMA_NUM_WINDOWS + 1);
          macdDatamergedList.get(i).histogramEMA = (1 - multipler2) * prevPeriodHistogramEMA + multipler2
              * macdDatamergedList.get(i).histogram;
          double comp = macdDatamergedList.get(i).histogram - macdDatamergedList.get(i).histogramEMA;
          if (comp == 0.0) {
            macdDatamergedList.get(i).histogramTrendType = HistogramTrendType.PLATEAUED;
          } else if (comp < 0.0) {
            macdDatamergedList.get(i).histogramTrendType = HistogramTrendType.DECELERATING;
          } else {
            macdDatamergedList.get(i).histogramTrendType = HistogramTrendType.ACCELERATING;
          }
        }
      }
      //logger.info("Inserting " + macdDatamergedList.get(i));
      if (!macdDataDao.insert(macdDatamergedList.get(i))) {
        logger.error("Failed inserting macd data: " + macdDatamergedList.get(i));
      }
    }
    return false;
  }

  private List<MACDData> convertToMACDData(List<List<String>> candlesticks, String coinPair, TimeFrame timeFrame) {
    List<MACDData> macdDataList = new ArrayList(candlesticks.size());
    for (List<String> candlestick: candlesticks) {
      MACDData macdData = new MACDData();
      macdData.coinPair = coinPair;
      macdData.timeFrame = timeFrame;
      macdData.time = new Date(Long.parseLong(candlestick.get(CANDLESTICK_INDEX_START_TIME)) * 1000);
      macdData.candleClosingPrice = Double.parseDouble(candlestick.get(CANDLESTICK_INDEX_CLOSING_PRICE));
      macdDataList.add(macdData);
    }
    return macdDataList;
  }

  private Pair<Date, Date> getStartDateAndEndDateForCandlesticksRequest(List<MACDData> macdRows, TimeFrame timeFrame) throws ParseException {
    Date candlestickStartTimeToQueryFrom;
    if (macdRows.isEmpty()) {
      candlestickStartTimeToQueryFrom = START_TIME;
    } else {
      int numRows = macdRows.size();
      Date lastProcessedCandlestickTime = macdRows.get(numRows - 1).time;
      switch (timeFrame) {
        case FIFTEEN_MINUTES:
          candlestickStartTimeToQueryFrom = DateUtils.addMinutes(lastProcessedCandlestickTime, 15);
          break;
        case HOUR:
          candlestickStartTimeToQueryFrom = DateUtils.addHours(lastProcessedCandlestickTime, 1);
          break;
        case FOUR_HOURS:
          candlestickStartTimeToQueryFrom = DateUtils.addHours(lastProcessedCandlestickTime, 4);
          break;
        case DAY:
        default:
          candlestickStartTimeToQueryFrom = DateUtils.addDays(lastProcessedCandlestickTime, 1);
      }
    }
    Date currentCandlestickStartTime = getCandlestickStart(new Date(clock.millis()), timeFrame);
    // All completed candlesticks have been fetched already. = in below condition should suffice, > is for defensive.
    if (candlestickStartTimeToQueryFrom.equals(currentCandlestickStartTime)
        || candlestickStartTimeToQueryFrom.after(currentCandlestickStartTime)) {
      return null;
    }

    Date lastCandlestickStartTimeToQuery;
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        lastCandlestickStartTimeToQuery = DateUtils.addMinutes(candlestickStartTimeToQueryFrom, 15 * NUM_CANDLESTICKS_MINUS_ONE);
        break;
      case HOUR:
        lastCandlestickStartTimeToQuery = DateUtils.addHours(candlestickStartTimeToQueryFrom, NUM_CANDLESTICKS_MINUS_ONE);
        break;
      case FOUR_HOURS:
        lastCandlestickStartTimeToQuery = DateUtils.addHours(candlestickStartTimeToQueryFrom, 4 * NUM_CANDLESTICKS_MINUS_ONE);
        break;
      case DAY:
      default:
        lastCandlestickStartTimeToQuery = DateUtils.addDays(candlestickStartTimeToQueryFrom, NUM_CANDLESTICKS_MINUS_ONE);
    }

    // lastCandlestickStartTimeToQuery should not cross the current incomplete candlestick, if it does, set it to the
    // last (realtime) candlestick.
    if (lastCandlestickStartTimeToQuery.equals(currentCandlestickStartTime)
        || lastCandlestickStartTimeToQuery.after(currentCandlestickStartTime)) {
      lastCandlestickStartTimeToQuery = getPreviousCandlestickStartTime(currentCandlestickStartTime, timeFrame);
    }
    return Pair.of(candlestickStartTimeToQueryFrom, lastCandlestickStartTimeToQuery);
  }

  private Date getPreviousCandlestickStartTime(Date candlestickStartTime, TimeFrame timeFrame) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return DateUtils.addMinutes(candlestickStartTime, -15);
      case HOUR:
        return DateUtils.addHours(candlestickStartTime, -1);
      case FOUR_HOURS:
        return DateUtils.addHours(candlestickStartTime, -4);
      case DAY:
      default:
        return DateUtils.addDays(candlestickStartTime, -1);
    }
  }

  private boolean isIncompleteCandlestick(long candlestickStartTime, TimeFrame timeFrame) {
    Date candlestickStart = new Date(candlestickStartTime);
    Date nextCandlestickStartTime;
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        nextCandlestickStartTime = DateUtils.addMinutes(candlestickStart, 15);
        break;
      case HOUR:
        nextCandlestickStartTime = DateUtils.addHours(candlestickStart, 1);
        break;
      case FOUR_HOURS:
        nextCandlestickStartTime = DateUtils.addHours(candlestickStart, 4);
        break;
      case DAY:
      default:
        nextCandlestickStartTime = DateUtils.addDays(candlestickStart, 1);
    }
    return nextCandlestickStartTime.after(new Date(clock.millis()));
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

  public static void main(String[] args) {
    SpringApplication.run(MACDCalculation.class, args);
  }

  @Override
  public void run(String... args) throws MessagingException {
    if (isTest) {
      return;
    }
    try {
      List<CurrencyPair> marginPairs = spotApi.listCurrencyPairs();
      List<Thread> runners = new ArrayList<>();
      for (TimeFrame timeFrame: TimeFrame.values()) {
        runners.addAll(startThreads(marginPairs, timeFrame));
      }
      runners.get(0).join();
    } catch (Exception ex) {
      mailer.sendEmail("Main thread exception.", ex.getMessage());
    }
  }

  private List<Thread> startThreads(List<CurrencyPair> marginPairs, TimeFrame timeFrame) {
    List<Thread> runners = new ArrayList<>();
    for (int i = 0; i < NUM_SHARDS; i++) {
      Thread runner = new Thread(new PartitionRunner(this, timeFrame, marginPairs, i));
      runners.add(runner);
      runner.start();
    }
    return runners;
  }

  public void setMockMailer(Mailer mockMailer) {
    this.mailer = mockMailer;
  }
}

class PartitionRunner implements Runnable {
  private final MACDCalculation macdCalculation;
  private final int shard;
  private final List<CurrencyPair> marginPairs;
  private final TimeFrame timeFrame;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final Mailer mailer = new Mailer();

  PartitionRunner(MACDCalculation macdCalculation, TimeFrame timeFrame, List<CurrencyPair> marginPairs, int shard) {
    this.macdCalculation = macdCalculation;
    this.marginPairs = marginPairs;
    this.shard = shard;
    this.timeFrame = timeFrame;
  }

  @Override
  public void run() {
    try {
      macdCalculation.fillMACDDataPartitioned(timeFrame, shard, marginPairs);
    } catch (Exception e) {
      logger.error("Paritioned run exception", e);
      try {
        mailer.sendEmail("Parittioned run exception", e.getMessage());
      } catch (MessagingException ex) {
        logger.error("Emiling failed", ex);
      }
    }
  }
}