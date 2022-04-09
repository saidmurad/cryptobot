package com.gateiobot.db;

import com.binance.bot.tradesignals.TimeFrame;
import com.google.common.base.Strings;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class MACDDataRowMapper implements RowMapper<MACDData> {
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  public MACDDataRowMapper() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Override
  public MACDData mapRow(ResultSet rs, int rowNum) throws SQLException {
    try {
      MACDData macdData = new MACDData();
      macdData.coinPair = rs.getString("CoinPair");
      macdData.timeFrame = TimeFrame.valueOf(rs.getString("TimeFrame"));
      macdData.time = dateFormat.parse(rs.getString("Time"));
      macdData.candleClosingPrice = rs.getDouble("CandleClosingPrice");
      macdData.sma = rs.getDouble("SMA");
      macdData.smaSlope = rs.getDouble("SMASlope");
      if (rs.getString("Trend") != null) {
        macdData.trendType = TrendType.valueOf(rs.getString("Trend"));
      } else {
        macdData.trendType = TrendType.NA;
      }
      macdData.ema12 = rs.getDouble("EMA12");
      macdData.ema26 = rs.getDouble("EMA26");
      macdData.macd = rs.getDouble("MACD");
      macdData.macdSignal = rs.getDouble("MACDSignal");
      macdData.histogram = rs.getDouble("histogram");
      macdData.histogramEMA = rs.getDouble("histogramEMA");
      macdData.histogramTrendType = rs.getString("histogramTrendType") == null
      ? HistogramTrendType.NA : HistogramTrendType.valueOf(rs.getString("histogramTrendType"));
      return macdData;
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }
}
