package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Component
// TODO: Write unit tests. Only manually tested (testing.MarketPriceStreamUsage).
public class MarketPriceStream {
  private final BookTickerPrices bookTickerPrices;
  private final BinanceApiWebSocketClient binanceApiWebSocketClient;

  private Set<String> coinPairs = new HashSet<>();
  private Closeable tickrStream = null;
  private Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  MarketPriceStream(BinanceApiClientFactory binanceApiClientFactory, BookTickerPrices bookTickerPrices) {
    this.binanceApiWebSocketClient = binanceApiClientFactory.newWebSocketClient();
    this.bookTickerPrices = bookTickerPrices;
  }

  public void addSymbol(String symbol) throws IOException {
    if (coinPairs.contains(symbol)) {
      return;
    }
    coinPairs.add(symbol);
    restartStream();
  }

  public void removeSymbol(String symbol) throws IOException {
    coinPairs.remove(symbol);
    restartStream();
  }

  private void restartStream() throws IOException {
    if (tickrStream != null) {
      tickrStream.close();
    }
    tickrStream = listenToTickerEvents();
  }

  private Closeable listenToTickerEvents() {
    return binanceApiWebSocketClient.onBookTickerEvent(getSymbolsList(), callback -> {
      try {
        System.out.println("Received book ticker event " + callback);
        bookTickerPrices.setBookTicker(callback);
      } catch (ParseException e) {
        logger.error(String.format("Exception in parsing book ticker %s.", callback), e);
      }
    });
  }

  private String getSymbolsList() {
    StringBuilder symbolsList = new StringBuilder();
    Iterator itr = coinPairs.iterator();
    while (itr.hasNext()) {
      if (symbolsList.length() > 0) {
        symbolsList.append(",");
      }
      symbolsList.append(((String)itr.next()).toLowerCase());
    }
    return symbolsList.toString();
  }
}
