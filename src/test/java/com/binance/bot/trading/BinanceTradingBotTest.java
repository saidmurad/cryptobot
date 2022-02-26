package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
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
  BinanceApiMarginRestClient mockBinanceApiMarginRestClient;
  @Mock
  ChartPatternSignalDaoImpl mockDao;
  @Mock SupportedSymbolsInfo mockSupportedSymbolsInfo;
  private BinanceTradingBot binanceTradingBot;
  @Mock private BookTickerPrices mockBookTickerPrices;
  @Captor
  ArgumentCaptor<MarginNewOrder> orderCaptor;
  @Captor ArgumentCaptor<ChartPatternSignal> chartPatternSignalArgumentCaptor;
  @Captor ArgumentCaptor<ChartPatternSignal.Order> chartPatternSignalOrderArgumentCaptor;
  private static final double BTC_PRICE = 40000;
  
  @Before
  public void setUp() throws BinanceApiException {
    when(mockSupportedSymbolsInfo.getTradingActiveSymbols()).thenReturn(Map.of("ETHUSDT", Lists.newArrayList()));
    when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    when(mockBinanceApiClientFactory.newMarginRestClient()).thenReturn(mockBinanceApiMarginRestClient);
    TickerPrice tickerPrice = new TickerPrice();
    tickerPrice.setPrice("4000");
    when(mockBinanceApiRestClient.getPrice("ETHUSDT")).thenReturn(tickerPrice);
    binanceTradingBot = new BinanceTradingBot(mockBinanceApiClientFactory, mockSupportedSymbolsInfo, mockDao, mockBookTickerPrices);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    BookTickerPrices.BookTicker btcBookTicker = BookTickerPrices.BookTicker.create(BTC_PRICE, BTC_PRICE); 
    when(mockBookTickerPrices.getBookTicker("BTCUSDT")).thenReturn(btcBookTicker);
    binanceTradingBot.perTradeAmountConfigured = 20.0;
    binanceTradingBot.fifteenMinuteTimeFrameAllowedTradeTypeConfig = "BOTH";
    binanceTradingBot.hourlyTimeFrameAllowedTradeTypeConfig = "BOTH";
    binanceTradingBot.fourHourlyTimeFrameAllowedTradeTypeConfig = "BOTH";
    binanceTradingBot.dailyTimeFrameAllowedTradeTypeConfig = "BOTH";
    binanceTradingBot.minMarginLevel = 1.5;
    binanceTradingBot.stopLossPercent = 5.0;
    binanceTradingBot.stopLimitPercent = 5.5;
  }

  private void setUsdtBalanceForStraightBuys(Integer usdtBal) throws BinanceApiException {
    setUsdtBalance(usdtBal, 0);
  }

  private void setUsdtBalance(Integer usdtFreeBal, Integer usdtValBorrowedAndLocked) throws BinanceApiException {
    MarginAccount account = new MarginAccount();
    MarginAssetBalance usdtBalance = new MarginAssetBalance();
    usdtBalance.setAsset("USDT");
    usdtBalance.setFree(usdtFreeBal.toString());
    usdtBalance.setBorrowed(usdtValBorrowedAndLocked.toString());
    usdtBalance.setLocked(usdtValBorrowedAndLocked.toString());
    account.setUserAssets(Lists.newArrayList(usdtBalance));
    Double netValBtc = usdtFreeBal / BTC_PRICE;
    Double totalValBtc = (usdtFreeBal + usdtValBorrowedAndLocked)/ BTC_PRICE;
    Double liabValBtc = usdtValBorrowedAndLocked / BTC_PRICE;
    account.setTotalAssetOfBtc(totalValBtc.toString());
    account.setTotalNetAssetOfBtc(netValBtc.toString());
    account.setTotalLiabilityOfBtc(liabValBtc.toString());
    Double marginLevel = liabValBtc > 0? totalValBtc / liabValBtc : 99;
    account.setMarginLevel(marginLevel.toString());
    when(mockBinanceApiMarginRestClient.getAccount()).thenReturn(account);
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
  public void perform_tradeTypeNotAllowed_doesntPlaceTrade() throws ParseException, BinanceApiException {
    binanceTradingBot.fifteenMinuteTimeFrameAllowedTradeTypeConfig = "NONE";
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.BUY)
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiMarginRestClient, never()).getAccount();
    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_doesntPlaceTrade() throws ParseException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.BUY)
        .setProfitPotentialPercent(0.5)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -16))
            .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiMarginRestClient, never()).getAccount();
    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void symbolNotTradingAtTheMoment_doesntPlaceTrade() throws ParseException, BinanceApiException {
    when(mockSupportedSymbolsInfo.getTradingActiveSymbols()).thenReturn(Map.of());
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.BUY)
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiMarginRestClient, never()).getAccount();
    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void lotSizeMapReturnsNull_doesNothing() throws ParseException, BinanceApiException {
    setUsdtBalanceForStraightBuys(120);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT")).thenReturn(null);
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.BUY)
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_and_profitPotentialIsThere_butVeryLate_Hourly_doesntPlaceTrade() throws ParseException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.HOUR)
        .setTradeType(TradeType.BUY)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -61))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.HOUR, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_but_profitPotentialIsThere_andNotVeryLate_Hourly_placesTrade() throws ParseException, BinanceApiException {
    binanceTradingBot.stopLossPercent = 5.0;
    setUsdtBalanceForStraightBuys(120);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.HOUR)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -59))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.HOUR, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));
    MarginNewOrderResponse buyOrderResp = new MarginNewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
    buyOrderResp.setExecutedQty("0.005");
    buyOrderResp.setStatus(OrderStatus.FILLED);

    MarginNewOrderResponse sellStopLossOrderResp = new MarginNewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiMarginRestClient.newOrder(any(MarginNewOrder.class))).thenAnswer(new Answer<MarginNewOrderResponse>() {
      private int count = 0;
      @Override
      public MarginNewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, times(2)).newOrder(orderCaptor.capture());
  }

  @Test
  public void perform_insertedLate_and_profitPotentialIsThere_butVeryLate_FourHourly_doesntPlaceTrade() throws ParseException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTradeType(TradeType.BUY)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -121))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FOUR_HOURS, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiMarginRestClient, never()).getAccount();
    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_but_profitPotentialIsThere_andNotVeryLate_FourHourly_placesTrade() throws ParseException, BinanceApiException {
    binanceTradingBot.stopLossPercent = 5.0;
    setUsdtBalanceForStraightBuys(120);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -119))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FOUR_HOURS, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));
    MarginNewOrderResponse buyOrderResp = new MarginNewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
    buyOrderResp.setExecutedQty("0.005");
    buyOrderResp.setStatus(OrderStatus.FILLED);

    MarginNewOrderResponse sellStopLossOrderResp = new MarginNewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiMarginRestClient.newOrder(any(MarginNewOrder.class))).thenAnswer(new Answer<MarginNewOrderResponse>() {
      private int count = 0;
      @Override
      public MarginNewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, times(2)).newOrder(orderCaptor.capture());
  }

  @Test
  public void perform_insertedLate_and_profitPotentialIsThere_butVeryLate_Daily_doesntPlaceTrade() throws ParseException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.DAY)
        .setTradeType(TradeType.BUY)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -241))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.DAY, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiMarginRestClient, never()).getAccount();
    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  @Test
  public void perform_insertedLate_but_profitPotentialIsThere_andNotVeryLate_Daily_placesTrade() throws ParseException, BinanceApiException {
    binanceTradingBot.stopLossPercent = 5.0;
    setUsdtBalanceForStraightBuys(120);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.DAY)
        .setTimeOfSignal(DateUtils.addMinutes(new Date(), -239))
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.DAY, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));
    MarginNewOrderResponse buyOrderResp = new MarginNewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
    buyOrderResp.setExecutedQty("0.005");
    buyOrderResp.setStatus(OrderStatus.FILLED);

    MarginNewOrderResponse sellStopLossOrderResp = new MarginNewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiMarginRestClient.newOrder(any(MarginNewOrder.class))).thenAnswer(new Answer<MarginNewOrderResponse>() {
      private int count = 0;
      @Override
      public MarginNewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, times(2)).newOrder(orderCaptor.capture());
  }

  @Test
  public void testPlaceBuyTrade_usesOnlyPerTradeAmount() throws ParseException, BinanceApiException {
    binanceTradingBot.stopLossPercent = 5.0;
    setUsdtBalanceForStraightBuys(120);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    MarginNewOrderResponse buyOrderResp = new MarginNewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
    buyOrderResp.setExecutedQty("0.005");
    buyOrderResp.setStatus(OrderStatus.FILLED);

    MarginNewOrderResponse sellStopLossOrderResp = new MarginNewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiMarginRestClient.newOrder(any(MarginNewOrder.class))).thenAnswer(new Answer<MarginNewOrderResponse>() {
      private int count = 0;
      @Override
      public MarginNewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, times(2)).newOrder(orderCaptor.capture());
    MarginNewOrder buyOrder = orderCaptor.getAllValues().get(0);
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
    MarginNewOrder sellStopLossOrder = orderCaptor.getAllValues().get(1);
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
  public void buyTrade_ableToBorrow() throws ParseException, BinanceApiException {
    binanceTradingBot.perTradeAmountConfigured = 20.0;
    // Will borrow the $1 more needed.
    setUsdtBalanceForStraightBuys(19);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT")).thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    MarginNewOrderResponse buyOrderResp = new MarginNewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
    buyOrderResp.setExecutedQty("0.005");
    buyOrderResp.setStatus(OrderStatus.FILLED);

    MarginNewOrderResponse sellStopLossOrderResp = new MarginNewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiMarginRestClient.newOrder(any(MarginNewOrder.class))).thenAnswer(new Answer<MarginNewOrderResponse>() {
      private int count = 0;
      @Override
      public MarginNewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient).borrow("USDT", "1");
    verify(mockBinanceApiMarginRestClient, times(2)).newOrder(orderCaptor.capture());
    MarginNewOrder buyOrder = orderCaptor.getAllValues().get(0);
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
    MarginNewOrder sellStopLossOrder = orderCaptor.getAllValues().get(1);
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
  public void buyTrade_atTheBorrowLimit_doesntPlaceTrade() throws ParseException, BinanceApiException {
    binanceTradingBot.perTradeAmountConfigured = 20.0;
    // Can't borrow the $1 more needed because borrowed amount is maxed out at margin level 1.5 (3 * 19 / (2 * 19)).
    setUsdtBalance(19, 38);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT")).thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
  }

  /*@Test
  public void marginThreshold_SellTrade_doesNothing() throws ParseException, BinanceApiException {
    MarginAccount marginAccount = new MarginAccount();
    marginAccount.setMarginLevel("2.0");
    when(mockBinanceApiMarginRestClient.getAccount()).thenReturn(marginAccount);
    binanceTradingBot.placeTrade(getChartPatternSignal().build());

    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verifyNoInteractions(mockDao);
  }*/

  @Test
  public void perTradeAmount_greaterThanAdjustedMinNotional_usesPerTradeAmountItself() throws ParseException, BinanceApiException {
    binanceTradingBot.perTradeAmountConfigured = 11;
    setUsdtBalanceForStraightBuys(120);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    MarginNewOrderResponse buyOrderResp = new MarginNewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
    buyOrderResp.setExecutedQty("0.0028");
    buyOrderResp.setStatus(OrderStatus.FILLED);

    MarginNewOrderResponse sellStopLossOrderResp = new MarginNewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiMarginRestClient.newOrder(any(MarginNewOrder.class))).thenAnswer(new Answer<MarginNewOrderResponse>() {
      private int count = 0;
      @Override
      public MarginNewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, times(2)).newOrder(orderCaptor.capture());
    MarginNewOrder buyOrder = orderCaptor.getAllValues().get(0);
    assertThat(buyOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(buyOrder.getSide()).isEqualTo(OrderSide.BUY);
    assertThat(buyOrder.getType()).isEqualTo(OrderType.MARKET);
    assertThat(buyOrder.getQuantity()).isEqualTo("0.0028");
  }

  @Test
  public void roundsUpQtyNotDown_and_limitsToStepSizeNumDigits_perTradeAmountIsIgnoredIfLessThanMinNotionalWithAdjustment() throws ParseException, BinanceApiException {
    binanceTradingBot.perTradeAmountConfigured = 10.1;
    setUsdtBalanceForStraightBuys(120);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    MarginNewOrderResponse buyOrderResp = new MarginNewOrderResponse();
    buyOrderResp.setOrderId(1L);
    buyOrderResp.setPrice("0.0");
    buyOrderResp.setExecutedQty("0.0026"); // 0.002525 is rounded up.
    buyOrderResp.setStatus(OrderStatus.FILLED);

    MarginNewOrderResponse sellStopLossOrderResp = new MarginNewOrderResponse();
    sellStopLossOrderResp.setOrderId(2L);
    sellStopLossOrderResp.setExecutedQty("0");
    sellStopLossOrderResp.setPrice("3800");
    sellStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiMarginRestClient.newOrder(any(MarginNewOrder.class))).thenAnswer(new Answer<MarginNewOrderResponse>() {
      private int count = 0;
      @Override
      public MarginNewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return buyOrderResp;
        }
        return sellStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, times(2)).newOrder(orderCaptor.capture());
    MarginNewOrder buyOrder = orderCaptor.getAllValues().get(0);
    assertThat(buyOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(buyOrder.getSide()).isEqualTo(OrderSide.BUY);
    assertThat(buyOrder.getType()).isEqualTo(OrderType.MARKET);
    assertThat(buyOrder.getQuantity()).isEqualTo("0.0028"); // Equivalent of $10.52 = 0.00263 rounded up after adjusted for stop loss notional.
  }

  @Test
  public void testPlaceSellTrade_underBorrowLimit_borrows() throws ParseException, BinanceApiException {
    binanceTradingBot.stopLossPercent = 5.0;
    // Allows for an additional $20 to be borrowed and new margin level will be 1.5
    setUsdtBalance(12, 4);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT"))
        .thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTradeType(TradeType.SELL)
        .setPriceTarget(3000)
        .build();
    MarginNewOrderResponse sellOrderResp = new MarginNewOrderResponse();
    sellOrderResp.setOrderId(1L);
    sellOrderResp.setPrice("0.0");
    sellOrderResp.setExecutedQty("0.005");
    sellOrderResp.setStatus(OrderStatus.FILLED);

    MarginNewOrderResponse buyStopLossOrderResp = new MarginNewOrderResponse();
    buyStopLossOrderResp.setOrderId(2L);
    buyStopLossOrderResp.setExecutedQty("0");
    buyStopLossOrderResp.setPrice("4200");
    buyStopLossOrderResp.setStatus(OrderStatus.NEW);

    when(mockBinanceApiMarginRestClient.newOrder(any(MarginNewOrder.class))).thenAnswer(new Answer<MarginNewOrderResponse>() {
      private int count = 0;
      @Override
      public MarginNewOrderResponse answer(InvocationOnMock invocationOnMock) {
        if (count == 0) {
          count ++;
          return sellOrderResp;
        }
        return buyStopLossOrderResp;
      }
    });

    binanceTradingBot.placeTrade(chartPatternSignal);

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, times(2)).newOrder(orderCaptor.capture());
    MarginNewOrder buyOrder = orderCaptor.getAllValues().get(0);
    assertThat(buyOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(buyOrder.getSide()).isEqualTo(OrderSide.SELL);
    assertThat(buyOrder.getType()).isEqualTo(OrderType.MARKET);
    assertThat(buyOrder.getQuantity()).isEqualTo("0.005"); // assuming market price $4000, and buying for $100.
    verify(mockDao).setEntryOrder(chartPatternSignalArgumentCaptor.capture(),
        chartPatternSignalOrderArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue()).isEqualTo(chartPatternSignal);
    assertThat(chartPatternSignalOrderArgumentCaptor.getValue()).isEqualTo(ChartPatternSignal.Order.create(
        sellOrderResp.getOrderId(),
        0.005,
        /* TODO: Find the actual price from the associated Trade */
        chartPatternSignal.priceAtTimeOfSignalReal(),
        OrderStatus.FILLED));
    MarginNewOrder sellStopLossOrder = orderCaptor.getAllValues().get(1);
    assertThat(sellStopLossOrder.getSymbol()).isEqualTo("ETHUSDT");
    assertThat(sellStopLossOrder.getSide()).isEqualTo(OrderSide.BUY);
    assertThat(sellStopLossOrder.getType()).isEqualTo(OrderType.STOP_LOSS_LIMIT);
    assertThat(sellStopLossOrder.getTimeInForce()).isEqualTo(TimeInForce.GTC);
    assertThat(sellStopLossOrder.getQuantity()).isEqualTo("0.005");
    assertThat(sellStopLossOrder.getStopPrice()).isEqualTo("4200.00");
    assertThat(sellStopLossOrder.getPrice()).isEqualTo("4220.00");
    verify(mockDao).setExitStopLimitOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(
            buyStopLossOrderResp.getOrderId(), 0.0, 0,
            OrderStatus.NEW));
  }

  @Test
  public void sellTrade_atTheBorrowLimit_doesntPlaceTrade() throws ParseException, BinanceApiException {
    binanceTradingBot.perTradeAmountConfigured = 20.0;
    // Can't borrow the $20 more needed because new margin level will be 37/25=1.48 whihc is < 1.5
    setUsdtBalance(12, 5);
    when(mockSupportedSymbolsInfo.getMinNotionalAndLotSize("ETHUSDT")).thenReturn(Pair.of(10.0, 4));
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTradeType(TradeType.SELL)
        .setPriceTarget(3000)
        .build();
    when(mockDao.getChartPatternSignalsToPlaceTrade(TimeFrame.FIFTEEN_MINUTES, TradeType.SELL))
        .thenReturn(Lists.newArrayList(chartPatternSignal));

    binanceTradingBot.perform();

    verify(mockBinanceApiMarginRestClient).getAccount();
    verify(mockBinanceApiMarginRestClient, never()).newOrder(any());
    verify(mockDao, never()).setEntryOrder(any(), any());
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