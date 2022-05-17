package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.trading.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import com.gateiobot.db.MACDDataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

@Component
public class TradeMonitoringTask  {
  private final ChartPatternSignalDaoImpl dao;
  private final MACDDataDao macdDataDao;
  private final BookTickerPrices bookTickerPrices;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final ExitPositionAtMarketPrice exitPositionAtMarketPrice;

  @Value("${use_breakout_candlestick_for_stop_loss}")
  public boolean useBreakoutCandlestickForStopLoss;


  @Autowired
  TradeMonitoringTask (ChartPatternSignalDaoImpl dao,
                       MACDDataDao macdDataDao,
                  BookTickerPrices bookTickerPrices,
                  ExitPositionAtMarketPrice exitPositionAtMarketPrice) {
    this.dao = dao;
    this.macdDataDao = macdDataDao;
    this.bookTickerPrices = bookTickerPrices;
    this.exitPositionAtMarketPrice = exitPositionAtMarketPrice;
  }

  @Scheduled(fixedDelay = 60000, initialDelayString = "${timing.initialDelay}")
  public void perform() throws IOException, MessagingException, ParseException, BinanceApiException, InterruptedException {
    HeartBeatChecker.logHeartBeat(getClass());
    List<ChartPatternSignal> activePositions = dao.getAllChartPatternsWithActiveTradePositions();
    for (ChartPatternSignal activePosition: activePositions) {
      BookTickerPrices.BookTicker bookTicker = bookTickerPrices.getBookTicker(activePosition.coinPair());
      if (bookTicker == null) {
        continue;
      }
      double currMarketPrice = activePosition.tradeType() == TradeType.BUY ? bookTicker.bestAsk() : bookTicker.bestBid();
      if (useBreakoutCandlestickForStopLoss) {
        double preBreakoutCandlestickStopLossPrice = macdDataDao.getStopLossLevelBasedOnBreakoutCandlestick(activePosition);
        double lastCompletedCandlestickClosingPrice = macdDataDao.getLastMACDData(activePosition.coinPair(), activePosition.timeFrame()).candleClosingPrice;
        if (isPriceTargetMet(activePosition, currMarketPrice) && (activePosition.tradeType() == TradeType.BUY && lastCompletedCandlestickClosingPrice < preBreakoutCandlestickStopLossPrice
                || activePosition.tradeType() == TradeType.SELL && lastCompletedCandlestickClosingPrice > preBreakoutCandlestickStopLossPrice)) {
          logger.info(String.format("Price target meta and the last completed candlestick closing Price retraced to pre-breakout price for chart pattern signal:%s.", activePosition));
          exitPositionAtMarketPrice.exitPositionIfStillHeld(activePosition, TradeExitType.STOP_LOSS_PRE_BREAKOUT_HIT);
        }
      }
      if (!useBreakoutCandlestickForStopLoss) {
        if (isPriceTargetMet(activePosition, currMarketPrice)){
          logger.info(String.format("Price target met for chart pattern signal:%s.", activePosition));
          exitPositionAtMarketPrice.exitPositionIfStillHeld(activePosition, TradeExitType.PROFIT_TARGET_MET);
        }
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
