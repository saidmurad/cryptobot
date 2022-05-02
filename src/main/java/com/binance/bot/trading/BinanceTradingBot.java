package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.CandlestickUtil;
import com.binance.bot.common.Mailer;
import com.binance.bot.common.Util;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.OutstandingTrades;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.gateiobot.db.MACDData;
import com.gateiobot.db.MACDDataDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.mail.MessagingException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

import static com.binance.bot.tradesignals.TimeFrame.FIFTEEN_MINUTES;

@Component
public class BinanceTradingBot {
  private static final String MISSING_MACD_DATA = "Missing MACD Data";
  private final BinanceApiRestClient binanceApiRestClient;
    private final BinanceApiMarginRestClient binanceApiMarginRestClient;
    private final ChartPatternSignalDaoImpl dao;
    private final SupportedSymbolsInfo supportedSymbolsInfo;
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BookTickerPrices bookTickerPrices;
    private final RepayBorrowedOnMargin repayBorrowedOnMargin;
    public Mailer mailer = new Mailer();
    private final OutstandingTrades outstandingTrades;
    private final MACDDataDao macdDataDao;

    @Value("${per_trade_amount}")
    public
    double perTradeAmountConfigured;
    @Value("${use_breakout_candlestick_for_stop_loss}")
    public boolean useBreakoutCandlestickForStopLoss;
    @Value("${stop_loss_percent}")
    public double stopLossPercent;
    @Value("${stop_limit_percent}")
    public double stopLimitPercent;
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
    // Enter trade if MACD value has the same sign (+/-) as the trade type, so far this was the only entry strategy
    // seemingly profitable.
    @Value("${use_macd_for_entry}")
    boolean entry_using_macd;
    final int[] numOutstandingTradesLimitByTimeFrame = new int[4];
    @Value("${use_sourcesignals_invalidations}")
    boolean useAltfinsInvalidations;
    private MarginAccount account;
    private BookTickerPrices.BookTicker btcPrice;

    @Autowired
    public BinanceTradingBot(BinanceApiClientFactory binanceApiRestClientFactory,
                             SupportedSymbolsInfo supportedSymbolsInfo,
                             ChartPatternSignalDaoImpl dao,
                             BookTickerPrices bookTickerPrices,
                             OutstandingTrades outstandingTrades,
                             RepayBorrowedOnMargin repayBorrowedOnMargin,
                             MACDDataDao macdDataDao) {
        this.binanceApiRestClient = binanceApiRestClientFactory.newRestClient();
        this.binanceApiMarginRestClient = binanceApiRestClientFactory.newMarginRestClient();
        this.supportedSymbolsInfo = supportedSymbolsInfo;
        this.dao = dao;
        this.bookTickerPrices = bookTickerPrices;
        this.outstandingTrades = outstandingTrades;
        this.repayBorrowedOnMargin = repayBorrowedOnMargin;
        this.macdDataDao = macdDataDao;
    }

    private Pair<Double, Double> getStopLossPercents(ChartPatternSignal chartPatternSignal) {
        if (useBreakoutCandlestickForStopLoss) {

        }
        return Pair.of(stopLossPercent, stopLimitPercent);
    }

