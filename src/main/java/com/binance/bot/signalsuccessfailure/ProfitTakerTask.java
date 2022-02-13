package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
public class ProfitTakerTask {
  private final ChartPatternSignalDaoImpl dao;
  private final BookTickerPrices bookTickerPrices;
  private final MarketPriceStream marketPriceStream;
  private final BinanceApiRestClient binanceApiRestClient;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  ProfitTakerTask(ChartPatternSignalDaoImpl dao,
                  BookTickerPrices bookTickerPrices,
                  MarketPriceStream marketPriceStream,
                  BinanceApiClientFactory binanceApiRestClientFactory) {
    this.dao = dao;
    this.bookTickerPrices = bookTickerPrices;
    this.marketPriceStream = marketPriceStream;
    this.binanceApiRestClient = binanceApiRestClientFactory.newRestClient();
  }

  @Scheduled(fixedDelay = 60000)
  public void perform() throws IOException {
    List<ChartPatternSignal> activePositions = dao.getAllChartPatternsWithActiveTradePositions();
    for (ChartPatternSignal activePosition: activePositions) {
      marketPriceStream.addSymbol(activePosition.coinPair());
      BookTickerPrices.BookTicker bookTicker = bookTickerPrices.getBookTicker(activePosition.coinPair());
      if (bookTicker == null) {
        continue;
      }
      if (isPriceTargetMet(activePosition, bookTicker)) {
        logger.info(String.format("Price taret met for chart pattern signal:\n.", activePosition));
        CancelOrderRequest cancelStopLimitOrder = new CancelOrderRequest(activePosition.coinPair(), activePosition.exitStopLimitOrder().orderId());
        CancelOrderResponse cancelStopLimitOrderResponse = binanceApiRestClient.cancelOrder(cancelStopLimitOrder);
        if (cancelStopLimitOrderResponse.getStatus() == OrderStatus.FILLED) {
          logger.info("Cancelled stop limit order for the pattern.");

        } else {
          logger.error("Cancel stop limit order is not FILLED but " + cancelStopLimitOrderResponse.getStatus().name());
        }
      }
    }
  }

  private boolean isPriceTargetMet(ChartPatternSignal activePosition, BookTickerPrices.BookTicker bookTicker) {
  }
}
