package com.binance.bot.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.account.MarginAccount;
import com.binance.api.client.domain.account.MarginAssetBalance;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
import com.binance.bot.util.SetupDatasource;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class AccountBalanceDaoTest {
  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock
  BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock
  BinanceApiMarginRestClient mockBinanceApiMarginRestClient;
  @Mock private BookTickerPrices mockBookTickerPrices;
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private AccountBalanceDao accountBalanceDao;
  private JdbcTemplate jdbcTemplate;
  private static final String CREATE_CROSS_MARGIN_FUNDING_HISTORY_TABLE = "create table CrossMarginAccountFundingHistory(\n" +
      "    Time TEXT not NULL,\n" +
      "    Principal REAL not NULL\n" +
      ")";

  private static final String CREATE_CROSS_MARGIN_ACCOUNT_BALANCE_HISTORY_TABLE = "create table CrossMarginAccountBalanceHistory(\n" +
      "    Time TEXT not NULL,\n" +
      "    FreeUSDT INTEGER not NULL,\n" +
      "    LockedUSDT INTEGER not NULL,\n" +
      "    BorrowedUSDT INTEGER NOT NULL,\n" +
      "    NetUSDT INTEGER not NULL,    \n" +
      "    TotalValue INTEGER not NULL,\n" +
      "    LiabilityValue INTEGER not NULL,\n" +
      "    NetValue INTEGER not NULL,\n" +
      "    MarginLevel REAL not NULL,\n" +
      "    ReturnRate REAL\n" +
      ")";
  @Before
  public void setUp() {
    when(mockBinanceApiClientFactory.newMarginRestClient()).thenReturn(mockBinanceApiMarginRestClient);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    accountBalanceDao = new AccountBalanceDao(mockBinanceApiClientFactory, mockBookTickerPrices);
    DataSource dataSource = SetupDatasource.getDataSource();
    jdbcTemplate = new JdbcTemplate(dataSource);
    accountBalanceDao.setJdbcTemplate(jdbcTemplate);
    jdbcTemplate.execute(CREATE_CROSS_MARGIN_FUNDING_HISTORY_TABLE);
    jdbcTemplate.execute(CREATE_CROSS_MARGIN_ACCOUNT_BALANCE_HISTORY_TABLE);
    jdbcTemplate.update(String.format("insert into CrossMarginAccountFundingHistory(Time, Principal) values('%s',12345)",
        new Date()));
  }

  @Test
  public void writeAccountBalanceToDB() throws BinanceApiException, ParseException {
    MarginAccount account = new MarginAccount();
    when(mockBinanceApiMarginRestClient.getAccount()).thenReturn(account);
    MarginAssetBalance usdtAssetBalance = new MarginAssetBalance();
    usdtAssetBalance.setAsset("USDT");
    usdtAssetBalance.setNetAsset("4");
    usdtAssetBalance.setBorrowed("4");
    usdtAssetBalance.setFree("6");
    usdtAssetBalance.setLocked("2");

    account.setUserAssets(Lists.newArrayList(usdtAssetBalance));
    account.setMarginLevel("1.5");
    account.setTotalAssetOfBtc("3");
    account.setTotalNetAssetOfBtc("1");
    account.setTotalLiabilityOfBtc("2");

    BookTickerPrices.BookTicker btcBookTicker = BookTickerPrices.BookTicker.create(40000, 40000);
    when(mockBookTickerPrices.getBookTicker("BTCUSDT")).thenReturn(btcBookTicker);

    accountBalanceDao.writeAccountBalanceToDB();

    Boolean result =
        jdbcTemplate.query("select * from CrossMarginAccountBalanceHistory",
            new ResultSetExtractor<Boolean>() {
      @Override
      public Boolean extractData(ResultSet rs) throws SQLException, DataAccessException {
        assertThat(rs.getInt("FreeUSDT")).isEqualTo(6);
        assertThat(rs.getInt("NetUSDT")).isEqualTo(4);
        assertThat(rs.getInt("LockedUSDT")).isEqualTo(2);
        assertThat(rs.getInt("BorrowedUSDT")).isEqualTo(4);
        // 3 * 40000
        assertThat(rs.getInt("TotalValue")).isEqualTo(120000);
        // 2 * 40000
        assertThat(rs.getInt("LiabilityValue")).isEqualTo(80000);
        assertThat(rs.getInt("NetValue")).isEqualTo(40000);
        assertThat(rs.getDouble("MarginLevel")).isEqualTo(1.5);
        assertThat(rs.getDouble("ReturnRate")).isEqualTo(224.017821);
        return true;
      }
    });
    assertThat(result).isTrue();
  }
}