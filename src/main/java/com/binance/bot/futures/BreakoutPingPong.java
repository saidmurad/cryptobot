package com.binance.bot.futures;

import com.binance.bot.tradesignals.TradeType;
import com.binance.client.SyncRequestClient;
import com.binance.client.model.enums.*;
import com.binance.client.model.market.Candlestick;
import com.binance.client.model.trade.*;
import com.google.auto.value.AutoValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

public class BreakoutPingPong implements Runnable{

  private static final double GAP_PERCENTAGE = 1.5;
  private final Logger logger = LoggerFactory.getLogger(BreakoutPingPong.class);

  private static final int MY_LEVERAGE_MULTIPLIER = 5;
  private static final int INITIAL_MARGIN_SIZE = 10000;
  @Autowired
  private SyncRequestClient syncRequestClient;

  @AutoValue
  private abstract static class EnterFutureWatch {
    abstract String coinPair();

    abstract TradeType tradeType();

    abstract double priceLevel();

    abstract double priceTarget();

    abstract long fiveMinVolThreshold();

    // TODO: ImplementAPI /v1/leveragebracket
    abstract int leverageBracket();

    // TODO: Add min notional in  /exchangeInfo api response instead of hard coding as 0.01.
    abstract double notionalOrderSize();

    abstract double rangeOfInterestMin();

    abstract double rangeOfInterestMax();
  }

  static class BookKeeping {
    boolean isInPosition = false;

    long stopLossOrderId;

    long takeProfitOrderId;

    double cumulativePnL = 0;

    double commissions = 0;
    Long timeOfEntry;
    double entryPrice;

    boolean isStoplossMovedToProfit = false;

    boolean isDone = false;
  }

  private final Map<EnterFutureWatch, BookKeeping> enterFuturesWatchMap = new HashMap<>();

  public BreakoutPingPong() {

  }

