package com.binance.bot.trading;

import com.altfins.ChartPatternSignal;
import com.altfins.TradeType;
import com.binance.api.client.BinanceApiRestClient;

public final class BinanceTradingBot {
    private final BinanceApiRestClient binanceApiRestClient;

    public BinanceTradingBot(BinanceApiRestClient binanceApiRestClient) {
        this.binanceApiRestClient = binanceApiRestClient;
    }

    public void placeTrade(ChartPatternSignal chartPatternSignal, boolean isVolSurged) {

    }
}