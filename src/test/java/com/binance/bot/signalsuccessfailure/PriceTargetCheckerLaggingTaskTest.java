package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.SupportedSymbolsInfo;
import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.text.ParseException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class PriceTargetCheckerLaggingTaskTest {
  @Rule
  public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock
  private ChartPatternSignalDaoImpl mockDao;
  @Mock private BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock private BinanceApiRestClient mockBinanceApiRestClient;
  @Mock private Clock mockClock;
  @Mock private SupportedSymbolsInfo mockSupportedSymbolsInfo;
  private final long timeOfSignal = 1640995200000L; // 01/01/2022 0:00 GMT
  private PriceTargetCheckerLaggingTask priceTargetCheckerLaggingTask;

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    priceTargetCheckerLaggingTask = new PriceTenCandlestickTimeCheckerLaggingTask(
        mockBinanceApiClientFactory, mockDao, mockSupportedSymbolsInfo);
    priceTargetCheckerLaggingTask.clock = mockClock;
    when(mockSupportedSymbolsInfo.getTradingActiveSymbols()).thenReturn(Map.of("ETHUSDT", Lists.newArrayList()),
        Map.of("BTCUSDT", Lists.newArrayList()));
  }

  @Test
  public void testPerformIteration_symbolNotInSupportedSymbolsInfo_skipped() throws ParseException, InterruptedException, IOException {
    ChartPatternSignal pattern = getChartPatternSignal().setCoinPair("ALGOUSDT").build();
    ArrayList<Pair<ChartPatternSignal, Integer>> patternsQueue = Lists.newArrayList(Pair.of(pattern, 1));

    priceTargetCheckerLaggingTask.performIteration(patternsQueue);

    assertThat(patternsQueue).isEmpty();
  }

  @Test
  public void testPerformIteration_tenCandlestickTimePriceReturnedOnFirstAttempt() throws ParseException, InterruptedException, IOException {
    ChartPatternSignal pattern = getChartPatternSignal().setCoinPair("ETHUSDT").build();
    when(mockClock.millis()).thenReturn(timeOfSignal + 152 * 60000);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1,
        // Ten candle window is at 2.5 hours, and we get agg trades using a minute window at first.
        timeOfSignal + 150 * 60000, timeOfSignal + 151 * 60000))
        .thenReturn(getAggTrades(4001.23));
    ArrayList<Pair<ChartPatternSignal, Integer>> patternsQueue = Lists.newArrayList(Pair.of(pattern, 1));

    priceTargetCheckerLaggingTask.performIteration(patternsQueue);

    verify(mockDao).setTenCandleStickTimePrice(pattern, 4001.23, 0.030750000000000454);
    assertThat(patternsQueue).isEmpty();
  }

  @Test
  public void testPerformIteration_noAggTradesReturned_requeuedWithBumpedAttemptCount() throws ParseException, InterruptedException, IOException {
    ChartPatternSignal pattern = getChartPatternSignal().setCoinPair("ETHUSDT").build();
    when(mockClock.millis()).thenReturn(timeOfSignal + 152 * 60000);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1,
        // Ten candle window is at 2.5 hours, and we get agg trades using a minute window at first.
        timeOfSignal + 150 * 60000, timeOfSignal + 151 * 60000))
        .thenReturn(Lists.newArrayList());
    ArrayList<Pair<ChartPatternSignal, Integer>> patternsQueue = Lists.newArrayList(Pair.of(pattern, 1));

    priceTargetCheckerLaggingTask.performIteration(patternsQueue);

    verifyNoInteractions(mockDao);
    assertThat(patternsQueue).hasSize(1);
    assertThat(patternsQueue.get(0).getValue()).isEqualTo(2);
  }

  @Test
  public void testPerformIteration_noAggTradesReturned_requeuedWithBumpedAttemptCount_nextEntryFrontOfQueue() throws ParseException, InterruptedException, IOException {
    ChartPatternSignal pattern = getChartPatternSignal().setCoinPair("ETHUSDT").build();
    when(mockClock.millis()).thenReturn(timeOfSignal + 152 * 60000);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1,
        // Ten candle window is at 2.5 hours, and we get agg trades using a minute window at first.
        timeOfSignal + 150 * 60000, timeOfSignal + 151 * 60000))
        .thenReturn(Lists.newArrayList());
    ChartPatternSignal pattern2 = getChartPatternSignal().setCoinPair("BTCUSDT").build();
    ArrayList<Pair<ChartPatternSignal, Integer>> patternsQueue = Lists.newArrayList(
        Pair.of(pattern, 1), Pair.of(pattern2, 1));

    priceTargetCheckerLaggingTask.performIteration(patternsQueue);

    verifyNoInteractions(mockDao);
    assertThat(patternsQueue).hasSize(2);
    assertThat(patternsQueue.get(0).getKey()).isEqualTo(pattern2);
    assertThat(patternsQueue.get(0).getValue()).isEqualTo(1);
    assertThat(patternsQueue.get(1).getKey()).isEqualTo(pattern);
    assertThat(patternsQueue.get(1).getValue()).isEqualTo(2);
  }

  @Test
  public void testPerformIteration_startTimeCappedToCurrTime_notRequeuedIfNoAggTrades() throws ParseException, InterruptedException, IOException {
    ChartPatternSignal pattern = getChartPatternSignal().setCoinPair("ETHUSDT").build();
    long tenCandlestickTime = timeOfSignal + 150 * 60000;
    long currTime = tenCandlestickTime - 60000;
    when(mockClock.millis()).thenReturn(currTime);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1,
        // Ten candle window is at 2.5 hours, and we get agg trades using a minute window at first.
        currTime, currTime + 60000))
        .thenReturn(Lists.newArrayList());
    ArrayList<Pair<ChartPatternSignal, Integer>> patternsQueue = Lists.newArrayList(Pair.of(pattern, 1));

    priceTargetCheckerLaggingTask.performIteration(patternsQueue);

    verifyNoInteractions(mockDao);
    assertThat(patternsQueue).isEmpty();
  }

  @Test
  // In this test requeue occurring is independent of the start time getting capped. Requeue or not is determined
  // by the window end time crossing the current time alone.
  public void testPerformIteration_startTimeCappedToSignalTargetTime() throws ParseException, InterruptedException, IOException {
    long tenCandlestickTime = timeOfSignal + 150 * 60000;
    long priceTargetTime = tenCandlestickTime - 60000;
    long currTime = timeOfSignal + 300 * 60000;
    ChartPatternSignal pattern = getChartPatternSignal().setCoinPair("ETHUSDT")
        .setPriceTargetTime(new Date(priceTargetTime))
        .build();
    when(mockClock.millis()).thenReturn(currTime);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1,
        // Ten candle window is at 2.5 hours, and we get agg trades using a minute window at first.
        priceTargetTime, priceTargetTime + 60000))
        .thenReturn(Lists.newArrayList());
    ArrayList<Pair<ChartPatternSignal, Integer>> patternsQueue = Lists.newArrayList(Pair.of(pattern, 1));

    priceTargetCheckerLaggingTask.performIteration(patternsQueue);

    verifyNoInteractions(mockDao);
    assertThat(patternsQueue).hasSize(1);
    assertThat(patternsQueue.get(0).getValue()).isEqualTo(2);
  }

  @Test
  public void testPerformIteration_noAggTradesReturned_maxAttemptsReached_notRequeued() throws ParseException, InterruptedException, IOException {
    ChartPatternSignal pattern = getChartPatternSignal().setCoinPair("ETHUSDT").build();
    when(mockClock.millis()).thenReturn(timeOfSignal + 152 * 60000);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1,
        // Ten candle window is at 2.5 hours, and we get agg trades using a minute window at first.
        timeOfSignal + 150 * 60000, timeOfSignal + 151 * 60000))
        .thenReturn(Lists.newArrayList());
    ArrayList<Pair<ChartPatternSignal, Integer>> patternsQueue = Lists.newArrayList(Pair.of(pattern, 60));

    priceTargetCheckerLaggingTask.performIteration(patternsQueue);

    verify(mockDao).failedToGetPriceAtTenCandlestickTime(pattern);
    assertThat(patternsQueue).isEmpty();
  }

  private List<AggTrade> getAggTrades(double tradePrice) {
    AggTrade aggTrade = new AggTrade();
    aggTrade.setPrice(Double.toString(tradePrice));
    return Lists.newArrayList(aggTrade);
  }

  private ChartPatternSignal.Builder getChartPatternSignal() {
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setTimeOfSignal(new Date(timeOfSignal))
        .setTimeOfInsertion(new Date(timeOfSignal))
        .setIsInsertedLate(false)
        .setPriceTarget(6000)
        .setPriceTargetTime(new Date(timeOfSignal + 200 * 60000))
        .setProfitPotentialPercent(2.3)
        .setIsSignalOn(true);
  }
}