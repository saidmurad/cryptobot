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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRule;
import org.mockito.junit.MockitoRule;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class MaxLossCalculatorTaskTest {
  @Rule
  public final MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock
  private ChartPatternSignalDaoImpl mockDao;
  @Mock private BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock private BinanceApiRestClient mockBinanceApiRestClient;
  @Mock private SupportedSymbolsInfo mockSupportedSymbolsInfo;
  private MaxLossCalculatorTask.RequestCounter requestCounter = new MaxLossCalculatorTask.RequestCounter();
  @Captor
  private ArgumentCaptor<ChartPatternSignal> chartPatternSignalArgumentCaptor;
  private MaxLossCalculatorTask maxLossCalculatorTask;
  private static final long SIGNAL_TIME = 1L;
  private static final long SIGNAL_TARGET_TIME = 100L;

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    maxLossCalculatorTask = new MaxLossCalculatorTask(
        mockDao, mockBinanceApiClientFactory, mockSupportedSymbolsInfo, requestCounter);
    when(mockDao.getAllChartPatternsNeedingMaxLossCalculated()).thenReturn(Lists.newArrayList(getChartPatternSignal().build()));
    when(mockSupportedSymbolsInfo.getTradingActiveSymbols()).thenReturn(Map.of("ETHUSDT", Lists.newArrayList()));
  }

  @Test
  public void testSymbolNotTrading_skips() throws ParseException, InterruptedException {
    when(mockDao.getAllChartPatternsNeedingMaxLossCalculated()).thenReturn(Lists.newArrayList(
        getChartPatternSignal()
            .setCoinPair("BTCUSDT")
            .build()));

    maxLossCalculatorTask.perform();

    verify(mockBinanceApiRestClient, never()).getAggTrades(any(), any(), any(), any(), any());
  }

  @Test
  public void testPerform_noTradesReturned_exitsLoop() throws ParseException, InterruptedException {
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1000, SIGNAL_TIME, SIGNAL_TARGET_TIME))
        .thenReturn(Lists.newArrayList());

    maxLossCalculatorTask.perform();

    verify(mockDao).updateMaxLossAndTargetMetValues(chartPatternSignalArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLoss()).isEqualTo(0.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossTime()).isNull();
    assertThat(chartPatternSignalArgumentCaptor.getValue().isPriceTargetMet()).isFalse();
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetMetTime()).isNull();
  }

  @Test
  public void testPerform_endTime_whenNotCappedBySignalTargetTime() throws ParseException, InterruptedException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setPriceTargetTime(new Date(SIGNAL_TIME + 3600001))
        .build();
    when(mockDao.getAllChartPatternsNeedingMaxLossCalculated()).thenReturn(Lists.newArrayList(chartPatternSignal));
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1000, SIGNAL_TIME, SIGNAL_TIME + 3600000))
        .thenReturn(Lists.newArrayList());

    maxLossCalculatorTask.perform();

    verify(mockBinanceApiRestClient).getAggTrades("ETHUSDT", null, 1000, SIGNAL_TIME, SIGNAL_TIME + 3600000);
  }

  @Test
  public void testPerform_tradeBeyondSignalTargetTime_exitsLoop() throws ParseException, InterruptedException {
    AggTrade aggTrade = new AggTrade();
    aggTrade.setTradeTime(SIGNAL_TARGET_TIME + 1);
    aggTrade.setPrice("1.0");
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1000, SIGNAL_TIME, SIGNAL_TARGET_TIME))
        .thenReturn(Lists.newArrayList());

    maxLossCalculatorTask.perform();

    verify(mockDao).updateMaxLossAndTargetMetValues(chartPatternSignalArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLoss()).isEqualTo(0.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossTime()).isNull();
    assertThat(chartPatternSignalArgumentCaptor.getValue().isPriceTargetMet()).isFalse();
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetMetTime()).isNull();
  }

  @Test
  public void testPerform_requestCounter() throws ParseException, InterruptedException {
    AggTrade aggTrade = new AggTrade();
    aggTrade.setTradeTime(SIGNAL_TARGET_TIME + 1);
    aggTrade.setPrice("1.0");
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1000, SIGNAL_TIME, SIGNAL_TARGET_TIME))
        .thenReturn(Lists.newArrayList());

    maxLossCalculatorTask.perform();

    assertThat(requestCounter.counter).isEqualTo(1);
  }

  @Test
  public void testPerform_maxLoss_usingBuyTrade_tradeIdIncrementInRequest() throws ParseException, InterruptedException {
    List<AggTrade> aggTrades = new ArrayList<>();
    AggTrade aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(1);
    aggTrade.setTradeTime(SIGNAL_TIME + 1);
    aggTrade.setPrice("3500");
    aggTrades.add(aggTrade);
    aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(2);
    aggTrade.setTradeTime(SIGNAL_TIME + 2);
    aggTrade.setPrice("3000");
    aggTrades.add(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades(eq("ETHUSDT"), any(), eq(1000), any(), any()))
        .thenAnswer(invocation-> {
          String fromId = invocation.getArgument(1);
          Long startTime = invocation.getArgument(3);
          Long endTime = invocation.getArgument(4);
          if (fromId == null && startTime == SIGNAL_TIME && endTime == SIGNAL_TARGET_TIME) {
            return aggTrades;
          }
          if (fromId != null && fromId.equals("3") && startTime == null && endTime == null) {
            return Lists.newArrayList();
          }
          throw new RuntimeException("Unexpected arguments");
        });

    maxLossCalculatorTask.perform();

    verify(mockDao).updateMaxLossAndTargetMetValues(chartPatternSignalArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLoss()).isEqualTo(1000.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossPercent()).isEqualTo(25.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossTime().getTime()).isEqualTo(SIGNAL_TIME + 2);
    assertThat(chartPatternSignalArgumentCaptor.getValue().isPriceTargetMet()).isFalse();
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetMetTime()).isNull();
  }

  @Test
  public void testPerform_maxLoss_usingSellTrade_tradeIdIncrementInRequest() throws ParseException, InterruptedException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTradeType(TradeType.SELL)
        .setPriceTarget(3000.0)
        .build();
    when(mockDao.getAllChartPatternsNeedingMaxLossCalculated()).thenReturn(Lists.newArrayList(chartPatternSignal));
    List<AggTrade> aggTrades = new ArrayList<>();
    AggTrade aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(1);
    aggTrade.setTradeTime(SIGNAL_TIME + 1);
    aggTrade.setPrice("4500");
    aggTrades.add(aggTrade);
    aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(2);
    aggTrade.setTradeTime(SIGNAL_TIME + 2);
    aggTrade.setPrice("5000");
    aggTrades.add(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades(eq("ETHUSDT"), any(), eq(1000), any(), any()))
        .thenAnswer(invocation-> {
          String fromId = invocation.getArgument(1);
          Long startTime = invocation.getArgument(3);
          Long endTime = invocation.getArgument(4);
          if (fromId == null && startTime == SIGNAL_TIME && endTime == SIGNAL_TARGET_TIME) {
            return aggTrades;
          }
          if (fromId != null && fromId.equals("3") && startTime == null && endTime == null) {
            return Lists.newArrayList();
          }
          throw new RuntimeException("Unexpected arguments");
        });

    maxLossCalculatorTask.perform();

    verify(mockDao).updateMaxLossAndTargetMetValues(chartPatternSignalArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLoss()).isEqualTo(1000.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossPercent()).isEqualTo(25.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossTime().getTime()).isEqualTo(SIGNAL_TIME + 2);
    assertThat(chartPatternSignalArgumentCaptor.getValue().isPriceTargetMet()).isFalse();
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetMetTime()).isNull();
  }

  @Test
  public void testPerform_data_from_two_iterations_fed_in_correctly() throws ParseException, InterruptedException {
    when(mockBinanceApiRestClient.getAggTrades(eq("ETHUSDT"), any(), eq(1000), any(), any()))
        .thenAnswer(invocation-> {
          String fromId = invocation.getArgument(1);
          Long startTime = invocation.getArgument(3);
          Long endTime = invocation.getArgument(4);
          if (fromId == null && startTime == SIGNAL_TIME && endTime == SIGNAL_TARGET_TIME) {
            AggTrade aggTrade = new AggTrade();
            aggTrade.setAggregatedTradeId(1);
            aggTrade.setTradeTime(SIGNAL_TIME + 1);
            aggTrade.setPrice("3500");
            return Lists.newArrayList(aggTrade);
          }
          if (fromId != null && fromId.equals("2") && startTime == null && endTime == null) {
            AggTrade aggTrade = new AggTrade();
            aggTrade.setAggregatedTradeId(2);
            aggTrade.setTradeTime(SIGNAL_TIME + 2);
            aggTrade.setPrice("3000");
            return Lists.newArrayList(aggTrade);
          }
          if (fromId != null && fromId.equals("3") && startTime == null && endTime == null) {
            return Lists.newArrayList();
          }
          throw new RuntimeException("Unexpected arguments");
        });

    maxLossCalculatorTask.perform();

    verify(mockDao).updateMaxLossAndTargetMetValues(chartPatternSignalArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLoss()).isEqualTo(1000.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossPercent()).isEqualTo(25.0);
    assertThat(chartPatternSignalArgumentCaptor.getValue().maxLossTime().getTime()).isEqualTo(SIGNAL_TIME + 2);
    assertThat(chartPatternSignalArgumentCaptor.getValue().isPriceTargetMet()).isFalse();
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetMetTime()).isNull();
  }

  @Test
  public void testPerform_isPriceTargetMet() throws ParseException, InterruptedException {
    List<AggTrade> aggTrades = new ArrayList<>();
    AggTrade aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(1);
    aggTrade.setTradeTime(SIGNAL_TIME + 1);
    aggTrade.setPrice("6000");
    aggTrades.add(aggTrade);
    aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(2);
    aggTrade.setTradeTime(SIGNAL_TIME + 2);
    aggTrade.setPrice("4999");
    aggTrades.add(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades(eq("ETHUSDT"), any(), eq(1000), any(), any()))
        .thenAnswer(invocation-> {
          String fromId = invocation.getArgument(1);
          Long startTime = invocation.getArgument(3);
          Long endTime = invocation.getArgument(4);
          if (fromId == null && startTime == SIGNAL_TIME && endTime == SIGNAL_TARGET_TIME) {
            return aggTrades;
          }
          if (fromId != null && fromId.equals("3") && startTime == null && endTime == null) {
            return Lists.newArrayList();
          }
          throw new RuntimeException("Unexpected arguments");
        });

    maxLossCalculatorTask.perform();

    verify(mockDao).updateMaxLossAndTargetMetValues(chartPatternSignalArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().isPriceTargetMet()).isTrue();
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetMetTime().getTime()).isEqualTo(SIGNAL_TIME + 1);
  }

  @Test
  public void testPerform_isPriceTargetNotMet() throws ParseException, InterruptedException {
    List<AggTrade> aggTrades = new ArrayList<>();
    AggTrade aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(1);
    aggTrade.setTradeTime(SIGNAL_TIME + 1);
    aggTrade.setPrice("5000");
    aggTrades.add(aggTrade);
    aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(2);
    aggTrade.setTradeTime(SIGNAL_TIME + 2);
    aggTrade.setPrice("4999");
    aggTrades.add(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades(eq("ETHUSDT"), any(), eq(1000), any(), any()))
        .thenAnswer(invocation-> {
          String fromId = invocation.getArgument(1);
          Long startTime = invocation.getArgument(3);
          Long endTime = invocation.getArgument(4);
          if (fromId == null && startTime == SIGNAL_TIME && endTime == SIGNAL_TARGET_TIME) {
            return aggTrades;
          }
          if (fromId != null && fromId.equals("3") && startTime == null && endTime == null) {
            return Lists.newArrayList();
          }
          throw new RuntimeException("Unexpected arguments");
        });

    maxLossCalculatorTask.perform();

    verify(mockDao).updateMaxLossAndTargetMetValues(chartPatternSignalArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().isPriceTargetMet()).isFalse();
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetMetTime()).isNull();
  }

  @Test
  public void testPerform_isPriceTargetMet_sellTrade_yes() throws ParseException, InterruptedException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTradeType(TradeType.SELL)
        .setPriceTarget(3000.0)
        .build();
    when(mockDao.getAllChartPatternsNeedingMaxLossCalculated()).thenReturn(Lists.newArrayList(chartPatternSignal));
    List<AggTrade> aggTrades = new ArrayList<>();
    AggTrade aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(1);
    aggTrade.setTradeTime(SIGNAL_TIME + 1);
    aggTrade.setPrice("2999");
    aggTrades.add(aggTrade);
    aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(2);
    aggTrade.setTradeTime(SIGNAL_TIME + 2);
    aggTrade.setPrice("3001");
    aggTrades.add(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades(eq("ETHUSDT"), any(), eq(1000), any(), any()))
        .thenAnswer(invocation-> {
          String fromId = invocation.getArgument(1);
          Long startTime = invocation.getArgument(3);
          Long endTime = invocation.getArgument(4);
          if (fromId == null && startTime == SIGNAL_TIME && endTime == SIGNAL_TARGET_TIME) {
            return aggTrades;
          }
          if (fromId != null && fromId.equals("3") && startTime == null && endTime == null) {
            return Lists.newArrayList();
          }
          throw new RuntimeException("Unexpected arguments");
        });

    maxLossCalculatorTask.perform();

    verify(mockDao).updateMaxLossAndTargetMetValues(chartPatternSignalArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().isPriceTargetMet()).isTrue();
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetMetTime().getTime()).isEqualTo(SIGNAL_TIME + 1);
  }

  @Test
  public void testPerform_isPriceTargetMet_sellTrade_no() throws ParseException, InterruptedException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTradeType(TradeType.SELL)
        .setPriceTarget(3000.0)
        .build();
    when(mockDao.getAllChartPatternsNeedingMaxLossCalculated()).thenReturn(Lists.newArrayList(chartPatternSignal));
    List<AggTrade> aggTrades = new ArrayList<>();
    AggTrade aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(1);
    aggTrade.setTradeTime(SIGNAL_TIME + 1);
    aggTrade.setPrice("3002");
    aggTrades.add(aggTrade);
    aggTrade = new AggTrade();
    aggTrade.setAggregatedTradeId(2);
    aggTrade.setTradeTime(SIGNAL_TIME + 2);
    aggTrade.setPrice("3001");
    aggTrades.add(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades(eq("ETHUSDT"), any(), eq(1000), any(), any()))
        .thenAnswer(invocation-> {
          String fromId = invocation.getArgument(1);
          Long startTime = invocation.getArgument(3);
          Long endTime = invocation.getArgument(4);
          if (fromId == null && startTime == SIGNAL_TIME && endTime == SIGNAL_TARGET_TIME) {
            return aggTrades;
          }
          if (fromId != null && fromId.equals("3") && startTime == null && endTime == null) {
            return Lists.newArrayList();
          }
          throw new RuntimeException("Unexpected arguments");
        });

    maxLossCalculatorTask.perform();

    verify(mockDao).updateMaxLossAndTargetMetValues(chartPatternSignalArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().isPriceTargetMet()).isFalse();
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetMetTime()).isNull();
  }

  private ChartPatternSignal.Builder getChartPatternSignal() {
    long currentTimeMillis = System.currentTimeMillis();
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setTimeOfSignal(new Date(SIGNAL_TIME))
        .setTimeOfInsertion(new Date(currentTimeMillis))
        .setIsInsertedLate(false)
        .setPriceTarget(6000)
        .setPriceTargetTime(new Date(SIGNAL_TARGET_TIME))
        .setProfitPotentialPercent(2.3)
        .setIsSignalOn(true);
  }

}