package com.binance.bot.trading;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

import static com.binance.bot.common.Constants.USDT;

public class BinanceTradingBot {
    private final BinanceApiRestClient binanceApiRestClient;
    private final ChartPatternSignalDaoImpl dao;
    private final SupportedSymbolsInfo supportedSymbolsInfo;
    private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final double PER_TRADE_AMOUNT = 20;
    @Autowired
    public BinanceTradingBot(BinanceApiRestClient binanceApiRestClient, SupportedSymbolsInfo supportedSymbolsInfo, ChartPatternSignalDaoImpl dao) {
        this.binanceApiRestClient = binanceApiRestClient;
        this.supportedSymbolsInfo = supportedSymbolsInfo;
        this.dao = dao;
    }

    public void placeTrade(ChartPatternSignal chartPatternSignal) throws ParseException {
        switch (chartPatternSignal.tradeType()) {
            case BUY:
                double usdtBalance = numberFormat.parse(binanceApiRestClient.getAccount().getAssetBalance(USDT).getFree()).doubleValue();
                if (usdtBalance < 1.0) {
                    logger.error("No USDT in Spot account for placing BUY trade for\n" + chartPatternSignal.toString());
                    return;
                }
                NewOrder buyOrder = new NewOrder(chartPatternSignal.coinPair(), OrderSide.BUY, OrderType.MARKET, TimeInForce.FOK, String.format("%.2f", usdtBalance / chartPatternSignal.priceAtTimeOfSignalReal()));
                NewOrderResponse buyOrderResp = binanceApiRestClient.newOrder(buyOrder);
                logger.info(String.format("Placed market buy order %s with status %s for chart pattern signal\n%s.", buyOrderResp.toString(), buyOrderResp.getStatus().name(), chartPatternSignal.toString()));
                dao.setEntryTrade(chartPatternSignal,
                    ChartPatternSignal.Trade.create(
                        buyOrderResp.getOrderId(), numberFormat.parse(buyOrderResp.getPrice()).doubleValue(), numberFormat.parse(buyOrderResp.getExecutedQty()).doubleValue()));

                NewOrder sellLimitOrder = new NewOrder(chartPatternSignal.coinPair(), OrderSide.SELL, OrderType.LIMIT, TimeInForce.GTC, buyOrderResp.getExecutedQty(), String.format("%.2f", chartPatternSignal.priceTarget()));
                NewOrderResponse sellLimitOrderResp = binanceApiRestClient.newOrder(sellLimitOrder);
                logger.info(String.format("Placed sell limit order %s with status %s for chart pattern signal\n%s.", sellLimitOrderResp.toString(), sellLimitOrderResp.getStatus().name(), chartPatternSignal.toString()));

                break;
            default:
        }
    }
}