package com.binance.bot.signalsuccessfailure;

import com.binance.bot.common.CandlestickUtil;
import com.binance.bot.common.Util;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.api.SpotApi;
import io.gate.gateapi.models.Order;
import io.gate.gateapi.models.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.binance.bot.tradesignals.ChartPatternSignal.Order.convertGateIoOrderStatus;

@Component
public class OrderMonitoringGateIo {
  private final ChartPatternSignalDaoImpl dao;
  private Clock clock;
  private SpotApi spotApi;
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  public OrderMonitoringGateIo(ChartPatternSignalDaoImpl dao) {
    this.dao = dao;
  }
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Scheduled(fixedDelay = 60000)
  public void run() throws ApiException, ParseException {
    List<ChartPatternSignal> activePositions = dao.getAllChartPatternsWithActiveTradePositions();
    for (ChartPatternSignal activePosition: activePositions) {
      String currencyPair = Util.getGateFormattedCurrencyPair(activePosition);
      if (activePosition.entryOrder().status() == ChartPatternSignal.Order.OrderStatusInt.OPEN) {
        Order entryOrderStatus = spotApi.getOrder(Long.toString(activePosition.entryOrder().orderId()),
            currencyPair,
            "cross_margin");
        if (entryOrderStatus.getStatus() == Order.StatusEnum.OPEN) {
          logger.warn(String.format("Entry order status is still in OPEN state for cps %s", activePosition));
          continue;
        } else {
          dao.updateEntryOrderStatus(activePosition, entryOrderStatus.getStatus());
        }
      }

      Ticker ticker = spotApi.listTickers().currencyPair(currencyPair).execute().get(0);
      if (isPriceTargetReached(activePosition, ticker)) {
        logger.info(String.format("Price target met for cps %s", activePosition));
        setExitOrder(activePosition, TradeExitType.PROFIT_TARGET_MET);
      } else if (new Date(clock.millis()).after(activePosition.priceTargetTime())
      || new Date(clock.millis()).equals(activePosition.priceTargetTime())) {
        logger.info(String.format("Target time elapsed for cps %s.", activePosition));
        setExitOrder(activePosition, TradeExitType.TARGET_TIME_PASSED);
      } else if (isStopLossHit(activePosition, ticker)) {
        logger.warn(String.format("Stop loss hit at price %f for cps %s.",
            getPriceForTradeType(ticker, activePosition.tradeType()),
            activePosition));
        setExitOrder(activePosition, TradeExitType.STOP_LOSS);
      }
    }
  }

  private boolean isStopLossHit(ChartPatternSignal activePosition, Ticker ticker) throws ParseException {
    boolean isStopLossPriceTouched =
        activePosition.tradeType() == TradeType.BUY
            && numberFormat.parse(ticker.getLowestAsk()).doubleValue() <= activePosition.stopLossPrice()
        || activePosition.tradeType() == TradeType.SELL
            && numberFormat.parse(ticker.getHighestBid()).doubleValue() >= activePosition.stopLossPrice();
    return isStopLossPriceTouched &&
        CandlestickUtil.getCandlestickStart(new Date(clock.millis()), activePosition.timeFrame())
            .after(activePosition.timeOfSignal());
  }

  private double getPriceForTradeType(Ticker ticker, TradeType tradeType) throws ParseException {
    if (tradeType == TradeType.BUY) {
      return numberFormat.parse(ticker.getLowestAsk()).doubleValue();
    }
    return numberFormat.parse(ticker.getHighestBid()).doubleValue();
  }

  private void setExitOrder(ChartPatternSignal activePosition, TradeExitType tradeExitType) throws ApiException, ParseException {
    Order order = new Order();
    order.setAccount(Order.AccountEnum.CROSS_MARGIN);
    String currencyPair = Util.getGateFormattedCurrencyPair(activePosition);
    order.setCurrencyPair(currencyPair);
    order.setType(Order.TypeEnum.LIMIT);
    order.setSide(activePosition.tradeType() == TradeType.BUY? Order.SideEnum.SELL: Order.SideEnum.BUY);
    order.setAmount(Double.toString(activePosition.entryOrder().executedQty()));
    order.setPrice(Double.toString(activePosition.priceTarget()));
    order.setTimeInForce(Order.TimeInForceEnum.GTC);
    if (activePosition.tradeType() == TradeType.SELL) {
      order.setAutoRepay(true);
    }
    Order exitOrderResp = spotApi.createOrder(order);
    ChartPatternSignal.Order exitOrder = ChartPatternSignal.Order.create(
        Long.parseLong(exitOrderResp.getId()),
        numberFormat.parse(exitOrderResp.getAmount()).doubleValue(),
        numberFormat.parse(exitOrderResp.getPrice()).doubleValue(),
        exitOrderResp.getStatus()
    );
    dao.setExitOrder(activePosition, exitOrder, tradeExitType);
  }

  private boolean isPriceTargetReached(ChartPatternSignal activePosition, Ticker ticker) throws ParseException {
    if (activePosition.tradeType() == TradeType.BUY) {
      return numberFormat.parse(ticker.getLowestAsk()).doubleValue() >= activePosition.priceTarget();
    }
    return numberFormat.parse(ticker.getHighestBid()).doubleValue() <= activePosition.priceTarget();
  }
}
