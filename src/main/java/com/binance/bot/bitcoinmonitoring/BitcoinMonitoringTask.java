package com.binance.bot.bitcoinmonitoring;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 15 minute candles: 1.5% over 5 candlesticsk.
 * 1 hour candles: 1.5% over 3 candlesticks
 * 4 hour candles: 5.5% over 3 candlesticks.
 */
@Component
public class BitcoinMonitoringTask {

  private final BinanceApiRestClient binanceApiRestClient;
  private final ChartPatternSignalDaoImpl dao;
  private boolean firstTime = true;
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final boolean[][] tradingReco = new boolean[4][2];
  public boolean[][] getTradingReco() {
    return tradingReco;
  }
  @Value("${fifteen_minute_movement_threshold_percent}")
  double fifteenMinuteMovementThresholdPercent;
  @Value("${hourly_movement_threshold_percent}")
  double hourlyMovementThresholdPercent;
  @Value("${four_hourly_movement_threshold_percent}")
  double fourHourlyMovementThresholdPercent;

  @Autowired
  public BitcoinMonitoringTask(BinanceApiClientFactory binanceApiClientFactory,
                               ChartPatternSignalDaoImpl dao) {
    this.binanceApiRestClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
  }

  private List<Double> lastFourCandlestickPrices = new ArrayList<>();
  private Map<TimeFrame, TradeType> overdoneTradeTypes = new HashMap<>();

  public TradeType getTradeTypeOverdone(TimeFrame timeFrame) {
    return overdoneTradeTypes.get(timeFrame);
  }

  boolean isFirstTimeFifteenMinuteTimeframe = true;
  boolean isFirstTimeHourlyTimeframe = true;
  boolean isFirstTimeFourHourlyTimeframe = true;

  public void backFill() throws ParseException {
    Date startDate = df.parse("2022-01-11 00:00");
    backFill(TimeFrame.FIFTEEN_MINUTES, CandlestickInterval.FIFTEEN_MINUTES, startDate, 1.5);
    backFill(TimeFrame.HOUR, CandlestickInterval.HOURLY, startDate, 1.5);
    backFill(TimeFrame.FOUR_HOURS, CandlestickInterval.FOUR_HOURLY, startDate, 5.5);
  }

  private void backFill(TimeFrame timeFrame, CandlestickInterval candlestickTimeFrame, Date startTimeToBackfillFrom,
                        double thresholdPercent) throws ParseException {
    Date startTime = startTimeToBackfillFrom;
    boolean firstIteration = true;
    while (true) {
      List<Candlestick> candlesticks = binanceApiRestClient.getCandlestickBars(
          "BTCUSDT", candlestickTimeFrame, 10, startTime.getTime(), null);
      Candlestick lastCandlestick = candlesticks.get(candlesticks.size() - 1);
      if (lastCandlestick.getCloseTime() > System.currentTimeMillis()) {
        break;
      }
      TradeType overdoneTradeType = getOverdoneTradeType(candlesticks, thresholdPercent);
      updateBitcoinPrices(timeFrame, candlesticks, firstIteration, overdoneTradeType);
      firstIteration = false;
      startTime = new Date(lastCandlestick.getCloseTime() + 1);
    }
  }

