package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.event.BookTickerEvent;
import com.binance.api.client.exception.BinanceApiException;
import com.google.auto.value.AutoValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class BookTickerPrices {
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private Logger logger = LoggerFactory.getLogger(getClass());
  private final BinanceApiRestClient binanceApiRestClient;

  @Autowired
  public BookTickerPrices(BinanceApiClientFactory binanceApiClientFactory) {
    this.binanceApiRestClient = binanceApiClientFactory.newRestClient();
  }

  public void setBookTicker(BookTickerEvent callback) throws ParseException {
    String ticker = callback.getSymbol().toUpperCase();
    bookTickerMap.put(ticker,
        BookTicker.create(
            numberFormat.parse(callback.getAskPrice()).doubleValue(),
            numberFormat.parse(callback.getBidPrice()).doubleValue()));
  }

  @AutoValue
  public abstract static class BookTicker {
    public abstract double bestAsk();

    public abstract double bestBid();

    public static BookTicker create(double bestAsk, double bestBid) {
      return new AutoValue_BookTickerPrices_BookTicker(bestAsk, bestBid);
    }
  }
  private Map<String, BookTicker> bookTickerMap = new HashMap<>();

  public BookTicker getBookTicker(String symbol) throws BinanceApiException, ParseException {
    BookTicker bookTicker = bookTickerMap.get(symbol);
    if (bookTicker == null) {
      //logger.info(String.format("No market stream ticker found for symbol %s.", symbol));
      double price = numberFormat.parse(binanceApiRestClient.getPrice(symbol).getPrice()).doubleValue();
      //logger.info(String.format("Fetched price of %f using REST api.", price));
      bookTicker = BookTicker.create(price, price);
    }
    return bookTicker;
  }
}
