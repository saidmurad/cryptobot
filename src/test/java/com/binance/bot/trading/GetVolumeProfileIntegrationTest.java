package com.binance.bot.trading;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.bot.tradesignals.TradeType;
import com.binance.common.TestUtil;
import com.google.inject.testing.fieldbinder.Bind;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.inject.Inject;
import java.time.Clock;

import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@TestPropertySource(
    locations = "classpath:test-application-realnet.properties")
public class GetVolumeProfileIntegrationTest {
    @Autowired
    private GetVolumeProfile getVolumeProfile;

    @Bind
    Clock clock = Clock.systemDefaultZone();

    // Works with testnet too.
    @Test
    public void testCanPlaceTrade() {
        assertTrue(getVolumeProfile.canPlaceTrade("ETHUSDT", TradeType.BUY));
    }

    // Run against real (non-test) binancenet to get non zero volumes
    @Test
    public void testGetVolumeProfile() {
        VolumeProfile volProfile = getVolumeProfile.getVolumeProfile("ETHUSDT");
        assertTrue(volProfile.minVol() > 0);
        assertTrue(volProfile.maxVol() > 0);
        assertTrue(volProfile.avgVol() > 0);
    }
}