  @Override
  public void run() {
    outer:
    while (true) {
      for (Map.Entry<EnterFutureWatch, BookKeeping> enterFutureWatchEntry : enterFuturesWatchMap.entrySet()) {
        EnterFutureWatch enterFutureWatch = enterFutureWatchEntry.getKey();
        BookKeeping bookKeeping = enterFutureWatchEntry.getValue();
        if (bookKeeping.isDone) {
          continue;
        }
        AccountInformation accountInfo = syncRequestClient.getAccountInformation();
        long usdtAvailable = accountInfo.getAssets().stream().filter(asset -> asset.getAsset().equals("USDT")).findFirst().get().getMarginBalance().longValue();
        logger.info("USDT available = " + usdtAvailable);
        if (INITIAL_MARGIN_SIZE < usdtAvailable) {
          logger.error("Initial margin required is " + INITIAL_MARGIN_SIZE + " . Exiting.");
          break outer;
        }
        Optional<Position> symbolInfoOptional = syncRequestClient.getAccountInformation().getPositions().stream().filter(position -> position.getSymbol().equals(enterFutureWatch.coinPair())).findFirst();
        if (!symbolInfoOptional.isPresent()) {
          logger.error("Symbol " + enterFutureWatch.coinPair() + " unsupported.");
          continue;
        }
        double markPrice = syncRequestClient.getMarkPrice(enterFutureWatch.coinPair()).get(0).getMarkPrice().doubleValue();
        if (!bookKeeping.isInPosition) {
          if (isPriceOutOfHands(markPrice, enterFutureWatch)) {
            continue;
          }
          if (isVolumeSurge(enterFutureWatch)) {
            logger.info("Entering into position for " + enterFutureWatch.coinPair() + " at mark price " + markPrice);
            syncRequestClient.changeInitialLeverage(enterFutureWatch.coinPair(), enterFutureWatch.leverageBracket());
            syncRequestClient.changeMarginType(enterFutureWatch.coinPair(), "ISOLATED");

            Order notionalOrder = placeMarketOrder(enterFutureWatch, enterFutureWatch.notionalOrderSize());
            logger.info("Created notional position for size " + notionalOrder.getExecutedQty().doubleValue());

            double marginNeededForPurchasing = INITIAL_MARGIN_SIZE * MY_LEVERAGE_MULTIPLIER / enterFutureWatch.leverageBracket();
            double quantity = INITIAL_MARGIN_SIZE * MY_LEVERAGE_MULTIPLIER / markPrice;
            syncRequestClient.addIsolatedPositionMargin(enterFutureWatch.coinPair(), /* add */ 1, String.valueOf(INITIAL_MARGIN_SIZE - marginNeededForPurchasing), PositionSide.BOTH);
            String amount = syncRequestClient.getPositionMarginHistory(enterFutureWatch.coinPair(), /* add */ 1, System.currentTimeMillis() - 5000, System.currentTimeMillis(), 1).get(0).getAmount();
            logger.info("Initial margin set to " + amount);

            Order limitOrder = placeLimitOrder(enterFutureWatch, markPrice * 1.01, quantity, false);
            logger.info(String.format("Placed Limit order id = %d. Initial executed quantity = %s.", limitOrder.getOrderId(), limitOrder.getExecutedQty()));
            Order limitOrderStatus = waitForLimitOrderExecution(enterFutureWatch.coinPair(), limitOrder.getOrderId());
            logger.info(String.format("Limit order executed for quantity %s and entry price %s.", limitOrder.getExecutedQty(), limitOrder.getPrice()));
            bookKeeping.entryPrice = limitOrderStatus.getPrice().doubleValue();
            bookKeeping.timeOfEntry = limitOrderStatus.getUpdateTime();

            // Place Stop loss and take profit orders.
            double entryPrice = limitOrder.getPrice().doubleValue();
            Order stoplossOrder = placeStopLossOrder(enterFutureWatch, entryPrice * 0.98);
            bookKeeping.stopLossOrderId = stoplossOrder.getOrderId();

            Order takeProfitOrder = placeLimitOrder(
                enterFutureWatch, enterFutureWatch.priceTarget(), quantity + enterFutureWatch.notionalOrderSize(), true);
            bookKeeping.takeProfitOrderId = takeProfitOrder.getOrderId();
            logger.info("Placed stop loss and take profit orders.");
            bookKeeping.isInPosition = true;
          }
        } else {
          // Retrieve position and print.
          PositionRisk positionRisk = syncRequestClient.getPositionRisk().stream().filter(position -> position.getSymbol().equals(enterFutureWatch.coinPair())).findFirst().get();
          printPosition(positionRisk);
          if (positionRisk.getPositionAmt().doubleValue() < 0.0000001) {
            logger.error("Found no position but expected some.");
            bookKeeping.isInPosition = false;
            // TODO: Handle the (unlikely) scenario that the position had closed due to profit target uncaught by this code.
            // Right now assuming stop lossed and continuing to try and re-enter position.
            lookupTrades(enterFutureWatch.coinPair(), bookKeeping, /* isStopLoss= */ true);
            continue;
          }
          // Check order statuses.
          Order stoplossOrder = syncRequestClient.getOrder(enterFutureWatch.coinPair(), bookKeeping.stopLossOrderId, null);
          if (stoplossOrder.getStatus().equals("FILLED")) {
            lookupTrades(enterFutureWatch.coinPair(), bookKeeping, /* isStopLoss= */ true);
            bookKeeping.isInPosition = false;
          } else {
            Order takeProfitOrder = syncRequestClient.getOrder(enterFutureWatch.coinPair(), bookKeeping.takeProfitOrderId, null);
            if (takeProfitOrder.getStatus().equals("FILLED")) {
              bookKeeping.isInPosition = false;
              bookKeeping.isDone = true;
              lookupTrades(enterFutureWatch.coinPair(), bookKeeping, /* isStopLoss= */ false);
            } else if (!bookKeeping.isStoplossMovedToProfit) {
              if (markPrice > bookKeeping.entryPrice * 1.01) {
                Order cancelOrder = syncRequestClient.cancelOrder(enterFutureWatch.coinPair(), stoplossOrder.getOrderId(), null);
                if (cancelOrder.getStatus().equals("CANCELED")) {
                  logger.error(String.format("Unable to cancel stop loss order id %d. Got status '%s'. May be the position got closed already.", stoplossOrder.getOrderId(), cancelOrder.getStatus()));
                } else {
                  logger.info(String.format("Canceled stop loss order placed for stop price %s.", stoplossOrder.getStopPrice()));
                  Order newStopLossOrder = placeStopLossOrder(enterFutureWatch, bookKeeping.entryPrice * 1.005);
                  bookKeeping.stopLossOrderId = newStopLossOrder.getOrderId();
                  logger.info(String.format("Moved stop loss to profit at %f.", newStopLossOrder.getStopPrice()));
                }
              }
            }
          }
        }
      }
      try {
        Thread.sleep(60000);
      } catch (InterruptedException e) {
        logger.error("Sleep 60000 interrupted.", e);
      }
    }
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

  private void lookupTrades(String coinPair, BookKeeping bookKeeping, boolean isStopLoss) {
    List<MyTrade> stopLossTrades = syncRequestClient.getAccountTrades(coinPair, bookKeeping.timeOfEntry, /* endTime=*/null, /* fromId= */ null, /* limit= */ null);
    double profitLoss = 0;
    double commissions = 0;
    for (MyTrade stopLossTrade : stopLossTrades) {
      profitLoss += stopLossTrade.getRealizedPnl().doubleValue();
      commissions += stopLossTrade.getCommission().doubleValue();
    }
    String tradeType = isStopLoss? "Stop loss" : "Take Profit";
    logger.info(String.format(tradeType + " realized=%f and commissions=%f %s", profitLoss, commissions, stopLossTrades.get(0).getCommissionAsset()));
    bookKeeping.cumulativePnL += profitLoss;
    bookKeeping.commissions += commissions;
    logger.info(String.format("Cumulative profit loss=%f and commissions=%f %s", bookKeeping.cumulativePnL, bookKeeping.commissions, stopLossTrades.get(0).getCommissionAsset()));
  }

  private Order placeStopLossOrder(EnterFutureWatch enterFutureWatch, double stopPrice) {
    return syncRequestClient.postOrder(enterFutureWatch.coinPair(), OrderSide.SELL, PositionSide.BOTH, OrderType.STOP_MARKET, null,
        "quantity", /* price= */ null, /* reduceOnly= */ "true", null, String.format("%.2f", stopPrice), WorkingType.MARK_PRICE, NewOrderRespType.RESULT);
  }

  private Order waitForLimitOrderExecution(String coinPair, Long orderId) {
    Order limitOrder = null;
    while (limitOrder == null || !limitOrder.getStatus().equals("FILLED")) {
      limitOrder = syncRequestClient.getOrder(coinPair, orderId, null);
      logger.info("Limit order status: executed qty=" + limitOrder.getExecutedQty());
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        logger.error("Sleep interrupted between getting order status.", e);
        throw new RuntimeException(e);
      }
    }
    return limitOrder;
  }

