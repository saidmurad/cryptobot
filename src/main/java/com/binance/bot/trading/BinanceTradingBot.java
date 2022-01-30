package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.CancelOrderResponse;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

import static com.binance.bot.common.Constants.USDT;

@Component
public class BinanceTradingBot {
    private final BinanceApiRestClient binanceApiRestClient;
    private final ChartPatternSignalDaoImpl dao;
    private final SupportedSymbolsInfo supportedSymbolsInfo;
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    static final double PER_TRADE_AMOUNT = 20;

    @Autowired
    public BinanceTradingBot(BinanceApiClientFactory binanceApiRestClientFactory, SupportedSymbolsInfo supportedSymbolsInfo, ChartPatternSignalDaoImpl dao) {
        this.binanceApiRestClient = binanceApiRestClientFactory.newRestClient();
        this.supportedSymbolsInfo = supportedSymbolsInfo;
        this.dao = dao;
    }

    String getFormattedQuantity(String coinPair, double qty) {
        Integer lotSize = supportedSymbolsInfo.getLotSize(coinPair);
        if (lotSize == null) {
            logger.error("Found no lot size for symbol " + coinPair + ". Using 2 as the lot size.");
            lotSize = 2;
        }
        return String.format("%." + lotSize + "f", qty);
    }

    public void placeTrade(ChartPatternSignal chartPatternSignal) throws ParseException {
        switch (chartPatternSignal.tradeType()) {
            case BUY:
                double usdtBalance = numberFormat.parse(binanceApiRestClient.getAccount().getAssetBalance(USDT).getFree()).doubleValue();
                if (usdtBalance < 5.0) {
                    logger.error("No USDT in Spot account for placing BUY trade for\n" + chartPatternSignal.toString());
                    return;
                }
                if (usdtBalance > PER_TRADE_AMOUNT) {
                    usdtBalance = PER_TRADE_AMOUNT;
                }

                NewOrder buyOrder = new NewOrder(chartPatternSignal.coinPair(), OrderSide.BUY,
                    OrderType.MARKET, /* timeInForce= */ null,
                    getFormattedQuantity(chartPatternSignal.coinPair(), usdtBalance / chartPatternSignal.priceAtTimeOfSignalReal()));
                NewOrderResponse buyOrderResp = binanceApiRestClient.newOrder(buyOrder);
                logger.info(String.format("Placed market buy order %s with status %s for chart pattern signal\n%s.", buyOrderResp.toString(), buyOrderResp.getStatus().name(), chartPatternSignal));
                // TODO: delayed market order executions.
                dao.setEntryOrder(chartPatternSignal,
                    ChartPatternSignal.Order.create(
                        buyOrderResp.getOrderId(),
                        /* TODO: Find the actual price from the associated Trade */
                        chartPatternSignal.priceAtTimeOfSignalReal(),
                        numberFormat.parse(buyOrderResp.getExecutedQty()).doubleValue(), buyOrderResp.getStatus()));

                NewOrder sellLimitOrder = new NewOrder(chartPatternSignal.coinPair(), OrderSide.SELL, OrderType.LIMIT, TimeInForce.GTC, buyOrderResp.getExecutedQty(), String.format("%.2f", chartPatternSignal.priceTarget()));
                NewOrderResponse sellLimitOrderResp = binanceApiRestClient.newOrder(sellLimitOrder);
                logger.info(String.format("Placed sell limit order %s with status %s for chart pattern signal\n%s.", sellLimitOrderResp.toString(), sellLimitOrderResp.getStatus().name(), chartPatternSignal.toString()));

                dao.setExitLimitOrder(chartPatternSignal,
                    ChartPatternSignal.Order.create(
                        sellLimitOrderResp.getOrderId(),
                        numberFormat.parse(sellLimitOrderResp.getPrice()).doubleValue(),
                        numberFormat.parse(sellLimitOrderResp.getExecutedQty()).doubleValue(),
                        sellLimitOrderResp.getStatus()));

                break;
            default:
        }
    }

    public boolean exitPosition(ChartPatternSignal chartPatternSignal) throws ParseException {
        CancelOrderRequest cancelOrderRequest = new CancelOrderRequest(chartPatternSignal.coinPair(), chartPatternSignal.exitLimitOrder().orderId());
        CancelOrderResponse cancelOrderResponse = binanceApiRestClient.cancelOrder(cancelOrderRequest);
        logger.info("Cancel order response of profit taking order: " + cancelOrderResponse.toString());

        double qtyToExit = chartPatternSignal.entryOrder().executedQty() - chartPatternSignal.exitLimitOrder().executedQty();
        NewOrder exitMarketOrder = new NewOrder(chartPatternSignal.coinPair(),
            chartPatternSignal.tradeType() == TradeType.BUY ? OrderSide.SELL : OrderSide.BUY,
            OrderType.MARKET, null, getFormattedQuantity(chartPatternSignal.coinPair(), qtyToExit));
        NewOrderResponse exitMarketOrderResponse = binanceApiRestClient.newOrder(exitMarketOrder);
        double currPrice = numberFormat.parse(binanceApiRestClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
        logger.info(String.format("Exit market order issued for chart pattern signal %s and got order response \n%s at price %d",
            chartPatternSignal.toString(), exitMarketOrderResponse.toString()), currPrice);

        // TODO: delayed market order executions.
        return dao.setExitMarketOrder(chartPatternSignal,
            ChartPatternSignal.Order.create(exitMarketOrderResponse.getOrderId(),
                numberFormat.parse(exitMarketOrderResponse.getExecutedQty()).doubleValue(),
                currPrice, exitMarketOrderResponse.getStatus()));
    }
}