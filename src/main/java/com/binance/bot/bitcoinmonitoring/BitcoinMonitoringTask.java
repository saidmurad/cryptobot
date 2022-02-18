package com.binance.bot.bitcoinmonitoring;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 15 minute candles: 1.5% over 5 candlesticsk.
 * 1 hour candles: 1.5% over 3 candlesticks
 * 4 hour candles: 5.5% over 3 candlesticks.
 */
@Component
public class BitcoinMonitoringTask {

  private final BinanceApiRestClient binanceApiRestClient;
  private final ChartPatternSignalDaoImpl dao;
  private boolean firstTime = true;
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  @Autowired
  private JdbcTemplate jdbcTemplate;
  final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final boolean[][] tradingReco = new boolean[4][2];
  public boolean[][] getTradingReco() {
    return tradingReco;
  }

  @Autowired
  public BitcoinMonitoringTask(BinanceApiClientFactory binanceApiClientFactory,
                               ChartPatternSignalDaoImpl dao,
                               JdbcTemplate jdbcTemplate) {
    this.binanceApiRestClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    this.jdbcTemplate = jdbcTemplate;
  }

  private List<Double> lastFourCandlestickPrices = new ArrayList<>();

  @Scheduled(fixedRate = 900000)
  public void perform() throws ParseException {
    List<Candlestick> candlesticks = binanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 5, null, null);
    double lastCandlestick = numberFormat.parse(candlesticks.get(4).getClose()).doubleValue();
    double oldCandlestick1 = numberFormat.parse(candlesticks.get(3).getClose()).doubleValue();
    double oldCandlestick2 = numberFormat.parse(candlesticks.get(2).getClose()).doubleValue();
    double oldCandlestick3 = numberFormat.parse(candlesticks.get(1).getClose()).doubleValue();
    double oldCandlestick4 = numberFormat.parse(candlesticks.get(0).getClose()).doubleValue();
    jdbcTemplate.update("insert into BTC values(?, ?, ?, ?, ?, ?)",
        df.format(new Date(candlesticks.get(4).getCloseTime())),
        lastCandlestick,
        getDiffPercent(oldCandlestick1, lastCandlestick),
        getDiffPercent(oldCandlestick2, lastCandlestick),
        getDiffPercent(oldCandlestick3, lastCandlestick),
        getDiffPercent(oldCandlestick4, lastCandlestick));
  }

  private double getDiffPercent(double price1, double price2) {
    return (price2 - price1) / price1 * 100;
  }
}
