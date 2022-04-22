package com.binance.bot.trading;

import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.CandlestickUtil;
import com.binance.bot.common.Mailer;
import com.binance.bot.common.Util;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.OutstandingTrades;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.gateiobot.GateIoClientFactory;
import com.gateiobot.db.MACDData;
import com.gateiobot.db.MACDDataDao;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.api.MarginApi;
import io.gate.gateapi.api.SpotApi;
import io.gate.gateapi.models.*;
import io.gate.gateapi.models.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

import static com.binance.bot.tradesignals.TimeFrame.FIFTEEN_MINUTES;

@Component
public class GateIoTradingBot {
  private final MarginApi marginClient;
  private final SpotApi spotClient;
  private final ChartPatternSignalDaoImpl dao;
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final List<CurrencyPair> spotCurrencyPairs;
  private Mailer mailer = new Mailer();
  private final OutstandingTrades outstandingTrades;
  private final CandlestickUtil candlestickUtil = new CandlestickUtil();
  private final MACDDataDao macdDataDao;

  private final List<MarginCurrencyPair> marginCurrencyPairs;

  @Value("${per_trade_amount}")
  public
  double perTradeAmountConfigured;
  @Value("${stop_loss_percent}")
  double stopLossPercent;
  @Value("${stop_limit_percent}")
  double stopLimitPercent;
  @Value("${min_margin_level}")
  double minMarginLevel;

  @Value("${num_outstanding_trades_limit_fifteen_minute_timeframe}")
  int numOutstandingTradesLimitFifteenMinuteTimeFrame;
  @Value("${num_outstanding_trades_limit_hourly_timeframe}")
  int numOutstandingTradesLimitHourlyTimeFrame;
  @Value("${num_outstanding_trades_limit_four_hourly_timeframe}")
  int numOutstandingTradesLimitFourHourlyTimeFrame;
  @Value("${num_outstanding_trades_limit_daily_timeframe}")
  int numOutstandingTradesLimitDailyTimeFrame;
  @Value("${late_time_fifteen_minute_timeframe}")
  int lateTimeFifteenMinuteTimeFrame;
  @Value("${late_time_hourly_timeframe}")
  int lateTimeHourlyTimeFrame;
  @Value("${late_time_four_hourly_timeframe}")
  int lateTimeFourHourlyTimeFrame;
  @Value("${late_time_daily_timeframe}")
  int lateTimeDailyTimeFrame;
  final int[] numOutstandingTradesLimitByTimeFrame = new int[4];
  @Value("${use_altfins_invalidations}")
  boolean useAltfinsInvalidations;
  @Value("${use_macd_for_entry}")
  boolean useMACDForEntry;

  @Autowired
  public GateIoTradingBot(GateIoClientFactory gateIoClientFactory,
                          ChartPatternSignalDaoImpl dao,
                          MACDDataDao macdDataDao,
                          OutstandingTrades outstandingTrades) throws ApiException {
    this.marginClient = gateIoClientFactory.getMarginApi();
    this.spotClient = gateIoClientFactory.getSpotApi();
    this.dao = dao;
    this.macdDataDao = macdDataDao;
    this.outstandingTrades = outstandingTrades;
    marginCurrencyPairs = marginClient.listMarginCurrencyPairs();
    this.spotCurrencyPairs = spotClient.listCurrencyPairs();
  }

  void setMockMailer(Mailer mailer) {
    this.mailer = mailer;
  }

  String getFormattedQuantity(double qty, int stepSizeNumDecimalPlaces) {
    String pattern = "#";
    for (int i = 0; i < stepSizeNumDecimalPlaces; i ++) {
      if (i == 0) {
        pattern += ".";
      }
      pattern += "#";
    }
    DecimalFormat df = new DecimalFormat(pattern);
    df.setRoundingMode(RoundingMode.CEILING);
    return df.format(qty);
  }

  @Value("${fifteen_minute_timeframe}")
  String fifteenMinuteTimeFrameAllowedTradeTypeConfig;
  @Value("${hourly_timeframe}")
  String hourlyTimeFrameAllowedTradeTypeConfig;
  @Value("${four_hourly_timeframe}")
  String fourHourlyTimeFrameAllowedTradeTypeConfig;
  @Value("${daily_timeframe}")
  String dailyTimeFrameAllowedTradeTypeConfig;
  private final TimeFrame[] timeFrames = {FIFTEEN_MINUTES, TimeFrame.HOUR, TimeFrame.FOUR_HOURS, TimeFrame.DAY};
  private final TradeType tradeTypes[] = {TradeType.BUY, TradeType.SELL};

