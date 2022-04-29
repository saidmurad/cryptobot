package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.account.MarginAccount;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

@Component
public class CrossMarginAccountBalance {
  private final BinanceApiMarginRestClient binanceApiMarginRestClient;
  private final BookTickerPrices bookTickerPrices;

  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);

  public CrossMarginAccountBalance(BinanceApiClientFactory binanceApiRestClientFactory, BookTickerPrices bookTickerPrices) {
    binanceApiMarginRestClient = binanceApiRestClientFactory.newMarginRestClient();
    this.bookTickerPrices = bookTickerPrices;
  }

  /**
   * Returns total and liability value in USDT.
   * @return
   * @throws BinanceApiException
   * @throws ParseException
   */
  public Pair<Integer, Integer> getTotalAndBorrowedUSDTValue() throws BinanceApiException, ParseException {
    MarginAccount account = binanceApiMarginRestClient.getAccount();
    double btcPrice = bookTickerPrices.getBookTicker("BTCUSDT").bestAsk();
    int totalUsdtVal = (int) (numberFormat.parse(account.getTotalAssetOfBtc()).doubleValue() * btcPrice);
    int liabUsdtVal = (int) (numberFormat.parse(account.getTotalLiabilityOfBtc()).doubleValue() * btcPrice);
    return Pair.of(totalUsdtVal, liabUsdtVal);
  }
}
