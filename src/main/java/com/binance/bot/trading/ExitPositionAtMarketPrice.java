package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.Mailer;
import com.binance.bot.common.Util;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.OutstandingTrades;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

@Component
/** For exiting at market price either due to signal invalidation, target time elapsed, or profit taking. */
public class ExitPositionAtMarketPrice {
  private final BinanceApiMarginRestClient binanceApiMarginRestClient;
  private final ChartPatternSignalDaoImpl dao;
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  public Mailer mailer;
  private final RepayBorrowedOnMargin repayBorrowedOnMargin;
  private final OutstandingTrades outstandingTrades;
  private final SupportedSymbolsInfo supportedSymbolsInfo;
  private final BookTickerPrices bookTickerPrices;
  private final CrossMarginAccountBalance crossMarginAccountBalance;
  @Value("${do_not_decrement_num_outstanding_trades}")
  boolean doNotDecrementNumOutstandingTrades;

  @Autowired
  ExitPositionAtMarketPrice(BinanceApiClientFactory binanceApiClientFactory, ChartPatternSignalDaoImpl dao,
                            Mailer mailer, RepayBorrowedOnMargin repayBorrowedOnMargin,
                            OutstandingTrades outstandingTrades,
                            SupportedSymbolsInfo supportedSymbolsInfo,
                            BookTickerPrices bookTickerPrices,
                            CrossMarginAccountBalance crossMarginAccountBalance) {
    this.binanceApiMarginRestClient = binanceApiClientFactory.newMarginRestClient();
    this.dao = dao;
    this.mailer = mailer;
    this.repayBorrowedOnMargin = repayBorrowedOnMargin;
    this.outstandingTrades = outstandingTrades;
    this.supportedSymbolsInfo = supportedSymbolsInfo;
    this.bookTickerPrices = bookTickerPrices;
    this.crossMarginAccountBalance = crossMarginAccountBalance;
  }

  private void cancelStopLimitOrder(ChartPatternSignal chartPatternSignal) throws BinanceApiException, ParseException, InterruptedException {
      OrderStatusRequest stopLimitOrderStatusRequest = new OrderStatusRequest(
          chartPatternSignal.coinPair(), chartPatternSignal.exitStopLimitOrder().orderId());
      // To get the most update from binance.
      Order stopLimitOrderStatus = binanceApiMarginRestClient.getOrderStatus(stopLimitOrderStatusRequest);
      //logger.info(String.format("Status of the stop limit order: %s.", stopLimitOrderStatus));
      dao.updateExitStopLimitOrder(chartPatternSignal, ChartPatternSignal.Order.create(stopLimitOrderStatus.getOrderId(),
          stopLimitOrderStatus.getExecutedQty() != null ? numberFormat.parse(stopLimitOrderStatus.getExecutedQty()).doubleValue() : 0,
          stopLimitOrderStatus.getPrice() != null ? numberFormat.parse(stopLimitOrderStatus.getPrice()).doubleValue() : 0,
          stopLimitOrderStatus.getStatus()));
      logger.info("Sleeping for a second between update and a query.");
      // To try if the empty result problem goes away.
      Thread.sleep(1000);
      logger.info("Woke up from sleep.");
      chartPatternSignal = dao.getChartPattern(chartPatternSignal);
      if (chartPatternSignal.isPositionExited() == null || Boolean.TRUE.equals(chartPatternSignal.isPositionExited())) {
        logger.info(String.format("cps.isPositionExited being %s, do nothing for cps %s.", chartPatternSignal.isPositionExited() == null ? "null" : "true", chartPatternSignal));
      }
      logger.info(String.format("Found position to exit: %s.", chartPatternSignal.toStringOrderValues()));

      if (stopLimitOrderStatus.getStatus() != OrderStatus.CANCELED) {
        CancelOrderRequest cancelStopLimitOrderRequest = new CancelOrderRequest(
            chartPatternSignal.coinPair(), chartPatternSignal.exitStopLimitOrder().orderId());
        CancelOrderResponse cancelStopLimitOrderResponse = binanceApiMarginRestClient.cancelOrder(cancelStopLimitOrderRequest);
        logger.info(String.format("Cancelled Stop Limit Order with response status %s.", cancelStopLimitOrderResponse.getStatus().name()));
        dao.cancelStopLimitOrder(chartPatternSignal);
      }
  }

