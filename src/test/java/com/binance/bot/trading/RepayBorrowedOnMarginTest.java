package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.account.MarginAccount;
import com.binance.api.client.domain.account.MarginAssetBalance;
import com.binance.api.client.exception.BinanceApiException;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.text.ParseException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
public class RepayBorrowedOnMarginTest {

  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock
  BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock
  BinanceApiMarginRestClient mockBinanceApiMarginRestClient;
  @Mock private MarginAccount mockMarginAccount;
  private static final String COIN = "LUNA";
  private RepayBorrowedOnMargin repayBorrowedOnMargin;

  @Before
  public void setUp() throws BinanceApiException {
    when(mockBinanceApiClientFactory.newMarginRestClient()).thenReturn(mockBinanceApiMarginRestClient);
    when(mockBinanceApiMarginRestClient.getAccount()).thenReturn(mockMarginAccount);
    repayBorrowedOnMargin = new RepayBorrowedOnMargin(mockBinanceApiClientFactory);
  }

  @Test
  public void repay_borrowedIsLessThanQty_repaysBorrowed() throws BinanceApiException, ParseException {
    MarginAssetBalance coinBalance = new MarginAssetBalance();
    coinBalance.setBorrowed("1.0");
    when(mockMarginAccount.getAssetBalance(COIN)).thenReturn(coinBalance);

    repayBorrowedOnMargin.repay(COIN, 2.0);

    verify(mockBinanceApiMarginRestClient).repay(eq(COIN), eq("1.0"));
  }

  @Test
  public void repay_borrowedIsMoreThanQty_repaysQty() throws BinanceApiException, ParseException {
    MarginAssetBalance coinBalance = new MarginAssetBalance();
    coinBalance.setBorrowed("3.0");
    when(mockMarginAccount.getAssetBalance(COIN)).thenReturn(coinBalance);

    repayBorrowedOnMargin.repay(COIN, 2.0);

    verify(mockBinanceApiMarginRestClient).repay(eq(COIN), eq("2.0"));
  }

  @Test
  public void repay_nothingToRepay() throws BinanceApiException, ParseException {
    MarginAssetBalance coinBalance = new MarginAssetBalance();
    coinBalance.setBorrowed("0.0");
    when(mockMarginAccount.getAssetBalance(COIN)).thenReturn(coinBalance);

    repayBorrowedOnMargin.repay(COIN, 2.0);

    verify(mockBinanceApiMarginRestClient, never()).repay(any(), any());
  }
}