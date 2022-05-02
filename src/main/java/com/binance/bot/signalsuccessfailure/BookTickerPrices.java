package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.domain.event.BookTickerEvent;
import com.google.auto.value.AutoValue;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class BookTickerPrices {
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private Boolean streamStarted = false;
  synchronized public void setBookTicker(BookTickerEvent callback) throws ParseException {
    bookTickerMap.put(callback.getSymbol().toUpperCase(),
        BookTicker.create(
            numberFormat.parse(callback.getAskPrice()).doubleValue(),
            numberFormat.parse(callback.getBidPrice()).doubleValue()));
    if (!streamStarted) {
      streamStarted = true;
      streamStarted.notifyAll();
    }
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

  synchronized public BookTicker getBookTicker(String symbol) throws InterruptedException {
    if (!streamStarted) {
      streamStarted.wait();
    }
    return bookTickerMap.get(symbol);
  }
}
