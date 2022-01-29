package com.binance.bot.database;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.trading.VolumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

@Repository
public class ChartPatternSignalDaoImpl {
  @Autowired
  private JdbcTemplate jdbcTemplate;
  final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  ChartPatternSignalDaoImpl() {
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  void setDataSource(DataSource dataSource) {
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  public boolean insertChartPatternSignal(ChartPatternSignal chartPatternSignal, VolumeProfile volProfile) {
    String sql = "insert into ChartPatternSignal(CoinPair, TimeFrame, TradeType, Pattern, PriceAtTimeOfSignal, PriceAtTimeOfSignalReal," +
        "PriceRelatedToPattern, TimeOfSignal, TimeOfInsertion, IsInsertedLate, NumTimesMissingInInput, " +
        "VolumeAtSignalCandlestick, VolumeAverage, IsVolumeSurge, PriceTarget, PriceTargetTime, ProfitPotentialPercent, " +
        "IsSignalOn, TenCandlestickTime, FailedToGetPriceAtTenCandlestickTime)" +
        "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    Object params[] = new Object[]{chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(),
        chartPatternSignal.tradeType().name(),
        chartPatternSignal.pattern(),
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

    return jdbcTemplate.update(sql, params) > 0;
  }

  public boolean invalidateChartPatternSignal(ChartPatternSignal chartPatternSignal, double priceAtTimeOfInvalidation, ReasonForSignalInvalidation reasonForSignalInvalidation) {
    String sql = "update ChartPatternSignal set IsSignalOn=0, TimeOfSignalInvalidation=?, " +
        "PriceAtTimeOfSignalInvalidation=?, ReasonForSignalInvalidation=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    boolean ret1 = jdbcTemplate.update(sql, df.format(new Date()), Double.toString(priceAtTimeOfInvalidation), reasonForSignalInvalidation.name(), chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())) == 1;
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

  public List<ChartPatternSignal> getAllChartPatterns() {
    List<ChartPatternSignal> allPatterns = new ArrayList<>();
    allPatterns.addAll(jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + TimeFrame.FIFTEEN_MINUTES.name() + "'", new ChartPatternSignalMapper()));
    allPatterns.addAll(jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + TimeFrame.HOUR.name() + "'", new ChartPatternSignalMapper()));
    allPatterns.addAll(jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + TimeFrame.FOUR_HOURS.name() + "'", new ChartPatternSignalMapper()));
    allPatterns.addAll(jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + TimeFrame.DAY.name() + "'", new ChartPatternSignalMapper()));
    return allPatterns;
  }

  // intented for test use.
  public ChartPatternSignal getChartPattern(ChartPatternSignal chartPatternSignal) {
    String sql = "select * from ChartPatternSignal where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    return jdbcTemplate.queryForObject(sql, new Object[]{chartPatternSignal.coinPair(), chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())}, new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getChatPatternSignalsThatJustReachedTenCandleStickTime() {
    String sql = "select * from ChartPatternSignal \n" +
        "    where PriceAtTenCandlestickTime is null\n" +
        "    and FailedToGetPriceAtTenCandlestickTime = 0\n" +
        "    and ((TimeFrame = 'FIFTEEN_MINUTES' and DATETIME(TimeOfSignal, '+150 minute') <= DATETIME('now') and DATETIME(TimeOfSignal, '+150 minute') >= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'HOUR' and DATETIME(TimeOfSignal, '+10 hour') <= DATETIME('now') and DATETIME(TimeOfSignal, '+10 hour') >= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'FOUR_HOURS' and DATETIME(TimeOfSignal, '+40 hour') <= DATETIME('now') and DATETIME(TimeOfSignal, '+40 hour') >= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'DAY' and DATETIME(TimeOfSignal, '+10 day') <= DATETIME('now') and DATETIME(TimeOfSignal, '+10 day') >= DATETIME('now', '-10 minute')))";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public List<ChartPatternSignal> getChatPatternSignalsThatLongSinceReachedTenCandleStickTime() {
    String sql = "select * from ChartPatternSignal \n" +
        "    where PriceAtTenCandlestickTime is null\n" +
        "    and FailedToGetPriceAtTenCandlestickTime = 0\n" +
        "    and ((TimeFrame = 'FIFTEEN_MINUTES' and DATETIME(TimeOfSignal, '+150 minute') <= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'HOUR' and DATETIME(TimeOfSignal, '+10 hour') <= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'FOUR_HOURS' and DATETIME(TimeOfSignal, '+40 hour') <= DATETIME('now', '-10 minute'))\n" +
        "    or (TimeFrame = 'DAY' and DATETIME(TimeOfSignal, '+10 day') <= DATETIME('now', '-10 minute')))";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public boolean setTenCandleStickTimePrice(ChartPatternSignal chartPatternSignal,
                                            double tenCandleStickTimePrice,
                                            double tenCandleStickTimeProfitPercent) {
    String sql = "update ChartPatternSignal set PriceAtTenCandlestickTime=?, ProfitPercentAtTenCandlestickTime=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    return jdbcTemplate.update(sql, tenCandleStickTimePrice, tenCandleStickTimeProfitPercent, chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())) == 1;
  }

  public void incrementNumTimesMissingInInput(List<ChartPatternSignal> chartPatternsMissingInInput) {
    String sql = "update ChartPatternSignal set NumTimesMissingInInput = NumTimesMissingInInput + 1 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    for (ChartPatternSignal chartPatternSignal: chartPatternsMissingInInput) {
      int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()));
      if (ret == 1) {
        logger.info("Updated chart pattern signal missing count to " + (chartPatternSignal.numTimesMissingInInput() + 1) + " for chart pattern signal: " + chartPatternSignal.toString());
      } else {
        logger.error("Failed to increment numTimesMissingInInput for chart pattern signal: " + chartPatternSignal.toString());
      }
    }
  }

  public void resetNumTimesMissingInInput(List<ChartPatternSignal> chartPatternSignalsReappearedInTime) {
    String sql = "update ChartPatternSignal set NumTimesMissingInInput = 0, IsSignalOn=1 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    String sql2 = "insert into ChartPatternSignalInvalidationEvents(CoinPair, TimeFrame, TradeType, Pattern, TimeOfSignal, InvalidationEventTime, Event)" +
        "values(?, ?, ?, ?, ?, ?, ?)";
    for (ChartPatternSignal chartPatternSignal: chartPatternSignalsReappearedInTime) {
      int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()));
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
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()));
    if (ret == 1) {
      logger.info("Updated FailedToGetPriceAtTenCandlestickTime to 1 for chart pattern signal: " + chartPatternSignal.toString());
    } else {
      logger.error("Failed to update FailedToGetPriceAtTenCandlestickTime to 1 for chart pattern signal: " + chartPatternSignal.toString());
    }
  }

  public List<ChartPatternSignal> getChartPatternSignalsToInvalidate() {
    String sql = "select * from ChartPatternSignal where IsSignalOn=1 and NumTimesMissingInInput >= 1";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public void setEntryTrade(ChartPatternSignal chartPatternSignal, ChartPatternSignal.Trade entryTrade) {
    String sql = "update ChartPatternSignal set EntryOrderId=?, EntryPrice=?, Qty=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    int ret = jdbcTemplate.update(sql, entryTrade.orderId(), entryTrade.price(), entryTrade.qty(),
        chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal()));
    if (ret == 1) {
      logger.info(String.format("Updated chart pattern sgnal \n%s\nwith entry order id %d.", chartPatternSignal.toString(), entryTrade.orderId()));
    } else {
      logger.error(String.format("Failed to update chart pattern sgnal \n%s\nwith entry order id %d.", chartPatternSignal.toString(), entryTrade.orderId()));
    }
  }

  // As the column is a late addition, this is for backfilling the column.
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
}
