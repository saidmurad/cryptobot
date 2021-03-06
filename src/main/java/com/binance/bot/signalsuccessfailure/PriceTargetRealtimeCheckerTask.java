package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.Mailer;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.trading.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static com.binance.bot.common.Util.getProfitPercentAtWithPrice;

@Component
public class PriceTargetRealtimeCheckerTask {

  private final BinanceApiRestClient restClient;
  private ChartPatternSignalDaoImpl dao;
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final SupportedSymbolsInfo supportedSymbolsInfo;
  private final ExitPositionAtMarketPrice exitPositionAtMarketPrice;

  @Value("${exit_trades_at_ten_candlestick_time}")
  boolean exitTradesAtTenCandlestickTime;
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  @Autowired
  private Mailer mailer;
  @Autowired
  PriceTargetRealtimeCheckerTask(BinanceApiClientFactory binanceApiClientFactory,
                                 ChartPatternSignalDaoImpl dao, SupportedSymbolsInfo supportedSymbolsInfo,
                                 ExitPositionAtMarketPrice exitPositionAtMarketPrice) {
    restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    this.supportedSymbolsInfo = supportedSymbolsInfo;
    this.exitPositionAtMarketPrice = exitPositionAtMarketPrice;
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Scheduled(fixedDelay = 60000, initialDelayString = "${timing.initialDelay}")
  // TODO: Unit test.
  public void performPriceTargetChecks() throws MessagingException {
    try {
      HeartBeatChecker.logHeartBeat(getClass());
      List<ChartPatternSignal> signalsTargetTime = dao.getChatPatternSignalsThatJustReachedTargetTime();
      logger.info(String.format("%d chart pattern signals reached their target time:", signalsTargetTime.size()));
      for (int i = 0; i < signalsTargetTime.size(); i++) {
        ChartPatternSignal chartPatternSignal = signalsTargetTime.get(i);
        if (!supportedSymbolsInfo.getTradingActiveSymbols().containsKey(chartPatternSignal.coinPair())) {
          //logger.warn("Symbol unsupported or unavailable at the moment: " + chartPatternSignal.coinPair());
          continue;
        }
        double priceAtTargetTime = NumberFormat.getInstance(Locale.US).parse(restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
        // TODO: Race condition with ProfitTakerTask.
        if (!exitTradesAtTenCandlestickTime) {
          exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal,
              TradeExitType.TARGET_TIME_PASSED);
        }
        boolean ret = dao.setSignalTargetTimePrice(chartPatternSignal, priceAtTargetTime, getProfitPercentAtWithPrice(chartPatternSignal, priceAtTargetTime));
        //logger.info("Set target time price for '" + chartPatternSignal.coinPair() + "' using api: Price. Ret val=" + ret);
      }
    } catch (Exception ex) {
      logger.error("Exception", ex);
      mailer.sendEmail("PriceTargetRealtimeCheckerTask exception.", ex.getMessage());
    }
  }
}
