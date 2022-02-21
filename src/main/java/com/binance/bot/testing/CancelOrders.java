package com.binance.bot.testing;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.*;
import com.binance.api.client.exception.BinanceApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CancelOrders {
  @Autowired
  private BinanceApiClientFactory factory;

  public void cancelOrders() throws BinanceApiException {
    BinanceApiRestClient restClient = factory.newRestClient();
    AllOrdersRequest allOrdersRequest = new AllOrdersRequest("ETHUSDT");
    List<Order> allOrders = restClient.getAllOrders(allOrdersRequest);

    for (Order order: allOrders) {
      if (order.getStatus() == OrderStatus.NEW) {
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest("ETHUSDT", order.getOrderId());
        CancelOrderResponse resp = restClient.cancelOrder(cancelOrderRequest);
      }
    }
  }
}
