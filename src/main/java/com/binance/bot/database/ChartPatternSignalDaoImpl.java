package com.binance.bot.database;

import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.bot.common.Util;
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

import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

@Repository
public class ChartPatternSignalDaoImpl {
  @Autowired
  JdbcTemplate jdbcTemplate;
  final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  ChartPatternSignalDaoImpl() {
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  void setDataSource(DataSource dataSource) {
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  public boolean insertChartPatternSignal(ChartPatternSignal chartPatternSignal, VolumeProfile volProfile) {
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

    logger.info("Inserting into db chart pattern signal: \n" + chartPatternSignal);
    return jdbcTemplate.update(sql, params) > 0;
  }

  public boolean invalidateChartPatternSignal(ChartPatternSignal chartPatternSignal, double priceAtTimeOfInvalidation,
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
    String sql2 = "insert into ChartPatternSignalInvalidationEvents(CoinPair, TimeFrame, TradeType, Pattern, TimeOfSignal, InvalidationEventTime, Event)" +
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
        "    and DATETIME(PriceTargetTime) <= DATETIME('now') and DATETIME(PriceTargetTime) >= DATETIME('now', '-10 minute')";
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

  public boolean setTenCandleStickTimePrice(ChartPatternSignal chartPatternSignal,
                                            double tenCandleStickTimePrice,
                                            double tenCandleStickTimeProfitPercent) {
    String sql = "update ChartPatternSignal set PriceAtTenCandlestickTime=?, ProfitPercentAtTenCandlestickTime=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    return jdbcTemplate.update(sql, tenCandleStickTimePrice, tenCandleStickTimeProfitPercent, chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt()) == 1;
  }

  public boolean setSignalTargetTimePrice(ChartPatternSignal chartPatternSignal,
                                            double price,
                                            double profitPercent) {
    String sql = "update ChartPatternSignal set PriceAtSignalTargetTime=?, ProfitPercentAtSignalTargetTime=?, IsSignalOn=0" +
        " where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) " +
        "and Attempt=?";
    return jdbcTemplate.update(sql, price, profitPercent, chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt()) == 1;
  }

  public boolean incrementNumTimesMissingInInput(List<ChartPatternSignal> chartPatternsMissingInInput) {
    String sql = "update ChartPatternSignal set NumTimesMissingInInput = NumTimesMissingInInput + 1 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) and " +
        "Attempt=?";
    boolean retVal = true;
    for (ChartPatternSignal chartPatternSignal: chartPatternsMissingInInput) {
      int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()), chartPatternSignal.attempt());
      if (ret == 1) {
        logger.info("Updated chart pattern signal missing count to " + (chartPatternSignal.numTimesMissingInInput() + 1) + " for chart pattern signal: " + chartPatternSignal.toString());
      } else {
        logger.error("Failed to increment numTimesMissingInInput for chart pattern signal: " + chartPatternSignal.toString());
      }
      retVal &= (ret == 1);
    }
    return retVal;
  }

  public void resetNumTimesMissingInInput(List<ChartPatternSignal> chartPatternSignalsReappearedInTime) {
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

  public void failedToGetPriceAtTenCandlestickTime(ChartPatternSignal chartPatternSignal) {
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

  public void failedToGetPriceAtSignalTargetTime(ChartPatternSignal chartPatternSignal) {
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

  public List<ChartPatternSignal> getChartPatternSignalsToPlaceTrade(TimeFrame timeFrame, TradeType tradeType) {
    String sql = String.format("select * from ChartPatternSignal where IsSignalOn=1 and EntryOrderId is null and " +
        "timeFrame='%s' and tradeType='%s'", timeFrame.name(), tradeType.name());
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public boolean setEntryOrder(ChartPatternSignal chartPatternSignal, ChartPatternSignal.Order entryOrder) {
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
  public boolean setExitMarketOrder(ChartPatternSignal chartPatternSignal, ChartPatternSignal.Order exitMarketOrder,
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

  public boolean setExitStopLimitOrder(ChartPatternSignal chartPatternSignal, ChartPatternSignal.Order exitMarketOrder) {
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

  public boolean updateExitStopLimitOrder(ChartPatternSignal chartPatternSignal,
                                          Order exitStopLimitOrderStatus) throws ParseException {
    double executedQty = numberFormat.parse(exitStopLimitOrderStatus.getExecutedQty()).doubleValue();
    double executedPrice = numberFormat.parse(exitStopLimitOrderStatus.getPrice()).doubleValue();
    Pair<Double, Double> realizedUnRealized = getRealizedUnRealized(
        chartPatternSignal,
        executedQty,
        numberFormat.parse(exitStopLimitOrderStatus.getPrice()).doubleValue(),
        TradeExitType.STOP_LOSS,
        exitStopLimitOrderStatus.getStatus());
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
    int ret = jdbcTemplate.update(sql, exitStopLimitOrderStatus.getOrderId(),
        exitStopLimitOrderStatus.getExecutedQty(),
        executedPrice,
        exitStopLimitOrderStatus.getStatus().name(),
        realizedUnRealized.getFirst(), realizedPercent, realizedUnRealized.getSecond(), unRealizedPercent,
        exitStopLimitOrderStatus.getStatus() == OrderStatus.FILLED,
        exitStopLimitOrderStatus.getStatus() == OrderStatus.FILLED ? 0 : 1,
        chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(),
        chartPatternSignal.tradeType().name(),
        chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()),
        chartPatternSignal.attempt());
    if (ret == 1) {
      logger.info(String.format("Updated chart pattern signal \n%s\nwith exit stop limit order id %d.", chartPatternSignal.toString(), exitStopLimitOrderStatus.getOrderId()));
    } else {
      logger.error(String.format("Failed to update chart pattern signal \n%s\nwith exit stop limit id %d.", chartPatternSignal.toString(), exitStopLimitOrderStatus.getOrderId()));
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
  public boolean setTenCandleSticktime(ChartPatternSignal chartPatternSignal) {
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

  public boolean updateMaxLossAndTargetMetValues(ChartPatternSignal chartPatternSignal) {
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
      logger.info(String.format("Updated max loss and target met values for chart pattern signal:%s\n." +
          "Updated values - maxLoss=%f, maxLossPercent=%f, IsPriceTargetMet=%s", chartPatternSignal,
          chartPatternSignal.maxLoss(), chartPatternSignal.maxLossPercent(),
          chartPatternSignal.isPriceTargetMet() != null ?
              chartPatternSignal.isPriceTargetMet() ? "True" : "False"
          : null));
    }
    return ret == 1;
  }

  public void cancelStopLimitOrder(ChartPatternSignal chartPatternSignal) {
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

  public void insertOverdoneTradeType(Date date, TimeFrame timeFrame, TradeType tradeTypeOverdone) {
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

  public void updateBitcoinPrice(TimeFrame timeFrame, Long openTime, String open, String close) {

  }
}
