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
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.text.ParseException;
import java.util.Date;

import static com.binance.bot.trading.BinanceTradingBot.PER_TRADE_AMOUNT;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
  ArgumentCaptor<NewOrder> buyOrderCaptor;
  @Captor
  ArgumentCaptor<NewOrder> sellOrderCaptor;

  @Test
  public void testPlaceBuyTrade() throws ParseException {
    setUsdtBalance(PER_TRADE_AMOUNT + 100);
    when(mockSupportedSymbolsInfo.getLotSize("ETHUSDT")).thenReturn(3);
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setExecutedQty("20");
    buyOrderResp.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.newOrder(any(NewOrder.class))).thenReturn(buyOrderResp);

    NewOrderResponse sellLimitOrderResp = new NewOrderResponse();
    sellLimitOrderResp.setOrderId(2L);
    sellLimitOrderResp.setExecutedQty("0");
    sellLimitOrderResp.setPrice("6000.012");
    sellLimitOrderResp.setStatus(OrderStatus.NEW);
    when(mockBinanceApiRestClient.newOrder(any())).thenReturn(sellLimitOrderResp);

    binanceTradingBot.placeTrade(getChartPatternSignal().build());

    verify(mockBinanceApiRestClient).newOrder(buyOrderCaptor.capture());
    NewOrder buyOrder = buyOrderCaptor.getValue();
    assertThat(buyOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(buyOrder.getSide()).isEqualTo(OrderSide.BUY);
    assertThat(buyOrder.getType()).isEqualTo(OrderType.MARKET);
    assertThat(buyOrder.getQuantity()).isEqualTo("0.005");
    verify(mockDao).setEntryOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(
            buyOrderResp.getOrderId(),
            /* TODO: Find the actual price from the associated Trade */
            chartPatternSignal.priceAtTimeOfSignalReal(),
            20.0, OrderStatus.FILLED));
    verify(mockBinanceApiRestClient).newOrder(sellOrderCaptor.capture());
    NewOrder sellLimitOrder = sellOrderCaptor.getValue();
    assertThat(sellLimitOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(sellLimitOrder.getSide()).isEqualTo(OrderSide.SELL);
    assertThat(sellLimitOrder.getType()).isEqualTo(OrderType.LIMIT);
    assertThat(sellLimitOrder.getTimeInForce()).isEqualTo(TimeInForce.GTC);
    assertThat(sellLimitOrder.getQuantity()).isEqualTo("20.0");
    assertThat(sellLimitOrder.getPrice()).isEqualTo("6000.012");
    verify(mockDao).setExitLimitOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(
            sellLimitOrderResp.getOrderId(), 6000.01, 0.0,
            OrderStatus.NEW));
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