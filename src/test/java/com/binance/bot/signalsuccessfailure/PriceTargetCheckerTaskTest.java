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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.binance.bot.signalsuccessfailure.PriceTargetCheckerTask.TIME_RANGE_AGG_TRADES;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class PriceTargetCheckerTaskTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock
  private BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock private BinanceApiRestClient mockBinanceApiRestClient;
  @Mock private ChartPatternSignalDaoImpl mockChartPatternSignalDaoImpl;
  @Mock private SupportedSymbolsInfo mockSupportedSymbolsInfo;
  private Date currentTime = new Date();

  private PriceTargetCheckerTask priceTargetCheckerTask;

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    when(mockSupportedSymbolsInfo.getSupportedSymbols()).thenReturn(Map.of("ETHUSDT", Lists.newArrayList()));
    priceTargetCheckerTask = new PriceTargetCheckerTask(
        mockBinanceApiClientFactory, mockChartPatternSignalDaoImpl, mockSupportedSymbolsInfo);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_fifteenMinutes() throws InterruptedException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.FIFTEEN_MINUTES).build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    long tenCandleStickTime = currentTime.getTime() + TimeUnit.MINUTES.toMillis(150);
    AggTrade aggTrade = new AggTrade();
    aggTrade.setPrice("6000");
    List<AggTrade> tradesList = Lists.newArrayList(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1, tenCandleStickTime, tenCandleStickTime + TIME_RANGE_AGG_TRADES))
        .thenReturn(tradesList);

    priceTargetCheckerTask.performPriceTargetChecks();

    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(chartPatternSignal, 6000, 50.0);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_fifteenMinutes_tradeTypeSell() throws InterruptedException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.SELL)
        .build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    long tenCandleStickTime = currentTime.getTime() + TimeUnit.MINUTES.toMillis(150);
    AggTrade aggTrade = new AggTrade();
    aggTrade.setPrice("6000");
    List<AggTrade> tradesList = Lists.newArrayList(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1, tenCandleStickTime, tenCandleStickTime + TIME_RANGE_AGG_TRADES))
        .thenReturn(tradesList);

    priceTargetCheckerTask.performPriceTargetChecks();

    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(chartPatternSignal, 6000, -50.0);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_hour() throws InterruptedException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.HOUR).build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    long tenCandleStickTime = currentTime.getTime() + TimeUnit.HOURS.toMillis(10);
    AggTrade aggTrade = new AggTrade();
    aggTrade.setPrice("6000");
    List<AggTrade> tradesList = Lists.newArrayList(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1, tenCandleStickTime, tenCandleStickTime + TIME_RANGE_AGG_TRADES))
        .thenReturn(tradesList);

    priceTargetCheckerTask.performPriceTargetChecks();

    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(chartPatternSignal, 6000, 50.0);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_4hour() throws InterruptedException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.FOUR_HOURS).build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    long tenCandleStickTime = currentTime.getTime() + TimeUnit.HOURS.toMillis(40);
    AggTrade aggTrade = new AggTrade();
    aggTrade.setPrice("6000");
    List<AggTrade> tradesList = Lists.newArrayList(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1, tenCandleStickTime, tenCandleStickTime + TIME_RANGE_AGG_TRADES))
        .thenReturn(tradesList);

    priceTargetCheckerTask.performPriceTargetChecks();

    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(chartPatternSignal, 6000, 50.0);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_day() throws InterruptedException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.DAY).build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    long tenCandleStickTime = currentTime.getTime() + TimeUnit.DAYS.toMillis(10);
    AggTrade aggTrade = new AggTrade();
    aggTrade.setPrice("6000");
    List<AggTrade> tradesList = Lists.newArrayList(aggTrade);
    when(mockBinanceApiRestClient.getAggTrades("ETHUSDT", null, 1, tenCandleStickTime, tenCandleStickTime + TIME_RANGE_AGG_TRADES))
        .thenReturn(tradesList);

    priceTargetCheckerTask.performPriceTargetChecks();

    // TODO: Fix the stupid error happening below.
    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(chartPatternSignal, 6000, 50.0);
  }

  private ChartPatternSignal.Builder getChartPatternSignal(TimeFrame timeFrame) {
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(timeFrame)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setTimeOfSignal(currentTime)
        .setPriceTargetTime(currentTime)
        .setPriceTarget(6000)
        .setProfitPotentialPercent(2.3);
  }
}