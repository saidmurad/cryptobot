package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.exception.BinanceApiException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OrderTest {

  private BinanceApiRestClient binanceApiRestClient;

  @Before
  public void setUp() {
    BinanceApiClientFactory binanceApiClientFactory = new BinanceApiClientFactory(
        true, "31MUPiM1hMNKt4uUXJ8et0GbYwXYvZ33HzLmRdXYEtrvvM8A0p59N510EvQcA99A",
        "SVI8zkiPBsM96Uyd1UijdojGpIefAT9ZzB5pWPSB9M6uPYSUHGyK32O6Bxdxppzf"
    );
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
  }
  @Test
  public void orderTest() throws BinanceApiException {
    NewOrder newOrder = new NewOrder("BTCUSDT", OrderSide.BUY, OrderType.LIMIT, TimeInForce.GTC,
        "12.1234567", "32000");
    binanceApiRestClient.newOrderTest(newOrder);
  }
}
