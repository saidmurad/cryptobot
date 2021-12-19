package com.binance.bot.generatesignals;

import com.altfins.ChartPatternSignal;
import com.altfins.TimeFrame;
import com.altfins.TradeType;
import com.binance.api.client.BinanceApiRestClient;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class GenerateSignals {
  private final BinanceApiRestClient binanceApiRestClient;
  private final List<ChartPatternSignal> breakoutsToWatch = new ArrayList<>();

  @Inject
  public GenerateSignals(BinanceApiRestClient binanceApiRestClient) {
    this.binanceApiRestClient = binanceApiRestClient;
  }

  private void populateSignalsToWatch() {
    breakoutsToWatch.add(ChartPatternSignal.newBuilder()
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setBreakoutPrice(48500)
        .setPattern("Descending Channel")
        .setTradeType(TradeType.BUY)
        .setTargetPrice(52000)
        .build());
  }
}
