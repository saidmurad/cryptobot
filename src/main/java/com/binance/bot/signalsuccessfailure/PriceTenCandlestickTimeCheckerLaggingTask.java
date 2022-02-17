package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.signalsuccessfailure.specifictradeactions.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.binance.bot.common.Util.getProfitPercentAtWithPrice;
import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

@Component
public class PriceTenCandlestickTimeCheckerLaggingTask extends PriceTargetCheckerLaggingTask {
  @Autowired
  PriceTenCandlestickTimeCheckerLaggingTask(BinanceApiClientFactory binanceApiClientFactory,
                                            ChartPatternSignalDaoImpl dao,
                                            SupportedSymbolsInfo supportedSymbolsInfo) {
    super(binanceApiClientFactory, dao, supportedSymbolsInfo);
    targetTimeType = TargetTimeType.TEN_CANDLESTICK;
  }

  @Override
  protected List<ChartPatternSignal> getChartPatternSignalsThatLongSinceReachedTargetTime() {
    return dao.getChatPatternSignalsThatLongSinceReachedTenCandleStickTime();
  }

  @Override
  protected boolean setTargetPrice(ChartPatternSignal chartPatternSignal, double tenCandleStickTimePrice) {
    return dao.setTenCandleStickTimePrice(chartPatternSignal, tenCandleStickTimePrice,
        getProfitPercentAtWithPrice(chartPatternSignal, tenCandleStickTimePrice));
  }

  @Override
  protected void markFailedToGetTargetPrice(ChartPatternSignal chartPatternSignal) {
    dao.failedToGetPriceAtTenCandlestickTime(chartPatternSignal);
  }

  @Override
  protected long getStartTimeWindow(ChartPatternSignal chartPatternSignal) {
    return Math.min(chartPatternSignal.timeOfSignal().getTime()
            + getTenCandleStickTimeIncrementMillis(chartPatternSignal),
        Math.min(clock.millis(), chartPatternSignal.priceTargetTime().getTime()));
  }
}
