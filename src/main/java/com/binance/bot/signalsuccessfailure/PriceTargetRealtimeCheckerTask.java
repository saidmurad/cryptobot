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
public class PriceTargetRealtimeCheckerTask {

  private final BinanceApiRestClient restClient;
  private ChartPatternSignalDaoImpl dao;
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final SupportedSymbolsInfo supportedSymbolsInfo;

  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  @Autowired
  PriceTargetRealtimeCheckerTask(BinanceApiClientFactory binanceApiClientFactory,
                                 ChartPatternSignalDaoImpl dao, SupportedSymbolsInfo supportedSymbolsInfo) {
    restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    this.supportedSymbolsInfo = supportedSymbolsInfo;
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Scheduled(fixedDelay = 60000)
  public void performPriceTargetChecks() throws ParseException, IOException {
    HeartBeatChecker.logHeartBeat(getClass());
    List<ChartPatternSignal> signalsTargetTime = dao.getChatPatternSignalsThatJustReachedTargetTime();
    for (int i = 0; i < signalsTargetTime.size(); i++) {
      ChartPatternSignal chartPatternSignal = signalsTargetTime.get(i);
      if (!supportedSymbolsInfo.getSupportedSymbols().containsKey(chartPatternSignal.coinPair())) {
        logger.warn("Symbol unsupported or unavailable at the moment: " + chartPatternSignal.coinPair());
        continue;
      }
      double priceAtTargetTime = NumberFormat.getInstance(Locale.US).parse(restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
      boolean ret = dao.setSignalTargetTimePrice(chartPatternSignal, priceAtTargetTime, getProfitPercentAtWithPrice(chartPatternSignal, priceAtTargetTime));
      logger.info("Set target time price for '" + chartPatternSignal.coinPair() + "' using api: Price. Ret val=" + ret);
    }
  }
}
