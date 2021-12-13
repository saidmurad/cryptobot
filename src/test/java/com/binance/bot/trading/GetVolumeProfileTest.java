package com.binance.bot.trading;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.inject.Inject;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class GetVolumeProfileTest {

  private static final String COIN_PAIR = "ETHUSDT";
  private static final long CURRENT_TIME_MILLIS = 999999999999999999l;

  @Mock @Bind
  private BinanceApiRestClient mockBinanceApiRestClient;
  @Mock @Bind private Clock clock;

  @Inject
  private GetVolumeProfile getVolumeProfile;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
  }

  @Test
  public void canPlaceTrade() {
  }

  @Test
  public void getVolumeProfile_minMaxAvg() {
    when(clock.millis()).thenReturn(CURRENT_TIME_MILLIS);
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 1000, CURRENT_TIME_MILLIS - 150 * 60 * 1000, CURRENT_TIME_MILLIS - 30 * 60 * 100))
        .thenReturn(getCandlesticks());
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 2, CURRENT_TIME_MILLIS - 30 * 60 * 1000, CURRENT_TIME_MILLIS))
        .thenReturn(getCandlesticks());

    VolumeProfile volumeProfile = getVolumeProfile.getVolumeProfile(COIN_PAIR);

    assertEquals(150.0, volumeProfile.avgVol());
    assertEquals(100.0, volumeProfile.minVol());
    assertEquals(200.0, volumeProfile.maxVol());
  }

  private List<Candlestick> getCandlesticks() {
    List<Candlestick> candlesticks = new ArrayList<>();
    Candlestick candlestick = new Candlestick();
    candlestick.setVolume("100");
    candlesticks.add(candlestick);
    candlestick = new Candlestick();
    candlestick.setVolume("200");
    candlesticks.add(candlestick);
    return candlesticks;
  }
}