  @Scheduled(fixedRate = 900000, initialDelayString = "${timing.initialDelay}")
  public void performFifteenMinuteTimeFrame() throws ParseException {
    List<Candlestick> candlesticks = binanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null);
    // Remove incomplete candlestick. TODO: Unit test.
    if (candlesticks.get(candlesticks.size() -1).getCloseTime() >= System.currentTimeMillis()) {
      candlesticks.remove(candlesticks.size() -1);
    }
    TradeType overdoneTradeType = getOverdoneTradeType(candlesticks, fifteenMinuteMovementThresholdPercent);
    overdoneTradeTypes.put(TimeFrame.FIFTEEN_MINUTES, overdoneTradeType);
    updateBitcoinPrices(TimeFrame.FIFTEEN_MINUTES, candlesticks, isFirstTimeFifteenMinuteTimeframe, overdoneTradeType);
    isFirstTimeFifteenMinuteTimeframe = false;
  }

  private void updateBitcoinPrices(TimeFrame timeFrame, List<Candlestick> candlesticks, boolean isFirstTime, TradeType overdoneTradeType) throws ParseException {
    int len = candlesticks.size();
    long currentCandlestickStart = candlesticks.get(len - 1).getCloseTime() + 1;
    dao.insertOverdoneTradeType(new Date(currentCandlestickStart), timeFrame, overdoneTradeType);
    if (isFirstTime) {
      // First 9 rows will normally have to insert rather than update. Handling that inside the daoImpl.
      for (int i =0; i < len -1; i++) {
        dao.insertBitcoinPrice(timeFrame, candlesticks.get(i).getOpenTime(),
            new Double(numberFormat.parse(candlesticks.get(i).getOpen()).doubleValue()),
            new Double(numberFormat.parse(candlesticks.get(i).getClose()).doubleValue()));
      }
    }
    dao.insertBitcoinPrice(timeFrame, candlesticks.get(len-1).getOpenTime(),
        new Double(numberFormat.parse(candlesticks.get(len-1).getOpen()).doubleValue()),
        new Double(numberFormat.parse(candlesticks.get(len-1).getClose()).doubleValue()));
  }

  @Scheduled(fixedRate = 3600000, initialDelayString = "${timing.initialDelay}")
  public void performHourlyTimeFrame() throws ParseException {
    List<Candlestick> candlesticks = binanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.HOURLY, 10, null, null);
    TradeType overdoneTradeType = getOverdoneTradeType(candlesticks, hourlyMovementThresholdPercent);
    overdoneTradeTypes.put(TimeFrame.HOUR,
        overdoneTradeType);
    overdoneTradeTypes.put(TimeFrame.HOUR, overdoneTradeType);
    updateBitcoinPrices(TimeFrame.HOUR, candlesticks, isFirstTimeHourlyTimeframe, overdoneTradeType);
    isFirstTimeHourlyTimeframe = false;
  }

  @Scheduled(fixedRate = 14400000, initialDelayString = "${timing.initialDelay}")
  public void performFourHourlyTimeFrame() throws ParseException {
    List<Candlestick> candlesticks = binanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FOUR_HOURLY, 10, null, null);
    TradeType overdoneTradeType = getOverdoneTradeType(candlesticks, fourHourlyMovementThresholdPercent);
    overdoneTradeTypes.put(TimeFrame.FOUR_HOURS,
        overdoneTradeType);
    overdoneTradeTypes.put(TimeFrame.FOUR_HOURS, overdoneTradeType);
    updateBitcoinPrices(TimeFrame.FOUR_HOURS, candlesticks, isFirstTimeFourHourlyTimeframe, overdoneTradeType);
    isFirstTimeFourHourlyTimeframe = false;
  }

  private TradeType getOverdoneTradeType(List<Candlestick> candlesticks, double movementThresholdPercent) throws ParseException {
    int len = candlesticks.size();
    boolean lastCandlestickGreen = isGreenCandle(candlesticks.get(len - 1));
    if (lastCandlestickGreen) {
      double lastCandlestickClose = numberFormat.parse(candlesticks.get(len - 1).getClose()).doubleValue();
      int i = len - 1;
      for (; i >= 0; i--) {
        Candlestick thisCandlestick = candlesticks.get(i);
        double thisCandlestickOpen = numberFormat.parse(thisCandlestick.getOpen()).doubleValue();
        if ((lastCandlestickClose - thisCandlestickOpen) / thisCandlestickOpen * 100 > movementThresholdPercent) {
          return TradeType.BUY;
        }
        boolean isCandlestickGreen = isGreenCandle(thisCandlestick);
        if (!isCandlestickGreen) {
          break;
        }
        lastCandlestickClose = numberFormat.parse(candlesticks.get(i).getClose()).doubleValue();
      }
      if (i == -1) {
        // Probably overbought.
        return TradeType.BUY;
      }
    } else {
      double lastCandlestickClose = numberFormat.parse(candlesticks.get(len - 1).getClose()).doubleValue();
      int i = len - 1;
      for (; i >= 0; i--) {
        Candlestick thisCandlestick = candlesticks.get(i);
        double thisCandlestickOpen = numberFormat.parse(thisCandlestick.getOpen()).doubleValue();
        if ((thisCandlestickOpen - lastCandlestickClose) / thisCandlestickOpen * 100 > movementThresholdPercent) {
          return TradeType.SELL;
        }
        boolean isCandlestickGreen = isGreenCandle(thisCandlestick);
        if (isCandlestickGreen) {
          break;
        }
        lastCandlestickClose = numberFormat.parse(candlesticks.get(i).getClose()).doubleValue();
      }
      if (i == -1) {
        // Probably overbought.
        return TradeType.SELL;
      }
    }
    return TradeType.NONE;
  }

  private boolean isGreenCandle(Candlestick candlestick) throws ParseException {
    return numberFormat.parse(candlestick.getClose()).doubleValue() >= numberFormat.parse(candlestick.getOpen()).doubleValue();
  }
}
