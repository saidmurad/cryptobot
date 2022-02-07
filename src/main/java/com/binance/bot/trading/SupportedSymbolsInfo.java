package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class SupportedSymbolsInfo {

  private BinanceApiRestClient binanceApiRestClient;
  private Set<String> supportedSymbols = new HashSet<>();
  private Map<String, Integer> lotSizeMap = new HashMap<>();
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private Map<String, List<OrderType>> tradingSymbolsMap = new HashMap<>();
  private long lastFetchTime = 0;

  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  @Autowired
  public SupportedSymbolsInfo(BinanceApiClientFactory binanceApiClientFactory) {
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
  }

  public Set<String> getSupportedSymbols() {
    if (!supportedSymbols.isEmpty()) {
      return supportedSymbols;
    }
    binanceApiRestClient.getExchangeInfo().getSymbols().parallelStream().forEach(symbolInfo -> {
      supportedSymbols.add(symbolInfo.getSymbol());
    });
    return supportedSymbols;
  }

  // exchangeInfo api weight is 10 and the limit is 1200 weight per minute, i.e. once ever 0.5 secs.
  public Map<String, List<OrderType>> getTradingActiveSymbols() {
    if (lastFetchTime != 0 && System.currentTimeMillis() - lastFetchTime <= 5000) {
      return tradingSymbolsMap;
    }
    tradingSymbolsMap = new HashMap();
    logger.info("Calling getExchangeInfo at time " + dateFormat.format(new Date()));
    binanceApiRestClient.getExchangeInfo().getSymbols().parallelStream().forEach(symbolInfo -> {
      if (symbolInfo.getStatus() == SymbolStatus.TRADING) {
        tradingSymbolsMap.put(symbolInfo.getSymbol(), symbolInfo.getOrderTypes());
      }
    });
    return tradingSymbolsMap;
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
