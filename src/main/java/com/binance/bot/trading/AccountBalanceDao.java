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

import javax.sql.DataSource;
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

  public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void writeAccountBalanceToDB() throws BinanceApiException, ParseException {
    MarginAccount account = binanceApiMarginRestClient.getAccount();
    double btcPrice = bookTickerPrices.getBookTicker("BTCUSDT").bestAsk();
    int principal = getPrincipal();
    int totalAssetValueInUSDT = (int) (numberFormat.parse(account.getTotalAssetOfBtc()).doubleValue() * btcPrice);
    int netAssetValueInUSDT = (int) (numberFormat.parse(account.getTotalNetAssetOfBtc()).doubleValue() * btcPrice);
    double rr = (double) (netAssetValueInUSDT - principal) / principal * 100;
    int liabilityValueInUSDT = (int) (numberFormat.parse(account.getTotalLiabilityOfBtc()).doubleValue() * btcPrice);
    MarginAssetBalance usdtBalance = account.getAssetBalance("USDT");
    String sql = String.format("insert into CrossMarginAccountBalanceHistory(Time, FreeUSDT, " +
            "LockedUSDT, BorrowedUSDT, NetUSDT," +
            "TotalValue, LiabilityValue, NetValue, MarginLevel, ReturnRate) values(" +
            "'%s', %d, %d, %d, %d, %d, %d, %d, %f, %f)",
        df.format(new Date()),
        numberFormat.parse(usdtBalance.getFree()).intValue(),
        numberFormat.parse(usdtBalance.getLocked()).intValue(),
        numberFormat.parse(usdtBalance.getBorrowed()).intValue(),
        numberFormat.parse(usdtBalance.getNetAsset()).intValue(),
        totalAssetValueInUSDT,
        liabilityValueInUSDT,
        netAssetValueInUSDT,
        numberFormat.parse(account.getMarginLevel()).doubleValue(),
        rr
        );
    if (jdbcTemplate.update(sql) != 1) {
      logger.error("Failed to insert row into CrossMarginAccountBalanceHistory.");
    }
  }

  private int getPrincipal() {
    String sql = "select principal from CrossMarginAccountFundingHistory " +
        "where rowid=(select max(rowid) from CrossMarginAccountFundingHistory)";
    return jdbcTemplate.queryForObject(sql, new Object[]{}, (rs, rowNum) -> rs.getInt("principal"));
  }
}
