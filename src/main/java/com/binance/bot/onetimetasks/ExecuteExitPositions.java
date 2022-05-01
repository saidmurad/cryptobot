package com.binance.bot.onetimetasks;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.trading.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.trading.SupportedSymbolsInfo;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ExecuteExitPositions {
  private final ChartPatternSignalDaoImpl dao;
  private final ExitPositionAtMarketPrice exitPositionAtMarketPrice;
  private final BinanceApiRestClient restClient;
  private final SupportedSymbolsInfo supportedSymbolsInfo;
  @Value("${fifteen_minute_timeframe_exit_trade_types}")
  private String fifteenMinuteExitTradeTypes;
  @Value("${hourly_timeframe_exit_trade_types}")
  private String hourlyExitTradeTypes;
  @Value("${four_hourly_timeframe_exit_trade_types}")
  private String fourHourlyExitTradeTypes;
  @Value("${daily_timeframe_exit_trade_types}")
  private String dailyExitTradeTypes;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  @Autowired
  ExecuteExitPositions(ChartPatternSignalDaoImpl dao, ExitPositionAtMarketPrice exitPositionAtMarketPrice,
  BinanceApiClientFactory binanceApiRestClientFactory, SupportedSymbolsInfo supportedSymbolsInfo) {
    this.dao = dao;
    this.exitPositionAtMarketPrice = exitPositionAtMarketPrice;
    this.restClient = binanceApiRestClientFactory.newRestClient();
    this.supportedSymbolsInfo = supportedSymbolsInfo;
  }

  public void perform() throws ParseException, MessagingException, BinanceApiException {
    List<ChartPatternSignal> positionsToExit = new ArrayList<>();
    positionsToExit.addAll(getPositionsToClose(TimeFrame.FIFTEEN_MINUTES, getTradeTypes(fifteenMinuteExitTradeTypes)));
    positionsToExit.addAll(getPositionsToClose(TimeFrame.HOUR, getTradeTypes(hourlyExitTradeTypes)));
    positionsToExit.addAll(getPositionsToClose(TimeFrame.FOUR_HOURS, getTradeTypes(fourHourlyExitTradeTypes)));
    positionsToExit.addAll(getPositionsToClose(TimeFrame.DAY, getTradeTypes(dailyExitTradeTypes)));

    for (ChartPatternSignal chartPatternSignal: positionsToExit) {
      if (!supportedSymbolsInfo.getTradingActiveSymbols().containsKey(chartPatternSignal.coinPair())) {
        // TODO: Should handle this.
        logger.warn(String.format("CoinPair %s not trading at the moment. Skipping", chartPatternSignal.coinPair()));
        continue;
      }
      exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.ORDERED_TO_EXIT_POSITIONS);
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
