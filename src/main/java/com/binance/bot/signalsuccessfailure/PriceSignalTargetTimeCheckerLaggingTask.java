package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

@Component
public class PriceSignalTargetTimeCheckerLaggingTask extends PriceTargetCheckerLaggingTask {
  @Autowired
  PriceSignalTargetTimeCheckerLaggingTask(BinanceApiClientFactory binanceApiClientFactory,
                                          ChartPatternSignalDaoImpl dao,
                                          SupportedSymbolsInfo supportedSymbolsInfo) {
    super(binanceApiClientFactory, dao, supportedSymbolsInfo);
    targetTimeType = TargetTimeType.END;
  }

  @Override
  protected List<ChartPatternSignal> getChartPatternSignalsThatLongSinceReachedTargetTime() {
    return dao.getChatPatternSignalsThatLongSinceReachedTargetTime();
  }

  @Override
  protected void markFailedToGetTargetPrice(ChartPatternSignal chartPatternSignal) {
    dao.failedToGetPriceAtSignalTargetTime(chartPatternSignal);
  }

  @Override
  protected long getStartTimeWindow(ChartPatternSignal chartPatternSignal) {
    return chartPatternSignal.priceTargetTime().getTime();
  }
}
