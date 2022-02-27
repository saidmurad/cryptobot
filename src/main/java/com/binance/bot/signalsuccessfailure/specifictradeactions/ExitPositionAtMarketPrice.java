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
import com.binance.bot.common.Util;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.RepayBorrowedOnMargin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

@Component
/** For exiting at market price either due to signal invalidation, target time elapsed, or profit taking. */
public class ExitPositionAtMarketPrice {
  private final BinanceApiMarginRestClient binanceApiMarginRestClient;
  private final ChartPatternSignalDaoImpl dao;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final Mailer mailer;
  private final RepayBorrowedOnMargin repayBorrowedOnMargin;

  @Autowired
  ExitPositionAtMarketPrice(BinanceApiClientFactory binanceApiClientFactory, ChartPatternSignalDaoImpl dao,
                            Mailer mailer, RepayBorrowedOnMargin repayBorrowedOnMargin) {
    this.binanceApiMarginRestClient = binanceApiClientFactory.newMarginRestClient();
    this.dao = dao;
    this.mailer = mailer;
    this.repayBorrowedOnMargin = repayBorrowedOnMargin;
  }

  public void exitPositionIfStillHeld(
      ChartPatternSignal chartPatternSignal, TradeExitType tradeExitType)
      throws MessagingException {
    try {
      if (chartPatternSignal.isPositionExited() == null || Boolean.TRUE.equals(chartPatternSignal.isPositionExited()
          // This is for backward compatibility.
          || chartPatternSignal.exitStopLimitOrder() == null)) {
        return;
      }
      OrderStatusRequest stopLimitOrderStatusRequest = new OrderStatusRequest(
          chartPatternSignal.coinPair(), chartPatternSignal.exitStopLimitOrder().orderId());
      // To get the most update from binance.
      Order stopLimitOrderStatus = binanceApiMarginRestClient.getOrderStatus(stopLimitOrderStatusRequest);
      logger.info(String.format("Status of the stop limit order: %s.", stopLimitOrderStatus));
      dao.updateExitStopLimitOrder(chartPatternSignal, ChartPatternSignal.Order.create(stopLimitOrderStatus.getOrderId(),
          stopLimitOrderStatus.getExecutedQty() != null ? numberFormat.parse(stopLimitOrderStatus.getExecutedQty()).doubleValue() : 0,
          stopLimitOrderStatus.getPrice() != null ? numberFormat.parse(stopLimitOrderStatus.getPrice()).doubleValue() : 0,
          stopLimitOrderStatus.getStatus()));
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
      CancelOrderResponse cancelStopLimitOrderResponse = binanceApiMarginRestClient.cancelOrder(cancelStopLimitOrderRequest);
      logger.info(String.format("Cancelled Stop Limit Order with response status %s.", cancelStopLimitOrderResponse.getStatus().name()));
      dao.cancelStopLimitOrder(chartPatternSignal);
      exitMarginAccountQty(chartPatternSignal, qtyToExit, tradeExitType);
      dao.writeAccountBalanceToDB();
    } catch (Exception ex) {
      logger.error("Exception for trade exit type %s." , tradeExitType.name(), ex);
      mailer.sendEmail("ExitPositionAtMarketPrice uncaught exception for trade exit type" + tradeExitType.name(), ex.getMessage());
    }
  }

  private void exitMarginAccountQty(ChartPatternSignal chartPatternSignal, double qtyToExit, TradeExitType tradeExitType)
      throws ParseException, MessagingException, BinanceApiException {
    String baseAsset = Util.getBaseAsset(chartPatternSignal.coinPair());
    MarginAssetBalance assetBalance = binanceApiMarginRestClient.getAccount().getAssetBalance(baseAsset);
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
      MarginNewOrder sellOrder = new MarginNewOrder(chartPatternSignal.coinPair(),
          chartPatternSignal.tradeType() == TradeType.BUY ? OrderSide.SELL : OrderSide.BUY,
          OrderType.MARKET, /* timeInForce= */ null,
          // TODO: In corner cases, will have to round up this quantity.
          "" + qtyToExit);
      MarginNewOrderResponse marketExitOrderResponse = binanceApiMarginRestClient.newOrder(sellOrder);
      logger.info(String.format("Executed %s order and got the response: %s.",
          chartPatternSignal.tradeType() == TradeType.BUY ? "sell" : "buy",
          marketExitOrderResponse));
      double executedQty = numberFormat.parse(marketExitOrderResponse.getExecutedQty()).doubleValue();
      double avgTradePrice = getAvgTradePrice(marketExitOrderResponse);
      dao.setExitMarketOrder(chartPatternSignal,
          ChartPatternSignal.Order.create(marketExitOrderResponse.getOrderId(),
              executedQty,
              avgTradePrice, marketExitOrderResponse.getStatus()), tradeExitType);
      if (chartPatternSignal.tradeType() == TradeType.BUY) {
        repayBorrowedOnMargin.repay("USDT", executedQty * avgTradePrice);
      } else {
        repayBorrowedOnMargin.repay(baseAsset, executedQty);
      }
    }
  }

  private double getAvgTradePrice(MarginNewOrderResponse sellOrderResponse) throws ParseException {
    List<Trade> fills = sellOrderResponse.getFills();
    double weightedSum=0, weight = 0;
    for (Trade fill: fills) {
      double fillPrice = Util.getDoubleValue(fill.getPrice());
      double fillQty = Util.getDoubleValue(fill.getQty());
      weightedSum += fillPrice * fillQty;
      weight += fillQty;
    }
    return weightedSum / weight;
  }
}
