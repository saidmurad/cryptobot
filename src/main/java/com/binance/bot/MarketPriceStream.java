package com.binance.bot;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import org.springframework.beans.factory.annotation.Autowired;

public class MarketPriceStream {
  private BinanceApiWebSocketClient binanceApiWebSocketClient;

  @Autowired
  MarketPriceStream(BinanceApiClientFactory binanceApiClientFactory) {
    this.binanceApiWebSocketClient = binanceApiClientFactory.newWebSocketClient();
  }

  public void run() {
    binanceApiWebSocketClient.onTickerEvent("BTCUSDT", callback -> {
      callback.getWeightedAveragePrice();
    });
  }
}