  // TODO: SELL type
  private Order placeMarketOrder(EnterFutureWatch enterFutureWatch, double quantity) {
    return syncRequestClient.postOrder(enterFutureWatch.coinPair(), OrderSide.BUY, PositionSide.BOTH, OrderType.MARKET, null,
        Double.toString(quantity), null, "false", null, null, WorkingType.MARK_PRICE, NewOrderRespType.RESULT);
  }

  private Order placeLimitOrder(EnterFutureWatch enterFutureWatch, double limitPrice, double quantity, boolean isReduceOnly) {
    return syncRequestClient.postOrder(
        enterFutureWatch.coinPair(), OrderSide.BUY, PositionSide.BOTH, OrderType.LIMIT, TimeInForce.GTC,
        Double.toString(quantity), String.format("%.2f", limitPrice), /* reduceOnly= */Boolean.toString(isReduceOnly), null, null, WorkingType.MARK_PRICE, NewOrderRespType.RESULT);
  }

  private boolean isVolumeSurge(EnterFutureWatch enterFutureWatch) {
    List<Candlestick> candlesticks = syncRequestClient.getCandlestick(enterFutureWatch.coinPair(), CandlestickInterval.FIVE_MINUTES, null, null, 2);
    switch (enterFutureWatch.tradeType()) {
      case BUY:
        // Needs a green candle surge.
        return candlesticks.get(0).getVolume().longValue() >= enterFutureWatch.fiveMinVolThreshold()
            && candlesticks.get(0).getClose().longValue() >= candlesticks.get(0).getOpen().longValue();
      default:
        return false;
    }
  }


  private boolean isPriceOutOfHands(double markPrice, EnterFutureWatch enterFutureWatch) {
    switch (enterFutureWatch.tradeType()) {
      case BUY:
        if (markPrice < enterFutureWatch.priceLevel() && markPrice >= enterFutureWatch.priceLevel() * (100 - GAP_PERCENTAGE)/100) {
          return false;
        }
        break;
    }
    return true;
  }
}
