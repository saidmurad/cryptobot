package com.binance.bot.database;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.MarginAccount;
import com.binance.api.client.domain.account.MarginAssetBalance;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.Util;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
import com.binance.bot.tradesignals.*;
import com.binance.bot.trading.VolumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

@Repository
public class ChartPatternSignalDaoImpl {
  @Autowired
  JdbcTemplate jdbcTemplate;
  static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private static final SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy");
  private static final SimpleDateFormat monthFormat = new SimpleDateFormat("MM");
  private static final SimpleDateFormat dayFormat = new SimpleDateFormat("dd");
  private static final SimpleDateFormat hourFormat = new SimpleDateFormat("HH");
  private static final SimpleDateFormat minuteFormat = new SimpleDateFormat("mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final BinanceApiMarginRestClient binanceApiMarginRestClient;
  private final BookTickerPrices bookTickerPrices;

  @Autowired
  ChartPatternSignalDaoImpl(BinanceApiClientFactory binanceApiClientFactory, BookTickerPrices bookTickerPrices) {
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
    this.binanceApiMarginRestClient = binanceApiClientFactory.newMarginRestClient();
    this.bookTickerPrices = bookTickerPrices;
    yearFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    monthFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    dayFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    hourFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    minuteFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("pragma journal_mode=WAL");
    }
  }

  void setDataSource(DataSource dataSource) {
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("pragma journal_mode=WAL");
  }

  public synchronized boolean insertChartPatternSignal(ChartPatternSignal chartPatternSignal, VolumeProfile volProfile) {
    String sql = "insert into ChartPatternSignal(CoinPair, TimeFrame, TradeType, Pattern, Attempt, PriceAtTimeOfSignal, PriceAtTimeOfSignalReal," +
        "PriceRelatedToPattern, TimeOfSignal, TimeOfInsertion, IsInsertedLate, NumTimesMissingInInput, " +
        "VolumeAtSignalCandlestick, VolumeAverage, IsVolumeSurge, PriceTarget, PriceTargetTime, ProfitPotentialPercent, " +
        "IsSignalOn, TenCandlestickTime, FailedToGetPriceAtTenCandlestickTime)" +
        "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    Object params[] = new Object[]{chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(),
        chartPatternSignal.tradeType().name(),
        chartPatternSignal.pattern(),
        chartPatternSignal.attempt(),
        chartPatternSignal.priceAtTimeOfSignal(),
        chartPatternSignal.priceAtTimeOfSignalReal(),
        chartPatternSignal.priceRelatedToPattern(),
        df.format(chartPatternSignal.timeOfSignal()),
        df.format(chartPatternSignal.timeOfInsertion()),
        chartPatternSignal.isInsertedLate(),
        chartPatternSignal.numTimesMissingInInput(),
        volProfile != null? (long) Double.parseDouble(volProfile.currentCandlestick().getVolume()) : null,
        volProfile != null? volProfile.avgVol() : null,
        volProfile != null? volProfile.isVolSurged()? 1:0 : 0,
        chartPatternSignal.priceTarget(),
        df.format(chartPatternSignal.priceTargetTime()),
        chartPatternSignal.profitPotentialPercent(),
        chartPatternSignal.isSignalOn(),
        chartPatternSignal.tenCandlestickTime() == null? null : df.format(chartPatternSignal.tenCandlestickTime()),
        chartPatternSignal.failedToGetPriceAtTenCandlestickTime()? 1:0
    };

