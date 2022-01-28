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
import java.util.TimeZone;
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
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  private static final int REQUEST_WEIGHT_1_MIN_LIMIT = 1200;
  private long requestCount = 0;

  @Scheduled(fixedDelay = 60000)
  public void performPriceTargetChecks() throws ParseException {
    List<ChartPatternSignal> signalsTenCandleStick = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    for (int i = 0; i < signalsTenCandleStick.size(); i++) {
      ChartPatternSignal chartPatternSignal = signalsTenCandleStick.get(i);
      if (!supportedSymbolsInfo.getSupportedSymbols().containsKey(chartPatternSignal.coinPair())) {
        logger.warn("Symbol unsupported: " + chartPatternSignal.coinPair());
        boolean ret = dao.invalidateChartPatternSignal(chartPatternSignal, 0.0, ReasonForSignalInvalidation.SYMBOL_NOT_SUPPORTED);
        logger.warn("Ret val: " + ret);
        continue;
      }
      long tenCandleStickTime = chartPatternSignal.timeOfSignal().getTime() + getTenCandleStickTimeIncrementMillis(chartPatternSignal);
      double tenCandleStickTimePrice = NumberFormat.getInstance(Locale.US).parse(restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
      boolean ret = dao.setTenCandleStickTimePrice(chartPatternSignal, tenCandleStickTimePrice, getProfitPercentAtTenCandlestickTime(chartPatternSignal, tenCandleStickTimePrice));
      logger.info("Set 10 candlestick time price for '" + chartPatternSignal.coinPair() + "' with 10 candlestick time due at '" + dateFormat.format(tenCandleStickTime) + "' using api: Price. Ret val=" + ret);
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
