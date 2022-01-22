package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.bot.common.Util;
import com.binance.bot.tradesignals.TradeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.time.Clock;
import java.util.List;

import static com.binance.bot.common.Constants.USDT;

@Component
public class GetVolumeProfile {
    private final BinanceApiRestClient binanceApiRestClient;
    private final Clock clock = Clock.systemDefaultZone();

    @Autowired
    GetVolumeProfile(BinanceApiClientFactory binanceApiClientFactory) {
        this.binanceApiRestClient = binanceApiClientFactory.newRestClient();
    }

    boolean canPlaceTrade(String coinPair, TradeType tradeType) {
        String coin = tradeType == TradeType.BUY ? USDT : Util.getCoinFromCoinPair(coinPair);
        AssetBalance balance = binanceApiRestClient.getAccount().getAssetBalance(coin);
        return Double.parseDouble(balance.getFree()) >= 0;
    }

    public VolumeProfile getVolumeProfile(String coinPair) {
        long currentTimeMillis = clock.millis();
        // Look from 2:30 hours past till 30 min ago
        List<Candlestick> candlesticks = binanceApiRestClient.getCandlestickBars(coinPair, CandlestickInterval.FIFTEEN_MINUTES, 1000, currentTimeMillis - 150 * 60 * 1000, currentTimeMillis - 30 * 60 * 100);
        double totVol = 0;
        double minVol = Double.parseDouble(candlesticks.get(0).getVolume());
        double maxVol = 0;
        for (Candlestick candlestick : candlesticks) {
            double vol = Double.parseDouble(candlestick.getVolume());
            totVol += vol;
            if (vol < minVol) {
                minVol = vol;
            } else if (vol > maxVol) {
                maxVol = vol;
            }
        }
        double avgVol = totVol / candlesticks.size();
        Candlestick currentCandlestick = binanceApiRestClient.getCandlestickBars(coinPair, CandlestickInterval.FIFTEEN_MINUTES, 2, currentTimeMillis - 30 * 60 * 1000, currentTimeMillis).get(0);
        return VolumeProfile.newBuilder()
                .setRecentCandlesticks(candlesticks)
                .setCurrentCandlestick(currentCandlestick)
                .setMinVol(minVol)
                .setMaxVol(maxVol)
                .setAvgVol(avgVol)
                .setIsVolSurged(Double.parseDouble(currentCandlestick.getVolume()) >= 2 * avgVol)
                .setIsVolAtleastMaintained(Double.parseDouble(currentCandlestick.getVolume()) >= avgVol)
                .build();
    }
}