  public void exitPositionIfStillHeld(
      ChartPatternSignal chartPatternSignal, TradeExitType tradeExitType) throws MessagingException {
    try {
      if (chartPatternSignal.isPositionExited() == null || Boolean.TRUE.equals(chartPatternSignal.isPositionExited()
          || chartPatternSignal.entryOrder() == null)) {
        return;
      }
      logger.info(String.format("Going to exit position for cps %s.", chartPatternSignal));
      double qtyToExit = chartPatternSignal.entryOrder().executedQty();
      // This is because there were times when stop loss order failed to be placed due to bugs.
      if (chartPatternSignal.exitStopLimitOrder() != null) {
        // If partial order has been executed it could only be the stop limit order.
        // exitStopLimitOrder can never be null.
        if (chartPatternSignal.exitStopLimitOrder().status() == ChartPatternSignal.Order.OrderStatusInt.PARTIALLY_FILLED) {
          qtyToExit -= chartPatternSignal.exitStopLimitOrder().executedQty();
          logger.info(String.format("Need to exit the remaining %f qty.", qtyToExit));
        } else {
          logger.info(String.format("Need to exit the %f qty.", qtyToExit));
        }
        // Cancel the stop limit order first to unlock the quantity.
        cancelStopLimitOrder(chartPatternSignal);
      }
      String baseAsset = Util.getBaseAsset(chartPatternSignal.coinPair());
      MarginAssetBalance assetBalance = binanceApiMarginRestClient.getAccount().getAssetBalance(baseAsset);
      double freeBalance = numberFormat.parse(assetBalance.getFree()).doubleValue();
      double lockedBalance = numberFormat.parse(assetBalance.getLocked()).doubleValue();
      double borrowed = numberFormat.parse(assetBalance.getBorrowed()).doubleValue();
      logger.info(String.format("Asset %s quantity free: %f, quantity locked: %f, borrowed: %f",
          baseAsset, freeBalance, lockedBalance, borrowed));
      double qtyToExitAvail = chartPatternSignal.tradeType() == TradeType.BUY ? freeBalance : borrowed;
      if (qtyToExitAvail < qtyToExit) {
        // TODO: Remove.
        if (Util.decimalCompare(qtyToExitAvail, qtyToExit * 0.999)) {
          logger.warn( String.format("Expected to find %f quantity of %s to exit but asset found only %f in cross margin account balance, but appears to be pre-bug fix for commissions deduction, hence proceeding to exit available quantity.",
              qtyToExit, baseAsset, qtyToExitAvail));
          qtyToExit = qtyToExitAvail;
        } else {
          String errorMsg = String.format("Expected to find %f quantity of %s to exit but asset found only %f in cross margin account balance.",
              qtyToExit, baseAsset, qtyToExitAvail);
          logger.error(errorMsg);
          mailer.sendEmail("Asset quantity expected amount to exit not found.", errorMsg);
          return;
        }
      }
      if (qtyToExit > 0) {
        Pair<Double, Integer> minNotionalAndLotSize = supportedSymbolsInfo.getMinNotionalAndLotSize(
            chartPatternSignal.coinPair());
        String qtyToExitStr;
        // For BUY type trades, the qty specified in the sell to exit order should be the exact amount in the account.
        // Hence the BinanceTradingBot would need to get the actual fill quantity and maintain that info, rather than
        // the quantity without taking commissions into account.
        // Commission is taken on teh USDT proceeds not on the base asset during a sell, hence this logic.
        if (chartPatternSignal.tradeType() == TradeType.SELL) {
          // TODO: Need to do this for the Stop loss order also.
          qtyToExit /= 0.999;
          if (minNotionalAndLotSize == null) {
            String errMsg = String.format("Unexpectedly supportedSymbolsInfo.getMinNotionalAndLotSize returned null for %s. Not exiting trade.", chartPatternSignal.coinPair());
            logger.error(errMsg);
            mailer.sendEmail("Missing minNotionalAndLotSize", errMsg);
            return;
          }
          qtyToExitStr = Util.getRoundedUpQuantity(qtyToExit, minNotionalAndLotSize.getSecond());
          MarginAccount marginAccount = binanceApiMarginRestClient.getAccount();
          // Borrow 0.1% more to account for any fluctuations in market price.
          int usdtValueNeededToBuyBackCoin = (int) (Math.ceil(Double.parseDouble(qtyToExitStr) * bookTickerPrices.getBookTicker(chartPatternSignal.coinPair()).bestAsk()) * 1.001);
          int freeUSDT = (int) numberFormat.parse(marginAccount.getAssetBalance("USDT").getFree()).doubleValue();
          if (freeUSDT < usdtValueNeededToBuyBackCoin) {
            double marginLevel = numberFormat.parse(marginAccount.getMarginLevel()).doubleValue();
            if (marginLevel < 1.2) {
              String errMsg = String.format("Not borrowing USDT to buy back %f quantity of %s due to margin at dangerously low level of %f, cps=%s.",
                  qtyToExit, chartPatternSignal.coinPair(), marginLevel, chartPatternSignal);
              logger.warn(errMsg);
              mailer.sendEmail("Margin at high risk", errMsg);
              return;
            }
            int usdtMoreNeeded = usdtValueNeededToBuyBackCoin - freeUSDT;
            Pair<Integer, Integer> totalAndBorrowedAccountValueInUSDT = crossMarginAccountBalance.getTotalAndBorrowedUSDTValue();
            double marginLevelNew = ((double) (totalAndBorrowedAccountValueInUSDT.getFirst() + usdtMoreNeeded)) / (totalAndBorrowedAccountValueInUSDT.getSecond() + usdtMoreNeeded);
            if (marginLevelNew < 1.2) {
              String errMsg = String.format("Not borrowing USDT to buy back %f quantity of %s due to margin will go to dangerously low level of %f, cps=%s.",
                  qtyToExit, chartPatternSignal.coinPair(), marginLevelNew, chartPatternSignal);
              logger.warn(errMsg);
              mailer.sendEmail("Margin at high risk", errMsg);
              return;
            }
            logger.info(String.format("Borrowing %d USDT in order to buy back shorted cps %s.", usdtMoreNeeded, chartPatternSignal));
            binanceApiMarginRestClient.borrow("USDT", "" + usdtMoreNeeded);
          }
        } else {
          qtyToExitStr = Util.getTruncatedQuantity(qtyToExit, minNotionalAndLotSize.getSecond());
        }
        MarginNewOrder marketExitOrder = new MarginNewOrder(chartPatternSignal.coinPair(),
            chartPatternSignal.tradeType() == TradeType.BUY ? OrderSide.SELL : OrderSide.BUY,
            OrderType.MARKET, /* timeInForce= */ null,
            // TODO: In corner cases, will have to round up this quantity.
            qtyToExitStr).newOrderRespType(NewOrderResponseType.FULL);
        logger.info(String.format("Sending market exit order %s.", marketExitOrder));
        MarginNewOrderResponse marketExitOrderResponse = binanceApiMarginRestClient.newOrder(marketExitOrder);
        logger.info(String.format("Executed %s order and got the response: %s.",
            chartPatternSignal.tradeType() == TradeType.BUY ? "sell" : "buy",
            marketExitOrderResponse));
        // Trade here isof the reverse direction as the chart pattern entry trade type.
        TradeFillData tradeFillData = new TradeFillData(marketExitOrderResponse,
            chartPatternSignal.tradeType() == TradeType.BUY ? TradeType.SELL : TradeType.BUY);
        dao.setExitOrder(chartPatternSignal,
            ChartPatternSignal.Order.create(marketExitOrderResponse.getOrderId(),
                tradeFillData.getQuantity(),
                tradeFillData.getAvgPrice(), marketExitOrderResponse.getStatus()), tradeExitType);
        mailer.sendEmail(String.format("Exited trade for %s.", tradeExitType.name()), chartPatternSignal.toString());
        if (chartPatternSignal.tradeType() == TradeType.SELL) {
          repayBorrowedOnMargin.repay(baseAsset, tradeFillData.getQuantity());
        }
        if (!doNotDecrementNumOutstandingTrades) {
          outstandingTrades.decrementNumOutstandingTrades(chartPatternSignal.timeFrame());
        }
      }
      dao.writeAccountBalanceToDB();
    } catch (Exception ex) {
      logger.error(String.format("Exception for trade exit type %s." , tradeExitType.name()), ex);
      mailer.sendEmail("ExitPositionAtMarketPrice uncaught exception for trade exit type" + tradeExitType.name(), ex.getMessage());
    }
  }
}
