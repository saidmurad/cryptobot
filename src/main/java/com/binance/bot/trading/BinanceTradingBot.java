package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.*;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.Mailer;
import com.binance.bot.common.Util;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.binance.bot.tradesignals.TimeFrame.FIFTEEN_MINUTES;

@Component
public class BinanceTradingBot {
    private final BinanceApiRestClient binanceApiRestClient;
    private final BinanceApiMarginRestClient binanceApiMarginRestClient;
    private final ChartPatternSignalDaoImpl dao;
    private final SupportedSymbolsInfo supportedSymbolsInfo;
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BookTickerPrices bookTickerPrices;
    private final AccountBalanceDao accountBalanceDao;
    private final Mailer mailer = new Mailer();
    @Value("${per_trade_amount}")
    public
    double perTradeAmountConfigured;
    @Value("${stop_loss_percent}")
    double stopLossPercent;
    @Value("${stop_limit_percent}")
    double stopLimitPercent;
    @Value("${min_margin_level}")
    double minMarginLevel;

    @Autowired
    public BinanceTradingBot(BinanceApiClientFactory binanceApiRestClientFactory,
                             SupportedSymbolsInfo supportedSymbolsInfo,
                             ChartPatternSignalDaoImpl dao,
                             BookTickerPrices bookTickerPrices,
                             AccountBalanceDao accountBalanceDao) {
        this.binanceApiRestClient = binanceApiRestClientFactory.newRestClient();
        this.binanceApiMarginRestClient = binanceApiRestClientFactory.newMarginRestClient();
        this.supportedSymbolsInfo = supportedSymbolsInfo;
        this.dao = dao;
        this.bookTickerPrices = bookTickerPrices;
        this.accountBalanceDao = accountBalanceDao;
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
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 2; j++) {
                if (isTradingAllowed(timeFrames[i], tradeTypes[j])) {
                    List<ChartPatternSignal> signalsToPlaceTrade = dao.getChartPatternSignalsToPlaceTrade(timeFrames[i], tradeTypes[j]);
                    for (ChartPatternSignal chartPatternSignal: signalsToPlaceTrade) {
                        try {
                            if ((!isLate(chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal())
                                || (chartPatternSignal.profitPotentialPercent() >= 0.5
                                && notVeryLate(chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal())))
                                && supportedSymbolsInfo.getTradingActiveSymbols().containsKey(chartPatternSignal.coinPair())) {
                                placeTrade(chartPatternSignal);
                            }
                        } catch (Exception ex) {
                            logger.error("Exception.", ex);
                            mailer.sendEmail("Exception in BinanceTradingBot", ex.getMessage());
                        }
                    }
                }
            }
        }
    }

    private boolean isLate(TimeFrame timeFrame, Date timeOfSignal) {
        long currTime = System.currentTimeMillis();
        long timeLagMins = (currTime - timeOfSignal.getTime()) / 60000;
        switch (timeFrame) {
            case FIFTEEN_MINUTES:
                return timeLagMins > 15;
            case HOUR:
                return timeLagMins > 30;
            case FOUR_HOURS:
                return timeLagMins > 30;
            default:
                return timeLagMins > 120;
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
    Pair<Integer, Integer> getAccountBalance() throws ParseException, BinanceApiException {
        MarginAccount account = binanceApiMarginRestClient.getAccount();
        int usdtFree = numberFormat.parse(account.getAssetBalance("USDT").getFree()).intValue();
        double marginLevel = numberFormat.parse(account.getMarginLevel()).doubleValue();
        double netBtcVal = numberFormat.parse(account.getTotalNetAssetOfBtc()).doubleValue();
        double totalBtcVal = numberFormat.parse(account.getTotalAssetOfBtc()).doubleValue();
        double liabBtcVal = numberFormat.parse(account.getTotalLiabilityOfBtc()).doubleValue();
        double moreBorrowableVal;
        BookTickerPrices.BookTicker btcPrice = bookTickerPrices.getBookTicker("BTCUSDT");
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

    public void placeTrade(ChartPatternSignal chartPatternSignal) throws ParseException, BinanceApiException, MessagingException {
        Pair<Integer, Integer> accountBalance = getAccountBalance();
        Pair<Double, Integer> minNotionalAndLotSize = supportedSymbolsInfo.getMinNotionalAndLotSize(
            chartPatternSignal.coinPair());
        if (minNotionalAndLotSize == null) {
            logger.error(String.format("Unexpectedly supportedSymbolsInfo.getMinNotionalAndLotSize returned null for %s", chartPatternSignal.coinPair()));
            return;
        }
        int stepSizeNumDecimalPlaces = minNotionalAndLotSize.getSecond();
        double minNotionalAdjustedForStopLoss =
            getMinNotionalAdjustedForStopLoss(minNotionalAndLotSize.getFirst(), stopLimitPercent);
        double tradeValueInUSDTToDo = Math.max(minNotionalAdjustedForStopLoss, perTradeAmountConfigured);
        double entryPrice = numberFormat.parse(binanceApiRestClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
        // Determine trade feasibility and borrow required quantity.
        switch (chartPatternSignal.tradeType()) {
            case BUY:
                if (tradeValueInUSDTToDo <= accountBalance.getFirst()) {
                    logger.info(String.format("Using %d from available USDT balance for the chart pattern signal %s.",
                        (int) tradeValueInUSDTToDo, chartPatternSignal));
                } else if (tradeValueInUSDTToDo - accountBalance.getFirst() <= accountBalance.getSecond()) {
                    Integer usdtToBorrow = (int) Math.ceil(tradeValueInUSDTToDo - accountBalance.getFirst());
                    logger.info("Borrowing %d USDT.", usdtToBorrow);
                    binanceApiMarginRestClient.borrow("USDT", usdtToBorrow.toString());
                } else {
                    String msg = String.format("Insufficient amount for trade for chart pattern signal %s.", chartPatternSignal);
                    logger.warn(msg);
                    mailer.sendEmail("Insufficient funds.", msg);
                    return;
                }
                break;
            case SELL:
                if (tradeValueInUSDTToDo <= accountBalance.getSecond()) {
                    Integer numCoinsToBorrow = (int) (tradeValueInUSDTToDo / entryPrice);
                    String baseAsset = Util.getBaseAsset(chartPatternSignal.coinPair());
                    logger.info("Borrowing %d coins of %s.", numCoinsToBorrow, baseAsset);
                    binanceApiMarginRestClient.borrow(baseAsset, numCoinsToBorrow.toString());
                } else {
                    String msg = String.format("Insufficient amount for trade for chart pattern signal %s.", chartPatternSignal);
                    logger.warn(msg);
                    mailer.sendEmail("Insufficient funds.", msg);
                    return;
                }
            default:
        }
        String roundedQuantity = getFormattedQuantity(tradeValueInUSDTToDo / entryPrice, stepSizeNumDecimalPlaces);

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
            roundedQuantity);
        MarginNewOrderResponse marketOrderResp = binanceApiMarginRestClient.newOrder(marketOrder);
        logger.info(String.format("Placed market %s order %s with status %s for chart pattern signal\n%s.", orderSide.name(),
            marketOrderResp.toString(), marketOrderResp.getStatus().name(), chartPatternSignal));
        // TODO: delayed market order executions.
        dao.setEntryOrder(chartPatternSignal,
            ChartPatternSignal.Order.create(
                marketOrderResp.getOrderId(),
                numberFormat.parse(marketOrderResp.getExecutedQty()).doubleValue(),
                /// TODO: Find the actual price from the associated Trade
                entryPrice, // because the order response returns just 0 as the price for market order fill.
                marketOrderResp.getStatus()));

        String stopPrice = String.format("%.2f", entryPrice * (100 - sign * stopLossPercent) / 100);
        String stopLimitPrice = String.format("%.2f", entryPrice * (100 - sign * stopLimitPercent) / 100);
        MarginNewOrder stopLossOrder = new MarginNewOrder(chartPatternSignal.coinPair(),
            stopLossOrderSide,
            OrderType.STOP_LOSS_LIMIT,
            TimeInForce.GTC,
            marketOrderResp.getExecutedQty(),
            stopLimitPrice);
        stopLossOrder.stopPrice(stopPrice);
        MarginNewOrderResponse stopLossOrderResp = binanceApiMarginRestClient.newOrder(stopLossOrder);
        logger.info(String.format("Placed %s Stop loss order %s with status %s for chart pattern signal\n%s.",
            stopLossOrderSide.name(), stopLossOrderResp.toString(), stopLossOrderResp.getStatus().name(), chartPatternSignal));

        dao.setExitStopLimitOrder(chartPatternSignal,
            ChartPatternSignal.Order.create(
                stopLossOrderResp.getOrderId(),
                0,0,
                stopLossOrderResp.getStatus()));
        accountBalanceDao.writeAccountBalanceToDB();
    }

    private double getMinNotionalAdjustedForStopLoss(Double minNotional, double stopLossPercent) {
        // Adding extra 25 cents to account for quick price drops when placing order that would reduce the amount being
        // ordered below min notional.
        return minNotional * 100 / (100 - stopLossPercent) + 0.25;
    }
}