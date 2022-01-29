package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
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

import static com.binance.bot.common.Util.getProfitPercentAtTenCandlestickTime;
import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

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

  @Scheduled(fixedDelay = 60000)
  public void performPriceTargetChecks() throws ParseException {
    List<ChartPatternSignal> signalsTenCandleStick = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    for (int i = 0; i < signalsTenCandleStick.size(); i++) {
      ChartPatternSignal chartPatternSignal = signalsTenCandleStick.get(i);
      if (!supportedSymbolsInfo.getSupportedSymbols().containsKey(chartPatternSignal.coinPair())) {
        logger.warn("Symbol unsupported or unavailable at the moment: " + chartPatternSignal.coinPair());
        continue;
      }
      long tenCandleStickTime = chartPatternSignal.timeOfSignal().getTime() + getTenCandleStickTimeIncrementMillis(chartPatternSignal);
      double tenCandleStickTimePrice = NumberFormat.getInstance(Locale.US).parse(restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
      boolean ret = dao.setTenCandleStickTimePrice(chartPatternSignal, tenCandleStickTimePrice, getProfitPercentAtTenCandlestickTime(chartPatternSignal, tenCandleStickTimePrice));
      logger.info("Set 10 candlestick time price for '" + chartPatternSignal.coinPair() + "' with 10 candlestick time due at '" + dateFormat.format(tenCandleStickTime) + "' using api: Price. Ret val=" + ret);
    }
  }
}
