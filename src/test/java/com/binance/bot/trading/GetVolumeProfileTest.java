package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class GetVolumeProfileTest {

  private static final String COIN_PAIR = "ETHUSDT";
  private static final long CURRENT_TIME_MILLIS = 999999999999999999l;

  @Mock private BinanceApiClientFactory mockBinanceApiRestClientFactory;
  @Mock
  private BinanceApiRestClient mockBinanceApiRestClient;
  @Mock private Clock clock;
  private GetVolumeProfile getVolumeProfile;

  @Before
  public void setUp() throws BinanceApiException {
    MockitoAnnotations.initMocks(this);
    when(mockBinanceApiRestClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    getVolumeProfile = new GetVolumeProfile(mockBinanceApiRestClientFactory);
  }

  @Test
  public void getVolumeProfile_minMaxAvg() throws BinanceApiException {
    when(clock.millis()).thenReturn(CURRENT_TIME_MILLIS);
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES,
        1000, CURRENT_TIME_MILLIS - 150 * 60 * 1000,
        CURRENT_TIME_MILLIS - 30 * 60 * 100))
        .thenReturn(getCandlesticks("100", "200"));
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES,
        2, CURRENT_TIME_MILLIS - 30 * 60 * 1000, CURRENT_TIME_MILLIS))
        .thenReturn(getCandlesticks("100", "200"));

    VolumeProfile volumeProfile = getVolumeProfile.getVolumeProfile(COIN_PAIR);

    assertEquals(150.0, volumeProfile.avgVol());
    assertEquals(100.0, volumeProfile.minVol());
    assertEquals(200.0, volumeProfile.maxVol());
  }

  @Test
  public void getVolumeProfile_volNotMaintainedEven() throws BinanceApiException {
    when(clock.millis()).thenReturn(CURRENT_TIME_MILLIS);
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 1000, CURRENT_TIME_MILLIS - 150 * 60 * 1000, CURRENT_TIME_MILLIS - 30 * 60 * 100))
        .thenReturn(getCandlesticks("100", "200"));
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 2, CURRENT_TIME_MILLIS - 30 * 60 * 1000, CURRENT_TIME_MILLIS))
        .thenReturn(getCandlesticks("100", "200"));

    VolumeProfile volumeProfile = getVolumeProfile.getVolumeProfile(COIN_PAIR);

    assertFalse(volumeProfile.isVolAtleastMaintained());
  }

  @Test
  public void getVolumeProfile_volAtleastMaintainedEven() throws BinanceApiException {
    when(clock.millis()).thenReturn(CURRENT_TIME_MILLIS);
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 1000, CURRENT_TIME_MILLIS - 150 * 60 * 1000, CURRENT_TIME_MILLIS - 30 * 60 * 100))
        .thenReturn(getCandlesticks("100", "200"));
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 2, CURRENT_TIME_MILLIS - 30 * 60 * 1000, CURRENT_TIME_MILLIS))
        .thenReturn(getCandlesticks("150", "200"));

    VolumeProfile volumeProfile = getVolumeProfile.getVolumeProfile(COIN_PAIR);

    assertTrue(volumeProfile.isVolAtleastMaintained());
  }

  @Test
  public void getVolumeProfile_volNotSurged() throws BinanceApiException {
    when(clock.millis()).thenReturn(CURRENT_TIME_MILLIS);
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 1000, CURRENT_TIME_MILLIS - 150 * 60 * 1000, CURRENT_TIME_MILLIS - 30 * 60 * 100))
        .thenReturn(getCandlesticks("100", "200"));
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 2, CURRENT_TIME_MILLIS - 30 * 60 * 1000, CURRENT_TIME_MILLIS))
        .thenReturn(getCandlesticks("150", "200"));

    VolumeProfile volumeProfile = getVolumeProfile.getVolumeProfile(COIN_PAIR);

    assertFalse(volumeProfile.isVolSurged());
  }

  @Test
  public void getVolumeProfile_volSurged() throws BinanceApiException {
    when(clock.millis()).thenReturn(CURRENT_TIME_MILLIS);
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 1000, CURRENT_TIME_MILLIS - 150 * 60 * 1000, CURRENT_TIME_MILLIS - 30 * 60 * 100))
        .thenReturn(getCandlesticks("100", "200"));
    when(mockBinanceApiRestClient.getCandlestickBars(COIN_PAIR, CandlestickInterval.FIFTEEN_MINUTES, 2, CURRENT_TIME_MILLIS - 30 * 60 * 1000, CURRENT_TIME_MILLIS))
        .thenReturn(getCandlesticks("300", "200"));

    VolumeProfile volumeProfile = getVolumeProfile.getVolumeProfile(COIN_PAIR);

    assertTrue(volumeProfile.isVolSurged());
  }

  private List<Candlestick> getCandlesticks(String vol1, String vol2) {
    List<Candlestick> candlesticks = new ArrayList<>();
    Candlestick candlestick = new Candlestick();
    candlestick.setVolume(vol1);
    candlesticks.add(candlestick);
    candlestick = new Candlestick();
    candlestick.setVolume(vol2);
    candlesticks.add(candlestick);
    return candlesticks;
  }
}