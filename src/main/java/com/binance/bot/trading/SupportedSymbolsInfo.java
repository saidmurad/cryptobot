package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.general.SymbolStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SupportedSymbolsInfo {

  private BinanceApiRestClient binanceApiRestClient;
  private Map<String, List<OrderType>> symbolInfoMap = new HashMap<>();
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  public SupportedSymbolsInfo(BinanceApiClientFactory binanceApiClientFactory) {
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
  }

  public Map<String, List<OrderType>> getSupportedSymbols() {
    if (!symbolInfoMap.isEmpty()) {
      return symbolInfoMap;
    }
    binanceApiRestClient.getExchangeInfo().getSymbols().parallelStream().forEach(symbolInfo -> {
      //if (symbolInfo.getStatus() == SymbolStatus.TRADING) {
        symbolInfoMap.put(symbolInfo.getSymbol(), symbolInfo.getOrderTypes());
      //}
    });
    logger.info(String.format("Returning symbol map with %d symbols.", symbolInfoMap.size()));
    return symbolInfoMap;
  }
}
