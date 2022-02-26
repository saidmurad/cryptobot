package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.account.MarginAccount;
import com.binance.api.client.domain.account.MarginAssetBalance;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Repository
public class AccountBalanceDao {
  @Autowired
  JdbcTemplate jdbcTemplate;
  static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private final BinanceApiMarginRestClient binanceApiMarginRestClient;
  private final BookTickerPrices bookTickerPrices;
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  public AccountBalanceDao(BinanceApiClientFactory binanceApiClientFactory, BookTickerPrices bookTickerPrices) {
    this.binanceApiMarginRestClient = binanceApiClientFactory.newMarginRestClient();
    this.bookTickerPrices = bookTickerPrices;
  }

  public void writeAccountBalanceToDB() throws BinanceApiException, ParseException {
    MarginAccount account = binanceApiMarginRestClient.getAccount();
    double btcPrice = bookTickerPrices.getBookTicker("BTCUSDT").bestAsk();
    double principal = getPrincipal();
    double totalAssetValueInUSDT = numberFormat.parse(account.getTotalAssetOfBtc()).doubleValue() * btcPrice;
    double rr = (totalAssetValueInUSDT - principal) / principal * 100;
    double liabilityValueInUSDT = numberFormat.parse(account.getTotalLiabilityOfBtc()).doubleValue() * btcPrice;
    liabilityValueInUSDT += getInterestInUSDT(account);
    String sql = String.format("insert into CrossMarginAccountBalanceHistory values('%s', %d, %d, %d, %f, %d, %f)",
        df.format(new Date()),
        numberFormat.parse(account.getAssetBalance("USDT").getFree()).intValue(),
        totalAssetValueInUSDT,
        liabilityValueInUSDT,
        numberFormat.parse(account.getMarginLevel()).doubleValue(),
        getLockedInTrades(account.getUserAssets()),
        rr
        );
    if (jdbcTemplate.update(sql) != 1) {
      logger.error("Failed to insert row into CrossMarginAccountBalanceHistory.");
    }
  }

  private double getInterestInUSDT(MarginAccount account) throws ParseException {
    MarginAssetBalance bnbBal = account.getAssetBalance("BNB");
    double bnbInterest = numberFormat.parse(bnbBal.getInterest()).doubleValue();
    if (bnbInterest == 0) {
      return 0;
    }
    double bnbPrice = bookTickerPrices.getBookTicker("BNBUSDT").bestAsk();
    return bnbInterest * bnbPrice;
  }

  private int getLockedInTrades(List<MarginAssetBalance> userAssets) throws ParseException {
    int totalLocked = 0;
    for (MarginAssetBalance asset: userAssets) {
      double locked = numberFormat.parse(asset.getLocked()).doubleValue();
      if (locked > 0) {

      }
    }
    return 0;
  }

  private int getPrincipal() {
    String sql = "select principal from CrossMarginAccountBalanceHistory " +
        "where rowid=(select max(rowid) from CrossMarginAccountBalanceHistory)";
    return jdbcTemplate.queryForObject(sql, new Object[]{}, (rs, rowNum) -> rs.getInt("principal"));
  }
}
