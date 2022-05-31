package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.CandlestickUtil;
import com.binance.bot.common.Mailer;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.SupportedSymbolsInfo;
import com.gateiobot.GateIoClientFactory;
import com.gateiobot.db.MACDData;
import com.gateiobot.db.MACDDataDao;
import io.gate.gateapi.api.SpotApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

@Component
public class MaxLossCalculatorTask {
  private final ChartPatternSignalDaoImpl dao;
  private final SpotApi spotApi;
  private final SupportedSymbolsInfo supportedSymbolsInfo;
  private final BinanceApiRestClient binanceApiRestClient;
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private Mailer mailer = new Mailer();
  private static final int CANDLESTICK_INDEX_CLOSING_PRICE = 2;
  private static final int CANDLESTICK_INDEX_START_TIME = 0;
  int NUM_CANDLESTICKS_MINUS_ONE = 1;

  @Autowired
  MaxLossCalculatorTask(ChartPatternSignalDaoImpl dao, GateIoClientFactory gateIoClientFactory, BinanceApiClientFactory binanceApiClientFactory,
                        SupportedSymbolsInfo supportedSymbolsInfo) {
    this.dao = dao;
    spotApi = gateIoClientFactory.getSpotApi();
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
    this.supportedSymbolsInfo = supportedSymbolsInfo;
  }

