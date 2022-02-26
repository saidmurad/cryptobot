package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.account.MarginAssetBalance;
import com.binance.api.client.domain.account.MarginTransaction;
import com.binance.api.client.exception.BinanceApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

@Component
public class RepayBorrowedOnMargin {
  private final BinanceApiMarginRestClient binanceMarginRestClient;
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  public RepayBorrowedOnMargin(BinanceApiClientFactory binanceApiClientFactory) {
    binanceMarginRestClient = binanceApiClientFactory.newMarginRestClient();
  }

  public void repay(String coin, double qty) throws BinanceApiException, ParseException {
    MarginAssetBalance coinAsset = binanceMarginRestClient.getAccount().getAssetBalance(coin);
    double borrowed = numberFormat.parse(coinAsset.getBorrowed()).doubleValue();
    Double qtyToRepay = Math.min(borrowed, qty);
    if (qtyToRepay == 0) {
      logger.warn(String.format("Did not find any borrowed quantity for %s to repay.", qty));
    } else {
      binanceMarginRestClient.repay(coin, qtyToRepay.toString());
      logger.info(String.format("Repaid %f of %s.", qtyToRepay, coin));
    }
  }
}
