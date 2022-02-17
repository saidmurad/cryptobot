package com.binance.bot.signalsuccessfailure.specifictradeactions;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
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

  @Autowired
  ExitPositionAtMarketPrice(BinanceApiClientFactory binanceApiClientFactory, ChartPatternSignalDaoImpl dao) {
    this.restClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
  }

  public void exitPositionIfStillHeld(
      ChartPatternSignal chartPatternSignal, double currMarketPrice, TradeExitType tradeExitType)
      throws MessagingException, ParseException {
    if (Boolean.TRUE.equals(chartPatternSignal.isPositionExited())) {
      return;
    }
    logger.info(String.format("Found position to exit: %s.", chartPatternSignal.toStringOrderValues()));
    double qtyToExit = chartPatternSignal.entryOrder().executedQty();
    // If partial order has been executed it could only be the stop limit order.
    // exitStopLimitOrder can never be null.
    if (chartPatternSignal.exitStopLimitOrder().status() == OrderStatus.PARTIALLY_FILLED) {
      qtyToExit -= chartPatternSignal.exitStopLimitOrder().executedQty();
      logger.info(String.format("Need to exit the remaining %f qty.", qtyToExit));
      CancelOrderRequest cancelStopLimitOrderRequest = new CancelOrderRequest(
          chartPatternSignal.coinPair(), chartPatternSignal.exitStopLimitOrder().orderId());
      CancelOrderResponse cancelStopLimitOrderResponse = restClient.cancelOrder(cancelStopLimitOrderRequest);
      logger.info(String.format("Cancelled Stop Limit Order with response status %s.", cancelStopLimitOrderResponse.getStatus().name()));
    } else {
      logger.info(String.format("Need to exit the %f qty.", qtyToExit));
    }
    exitSpotAccountQty(chartPatternSignal, qtyToExit, currMarketPrice, tradeExitType);
  }

  private void exitSpotAccountQty(ChartPatternSignal chartPatternSignal, double qtyToExit, double currPrice,
                                  TradeExitType tradeExitType)
      throws ParseException, MessagingException {
    String baseAsset = chartPatternSignal.coinPair().substring(0, chartPatternSignal.coinPair().length() - 4);
    double spotAccountBalance = numberFormat.parse(restClient.getAccount().getAssetBalance(baseAsset).getFree()).doubleValue();
    if (spotAccountBalance < qtyToExit) {
      String errorMsg = String.format("Expected to find %f quantity of %s to exit but asset found only %f in spot account balance.",
          qtyToExit, baseAsset, spotAccountBalance);
      logger.error(errorMsg);
      Mailer.sendEmail("Asset to exit not found", errorMsg);
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
