package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.trading.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
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
public class ProfitTakerTaskTest {

  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private ChartPatternSignalDaoImpl dao;
  @Mock private BookTickerPrices bookTickerPrices;
  @Mock private ExitPositionAtMarketPrice exitPositionAtMarketPrice;
  private final long timeOfSignal = System.currentTimeMillis();
  private ProfitTakerTask profitTakerTask;

  @Before
  public void setUp() {
    profitTakerTask = new ProfitTakerTask(dao, bookTickerPrices, exitPositionAtMarketPrice);
  }

  @Test
  public void testPerform_noBookTickerCurrentlyAvailable_doesNthing() throws MessagingException, IOException, ParseException, BinanceApiException {
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(getChartPatternSignal().build()));
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(null);

    profitTakerTask.perform();

    verifyNoInteractions(exitPositionAtMarketPrice);
  }

  @Test
  public void testPerform_BUYTrade_priceTargetNotYetMet_doesNothing() throws MessagingException, IOException, ParseException, BinanceApiException {
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(getChartPatternSignal().build()));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(5999, 5998);
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(bookTicker);

    profitTakerTask.perform();

    verifyNoInteractions(exitPositionAtMarketPrice);
  }

  @Captor
  ArgumentCaptor<ChartPatternSignal> chartPatternSignalArgumentCaptor;
  @Captor ArgumentCaptor<TradeExitType> tradeExitTypeArgumentCaptor;

  @Test
  public void testPerform_BUYTrade_priceTargetMet_exitsTrade() throws MessagingException, IOException, ParseException, BinanceApiException {
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(
        getChartPatternSignal().build()));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(6000, 5998);
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(bookTicker);

    profitTakerTask.perform();

    verify(exitPositionAtMarketPrice).exitPositionIfStillHeld(
        chartPatternSignalArgumentCaptor.capture(), tradeExitTypeArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue()).isEqualTo(getChartPatternSignal().build());
    assertThat(tradeExitTypeArgumentCaptor.getValue()).isEqualTo(TradeExitType.PROFIT_TARGET_MET);
  }

  @Test
  public void testPerform_SELLTrade_priceTargetNotYetMet_doesNothing() throws MessagingException, IOException, ParseException, BinanceApiException {
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(
        getChartPatternSignal().setTradeType(TradeType.SELL).setPriceTarget(3000).build()));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(3001, 3001);

    profitTakerTask.perform();

    verifyNoInteractions(exitPositionAtMarketPrice);
  }

  @Test
  public void testPerform_SELLTrade_priceTargetMet_exitsTrade() throws MessagingException, IOException, ParseException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setTradeType(TradeType.SELL).setPriceTarget(3000).build();
    when(dao.getAllChartPatternsWithActiveTradePositions()).thenReturn(Lists.newArrayList(
        chartPatternSignal));
    BookTickerPrices.BookTicker bookTicker = BookTickerPrices.BookTicker.create(3001, 3000);
    when(bookTickerPrices.getBookTicker("ETHUSDT")).thenReturn(bookTicker);

    profitTakerTask.perform();

    verify(exitPositionAtMarketPrice).exitPositionIfStillHeld(
        chartPatternSignalArgumentCaptor.capture(), tradeExitTypeArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue()).isEqualTo(chartPatternSignal);
    assertThat(tradeExitTypeArgumentCaptor.getValue()).isEqualTo(TradeExitType.PROFIT_TARGET_MET);
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