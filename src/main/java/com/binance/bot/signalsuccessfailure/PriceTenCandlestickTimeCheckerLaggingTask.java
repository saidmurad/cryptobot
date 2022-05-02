package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.trading.ExitPositionAtMarketPrice;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.util.List;

import static com.binance.bot.common.Util.getProfitPercentAtWithPrice;
import static com.binance.bot.common.Util.getTenCandleStickTimeIncrementMillis;

@Component
public class PriceTenCandlestickTimeCheckerLaggingTask extends PriceTargetCheckerLaggingTask {
  @Value("${exit_trades_at_ten_candlestick_time}")
  boolean exitTradesAtTenCandlestickTime;
  private final ExitPositionAtMarketPrice exitPositionAtMarketPrice;
  @Autowired
  PriceTenCandlestickTimeCheckerLaggingTask(BinanceApiClientFactory binanceApiClientFactory,
                                            ChartPatternSignalDaoImpl dao,
                                            SupportedSymbolsInfo supportedSymbolsInfo,
                                            ExitPositionAtMarketPrice exitPositionAtMarketPrice) {
    super(binanceApiClientFactory, dao, supportedSymbolsInfo);
    targetTimeType = TargetTimeType.TEN_CANDLESTICK;
    this.exitPositionAtMarketPrice = exitPositionAtMarketPrice;
  }

  @Override
  protected List<ChartPatternSignal> getChartPatternSignalsThatLongSinceReachedTargetTime() {
    return dao.getChatPatternSignalsThatLongSinceReachedTenCandleStickTime();
  }

  @Override
  protected boolean setTargetPrice(ChartPatternSignal chartPatternSignal, double tenCandleStickTimePrice) throws MessagingException {
    if (exitTradesAtTenCandlestickTime) {
      exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.TEN_CANDLESTICK_TIME_PASSED);
    }
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
