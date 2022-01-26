package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
class PriceTargetCheckerTask {

  static final long TIME_RANGE_AGG_TRADES = 60000;
  private final BinanceApiRestClient restClient;
  private ChartPatternSignalDaoImpl dao;
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final SupportedSymbolsInfo supportedSymbolsInfo;

  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  @Autowired
  PriceTargetCheckerTask(BinanceApiClientFactory binanceApiClientFactory,
                         ChartPatternSignalDaoImpl dao, SupportedSymbolsInfo supportedSymbolsInfo) {
    restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    this.supportedSymbolsInfo = supportedSymbolsInfo;
  }

  private static final int REQUEST_WEIGHT_1_MIN_LIMIT = 1200;
  private long requestCount = 0;

  private void doRequestThrottleIfNeeded() throws InterruptedException {
    if (requestCount % REQUEST_WEIGHT_1_MIN_LIMIT == 0) {
      Thread.sleep(60000);
    }
  }

  @Scheduled(fixedDelay = 60000)
  public void performPriceTargetChecks() throws InterruptedException, ParseException {
    List<ChartPatternSignal> signalsTenCandleStick = dao.getChatPatternSignalsThatReachedTenCandleStickTime();
    for (int i = 0; i < signalsTenCandleStick.size(); i++) {
      ChartPatternSignal chartPatternSignal = signalsTenCandleStick.get(i);
      if (!supportedSymbolsInfo.getSupportedSymbols().containsKey(chartPatternSignal.coinPair())) {
        logger.warn("Symbol unsupported: " + chartPatternSignal.coinPair());
        boolean ret = dao.invalidateChartPatternSignal(chartPatternSignal, 0.0, ReasonForSignalInvalidation.SYMBOL_NOT_SUPPORTED);
        logger.warn("Ret val: " + ret);
        continue;
      }
      long tenCandleStickTime = chartPatternSignal.timeOfSignal().getTime() + getTenCandleStickTimeIncrementMillis(chartPatternSignal);
      double tenCandleStickTimePrice = 0.0;
      String usedWhichApi = "";
      if (System.currentTimeMillis() - tenCandleStickTime <= 600000) {
        tenCandleStickTimePrice = NumberFormat.getInstance(Locale.US).parse(restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
        usedWhichApi = "Price";
        doRequestThrottleIfNeeded();
      }
      // TODO: Move below block to a one time runnable batch processing.
      else {
        // TODO: Unit test.
        for (int j = 0; j < 60; j ++) {
          List<AggTrade> tradesList = restClient.getAggTrades(chartPatternSignal.coinPair(), null, 1, tenCandleStickTime, tenCandleStickTime + TIME_RANGE_AGG_TRADES * (j + 1));
          doRequestThrottleIfNeeded();
          if (tradesList.size() == 0) {
            logger.error(String.format("Got zero agg trades for '%s' in timeFrame '%s' with start time '%s' and end time %d min " +
                    "but got zero trades.", chartPatternSignal.toString(), chartPatternSignal.timeFrame().name(), dateFormat.format(tenCandleStickTime), j+1));
          } else {
            tenCandleStickTimePrice = Double.parseDouble(tradesList.get(0).getPrice());
            usedWhichApi = "aggTrades";
          }
        }
        if (tenCandleStickTimePrice == 0.0) {
          logger.error(String.format("Could not get agg trades for '%s' even with 60 minute interval, marking as failed in DB.", chartPatternSignal.toString()));
          dao.failedToGetPriceAtTenCandlestickTime(chartPatternSignal);
        }
      }
      if (tenCandleStickTimePrice > 0.0) {
        boolean ret = dao.setTenCandleStickTimePrice(chartPatternSignal, tenCandleStickTimePrice, getProfitPercentAtTenCandlestickTime(chartPatternSignal, tenCandleStickTimePrice));
        logger.info("Set 10 candlestick time price for '" + chartPatternSignal.coinPair() + "' using api: " + usedWhichApi + ". Ret val=" + ret);
      }
    }
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
