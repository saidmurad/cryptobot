package com.gateiobot.db;

import com.binance.bot.common.CandlestickUtil;
import com.binance.bot.common.Mailer;
import com.binance.bot.common.Util;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeType;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.binance.bot.tradesignals.TimeFrame;

import javax.mail.MessagingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@Repository
public class MACDDataDao {
  @Autowired
  public
  JdbcTemplate jdbcTemplate;
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  Clock clock = Clock.systemDefaultZone();


  @Autowired
  public MACDDataDao() {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("pragma journal_mode=WAL");
    }
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  public void setMockClock(Clock clock) {
    this.clock = clock;
  }

  synchronized public List<MACDData> getFullMACDDataList(String coinPair, TimeFrame timeFrame) {
    String sql = String.format(
        "select * from MACDData where CoinPair='%s' and TimeFrame='%s' order by Time",
        coinPair, timeFrame.name());
    return jdbcTemplate.query(sql, new MACDDataRowMapper());
  }

  synchronized public List<MACDData> getMACDDataList(String coinPair, TimeFrame timeFrame, int numRows) {
    String sql = String.format(
        "select * from MACDData where CoinPair='%s' and TimeFrame='%s' order by Time desc limit %d",
        coinPair, timeFrame.name(), numRows);
    List<MACDData> descendingList = jdbcTemplate.query(sql, new MACDDataRowMapper());
    List<MACDData> ascendingList = new ArrayList<>(descendingList.size());
    for (int i = descendingList.size() - 1; i >= 0; i--) {
      ascendingList.add(descendingList.get(i));
    }
    return ascendingList;
  }

  synchronized public List<MACDData> getMACDDataUntilTime(String coinPair, TimeFrame timeFrame, Date time, int numRows) {
    if (!coinPair.contains("_")) {
      String baseAsset = coinPair.substring(0, coinPair.length() - 4);
      coinPair = baseAsset + "_" + "USDT";
    }
    String sql = String.format(
        "select * from MACDData where CoinPair='%s' and TimeFrame='%s' and DATE(Time) <= DATE('%s') order by Time " +
            "desc limit %d",
        coinPair, timeFrame.name(), dateFormat.format(time), numRows);
    List<MACDData> descendingList = jdbcTemplate.query(sql, new MACDDataRowMapper());
    List<MACDData> ascendingList = new ArrayList<>(descendingList.size());
    for (int i = descendingList.size() - 1; i >= 0; i--) {
      ascendingList.add(descendingList.get(i));
    }
    return ascendingList;
  }

  synchronized public List<MACDData> getMACDDataBetweenTimes(String coinPair, TimeFrame timeFrame, Date fromTime, Date toTime) {
    if (!coinPair.contains("_")) {
      coinPair = Util.getGateFormattedCurrencyPair(coinPair);
    }
    String sql = String.format(
            "select * from MACDData where CoinPair='%s' and TimeFrame='%s' and DateTime(Time) >= DateTime('%s') and DateTime(Time) <= DateTime('%s') " +
                    "order by Time",
            coinPair, timeFrame.name(), dateFormat.format(fromTime), dateFormat.format(toTime));
    return jdbcTemplate.query(sql, new MACDDataRowMapper());
  }

  synchronized public MACDData getMACDDataForCandlestick(String coinPair, TimeFrame timeFrame, Date time) {
    String sql = String.format(
        "select * from MACDData where CoinPair='%s' and TimeFrame='%s' and DATETime(Time) = DATETime('%s')",
        coinPair, timeFrame.name(), dateFormat.format(time));
    try {
      return jdbcTemplate.queryForObject(sql, new MACDDataRowMapper());
    } catch (EmptyResultDataAccessException ex) {
      return null;
    }
  }

  synchronized private List<MACDData> getMACDDataAfterTime(String coinPair, TimeFrame timeFrame, Date time, int numRows) {
    if (!coinPair.contains("_")) {
      String baseAsset = coinPair.substring(0, coinPair.length() - 4);
      coinPair = baseAsset + "_" + "USDT";
    }
    String sql = String.format(
        "select * from MACDData where CoinPair='%s' and TimeFrame='%s' and DateTime(Time) >= DateTime('%s') order by Time limit %d",
        coinPair, timeFrame.name(), dateFormat.format(time), numRows);
    return jdbcTemplate.query(sql, new MACDDataRowMapper());
  }

  public MACDData getTradeExitSignalBySignalCrossOver(ChartPatternSignal chartPatternSignal) {
    Date candlestickTime = chartPatternSignal.timeOfSignal();
    String coinPair = chartPatternSignal.coinPair();
    if (!coinPair.contains("_")) {
      String baseAsset = coinPair.substring(0, coinPair.length() - 4);
      coinPair = baseAsset + "_" + "USDT";
    }
    do {
      List<MACDData> macdDataList = getMACDDataAfterTime(coinPair, chartPatternSignal.timeFrame(), candlestickTime, 10);
      for (MACDData macdData : macdDataList) {
        if (macdData.histogram == 0.0) {
          // TODO: incomplete data. sends to infinite loop. occurred for UNI_USDT in 4 hour.
          return null;
        }
        if (chartPatternSignal.tradeType() == TradeType.BUY && macdData.histogram < 0
            || chartPatternSignal.tradeType() == TradeType.SELL && macdData.histogram > 0) {
          return macdData;
        }
      }
      if (!macdDataList.isEmpty()) {
        candlestickTime = DateUtils.addMinutes(macdDataList.get(macdDataList.size() -1).time, 1);
      } else {
        candlestickTime = null;
      }
      //logger.info("Moving to next iteration with candlesticktime " + dateFormat.format(candlestickTime));
    } while (candlestickTime != null);
    return null;
  }

