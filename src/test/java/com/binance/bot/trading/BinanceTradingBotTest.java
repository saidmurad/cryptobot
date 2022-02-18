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
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.DateUtils;
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
import java.util.Map;
import java.util.Set;

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
  @Captor
  ArgumentCaptor<NewOrder> orderCaptor;
  @Captor ArgumentCaptor<ChartPatternSignal> chartPatternSignalArgumentCaptor;
  @Captor ArgumentCaptor<ChartPatternSignal.Order> chartPatternSignalOrderArgumentCaptor;

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    TickerPrice tickerPrice = new TickerPrice();
    tickerPrice.setPrice("4000");
    when(mockBinanceApiRestClient.getPrice("ETHUSDT")).thenReturn(tickerPrice);
    binanceTradingBot = new BinanceTradingBot(mockBinanceApiClientFactory, mockSupportedSymbolsInfo, mockDao);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    binanceTradingBot.perTradeAmount = 20.0;
    binanceTradingBot.fifteenMinuteTimeFrameAllowedTradeTypeConfig = "BOTH";
    binanceTradingBot.hourlyTimeFrameAllowedTradeTypeConfig = "BOTH";
    binanceTradingBot.fourHourlyTimeFrameAllowedTradeTypeConfig = "BOTH";
    binanceTradingBot.dailyTimeFrameAllowedTradeTypeConfig = "BOTH";
    binanceTradingBot.stopLossPercent = 5.0;
    binanceTradingBot.stopLimitPercent = 5.5;
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

  @Test
  public void testIsTradingAllowed_none() {
    binanceTradingBot.fifteenMinuteTimeFrameAllowedTradeTypeConfig = "NONE";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY)).isFalse();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FIFTEEN_MINUTES, TradeType.SELL)).isFalse();

    binanceTradingBot.hourlyTimeFrameAllowedTradeTypeConfig = "NONE";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.HOUR, TradeType.BUY)).isFalse();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.HOUR, TradeType.SELL)).isFalse();

    binanceTradingBot.fourHourlyTimeFrameAllowedTradeTypeConfig = "NONE";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FOUR_HOURS, TradeType.BUY)).isFalse();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FOUR_HOURS, TradeType.SELL)).isFalse();

    binanceTradingBot.dailyTimeFrameAllowedTradeTypeConfig = "NONE";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.DAY, TradeType.BUY)).isFalse();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.DAY, TradeType.SELL)).isFalse();
  }

  @Test
  public void testIsTradingAllowed_buyOnly() {
    binanceTradingBot.fifteenMinuteTimeFrameAllowedTradeTypeConfig = "BUY";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY)).isTrue();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FIFTEEN_MINUTES, TradeType.SELL)).isFalse();

    binanceTradingBot.hourlyTimeFrameAllowedTradeTypeConfig = "BUY";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.HOUR, TradeType.BUY)).isTrue();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.HOUR, TradeType.SELL)).isFalse();

    binanceTradingBot.fourHourlyTimeFrameAllowedTradeTypeConfig = "BUY";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FOUR_HOURS, TradeType.BUY)).isTrue();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FOUR_HOURS, TradeType.SELL)).isFalse();

    binanceTradingBot.dailyTimeFrameAllowedTradeTypeConfig = "BUY";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.DAY, TradeType.BUY)).isTrue();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.DAY, TradeType.SELL)).isFalse();
  }

  @Test
  public void testIsTradingAllowed_sellOnly() {
    binanceTradingBot.fifteenMinuteTimeFrameAllowedTradeTypeConfig = "SELL";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY)).isFalse();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FIFTEEN_MINUTES, TradeType.SELL)).isTrue();

    binanceTradingBot.hourlyTimeFrameAllowedTradeTypeConfig = "SELL";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.HOUR, TradeType.BUY)).isFalse();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.HOUR, TradeType.SELL)).isTrue();

    binanceTradingBot.fourHourlyTimeFrameAllowedTradeTypeConfig = "SELL";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FOUR_HOURS, TradeType.BUY)).isFalse();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FOUR_HOURS, TradeType.SELL)).isTrue();

    binanceTradingBot.dailyTimeFrameAllowedTradeTypeConfig = "SELL";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.DAY, TradeType.BUY)).isFalse();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.DAY, TradeType.SELL)).isTrue();
  }

  @Test
  public void testIsTradingAllowed_both() {
    binanceTradingBot.fifteenMinuteTimeFrameAllowedTradeTypeConfig = "BOTH";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY)).isTrue();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FIFTEEN_MINUTES, TradeType.SELL)).isTrue();

    binanceTradingBot.hourlyTimeFrameAllowedTradeTypeConfig = "BOTH";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.HOUR, TradeType.BUY)).isTrue();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.HOUR, TradeType.SELL)).isTrue();

    binanceTradingBot.fourHourlyTimeFrameAllowedTradeTypeConfig = "BOTH";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FOUR_HOURS, TradeType.BUY)).isTrue();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.FOUR_HOURS, TradeType.SELL)).isTrue();

    binanceTradingBot.dailyTimeFrameAllowedTradeTypeConfig = "BOTH";
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.DAY, TradeType.BUY)).isTrue();
    assertThat(binanceTradingBot.isTradingAllowed(TimeFrame.DAY, TradeType.SELL)).isTrue();
  }

  @Test
  public void perform_tradeTypeNotAllowed_doesntPlaceTrade() throws ParseException {
    binanceTradingBot.fifteenMinuteTimeFrameAllowedTradeTypeConfig = "NONE";
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.BUY)
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_doesntPlaceTrade() throws ParseException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.BUY)
        .setProfitPotentialPercent(0.5)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -16))
            .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void symbolNotTradingAtTheMoment_doesntPlaceTrade() throws ParseException {
    when(mockSupportedSymbolsInfo.getTradingActiveSymbols()).thenReturn(Map.of());
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.BUY)
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void lotSizeMapReturnsNull_doesNothing() throws ParseException {
    setUsdtBalance(120.0);
    when(mockSupportedSymbolsInfo.getTradingActiveSymbols()).thenReturn(Map.of("ETHUSDT", Lists.newArrayList()));
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT")).thenReturn(null);
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.BUY)
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_and_profitPotentialIsThere_butVeryLate_Hourly_doesntPlaceTrade() throws ParseException {
    when(mockSupportedSymbolsInfo.getSupportedSymbols()).thenReturn(Set.of());
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.HOUR)
        .setTradeType(TradeType.BUY)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -61))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.HOUR, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_but_profitPotentialIsThere_andNotVeryLate_Hourly_placesTrade() throws ParseException {
    binanceTradingBot.stopLossPercent = 5.0;
    setUsdtBalance(120.0);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.HOUR)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -59))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.HOUR, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
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
  }

  @Test
  public void perform_insertedLate_and_profitPotentialIsThere_butVeryLate_FourHourly_doesntPlaceTrade() throws ParseException {
    when(mockSupportedSymbolsInfo.getSupportedSymbols()).thenReturn(Set.of());
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTradeType(TradeType.BUY)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -121))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FOUR_HOURS, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_but_profitPotentialIsThere_andNotVeryLate_FourHourly_placesTrade() throws ParseException {
    binanceTradingBot.stopLossPercent = 5.0;
    setUsdtBalance(120.0);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -119))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FOUR_HOURS, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
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
  }

  @Test
  public void perform_insertedLate_and_profitPotentialIsThere_butVeryLate_Daily_doesntPlaceTrade() throws ParseException {
    when(mockSupportedSymbolsInfo.getSupportedSymbols()).thenReturn(Set.of());
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.DAY)
        .setTradeType(TradeType.BUY)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -241))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.DAY, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_but_profitPotentialIsThere_andNotVeryLate_Daily_placesTrade() throws ParseException {
    binanceTradingBot.stopLossPercent = 5.0;
    setUsdtBalance(120.0);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.DAY)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -239))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.DAY, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
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
  }

  @Test
  public void testPlaceBuyTrade_usesOnlyPerTradeAmount() throws ParseException {
    binanceTradingBot.stopLossPercent = 5.0;
    setUsdtBalance(120.0);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
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
    assertThat(sellStopLossOrder.getType()).isEqualTo(OrderType.STOP_LOSS_LIMIT);
    assertThat(sellStopLossOrder.getTimeInForce()).isEqualTo(TimeInForce.GTC);
    assertThat(sellStopLossOrder.getQuantity()).isEqualTo("0.005");
    assertThat(sellStopLossOrder.getStopPrice()).isEqualTo("3800.00");
    assertThat(sellStopLossOrder.getPrice()).isEqualTo("3780.00");
    verify(mockDao).setExitStopLimitOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(
            sellStopLossOrderResp.getOrderId(), 0.0, 0,
            OrderStatus.NEW));
  }

  @Test
  public void insufficientUSDTInAccount_doesNothing() throws ParseException {
    binanceTradingBot.perTradeAmount = 20.0;
    setUsdtBalance(19.5);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));

    binanceTradingBot.placeTrade(getChartPatternSignal().build());

    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verifyNoInteractions(mockDao);
  }

  @Test
  public void perTradeAmount_greaterThanAdjustedMinNotional_usesPerTradeAmountItself() throws ParseException {
    binanceTradingBot.perTradeAmount = 11;
    setUsdtBalance(120.0);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
    buyOrderResp.setExecutedQty("0.0028");
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
    assertThat(buyOrder.getQuantity()).isEqualTo("0.0028");
  }

  @Test
  public void roundsUpQtyNotDown_and_limitsToStepSizeNumDigits_perTradeAmountIsIgnoredIfLessThanMinNotionalWithAdjustment() throws ParseException {
    binanceTradingBot.perTradeAmount = 10.1;
    setUsdtBalance(120.0);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    NewOrderResponse buyOrderResp = new NewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
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
    assertThat(buyOrder.getQuantity()).isEqualTo("0.0028"); // Equivalent of $10.52 = 0.00263 rounded up after adjusted for stop loss notional.
  }

  @Test
  public void formattedQuantity_forWholeLotSizeOnly() {
    assertThat(binanceTradingBot.getFormattedQuantity(10.123, 0)).isEqualTo("11");
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