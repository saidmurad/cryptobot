package com.gateiobot.db;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeType;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.binance.bot.tradesignals.TimeFrame;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@Repository
public class MACDDataDao {
  @Autowired
  public JdbcTemplate jdbcTemplate;
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  @Autowired
  public MACDDataDao() {
    if (jdbcTemplate != null) {
      jdbcTemplate.execute("pragma journal_mode=WAL");
    }
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  synchronized public List<MACDData> getMACDDataUntilTime(String coinPair, TimeFrame timeFrame, int numRows) {
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

  synchronized private List<MACDData> getMACDDataAfterTime(String coinPair, TimeFrame timeFrame, Date time, int numRows) {
    if (!coinPair.contains("_")) {
      String baseAsset = coinPair.substring(0, coinPair.length() - 4);
      coinPair = baseAsset + "_" + "USDT";
    }
    String sql = String.format(
        "select * from MACDData where CoinPair='%s' and TimeFrame='%s' and DATE(Time) >= DATE('%s') order by Time limit %d",
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

  synchronized public boolean insert(MACDData macd) {
    String updateSql = String.format("insert i`nto MACDData(" +
            "CoinPair, TimeFram``e, Time, CandleClosingPrice, SMA, SMASlope," +
            "Trend, EMA12, EMA26, MACD, MACDSignal, Histogram) values " +
            "('%s', '%s', '%s', %f, %f, %f, '%s', %f, %f, %f, %f, %f)",
        macd.coinPair, macd.timeFrame.name(), dateFormat.format(macd.time), macd.candleClosingPrice, macd.sma,
        macd.smaSlope, macd.trendType, macd.ema12, macd.ema26, macd.macd, macd.macdSignal, macd.histogram);
    return jdbcTemplate.update(updateSql) == 1;
  }
}
