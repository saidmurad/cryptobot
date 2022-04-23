package com.binance.bot.database;

import com.binance.bot.tradesignals.*;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class ChartPatternSignalMapper implements RowMapper<ChartPatternSignal> {
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  public ChartPatternSignalMapper() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Override
  public ChartPatternSignal mapRow(ResultSet rs, int rowNum) throws SQLException {
    ChartPatternSignal.Builder chartPatternSignalBuilder = null;
    try {
      chartPatternSignalBuilder = ChartPatternSignal.newBuilder()
          .setCoinPair(rs.getString("CoinPair"))
          .setTimeFrame(TimeFrame.valueOf(rs.getString("TimeFrame")))
          .setTradeType(TradeType.valueOf(rs.getString("TradeType")))
          .setCoinPair(rs.getString("CoinPair"))
          .setPattern(rs.getString("Pattern"))
          .setAttempt(rs.getInt("Attempt"))
          .setPriceAtTimeOfSignal(rs.getDouble("PriceAtTimeOfSignal"))
          .setPriceAtTimeOfSignalReal(rs.getDouble("PriceAtTimeOfSignalReal"))
          .setTimeOfSignal(dateFormat.parse(rs.getString("TimeOfSignal")))
          .setTimeOfInsertion(rs.getString("TimeOfInsertion") != null? dateFormat.parse(rs.getString("TimeOfInsertion")) : null)
          .setIsInsertedLate(rs.getInt("IsInsertedLate") == 1)
          .setPriceTarget(rs.getDouble("PriceTarget"))
          .setPriceTargetTime(dateFormat.parse(rs.getString("PriceTargetTime")))
          .setProfitPotentialPercent(rs.getDouble("ProfitPotentialPercent"))
          .setIsSignalOn(rs.getInt("IsSignalOn") == 1)
          .setNumTimesMissingInInput(rs.getInt("NumTimesMissingInInput"))
          .setVolumeAtSignalCandlestick(rs.getInt("VolumeAtSignalCandlestick"))
          .setVolumeAverage(rs.getDouble("VolumeAverage"))
          .setIsVolumeSurge(rs.getInt("IsVolumeSurge") == 1)
          .setPriceAtTimeOfSignalInvalidation(rs.getDouble("PriceAtTimeOfSignalInvalidation"))
          .setProfitPercentAtTimeOfSignalInvalidation(rs.getDouble("ProfitPercentAtTimeOfSignalInvalidation"))
          .setTimeOfSignalInvalidation(rs.getString("TimeOfSignalInvalidation") != null?
              dateFormat.parse(rs.getString("TimeOfSignalInvalidation")): null)
          .setReasonForSignalInvalidation(rs.getString("ReasonForSignalInvalidation") != null?
              ReasonForSignalInvalidation.valueOf(rs.getString("ReasonForSignalInvalidation")) : null)
          .setPriceAtSignalTargetTime(rs.getDouble("PriceAtSignalTargetTime"))
          .setPriceAtTenCandlestickTime(rs.getDouble("PriceAtTenCandlestickTime"))
          .setFailedToGetPriceAtTenCandlestickTime(rs.getInt("FailedToGetPriceAtTenCandlestickTime") == 1)
          .setFailedToGetPriceAtSignalTargetTime(rs.getInt("FailedToGetPriceAtSignalTargetTime") == 1)
          .setProfitPercentAtTenCandlestickTime(rs.getDouble("ProfitPercentAtTenCandlestickTime"))
          .setProfitPercentAtSignalTargetTime(rs.getDouble("ProfitPercentAtSignalTargetTime"))
          .setPriceBestReached(rs.getDouble("PriceBestReached"))
          .setPriceCurrent(rs.getDouble("PriceCurrent"))
          .setCurrentTime(rs.getString("CurrentTime") != null? dateFormat.parse(rs.getString("CurrentTime")) : null)
          .setTenCandlestickTime(rs.getString("TenCandlestickTime") != null? dateFormat.parse(rs.getString("TenCandlestickTime")) : null)
          .setMaxLoss(rs.getDouble("MaxLoss"))
          .setMaxLossPercent(rs.getDouble("MaxLossPercent"))
          .setMaxLossTime(rs.getString("MaxLossTime") != null? dateFormat.parse(rs.getString("MaxLossTime")): null)
          .setIsPriceTargetMet(rs.getString("IsPriceTargetMet") != null? rs.getInt("IsPriceTargetMet") == 1 : null)
          .setPriceTargetMetTime(rs.getString("PriceTargetMetTime") != null? dateFormat.parse(rs.getString("PriceTargetMetTime")): null);
      if (rs.getString("EntryOrderId") != null) {
        chartPatternSignalBuilder.setEntryOrder(
          ChartPatternSignal.Order.create(
              Long.parseLong(rs.getString("EntryOrderId")), rs.getDouble("EntryExecutedQty"), rs.getDouble("EntryAvgPrice"),
              ChartPatternSignal.Order.OrderStatusInt.valueOf(rs.getString("EntryOrderStatus"))));
      }
      if (rs.getString("ExitStopLossOrderId") != null) {
        chartPatternSignalBuilder.setExitStopLimitOrder(
            ChartPatternSignal.Order.create(
                Long.parseLong(rs.getString("ExitStopLossOrderId")), rs.getDouble("ExitStopLossOrderExecutedQty"),
                rs.getDouble("ExitStopLossAvgPrice"),
                ChartPatternSignal.Order.OrderStatusInt.valueOf(rs.getString("ExitStopLossOrderStatus"))));
      }
      if (rs.getString("ExitOrderId") != null) {
        chartPatternSignalBuilder.setExitOrder(
            ChartPatternSignal.Order.create(
                Long.parseLong(rs.getString("ExitOrderId")), rs.getDouble("ExitOrderExecutedQty"),
                rs.getDouble("ExitOrderAvgPrice"),
                ChartPatternSignal.Order.OrderStatusInt.valueOf(rs.getString("ExitOrderStatus"))));
      }
      chartPatternSignalBuilder.setRealized(rs.getDouble("Realized"));
      chartPatternSignalBuilder.setRealizedPercent(rs.getDouble("RealizedPercent"));
      chartPatternSignalBuilder.setUnRealized(rs.getDouble("UnRealized"));
      chartPatternSignalBuilder.setUnRealizedPercent(rs.getDouble("UnRealizedPercent"));
      chartPatternSignalBuilder.setIsPositionExited(rs.getInt("IsPositionExited") == 1);
      if (rs.getString("TradeExitType") != null) {
        chartPatternSignalBuilder.setTradeExitType(TradeExitType.valueOf(rs.getString("TradeExitType")));
      }
      chartPatternSignalBuilder.setStopLossPrice(rs.getDouble("StopLossPrice"));
      return chartPatternSignalBuilder.build();
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }
}