  @Scheduled(fixedDelay = 600000, initialDelayString = "${timing.initialDelay}")
  public void perform() throws MessagingException {
    try {
      List<ChartPatternSignal> chartPatternSignals = dao.getAllChartPatternsNeedingMaxLossCalculated();
      logger.info(String.format("Found %d chart pattern signals those either don't have max loss  or  don't have preBreakoutCandlestickStopLossPrice and profit target set.",
          chartPatternSignals.size()));
      for (ChartPatternSignal chartPatternSignal : chartPatternSignals) {
        HeartBeatChecker.logHeartBeat(getClass());
        if (!supportedSymbolsInfo.getTradingActiveSymbols().containsKey(chartPatternSignal.coinPair())) {
          continue;
        }
        if (chartPatternSignal.maxLoss() == null || chartPatternSignal.lossTimesCalculated() == null) {
          Pair<Double, Double> maxLossAndPercent = Pair.of(0.0, 0.0);
          long maxLossTime = 0;
          Date stopLossTime = null;
          long twoPercentLossTime = 0;
          long fivePercentLossTime = 0;
          Map<Integer, Long> lossPercentageAndTimeMap = new HashMap<>();
          boolean isProfitTargetMet = false;
          long targetMetTime = 0;
          long signalTime = chartPatternSignal.timeOfSignal().getTime();
          long signalTargetTime = chartPatternSignal.priceTargetTime().getTime();
          boolean firstIteration = true;
          boolean isDone = false;
          Long fromId = null;
          long beginTime = System.currentTimeMillis();
          SpotApi.APIlistCandlesticksRequest req = spotApi.listCandlesticks(chartPatternSignal.coinPair());
          req = req.from(chartPatternSignal.timeOfSignal().getTime() / 1000);
          req = req.to(chartPatternSignal.priceTargetTime().getTime() / 1000);
          req = req.interval(getTimeInterval(chartPatternSignal.timeFrame()));
          List<List<String>> candlesticks = req.execute();
          Double preBreakoutCandlestickStopLossPrice = Double.parseDouble(candlesticks.get(0).get(CANDLESTICK_INDEX_CLOSING_PRICE));
          for (List<String> macdData : candlesticks) {
            if (stopLossTime == null && (chartPatternSignal.tradeType() == TradeType.BUY &&  Double.parseDouble(macdData.get(CANDLESTICK_INDEX_CLOSING_PRICE)) < preBreakoutCandlestickStopLossPrice
                    || chartPatternSignal.tradeType() == TradeType.SELL && Double.parseDouble(macdData.get(CANDLESTICK_INDEX_CLOSING_PRICE)) > preBreakoutCandlestickStopLossPrice)) {
              stopLossTime = new Date(Long.parseLong(macdData.get(CANDLESTICK_INDEX_START_TIME)) * 1000);
              break;
            }
          }
          while (!isDone) {
            // Heart beat every 5 minutes.
            if (((System.currentTimeMillis() - beginTime) / 60000) % 5 == 0) {
              HeartBeatChecker.logHeartBeat(getClass());
            }
            List<AggTrade> aggTrades = binanceApiRestClient.getAggTrades(
                    chartPatternSignal.coinPair(), fromId == null ? null : Long.toString(fromId), 1000,
                    firstIteration ? signalTime : null, firstIteration ? getToTime(signalTime, chartPatternSignal) : null);
            firstIteration = false;
            if (aggTrades.isEmpty()) {
              isDone = true;
            }
            for (AggTrade aggTrade : aggTrades) {
              if (aggTrade.getTradeTime() > signalTargetTime) {
                isDone = true;
                break;
              }
              Pair<Double, Double> newMaxLossAndPercent =
                      getMaxLossAndPercent(maxLossAndPercent, chartPatternSignal, aggTrade);
              Pair<Double, Double> pnlAndPercent = getPnlAndPercent(chartPatternSignal, aggTrade);
              if (newMaxLossAndPercent.getFirst() > maxLossAndPercent.getFirst()) {
                maxLossTime = aggTrade.getTradeTime();
              }
              maxLossAndPercent = newMaxLossAndPercent;
              if (pnlAndPercent.getSecond() >= 2 && twoPercentLossTime == 0.0) {
                twoPercentLossTime = aggTrade.getTradeTime();
              }
              if (pnlAndPercent.getSecond() >= 5 && fivePercentLossTime == 0.0) {
                fivePercentLossTime = aggTrade.getTradeTime();
              }
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
        /*logger.info(String.format("Getting all aggTrades took %d seconds.",
            (System.currentTimeMillis() - beginTime) / 1000));*/
          ChartPatternSignal updatedChartPatternSignal = ChartPatternSignal.newBuilder()
                  .copy(chartPatternSignal)
                  .setMaxLoss(maxLossAndPercent.getFirst())
                  .setMaxLossPercent(maxLossAndPercent.getSecond())
                  .setMaxLossTime(maxLossTime > 0 ? new Date(maxLossTime) : null)
                  .setStopLossTime(stopLossTime)
                  .setTwoPercentLossTime(twoPercentLossTime > 0 ? new Date(twoPercentLossTime) : null)
                  .setFivePercentLossTime(fivePercentLossTime > 0 ? new Date(fivePercentLossTime) : null)
                  .setLossTimesCalculated(true)
                  .setPreBreakoutCandlestickStopLossPrice(preBreakoutCandlestickStopLossPrice)
                  .setIsPriceTargetMet(isProfitTargetMet)
                  .setPriceTargetMetTime(targetMetTime > 0 ? new Date(targetMetTime) : null)
                  .build();
          dao.updateMaxLossAndTargetMetValues(updatedChartPatternSignal);
        }
        if(chartPatternSignal.preBreakoutCandlestickStopLossPrice() == null) {
          SpotApi.APIlistCandlesticksRequest req = spotApi.listCandlesticks(chartPatternSignal.coinPair());
          Date candlestickFromTime = CandlestickUtil.getIthCandlestickTime(chartPatternSignal.timeOfSignal(), chartPatternSignal.timeFrame(), -NUM_CANDLESTICKS_MINUS_ONE);
          req = req.from(candlestickFromTime.getTime() / 1000);
          req = req.to(chartPatternSignal.timeOfSignal().getTime() / 1000);
          req = req.interval(getTimeInterval(chartPatternSignal.timeFrame()));
          List<List<String>> candlesticks = req.execute();
          Double preBreakoutCandlestickStopLossPrice = Double.parseDouble(candlesticks.get(0).get(CANDLESTICK_INDEX_CLOSING_PRICE));
          ChartPatternSignal updatedChartPatternSignal = ChartPatternSignal.newBuilder()
                  .copy(chartPatternSignal)
                  .setPreBreakoutCandlestickStopLossPrice(preBreakoutCandlestickStopLossPrice)
                  .build();
          dao.updatePreBreakoutCandlestickStopLossPrice(updatedChartPatternSignal);
        }
      }
    } catch (Exception ex) {
      logger.error("Exception.", ex);
      mailer.sendEmail("Exception in MaxLossCalculatorTask.", ex.getMessage() != null? ex.getMessage() : ex.getClass().getName());
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
    Pair<Double, Double> pnlAndPercent = getPnlAndPercent(chartPatternSignal, aggTrade);
    if (maxPnLAndPercent.getFirst() < pnlAndPercent.getFirst()) {
      return pnlAndPercent;
    }
    return maxPnLAndPercent;
  }

  private Pair<Double, Double> getPnlAndPercent(ChartPatternSignal chartPatternSignal, AggTrade aggTrade) throws ParseException {
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

    return Pair.of(pnl, pnlPercent);
  }

  private String getTimeInterval(TimeFrame timeFrame) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return "15m";
      case HOUR:
        return "1h";
      case FOUR_HOURS:
        return "4h";
      case DAY:
      default:
        return "1d";
    }
  }
}
