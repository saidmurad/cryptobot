package com.binance.bot.generatesignals;

import com.altfins.ChartPatternSignal;
import com.altfins.TradeType;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.bot.common.Util;
import com.binance.bot.trading.BinanceTradingBot;
import com.binance.bot.trading.GetVolumeProfile;
import com.binance.bot.trading.VolumeProfile;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class ContinuousPriceMonitor implements Runnable{
  private final List<ChartPatternSignal> breakoutsToWatch;
  private final BinanceApiRestClient binanceApiRestClient;
  private final GetVolumeProfile getVolumeProfile;
  private final Logger logger = Logger.getLogger(getClass().getName());
  private final BinanceTradingBot binanceTradingBot;

  ContinuousPriceMonitor(List<ChartPatternSignal> breakoutsToWatch, BinanceApiRestClient binanceApiRestClient, GetVolumeProfile getVolumeProfile, BinanceTradingBot binanceTradingBot) {
    this.breakoutsToWatch = breakoutsToWatch;
    this.binanceApiRestClient = binanceApiRestClient;
    this.getVolumeProfile = getVolumeProfile;
    this.binanceTradingBot = binanceTradingBot;
  }

  @Override
  public void run() {
    while (true) {
      try {
        for (ChartPatternSignal chartPatternSignal : breakoutsToWatch) {
          TickerPrice price = binanceApiRestClient.getPrice(chartPatternSignal.getCoinPair());
          if (isPriceAtBreakout(price.getPrice(), chartPatternSignal.getBreakoutPrice(), chartPatternSignal.getTradeType())) {
            logger.log(Level.INFO, "Breakout price met for chart pattern signal at " + price.getPrice());
            logger.log(Level.INFO, Util.chartPatternSignalToString(chartPatternSignal));
          }
          VolumeProfile volumeProfile = getVolumeProfile.getVolumeProfile(chartPatternSignal.getCoinPair());
          if (volumeProfile.isVolSurged()) {
            logger.log(Level.INFO, "Placing trade, as volume has surged.");
            logger.log(Level.INFO, Util.volumeProfileToString(volumeProfile));
            binanceTradingBot.placeTrade(chartPatternSignal, /* isVolSurged */ true);
          } else {
            logger.log(Level.INFO, "Not placing trade, as volume has not surged.");
          }
        }
        Thread.sleep(60000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private boolean isPriceAtBreakout(String priceStr, double breakoutPrice, TradeType tradeType) {
    double price = Double.parseDouble(priceStr);
    return tradeType == TradeType.BUY && price >= breakoutPrice || tradeType == TradeType.SELL && price <= breakoutPrice;
  }
}
