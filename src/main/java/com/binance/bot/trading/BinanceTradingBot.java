package com.binance.bot.trading;

import com.altfins.TradeType;
import com.binance.api.client.BinanceApiRestClient;

public final class BinanceTradingBot {
    private final BinanceApiRestClient binanceApiRestClient;

    public BinanceTradingBot(BinanceApiRestClient binanceApiRestClient) {
        this.binanceApiRestClient = binanceApiRestClient;
    }

    public void trade(String coin, TradeType tradeType) {
        VolumeProfile volumeProfile = get5minVolumeProfile("coin"/*use autovalue and getter? tradeInfo.coin*/);
        boolean highConfidence = volumeProfile.current >= 2* volumeProfile.avg;

    }


    // Volume profie info for the past hour in 5 in candle stick intervals.
    static class VolumeProfile {
        // The min volume seen in a 5 min candle.
        double min;
        // The max volume seen
        double max;
        double avg;
        // The current volume
        double current;
    }

    /**
     * Gets the last hour's volume profile in 5 min candle sticks,
     */
    private VolumeProfile get5minVolumeProfile(String coin) {
        // TODO
        return null;
    }
}