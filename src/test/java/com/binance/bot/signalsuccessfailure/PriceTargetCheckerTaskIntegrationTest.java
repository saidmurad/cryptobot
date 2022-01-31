package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
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

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class PriceTargetCheckerTaskIntegrationTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private ChartPatternSignalDaoImpl mockChartPatternSignalDaoImpl;
  @Mock private SupportedSymbolsInfo mockSupportedSymbolsInfo;
  private Date currentTime = new Date();
  private PriceTargetCheckerTask priceTargetCheckerTask;

  @Before
  public void setUp() {
    priceTargetCheckerTask = new PriceTargetCheckerTask(new BinanceApiClientFactory(true, null, null), mockChartPatternSignalDaoImpl, mockSupportedSymbolsInfo);
  }

  @Test
  public void testPerformPriceTargetChecks_timeFrame_fifteenMinutes() throws InterruptedException, ParseException, IOException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal();
    List<ChartPatternSignal> chartPatternSignals = Lists.newArrayList(chartPatternSignal);
    when(mockChartPatternSignalDaoImpl.getChatPatternSignalsThatJustReachedTenCandleStickTime())
        .thenReturn(chartPatternSignals);

    priceTargetCheckerTask.performPriceTargetChecks();

//    verify(mockChartPatternSignalDaoImpl).setTenCandleStickTimePrice(eq(chartPatternSignal), any(), any());
  }

  private ChartPatternSignal getChartPatternSignal() {
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(150)))
        .setPriceTargetTime(new Date(currentTime.getTime() + TimeUnit.MINUTES.toMillis(150)))
        .setPriceTarget(6000)
        .setProfitPotentialPercent(2.3)
        .build();
  }
}