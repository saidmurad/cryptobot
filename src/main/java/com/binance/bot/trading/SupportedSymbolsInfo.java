package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
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
  private Map<String, Integer> lotSizeMap = new HashMap<>();
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private Long lastFetched = null;

  @Autowired
  public SupportedSymbolsInfo(BinanceApiClientFactory binanceApiClientFactory) {
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
  }

  public Map<String, List<OrderType>> getSupportedSymbols() {
    if (!symbolInfoMap.isEmpty() && (System.currentTimeMillis() - lastFetched) < 60000) {
      return symbolInfoMap;
    }
    symbolInfoMap = new HashMap<>();
    lastFetched = System.currentTimeMillis();
    binanceApiRestClient.getExchangeInfo().getSymbols().parallelStream().forEach(symbolInfo -> {
      if (symbolInfo.getStatus() == SymbolStatus.TRADING) {
        symbolInfoMap.put(symbolInfo.getSymbol(), symbolInfo.getOrderTypes());
      }
    });
    logger.info(String.format("Returning symbol map with %d symbols.", symbolInfoMap.size()));
    return symbolInfoMap;
  }

  private Integer getTickSizeAsNum(String tickSizeStr) {
    Double tickSize = Double.parseDouble(tickSizeStr);
    int count = 0;
    while (tickSize < 1.0) {
      tickSize *= 10;
      count++;
    }
    return count;
  }

  public Integer getLotSize(String symbol) {
    if (lotSizeMap.isEmpty()) {
      binanceApiRestClient.getExchangeInfo().getSymbols().parallelStream().forEach(symbolInfo -> {
        String stepSize = symbolInfo.getSymbolFilter(FilterType.LOT_SIZE).getStepSize();
        if (stepSize != null) {
          lotSizeMap.put(symbolInfo.getSymbol(), getTickSizeAsNum(stepSize));
        }
      });
    }
    return lotSizeMap.get(symbol);
  }
}
