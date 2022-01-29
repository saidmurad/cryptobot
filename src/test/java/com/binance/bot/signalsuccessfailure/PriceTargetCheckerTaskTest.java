package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.AggTrade;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.SupportedSymbolsInfo;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.text.ParseException;
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
  public void testPerformPriceTargetChecks_timeFrame_fifteenMinutes() throws ParseException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.FIFTEEN_MINUTES).build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatJustReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    TickerPrice tickerPrice = new TickerPrice();
    tickerPrice.setPrice("6000");
    when(mockBinanceApiRestClient.getPrice("ETHUSDT")).thenReturn(tickerPrice);

    priceTargetCheckerTask.performPriceTargetChecks();

    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(chartPatternSignal, 6000, 50.0);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_fifteenMinutes_tradeTypeSell() throws ParseException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.SELL)
        .build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatJustReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    TickerPrice tickerPrice = new TickerPrice();
    tickerPrice.setPrice("6000");
    when(mockBinanceApiRestClient.getPrice("ETHUSDT")).thenReturn(tickerPrice);

    priceTargetCheckerTask.performPriceTargetChecks();

    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(chartPatternSignal, 6000, -50.0);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_hour() throws ParseException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.HOUR).build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatJustReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    TickerPrice tickerPrice = new TickerPrice();
    tickerPrice.setPrice("6000");
    when(mockBinanceApiRestClient.getPrice("ETHUSDT")).thenReturn(tickerPrice);

    priceTargetCheckerTask.performPriceTargetChecks();

    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(chartPatternSignal, 6000, 50.0);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_4hour() throws ParseException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.FOUR_HOURS).build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatJustReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    TickerPrice tickerPrice = new TickerPrice();
    tickerPrice.setPrice("6000");
    when(mockBinanceApiRestClient.getPrice("ETHUSDT")).thenReturn(tickerPrice);

    priceTargetCheckerTask.performPriceTargetChecks();

    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(chartPatternSignal, 6000, 50.0);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_day() throws InterruptedException, ParseException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal(TimeFrame.DAY).build();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatJustReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);
    TickerPrice tickerPrice = new TickerPrice();
    tickerPrice.setPrice("6000");
    when(mockBinanceApiRestClient.getPrice("ETHUSDT")).thenReturn(tickerPrice);

    priceTargetCheckerTask.performPriceTargetChecks();

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