package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.account.MarginAccount;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
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

import java.text.ParseException;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class CrossMarginAccountBalanceTest {

  @Rule public MockitoRule mocks = MockitoJUnit.rule();

  @Mock private BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock
  private BinanceApiMarginRestClient mockBinanceApiMarginRestClient;
  @Mock private BookTickerPrices mockBookTickerPrices;
  @Mock private MarginAccount mockMarginAccount;

  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newMarginRestClient()).thenReturn(mockBinanceApiMarginRestClient);
  }

  @Test
  public void getTotalAndBorrowedUSDTValue() throws BinanceApiException, ParseException, InterruptedException {
    CrossMarginAccountBalance crossMarginAccountBalance = new CrossMarginAccountBalance(mockBinanceApiClientFactory, mockBookTickerPrices);
    BookTickerPrices.BookTicker btcTicker = BookTickerPrices.BookTicker.create(20000, 0);
    when(mockBookTickerPrices.getBookTicker("BTCUSDT")).thenReturn(btcTicker);
    when(mockBinanceApiMarginRestClient.getAccount()).thenReturn(mockMarginAccount);
    when(mockMarginAccount.getTotalAssetOfBtc()).thenReturn("1");
    when(mockMarginAccount.getTotalLiabilityOfBtc()).thenReturn("2");

    Pair<Integer, Integer> totalAndBorrowedUSDTValue = crossMarginAccountBalance.getTotalAndBorrowedUSDTValue();

    assertThat(totalAndBorrowedUSDTValue.getFirst()).isEqualTo(20000);
    assertThat(totalAndBorrowedUSDTValue.getSecond()).isEqualTo(40000);
  }
}