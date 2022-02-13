package com.binance.bot.testing;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GetOrderStatus {
  @Autowired
  private BinanceApiClientFactory factory;

  public void getOrderStatus() {
    BinanceApiRestClient restClient = factory.newRestClient();
    OrderStatusRequest orderStatusRequest = new OrderStatusRequest("XRPUSDT",4019246847L);
    Order orderStatus = restClient.getOrderStatus(orderStatusRequest);

  }
}
