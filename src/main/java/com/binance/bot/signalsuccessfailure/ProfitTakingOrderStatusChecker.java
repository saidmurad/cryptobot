package com.binance.bot.signalsuccessfailure;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.trading.BinanceTradingBot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.binance.bot.database.ChartPatternSignalDaoImpl;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.List;
import java.util.Locale;

@Component
public class ProfitTakingOrderStatusChecker {
  @Autowired
  private ChartPatternSignalDaoImpl dao;
  @Autowired
  private BinanceTradingBot binanceTradingBot;
  @Autowired
  private BinanceApiClientFactory binanceApiRestClientFactory;
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  @Scheduled(fixedDelay = 60000)
  public void perform() throws ParseException {
    List<ChartPatternSignal> activePositions = dao.getAllChartPatternsWithActiveTradePositions();
    BinanceApiRestClient restClient = binanceApiRestClientFactory.newRestClient();
    for (ChartPatternSignal activePosition: activePositions) {
      OrderStatusRequest orderStatusRequest = new OrderStatusRequest(activePosition.coinPair(), activePosition.exitLimitOrder().orderId());
      Order orderStatus = restClient.getOrderStatus(orderStatusRequest);
      if (orderStatus.getStatus() == OrderStatus.FILLED ||
          orderStatus.getStatus() == OrderStatus.PARTIALLY_FILLED &&
              numberFormat.parse(orderStatus.getExecutedQty()).doubleValue() > activePosition.exitLimitOrder().executedQty()) {
        double executedQtyInOrderStatus = numberFormat.parse(orderStatus.getExecutedQty()).doubleValue();
        dao.setExitMarketOrder(activePosition,
            ChartPatternSignal.Order.create(orderStatus.getOrderId(), executedQtyInOrderStatus,
                numberFormat.parse(orderStatus.getPrice()).doubleValue(), orderStatus.getStatus()));
      }
    }
  }
}