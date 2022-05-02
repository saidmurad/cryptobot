package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.OutstandingTrades;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.RepayBorrowedOnMargin;
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
import org.mockito.junit.MockitoRule;

import javax.mail.MessagingException;
import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class StopLimitOrderStatusCheckerTest {

  @Rule public final MockitoRule rule = MockitoJUnit.rule();
  @Mock
  BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock
  BinanceApiMarginRestClient mockBinanceApiMarginRestClient;
  @Mock
  RepayBorrowedOnMargin mockRepayBorrowedOnMargin;
  @Mock
  ChartPatternSignalDaoImpl mockDao;
  @Mock
  OutstandingTrades mockOutstandingTrades;

  private StopLimitOrderStatusChecker stopLimitOrderStatusChecker;

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newMarginRestClient()).thenReturn(mockBinanceApiMarginRestClient);
    stopLimitOrderStatusChecker = new StopLimitOrderStatusChecker(mockDao, mockRepayBorrowedOnMargin,
        mockBinanceApiClientFactory, mockOutstandingTrades);
  }

  @Test
  public void activePositionButWithoutEntryOrderId_skipped() throws MessagingException, InterruptedException, ParseException, IOException, BinanceApiException {
    stopLimitOrderStatusChecker.doNotDecrementNumOutstandingTrades = false;
    when(mockDao.getAllChartPatternsWithActiveTradePositions()).thenReturn(
        Lists.newArrayList(getChartPatternSignal().build()));

    stopLimitOrderStatusChecker.perform();

    verifyNoInteractions(mockBinanceApiMarginRestClient);
    verify(mockDao, never()).updateExitStopLimitOrder(any(), any());
    verify(mockDao, never()).writeAccountBalanceToDB();
    verifyNoInteractions(mockOutstandingTrades);
  }

  @Captor
  ArgumentCaptor<OrderStatusRequest> orderStatusRequestArgumentCaptor;
  @Test
  public void stopLimitOrderFilled_buyTrade_statusGetsUpdatedInDB_borrowedAmountRepaid() throws MessagingException, InterruptedException, ParseException, IOException, BinanceApiException {
    stopLimitOrderStatusChecker.doNotDecrementNumOutstandingTrades = false;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setEntryOrder(
            ChartPatternSignal.Order.create(1, 2.0, 3.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0.0, 0.0, OrderStatus.NEW))
        .build();
    when(mockDao.getAllChartPatternsWithActiveTradePositions()).thenReturn(
        Lists.newArrayList(chartPatternSignal));
    Order exitLimitOrderStatus = new Order();
    exitLimitOrderStatus.setOrderId(2L);
    exitLimitOrderStatus.setStopPrice("4");
    exitLimitOrderStatus.setStatus(OrderStatus.FILLED);
    exitLimitOrderStatus.setExecutedQty("5.0");
    when(mockBinanceApiMarginRestClient.getOrderStatus(any())).thenReturn(exitLimitOrderStatus);
    stopLimitOrderStatusChecker.perform();

    verify(mockBinanceApiMarginRestClient).getOrderStatus(orderStatusRequestArgumentCaptor.capture());
    assertThat(orderStatusRequestArgumentCaptor.getValue().getOrderId()).isEqualTo(2);
    assertThat(orderStatusRequestArgumentCaptor.getValue().getSymbol()).isEqualTo("ETHUSDT");
    verify(mockDao).updateExitStopLimitOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(2L, 5, 4, OrderStatus.FILLED));
    verify(mockOutstandingTrades).decrementNumOutstandingTrades(TimeFrame.FIFTEEN_MINUTES);
    verify(mockDao).writeAccountBalanceToDB();
  }

  @Test
  public void doNotDecrementOutstandingTradeCount() throws MessagingException, InterruptedException, ParseException, IOException, BinanceApiException {
    stopLimitOrderStatusChecker.doNotDecrementNumOutstandingTrades = true;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setEntryOrder(
            ChartPatternSignal.Order.create(1, 2.0, 3.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0.0, 0.0, OrderStatus.NEW))
        .build();
    when(mockDao.getAllChartPatternsWithActiveTradePositions()).thenReturn(
        Lists.newArrayList(chartPatternSignal));
    Order exitLimitOrderStatus = new Order();
    exitLimitOrderStatus.setOrderId(2L);
    exitLimitOrderStatus.setStatus(OrderStatus.FILLED);
    exitLimitOrderStatus.setExecutedQty("5.0");
    exitLimitOrderStatus.setStopPrice("4");
    when(mockBinanceApiMarginRestClient.getOrderStatus(any())).thenReturn(exitLimitOrderStatus);
    stopLimitOrderStatusChecker.perform();

    verifyNoInteractions(mockOutstandingTrades);
  }

  @Test
  public void stopLimitOrder_buyTrade_PartiallyFilled_borowedAmountNotRepaid() throws MessagingException, InterruptedException, ParseException, IOException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setEntryOrder(
            ChartPatternSignal.Order.create(1, 2.0, 3.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0.0, 0.0, OrderStatus.NEW))
        .build();
    when(mockDao.getAllChartPatternsWithActiveTradePositions()).thenReturn(
        Lists.newArrayList(chartPatternSignal));
    Order exitLimitOrderStatus = new Order();
    exitLimitOrderStatus.setOrderId(2L);
    exitLimitOrderStatus.setStatus(OrderStatus.PARTIALLY_FILLED);
    exitLimitOrderStatus.setExecutedQty("5.0");
    exitLimitOrderStatus.setStopPrice("4");
    when(mockBinanceApiMarginRestClient.getOrderStatus(any())).thenReturn(exitLimitOrderStatus);

    stopLimitOrderStatusChecker.perform();

    verify(mockRepayBorrowedOnMargin, never()).repay(any(), anyLong());
    verifyNoInteractions(mockOutstandingTrades);
  }

  @Test
  public void stopLimitOrderFilled_sellTrade_statusGetsUpdatedInDB_borrowedAmountRepaid() throws MessagingException, InterruptedException, ParseException, IOException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTradeType(TradeType.SELL)
        .setEntryOrder(
            ChartPatternSignal.Order.create(1, 2.0, 3.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0.0, 0.0, OrderStatus.NEW))
        .build();
    when(mockDao.getAllChartPatternsWithActiveTradePositions()).thenReturn(
        Lists.newArrayList(chartPatternSignal));
    Order exitLimitOrderStatus = new Order();
    exitLimitOrderStatus.setOrderId(2L);
    exitLimitOrderStatus.setStatus(OrderStatus.FILLED);
    exitLimitOrderStatus.setExecutedQty("5.0");
    exitLimitOrderStatus.setStopPrice("2");
    when(mockBinanceApiMarginRestClient.getOrderStatus(any())).thenReturn(exitLimitOrderStatus);

    stopLimitOrderStatusChecker.perform();

    verify(mockBinanceApiMarginRestClient).getOrderStatus(orderStatusRequestArgumentCaptor.capture());
    assertThat(orderStatusRequestArgumentCaptor.getValue().getOrderId()).isEqualTo(2);
    assertThat(orderStatusRequestArgumentCaptor.getValue().getSymbol()).isEqualTo("ETHUSDT");
    verify(mockDao).updateExitStopLimitOrder(eq(chartPatternSignal),
        eq(ChartPatternSignal.Order.create(2L, 4.995, 2, OrderStatus.FILLED)));
    verify(mockRepayBorrowedOnMargin).repay(eq("ETH"), eq(4.995));
  }

  @Test
  public void stopLimitOrderPartiallyFilled_sellTrade_statusGetsUpdatedInDB_borrowedAmountNotRepaid() throws MessagingException, InterruptedException, ParseException, IOException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTradeType(TradeType.SELL)
        .setEntryOrder(
            ChartPatternSignal.Order.create(1, 2.0, 3.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0.0, 0.0, OrderStatus.NEW))
        .build();
    when(mockDao.getAllChartPatternsWithActiveTradePositions()).thenReturn(
        Lists.newArrayList(chartPatternSignal));
    Order exitLimitOrderStatus = new Order();
    exitLimitOrderStatus.setOrderId(2L);
    exitLimitOrderStatus.setStatus(OrderStatus.PARTIALLY_FILLED);
    exitLimitOrderStatus.setPrice("2.0");
    exitLimitOrderStatus.setExecutedQty("2.5");
    exitLimitOrderStatus.setStopPrice("2");
    when(mockBinanceApiMarginRestClient.getOrderStatus(any())).thenReturn(exitLimitOrderStatus);

    stopLimitOrderStatusChecker.perform();

    verify(mockBinanceApiMarginRestClient).getOrderStatus(orderStatusRequestArgumentCaptor.capture());
    assertThat(orderStatusRequestArgumentCaptor.getValue().getOrderId()).isEqualTo(2);
    assertThat(orderStatusRequestArgumentCaptor.getValue().getSymbol()).isEqualTo("ETHUSDT");
    verify(mockDao).updateExitStopLimitOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(2L, 2.4975, 2.0, OrderStatus.PARTIALLY_FILLED));
    verify(mockRepayBorrowedOnMargin, never()).repay(any(), anyLong());
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
        .setPriceTargetTime(new Date(currentTimeMillis))
        .setProfitPotentialPercent(2.3)
        .setIsSignalOn(true);
  }
}