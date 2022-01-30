package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Date;

public class PriceTargetCheckerLaggingTaskTest extends TestCase {
  @Rule
  public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock
  private ChartPatternSignalDaoImpl mockDao;
  @Mock private BinanceApiClientFactory binanceApiClientFactory;

  private final PriceTargetCheckerLaggingTask priceTargetCheckerLaggingTask = new PriceTargetCheckerLaggingTask(binanceApiClientFactory, mockDao);
  public void testPerformIteration_roundRobin() {
    ChartPatternSignal pattern1 = getChartPatternSignal().setCoinPair("ETHUSDT").build();
    ChartPatternSignal pattern2 = getChartPatternSignal().setCoinPair("BTCUSDT").build();


  }

  private ChartPatternSignal.Builder getChartPatternSignal() {
    long currentTimeMillis = System.currentTimeMillis();
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setTimeOfSignal(new Date(currentTimeMillis))
        .setTimeOfInsertion(new Date(currentTimeMillis))
        .setIsInsertedLate(false)
        .setPriceTarget(6000)
        .setPriceTargetTime(new Date(currentTimeMillis + 360000))
        .setProfitPotentialPercent(2.3)
        .setIsSignalOn(true);
  }
}