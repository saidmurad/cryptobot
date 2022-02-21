package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.heartbeatchecker.HeartBeatChecker;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.trading.BinanceTradingBot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.binance.bot.database.ChartPatternSignalDaoImpl;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

@Component
public class StopLimitOrderStatusChecker {
  private final ChartPatternSignalDaoImpl dao;
  private final BinanceApiRestClient binanceApiRestClient;
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  @Autowired
  StopLimitOrderStatusChecker(ChartPatternSignalDaoImpl dao, BinanceApiClientFactory binanceApiRestClientFactory) {
    this.dao = dao;
    this.binanceApiRestClient = binanceApiRestClientFactory.newRestClient();
  }

  @Scheduled(fixedDelay = 60000, initialDelayString = "${timing.initialDelay}")
  public void perform() throws ParseException, IOException, BinanceApiException {
    HeartBeatChecker.logHeartBeat(getClass());
    List<ChartPatternSignal> activePositions = dao.getAllChartPatternsWithActiveTradePositions();
    for (ChartPatternSignal activePosition: activePositions) {
      if (activePosition.exitStopLimitOrder() == null) {
        continue;
      }
      OrderStatusRequest orderStatusRequest = new OrderStatusRequest(
          activePosition.coinPair(), activePosition.exitStopLimitOrder().orderId());
      Order orderStatus = binanceApiRestClient.getOrderStatus(orderStatusRequest);
      if (orderStatus.getStatus() == OrderStatus.FILLED ||
          orderStatus.getStatus() == OrderStatus.PARTIALLY_FILLED) {
        dao.updateExitStopLimitOrder(activePosition, orderStatus);
      }
    }
  }
}
