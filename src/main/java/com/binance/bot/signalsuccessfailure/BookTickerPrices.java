package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.domain.event.BookTickerEvent;
import com.google.auto.value.AutoValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class BookTickerPrices {
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private Map<String, Date> tickersAwaited = new HashMap<>();
  private Logger logger = LoggerFactory.getLogger(getClass());

  public BookTickerPrices() {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  synchronized public void setBookTicker(BookTickerEvent callback) throws ParseException {
    String ticker = callback.getSymbol().toUpperCase();
    bookTickerMap.put(ticker,
        BookTicker.create(
            numberFormat.parse(callback.getAskPrice()).doubleValue(),
            numberFormat.parse(callback.getBidPrice()).doubleValue()));
    Date tickerAwaitedSince = tickersAwaited.get(ticker);
    if (tickerAwaitedSince != null) {
      synchronized (tickerAwaitedSince) {
        tickerAwaitedSince.notifyAll();
      }
    }
  }

  @Scheduled(fixedDelay = 300000)
  public void printNumTasksBlocked() {
    logger.info(String.format("Number of blocked tasks=%d", tickersAwaited.size()));
    Iterator<String> itr = tickersAwaited.keySet().iterator();
    while (itr.hasNext()) {
      String symbol = itr.next();
      logger.info(String.format("Waiting for ticker %s since %s.", symbol,
          dateFormat.format(tickersAwaited.get(symbol))));
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
    BookTicker bookTicker = bookTickerMap.get(symbol);
    if (bookTicker == null) {
      Date now = tickersAwaited.get(symbol);
      if (now == null) {
        now = new Date();
        tickersAwaited.put(symbol, now);
      }
      logger.info(String.format("Task going to wait for book ticker for symbol %s.", symbol));
      synchronized (now) {
        now.wait();
      }
      logger.info(String.format("Task woke up from wait for book ticker for symbol %s.", symbol));
    }
    return bookTickerMap.get(symbol);
  }
}
