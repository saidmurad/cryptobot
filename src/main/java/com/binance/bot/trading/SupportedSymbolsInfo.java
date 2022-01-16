package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SupportedSymbolsInfo {

  private BinanceApiRestClient binanceApiRestClient;
  private Map<String, List<OrderType>> symbolInfoMap = new HashMap<>();

  @Autowired
  public SupportedSymbolsInfo(BinanceApiClientFactory binanceApiClientFactory) {
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
  }

  public Map<String, List<OrderType>> getSupportedSymbols() {
    if (!symbolInfoMap.isEmpty()) {
      return symbolInfoMap;
    }
    binanceApiRestClient.getExchangeInfo().getSymbols().parallelStream().forEach(symbolInfo -> {
      symbolInfoMap.put(symbolInfo.getSymbol(), symbolInfo.getOrderTypes());
    });
    return symbolInfoMap;
  }
}
