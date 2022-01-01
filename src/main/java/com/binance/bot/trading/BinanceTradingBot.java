package com.binance.bot.trading;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.bot.tradesignals.ChartPatternSignal;

public final class BinanceTradingBot {
    private final BinanceApiRestClient binanceApiRestClient;

    public BinanceTradingBot(BinanceApiRestClient binanceApiRestClient) {
        this.binanceApiRestClient = binanceApiRestClient;
    }

    public void placeTrade(ChartPatternSignal chartPatternSignal, boolean isVolSurged) {

    }
}