    //logger.info("Inserting into db chart pattern signal: \n" + chartPatternSignal);
    return jdbcTemplate.update(sql, params) > 0;
  }

  public synchronized boolean invalidateChartPatternSignal(ChartPatternSignal chartPatternSignal, double priceAtTimeOfInvalidation,
                                              ReasonForSignalInvalidation reasonForSignalInvalidation) {
    String sql = "update ChartPatternSignal set IsSignalOn=0, TimeOfSignalInvalidation=?, " +
        "PriceAtTimeOfSignalInvalidation=?, ProfitPercentAtTimeOfSignalInvalidation=?, " +
        "ReasonForSignalInvalidation=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    boolean ret1 = jdbcTemplate.update(sql, df.format(new Date()), Double.toString(priceAtTimeOfInvalidation),
        Util.getProfitPercentAtWithPrice(chartPatternSignal, priceAtTimeOfInvalidation),
        reasonForSignalInvalidation.name(), chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt()) == 1;
    String sql2 = "insert into ChartPatternSignalInvalidationEvents(CoinPair, TimeFrame, TradeType, Pattern, " +
        "TimeOfSignal, InvalidationEventTime, Event)" +
        "values(?, ?, ?, ?, ?, ?, ?)";

    boolean ret2 = jdbcTemplate.update(sql2, chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), "Disappeared", df.format(new Date())) == 1;
    return ret1 && ret2;
  }

  // TODO: Think of a way how we can trim the data considered.
  public List<ChartPatternSignal> getAllChartPatterns(TimeFrame timeFrame) {
    return jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + timeFrame.name() + "'", new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getChartPatternsWithActiveTradePositions(TimeFrame timeFrame, TradeType tradeType) {
    String sql = String.format("select * from ChartPatternSignal where TimeFrame='%s' and " +
        "TradeType='%s' " +
        "and IsPositionExited=0 and EntryExecutedQty>0", timeFrame.name(), tradeType.name());
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getAllChartPatternsWithActiveTradePositions() {
    String sql = String.format("select * from ChartPatternSignal where" +
        " IsPositionExited=0 and EntryOrderId>0");
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getAllChartPatterns() {
    List<ChartPatternSignal> allPatterns = new ArrayList<>();
    allPatterns.addAll(jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + TimeFrame.FIFTEEN_MINUTES.name() + "'", new ChartPatternSignalMapper()));
    allPatterns.addAll(jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + TimeFrame.HOUR.name() + "'", new ChartPatternSignalMapper()));
    allPatterns.addAll(jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + TimeFrame.FOUR_HOURS.name() + "'", new ChartPatternSignalMapper()));
    allPatterns.addAll(jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + TimeFrame.DAY.name() + "'", new ChartPatternSignalMapper()));
    return allPatterns;
  }

  public List<ChartPatternSignal> getAllChartPatternsNeedingMaxLossCalculated() {
    String sql = String.format("select * from ChartPatternSignal where MaxLoss is null " +
        "and datetime(PriceTargetTime) < datetime('%s') " +
            "order by datetime(TimeOfSignal), TimeFrame",
        df.format(new Date()));
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  // intented for test use.
  public ChartPatternSignal getChartPattern(ChartPatternSignal chartPatternSignal) {
    String sql = "select * from ChartPatternSignal where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) and Attempt=?";
    return jdbcTemplate.queryForObject(sql, new Object[]{chartPatternSignal.coinPair(), chartPatternSignal.timeFrame().name(),
        chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt()}, new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getChatPatternSignalsThatJustReachedTenCandleStickTime() {
    String sql = "select * from ChartPatternSignal \n" +
        "    where (PriceAtTenCandlestickTime is null or PriceAtTenCandlestickTime = 0)\n" +
        "    and (FailedToGetPriceAtTenCandlestickTime is null or FailedToGetPriceAtTenCandlestickTime = 0)\n" +
        "    and ((TimeFrame = 'FIFTEEN_MINUTES' and DATETIME(TimeOfSignal, '+150 minute') <= DATETIME('now') and DATETIME(TimeOfSignal, '+150 minute') >= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'HOUR' and DATETIME(TimeOfSignal, '+10 hour') <= DATETIME('now') and DATETIME(TimeOfSignal, '+10 hour') >= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'FOUR_HOURS' and DATETIME(TimeOfSignal, '+40 hour') <= DATETIME('now') and DATETIME(TimeOfSignal, '+40 hour') >= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'DAY' and DATETIME(TimeOfSignal, '+10 day') <= DATETIME('now') and DATETIME(TimeOfSignal, '+10 day') >= DATETIME('now', '-10 minute')))";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getChatPatternSignalsThatJustReachedTargetTime() {
    String sql = "select * from ChartPatternSignal \n" +
        "    where (PriceAtSignalTargetTime is null or PriceAtSignalTargetTime = 0)\n" +
        "    and (FailedToGetPriceAtSignalTargetTime is null or FailedToGetPriceAtSignalTargetTime = 0)\n" +
        "    and DATETIME(PriceTargetTime) <= DATETIME('now') " +
        "and DATETIME(PriceTargetTime) >= DATETIME('now', '-10 minute')";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getChatPatternSignalsThatLongSinceReachedTenCandleStickTime() {
    String sql = "select * from ChartPatternSignal \n" +
        "    where (PriceAtTenCandlestickTime is null or PriceAtTenCandlestickTime = 0)\n" +
        "    and (FailedToGetPriceAtTenCandlestickTime is null or FailedToGetPriceAtTenCandlestickTime = 0)\n" +
        "    and ((TimeFrame = 'FIFTEEN_MINUTES' and DATETIME(TimeOfSignal, '+150 minute') <= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'HOUR' and DATETIME(TimeOfSignal, '+10 hour') <= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'FOUR_HOURS' and DATETIME(TimeOfSignal, '+40 hour') <= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'DAY' and DATETIME(TimeOfSignal, '+10 day') <= DATETIME('now', '-10 minute')))";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getChatPatternSignalsThatLongSinceReachedTargetTime() {
    String sql = "select * from ChartPatternSignal \n" +
        "    where (PriceAtSignalTargetTime is null or PriceAtSignalTargetTime = 0)\n" +
        "    and (FailedToGetPriceAtSignalTargetTime is null or FailedToGetPriceAtSignalTargetTime = 0)\n" +
        "    and DATETIME(PriceTargetTime) <= DATETIME('now', '-10 minute')";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public synchronized boolean setTenCandleStickTimePrice(ChartPatternSignal chartPatternSignal,
                                            double tenCandleStickTimePrice,
                                            double tenCandleStickTimeProfitPercent) {
    String sql = "update ChartPatternSignal set PriceAtTenCandlestickTime=?, ProfitPercentAtTenCandlestickTime=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    return jdbcTemplate.update(sql, tenCandleStickTimePrice, tenCandleStickTimeProfitPercent, chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt()) == 1;
  }

  public synchronized boolean setSignalTargetTimePrice(ChartPatternSignal chartPatternSignal,
                                            double price,
                                            double profitPercent) {
    String sql = "update ChartPatternSignal set PriceAtSignalTargetTime=?, ProfitPercentAtSignalTargetTime=?, IsSignalOn=0" +
        " where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    return jdbcTemplate.update(sql, price, profitPercent, chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt()) == 1;
  }

  public synchronized boolean incrementNumTimesMissingInInput(List<ChartPatternSignal> chartPatternsMissingInInput) {
    String sql = "update ChartPatternSignal set NumTimesMissingInInput = NumTimesMissingInInput + 1 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) and " +
        "Attempt=?";
    boolean retVal = true;
    for (ChartPatternSignal chartPatternSignal: chartPatternsMissingInInput) {
      int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt());
      if (ret == 1) {
        //logger.info("Updated chart pattern signal missing count to " + (chartPatternSignal.numTimesMissingInInput() + 1) + " for chart pattern signal: " + chartPatternSignal.toString());
      } else {
        logger.error("Failed to increment numTimesMissingInInput for chart pattern signal: " + chartPatternSignal.toString());
      }
      retVal &= (ret == 1);
    }
    return retVal;
  }

  public synchronized void resetNumTimesMissingInInput(List<ChartPatternSignal> chartPatternSignalsReappearedInTime) {
    String sql = "update ChartPatternSignal set NumTimesMissingInInput = 0, IsSignalOn=1 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    String sql2 = "insert into ChartPatternSignalInvalidationEvents(CoinPair, TimeFrame, TradeType, Pattern, TimeOfSignal, InvalidationEventTime, Event)" +
        "values(?, ?, ?, ?, ?, ?, ?)";
    for (ChartPatternSignal chartPatternSignal: chartPatternSignalsReappearedInTime) {
      int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt());
      if (ret == 1) {
        logger.info("Updated chart pattern signal missing count to 0 for chart pattern signal: " + chartPatternSignal.toString());
      } else {
        logger.error("Failed to make numTimesMissingInInput 0 for chart pattern signal: " + chartPatternSignal.toString());
      }
      ret = jdbcTemplate.update(sql2, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()), "Reappeared", df.format(new Date()));
      if (ret != 1) {
        logger.error("Failed to update auxillary event table for chart pattern " + chartPatternSignal.toString());
      }
    }
  }

  public synchronized void failedToGetPriceAtTenCandlestickTime(ChartPatternSignal chartPatternSignal) {
    String sql = "update ChartPatternSignal set FailedToGetPriceAtTenCandlestickTime = 1 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt());
    if (ret == 1) {
      logger.info("Updated FailedToGetPriceAtTenCandlestickTime to 1 for chart pattern signal: " + chartPatternSignal.toString());
    } else {
      logger.error("Failed to update FailedToGetPriceAtTenCandlestickTime to 1 for chart pattern signal: " + chartPatternSignal.toString());
    }
  }

  public synchronized void failedToGetPriceAtSignalTargetTime(ChartPatternSignal chartPatternSignal) {
    String sql = "update ChartPatternSignal set FailedToGetPriceAtSignalTargetTime = 1, IsSignalOn=0 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt());
    if (ret == 1) {
      logger.info("Updated FailedToGetPriceAtSignalTargetTime to 1 for chart pattern signal: " + chartPatternSignal.toString());
    } else {
      logger.error("Failed to update FailedToGetPriceAtSignalTargetTime to 1 for chart pattern signal: " + chartPatternSignal.toString());
    }
  }

  public List<ChartPatternSignal> getChartPatternSignalsToInvalidate() {
    String sql = "select * from ChartPatternSignal where IsSignalOn=1 and NumTimesMissingInInput >= 1";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getChartPatternSignalsToPlaceTrade(
      TimeFrame timeFrame, TradeType tradeType, boolean useAltfinsInvalidations) {
    if (useAltfinsInvalidations) {
      return getChartPatternSignalsToPlaceTradeUsingAltfinsInvalidations(timeFrame, tradeType);
    }
    return getChartPatternSignalsToPlaceTradeNotUsingAltfinsInvalidations(timeFrame, tradeType);
  }

  private List<ChartPatternSignal> getChartPatternSignalsToPlaceTradeUsingAltfinsInvalidations(
      TimeFrame timeFrame, TradeType tradeType) {
    String sql = String.format("select * from ChartPatternSignal where EntryOrderId is null and " +
        "timeFrame='%s' and tradeType='%s' and DATETIME(TimeOfSignal) >= DATETIME('now', '-120 minutes')", timeFrame.name(), tradeType.name());
    List<ChartPatternSignal> chartPatternSignals = jdbcTemplate.query(sql, new ChartPatternSignalMapper());
    Map<ChartPatternSignal, Integer> highestAttemptMap = new HashMap<>();
    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      Integer attempt = highestAttemptMap.get(chartPatternSignal);
      Integer updatedAttempt = chartPatternSignal.attempt();
      if (attempt == null || attempt < updatedAttempt) {
        highestAttemptMap.put(chartPatternSignal, updatedAttempt);
      }
    }
    return chartPatternSignals.stream().filter(chartPatternSignal ->
        chartPatternSignal.attempt() == highestAttemptMap.get(chartPatternSignal))
        .collect(Collectors.toList());
  }

  private List<ChartPatternSignal> getChartPatternSignalsToPlaceTradeNotUsingAltfinsInvalidations(
      TimeFrame timeFrame, TradeType tradeType) {
    String sql = String.format("select * from ChartPatternSignal where EntryOrderId is null and " +
        "timeFrame='%s' and tradeType='%s' and Attempt=1 and DATETIME(TimeOfSignal) >= DATETIME('now', '-120 minutes')", timeFrame.name(), tradeType.name());
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public synchronized boolean setEntryOrder(ChartPatternSignal chartPatternSignal, ChartPatternSignal.Order entryOrder) {
    String sql = "update ChartPatternSignal set EntryOrderId=?, EntryExecutedQty=?, EntryAvgPrice=?, EntryOrderStatus=?, " +
        "IsPositionExited=0 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    int ret = jdbcTemplate.update(sql, entryOrder.orderId(), entryOrder.executedQty(), entryOrder.avgPrice(),
        entryOrder.status().name(), chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt());
    if (ret == 1) {
      logger.info(String.format("Updated chart pattern sgnal \n%s\nwith entry order id %d.", chartPatternSignal.toString(), entryOrder.orderId()));
    } else {
      logger.error(String.format("Failed to update chart pattern sgnal \n%s\nwith entry order id %d.", chartPatternSignal.toString(), entryOrder.orderId()));
    }
    return ret == 1;
  }

  // Called when signal is invalidated or target time has elapsed.
  public synchronized boolean setExitMarketOrder(ChartPatternSignal chartPatternSignal, ChartPatternSignal.Order exitMarketOrder,
                                    TradeExitType tradeExitType) {
    Pair<Double, Double> realizedUnRealized = getRealizedUnRealized(
        chartPatternSignal, exitMarketOrder.executedQty(), exitMarketOrder.avgPrice(), tradeExitType, exitMarketOrder.status());
    double realizedPercent = realizedUnRealized.getFirst() /
        (chartPatternSignal.entryOrder().executedQty() * chartPatternSignal.entryOrder().avgPrice()) * 100;
    double unRealizedPercent = realizedUnRealized.getSecond() /
        (chartPatternSignal.entryOrder().executedQty() * chartPatternSignal.entryOrder().avgPrice()) * 100;
    String sql = "update ChartPatternSignal set ExitMarketOrderId=?, ExitMarketOrderExecutedQty=?, ExitMarketOrderAvgPrice=?, " +
        "ExitMarketOrderStatus=?, " +
        "Realized=?, RealizedPercent=?, UnRealized=?, UnRealizedPercent=?, IsPositionExited=?, IsSignalOn=?," +
        "TradeExitType=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    int ret = jdbcTemplate.update(sql, exitMarketOrder.orderId(), exitMarketOrder.executedQty(), exitMarketOrder.avgPrice(),
        exitMarketOrder.status().name(),
        realizedUnRealized.getFirst(), realizedPercent,
        realizedUnRealized.getSecond(), unRealizedPercent,
        exitMarketOrder.status() == OrderStatus.FILLED ? 1 : 0,
        exitMarketOrder.status() == OrderStatus.FILLED ? 0 : 1,
        tradeExitType.name(), chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt());
    if (ret == 1) {
      logger.info(String.format("Updated chart pattern signal \n%s\nwith exit limit market id %d.", chartPatternSignal.toString(), exitMarketOrder.orderId()));
    } else {
      logger.error(String.format("Failed to update chart pattern sgnal \n%s\nwith exit limit market id %d.", chartPatternSignal.toString(), exitMarketOrder.orderId()));
    }
    return ret == 1;
  }

  public synchronized boolean setExitStopLimitOrder(ChartPatternSignal chartPatternSignal, ChartPatternSignal.Order exitMarketOrder) {
    // Assumes that the pattern is already uptodate with the DB value, so doesn't query for it.
    String sql = "update ChartPatternSignal set ExitStopLossOrderId=?, ExitStopLossOrderStatus=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    int ret = jdbcTemplate.update(sql, exitMarketOrder.orderId(),
        exitMarketOrder.status().name(),
        chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt());
    if (ret == 1) {
      logger.info(String.format("Updated chart pattern signal \n%s\nwith exit market order id %d.", chartPatternSignal.toString(), exitMarketOrder.orderId()));
    } else {
      logger.error(String.format("Failed to update chart pattern signal \n%s\nwith exit marketorder id %d.", chartPatternSignal.toString(), exitMarketOrder.orderId()));
    }
    return ret == 1;
  }

  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  public synchronized boolean updateExitStopLimitOrder(ChartPatternSignal chartPatternSignal,
                                          ChartPatternSignal.Order exitStopLimitOrderStatus) throws ParseException {
    double executedQty = exitStopLimitOrderStatus.executedQty();
    double executedPrice = exitStopLimitOrderStatus.avgPrice();
    Pair<Double, Double> realizedUnRealized = getRealizedUnRealized(
        chartPatternSignal,
        executedQty,
        exitStopLimitOrderStatus.avgPrice(),
        TradeExitType.STOP_LOSS,
        exitStopLimitOrderStatus.status());
    double realizedPercent = realizedUnRealized.getFirst() /
        (chartPatternSignal.entryOrder().executedQty() * chartPatternSignal.entryOrder().avgPrice()) * 100;
    double unRealizedPercent = realizedUnRealized.getSecond() /
        (chartPatternSignal.entryOrder().executedQty() * chartPatternSignal.entryOrder().avgPrice()) * 100;
    String sql = "update ChartPatternSignal set ExitStopLossOrderId=?, ExitStopLossOrderExecutedQty=?," +
        "ExitStopLossOrderAvgPrice=?," +
        "ExitStopLossOrderStatus=?," +
        "Realized=?, RealizedPercent=?, Unrealized=?, UnRealizedPercent=?," +
        "IsPositionExited=?, IsSignalOn=?, TradeExitType='STOP_LOSS' where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    int ret = jdbcTemplate.update(sql, exitStopLimitOrderStatus.orderId(),
        exitStopLimitOrderStatus.executedQty(),
        executedPrice,
        exitStopLimitOrderStatus.status().name(),
        realizedUnRealized.getFirst(), realizedPercent, realizedUnRealized.getSecond(), unRealizedPercent,
        exitStopLimitOrderStatus.status() == OrderStatus.FILLED,
        exitStopLimitOrderStatus.status() == OrderStatus.FILLED ? 0 : 1,
        chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(),
        chartPatternSignal.tradeType().name(),
        chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()),
        chartPatternSignal.attempt());
    if (ret == 1) {
      logger.info(String.format("Updated chart pattern signal \n%s\nwith exit stop limit order id %d.", chartPatternSignal.toString(), exitStopLimitOrderStatus.orderId()));
    } else {
      logger.error(String.format("Failed to update chart pattern signal \n%s\nwith exit stop limit id %d.", chartPatternSignal.toString(), exitStopLimitOrderStatus.orderId()));
    }
    return ret == 1;
  }

  private Pair<Double, Double> getRealizedUnRealized(ChartPatternSignal chartPatternSignal, double exitQty,
                                                     double exitPrice, TradeExitType tradeExitType, OrderStatus orderStatus) {
    double realized = exitQty *
        (chartPatternSignal.tradeType() == TradeType.BUY ? 1 : -1) *
        (exitPrice - chartPatternSignal.entryOrder().avgPrice());
    // Since only Stop loss order I'm placing as a stop loss with limit, it could have executed partially.
    // If this order is also a stop lomit order, the total order executed is already fully present in exitQty.
    if (chartPatternSignal.realized() != null) {
      if (tradeExitType != TradeExitType.STOP_LOSS) {
        realized += chartPatternSignal.realized();
      }
    }
    double unRealized = 0.0;
    // For market orders I don't really expect or handle partial fills.
    if (tradeExitType == TradeExitType.STOP_LOSS && orderStatus == OrderStatus.PARTIALLY_FILLED) {
      unRealized = (chartPatternSignal.entryOrder().executedQty() - chartPatternSignal.exitStopLimitOrder().executedQty() - exitQty) *
          (chartPatternSignal.tradeType() == TradeType.BUY ? 1 : -1) *
          (exitPrice - chartPatternSignal.entryOrder().avgPrice());
    }
    return Pair.of(realized, unRealized);
  }

  // As the column i`s a late addition, this is for backfilling the column.
  public synchronized boolean setTenCandleSticktime(ChartPatternSignal chartPatternSignal) {
    Date tenCandlestickTime = new Date(chartPatternSignal.timeOfSignal().getTime() + getTenCandleStickTimeIncrementMillis(chartPatternSignal));
    boolean shouldEraseSuperflousTenCandlestickPrice = tenCandlestickTime.after(chartPatternSignal.priceTargetTime());
    if (shouldEraseSuperflousTenCandlestickPrice) {
      tenCandlestickTime = chartPatternSignal.priceTargetTime();
    }
    String updateSql = "update ChartPatternSignal set TenCandlestickTime=?, PriceAtTenCandlestickTime=? " +
        "where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    boolean ret = jdbcTemplate.update(updateSql, df.format(tenCandlestickTime),
        shouldEraseSuperflousTenCandlestickPrice? null : chartPatternSignal.priceAtTenCandlestickTime(),
        chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())) == 1;
    if (!ret) {
      logger.error(String.format("Failed to update ten candlestick time for chart pattern signal \n%s.", chartPatternSignal.toString()));
    }
    return ret;
  }

  public synchronized boolean updateMaxLossAndTargetMetValues(ChartPatternSignal chartPatternSignal) {
    String updateSql = "update ChartPatternSignal set MaxLoss=?, MaxLossPercent=?, MaxLossTime=?, IsPriceTargetMet=?, " +
        "PriceTargetMetTime=? " +
        "where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) and " +
        "Attempt=?";
    int ret = jdbcTemplate.update(updateSql, chartPatternSignal.maxLoss(), chartPatternSignal.maxLossPercent(),
        chartPatternSignal.maxLossTime() != null ? df.format(chartPatternSignal.maxLossTime()) : null,
        chartPatternSignal.isPriceTargetMet(),
        chartPatternSignal.priceTargetMetTime() != null ? df.format(chartPatternSignal.priceTargetMetTime()) : null,
        chartPatternSignal.coinPair(), chartPatternSignal.timeFrame(),
        chartPatternSignal.tradeType(), chartPatternSignal.pattern(), df.format(chartPatternSignal.timeOfSignal()),
        chartPatternSignal.attempt());
    if (ret != 1) {
      logger.error(String.format("Failed to update max loss and target met values for chart pattern signal \n%s.",
          chartPatternSignal));
    } else {
      /*logger.info(String.format("Updated max loss and target met values for chart pattern signal:%s\n." +
          "Updated values - maxLoss=%f, maxLossPercent=%f, IsPriceTargetMet=%s", chartPatternSignal,
          chartPatternSignal.maxLoss(), chartPatternSignal.maxLossPercent(),
          chartPatternSignal.isPriceTargetMet() != null ?
              chartPatternSignal.isPriceTargetMet() ? "True" : "False"
          : null));*/
    }
    return ret == 1;
  }

  public synchronized void cancelStopLimitOrder(ChartPatternSignal chartPatternSignal) {
    String updateSql = String.format("update ChartPatternSignal set ExitStopLossOrderStatus='%s' " +
        "where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)",
        OrderStatus.CANCELED);
    boolean ret = jdbcTemplate.update(updateSql,
        chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())) == 1;
    if (ret) {
      logger.info("Updated Stop Limit Order status to Canceled for chart pattern signal: %s.", chartPatternSignal);
    } else {
      logger.error("Failed to update Stop Limit Order status to Canceled for chart pattern signal: %s", chartPatternSignal);
    }
  }

  public synchronized void insertOverdoneTradeType(Date date, TimeFrame timeFrame, TradeType tradeTypeOverdone) {
    boolean rowAlreadyExists = doesBitcoinPriceMonitoringRowAlreadyExist(date, timeFrame);
    int ret;
    if (rowAlreadyExists) {
      ret = jdbcTemplate.update(String.format("update BitcoinPriceMonitoring set tradeTypeOverdone='%s' " +
          "where Time='%s' and TimeFrame='%s'", tradeTypeOverdone.name(), df.format(date), timeFrame.name()));
    } else {
      ret = jdbcTemplate.update(String.format("insert into BitcoinPriceMonitoring(Time, TimeFrame, TradeTypeOverdone) " +
          "values('%s', '%s', '%s')", df.format(date), timeFrame.name(), tradeTypeOverdone.name()));
    }
    String msg = String.format("%s %s BitcoinPriceMonitoring for date '%s', timeframe '%s' and " +
            "tradeTypeOverdone '%s'",
        ret == 1? "Done " : "Failed to ",
        rowAlreadyExists? "update" : "insert", df.format(date), timeFrame.name(),
        tradeTypeOverdone.name());
    if (ret == 0) {
      logger.error(msg);
    } else {
      logger.info(msg);
    }
  }

  private boolean doesBitcoinPriceMonitoringRowAlreadyExist(Date date, TimeFrame timeFrame) {
    Integer count = jdbcTemplate.queryForObject(String.format("select count(0) as count from BitcoinPriceMonitoring " +
        "where Time='%s' and " +
        "TimeFrame='%s'", df.format(date), timeFrame.name()), new Object[]{}, (rs, rowNum) -> {
      return rs.getInt("count");
    });
    return count == 1;
  }

  public synchronized void insertBitcoinPrice(TimeFrame timeFrame, Long openTime, Double open, Double close) {
    Date openDate = new Date(openTime);
    boolean rowAlreadyExists = doesBitcoinPriceMonitoringRowAlreadyExist(openDate, timeFrame);
    int ret;
    if (!rowAlreadyExists) {
      ret = jdbcTemplate.update(String.format("insert into BitcoinPriceMonitoring(" +
          "Time, TimeFrame, CandleOpenPrice, CandleClosePrice) " +
          "values('%s', '%s', %f, %f)", df.format(openDate), timeFrame.name(), open, close));
      /*logger.info(String.format("%s %s BitcoinPriceMonitoring for date '%s', timeframe '%s'",
          ret == 1? "Done " : "Failed to ",
          rowAlreadyExists? "update" : "insert", df.format(openDate), timeFrame.name()));*/
    } {
      ret = jdbcTemplate.update(String.format("update BitcoinPriceMonitoring " +
          "set CandleOpenPrice=%f, CandleClosePrice=%f " +
          "where Time='%s' and TimeFrame='%s'", open, close, df.format(openDate), timeFrame.name()));
      /*logger.info(String.format("Updated already existing row in BitcoinPriceMonitoringRow for date '%s' and " +
          "timeFrame '%s.", df.format(openDate), timeFrame.name()));*/
    }
    String msg = String.format("%s %s BitcoinPriceMonitoring for date '%s', timeframe '%s' with " +
            "open %f, close %f",
        ret == 1? "Done " : "Failed to ",
        rowAlreadyExists? "update" : "insert", df.format(openDate), timeFrame.name(),
        open, close);
    if (ret == 0) {
      logger.error(msg);
    } else {
  //    logger.info(msg);
    }
  }

  public TradeType getOverdoneTradeType(Date time, TimeFrame timeFrame) throws ParseException {
    Date candlestickStart = getCandlestickStart(time, timeFrame);
    String sql = String.format("select TradeTypeOverdone from BitcoinPriceMonitoring " +
        "where Time='%s' and TimeFrame='%s'", df.format(candlestickStart), timeFrame.name());
    String tradeTypeOverdoneStr = jdbcTemplate.queryForObject(sql, new Object[]{},
        (rs, numRows) -> rs.getString("TradeTypeOverdone"));
    return TradeType.valueOf(tradeTypeOverdoneStr);
  }

  synchronized public void setEntryEligibleBasedOnMACDSignalCrossOver(
      ChartPatternSignal chartPatternSignal, boolean isEligible) {
    String updateSql = "update ChartPatternSignal set EntryEligibleBasedOnMACDSignalCrossOver=? " +
        "where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    boolean ret = jdbcTemplate.update(updateSql, isEligible? 1:0,
        chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt()) == 1;
    if (!ret) {
      logger.error("Failed updating EntryEligible for chart pattern signal " + chartPatternSignal);
    }
  }

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

  public synchronized void writeAccountBalanceToDB() throws BinanceApiException, ParseException {
    MarginAccount account = binanceApiMarginRestClient.getAccount();
    double btcPrice = bookTickerPrices.getBookTicker("BTCUSDT").bestAsk();
    int principal = getPrincipal();
    int totalAssetValueInUSDT = (int) (numberFormat.parse(account.getTotalAssetOfBtc()).doubleValue() * btcPrice);
    int netAssetValueInUSDT = (int) (numberFormat.parse(account.getTotalNetAssetOfBtc()).doubleValue() * btcPrice);
    double rr = (double) (netAssetValueInUSDT - principal) / principal * 100;
    int liabilityValueInUSDT = (int) (numberFormat.parse(account.getTotalLiabilityOfBtc()).doubleValue() * btcPrice);
    MarginAssetBalance usdtBalance = account.getAssetBalance("USDT");
    String sql = String.format("insert into CrossMarginAccountBalanceHistory(Time, FreeUSDT, " +
            "LockedUSDT, BorrowedUSDT, NetUSDT," +
            "TotalValue, LiabilityValue, NetValue, MarginLevel, ReturnRate) values(" +
            "'%s', %d, %d, %d, %d, %d, %d, %d, %f, %f)",
        df.format(new Date()),
        numberFormat.parse(usdtBalance.getFree()).intValue(),
        numberFormat.parse(usdtBalance.getLocked()).intValue(),
        numberFormat.parse(usdtBalance.getBorrowed()).intValue(),
        numberFormat.parse(usdtBalance.getNetAsset()).intValue(),
        totalAssetValueInUSDT,
        liabilityValueInUSDT,
        netAssetValueInUSDT,
        numberFormat.parse(account.getMarginLevel()).doubleValue(),
        rr
    );
    if (jdbcTemplate.update(sql) != 1) {
      logger.error("Failed to insert row into CrossMarginAccountBalanceHistory.");
    }
  }

  private int getPrincipal() {
    String sql = "select principal from CrossMarginAccountFundingHistory " +
        "where rowid=(select max(rowid) from CrossMarginAccountFundingHistory)";
    return jdbcTemplate.queryForObject(sql, new Object[]{}, (rs, rowNum) -> rs.getInt("principal"));
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
}
