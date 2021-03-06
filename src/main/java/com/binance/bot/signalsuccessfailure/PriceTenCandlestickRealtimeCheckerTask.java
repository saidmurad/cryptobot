package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.trading.ExitPositionAtMarketPrice;
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
  private final ExitPositionAtMarketPrice exitPositionAtMarketPrice;
  private ChartPatternSignalDaoImpl dao;
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final SupportedSymbolsInfo supportedSymbolsInfo;
  @Value("${exit_trades_at_ten_candlestick_time}")
  boolean exitTradesAtTenCandlestickTime;

  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  @Autowired
  PriceTenCandlestickRealtimeCheckerTask(BinanceApiClientFactory binanceApiClientFactory,
                                         ChartPatternSignalDaoImpl dao, SupportedSymbolsInfo supportedSymbolsInfo,
                                         ExitPositionAtMarketPrice exitPositionAtMarketPrice) {
    restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    this.supportedSymbolsInfo = supportedSymbolsInfo;
    this.exitPositionAtMarketPrice = exitPositionAtMarketPrice;
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

  }

  @Scheduled(fixedDelay = 60000, initialDelayString = "${timing.initialDelay}")
  public void performPriceTargetChecks() throws ParseException, IOException, BinanceApiException, MessagingException {
    HeartBeatChecker.logHeartBeat(getClass());
    List<ChartPatternSignal> signalsTenCandleStick = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    logger.info(String.format("%d signals reached ten candle stick time.", signalsTenCandleStick.size()));
    for (int i = 0; i < signalsTenCandleStick.size(); i++) {
      ChartPatternSignal chartPatternSignal = signalsTenCandleStick.get(i);
      if (!supportedSymbolsInfo.getTradingActiveSymbols().containsKey(chartPatternSignal.coinPair())) {
        //logger.info("Symbol unsupported or unavailable at the moment: " + chartPatternSignal.coinPair());
        continue;
      }
      long tenCandleStickTime = chartPatternSignal.timeOfSignal().getTime() + getTenCandleStickTimeIncrementMillis(chartPatternSignal);
      if (tenCandleStickTime > chartPatternSignal.priceTargetTime().getTime()) {
        // TODO: Unit test this.
        //logger.info(String.format("Not setting 10 candlestick time %s price for %s since it falls after target time.",
        //    dateFormat.format(new Date(tenCandleStickTime)), chartPatternSignal));
      } else {
        double tenCandleStickTimePrice = NumberFormat.getInstance(Locale.US).parse(restClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
        boolean ret = dao.setTenCandleStickTimePrice(chartPatternSignal, tenCandleStickTimePrice, getProfitPercentAtWithPrice(chartPatternSignal, tenCandleStickTimePrice));
        if (exitTradesAtTenCandlestickTime) {
          exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TEN_CANDLESTICK_TIME_PASSED);
        }
        //logger.info("Set 10 candlestick time price for '" + chartPatternSignal.coinPair() + "' with 10 candlestick time due at '" + dateFormat.format(tenCandleStickTime) + "' using api: Price. Ret val=" + ret);
      }
    }
  }
}
