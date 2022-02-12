package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.FilterType;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.data.util.Pair;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class SupportedSymbolsInfoTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock
  private BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock private BinanceApiRestClient mockBinanceApiRestClient;

  private SupportedSymbolsInfo supportedSymbolsInfo;
  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newRestClient()).thenReturn(mockBinanceApiRestClient);
    supportedSymbolsInfo = new SupportedSymbolsInfo(mockBinanceApiClientFactory);
  }

  @Test
  public void getMinNotionalAndLotSize() {
    ExchangeInfo exchangeInfo = new ExchangeInfo();
    SymbolInfo symbolInfo = new SymbolInfo();
    symbolInfo.setSymbol("BTCUSDT");
    SymbolFilter minNotionalFilter = new SymbolFilter();
    minNotionalFilter.setFilterType(FilterType.MIN_NOTIONAL);
    minNotionalFilter.setMinNotional("10");
    SymbolFilter lotSizeFilter = new SymbolFilter();
    lotSizeFilter.setFilterType(FilterType.LOT_SIZE);
    lotSizeFilter.setStepSize("0.001");
    List<SymbolFilter> filters = Lists.newArrayList(lotSizeFilter, minNotionalFilter);
    symbolInfo.setFilters(filters);
    exchangeInfo.setSymbols(Lists.newArrayList(symbolInfo));

    when(mockBinanceApiRestClient.getExchangeInfo()).thenReturn(exchangeInfo);

    Pair<Double, Integer> minNotionalAndLotSize = supportedSymbolsInfo.getMinNotionalAndLotSize("BTCUSDT");
    assertThat(minNotionalAndLotSize.getFirst()).isEqualTo(10.0);
    assertThat(minNotionalAndLotSize.getSecond()).isEqualTo(3);
  }
}