package com.binance.bot.processsignals;

import com.altfins.TimeFrame;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.bot.chartpattern.ChartPatternSignal;
import com.binance.bot.trading.GetVolumeProfile;

import javax.inject.Inject;
import java.util.List;

public class ProcessSignals {

    private final BinanceApiRestClient binanceApiRestClient;
    private final GetVolumeProfile getVolumeProfile;

    @Inject
    ProcessSignals(BinanceApiRestClient binanceApiRestClient, GetVolumeProfile getVolumeProfile) {
        this.binanceApiRestClient = binanceApiRestClient;
        this.getVolumeProfile = getVolumeProfile;
    }

    /**
     * Receives and processes the list of currently "active" patterns for a particular time frame.
     * Only newer patterns from the list are to be processed, as older patterns would have already been
     * seen before and entered in our system. "Newer" is defined as follows:
     * For 15 minute time frame, less then 15 minutes.
     * For other time frames, less than an hour.
     */
    public void ProcessPattern(TimeFrame timeFrame, List<ChartPatternSignal> chartPatternSignals) {
        // Filter the recent signals.

        // For each signal, get the current price using binanceApiRestClient, and volume profile using
        // getVolumeProfile for the coin pair.

        // Insert to SQLite db columns: coinPair, timeframe, tradeType, currentPrice, targetPrice,
        // isVolumeSurged as a boolean, VolumeProfile as a blob,
        // signal occurence time (current time),
        // 10 candlestick expiry time (rounded up to minutes), signal expiry time (rounded up to minutes).
    }
}
