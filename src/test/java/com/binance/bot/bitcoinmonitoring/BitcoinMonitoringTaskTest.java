package com.binance.bot.bitcoinmonitoring;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
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
import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class BitcoinMonitoringTaskTest {
  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock
  private BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock private BinanceApiRestClient mockBinanceApiRestClient;
  @Mock private ChartPatternSignalDaoImpl mockDao;
  @Mock private Clock mockClock;
  private BitcoinMonitoringTask bitcoinMonitoringTask;

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    bitcoinMonitoringTask = new BitcoinMonitoringTask(mockBinanceApiClientFactory, mockDao);
    bitcoinMonitoringTask.fifteenMinuteMovementThresholdPercent = 1.5;
    //bitcoinMonitoringTask.isFirstTimeFifteenMinuteTimeframe = false;
  }

  @Test
  public void whenNotCalculated() {
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isNull();
  }

  @Test
  public void updatesDatabase_firstTimeIteration_fifteenMinutes() throws ParseException, BinanceApiException {
    bitcoinMonitoringTask.isFirstTimeFifteenMinuteTimeframe = true;
    Double price = 100.0;
    List<Candlestick> candlesticks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Candlestick candlestick = new Candlestick();
      candlestick.setOpen(price.toString());
      price = price + 1.0;
      candlestick.setClose(price.toString());
      candlesticks.add(candlestick);
      candlestick.setOpenTime((long) i);
      candlestick.setCloseTime((long) i + 1);
    }
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(candlesticks);

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();

    verify(mockDao, times(10)).insertBitcoinPrice(eq(TimeFrame.FIFTEEN_MINUTES), any(), any(), any());
    verify(mockDao).insertOverdoneTradeType(eq(new Date(11)), eq(TimeFrame.FIFTEEN_MINUTES), eq(TradeType.BUY));
  }

  @Test
  public void updatesDatabase_notFirstTimeIteration_fifteenMinutes() throws ParseException, BinanceApiException {
    bitcoinMonitoringTask.isFirstTimeFifteenMinuteTimeframe = false;
    Double price = 100.0;
    List<Candlestick> candlesticks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Candlestick candlestick = new Candlestick();
      candlestick.setOpen(price.toString());
      price = price + 1.0;
      candlestick.setClose(price.toString());
      candlesticks.add(candlestick);
      candlestick.setOpenTime((long) i);
      candlestick.setCloseTime((long) i + 1);
    }
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(candlesticks);

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();

    verify(mockDao, times(1)).insertBitcoinPrice(eq(TimeFrame.FIFTEEN_MINUTES), any(), any(), any());
    verify(mockDao).insertOverdoneTradeType(eq(new Date(11)), eq(TimeFrame.FIFTEEN_MINUTES), eq(TradeType.BUY));
  }

  @Test
  public void updatesDatabase_firstTimeIteration_hourly() throws ParseException, BinanceApiException {
    bitcoinMonitoringTask.isFirstTimeHourlyTimeframe = true;
    Double price = 100.0;
    List<Candlestick> candlesticks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Candlestick candlestick = new Candlestick();
      candlestick.setOpen(price.toString());
      price = price + 1.0;
      candlestick.setClose(price.toString());
      candlesticks.add(candlestick);
      candlestick.setOpenTime((long) i);
      candlestick.setCloseTime((long) i + 1);
    }
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.HOURLY, 10, null, null))
        .thenReturn(candlesticks);

    bitcoinMonitoringTask.performHourlyTimeFrame();

    verify(mockDao, times(10)).insertBitcoinPrice(eq(TimeFrame.HOUR), any(), any(), any());
    verify(mockDao).insertOverdoneTradeType(eq(new Date(11)), eq(TimeFrame.HOUR), eq(TradeType.BUY));
  }

  @Test
  public void updatesDatabase_notFirstTimeIteration_hourly() throws ParseException, BinanceApiException {
    bitcoinMonitoringTask.isFirstTimeHourlyTimeframe = false;
    Double price = 100.0;
    List<Candlestick> candlesticks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Candlestick candlestick = new Candlestick();
      candlestick.setOpen(price.toString());
      price = price + 1.0;
      candlestick.setClose(price.toString());
      candlesticks.add(candlestick);
      candlestick.setOpenTime((long) i);
      candlestick.setCloseTime((long) i + 1);
    }
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.HOURLY, 10, null, null))
        .thenReturn(candlesticks);

    bitcoinMonitoringTask.performHourlyTimeFrame();

    verify(mockDao, times(1)).insertBitcoinPrice(eq(TimeFrame.HOUR), any(), any(), any());
    verify(mockDao).insertOverdoneTradeType(eq(new Date(11)), eq(TimeFrame.HOUR), eq(TradeType.BUY));
  }

  @Test
  public void updatesDatabase_firstTimeIteration_fourHourly() throws ParseException, BinanceApiException {
    bitcoinMonitoringTask.isFirstTimeFourHourlyTimeframe = true;
    Double price = 100.0;
    List<Candlestick> candlesticks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Candlestick candlestick = new Candlestick();
      candlestick.setOpen(price.toString());
      price = price + 1.0;
      candlestick.setClose(price.toString());
      candlesticks.add(candlestick);
      candlestick.setOpenTime((long) i);
      candlestick.setCloseTime((long) i + 1);
    }
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FOUR_HOURLY, 10, null, null))
        .thenReturn(candlesticks);

    bitcoinMonitoringTask.performFourHourlyTimeFrame();

    verify(mockDao, times(10)).insertBitcoinPrice(eq(TimeFrame.FOUR_HOURS), any(), any(), any());
    verify(mockDao).insertOverdoneTradeType(eq(new Date(11)), eq(TimeFrame.FOUR_HOURS), eq(TradeType.BUY));
  }

  @Test
  public void updatesDatabase_notFirstTimeIteration_fourHourly() throws ParseException, BinanceApiException {
    bitcoinMonitoringTask.isFirstTimeFourHourlyTimeframe = false;
    Double price = 100.0;
    List<Candlestick> candlesticks = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Candlestick candlestick = new Candlestick();
      candlestick.setOpen(price.toString());
      price = price + 1.0;
      candlestick.setClose(price.toString());
      candlesticks.add(candlestick);
      candlestick.setOpenTime((long) i);
      candlestick.setCloseTime((long) i + 1);
    }
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FOUR_HOURLY, 10, null, null))
        .thenReturn(candlesticks);

    bitcoinMonitoringTask.performFourHourlyTimeFrame();

    verify(mockDao, times(1)).insertBitcoinPrice(eq(TimeFrame.FOUR_HOURS), any(), any(), any());
    verify(mockDao).insertOverdoneTradeType(eq(new Date(11)), eq(TimeFrame.FOUR_HOURS), eq(TradeType.BUY));
  }

  @Test
  public void sellOverdone_in_oneCandlestick() throws ParseException, BinanceApiException {
    Candlestick candlestick0 = new Candlestick();
    candlestick0.setOpenTime(1L);
    candlestick0.setCloseTime(2L);
    candlestick0.setOpen("0");
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("100");
    candlestick1.setClose("98.5");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1));

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.SELL);
  }

  @Test
  public void sellOverdone_in_twoCandlesticks() throws ParseException, BinanceApiException {
    Candlestick candlestickStreakBreakerInGreen = new Candlestick();
    candlestickStreakBreakerInGreen.setOpen("99.1");
    candlestickStreakBreakerInGreen.setClose("99.2");
    candlestickStreakBreakerInGreen.setOpenTime(1L);
    candlestickStreakBreakerInGreen.setCloseTime(2L);
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("100");
    candlestick1.setClose("99");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("99");
    candlestick2.setClose("98.5");
    candlestick2.setCloseTime(1L);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestickStreakBreakerInGreen, candlestick1, candlestick2));

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.SELL);
  }

  @Test
  public void sellOverdone_inTenCandlesticks_butNotYetMeetingPercentageThreshold() throws ParseException, BinanceApiException {
    List<Candlestick> candlesticks = new ArrayList<>();
    Double openPrice = 100.0;
    for (int i = 0; i < 10; i++) {
      Double closePrice = new Double(openPrice - 0.1);
      Candlestick candlestick = new Candlestick();
      candlestick.setOpen(openPrice.toString());
      candlestick.setClose(closePrice.toString());
      candlestick.setCloseTime(1L);
      candlesticks.add(candlestick);
      openPrice = closePrice;
    }
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(candlesticks);

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.SELL);
  }

  @Test
  public void fallingStreakNotUnbroken_redGreenWhileLookingBackwards() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("98.5");
    candlestick1.setClose("99");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);

    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("99");
    candlestick2.setClose("98.5");
    candlestick2.setCloseTime(1L);

    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2));

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.NONE);
  }

  @Test
  public void fallingStreakNotUnbroken_redGreenWhileLookingBackwards_butStillConsideredOverdone() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("100");
    candlestick1.setClose("101");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);

    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("99");
    candlestick2.setClose("98.5");
    candlestick2.setCloseTime(1L);

    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2));

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.SELL);
  }

  @Test
  public void buyOverdone_in_oneCandlestick() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("0");
    candlestick1.setClose("0");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);

    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(1L);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2));

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.BUY);
  }

  @Test
  public void lastCandlestickRemoved_fifteenMinutes() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("0");
    candlestick1.setClose("0");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);

    // This should prevail as buy overdone.
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(1L);

    Candlestick incompleteCandlestick = new Candlestick();
    incompleteCandlestick.setOpen("90");
    incompleteCandlestick.setClose("80");
    incompleteCandlestick.setCloseTime(10L);
    when(mockClock.millis()).thenReturn(9L);
    bitcoinMonitoringTask.setMockClock(mockClock);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2, incompleteCandlestick));

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.BUY);
  }

  @Test
  public void lastCandlestickNotRemoved_fifteenMinutes() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("0");
    candlestick1.setClose("0");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);

    // This should prevail as buy overdone.
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(1L);

    Candlestick incompleteCandlestick = new Candlestick();
    incompleteCandlestick.setOpen("90");
    incompleteCandlestick.setClose("80");
    incompleteCandlestick.setCloseTime(10L);
    when(mockClock.millis()).thenReturn(11L);
    bitcoinMonitoringTask.setMockClock(mockClock);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2, incompleteCandlestick));

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.SELL);
  }

  @Test
  public void lastCandlestickRemoved_hourly() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("0");
    candlestick1.setClose("0");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);

    // This should prevail as buy overdone.
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(1L);

    Candlestick incompleteCandlestick = new Candlestick();
    incompleteCandlestick.setOpen("90");
    incompleteCandlestick.setClose("80");
    incompleteCandlestick.setCloseTime(10L);
    when(mockClock.millis()).thenReturn(9L);
    bitcoinMonitoringTask.setMockClock(mockClock);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.HOURLY, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2, incompleteCandlestick));

    bitcoinMonitoringTask.performHourlyTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.HOUR)).isEqualTo(TradeType.BUY);
  }

  @Test
  public void lastCandlestickNotRemoved_hourly() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("0");
    candlestick1.setClose("0");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);

    // This should prevail as buy overdone.
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(1L);

    Candlestick incompleteCandlestick = new Candlestick();
    incompleteCandlestick.setOpen("90");
    incompleteCandlestick.setClose("80");
    incompleteCandlestick.setCloseTime(10L);
    when(mockClock.millis()).thenReturn(11L);
    bitcoinMonitoringTask.setMockClock(mockClock);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.HOURLY, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2, incompleteCandlestick));

    bitcoinMonitoringTask.performHourlyTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.HOUR)).isEqualTo(TradeType.SELL);
  }

  @Test
  public void lastCandlestickRemoved_fourHourly() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("0");
    candlestick1.setClose("0");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);

    // This should prevail as buy overdone.
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(1L);

    Candlestick incompleteCandlestick = new Candlestick();
    incompleteCandlestick.setOpen("90");
    incompleteCandlestick.setClose("80");
    incompleteCandlestick.setCloseTime(10L);
    when(mockClock.millis()).thenReturn(9L);
    bitcoinMonitoringTask.setMockClock(mockClock);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FOUR_HOURLY, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2, incompleteCandlestick));

    bitcoinMonitoringTask.performFourHourlyTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FOUR_HOURS)).isEqualTo(TradeType.BUY);
  }

  @Test
  public void lastCandlestickNotRemoved_fourHourly() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("0");
    candlestick1.setClose("0");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);

    // This should prevail as buy overdone.
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(1L);

    Candlestick incompleteCandlestick = new Candlestick();
    incompleteCandlestick.setOpen("90");
    incompleteCandlestick.setClose("80");
    incompleteCandlestick.setCloseTime(10L);
    when(mockClock.millis()).thenReturn(11L);
    bitcoinMonitoringTask.setMockClock(mockClock);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FOUR_HOURLY, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2, incompleteCandlestick));

    bitcoinMonitoringTask.performFourHourlyTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FOUR_HOURS)).isEqualTo(TradeType.SELL);
  }

  @Test
  public void buyOverdone_in_oneCandlestick_hourly() throws ParseException, BinanceApiException {
    bitcoinMonitoringTask.hourlyMovementThresholdPercent = 1.5;
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("0");
    candlestick1.setClose("0");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(1L);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.HOURLY, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2));

    bitcoinMonitoringTask.performHourlyTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.HOUR)).isEqualTo(TradeType.BUY);
  }

  @Test
  public void buyOverdone_in_oneCandlestick_fourHourly() throws ParseException, BinanceApiException {
    bitcoinMonitoringTask.fourHourlyMovementThresholdPercent = 1.5;
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("0");
    candlestick1.setClose("0");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(2L);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FOUR_HOURLY, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2));

    bitcoinMonitoringTask.performFourHourlyTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FOUR_HOURS)).isEqualTo(TradeType.BUY);
  }

  @Test
  public void buyOverdone_in_twoCandlesticks() throws ParseException, BinanceApiException {
    Candlestick streakBreakerInRed = new Candlestick();
    streakBreakerInRed.setOpen("100.1");
    streakBreakerInRed.setClose("100");
    streakBreakerInRed.setOpenTime(1L);
    streakBreakerInRed.setCloseTime(2L);
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("100");
    candlestick1.setClose("101");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("101");
    candlestick2.setClose("101.5");
    candlestick2.setCloseTime(2L);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(Lists.newArrayList(streakBreakerInRed, candlestick1, candlestick2));

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.BUY);
  }

  @Test
  public void buyOverdone_inTenCandlesticks_butNotYetMeetingPercentageThreshold() throws ParseException, BinanceApiException {
    List<Candlestick> candlesticks = new ArrayList<>();
    Double openPrice = 100.0;
    for (int i = 0; i < 10; i++) {
      Double closePrice = new Double(openPrice + 0.1);
      Candlestick candlestick = new Candlestick();
      candlestick.setOpen(openPrice.toString());
      candlestick.setClose(closePrice.toString());
      candlestick.setOpenTime(1L);
      candlestick.setCloseTime(2L);
      candlesticks.add(candlestick);
      openPrice = closePrice;
    }
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(candlesticks);

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.BUY);
  }

  @Test
  public void risingStreakNotUnbroken_greenRedWhileLookingBackwards() throws ParseException, BinanceApiException {
    Candlestick candlestick1 = new Candlestick();
    candlestick1.setOpen("99.6");
    candlestick1.setClose("98.5");
    candlestick1.setOpenTime(1L);
    candlestick1.setCloseTime(2L);
    Candlestick candlestick2 = new Candlestick();
    candlestick2.setOpen("100");
    candlestick2.setClose("101");
    candlestick2.setCloseTime(2L);
    when(mockBinanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null))
        .thenReturn(Lists.newArrayList(candlestick1, candlestick2));

    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
    assertThat(bitcoinMonitoringTask.getTradeTypeOverdone(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.NONE);
  }
}