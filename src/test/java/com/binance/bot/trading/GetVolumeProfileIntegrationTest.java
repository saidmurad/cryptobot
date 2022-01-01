package com.binance.bot.trading;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.bot.tradesignals.TradeType;
import com.binance.common.TestUtil;
import com.google.inject.testing.fieldbinder.Bind;
import junit.framework.TestCase;
import org.junit.Before;
import org.mockito.MockitoAnnotations;

import javax.inject.Inject;
import java.time.Clock;

public class GetVolumeProfileIntegrationTest extends TestCase {

    private boolean isUsingTestNet, isUsingRealApiKey;

    @Inject
    private GetVolumeProfile getVolumeProfile;

    @Inject
    private BinanceApiRestClient binanceApiRestClient;

    @Bind
    Clock clock = Clock.systemDefaultZone();

    @Before
    public void setUp() {
        String testArgs[] = TestUtil.getArgs();
        isUsingTestNet = testArgs.length > 0 && testArgs[0].equals("--use_testnet=true");
        isUsingRealApiKey = testArgs.length > 1 && testArgs[1].startsWith("--api_key=true");
        MockitoAnnotations.initMocks(this);
    }

    public void testCanPlaceTrade() {
        if (!isUsingTestNet && !isUsingRealApiKey) {
            return;
        }
        assertTrue(getVolumeProfile.canPlaceTrade("ETHUSDT", TradeType.BUY));
    }

    // Run against real (non-test) binancenet to get non zero volumes using test api key itself but by passing
    // System Property -D--use_testnet=false
    public void testGetVolumeProfile() {
        if (isUsingTestNet) {
            return;
        }
        VolumeProfile volProfile = getVolumeProfile.getVolumeProfile("ETHUSDT");
        assertTrue(volProfile.minVol() > 0);
        assertTrue(volProfile.maxVol() > 0);
        assertTrue(volProfile.avgVol() > 0);
    }
}