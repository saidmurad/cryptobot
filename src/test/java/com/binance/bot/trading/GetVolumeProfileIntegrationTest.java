package com.binance.bot.trading;

import com.altfins.TradeType;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.bot.module.BinanceModule;
import com.binance.common.TestUtil;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import junit.framework.TestCase;
import org.junit.Before;
import org.mockito.MockitoAnnotations;

import javax.inject.Inject;
import java.time.Clock;

public class GetVolumeProfileIntegrationTest extends TestCase {

    private static final long CURRENT_TIME_MILLIS = 999999999999999999l;

    @Inject
    private GetVolumeProfile getVolumeProfile;

    @Inject
    private BinanceApiRestClient binanceApiRestClient;

    @Bind
    Clock clock = Clock.systemDefaultZone();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Guice.createInjector(
            BoundFieldModule.of(this),
            new BinanceModule(TestUtil.getArgs())).injectMembers(this);
    }

    public void testCanPlaceTrade() {
        assertTrue(getVolumeProfile.canPlaceTrade("ETHUSDT", TradeType.BUY));
    }

    // Run against real net to get non zero volumes using test api key, by passing System Property -D--use_testnet=false
    public void testGetVolumeProfile() {
        VolumeProfile volProfile = getVolumeProfile.getVolumeProfile("ETHUSDT");
        assertTrue(volProfile.minVol() > 0);
        assertTrue(volProfile.maxVol() > 0);
        assertTrue(volProfile.avgVol() > 0);
    }
}