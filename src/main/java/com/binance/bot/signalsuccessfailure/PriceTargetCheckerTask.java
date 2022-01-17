package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
class PriceTargetCheckerTask {

  private final BinanceApiRestClient restClient;
  private ChartPatternSignalDaoImpl dao;
  private Logger logger = LoggerFactory.getLogger(getClass());
  @Autowired
  private SupportedSymbolsInfo supportedSymbolsInfo;

  @Autowired
  PriceTargetCheckerTask(BinanceApiClientFactory binanceApiClientFactory,
                         ChartPatternSignalDaoImpl dao) {
    restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
  }

  @Scheduled(fixedDelay = 60000)
  public void performPriceTargetChecks() {
    logger.info("Invoked.");
    List<ChartPatternSignal> signalsTenCandleStick = dao.getChatPatternSignalsThatReachedTenCandleStickTime();
    signalsTenCandleStick.stream().forEach(chartPatternSignal -> {
      if (!supportedSymbolsInfo.getSupportedSymbols().containsKey(chartPatternSignal.coinPair())) {
        logger.warn("Symbol unsupported: " + chartPatternSignal.coinPair());
        boolean ret = dao.invalidateChartPatternSignal(chartPatternSignal, ReasonForSignalInvalidation.SYMBOL_NOT_SUPPORTED);
        logger.warn("Ret val: " + ret);
        return;
      }
      long tenCandleStickTime = chartPatternSignal.timeOfSignal().getTime() + getTenCandleStickTimeIncrementMillis(chartPatternSignal);
      List<AggTrade> tradesList = restClient.getAggTrades(chartPatternSignal.coinPair(), null, 1, tenCandleStickTime, tenCandleStickTime + 1000);
      if (tradesList.size() == 0) {
        logger.error(String.format("Got zero agg trades for '%s' with start time '%d' and end time '%d' but got zero trades.", chartPatternSignal.coinPair(),
            tenCandleStickTime, tenCandleStickTime + 1000));
        return;
      }
      double tenCandleStickTimePrice = Double.parseDouble(tradesList.get(0).getPrice());
      boolean ret = dao.setTenCandleStickTimePrice(chartPatternSignal, tenCandleStickTimePrice, getProfitPercentAtTenCandlestickTime(chartPatternSignal, tenCandleStickTimePrice));
      logger.info("Set 10 candlestick time price for '" + chartPatternSignal.coinPair() + "'. Ret val=" + ret);
    });
  }

  private double getProfitPercentAtTenCandlestickTime(ChartPatternSignal chartPatternSignal, double tenCandleStickTimePrice) {
    switch (chartPatternSignal.tradeType()) {
      case BUY:
        return (tenCandleStickTimePrice - chartPatternSignal.priceAtTimeOfSignal()) / chartPatternSignal.priceAtTimeOfSignal() * 100;
      default:
        return (chartPatternSignal.priceAtTimeOfSignal() - tenCandleStickTimePrice) / chartPatternSignal.priceAtTimeOfSignal() * 100;
    }
  }

  private long getTenCandleStickTimeIncrementMillis(ChartPatternSignal chartPatternSignal) {
    switch (chartPatternSignal.timeFrame()) {
      case FIFTEEN_MINUTES:
        return TimeUnit.MINUTES.toMillis(150);
      case HOUR:
        return TimeUnit.HOURS.toMillis(10);
      case FOUR_HOURS:
        return TimeUnit.HOURS.toMillis(40);
      default:
        return TimeUnit.DAYS.toMillis(10);
    }
  }
}
