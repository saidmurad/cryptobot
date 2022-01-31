package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static com.binance.bot.common.Util.getProfitPercentAtWithPrice;
import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

@Component
public class PriceTenCandlestickRealtimeCheckerTask {

  static final long TIME_RANGE_AGG_TRADES = 60000;
  private final BinanceApiRestClient restClient;
  private ChartPatternSignalDaoImpl dao;
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final SupportedSymbolsInfo supportedSymbolsInfo;

  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  @Autowired
  PriceTenCandlestickRealtimeCheckerTask(BinanceApiClientFactory binanceApiClientFactory,
                                         ChartPatternSignalDaoImpl dao, SupportedSymbolsInfo supportedSymbolsInfo) {
    restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    this.supportedSymbolsInfo = supportedSymbolsInfo;
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

  }

  @Scheduled(fixedDelay = 60000)
  public void performPriceTargetChecks() throws ParseException, IOException {
    HeartBeatChecker.logHeartBeat(getClass());
    List<ChartPatternSignal> signalsTenCandleStick = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    for (int i = 0; i < signalsTenCandleStick.size(); i++) {
      ChartPatternSignal chartPatternSignal = signalsTenCandleStick.get(i);
      if (!supportedSymbolsInfo.getSupportedSymbols().containsKey(chartPatternSignal.coinPair())) {
        logger.warn("Symbol unsupported or unavailable at the moment: " + chartPatternSignal.coinPair());
        continue;
      }
      long tenCandleStickTime = chartPatternSignal.timeOfSignal().getTime() + getTenCandleStickTimeIncrementMillis(chartPatternSignal);
      if (tenCandleStickTime > chartPatternSignal.priceTargetTime().getTime()) {
        // TODO: Unit test this.
        logger.info(String.format("Not setting 10 candlestick time %s price for %s since it falls after target time.",
            dateFormat.format(new Date(tenCandleStickTime)), chartPatternSignal));
      } else {
        double tenCandleStickTimePrice = NumberFormat.getInstance(Locale.US).parse(restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
        boolean ret = dao.setTenCandleStickTimePrice(chartPatternSignal, tenCandleStickTimePrice, getProfitPercentAtWithPrice(chartPatternSignal, tenCandleStickTimePrice));
        logger.info("Set 10 candlestick time price for '" + chartPatternSignal.coinPair() + "' with 10 candlestick time due at '" + dateFormat.format(tenCandleStickTime) + "' using api: Price. Ret val=" + ret);
      }
    }
  }
}
