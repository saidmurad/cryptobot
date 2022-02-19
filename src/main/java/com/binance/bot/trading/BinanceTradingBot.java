package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.*;
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

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static com.binance.bot.common.Constants.USDT;
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

    @Value("${per_trade_amount}")
    double perTradeAmount;
    @Value("${stop_loss_percent}")
    double stopLossPercent;
    @Value("${stop_limit_percent}")
    double stopLimitPercent;

    @Autowired
    public BinanceTradingBot(BinanceApiClientFactory binanceApiRestClientFactory,
                             SupportedSymbolsInfo supportedSymbolsInfo,
                             ChartPatternSignalDaoImpl dao,
                             BookTickerPrices bookTickerPrices) {
        this.binanceApiRestClient = binanceApiRestClientFactory.newRestClient();
        this.binanceApiMarginRestClient = binanceApiRestClientFactory.newMarginRestClient();
        this.supportedSymbolsInfo = supportedSymbolsInfo;
        this.dao = dao;
        this.bookTickerPrices = bookTickerPrices;
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
    public void perform() throws ParseException {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 2; j++) {
                if (isTradingAllowed(timeFrames[i], tradeTypes[j])) {
                    List<ChartPatternSignal> signalsToPlaceTrade = dao.getChartPatternSignalsToPlaceTrade(timeFrames[i], tradeTypes[j]);
                    for (ChartPatternSignal chartPatternSignal: signalsToPlaceTrade) {
                        if ((!isLate(chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal())
                            || (chartPatternSignal.profitPotentialPercent() >= 0.5
                        && notVeryLate(chartPatternSignal.timeFrame(), chartPatternSignal.timeOfSignal())))
                        && supportedSymbolsInfo.getTradingActiveSymbols().containsKey(chartPatternSignal.coinPair())) {
                            placeTrade(chartPatternSignal);
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

    double usdtValueAvailabeToBorrow() throws ParseException {
        MarginAccount account = binanceApiMarginRestClient.getAccount();
        double marginLevel = numberFormat.parse(account.getMarginLevel()).doubleValue();
        logger.info("Margin level=" + marginLevel);
        if (marginLevel <= 2) {
            logger.warn("Can't borrow anymore.");
            return 0;
        }
        double netBtcVal = numberFormat.parse(account.getTotalNetAssetOfBtc()).doubleValue();
        double liabBtcVal = numberFormat.parse(account.getTotalLiabilityOfBtc()).doubleValue();
        double availToBorrowBtcVal = netBtcVal - liabBtcVal;
        BookTickerPrices.BookTicker btcPrice = bookTickerPrices.getBookTicker("BTCUSDT");
        return availToBorrowBtcVal * btcPrice.bestAsk();
    }

    public void placeTrade(ChartPatternSignal chartPatternSignal) throws ParseException {
        double usdtValueAvailableToTrade;
        if (chartPatternSignal.tradeType() == TradeType.BUY) {
            usdtValueAvailableToTrade = numberFormat.parse(binanceApiRestClient.getAccount().getAssetBalance(USDT).getFree()).doubleValue();
        } else {
            usdtValueAvailableToTrade = usdtValueAvailabeToBorrow();
        }
        Pair<Double, Integer> minNotionalAndLotSize = supportedSymbolsInfo.getMinNotionalAndLotSize(
            chartPatternSignal.coinPair());
        if (minNotionalAndLotSize == null) {
            logger.error(String.format("Unexpectedly supportedSymbolsInfo.getMinNotionalAndLotSize returned null for %s", chartPatternSignal.coinPair()));
            return;
        }
        int stepSizeNumDecimalPlaces = minNotionalAndLotSize.getSecond();
        double minOrderQtyAdjustedForStopLoss =
            getMinQtyUSDTAdjustedForStopLoss(minNotionalAndLotSize.getFirst(), stopLimitPercent);
        if (usdtValueAvailableToTrade < minOrderQtyAdjustedForStopLoss) {
            logger.error(String.format("Lesser than minimum adjusted notional amount of " +
                    " %f USDT in Spot account for placing BUY trade for\n%s.",
                minOrderQtyAdjustedForStopLoss, chartPatternSignal));
            return;
        }
        if (usdtValueAvailableToTrade < perTradeAmount) {
            logger.error(String.format("Lesser than per trade amount %f USDT in Spot account for placing BUY trade for\n%s.",
                perTradeAmount, chartPatternSignal));
            return;
        }
        double usdtToUse = perTradeAmount;
        if (usdtToUse < minOrderQtyAdjustedForStopLoss) {
            logger.warn(String.format(
                "Increasing per trade amount to %f to meet the min order qty adjusted for stop loss.", minOrderQtyAdjustedForStopLoss));
            usdtToUse = minOrderQtyAdjustedForStopLoss;
        }
        double entryPrice = numberFormat.parse(binanceApiRestClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
        String roundedQuantity = getFormattedQuantity(usdtToUse / entryPrice, stepSizeNumDecimalPlaces);

        if (chartPatternSignal.tradeType() == TradeType.SELL) {
            String baseAsset = chartPatternSignal.coinPair().substring(0, chartPatternSignal.coinPair().length() - 4);
            binanceApiMarginRestClient.borrow(baseAsset, roundedQuantity);
        }
        OrderSide orderSide;
        int sign;
        OrderSide stopLossOrderSide;
        switch (chartPatternSignal.tradeType()) {
            case BUY:
                orderSide = OrderSide.BUY;
                stopLossOrderSide = OrderSide.SELL;
                sign = 1;
                NewOrder marketOrder = new NewOrder(chartPatternSignal.coinPair(), orderSide,
                    OrderType.MARKET, /* timeInForce= */ null,
                    roundedQuantity);
                NewOrderResponse marketOrderResp = binanceApiRestClient.newOrder(marketOrder);
                break;
            default:
                orderSide = OrderSide.SELL;
                stopLossOrderSide = OrderSide.BUY;
                sign = -1;
                MarginNewOrder marginMarketOrder = new MarginNewOrder(chartPatternSignal.coinPair(), orderSide,
                    OrderType.MARKET, /* timeInForce= */ null,
                    roundedQuantity);
                MarginNewOrderResponse marginMarketOrderResp = binanceApiMarginRestClient.newOrder(marginMarketOrder);
        }
/*
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
        NewOrder stopLossOrder = new NewOrder(chartPatternSignal.coinPair(),
            stopLossOrderSide,
            OrderType.STOP_LOSS_LIMIT,
            TimeInForce.GTC,
            marketOrderResp.getExecutedQty(),
            stopLimitPrice);
        stopLossOrder.stopPrice(stopPrice);
        NewOrderResponse stopLossOrderResp = binanceApiRestClient.newOrder(stopLossOrder)
        logger.info(String.format("Placed %s Stop loss order %s with status %s for chart pattern signal\n%s.",
            stopLossOrderSide.name(), stopLossOrderResp.toString(), stopLossOrderResp.getStatus().name(), chartPatternSignal));

        dao.setExitStopLimitOrder(chartPatternSignal,
            ChartPatternSignal.Order.create(
                stopLossOrderResp.getOrderId(),
                0,0,
                stopLossOrderResp.getStatus()));*/
    }

    private double getMinQtyUSDTAdjustedForStopLoss(Double minNotional, double stopLossPercent) {
        // Adding extra 25 cents to account for quick price drops when placing order that would reduce the amount being
        // ordered below min notional.
        return minNotional * 100 / (100 - stopLossPercent) + 0.25;
    }
}