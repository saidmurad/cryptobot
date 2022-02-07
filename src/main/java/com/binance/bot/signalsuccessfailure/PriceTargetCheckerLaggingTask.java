package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static com.binance.bot.common.Util.getProfitPercentAtWithPrice;

public abstract class PriceTargetCheckerLaggingTask {

  enum TargetTimeType {
    TEN_CANDLESTICK,
    END
  }

  protected TargetTimeType targetTimeType;
  static final long TIME_RANGE_AGG_TRADES = 60000;
  private final BinanceApiRestClient restClient;
  private final SupportedSymbolsInfo supportedSymbolsInfo;
  protected ChartPatternSignalDaoImpl dao;
  private Logger logger = LoggerFactory.getLogger(getClass());

  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  Clock clock = Clock.systemDefaultZone();
  private final static int MAX_WINDOW_MINS = 60;
  private final static NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  long requestCount = 0;

  PriceTargetCheckerLaggingTask(BinanceApiClientFactory binanceApiClientFactory,
                                ChartPatternSignalDaoImpl dao,
                                SupportedSymbolsInfo supportedSymbolsInfo) {
    restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    this.supportedSymbolsInfo = supportedSymbolsInfo;
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  private static final int REQUEST_WEIGHT_1_MIN_LIMIT = 1200;

  // Caution: Not unit tested nor found worth the trouble.
  @Scheduled(fixedDelay = 600000)
  public void perform() throws InterruptedException, ParseException, IOException {
    HeartBeatChecker.logHeartBeat(getClass());
    List<ChartPatternSignal> patterns = getChartPatternSignalsThatLongSinceReachedTargetTime();
    logger.info(String.format("Retrieved %d patterns from DB.", patterns.size()));
    List<Pair<ChartPatternSignal, Integer>>  attemptedPatterns = new ArrayList<>();
    for (ChartPatternSignal pattern: patterns) {
      attemptedPatterns.add(Pair.of(pattern, 1));
    }
    while (!attemptedPatterns.isEmpty()) {
      performIteration(attemptedPatterns);
    }
    logger.info("PriceTargetCheckerLaggingTask of type " + targetTimeType + " finished work.");
  }

  protected abstract List<ChartPatternSignal> getChartPatternSignalsThatLongSinceReachedTargetTime();

  void performIteration(List<Pair<ChartPatternSignal, Integer>> attemptedPatterns) throws InterruptedException, ParseException, IOException {
    Pair<ChartPatternSignal, Integer> patternPair = attemptedPatterns.remove(0);
    if (!supportedSymbolsInfo.getTradingActiveSymbols().containsKey(patternPair.getKey().coinPair())) {
      //logger.warn(String.format("Skipping chart pattern signal %s not in supported symbols at the moment.", patternPair.getKey().toString()));
      return;
    }
    ChartPatternSignal chartPatternSignal = patternPair.getKey();
    int attemptCount = patternPair.getValue();
    requestCount++;
    // Not have time to implement a real rolling window. Using half the limit because I also have the price to
    // determine at target time, run from a different scheduled task.
    if (requestCount % (REQUEST_WEIGHT_1_MIN_LIMIT / 2) == 0) {
      Thread.sleep(60000);
    }
    long currTime = clock.millis();
    long startTimeWindow = getStartTimeWindow(chartPatternSignal);
    long endTimeWindow = startTimeWindow + TIME_RANGE_AGG_TRADES * attemptCount;

    boolean windowAtCurrTimeItself = false;
    if (endTimeWindow >= currTime) {
      windowAtCurrTimeItself = true;
    }
    List<AggTrade> tradesList = restClient.getAggTrades(chartPatternSignal.coinPair(), null, 1, startTimeWindow, endTimeWindow);

    if (!tradesList.isEmpty()) {
      double price = numberFormat.parse(tradesList.get(0).getPrice()).doubleValue();
      boolean ret = setTargetPrice(chartPatternSignal, price);
      logger.info("Set " + targetTimeTypeName() + " price for '" + chartPatternSignal.coinPair() + "' with time due at '" + dateFormat.format(startTimeWindow) + "' using api: aggTrades. Ret val=" + ret);
    }
    else {
      attemptCount++;
      logger.error(String.format("Could not get agg trades for '%s' for '%s' with end window at price target time %s, " +
              "after %d attempts. Api args used - window start time = %d and window end = %d",
          chartPatternSignal, targetTimeTypeName(), dateFormat.format(chartPatternSignal.priceTargetTime()),
          attemptCount - 1, startTimeWindow, endTimeWindow));
      if (attemptCount > MAX_WINDOW_MINS) {
        logger.error(String.format("Could not get agg trades for '%s' for '%s' even with 60 minute interval, marking as failed in DB.", chartPatternSignal.toString(), targetTimeTypeName()));
        markFailedToGetTargetPrice(chartPatternSignal);
      } else if (windowAtCurrTimeItself) {
        logger.error(String.format("As end window crosses current time, skipping it for it to be retried in future attempts.", chartPatternSignal.toString(), targetTimeTypeName()));
      } else {
        attemptedPatterns.add(Pair.of(chartPatternSignal, attemptCount));
      }
    }
  }

  protected abstract boolean setTargetPrice(ChartPatternSignal chartPatternSignal, double price);

  protected abstract void markFailedToGetTargetPrice(ChartPatternSignal chartPatternSignal);

  protected abstract long getStartTimeWindow(ChartPatternSignal chartPatternSignal);

  private String targetTimeTypeName() {
    return targetTimeType == TargetTimeType.TEN_CANDLESTICK? "Ten Candlestick time" : "Price target time";
  }
}
