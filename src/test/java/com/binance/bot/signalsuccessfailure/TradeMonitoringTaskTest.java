package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.trading.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import com.gateiobot.db.MACDData;
import com.gateiobot.db.MACDDataDao;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import javax.mail.MessagingException;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class TradeMonitoringTaskTest {

  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private ChartPatternSignalDaoImpl dao;
  @Mock private MACDDataDao mockMacdDataDao;
  @Mock private BookTickerPrices bookTickerPrices;
  @Mock private ExitPositionAtMarketPrice exitPositionAtMarketPrice;
  private final long timeOfSignal = System.currentTimeMillis();
  private TradeMonitoringTask tradeMonitoringTask;

  @Before
  public void setUp() {
    tradeMonitoringTask = new TradeMonitoringTask(dao, mockMacdDataDao, bookTickerPrices, exitPositionAtMarketPrice);
  }

  @Test
  public void testPerform_noBookTickerCurrentlyAvailable_doesNthing() throws MessagingException, InterruptedException, IOException, ParseException, BinanceApiException {
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(getChartPatternSignal().build()));
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(null);

    tradeMonitoringTask.perform();

    verifyNoInteractions(exitPositionAtMarketPrice);
  }

  @Test
  public void testPerform_BUYTrade_priceTargetNotYetMet_doesNothing() throws MessagingException, InterruptedException, IOException, ParseException, BinanceApiException {
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(getChartPatternSignal().build()));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(5999, 5998);
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(bookTicker);

    tradeMonitoringTask.perform();

    verifyNoInteractions(exitPositionAtMarketPrice);
  }

  @Captor
  ArgumentCaptor<ChartPatternSignal> chartPatternSignalArgumentCaptor;
  @Captor ArgumentCaptor<TradeExitType> tradeExitTypeArgumentCaptor;

  @Test
  public void testPerform_BUYTrade_priceTargetMet_exitsTrade() throws MessagingException, InterruptedException, IOException, ParseException, BinanceApiException {
    tradeMonitoringTask.useBreakoutCandlestickForStopLoss = true;
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(
        getChartPatternSignal().build()));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(6000, 5998);
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(bookTicker);

    tradeMonitoringTask.perform();

    verify(exitPositionAtMarketPrice).exitPositionIfStillHeld(
        chartPatternSignalArgumentCaptor.capture(), tradeExitTypeArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue()).isEqualTo(getChartPatternSignal().build());
    assertThat(tradeExitTypeArgumentCaptor.getValue()).isEqualTo(TradeExitType.PROFIT_TARGET_MET);
    verify(mockMacdDataDao, never()).getStopLossLevelBasedOnBreakoutCandlestick(any());
  }

  @Test
  public void testPerform_BUYTrade_LastCandlestickRetracedToBeforeBreakout_exitsTrade() throws MessagingException, InterruptedException, IOException, ParseException, BinanceApiException {
    tradeMonitoringTask.useBreakoutCandlestickForStopLoss = true;
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(
            getChartPatternSignal()
                .setPriceAtTimeOfSignal(4000)
                .setPriceTarget(6000)
                .build()));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(3989, 3989);
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(bookTicker);
    when(mockMacdDataDao.getStopLossLevelBasedOnBreakoutCandlestick(any())).thenReturn( 3990.0);
    MACDData macdData = new MACDData();
    macdData.candleClosingPrice = 3989;
    when(mockMacdDataDao.getLastMACDData(any(), any())).thenReturn(macdData);
    tradeMonitoringTask.useBreakoutCandlestickForStopLoss = true;

    tradeMonitoringTask.perform();

    verify(exitPositionAtMarketPrice).exitPositionIfStillHeld(
            chartPatternSignalArgumentCaptor.capture(), tradeExitTypeArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue()).isEqualTo(getChartPatternSignal().build());
    assertThat(tradeExitTypeArgumentCaptor.getValue()).isEqualTo(TradeExitType.STOP_LOSS_PRE_BREAKOUT_HIT);
  }

  @Test
  public void testPerform_SELLTrade_LastCandlestickRetracedToBeforeBreakout_exitsTrade() throws MessagingException, InterruptedException, IOException, ParseException, BinanceApiException {
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(
            getChartPatternSignal()
                .setTradeType(TradeType.SELL)
                .setPriceAtTimeOfSignal(5990)
                .setPriceTarget(5900)
                .build()));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(6000, 6001);
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(bookTicker);
    when(mockMacdDataDao.getStopLossLevelBasedOnBreakoutCandlestick(any())).thenReturn( 6000.0);
    MACDData macdData = new MACDData();
    macdData.candleClosingPrice = 6010.0;
    when(mockMacdDataDao.getLastMACDData(any(), any())).thenReturn(macdData);
    tradeMonitoringTask.useBreakoutCandlestickForStopLoss = true;

    tradeMonitoringTask.perform();

    verify(exitPositionAtMarketPrice).exitPositionIfStillHeld(
            chartPatternSignalArgumentCaptor.capture(), tradeExitTypeArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue()).isEqualTo(getChartPatternSignal().setTradeType(TradeType.SELL).build());
    assertThat(tradeExitTypeArgumentCaptor.getValue()).isEqualTo(TradeExitType.STOP_LOSS_PRE_BREAKOUT_HIT);
  }

  @Test
  public void testPerform_SELLTrade_priceTargetNotYetMet_doesNothing() throws MessagingException, InterruptedException, IOException, ParseException, BinanceApiException {
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(
        getChartPatternSignal().setTradeType(TradeType.SELL).setPriceTarget(3000).build()));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(3001, 3001);

    tradeMonitoringTask.perform();

    verifyNoInteractions(exitPositionAtMarketPrice);
  }

  @Test
  public void testPerform_SELLTrade_priceTargetMet_exitsTrade() throws MessagingException, InterruptedException, IOException, ParseException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setTradeType(TradeType.SELL).setPriceTarget(3000).build();
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(
        chartPatternSignal));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(3001, 3000);
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(bookTicker);

    tradeMonitoringTask.perform();

    verify(exitPositionAtMarketPrice).exitPositionIfStillHeld(
        chartPatternSignalArgumentCaptor.capture(), tradeExitTypeArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue()).isEqualTo(chartPatternSignal);
    assertThat(tradeExitTypeArgumentCaptor.getValue()).isEqualTo(TradeExitType.PROFIT_TARGET_MET);
    verify(mockMacdDataDao, never()).getStopLossLevelBasedOnBreakoutCandlestick(any());
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