  @ConditionalOnProperty(
      value = "app.scheduling.enable", havingValue = "true", matchIfMissing = true
  )
  @Scheduled(fixedDelay = 60000, initialDelayString = "${timing.initialDelay}")
  public void perform() throws MessagingException {
    numOutstandingTradesLimitByTimeFrame[0] = numOutstandingTradesLimitFifteenMinuteTimeFrame;
    numOutstandingTradesLimitByTimeFrame[1] = numOutstandingTradesLimitHourlyTimeFrame;
    numOutstandingTradesLimitByTimeFrame[2] = numOutstandingTradesLimitFourHourlyTimeFrame;
    numOutstandingTradesLimitByTimeFrame[3] = numOutstandingTradesLimitDailyTimeFrame;
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 2; j++) {
        if (isTradingAllowed(timeFrames[i], tradeTypes[j])) {
          List<ChartPatternSignal> signalsToPlaceTrade = dao.getChartPatternSignalsToPlaceTrade(
              timeFrames[i], tradeTypes[j], useAltfinsInvalidations);
          for (ChartPatternSignal chartPatternSignal: signalsToPlaceTrade) {
            try {
              String coinPair = Util.getGateFormattedCurrencyPair(chartPatternSignal.coinPair());
              if ((!isLate(chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal())
                  || (chartPatternSignal.profitPotentialPercent() >= 0.5
                  && notVeryLate(chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal())))) {
                Optional<MarginCurrencyPair> marginCurrencyPair =
                    marginCurrencyPairs.stream().filter(pair -> pair.getId().equals(coinPair)).findFirst();
                if (marginCurrencyPair.isPresent()) {
                  CurrencyPair currencyPair = spotCurrencyPairs.stream().filter(pair -> pair.getId().equals(coinPair)).findFirst().get();
                  if (chartPatternSignal.tradeType() == TradeType.BUY && currencyPair.getTradeStatus() == CurrencyPair.TradeStatusEnum.BUYABLE
                      || chartPatternSignal.tradeType() == TradeType.SELL && currencyPair.getTradeStatus() == CurrencyPair.TradeStatusEnum.SELLABLE) {
                    int numOutstandingTrades = outstandingTrades.getNumOutstandingTrades(timeFrames[i]);
                    if (numOutstandingTrades < numOutstandingTradesLimitByTimeFrame[i]) {
                      if (!entryDeniedBasedOnMACD(chartPatternSignal)) {
                        placeTrade(chartPatternSignal, numOutstandingTrades, currencyPair);
                      }
                    }
                  }
                }
              }
            } catch (Exception ex) {
              logger.error("Exception.", ex);
              mailer.sendEmail("Exception in GateIoTradingBot", ex.getMessage());
            }
          }
        }
      }
    }
  }

  private boolean entryDeniedBasedOnMACD(ChartPatternSignal chartPatternSignal) {
    if (!useMACDForEntry) return false;
    MACDData lastCandlestickMACD = macdDataDao.getMACDDataForCandlestick(
        Util.getGateFormattedCurrencyPair(chartPatternSignal.coinPair()),
        chartPatternSignal.timeFrame(),
        candlestickUtil.getIthCandlestickTime(
            chartPatternSignal.timeOfSignal(),
            chartPatternSignal.timeFrame(),
            -1)
    );
    return chartPatternSignal.tradeType() == TradeType.BUY && lastCandlestickMACD.macd < 0
        || chartPatternSignal.tradeType() == TradeType.SELL && lastCandlestickMACD.macd > 0;
  }

  private boolean isLate(TimeFrame timeFrame, Date timeOfSignal) {
    long currTime = System.currentTimeMillis();
    long timeLagMins = (currTime - timeOfSignal.getTime()) / 60000;
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return timeLagMins > lateTimeFifteenMinuteTimeFrame;
      case HOUR:
        return timeLagMins > lateTimeHourlyTimeFrame;
      case FOUR_HOURS:
        return timeLagMins > lateTimeFourHourlyTimeFrame;
      default:
        return timeLagMins > lateTimeDailyTimeFrame;
    }
  }

  private boolean notVeryLate(TimeFrame timeFrame, Date timeOfSignal) {
    long currTime = System.currentTimeMillis();
    long timeLagMins = (currTime - timeOfSignal.getTime()) / 60000;
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return false;
      case HOUR:
        return timeLagMins <= 60;
      case FOUR_HOURS:
        return timeLagMins <= 120;
      default:
        return timeLagMins <= 240;
    }
  }

  boolean isTradingAllowed(TimeFrame timeFrame, TradeType tradeType) {
    String configForTimeFrame;
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        configForTimeFrame = fifteenMinuteTimeFrameAllowedTradeTypeConfig;
        break;
      case HOUR:
        configForTimeFrame = hourlyTimeFrameAllowedTradeTypeConfig;
        break;
      case FOUR_HOURS:
        configForTimeFrame = fourHourlyTimeFrameAllowedTradeTypeConfig;
        break;
      default:
        configForTimeFrame = dailyTimeFrameAllowedTradeTypeConfig;
    }
    switch (configForTimeFrame) {
      case "NONE":
        return false;
      case "BOTH":
        return true;
      case "BUY":
        return tradeType == TradeType.BUY;
      case "SELL":
      default:
        return tradeType == TradeType.SELL;
    }
  }

  /** Return Pair of usdt free and value in usdt available to borrow. **/
  Pair<Integer, Integer> getAccountBalance() throws ParseException, BinanceApiException, ApiException {
    CrossMarginAccount account = marginClient.getCrossMarginAccount();
    CrossMarginBalance usdtBalance = account.getBalances().get("USDT");
    int usdtFree = numberFormat.parse(usdtBalance.getAvailable()).intValue();
    double marginLevel = numberFormat.parse(account.getRisk()).doubleValue();
    double totalUSDTVal = numberFormat.parse(account.getTotal()).doubleValue();
    double liabUSDTVal = numberFormat.parse(account.getBorrowed()).doubleValue();
    double moreBorrowableVal;
    if (marginLevel > minMarginLevel) {
      /**
       * totalBtcVal + moreBorrow / (liabVal + morebOrrow) = minMarginLevel
       * totalBtcVal + moreBorrow = minMarginLevel * liabVal + minMarginLevel * moreBorrow
       * moreBorrow = (totalBtcVal - minMarginLevel * liabVal) / (minMarginLevel -1)
       */
      moreBorrowableVal = (totalUSDTVal - minMarginLevel * liabUSDTVal) / (minMarginLevel -1);
    } else {
      moreBorrowableVal = 0;
    }
    logger.info(String.format("Wallet balance: USDT free=%d, Margin level=%f, Net value USDT=%d, Liability " +
            "value USDT=%d, Total value USDT=%d", usdtFree, marginLevel,
        (int) (totalUSDTVal - liabUSDTVal),
        (int) (liabUSDTVal),
        (int) (totalUSDTVal)));
    return Pair.of(usdtFree, (int) (moreBorrowableVal));
  }

  public void placeTrade(ChartPatternSignal chartPatternSignal, int numOutstandingTrades, CurrencyPair spotCurrencyPair) throws ParseException, BinanceApiException, MessagingException, ApiException {
    Pair<Integer, Integer> accountBalance = getAccountBalance();
    String currencyPair = Util.getGateFormattedCurrencyPair(chartPatternSignal.coinPair());
    Ticker ticker = spotClient.listTickers().currencyPair(currencyPair).execute().get(0);
    double entryPrice = getEntryPrice(chartPatternSignal.tradeType(), ticker);
    double minNotionalValueInUSDT = numberFormat.parse(spotCurrencyPair.getMinQuoteAmount()).doubleValue();
    double minBaseAssetValueInUSDT = numberFormat.parse(spotCurrencyPair.getMinBaseAmount()).doubleValue() * entryPrice;
    double minOrderValueInUSDT = Math.max(minNotionalValueInUSDT, minBaseAssetValueInUSDT);
    double minNotionalAdjustedForStopLoss =
        getMinNotionalAdjustedForStopLoss(minOrderValueInUSDT, stopLimitPercent);
    double tradeValueInUSDTToDo = Math.max(minNotionalAdjustedForStopLoss, perTradeAmountConfigured);
    // Determine trade feasibility and borrow required quantity.
    switch (chartPatternSignal.tradeType()) {
      case BUY:
        if (tradeValueInUSDTToDo <= accountBalance.getFirst()) {
          logger.info(String.format("Using %f from available USDT balance for the chart pattern signal %s.",
              tradeValueInUSDTToDo, chartPatternSignal));
        } else if (tradeValueInUSDTToDo - accountBalance.getFirst() <= accountBalance.getSecond()) {
          Integer usdtToBorrow = (int) Math.ceil(tradeValueInUSDTToDo - accountBalance.getFirst());
          logger.info("Borrowing %d USDT.", usdtToBorrow);
          CrossMarginLoan usdtLoan = new CrossMarginLoan();
          usdtLoan.setCurrency("USDT");
          usdtLoan.setAmount(Integer.toString(usdtToBorrow));
          marginClient.createCrossMarginLoan(usdtLoan);
        } else {
          String msg = String.format("Insufficient amount for trade for chart pattern signal %s.", chartPatternSignal);
          logger.warn(msg);
          mailer.sendEmail("Insufficient funds.", msg);
          return;
        }
        break;
      case SELL:
        if (tradeValueInUSDTToDo <= accountBalance.getSecond()) {
          String numCoinsToBorrow = getFormattedQuantity(tradeValueInUSDTToDo / entryPrice,
              spotCurrencyPair.getAmountPrecision());
          String baseAsset = Util.getBaseAsset(chartPatternSignal.coinPair());
          logger.info(String.format("Borrowing %s coins of %s.", numCoinsToBorrow, baseAsset));
          CrossMarginLoan loan = new CrossMarginLoan();
          loan.setCurrency(baseAsset);
          loan.setAmount(numCoinsToBorrow);
          marginClient.createCrossMarginLoan(loan);
        } else {
          String msg = String.format("Insufficient amount for trade for chart pattern signal %s.", chartPatternSignal);
          logger.warn(msg);
          mailer.sendEmail("Insufficient funds.", msg);
          return;
        }
      default:
    }
    String roundedQuantity = getFormattedQuantity(tradeValueInUSDTToDo / entryPrice,
        spotCurrencyPair.getAmountPrecision());

    Order.SideEnum orderSide;
    int sign;
    Order.SideEnum stopLossOrderSide;
    switch (chartPatternSignal.tradeType()) {
      case BUY:
        orderSide = Order.SideEnum.BUY;
        stopLossOrderSide = Order.SideEnum.SELL;
        sign = 1;
        break;
      default:
        orderSide = Order.SideEnum.SELL;
        stopLossOrderSide = Order.SideEnum.BUY;
        sign = -1;
    }
    Order order = new Order();
    order.setAccount(Order.AccountEnum.CROSS_MARGIN);
    order.setCurrencyPair(currencyPair);
    order.setType(Order.TypeEnum.LIMIT);
    order.setSide(orderSide);
    order.setAmount(roundedQuantity);
    order.setPrice(Double.toString(entryPrice));
    order.setTimeInForce(Order.TimeInForceEnum.GTC);
    order.setAutoBorrow(true);
    Order orderResponse = spotClient.createOrder(order);
    double stopLossPrice = macdDataDao.getMACDDataForCandlestick(
        currencyPair, chartPatternSignal.timeFrame(),
        candlestickUtil.getIthCandlestickTime(
        chartPatternSignal.timeOfSignal(), chartPatternSignal.timeFrame(), -2))
        .candleClosingPrice;
    dao.updateStopLossPrice(chartPatternSignal, stopLossPrice);
    logger.info(String.format("Placed market %s order %s with stop loss %f and status %s for chart pattern signal\n%s.",
        orderSide.name(),
        orderResponse.toString(), stopLossPrice, orderResponse.getStatus().name(), chartPatternSignal));
    outstandingTrades.incrementNumOutstandingTrades(chartPatternSignal.timeFrame());
    // TODO: delayed market order executions.
    dao.setEntryOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(
            Integer.parseInt(orderResponse.getId()),
            numberFormat.parse(orderResponse.getAmount()).doubleValue(),
            /// TODO: Find the actual price from the associated Trade
            entryPrice, // because the order response returns just 0 as the price for market order fill.
            orderResponse.getStatus()));
    dao.writeAccountBalanceToDB();
  }

  private double getEntryPrice(TradeType tradeType, Ticker ticker) throws ParseException {
    if (tradeType == TradeType.BUY) {
      return numberFormat.parse(ticker.getLowestAsk()).doubleValue();
    }
    return numberFormat.parse(ticker.getHighestBid()).doubleValue();
  }

  private double getMinNotionalAdjustedForStopLoss(Double minNotional, double stopLossPercent) {
    // Adding extra 25 cents to account for quick price drops when placing order that would reduce the amount being
    // ordered below min notional.
    return minNotional * 100 / (100 - stopLossPercent) + 0.25;
  }
}