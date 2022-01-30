package com.binance.bot.onetimetasks;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.BinanceTradingBot;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ExecuteExitPositions {
  private final ChartPatternSignalDaoImpl dao;
  private final BinanceTradingBot binanceTradingBot;
  @Value("${fifteen_minute_timeframe_exit_trade_types}")
  private String fifteenMinuteExitTradeTypes;
  @Value("${hourly_timeframe_exit_trade_types}")
  private String hourlyExitTradeTypes;
  @Value("${four_hourly_timeframe_exit_trade_types}")
  private String fourHourlyExitTradeTypes;
  @Value("${daily_timeframe_exit_trade_types}")
  private String dailyExitTradeTypes;

  @Autowired
  ExecuteExitPositions(ChartPatternSignalDaoImpl dao, BinanceTradingBot binanceTradingBot) {
    this.dao = dao;
    this.binanceTradingBot = binanceTradingBot;
  }

  public void perform() throws ParseException {
    List<ChartPatternSignal> positionsToExit = new ArrayList<>();
    positionsToExit.addAll(getPositionsToClose(TimeFrame.FIFTEEN_MINUTES, getTradeTypes(fifteenMinuteExitTradeTypes)));
    positionsToExit.addAll(getPositionsToClose(TimeFrame.HOUR, getTradeTypes(hourlyExitTradeTypes)));
    positionsToExit.addAll(getPositionsToClose(TimeFrame.FOUR_HOURS, getTradeTypes(fourHourlyExitTradeTypes)));
    positionsToExit.addAll(getPositionsToClose(TimeFrame.DAY, getTradeTypes(dailyExitTradeTypes)));

    for (ChartPatternSignal chartPatternSignal: positionsToExit) {
      binanceTradingBot.exitPosition(chartPatternSignal);
    }
  }

  List<TradeType> getTradeTypes(String tradeTypesStr) {
    switch (tradeTypesStr) {
      case "BUY":
        return Lists.newArrayList(TradeType.BUY);
      case "SELL":
        return Lists.newArrayList(TradeType.SELL);
      case "BOTH":
        return Lists.newArrayList(TradeType.BUY, TradeType.SELL);
      default:
        return Lists.newArrayList();
    }
  }

  private List<ChartPatternSignal> getPositionsToClose(TimeFrame timeFrame, List<TradeType> tradeTypes) {
    List<ChartPatternSignal> positionsToExit = new ArrayList<>();
    for (TradeType tradeType: tradeTypes) {
      positionsToExit.addAll(dao.getChartPatternsWithActiveTradePositions(timeFrame, tradeType));
    }
    return positionsToExit;
  }
}
