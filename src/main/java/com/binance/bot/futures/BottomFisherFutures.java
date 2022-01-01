package com.binance.bot.futures;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.bot.database.PositionToWatch;
import com.binance.client.SyncRequestClient;
import com.binance.client.model.enums.*;
import com.binance.client.model.market.Candlestick;
import com.binance.client.model.trade.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * The aim is to enter a position on assured no-loss, and for that we will only enter if the
 * volume is spiking enough to give a higher probability that a stop loss can be set a little distance
 * away from the entry point. Otherwise don't enter the position at all.
 */
@Service
public class BottomFisherFutures {

  private static final int INITIAL_MARGIN_USDT_PER_POSITION = 10000;
  private static final int LEVERAGE = 5;
  private static final CandlestickInterval INTERVAL = CandlestickInterval.ONE_MINUTE;
  private final SyncRequestClient syncRequestClient;
  private static final Logger logger = LoggerFactory.getLogger(BottomFisherFutures.class);

  @Autowired
  public BottomFisherFutures(BinanceApiClientFactory clientFactory) {
    this.syncRequestClient = clientFactory.newFuturesRestClient();
  }

  public void observeCoins() throws InterruptedException {
    PositionToWatch positionToWatch = PositionToWatch.builder()
        .setCoinPair("BTCUSDT")
        .setIsLong(true)
        .setPriceLevelToWatch(46000)
        .setFifteenMinVolumeThreshold(1000)
        .build();
    while (true) {
      observePosition(positionToWatch);
      Thread.sleep(60000);
    }
  }

  private boolean holding = false;
  private Long stopLossOrderId = null;
  private double realizedSoFar = 0;
  private double commissionsSoFar = 0;
  private long heldQty = 0;
  private double entryPrice;
  private void observePosition(PositionToWatch positionToWatch) {
    List<Asset> assets = syncRequestClient.getAccountInformation().getAssets();
    long usdtAvailable = assets.stream().filter(asset -> asset.getAsset().equals("USDT")).findFirst().get().getMarginBalance().longValue();
    if (usdtAvailable < INITIAL_MARGIN_USDT_PER_POSITION) {
      return;
    }
    double markPrice = syncRequestClient.getMarkPrice(positionToWatch.coinPair()).get(0).getMarkPrice().doubleValue();
    logger.info(getCurrentTimeString() + " Current mark price = " + markPrice);
    //Optional<Position> positionOptional = accountInfo.getPositions().stream().filter(position -> position.getSymbol().equals(positionToWatch.coinPair())).findFirst();
    Optional<PositionRisk> positionOptional = syncRequestClient.getPositionRisk().stream().filter(position -> position.getSymbol().equals(positionToWatch.coinPair())).findFirst();
    printPosition(positionOptional.get());
    if (stopLossOrderId != null) {
      Order stopLossOrder = syncRequestClient.getOrder(positionToWatch.coinPair(), stopLossOrderId, null);
      if (stopLossOrder.getExecutedQty().longValue() > 0) {
        MyTrade lastTrade = syncRequestClient.getAccountTrades(positionToWatch.coinPair(), null, null, null, 1).get(0);
        logger.info("Got stopped at " + lastTrade.getPrice() + " for quantity " + lastTrade.getQty() + " with commission " + lastTrade.getCommission().doubleValue());
        realizedSoFar += (lastTrade.getPrice().doubleValue() - entryPrice) * heldQty;
        commissionsSoFar += lastTrade.getCommission().doubleValue();
        logger.info("Realized so far = " + realizedSoFar);
        stopLossOrderId = null;
        holding = false;
      }
    }
      // Enter position.
      if (!holding) {
        if (shouldBuy(positionToWatch)) {
          logger.info(getCurrentTimeString() + " Placing order.");
          Order marketOrder = syncRequestClient.postOrder(positionToWatch.coinPair(), OrderSide.BUY, PositionSide.BOTH, OrderType.MARKET, null,
              "1", null, "false", null, null, WorkingType.MARK_PRICE, NewOrderRespType.RESULT);
          entryPrice = marketOrder.getPrice().doubleValue();
          heldQty = marketOrder.getExecutedQty().longValue();
          holding = true;
        } else {
          logger.info("Not yet time to buy.");
        }
      } else if (stopLossOrderId == null) {
        // Place protective stop loss order above.
        double entryPrice =positionOptional.get().getEntryPrice().doubleValue();
        if (markPrice > entryPrice * 1.001) {
          Order stopLossOrder = syncRequestClient.postOrder(positionToWatch.coinPair(), OrderSide.SELL, PositionSide.BOTH, OrderType.STOP_MARKET, null,
              "1", /* price= */null, "true", null, /* stopPrice= */ Double.toString(entryPrice * 1.0005), WorkingType.MARK_PRICE, NewOrderRespType.RESULT);
          logger.info("Placed stop loss order at price " + entryPrice * 1.0005);
          stopLossOrderId = stopLossOrder.getOrderId();
        }
      }
  }

  private String getCurrentTimeString() {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    LocalDateTime now = LocalDateTime.now();
    return dtf.format(now);
  }

  private boolean shouldBuy(PositionToWatch positionToWatch) {
    List<Candlestick> candlesticks = syncRequestClient.getCandlestick(positionToWatch.coinPair(), INTERVAL, null, null, 5);
    for (int i = 0; i < 3; i++) {
      if (!(candlesticks.get(i).getVolume().longValue() > positionToWatch.fifteenMinVolumeThreshold()
          && (i == 0 || candlesticks.get(i).getLow().longValue() < candlesticks.get(i-1).getLow().longValue()))) {
        return false;
      }
    }
    return candlesticks.get(3).getVolume().longValue() > positionToWatch.fifteenMinVolumeThreshold()
        && candlesticks.get(3).getLow().longValue() >= candlesticks.get(2).getLow().longValue();
  }

  private void printPosition(PositionRisk position) {
    logger.info(position.getSymbol() + "\nEntry price: " + position.getEntryPrice()
        + "\nSide: " + position.getPositionSide()
        + "\nQuantity: " + position.getPositionAmt()
        + "\nMark Price: " + position.getMarkPrice()
    + "\nEntry Price: " + position.getEntryPrice()
    + "\nIsolated Margin: " + position.getIsolatedMargin()
    + "\nUnrealized: " + position.getUnrealizedProfit());
  }
}
