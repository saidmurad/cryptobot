package com.binance.bot.signalsuccessfailure.specifictradeactions;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.Mailer;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.OutstandingTrades;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.RepayBorrowedOnMargin;
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
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class ExitPositionAtMarketPriceTest {

  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock private ChartPatternSignalDaoImpl mockDao;
  @Mock private BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock private BinanceApiMarginRestClient mockBinanceApiRestClient;
  @Mock private Mailer mockMailer;
  @Mock private RepayBorrowedOnMargin mockRepayBorrowedOnMargin;
  @Mock private OutstandingTrades mockOutstandingTrades;
  private final long timeOfSignal = System.currentTimeMillis();
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newMarginRestClient()).thenReturn(mockBinanceApiRestClient);
    exitPositionAtMarketPrice = new ExitPositionAtMarketPrice(
        mockBinanceApiClientFactory, mockDao, mockMailer, mockRepayBorrowedOnMargin, mockOutstandingTrades);
  }

  private ExitPositionAtMarketPrice exitPositionAtMarketPrice;

  @Test
  public void exitPositionIfStillHeld_isExitedReturnsNull_doesNothing() throws MessagingException, ParseException, BinanceApiException {
    exitPositionAtMarketPrice.doNotDecrementNumOutstandingTrades = false;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsPositionExited(null).build();

    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TARGET_TIME_PASSED);

    verifyNoInteractions(mockDao);
    verifyNoInteractions(mockOutstandingTrades);
  }

  @Test
  public void exitPositionIfStillHeld_isExitedReturnsTrue_doesNothing() throws MessagingException, ParseException, BinanceApiException {
    exitPositionAtMarketPrice.doNotDecrementNumOutstandingTrades = false;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setIsPositionExited(true).build();

    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TARGET_TIME_PASSED);

    verifyNoInteractions(mockDao);
  }

  @Captor ArgumentCaptor<OrderStatusRequest> stopLossOrderStatusRequestCapture;
  @Captor
  ArgumentCaptor<CancelOrderRequest> cancelOrderRequestCapture;
  @Captor ArgumentCaptor<MarginNewOrder> newOrderCapture;

  @Test
  public void exitPositionIfStillHeld_stopLossStatusReturnsFILLED_returnsRightAfter()
      throws MessagingException, BinanceApiException {
    exitPositionAtMarketPrice.doNotDecrementNumOutstandingTrades = false;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsPositionExited(false)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 10.0, 20.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))
        .build();
    Order exitStopLossOrderStatus = new Order();
    exitStopLossOrderStatus.setOrderId(2L);
    exitStopLossOrderStatus.setStatus(OrderStatus.FILLED);
    exitStopLossOrderStatus.setPrice("17.0");
    exitStopLossOrderStatus.setExecutedQty("10.0");
    when(mockBinanceApiRestClient.getOrderStatus(any())).thenReturn(exitStopLossOrderStatus);
    chartPatternSignal = ChartPatternSignal.newBuilder().copy(chartPatternSignal)
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 10.0, 17.0, OrderStatus.FILLED))
        .setIsPositionExited(true)
        .build();
    when(mockDao.getChartPattern(chartPatternSignal)).thenReturn(chartPatternSignal);

    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TARGET_TIME_PASSED);

    verify(mockBinanceApiRestClient, never()).cancelOrder(any());
    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verify(mockDao, never()).setExitOrder(any(), any(), any());
    verifyNoInteractions(mockRepayBorrowedOnMargin);
    verifyNoInteractions(mockOutstandingTrades);
  }

  @Test
  public void exitPositionIfStillHeld_buySignal_noPartialStopLossTrade_exitsTradeForFullQty()
      throws MessagingException, ParseException, BinanceApiException {
    exitPositionAtMarketPrice.doNotDecrementNumOutstandingTrades = false;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsPositionExited(false)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 10.0, 20.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))
        .build();
    Order exitStopLossOrderStatus = new Order();
    exitStopLossOrderStatus.setOrderId(1L);
    exitStopLossOrderStatus.setStatus(OrderStatus.NEW);
    when(mockBinanceApiRestClient.getOrderStatus(any())).thenReturn(exitStopLossOrderStatus);
    // Return the same unchanged exit stop loss order status as NEW.
    when(mockDao.getChartPattern(chartPatternSignal)).thenReturn(chartPatternSignal);
    CancelOrderResponse cancelOrderResponse = new CancelOrderResponse();
    cancelOrderResponse.setOrderId(2L);
    cancelOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponse);
    MarginAssetBalance ethBalance = new MarginAssetBalance();
    ethBalance.setAsset("ETH");
    ethBalance.setFree("10.0");
    ethBalance.setLocked("0.0");
    MarginAccount account = new MarginAccount();
    account.setUserAssets(Lists.newArrayList(ethBalance));
    when(mockBinanceApiRestClient.getAccount()).thenReturn(account);
    MarginNewOrderResponse exitMarketOrderResponse = new MarginNewOrderResponse();
    exitMarketOrderResponse.setOrderId(3L);
    exitMarketOrderResponse.setExecutedQty("10.0");
    Trade fill1 = new Trade();
    fill1.setPrice("0.9");
    fill1.setQty("5.0");
    Trade fill2 = new Trade();
    fill2.setPrice("1.1");
    fill2.setQty("5.0");
    exitMarketOrderResponse.setFills(Lists.newArrayList(fill1, fill2));
    exitMarketOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.newOrder(any(MarginNewOrder.class))).thenReturn(exitMarketOrderResponse);

    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TARGET_TIME_PASSED);

    verify(mockBinanceApiRestClient).getOrderStatus(stopLossOrderStatusRequestCapture.capture());
    verify(mockDao).updateExitStopLimitOrder(any(), eq(
        ChartPatternSignal.Order.create(exitStopLossOrderStatus.getOrderId(),
            0,
            0, exitStopLossOrderStatus.getStatus())
    ));
    assertThat(stopLossOrderStatusRequestCapture.getValue().getOrderId()).isEqualTo(2L);
    verify(mockBinanceApiRestClient).cancelOrder(cancelOrderRequestCapture.capture());
    assertThat(cancelOrderRequestCapture.getValue().getOrderId()).isEqualTo(2);
    assertThat(cancelOrderRequestCapture.getValue().getSymbol()).isEqualTo("ETHUSDT");
    verify(mockDao).cancelStopLimitOrder(eq(chartPatternSignal));
    verify(mockBinanceApiRestClient).newOrder(newOrderCapture.capture());
    assertThat(newOrderCapture.getValue().getSymbol()).isEqualTo("ETHUSDT");
    assertThat(newOrderCapture.getValue().getSide()).isEqualTo(OrderSide.SELL);
    assertThat(newOrderCapture.getValue().getType()).isEqualTo(OrderType.MARKET);
    assertThat(newOrderCapture.getValue().getTimeInForce()).isNull();
    assertThat(newOrderCapture.getValue().getQuantity()).isEqualTo("10.0");
    verify(mockDao).setExitOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(3L,
            10.0, 1.0, OrderStatus.FILLED), TradeExitType.TARGET_TIME_PASSED);
    verify(mockDao).writeAccountBalanceToDB();
    verify(mockOutstandingTrades).decrementNumOutstandingTrades(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void alreadyCanceledStopLossBySomeOccurence_exitsTradeForProfitTaking()
      throws MessagingException, ParseException, BinanceApiException {
    exitPositionAtMarketPrice.doNotDecrementNumOutstandingTrades = false;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsPositionExited(false)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 10.0, 20.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))
        .build();
    Order exitStopLossOrderStatus = new Order();
    exitStopLossOrderStatus.setOrderId(1L);
    exitStopLossOrderStatus.setStatus(OrderStatus.CANCELED);
    when(mockBinanceApiRestClient.getOrderStatus(any())).thenReturn(exitStopLossOrderStatus);
    // Return the same unchanged exit stop loss order status as NEW.
    when(mockDao.getChartPattern(chartPatternSignal)).thenReturn(chartPatternSignal);
    MarginAssetBalance ethBalance = new MarginAssetBalance();
    ethBalance.setAsset("ETH");
    ethBalance.setFree("10.0");
    ethBalance.setLocked("0.0");
    MarginAccount account = new MarginAccount();
    account.setUserAssets(Lists.newArrayList(ethBalance));
    when(mockBinanceApiRestClient.getAccount()).thenReturn(account);
    MarginNewOrderResponse exitMarketOrderResponse = new MarginNewOrderResponse();
    exitMarketOrderResponse.setOrderId(3L);
    exitMarketOrderResponse.setExecutedQty("10.0");
    Trade fill1 = new Trade();
    fill1.setPrice("0.9");
    fill1.setQty("5.0");
    Trade fill2 = new Trade();
    fill2.setPrice("1.1");
    fill2.setQty("5.0");
    exitMarketOrderResponse.setFills(Lists.newArrayList(fill1, fill2));
    exitMarketOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.newOrder(any(MarginNewOrder.class))).thenReturn(exitMarketOrderResponse);

    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TARGET_TIME_PASSED);

    verify(mockBinanceApiRestClient).getOrderStatus(stopLossOrderStatusRequestCapture.capture());
    verify(mockDao).updateExitStopLimitOrder(any(), eq(
        ChartPatternSignal.Order.create(exitStopLossOrderStatus.getOrderId(),
            0,
            0, exitStopLossOrderStatus.getStatus())
    ));
    assertThat(stopLossOrderStatusRequestCapture.getValue().getOrderId()).isEqualTo(2L);
    verify(mockBinanceApiRestClient, never()).cancelOrder(any(CancelOrderRequest.class));
    verify(mockDao, never()).cancelStopLimitOrder(any(ChartPatternSignal.class));
    verify(mockBinanceApiRestClient).newOrder(newOrderCapture.capture());
    assertThat(newOrderCapture.getValue().getSymbol()).isEqualTo("ETHUSDT");
    assertThat(newOrderCapture.getValue().getSide()).isEqualTo(OrderSide.SELL);
    assertThat(newOrderCapture.getValue().getType()).isEqualTo(OrderType.MARKET);
    assertThat(newOrderCapture.getValue().getTimeInForce()).isNull();
    assertThat(newOrderCapture.getValue().getQuantity()).isEqualTo("10.0");
    verify(mockDao).setExitOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(3L,
            10.0, 1.0, OrderStatus.FILLED), TradeExitType.TARGET_TIME_PASSED);
    verify(mockDao).writeAccountBalanceToDB();
    verify(mockOutstandingTrades).decrementNumOutstandingTrades(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void decrementNumOutstandingTrades()
      throws MessagingException, ParseException, BinanceApiException {
    exitPositionAtMarketPrice.doNotDecrementNumOutstandingTrades = true;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsPositionExited(false)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 10.0, 20.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))
        .build();
    Order exitStopLossOrderStatus = new Order();
    exitStopLossOrderStatus.setOrderId(1L);
    exitStopLossOrderStatus.setStatus(OrderStatus.NEW);
    when(mockBinanceApiRestClient.getOrderStatus(any())).thenReturn(exitStopLossOrderStatus);
    // Return the same unchanged exit stop loss order status as NEW.
    when(mockDao.getChartPattern(chartPatternSignal)).thenReturn(chartPatternSignal);
    CancelOrderResponse cancelOrderResponse = new CancelOrderResponse();
    cancelOrderResponse.setOrderId(2L);
    cancelOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponse);
    MarginAssetBalance ethBalance = new MarginAssetBalance();
    ethBalance.setAsset("ETH");
    ethBalance.setFree("10.0");
    ethBalance.setLocked("0.0");
    MarginAccount account = new MarginAccount();
    account.setUserAssets(Lists.newArrayList(ethBalance));
    when(mockBinanceApiRestClient.getAccount()).thenReturn(account);
    MarginNewOrderResponse exitMarketOrderResponse = new MarginNewOrderResponse();
    exitMarketOrderResponse.setOrderId(3L);
    exitMarketOrderResponse.setExecutedQty("10.0");
    Trade fill1 = new Trade();
    fill1.setPrice("0.9");
    fill1.setQty("5.0");
    Trade fill2 = new Trade();
    fill2.setPrice("1.1");
    fill2.setQty("5.0");
    exitMarketOrderResponse.setFills(Lists.newArrayList(fill1, fill2));
    exitMarketOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.newOrder(any(MarginNewOrder.class))).thenReturn(exitMarketOrderResponse);

    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TARGET_TIME_PASSED);

    verifyNoInteractions(mockOutstandingTrades);
  }

  @Test
  public void exitPositionIfStillHeld_sellSignal_exitsTradeForFullQty()
      throws MessagingException, ParseException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTradeType(TradeType.SELL)
        .setIsPositionExited(false)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 10.0, 20.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))
        .build();
    Order exitStopLossOrderStatus = new Order();
    exitStopLossOrderStatus.setOrderId(1L);
    exitStopLossOrderStatus.setStatus(OrderStatus.NEW);
    when(mockBinanceApiRestClient.getOrderStatus(any())).thenReturn(exitStopLossOrderStatus);
    // Return the same unchanged exit stop loss order status as NEW.
    when(mockDao.getChartPattern(chartPatternSignal)).thenReturn(chartPatternSignal);
    CancelOrderResponse cancelOrderResponse = new CancelOrderResponse();
    cancelOrderResponse.setOrderId(2L);
    cancelOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponse);
    MarginAssetBalance ethBalance = new MarginAssetBalance();
    ethBalance.setAsset("ETH");
    ethBalance.setBorrowed("10.0");
    ethBalance.setFree("0.0");
    ethBalance.setLocked("0.0");
    MarginAccount account = new MarginAccount();
    account.setUserAssets(Lists.newArrayList(ethBalance));
    when(mockBinanceApiRestClient.getAccount()).thenReturn(account);
    MarginNewOrderResponse exitMarketOrderResponse = new MarginNewOrderResponse();
    exitMarketOrderResponse.setOrderId(3L);
    exitMarketOrderResponse.setExecutedQty("10.0");
    Trade fill1 = new Trade();
    fill1.setPrice("0.9");
    fill1.setQty("5.0");
    Trade fill2 = new Trade();
    fill2.setPrice("1.1");
    fill2.setQty("5.0");
    exitMarketOrderResponse.setFills(Lists.newArrayList(fill1, fill2));
    exitMarketOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.newOrder(any(MarginNewOrder.class))).thenReturn(exitMarketOrderResponse);

    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TARGET_TIME_PASSED);

    verify(mockBinanceApiRestClient).getOrderStatus(stopLossOrderStatusRequestCapture.capture());
    verify(mockDao).updateExitStopLimitOrder(any(), eq(
        ChartPatternSignal.Order.create(exitStopLossOrderStatus.getOrderId(),
            0,
            0, exitStopLossOrderStatus.getStatus())
    ));
    assertThat(stopLossOrderStatusRequestCapture.getValue().getOrderId()).isEqualTo(2L);
    verify(mockBinanceApiRestClient).cancelOrder(cancelOrderRequestCapture.capture());
    assertThat(cancelOrderRequestCapture.getValue().getOrderId()).isEqualTo(2);
    assertThat(cancelOrderRequestCapture.getValue().getSymbol()).isEqualTo("ETHUSDT");
    verify(mockDao).cancelStopLimitOrder(eq(chartPatternSignal));
    verify(mockBinanceApiRestClient).newOrder(newOrderCapture.capture());
    assertThat(newOrderCapture.getValue().getSymbol()).isEqualTo("ETHUSDT");
    assertThat(newOrderCapture.getValue().getSide()).isEqualTo(OrderSide.BUY);
    assertThat(newOrderCapture.getValue().getType()).isEqualTo(OrderType.MARKET);
    assertThat(newOrderCapture.getValue().getTimeInForce()).isNull();
    assertThat(newOrderCapture.getValue().getQuantity()).isEqualTo("10.0");
    verify(mockDao).setExitOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(3L,
            10.0, 1.0, OrderStatus.FILLED), TradeExitType.TARGET_TIME_PASSED);
    verify(mockRepayBorrowedOnMargin).repay("ETH", 10);
    verify(mockOutstandingTrades).decrementNumOutstandingTrades(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void exitPositionIfStillHeld_insfficientQty_shouldNeverHappen()
      throws MessagingException, ParseException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsPositionExited(false)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 10.0, 20.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))
        .build();
    Order exitStopLossOrderStatus = new Order();
    exitStopLossOrderStatus.setOrderId(1L);
    exitStopLossOrderStatus.setStatus(OrderStatus.NEW);
    when(mockBinanceApiRestClient.getOrderStatus(any())).thenReturn(exitStopLossOrderStatus);
    // Return the same unchanged exit stop loss order status as NEW.
    when(mockDao.getChartPattern(chartPatternSignal)).thenReturn(chartPatternSignal);
    CancelOrderResponse cancelOrderResponse = new CancelOrderResponse();
    cancelOrderResponse.setOrderId(2L);
    cancelOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponse);
    MarginAssetBalance ethBalance = new MarginAssetBalance();
    ethBalance.setAsset("ETH");
    ethBalance.setFree("9.0");
    ethBalance.setLocked("0.0");
    MarginAccount account = new MarginAccount();
    account.setUserAssets(Lists.newArrayList(ethBalance));
    when(mockBinanceApiRestClient.getAccount()).thenReturn(account);

    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TARGET_TIME_PASSED);

    verify(mockBinanceApiRestClient).getOrderStatus(stopLossOrderStatusRequestCapture.capture());
    verify(mockDao).updateExitStopLimitOrder(any(), eq(
        ChartPatternSignal.Order.create(exitStopLossOrderStatus.getOrderId(),
            0,
            0, exitStopLossOrderStatus.getStatus())
    ));
    assertThat(stopLossOrderStatusRequestCapture.getValue().getOrderId()).isEqualTo(2L);
    verify(mockBinanceApiRestClient).cancelOrder(cancelOrderRequestCapture.capture());
    assertThat(cancelOrderRequestCapture.getValue().getOrderId()).isEqualTo(2);
    assertThat(cancelOrderRequestCapture.getValue().getSymbol()).isEqualTo("ETHUSDT");
    verify(mockDao).cancelStopLimitOrder(chartPatternSignal);
    verify(mockBinanceApiRestClient, never()).newOrder(any());
    verify(mockDao, never()).setExitOrder(any(), any(), any());
    verify(mockMailer).sendEmail(any(), any());
  }

  @Test
  public void exitPositionIfStillHeld_partialStopLossTrade_exitsTradeForRemainingQty()
      throws MessagingException, ParseException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsPositionExited(false)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 10.0, 20.0, OrderStatus.FILLED))
        .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))
        .build();
    Order exitStopLossOrderStatus = new Order();
    exitStopLossOrderStatus.setOrderId(2L);
    exitStopLossOrderStatus.setStatus(OrderStatus.PARTIALLY_FILLED);
    exitStopLossOrderStatus.setExecutedQty("5.0");
    exitStopLossOrderStatus.setPrice("17.0");
    when(mockBinanceApiRestClient.getOrderStatus(any())).thenReturn(exitStopLossOrderStatus);
    chartPatternSignal = ChartPatternSignal.newBuilder().copy(chartPatternSignal)
            .setExitStopLimitOrder(ChartPatternSignal.Order.create(2, 5.0, 17.0, OrderStatus.PARTIALLY_FILLED))
                .build();
    when(mockDao.getChartPattern(chartPatternSignal)).thenReturn(chartPatternSignal);
    CancelOrderResponse cancelOrderResponse = new CancelOrderResponse();
    cancelOrderResponse.setOrderId(2L);
    cancelOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.cancelOrder(any(CancelOrderRequest.class))).thenReturn(cancelOrderResponse);
    MarginAssetBalance ethBalance = new MarginAssetBalance();
    ethBalance.setAsset("ETH");
    ethBalance.setFree("5.0");
    ethBalance.setLocked("0.0");
    MarginAccount account = new MarginAccount();
    account.setUserAssets(Lists.newArrayList(ethBalance));
    when(mockBinanceApiRestClient.getAccount()).thenReturn(account);
    MarginNewOrderResponse exitMarketOrderResponse = new MarginNewOrderResponse();
    exitMarketOrderResponse.setOrderId(3L);
    Trade fill1 = new Trade();
    fill1.setPrice("0.9");
    fill1.setQty("2.5");
    Trade fill2 = new Trade();
    fill2.setPrice("1.1");
    fill2.setQty("2.5");
    exitMarketOrderResponse.setFills(Lists.newArrayList(fill1, fill2));
    exitMarketOrderResponse.setExecutedQty("5.0");
    exitMarketOrderResponse.setStatus(OrderStatus.FILLED);
    when(mockBinanceApiRestClient.newOrder(any(MarginNewOrder.class))).thenReturn(exitMarketOrderResponse);

    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TARGET_TIME_PASSED);

    verify(mockBinanceApiRestClient).getOrderStatus(stopLossOrderStatusRequestCapture.capture());
    verify(mockDao).updateExitStopLimitOrder(any(), eq(
        ChartPatternSignal.Order.create(exitStopLossOrderStatus.getOrderId(),
            numberFormat.parse(exitStopLossOrderStatus.getExecutedQty()).doubleValue(),
            numberFormat.parse(exitStopLossOrderStatus.getPrice()).doubleValue(), exitStopLossOrderStatus.getStatus())
    ));
    assertThat(stopLossOrderStatusRequestCapture.getValue().getOrderId()).isEqualTo(2L);
    verify(mockBinanceApiRestClient).cancelOrder(cancelOrderRequestCapture.capture());
    assertThat(cancelOrderRequestCapture.getValue().getOrderId()).isEqualTo(2);
    assertThat(cancelOrderRequestCapture.getValue().getSymbol()).isEqualTo("ETHUSDT");
    verify(mockDao).cancelStopLimitOrder(chartPatternSignal);
    verify(mockBinanceApiRestClient).newOrder(newOrderCapture.capture());
    assertThat(newOrderCapture.getValue().getSymbol()).isEqualTo("ETHUSDT");
    assertThat(newOrderCapture.getValue().getSide()).isEqualTo(OrderSide.SELL);
    assertThat(newOrderCapture.getValue().getType()).isEqualTo(OrderType.MARKET);
    assertThat(newOrderCapture.getValue().getTimeInForce()).isNull();
    assertThat(newOrderCapture.getValue().getQuantity()).isEqualTo("5.0");
    verify(mockDao).setExitOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(3L,
            5.0, 1.0, OrderStatus.FILLED), TradeExitType.TARGET_TIME_PASSED);
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