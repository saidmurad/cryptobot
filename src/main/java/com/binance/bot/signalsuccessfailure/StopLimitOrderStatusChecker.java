package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.Mailer;
import com.binance.bot.common.Util;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.AccountBalanceDao;
import com.binance.bot.trading.BinanceTradingBot;
import com.binance.bot.trading.RepayBorrowedOnMargin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.binance.bot.database.ChartPatternSignalDaoImpl;

import javax.mail.MessagingException;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

import static com.binance.bot.common.Util.getDoubleValue;

@Component
public class StopLimitOrderStatusChecker {
  private final ChartPatternSignalDaoImpl dao;
  private final RepayBorrowedOnMargin repayBorrowedOnMargin;
  private final BinanceApiMarginRestClient binanceApiMarginRestClient;
  private final AccountBalanceDao accountBalanceDao;
  private final Mailer mailer = new Mailer();
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  @Autowired
  StopLimitOrderStatusChecker(ChartPatternSignalDaoImpl dao, RepayBorrowedOnMargin repayBorrowedOnMargin,
                              BinanceApiClientFactory binanceApiClientFactory,
                              AccountBalanceDao accountBalanceDao) {
    this.dao = dao;
    this.binanceApiMarginRestClient = binanceApiClientFactory.newMarginRestClient();
    this.repayBorrowedOnMargin = repayBorrowedOnMargin;
    this.accountBalanceDao = accountBalanceDao;
  }

  @Scheduled(fixedDelay = 60000, initialDelayString = "${timing.initialDelay}")
  public void perform() throws MessagingException {
    try {
      HeartBeatChecker.logHeartBeat(getClass());
      List<ChartPatternSignal> activePositions = dao.getAllChartPatternsWithActiveTradePositions();
      for (ChartPatternSignal activePosition : activePositions) {
        if (activePosition.exitStopLimitOrder() == null) {
          continue;
        }
        OrderStatusRequest orderStatusRequest = new OrderStatusRequest(
            activePosition.coinPair(), activePosition.exitStopLimitOrder().orderId());
        Order orderStatus = binanceApiMarginRestClient.getOrderStatus(orderStatusRequest);
        if (orderStatus.getStatus() == OrderStatus.FILLED ||
            orderStatus.getStatus() == OrderStatus.PARTIALLY_FILLED) {
          Double qty = numberFormat.parse(orderStatus.getExecutedQty()).doubleValue();
          double price = getAvgFillPrice(activePosition.coinPair(), orderStatus.getOrderId());
          dao.updateExitStopLimitOrder(activePosition,
              ChartPatternSignal.Order.create(orderStatus.getOrderId(), qty,
                  price, orderStatus.getStatus()));
          if (orderStatus.getStatus() == OrderStatus.FILLED) {
            if (activePosition.tradeType() == TradeType.SELL) {
              repayBorrowedOnMargin.repay(Util.getBaseAsset(activePosition.coinPair()), qty);
            } else {
              repayBorrowedOnMargin.repay("USDT", qty * price);
            }
          }
        }
      }
      accountBalanceDao.writeAccountBalanceToDB();
    } catch (Exception ex) {
      logger.error("Exception.", ex);
      mailer.sendEmail("StopLimitOrderStatusChecker uncaught exception.", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getCanonicalName());
    }
  }

  private double getAvgFillPrice(String coinPair, long orderId) throws BinanceApiException, ParseException {
    List<Trade> trades = binanceApiMarginRestClient.getMyTrades(coinPair, orderId);
    double weightedSum =0.0, weight = 0.0;
    for (Trade trade: trades) {
      weightedSum += getDoubleValue(trade.getQty()) * getDoubleValue(trade.getPrice());
      weight += getDoubleValue(trade.getQty());
    }
    return weightedSum / weight;
  }
}
