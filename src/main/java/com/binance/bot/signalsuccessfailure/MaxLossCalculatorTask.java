package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Component
public class MaxLossCalculatorTask {
  private final ChartPatternSignalDaoImpl dao;
  private final BinanceApiRestClient binanceApiRestClient;
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final SupportedSymbolsInfo supportedSymbolsInfo;
  private final RequestCounter requestCounter;
  private static final int REQUEST_WEIGHT_1_MIN_LIMIT = 1200;

  @Component
  static class RequestCounter {
    int counter = 0;
  }

  @Autowired
  MaxLossCalculatorTask(ChartPatternSignalDaoImpl dao, BinanceApiClientFactory binanceApiClientFactory,
                        SupportedSymbolsInfo supportedSymbolsInfo, RequestCounter requestCounter) {
    this.dao = dao;
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
    this.supportedSymbolsInfo = supportedSymbolsInfo;
    this.requestCounter = requestCounter;
  }

  @Scheduled(fixedDelay = 600000)
  public void perform() throws ParseException, InterruptedException, IOException {
    HeartBeatChecker.logHeartBeat(getClass());
    List<ChartPatternSignal> chartPatternSignals = dao.getAllChartPatternsNeedingMaxLossCalculated();
    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      if (!supportedSymbolsInfo.getTradingActiveSymbols().containsKey(chartPatternSignal.coinPair())) {
        continue;
      }
      Pair<Double, Double> maxLossAndPercent = Pair.of(0.0, 0.0);
      long maxLossTime = 0;
      boolean isProfitTargetMet = false;
      long targetMetTime = 0;
      long signalTime = chartPatternSignal.timeOfSignal().getTime();
      long signalTargetTime = chartPatternSignal.priceTargetTime().getTime();
      boolean firstIteration = true;
      boolean isDone = false;
      Long fromId = null;
      while (!isDone) {
        if (requestCounter.counter ++ >= REQUEST_WEIGHT_1_MIN_LIMIT/2) {
          Thread.sleep(60000);
        }
        List<AggTrade> aggTrades = binanceApiRestClient.getAggTrades(
            chartPatternSignal.coinPair(), fromId == null? null : Long.toString(fromId), 1000,
            firstIteration ? signalTime : null, firstIteration? getToTime(signalTime, chartPatternSignal) : null);
        firstIteration = false;
        if (aggTrades.isEmpty()) {
          isDone = true;
        }
        for (AggTrade aggTrade: aggTrades) {
          if (aggTrade.getTradeTime() > signalTargetTime) {
            isDone = true;
            break;
          }
          Pair<Double, Double> newMaxLossAndPercent = getMaxLossAndPercent(maxLossAndPercent, chartPatternSignal, aggTrade);
          if (newMaxLossAndPercent.getFirst() > maxLossAndPercent.getFirst()) {
            maxLossTime = aggTrade.getTradeTime();
          }
          maxLossAndPercent = newMaxLossAndPercent;
          if (!isProfitTargetMet && isTargetMet(chartPatternSignal, aggTrade)) {
            isProfitTargetMet = true;
            targetMetTime = aggTrade.getTradeTime();
          }
        }
        if (!isDone) {
          // For the next batch's fromId.
          fromId = aggTrades.get(aggTrades.size() - 1).getAggregatedTradeId() + 1;
        }
      }
      ChartPatternSignal updatedChartPatternSignal = ChartPatternSignal.newBuilder()
          .copy(chartPatternSignal)
          .setMaxLoss(maxLossAndPercent.getFirst())
          .setMaxLossPercent(maxLossAndPercent.getSecond())
          .setMaxLossTime(maxLossTime > 0 ? new Date(maxLossTime) : null)
          .setIsPriceTargetMet(isProfitTargetMet)
          .setPriceTargetMetTime(targetMetTime > 0 ? new Date(targetMetTime) : null)
          .build();
      dao.updateMaxLossAndTargetMetValues(updatedChartPatternSignal);
    }
  }

  private Long getToTime(long signalTime, ChartPatternSignal chartPatternSignal) {
    return Math.min(signalTime + 3600000, chartPatternSignal.priceTargetTime().getTime());
  }

  private boolean isTargetMet(ChartPatternSignal chartPatternSignal, AggTrade aggTrade) throws ParseException {
    switch (chartPatternSignal.tradeType()) {
      case BUY:
        return numberFormat.parse(aggTrade.getPrice()).doubleValue() >= chartPatternSignal.priceTarget();
      default:
        return numberFormat.parse(aggTrade.getPrice()).doubleValue() <= chartPatternSignal.priceTarget();
    }
  }

  private Pair<Double, Double> getMaxLossAndPercent(Pair<Double, Double> maxPnLAndPercent,
                                                    ChartPatternSignal chartPatternSignal, AggTrade aggTrade) throws ParseException {
    double pnl, pnlPercent;
    double aggTradePrice = numberFormat.parse(aggTrade.getPrice()).doubleValue();
    switch (chartPatternSignal.tradeType()) {
      case BUY:
        pnl = chartPatternSignal.priceAtTimeOfSignal() - aggTradePrice;
        break;
      case SELL:
      default:
        pnl = aggTradePrice - chartPatternSignal.priceAtTimeOfSignal();
    }
    pnlPercent = pnl / chartPatternSignal.priceAtTimeOfSignal() * 100;
    if (maxPnLAndPercent.getFirst() < pnl) {
      return Pair.of(pnl, pnlPercent);
    }
    return maxPnLAndPercent;
  }
}
