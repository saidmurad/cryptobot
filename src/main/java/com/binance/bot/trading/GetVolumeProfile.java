package com.binance.bot.trading;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.Util;
import com.binance.bot.tradesignals.TradeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.time.Clock;
import java.util.List;

import static com.binance.bot.common.Constants.USDT;

@Component
public class GetVolumeProfile {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BinanceApiAsyncRestClient binanceApiRestClient;
    private final Clock clock = Clock.systemDefaultZone();

    @Autowired
    GetVolumeProfile(BinanceApiClientFactory binanceApiClientFactory) {
        this.binanceApiRestClient = binanceApiClientFactory.newAsyncRestClient();
    }

    public void getVolumeProfile(String coinPair, BinanceApiCallback<VolumeProfile> volumeProfileCallback) {
        long currentTimeMillis = clock.millis();
        // Look from 2:30 hours past till 30 min ago
        binanceApiRestClient.getCandlestickBars(
            coinPair, CandlestickInterval.FIFTEEN_MINUTES, 1000,
            currentTimeMillis - 150 * 60 * 1000, currentTimeMillis - 30 * 60 * 100,
            candlesticks-> {
                if (candlesticks.isEmpty()) {
                    logger.warn("Got 0 klines for " + coinPair + " so volume data is missing");
                    volumeProfileCallback.onResponse(null);
                }
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
              double finalMinVol = minVol;
              double finalMaxVol = maxVol;
              binanceApiRestClient.getCandlestickBars(coinPair, CandlestickInterval.FIFTEEN_MINUTES, 2, currentTimeMillis - 30 * 60 * 1000, currentTimeMillis,
                    candlesticks2 -> {
                      Candlestick currentCandlestick = candlesticks2.get(0);
                      VolumeProfile volumeProfile = VolumeProfile.newBuilder()
                          .setRecentCandlesticks(candlesticks)
                          .setCurrentCandlestick(currentCandlestick)
                          .setMinVol(finalMinVol)
                          .setMaxVol(finalMaxVol)
                          .setAvgVol(avgVol)
                          .setIsVolSurged(Double.parseDouble(currentCandlestick.getVolume()) >= 2 * avgVol)
                          .setIsVolAtleastMaintained(Double.parseDouble(currentCandlestick.getVolume()) >= avgVol)
                          .build();
                      volumeProfileCallback.onResponse(volumeProfile);
                    });
            });
    }
}
