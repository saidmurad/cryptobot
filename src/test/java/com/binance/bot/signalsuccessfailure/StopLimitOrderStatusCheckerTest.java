package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
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
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class StopLimitOrderStatusCheckerTest {

  @Rule public final MockitoRule rule = MockitoJUnit.rule();
  @Mock
  BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock
  BinanceApiRestClient mockBinanceApiRestClient;
  @Mock
  ChartPatternSignalDaoImpl mockDao;

  private StopLimitOrderStatusChecker stopLimitOrderStatusChecker;

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    stopLimitOrderStatusChecker = new StopLimitOrderStatusChecker(mockDao, mockBinanceApiClientFactory);
  }

  @Test
  public void activePositionButWithoutEntryOrderId_skipped() throws ParseException, IOException {
    when(mockDao.getAllChartPatternsWithActiveTradePositions()).thenReturn(
        Lists.newArrayList(getChartPatternSignal().build()));

    stopLimitOrderStatusChecker.perform();

    verifyNoInteractions(mockBinanceApiRestClient);
    verify(mockDao, never()).updateExitStopLimitOrder(any(), any());
  }

  @Captor
  ArgumentCaptor<OrderStatusRequest> orderStatusRequestArgumentCaptor;
  @Test
  public void stopLimitOrderFilled_statusGetsUpdatedInDB() throws ParseException, IOException {
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
    exitLimitOrderStatus.setPrice("4.0");
    exitLimitOrderStatus.setExecutedQty("5.0");
    when(mockBinanceApiRestClient.getOrderStatus(any())).thenReturn(exitLimitOrderStatus);

    stopLimitOrderStatusChecker.perform();

    verify(mockBinanceApiRestClient).getOrderStatus(orderStatusRequestArgumentCaptor.capture());
    assertThat(orderStatusRequestArgumentCaptor.getValue().getOrderId()).isEqualTo(2);
    assertThat(orderStatusRequestArgumentCaptor.getValue().getSymbol()).isEqualTo("ETHUSDT");
    verify(mockDao).updateExitStopLimitOrder(chartPatternSignal, exitLimitOrderStatus);
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