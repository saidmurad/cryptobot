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
import com.binance.bot.database.OutstandingTrades;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.BinanceTradingBot;
import com.binance.bot.trading.RepayBorrowedOnMargin;
import com.binance.bot.trading.TradeFillData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
  private final OutstandingTrades outstandingTrades;
  public Mailer mailer = new Mailer();
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  @Value("${do_not_decrement_num_outstanding_trades}")
  boolean doNotDecrementNumOutstandingTrades;

  @Autowired
  StopLimitOrderStatusChecker(ChartPatternSignalDaoImpl dao, RepayBorrowedOnMargin repayBorrowedOnMargin,
                              BinanceApiClientFactory binanceApiClientFactory,
                              OutstandingTrades outstandingTrades) {
    this.dao = dao;
    this.binanceApiMarginRestClient = binanceApiClientFactory.newMarginRestClient();
    this.repayBorrowedOnMargin = repayBorrowedOnMargin;
    this.outstandingTrades = outstandingTrades;
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
          String msg = String.format("Stop limit order found executed %s for cps %s.", orderStatus, activePosition);
          logger.info(msg);
          mailer.sendEmail("Stop loss hit", msg);
          Double executedQty = numberFormat.parse(orderStatus.getExecutedQty()).doubleValue();
          // TODO: Take into consideration commissions for pnl calculations.
          // When closing a short position, commission is deducted on the base asset we receive as proceeds.
          if (activePosition.tradeType() == TradeType.SELL) {
            executedQty *= 0.999;
          }
          /**
           The 'price' seems to be the stop limit price placed for the stop limit order, while the stopLomitPrice is null, weird.
           Order[symbol=ETHUSDT,orderId=8758446990,clientOrderId=bwk9Ti2ji1MinoPzaAa0tX,price=2802.68,origQty=0.0037,executedQty=0.0037,status=FILLED,timeInForce=GTC,type=STOP_LOSS_LIMIT,side=SELL,stopPrice=2803.16,stopLimitPrice=<null>,icebergQty=0,time=1651443951248,cummulativeQuoteQty=10.371618,updateTime=1651443965293,isWorking=true,origQuoteOrderQty=<null>]
           */
          dao.updateExitStopLimitOrder(activePosition,
              ChartPatternSignal.Order.create(orderStatus.getOrderId(), executedQty,
                  numberFormat.parse(orderStatus.getStopPrice()).doubleValue(), orderStatus.getStatus()));
          if (orderStatus.getStatus() == OrderStatus.FILLED) {
            if (activePosition.tradeType() == TradeType.SELL) {
              repayBorrowedOnMargin.repay(Util.getBaseAsset(activePosition.coinPair()), executedQty);
            }
            if (!doNotDecrementNumOutstandingTrades) {
              outstandingTrades.decrementNumOutstandingTrades(activePosition.timeFrame());
            }
          }
        }
        dao.writeAccountBalanceToDB();
      }
    } catch (Exception ex) {
      logger.error("Exception.", ex);
      mailer.sendEmail("StopLimitOrderStatusChecker uncaught exception.", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getCanonicalName());
    }
  }
}
