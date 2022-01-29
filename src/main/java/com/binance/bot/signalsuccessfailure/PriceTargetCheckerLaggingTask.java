package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static com.binance.bot.common.Util.getProfitPercentAtTenCandlestickTime;
import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

public class PriceTargetCheckerLaggingTask {

  enum TargetTimeType {
    TEN_CANDLESTICK,
    END
  }
  private TargetTimeType targetTimeType;
  static final long TIME_RANGE_AGG_TRADES = 60000;
  private final BinanceApiRestClient restClient;
  private ChartPatternSignalDaoImpl dao;
  private Logger logger = LoggerFactory.getLogger(getClass());

  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final static int MAX_WINDOW_MINS = 60;
  private final static NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  long requestCount = 0;
  @Autowired
  PriceTargetCheckerLaggingTask(BinanceApiClientFactory binanceApiClientFactory,
                                ChartPatternSignalDaoImpl dao) {
    restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  private static final int REQUEST_WEIGHT_1_MIN_LIMIT = 1200;

  public void setTargetTimeType(TargetTimeType targetTime){
    this.targetTimeType = targetTimeType;
  }

  // Caution: Not unit tested nor found worth the trouble.
  public void perform() throws InterruptedException, ParseException {
    List<ChartPatternSignal> patterns = dao.getChatPatternSignalsThatLongSinceReachedTenCandleStickTime();
    List<Pair<ChartPatternSignal, Integer>>  attemptedPatterns = new ArrayList<>();
    for (ChartPatternSignal pattern: patterns) {
      attemptedPatterns.add(Pair.of(pattern, 1));
    }
    while (!attemptedPatterns.isEmpty()) {
      performIteration(attemptedPatterns);
    }
  }

  void performIteration(List<Pair<ChartPatternSignal, Integer>> attemptedPatterns) throws InterruptedException, ParseException {
    Pair<ChartPatternSignal, Integer> patternPair = attemptedPatterns.remove(0);
    ChartPatternSignal chartPatternSignal = patternPair.getKey();
    int attemptCount = patternPair.getValue();
    requestCount++;
    // Not have time to implement a real rolling window. Minusing 100 to allow lax.
    if (requestCount % (REQUEST_WEIGHT_1_MIN_LIMIT - 100) == 0) {
      Thread.sleep(60000);
    }
    long timePointOfInterest = chartPatternSignal.timeOfSignal().getTime() + getTenCandleStickTimeIncrementMillis(chartPatternSignal);
    long endTimeWindow = timePointOfInterest + TIME_RANGE_AGG_TRADES * attemptCount;
    long currTime = System.currentTimeMillis();
    boolean maxWindowReached = false;
    if (endTimeWindow > currTime) {
      maxWindowReached = true;
      endTimeWindow = currTime;
    }
    List<AggTrade> tradesList = restClient.getAggTrades(chartPatternSignal.coinPair(), null, 1, timePointOfInterest, endTimeWindow);

    if (!tradesList.isEmpty()) {
      double tenCandleStickTimePrice = numberFormat.parse(tradesList.get(0).getPrice()).doubleValue();
      boolean ret = dao.setTenCandleStickTimePrice(chartPatternSignal, tenCandleStickTimePrice, getProfitPercentAtTenCandlestickTime(chartPatternSignal, tenCandleStickTimePrice));
      logger.info("Set " + targetTimeTypeName() + " price for '" + chartPatternSignal.coinPair() + "' with time due at '" + dateFormat.format(timePointOfInterest) + "' using api: aggTrades. Ret val=" + ret);
    }
    else {
      attemptCount++;
      if (attemptCount > MAX_WINDOW_MINS) {
        logger.error(String.format("Could not get agg trades for '%s' for '%s' even with 60 minute interval, marking as failed in DB.", chartPatternSignal.toString(), targetTimeTypeName()));
        dao.failedToGetPriceAtTenCandlestickTime(chartPatternSignal);
      } else if (maxWindowReached) {
        logger.error(String.format("Could not get agg trades for '%s' for '%s' even with end window at present time, marking as failed in DB.", chartPatternSignal.toString(), targetTimeTypeName()));
        dao.failedToGetPriceAtTenCandlestickTime(chartPatternSignal);
      }
      else {
        patternPair.setValue(attemptCount);
        attemptedPatterns.add(patternPair);
      }
    }

  }

  private String targetTimeTypeName() {
    return targetTimeType == TargetTimeType.TEN_CANDLESTICK? "Ten Candlestick time" : "Price target time";
  }
}
