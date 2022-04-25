package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.signalsuccessfailure.specifictradeactions.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

@Component
public class ProfitTakerTask {
  private final ChartPatternSignalDaoImpl dao;
  private final BookTickerPrices bookTickerPrices;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ExitPositionAtMarketPrice exitPositionAtMarketPrice;

  @Autowired
  ProfitTakerTask(ChartPatternSignalDaoImpl dao,
                  BookTickerPrices bookTickerPrices,
                  ExitPositionAtMarketPrice exitPositionAtMarketPrice) {
    this.dao = dao;
    this.bookTickerPrices = bookTickerPrices;
    this.exitPositionAtMarketPrice = exitPositionAtMarketPrice;
  }

  @Scheduled(fixedDelay = 60000, initialDelayString = "${timing.initialDelay}")
  public void perform() throws IOException, MessagingException, ParseException, BinanceApiException {
    HeartBeatChecker.logHeartBeat(getClass());
    List<ChartPatternSignal> activePositions = dao.getAllChartPatternsWithActiveTradePositions();
    for (ChartPatternSignal activePosition: activePositions) {
      BookTickerPrices.BookTicker bookTicker = bookTickerPrices.getBookTicker(activePosition.coinPair());
      if (bookTicker == null) {
        continue;
      }
      double currMarketPrice = activePosition.tradeType() == TradeType.BUY ? bookTicker.bestAsk() : bookTicker.bestBid();
      if (isPriceTargetMet(activePosition, currMarketPrice)) {
        logger.info(String.format("Price target met for chart pattern signal:%s.", activePosition));
        exitPositionAtMarketPrice.exitPositionIfStillHeld(activePosition, TradeExitType.PROFIT_TARGET_MET);
      }
    }
  }

  private boolean isPriceTargetMet(ChartPatternSignal activePosition, double currMarketPrice) {
    switch (activePosition.tradeType()) {
      case BUY:
        return currMarketPrice >= activePosition.priceTarget();
      case SELL:
      default:
        return currMarketPrice <= activePosition.priceTarget();
    }
  }
}
