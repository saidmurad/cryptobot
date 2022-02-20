package com.binance.bot.bitcoinmonitoring;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

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
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final boolean[][] tradingReco = new boolean[4][2];
  public boolean[][] getTradingReco() {
    return tradingReco;
  }
  @Value("${fifteen_minute_movement_threshold_percent}")
  private double fifteenMinuteMovementThresholdPercent;
  @Autowired
  public BitcoinMonitoringTask(BinanceApiClientFactory binanceApiClientFactory,
                               ChartPatternSignalDaoImpl dao,
                               JdbcTemplate jdbcTemplate) {
    this.binanceApiRestClient = binanceApiClientFactory.newRestClient();
    this.dao = dao;
    this.jdbcTemplate = jdbcTemplate;
  }

  private List<Double> lastFourCandlestickPrices = new ArrayList<>();
  private Map<TimeFrame, TradeType> overdoneTradeType = new HashMap<>();

  public
  @Scheduled(fixedRate = 900000, initialDelayString = "${timing.initialDelay}")
  public void performFifteenMinuteTimeFrame() throws ParseException {
    List<Candlestick> candlesticks = binanceApiRestClient.getCandlestickBars(
        "BTCUSDT", CandlestickInterval.FIFTEEN_MINUTES, 10, null, null);
    TradeType tradeTypeToDisallow = getOverdoneTradeType(candlesticks, fifteenMinuteMovementThresholdPercent);
    /*jdbcTemplate.update("insert into BTC values(?, ?, ?, ?, ?, ?)",
        df.format(new Date(candlesticks.get(4).getCloseTime())),
        lastCandlestick,
        getDiffPercent(oldCandlestick1, lastCandlestick),
        getDiffPercent(oldCandlestick2, lastCandlestick),
        getDiffPercent(oldCandlestick3, lastCandlestick),
        getDiffPercent(oldCandlestick4, lastCandlestick));*/
  }

  private TradeType getOverdoneTradeType(List<Candlestick> candlesticks, double movementThresholdPercent) throws ParseException {
    int len = candlesticks.size();
    boolean lastCandlestickGreen = isGreenCandle(candlesticks.get(len - 1));
    if (lastCandlestickGreen) {
      double lastCandlestickClose = numberFormat.parse(candlesticks.get(len - 1).getClose()).doubleValue();
      int i = len - 1;
      for (; i >= 0; i--) {
        Candlestick thisCandlestick = candlesticks.get(i);
        double thisCandlestickOpen = numberFormat.parse(thisCandlestick.getOpen()).doubleValue();
        if ((lastCandlestickClose - thisCandlestickOpen) / thisCandlestickOpen * 100 > movementThresholdPercent) {
          return TradeType.BUY;
        }
        boolean isCandlestickGreen = isGreenCandle(thisCandlestick);
        if (!isCandlestickGreen) {
          break;
        }
        lastCandlestickClose = numberFormat.parse(candlesticks.get(i).getClose()).doubleValue();
      }
      if (i == -1) {
        // Probably overbought.
        return TradeType.BUY;
      }
    } else {
      double lastCandlestickClose = numberFormat.parse(candlesticks.get(len - 1).getClose()).doubleValue();
      int i = len - 1;
      for (; i >= 0; i--) {
        Candlestick thisCandlestick = candlesticks.get(i);
        double thisCandlestickOpen = numberFormat.parse(thisCandlestick.getOpen()).doubleValue();
        if ((thisCandlestickOpen - lastCandlestickClose) / thisCandlestickOpen * 100 > movementThresholdPercent) {
          return TradeType.SELL;
        }
        boolean isCandlestickGreen = isGreenCandle(thisCandlestick);
        if (isCandlestickGreen) {
          break;
        }
        lastCandlestickClose = numberFormat.parse(candlesticks.get(i).getClose()).doubleValue();
      }
      if (i == -1) {
        // Probably overbought.
        return TradeType.SELL;
      }
    }
    return null;
  }

  private boolean isGreenCandle(Candlestick candlestick) throws ParseException {
    return numberFormat.parse(candlestick.getClose()).doubleValue() - numberFormat.parse(candlestick.getOpen()).doubleValue();
  }

  private double getDiffPercent(double price1, double price2) {
    return (price2 - price1) / price1 * 100;
  }
}
