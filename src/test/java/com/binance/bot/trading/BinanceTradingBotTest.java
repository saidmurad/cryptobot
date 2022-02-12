package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.springframework.data.util.Pair;

import java.text.ParseException;
import java.util.Date;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class BinanceTradingBotTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Mock
  BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock
  BinanceApiRestClient mockBinanceApiRestClient;
  @Mock
  ChartPatternSignalDaoImpl mockDao;
  @Mock SupportedSymbolsInfo mockSupportedSymbolsInfo;
  private BinanceTradingBot binanceTradingBot;

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    binanceTradingBot = new BinanceTradingBot(mockBinanceApiClientFactory, mockSupportedSymbolsInfo, mockDao);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    binanceTradingBot.perTradeAmount = 20.0;
  }

  private void setUsdtBalance(Double bal) {
    Account account = new Account();
    AssetBalance usdtBalance = new AssetBalance();
    usdtBalance.setAsset("USDT");
    usdtBalance.setFree(bal.toString());
    account.setBalances(Lists.newArrayList(usdtBalance));
    when(mockBinanceApiRestClient.getAccount()).thenReturn(account);
  }

  @Test
  public void testPlaceBuyTrade_insufficientUSDT() throws ParseException {
    setUsdtBalance(4.9);
    binanceTradingBot.placeTrade(getChartPatternSignal().build());
  }

  @Captor
  ArgumentCaptor<NewOrder> orderCaptor;
  @Captor
  ArgumentCaptor<NewOrder> sellOrderCaptor;
  @Captor ArgumentCaptor<ChartPatternSignal> chartPatternSignalArgumentCaptor;
  @Captor ArgumentCaptor<ChartPatternSignal.Order> chartPatternSignalOrderArgumentCaptor;

  @Test
  public void testPlaceBuyTrade_usesOnlyPerTradeAmount() throws ParseException {
    binanceTradingBot.stopLossPercent = 5.0;
    setUsdtBalance(120.0);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setExecutedQty("0.005");
    buyOrderResp.setStatus(OrderStatus.FILLED);

    NewOrderResponse sellStopLossOrderResp = new NewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiRestClient.newOrder(any(NewOrder.class))).thenAnswer(new Answer<NewOrderResponse>() {
      private int count = 0;
      @Override
      public NewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiRestClient, times(2)).newOrder(orderCaptor.capture());
    NewOrder buyOrder = orderCaptor.getAllValues().get(0);
    assertThat(buyOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(buyOrder.getSide()).isEqualTo(OrderSide.BUY);
    assertThat(buyOrder.getType()).isEqualTo(OrderType.MARKET);
    assertThat(buyOrder.getQuantity()).isEqualTo("0.005"); // assuming market price $4000, and buying for $100.
    verify(mockDao).setEntryOrder(chartPatternSignalArgumentCaptor.capture(),
        chartPatternSignalOrderArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue()).isEqualTo(chartPatternSignal);
    assertThat(chartPatternSignalOrderArgumentCaptor.getValue()).isEqualTo(ChartPatternSignal.Order.create(
        buyOrderResp.getOrderId(),
        0.005,
        /* TODO: Find the actual price from the associated Trade */
        chartPatternSignal.priceAtTimeOfSignalReal(),
        OrderStatus.FILLED));
    NewOrder sellStopLossOrder = orderCaptor.getAllValues().get(1);
    assertThat(sellStopLossOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(sellStopLossOrder.getSide()).isEqualTo(OrderSide.SELL);
    assertThat(sellStopLossOrder.getType()).isEqualTo(OrderType.STOP_LOSS);
    assertThat(sellStopLossOrder.getTimeInForce()).isNull();
    assertThat(sellStopLossOrder.getQuantity()).isEqualTo("0.005");
    assertThat(sellStopLossOrder.getPrice()).isEqualTo("3800.00");
    verify(mockDao).setExitStopLossOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(
            sellStopLossOrderResp.getOrderId(), 0.0, 3800.0,
            OrderStatus.NEW));
  }

  @Test
  public void insufficientUSDTInAccount_doesNothing() throws ParseException {
    binanceTradingBot.stopLossPercent = 5.0;
    binanceTradingBot.perTradeAmount = 20.0;
    setUsdtBalance(19.5);

    binanceTradingBot.placeTrade(getChartPatternSignal().build());

    verifyNoInteractions(mockSupportedSymbolsInfo);
    verifyNoInteractions(mockDao);
  }

  @Test
  public void testPlaceBuyTrade_usesAtleastMinNotionalAmount() throws ParseException {
    binanceTradingBot.stopLossPercent = 5.0;
    binanceTradingBot.perTradeAmount = 9;
    setUsdtBalance(120.0);
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setExecutedQty("0.0025"); // Equivalent of $10 notional.
    buyOrderResp.setStatus(OrderStatus.FILLED);

    NewOrderResponse sellStopLossOrderResp = new NewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiRestClient.newOrder(any(NewOrder.class))).thenAnswer(new Answer<NewOrderResponse>() {
      private int count = 0;
      @Override
      public NewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiRestClient, times(2)).newOrder(orderCaptor.capture());
    NewOrder buyOrder = orderCaptor.getAllValues().get(0);
    assertThat(buyOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(buyOrder.getSide()).isEqualTo(OrderSide.BUY);
    assertThat(buyOrder.getType()).isEqualTo(OrderType.MARKET);
    assertThat(buyOrder.getQuantity()).isEqualTo("0.0025"); // Equivalent of $10 notional.
  }

  @Test
  public void roundsUpQtyNotDown_and_limitsToStepSizeNumDigits() throws ParseException {
    binanceTradingBot.stopLossPercent = 5.0;
    binanceTradingBot.perTradeAmount = 10.1;
    setUsdtBalance(120.0);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setExecutedQty("0.0026"); // 0.002525 is rounded up.
    buyOrderResp.setStatus(OrderStatus.FILLED);

    NewOrderResponse sellStopLossOrderResp = new NewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiRestClient.newOrder(any(NewOrder.class))).thenAnswer(new Answer<NewOrderResponse>() {
      private int count = 0;
      @Override
      public NewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiRestClient, times(2)).newOrder(orderCaptor.capture());
    NewOrder buyOrder = orderCaptor.getAllValues().get(0);
    assertThat(buyOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(buyOrder.getSide()).isEqualTo(OrderSide.BUY);
    assertThat(buyOrder.getType()).isEqualTo(OrderType.MARKET);
    assertThat(buyOrder.getQuantity()).isEqualTo("0.0026"); // Equivalent of $10 notional.
  }

  private ChartPatternSignal.Builder getChartPatternSignal() {
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setPriceAtTimeOfSignalReal(4000.0)
        .setTimeOfSignal(new Date())
        .setTimeOfInsertion(new Date())
        .setIsInsertedLate(false)
        .setPriceTarget(6000.0123)
        .setPriceTargetTime(new Date(System.currentTimeMillis() + 360000))
        .setProfitPotentialPercent(2.3)
        .setIsSignalOn(true);
  }
}