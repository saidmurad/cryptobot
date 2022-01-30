package com.binance.bot.signalsuccessfailure.testing;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RestoreTestAccountBalance {
  @Autowired
  private BinanceApiClientFactory binanceApiClientFactory;
  @Autowired
  private SupportedSymbolsInfo symbolsInfo;

  public void perform() {
    BinanceApiRestClient restClient = binanceApiClientFactory.newRestClient();
    AssetBalance btcBalance = restClient.getAccount().getAssetBalance("USDT");

    Integer lotSize = symbolsInfo.getLotSize("BTCUSDT");
  }
}
