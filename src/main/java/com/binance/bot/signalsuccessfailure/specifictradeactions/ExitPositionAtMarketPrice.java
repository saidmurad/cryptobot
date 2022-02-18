package com.binance.bot.signalsuccessfailure.specifictradeactions;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.bot.common.Mailer;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

@Component
/** For exiting at market price either due to signal invalidation, target time elapsed, or profit taking. */
public class ExitPositionAtMarketPrice {
  private final BinanceApiRestClient restClient;
  private final ChartPatternSignalDaoImpl dao;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final Mailer mailer;

  @Autowired
  ExitPositionAtMarketPrice(BinanceApiClientFactory binanceApiClientFactory, ChartPatternSignalDaoImpl dao, Mailer mailer) {
    this.restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    this.mailer = mailer;
  }

  public void exitPositionIfStillHeld(
      ChartPatternSignal chartPatternSignal, double currMarketPrice, TradeExitType tradeExitType)
      throws MessagingException, ParseException {
    if (chartPatternSignal.isPositionExited() == null || Boolean.TRUE.equals(chartPatternSignal.isPositionExited())) {
      return;
    }
    OrderStatusRequest stopLimitOrderStatusRequest = new OrderStatusRequest(
        chartPatternSignal.coinPair(), chartPatternSignal.exitStopLimitOrder().orderId());
    Order stopLimitOrderStatus = restClient.getOrderStatus(stopLimitOrderStatusRequest);
    logger.info(String.format("Status of the stop limit order: %s.", stopLimitOrderStatus));
    dao.updateExitStopLimitOrder(chartPatternSignal, stopLimitOrderStatus);
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    if (chartPatternSignal.isPositionExited() == null || Boolean.TRUE.equals(chartPatternSignal.isPositionExited())) {
      return;
    }
    logger.info(String.format("Found position to exit: %s.", chartPatternSignal.toStringOrderValues()));
    double qtyToExit = chartPatternSignal.entryOrder().executedQty();
    // If partial order has been executed it could only be the stop limit order.
    // exitStopLimitOrder can never be null.
    if (chartPatternSignal.exitStopLimitOrder().status() == OrderStatus.PARTIALLY_FILLED) {
      qtyToExit -= chartPatternSignal.exitStopLimitOrder().executedQty();
      logger.info(String.format("Need to exit the remaining %f qty.", qtyToExit));
    } else {
      logger.info(String.format("Need to exit the %f qty.", qtyToExit));
    }
    CancelOrderRequest cancelStopLimitOrderRequest = new CancelOrderRequest(
        chartPatternSignal.coinPair(), chartPatternSignal.exitStopLimitOrder().orderId());
    CancelOrderResponse cancelStopLimitOrderResponse = restClient.cancelOrder(cancelStopLimitOrderRequest);
    logger.info(String.format("Cancelled Stop Limit Order with response status %s.", cancelStopLimitOrderResponse.getStatus().name()));
    exitSpotAccountQty(chartPatternSignal, qtyToExit, currMarketPrice, tradeExitType);
  }

  private void exitSpotAccountQty(ChartPatternSignal chartPatternSignal, double qtyToExit, double currPrice,
                                  TradeExitType tradeExitType)
      throws ParseException, MessagingException {
    String baseAsset = chartPatternSignal.coinPair().substring(0, chartPatternSignal.coinPair().length() - 4);
    AssetBalance assetBalance = restClient.getAccount().getAssetBalance(baseAsset);
    double freeBalance = numberFormat.parse(assetBalance.getFree()).doubleValue();
    double lockedBalance = numberFormat.parse(assetBalance.getLocked()).doubleValue();
    logger.info(String.format("Asset %s quantity free: %f and quantity locked: %f", baseAsset, freeBalance, lockedBalance));
    if (freeBalance < qtyToExit) {
      String errorMsg = String.format("Expected to find %f quantity of %s to exit but asset found only %f in spot account balance.",
          qtyToExit, baseAsset, freeBalance);
      logger.error(errorMsg);
      mailer.sendEmail("Asset quantity expected amount to exit not found.", errorMsg);
      return;
    }
    if (qtyToExit > 0) {
      NewOrder sellOrder = new NewOrder(chartPatternSignal.coinPair(), OrderSide.SELL,
          OrderType.MARKET, /* timeInForce= */ null,
          // TODO: In corner cases, will have to round up this quantity.
          "" + qtyToExit);
      NewOrderResponse sellOrderResponse = restClient.newOrder(sellOrder);
      logger.info(String.format("Executed sell order and got the response: %s.", sellOrderResponse));
      dao.setExitMarketOrder(chartPatternSignal,
          ChartPatternSignal.Order.create(sellOrderResponse.getOrderId(),
              numberFormat.parse(sellOrderResponse.getExecutedQty()).doubleValue(),
              currPrice, sellOrderResponse.getStatus()), tradeExitType);
    }
  }
}
