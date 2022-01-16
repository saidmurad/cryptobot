package com.binance.bot.database;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class ChartPatternSignalMapper implements RowMapper<ChartPatternSignal> {
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  ChartPatternSignalMapper() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Override
  public ChartPatternSignal mapRow(ResultSet rs, int rowNum) throws SQLException {
    ChartPatternSignal.Builder chartPatternSignalBuilder = null;
    try {
      return ChartPatternSignal.newBuilder()
          .setCoinPair(rs.getString("CoinPair"))
          .setTimeFrame(TimeFrame.valueOf(rs.getString("TimeFrame")))
          .setTradeType(TradeType.valueOf(rs.getString("TradeType")))
          .setCoinPair(rs.getString("CoinPair"))
          .setPattern(rs.getString("Pattern"))
          .setPriceAtTimeOfSignal(rs.getDouble("PriceAtTimeOfSignal"))
          .setTimeOfSignal(dateFormat.parse(rs.getString("TimeOfSignal")))
          .setPriceTarget(rs.getDouble("PriceTarget"))
          .setPriceTargetTime(dateFormat.parse(rs.getString("PriceTargetTime")))
          .setProfitPotentialPercent(rs.getDouble("ProfitPotentialPercent"))
          .setIsSignalOn(rs.getInt("IsSignalOn") == 1)
          .setVolumeAtSignalCandlestick(rs.getInt("VolumeAtSignalCandlestick"))
          .setVolumeAverage(rs.getDouble("VolumeAverage"))
          .setIsVolumeSurge(rs.getInt("IsVolumeSurge") == 1)
          .setTimeOfSignalInvalidation(rs.getString("TimeOfSignalInvalidation") != null?
              dateFormat.parse(rs.getString("TimeOfSignalInvalidation")): null)
          .setReasonForSignalInvalidation(rs.getString("ReasonForSignalInvalidation") != null?
              ReasonForSignalInvalidation.valueOf(rs.getString("ReasonForSignalInvalidation")) : null)
          .setPriceAtSignalTargetTime(rs.getDouble("PriceAtSignalTargetTime"))
          .setPriceAtTenCandlestickTime(rs.getDouble("PriceAtTenCandlestickTime"))
          .setProfitPercentAtTenCandlestickTime(rs.getDouble("ProfitPercentAtTenCandlestickTime"))
          .setPriceBestReached(rs.getDouble("PriceBestReached"))
          .setPriceCurrent(rs.getDouble("PriceCurrent"))
          .setCurrentTime(rs.getString("CurrentTime") != null? dateFormat.parse(rs.getString("CurrentTime")) : null)
          .build();
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }
}