  public MACDData getTradeExitSignalByHistogramTrendReversal(ChartPatternSignal chartPatternSignal) {
    Date candlestickTime = chartPatternSignal.timeOfSignal();
    String coinPair = chartPatternSignal.coinPair();
    if (!coinPair.contains("_")) {
      String baseAsset = coinPair.substring(0, coinPair.length() - 4);
      coinPair = baseAsset + "_" + "USDT";
    }
    do {
      List<MACDData> macdDataList = getMACDDataAfterTime(coinPair, chartPatternSignal.timeFrame(), candlestickTime, 10);
      for (MACDData macdData : macdDataList) {
        if (macdData.histogram == 0.0) {
          // TODO: incomplete data. sends to infinite loop. occurred for UNI_USDT in 4 hour.
          return null;
        }
        if (chartPatternSignal.tradeType() == TradeType.BUY && macdData.histogramTrendType == HistogramTrendType.DECELERATING
            || chartPatternSignal.tradeType() == TradeType.SELL && macdData.histogramTrendType == HistogramTrendType.ACCELERATING) {
          return macdData;
        }
      }
      if (!macdDataList.isEmpty()) {
        candlestickTime = DateUtils.addMinutes(macdDataList.get(macdDataList.size() -1).time, 1);
      } else {
        candlestickTime = null;
      }
      //logger.info("Moving to next iteration with candlesticktime " + dateFormat.format(candlestickTime));
    } while (candlestickTime != null);
    return null;
  }

  synchronized public boolean insert(MACDData macd) {
    String updateSql = String.format("insert into MACDData(" +
            "CoinPair, TimeFrame, Time, CandleClosingPrice, SMA, SMASlope," +
            "Trend, EMA12, EMA26, MACD, MACDSignal, Histogram, HistogramEMA, HistogramTrendType) values " +
            "('%s', '%s', '%s', %f, %f, %f, '%s', %f, %f, %f, %f, %f, %f, '%s')",
        macd.coinPair, macd.timeFrame.name(), dateFormat.format(macd.time), macd.candleClosingPrice, macd.sma,
        macd.smaSlope, macd.trendType, macd.ema12, macd.ema26, macd.macd, macd.macdSignal, macd.histogram,
        macd.histogramEMA, macd.histogramTrendType == null ? HistogramTrendType.NA.name() : macd.histogramTrendType.name());
    return jdbcTemplate.update(updateSql) == 1;
  }

  synchronized public void updatePPOMacd(MACDData macdData) {
    String updateSql = String.format("update MACDData set PPOMacd=%f, PPOMacdSignalLine=%f, PPOHistogram=%f " +
        "where CoinPair='%s' and TimeFrame='%s' and Time='%s'", macdData.ppoMacd, macdData.ppoMacdSignalLine,
        macdData.ppoHistogram, macdData.coinPair, macdData.timeFrame.name(), dateFormat.format(macdData.time));
    int ret = jdbcTemplate.update(updateSql);
    if (ret != 1) {
      logger.error(String.format("Failed to udpate PPOMACd for MACDData %s. Expected ret = 1 but got %d.",
          macdData, ret));
    }
    return;
  }

  public void updateHistogramEMA(MACDData macdData) {
    String updateSql = String.format("update MACDData set HistogramEMA=%f, HistogramTrendType='%s' " +
            "where CoinPair='%s' and TimeFrame='%s' and Time='%s'", macdData.histogramEMA, macdData.histogramTrendType.name(),
        macdData.coinPair, macdData.timeFrame.name(), dateFormat.format(macdData.time));
    int ret = jdbcTemplate.update(updateSql);
    if (ret != 1) {
      logger.error(String.format("Failed to udpate HistogramEMA for MACDData %s. Expected ret = 1 but got %d.",
          macdData, ret));
    }
    return;
  }

  public MACDData getLastMACDData(String coinPair, TimeFrame timeFrame) throws ParseException {
    Date currentCandlestickStart = CandlestickUtil.getCandlestickStart(new Date(clock.millis()), timeFrame);
    Date lastCandlestickStart = CandlestickUtil.getIthCandlestickTime(currentCandlestickStart, timeFrame, -1);
    return getMACDDataForCandlestick(coinPair, timeFrame, lastCandlestickStart);
  }

  public double getStopLossLevelBasedOnBreakoutCandlestick(ChartPatternSignal chartPatternSignal)  {
    MACDData candlestickBeforeBreakoutOne = getMACDDataForCandlestick(
        Util.getGateFormattedCurrencyPair(chartPatternSignal.coinPair()), chartPatternSignal.timeFrame(),
        CandlestickUtil.getIthCandlestickTime(chartPatternSignal.timeOfSignal(), chartPatternSignal.timeFrame(), -2));
    if (candlestickBeforeBreakoutOne == null) {
      try {
        mailer.sendEmail("Null pre-breakout candlestick price.", chartPatternSignal.toString());
      } catch (MessagingException e) {
        logger.error("Exception", e);
      }
    }
    return candlestickBeforeBreakoutOne.candleClosingPrice;
  }
  private final Mailer mailer = new Mailer();
}