    void setMockMailer(Mailer mailer) {
        this.mailer = mailer;
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
                          // TODO: REcord the reason for rejection in cps in DB.
                            if (!isPriceTargetAlreadyReached(chartPatternSignal)
                                && !permanentErrorCases.contains(chartPatternSignal.coinPair())
                          && (!isLate(chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal())
                                || (chartPatternSignal.profitPotentialPercent() >= 0.5
                                && notVeryLate(chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal())))
                                && isActiveSymbolAndMarginAllowed(chartPatternSignal.coinPair())) {
                                int numOutstandingTrades = outstandingTrades.getNumOutstandingTrades(timeFrames[i]);
                                if (numOutstandingTrades < numOutstandingTradesLimitByTimeFrame[i]
                                && (!entry_using_macd || canEnterBasedOnMACD(chartPatternSignal))) {
                                  placeTrade(chartPatternSignal, numOutstandingTrades);
                                }
                            }
                        } catch (Exception ex) {
                            logger.error("Exception.", ex);
                            mailer.sendEmail("Exception in BinanceTradingBot", ex.getMessage() != null? ex.getMessage() : ex.getClass().getName());
                        }
                    }
                }
            }
        }
    }

    //TODO: Mark the cps as considered and dropped so it doesn't ever enter the trade for it.
    private boolean isPriceTargetAlreadyReached(ChartPatternSignal chartPatternSignal) throws BinanceApiException, ParseException {
      BookTickerPrices.BookTicker ticker = bookTickerPrices.getBookTicker(chartPatternSignal.coinPair());
      switch (chartPatternSignal.tradeType()) {
        case BUY:
          return ticker.bestAsk() >= chartPatternSignal.priceTarget();
        default:
          return ticker.bestBid() <= chartPatternSignal.priceTarget();
      }
    }

    private double getBreakoutPointBasedStopLossPrice(ChartPatternSignal chartPatternSignal) {
        Date beforeBreakoutCandlestickTime = CandlestickUtil.getIthCandlestickTime(chartPatternSignal.timeOfSignal(), chartPatternSignal.timeFrame(), -2);
        MACDData beforeBreakoutCandlestickMACDData = macdDataDao.getMACDDataForCandlestick(chartPatternSignal.coinPair(), chartPatternSignal.timeFrame(), beforeBreakoutCandlestickTime);
        return beforeBreakoutCandlestickMACDData.candleClosingPrice;
    }

    private final Set<Pair<ChartPatternSignal, String>> emailsAlreadySent = new HashSet<>();
    private final Set<String> permanentErrorCases = new HashSet<>();
    private boolean canEnterBasedOnMACD(ChartPatternSignal chartPatternSignal) throws ParseException, MessagingException {
        MACDData lastMACD = macdDataDao.getLastMACDData(Util.getGateFormattedCurrencyPair(chartPatternSignal.coinPair()), chartPatternSignal.timeFrame());
        if (lastMACD == null) {
            if (!emailsAlreadySent.contains(Pair.of(chartPatternSignal, MISSING_MACD_DATA))) {
              logger.error("Got null last MACD data for cps " + chartPatternSignal);
              mailer.sendEmail("Missing MACD data", "Got null last MACD data for cps " + chartPatternSignal);
              emailsAlreadySent.add(Pair.of(chartPatternSignal, MISSING_MACD_DATA));
            }
            return false;
        }
        return chartPatternSignal.tradeType() == TradeType.BUY && lastMACD.macd > 0
            || chartPatternSignal.tradeType() == TradeType.SELL && lastMACD.macd < 0;
    }

    private boolean isActiveSymbolAndMarginAllowed(String coinPair) throws BinanceApiException {
        Boolean isMarginAllowed = supportedSymbolsInfo.getTradingActiveSymbols().get(coinPair);
        return isMarginAllowed != null && isMarginAllowed;
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
    Pair<Integer, Integer> getAccountBalanceFreeUSDTAndMoreBorrowableUSDTValue() throws ParseException, BinanceApiException, InterruptedException {
        this.account = binanceApiMarginRestClient.getAccount();
        int usdtFree = numberFormat.parse(account.getAssetBalance("USDT").getFree()).intValue();
        double marginLevel = numberFormat.parse(account.getMarginLevel()).doubleValue();
        double netBtcVal = numberFormat.parse(account.getTotalNetAssetOfBtc()).doubleValue();
        double totalBtcVal = numberFormat.parse(account.getTotalAssetOfBtc()).doubleValue();
        double liabBtcVal = numberFormat.parse(account.getTotalLiabilityOfBtc()).doubleValue();
        double moreBorrowableVal;
        btcPrice = bookTickerPrices.getBookTicker("BTCUSDT");
        if (marginLevel > minMarginLevel) {
            /**
             * totalBtcVal + moreBorrow / (liabVal + morebOrrow) = minMarginLevel
             * totalBtcVal + moreBorrow = minMarginLevel * liabVal + minMarginLevel * moreBorrow
             * moreBorrow = (totalBtcVal - minMarginLevel * liabVal) / (minMarginLevel -1)
             */
            moreBorrowableVal = (totalBtcVal - minMarginLevel * liabBtcVal) / (minMarginLevel -1);
        } else {
            moreBorrowableVal = 0;
        }
        logger.info(String.format("Wallet balance: USDT free=%d, Margin level=%f, Net value USDT=%d, Liability " +
            "value USDT=%d, Total value USDT=%d", usdtFree, marginLevel,
            (int) (netBtcVal * btcPrice.bestAsk()),
            (int) (liabBtcVal * btcPrice.bestAsk()),
            (int) (totalBtcVal * btcPrice.bestAsk())));
        return Pair.of(usdtFree, (int) (moreBorrowableVal * btcPrice.bestAsk()));
    }

    public void placeTrade(ChartPatternSignal chartPatternSignal, int numOutstandingTrades) throws ParseException, BinanceApiException, MessagingException, InterruptedException {
        Pair<Integer, Integer> accountBalance = getAccountBalanceFreeUSDTAndMoreBorrowableUSDTValue();
        Pair<Double, Integer> minNotionalAndLotSize = supportedSymbolsInfo.getMinNotionalAndLotSize(
            chartPatternSignal.coinPair());
        if (minNotionalAndLotSize == null) {
          String errMsg = String.format("Unexpectedly supportedSymbolsInfo.getMinNotionalAndLotSize returned null for %s", chartPatternSignal.coinPair());
            logger.error(errMsg);
            mailer.sendEmail("Missing minNotionalAndLotSize", errMsg);
            return;
        }
        int stepSizeNumDecimalPlaces = minNotionalAndLotSize.getSecond();
        double minNotionalAdjustedForStopLoss =
            getMinNotionalAdjustedForStopLoss(minNotionalAndLotSize.getFirst(), stopLimitPercent);
        double tradeValueInUSDTToDo = Math.max(minNotionalAdjustedForStopLoss, perTradeAmountConfigured);
      BookTickerPrices.BookTicker bookTicker = bookTickerPrices.getBookTicker(chartPatternSignal.coinPair());
        double entryPrice = chartPatternSignal.tradeType() == TradeType.BUY? bookTicker.bestAsk() : bookTicker.bestBid();
        // Determine trade feasibility and borrow required quantity.
        switch (chartPatternSignal.tradeType()) {
            case BUY:
                if (tradeValueInUSDTToDo <= accountBalance.getFirst()) {
                    logger.info(String.format("Using %f from available USDT balance for the chart pattern signal %s.",
                        tradeValueInUSDTToDo, chartPatternSignal));
                } else if (tradeValueInUSDTToDo - accountBalance.getFirst() <= accountBalance.getSecond()) {
                    Integer usdtToBorrow = (int) Math.ceil(tradeValueInUSDTToDo - accountBalance.getFirst());
                    logger.info(String.format("Borrowing %d USDT.", usdtToBorrow));
                    binanceApiMarginRestClient.borrow("USDT", usdtToBorrow.toString());
                } else {
                    String msg = String.format("Insufficient amount for trade for chart pattern signal %s.", chartPatternSignal);
                    logger.warn(msg);
                    mailer.sendEmail("Insufficient funds.", msg);
                    return;
                }
                break;
            case SELL:
              try {
                String numCoinsToBorrow = Util.getRoundedUpQuantity(tradeValueInUSDTToDo / entryPrice, stepSizeNumDecimalPlaces);
                String baseAsset = Util.getBaseAsset(chartPatternSignal.coinPair());
                if (tradeValueInUSDTToDo <= accountBalance.getSecond()) {
                  logger.info(String.format("Borrowing %s coins of %s.", numCoinsToBorrow, baseAsset));
                  binanceApiMarginRestClient.borrow(baseAsset, numCoinsToBorrow);
                } else {
                  int usdtToBeRepaidToAllowBorrowToSell = usdtToBeRepaidToAllowBorrowToSell(tradeValueInUSDTToDo);
                  if (usdtToBeRepaidToAllowBorrowToSell > 0) {
                    logger.info(String.format("Repaying %d USDT to make room for borrowing %s coins of '%s'.",
                        usdtToBeRepaidToAllowBorrowToSell, numCoinsToBorrow, Util.getBaseAsset(chartPatternSignal.coinPair())));
                    repayBorrowedOnMargin.repay("USDT", usdtToBeRepaidToAllowBorrowToSell);
                    logger.info(String.format("Borrowing %s coins of %s.", numCoinsToBorrow, baseAsset));
                    binanceApiMarginRestClient.borrow(baseAsset, numCoinsToBorrow);
                  } else {
                    String msg = String.format("Insufficient amount for trade for chart pattern signal %s.", chartPatternSignal);
                    logger.warn(msg);
                    mailer.sendEmail("Insufficient funds.", msg);
                    return;
                  }
                }
              } catch (BinanceApiException ex) {
                // TODO: Unit test.
                if (ex.getError().getCode() == -3045) {
                  String msg = String.format("Got BinanceApiError for unavailable to borrow right now. Ignoring this cps %s.", chartPatternSignal);
                  logger.warn(msg);
                  mailer.sendEmail("Asset Unavailable to borrow", msg);
                  permanentErrorCases.add(chartPatternSignal.coinPair());
                  return;
                } else {
                  throw ex;
                }
              }
            default:
        }
        String roundedQuantity = Util.getRoundedUpQuantity(tradeValueInUSDTToDo / entryPrice, stepSizeNumDecimalPlaces);

        OrderSide orderSide;
        int sign;
        OrderSide stopLossOrderSide;
        switch (chartPatternSignal.tradeType()) {
            case BUY:
                orderSide = OrderSide.BUY;
                stopLossOrderSide = OrderSide.SELL;
                sign = 1;
                break;
            default:
                orderSide = OrderSide.SELL;
                stopLossOrderSide = OrderSide.BUY;
                sign = -1;
        }
        MarginNewOrder marketOrder = new MarginNewOrder(chartPatternSignal.coinPair(), orderSide,
            OrderType.MARKET, /* timeInForce= */ null,
            roundedQuantity).newOrderRespType(NewOrderResponseType.FULL);
        MarginNewOrderResponse marketOrderResp = binanceApiMarginRestClient.newOrder(marketOrder);
        String logmsg = String.format("Placed market %s order %s with status %s for chart pattern signal\n%s.", orderSide.name(),
            marketOrderResp.toString(), marketOrderResp.getStatus().name(), chartPatternSignal);
        logger.info(logmsg);
        mailer.sendEmail("Placed trade", logmsg);
        outstandingTrades.incrementNumOutstandingTrades(chartPatternSignal.timeFrame());
        // TODO: delayed market order executions.
        TradeFillData tradeFillData = new TradeFillData(marketOrderResp, chartPatternSignal.tradeType());
        dao.setEntryOrder(chartPatternSignal,
            ChartPatternSignal.Order.create(
                marketOrderResp.getOrderId(),
                tradeFillData.getQuantity(),
                tradeFillData.getAvgPrice(),
                marketOrderResp.getStatus()));

        Integer tickSizeNumDecimals = supportedSymbolsInfo.getMinPriceAndTickSize(chartPatternSignal.coinPair()).getSecond();
        if (tickSizeNumDecimals != null) {
          //stopLossPercents =
          String stopPrice, stopLimitPrice;
          if (stopLossPercent == 0.0) { // integ test
            stopPrice = Double.toString(entryPrice - sign * 0.02);
            stopLimitPrice = Double.toString(entryPrice - sign * 0.5);
          } else {
            stopPrice = Util.getRoundedUpQuantity(entryPrice * (100 - sign * stopLossPercent) / 100, tickSizeNumDecimals);
            stopLimitPrice = Util.getRoundedUpQuantity(entryPrice * (100 - sign * stopLimitPercent) / 100, tickSizeNumDecimals);
          }
          String qtyForStopLossExit;
          if (chartPatternSignal.tradeType() == TradeType.BUY) {
            // For buy trades, the qty can be sold with commisison calculated only on the USDT proceeds from the sale.
            // qtyForStopLossExit for a BUY fill for 0.0039 will be 0.1% less than that.
            // Truncating, because otherwise we get an error from the exchange for insufficient quantity. Leave behind
            // the small position instead.
            qtyForStopLossExit = Util.getTruncatedQuantity(tradeFillData.getQuantity(), stepSizeNumDecimalPlaces);
          } else {
            // For sell orders, while buying back the commission 0.1% is deducted on the base asset.
            double qtyAdjustedForCommission = tradeFillData.getQuantity() / 0.999;
            qtyForStopLossExit = Util.getRoundedUpQuantity(qtyAdjustedForCommission, stepSizeNumDecimalPlaces);
          }
          MarginNewOrder stopLossOrder = new MarginNewOrder(chartPatternSignal.coinPair(),
              stopLossOrderSide,
              OrderType.STOP_LOSS_LIMIT,
              TimeInForce.GTC,
              qtyForStopLossExit,
              stopLimitPrice);
          stopLossOrder.stopPrice(stopPrice);
          logger.info(String.format("Placing stop loss order %s with stop price %s and stop limit price %s for cps %s.",
              stopLossOrder, stopPrice, stopLimitPrice, chartPatternSignal));
          MarginNewOrderResponse stopLossOrderResp = binanceApiMarginRestClient.newOrder(stopLossOrder);
          logger.info(String.format("Placed %s Stop loss order %s with status %s for chart pattern signal\n%s.",
              stopLossOrderSide.name(), stopLossOrderResp.toString(), stopLossOrderResp.getStatus().name(), chartPatternSignal));

          dao.setExitStopLimitOrder(chartPatternSignal,
              ChartPatternSignal.Order.create(
                  stopLossOrderResp.getOrderId(),
                  0, 0,
                  stopLossOrderResp.getStatus()));
        } else {
          String errMsg = String.format("Weirdity occured for coin pair %s, got null minPriceAndTickSize for the symbol. Did not place stop loss order because of this. SupportedSymbolsInfo size was %d.", chartPatternSignal.coinPair(), supportedSymbolsInfo.getMinPricAndTickSizeMapSize());
          logger.error(errMsg);
          mailer.sendEmail("Missing minPriceAndTickSize unexpectedly.", errMsg);
        }
        dao.writeAccountBalanceToDB();
    }

  private int usdtToBeRepaidToAllowBorrowToSell(double tradeValueInUSDTToDo) throws ParseException {
        MarginAssetBalance usdtBalance = account.getAssetBalance("USDT");
      int usdtFree = numberFormat.parse(usdtBalance.getFree()).intValue();
      int usdtBorrowed = numberFormat.parse(usdtBalance.getBorrowed()).intValue();
      int usdtToRepay;
      if (usdtFree < usdtBorrowed) {
          usdtToRepay = usdtFree;
      } else {
          usdtToRepay = usdtBorrowed;
      }
      double moreToBorrowForTrade = tradeValueInUSDTToDo  - usdtToRepay;
        double newLiabBtc = moreToBorrowForTrade / btcPrice.bestAsk();
        double totalBtcVal = numberFormat.parse(account.getTotalAssetOfBtc()).doubleValue() + newLiabBtc;
      double liabBtcVal = numberFormat.parse(account.getTotalLiabilityOfBtc()).doubleValue() + newLiabBtc;
      if (totalBtcVal / liabBtcVal >= 1.5) {
          return usdtToRepay;
      }
      return -1;
    }

    private double getMinNotionalAdjustedForStopLoss(Double minNotional, double stopLossPercent) {
        // Adding extra 25 cents to account for quick price drops when placing order that would reduce the amount being
        // ordered below min notional.
        return minNotional * 100 / (100 - stopLossPercent) + 0.25;
    }
}