package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.signalsuccessfailure.specifictradeactions.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.text.ParseException;
import java.util.List;

import static com.binance.bot.common.Util.getProfitPercentAtWithPrice;
import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

@Component
public class PriceSignalTargetTimeCheckerLaggingTask extends PriceTargetCheckerLaggingTask {
  @Autowired
  private ExitPositionAtMarketPrice exitPositionAtMarketPrice;
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

  // TODO: Unit test doesn't exist for this calss as it is a subclass providing only the overrides.
  @Override
  protected boolean setTargetPrice(ChartPatternSignal chartPatternSignal, double targetTimePrice) throws MessagingException, ParseException, BinanceApiException {
    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, targetTimePrice, TradeExitType.TARGET_TIME_PASSED);
    return dao.setSignalTargetTimePrice(chartPatternSignal, targetTimePrice,
        getProfitPercentAtWithPrice(chartPatternSignal, targetTimePrice));
  }
}
