package com.binance.bot.database;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.MarginAccount;
import com.binance.api.client.domain.account.MarginAssetBalance;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.signalsuccessfailure.BookTickerPrices;
import com.binance.bot.tradesignals.*;
import com.binance.bot.trading.VolumeProfile;
import com.binance.bot.util.SetupDatasource;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.dao.DataAccessException;
import org.springframework.data.util.Pair;
import org.springframework.jdbc.core.ResultSetExtractor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class ChartPatternSignalDaoImplTest {
  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();

  private final long currentTimeMillis = System.currentTimeMillis();
  ChartPatternSignalDaoImpl dao;
  private VolumeProfile volProfile;
  public static final String createTableStmt = "Create Table ChartPatternSignal(\n" +
      "    CoinPair TEXT NOT NULL,\n" +
      "    TimeFrame TEXT NOT NULL,\n" +
      "    TradeType TEXT NOT NULL,\n" +
      "    Pattern TEXT NOT NULL,\n" +
      "    Attempt INTEGER NOT NULL,\n" +
      "    PriceAtTimeOfSignal REAL NOT NULL,\n" +
      "    PriceAtTimeOfSignalReal REAL,\n" +
      "    PriceRelatedToPattern REAL,\n" +
      "    TimeOfSignal TEXT NOT NULL,\n" +
      "    TimeOfInsertion TEXT,\n" +
      "    IsInsertedLate INTEGER,\n" +
      "    PriceTarget REAL NOT NULL,\n" +
      "    PriceTargetTime TEXT NOT NULL,\n" +
      "    ProfitPotentialPercent REAL NOT NULL,\n" +
      "    IsSignalOn INTEGER,\n" +
      "    NumTimesMissingInInput INTEGER,\n" +
      "    VolumeAtSignalCandlestick INTEGER,\n" +
      "    VolumeAverage REAL,\n" +
      "    IsVolumeSurge INTEGER,\n" +
      "    TimeOfSignalInvalidation TEXT,\n" +
      "    PriceAtTimeOfSignalInvalidation REAL,\n" +
      "    ProfitPercentAtTimeOfSignalInvalidation REAL,\n" +
      "    ReasonForSignalInvalidation TEXT,\n" +
      "    PriceAtSignalTargetTime REAL,\n" +
      "    ProfitPercentAtSignalTargetTime REAL,\n" +
      "    TenCandlestickTime TEXT,\n" +
      "    PriceAtTenCandlestickTime REAL,\n" +
      "    FailedToGetPriceAtTenCandlestickTime INTEGER,\n" +
      "    FailedToGetPriceAtSignalTargetTime INTEGER,\n" +
      "    ProfitPercentAtTenCandlestickTime REAL,\n" +
      "    PriceBestReached REAL,\n" +
      "    PriceCurrent REAL,\n" +
      "    CurrentTime TEXT, \n" +
      "    EntryOrderId INTEGER, \n" +
      "    EntryExecutedQty REAL, \n" +
      "    EntryAvgPrice REAL, \n" +
      "    EntryOrderStatus TEXT, \n" +
      "    ExitStopLossOrderId INTEGER, \n" +
      "    ExitStopLossOrderExecutedQty REAL, \n" +
      "    ExitStopLossOrderAvgPrice REAL, \n" +
      "    ExitStopLossOrderStatus TEXT, \n" +
      "    ExitMarketOrderId INTEGER, \n" +
      "    ExitMarketOrderExecutedQty REAL, \n" +
      "    ExitMarketOrderAvgPrice REAL, \n" +
      "    ExitMarketOrderStatus TEXT, \n" +
      "    Realized REAL, \n" +
      "    RealizedPercent REAL, \n" +
      "    UnRealized REAL, \n" +
      "    UnRealizedPercent REAL,\n" +
      "    IsPositionExited INTEGER, \n" +
      "    MaxLoss REAL,\n" +
      "    MaxLossPercent REAL,\n" +
      "    MaxLossTime TEXT,\n" +
      "    IsPriceTargetMet INTEGER,\n" +
      "    PriceTargetMetTime REAL," +
      "    TradeExitType TEXT,\n" +
      "    EntryEligibleBasedOnMACDSignalCrossOver INTEGER,\n" +
      "    CONSTRAINT chartpatternsignal_pk PRIMARY KEY (CoinPair, TimeFrame, TradeType, TimeOfSignal, Attempt)\n" +
      ");";
  public static final String CREATE_TABLE_STMT_BITCOIN_MONITORING = "create table BitcoinPriceMonitoring(" +
      "time TEXT not Null, timeFrame TEXT not Null, candleOpenPrice REAL, candleClosePrice REAL, " +
      "tradeTypeOverdone TEXT," +
      "Constraint PK Primary Key(time, timeFrame))";

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

  final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  /*
  create table ChartPatternSignalInvalidationEvents(CoinPair TEXT not null, TimeFrame text not null, TradeType text not null, Pattern Text not null, InvalidationEventTime TEXT not null, Event TEXT not null);
   */
  public ChartPatternSignalDaoImplTest() {
  }
  @Mock
  BinanceApiClientFactory mockBinanceApiClientFactory;
  @Mock
  BinanceApiMarginRestClient mockBinanceApiMarginRestClient;
  @Mock private BookTickerPrices mockBookTickerPrices;
  @Before
  public void setUp() throws SQLException {
    when(mockBinanceApiClientFactory.newMarginRestClient()).thenReturn(mockBinanceApiMarginRestClient);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    DataSource dataSource = SetupDatasource.getDataSource();
    dao = new ChartPatternSignalDaoImpl(mockBinanceApiClientFactory, mockBookTickerPrices);
    dao.setDataSource(dataSource);
    Statement stmt = dataSource.getConnection().createStatement();
    stmt.execute(createTableStmt);
    stmt.execute(CREATE_TABLE_STMT_BITCOIN_MONITORING);
    stmt.execute("create table ChartPatternSignalInvalidationEvents(CoinPair TEXT not null, TimeFrame text not null, TradeType text not null, Pattern Text not null, TimeOfSignal Text not null, InvalidationEventTime TEXT not null, Event TEXT not null);");
    Candlestick currentCandlestick = new Candlestick();
    currentCandlestick.setVolume("100.0000");
    volProfile = VolumeProfile.newBuilder()
        .setCurrentCandlestick(currentCandlestick)
        .setMinVol(49)
        .setMaxVol(51)
        .setIsVolAtleastMaintained(true)
        .setAvgVol(50)
        .setIsVolSurged(true)
        .setRecentCandlesticks(Lists.newArrayList(currentCandlestick))
        .build();
    stmt.execute(CREATE_CROSS_MARGIN_FUNDING_HISTORY_TABLE);
    stmt.execute(CREATE_CROSS_MARGIN_ACCOUNT_BALANCE_HISTORY_TABLE);
    dao.jdbcTemplate.update(String.format("insert into CrossMarginAccountFundingHistory(Time, Principal) values('%s',12345)",
        new Date()));
  }

  @Test
  public void testGetAllChartPatternSignals() {
    assertTrue(dao.insertChartPatternSignal(getChartPatternSignal().setIsSignalOn(true).build(), volProfile));
    assertTrue(dao.insertChartPatternSignal(getChartPatternSignal().setCoinPair("Unrelated").setIsSignalOn(false).build(), volProfile));
    List<ChartPatternSignal> chartPatternSignals = dao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES);
    assertTrue(chartPatternSignals.size() == 2);
    assertChartPatternAgainstInsertedValues(chartPatternSignals.get(0), volProfile);
  }

  @Test
  public void testInsertChartPatternSignal_nullVolumeProfile() {
    assertTrue(dao.insertChartPatternSignal(getChartPatternSignal().setIsSignalOn(true).build(), null));
    List<ChartPatternSignal> chartPatternSignals = dao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES);
    assertTrue(chartPatternSignals.size() == 1);
    assertThat(chartPatternSignals.get(0).isVolumeSurge()).isFalse();
    assertThat(chartPatternSignals.get(0).volumeAverage()).isEqualTo(0.0);
    assertThat(chartPatternSignals.get(0).volumeAtSignalCandlestick()).isEqualTo(0);
  }

  @Test
  public void testInvalidateChartPatternSignal() {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true).build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    ChartPatternSignal unrelatedChartPatternSignal = getChartPatternSignal()
        .setCoinPair("UNRELATED")
        .setIsSignalOn(true)
        .build();
    dao.insertChartPatternSignal(unrelatedChartPatternSignal, volProfile);

    final double priceAtTimeOfInvalidation = 5000;
    assertThat(dao.invalidateChartPatternSignal(chartPatternSignal, priceAtTimeOfInvalidation, ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS))
        .isTrue();

    long currentTime = System.currentTimeMillis();
    ChartPatternSignal updatedChartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(updatedChartPatternSignal).isNotNull();
    assertThat(updatedChartPatternSignal.isSignalOn()).isFalse();
    assertThat(updatedChartPatternSignal.reasonForSignalInvalidation()).isEqualTo(ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS);
    assertThat(updatedChartPatternSignal.timeOfSignalInvalidation().getTime() - currentTime).isLessThan(5000L);
    assertThat(updatedChartPatternSignal.priceAtTimeOfSignalInvalidation()).isEqualTo(priceAtTimeOfInvalidation);
    assertThat(updatedChartPatternSignal.profitPercentAtTimeOfSignalInvalidation()).isEqualTo(25.0);
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_markedAsFailed_notReturned() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(149)))
        .setFailedToGetPriceAtTenCandlestickTime(true)
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();

    assertThat(ret).hasSize(0);
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_fifteenMinute_TimeFrame_justExpired() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(149)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(150)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_fifteenMinute_TimeFrame_expiredWithinGracePeriod() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(161)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(155)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatLongSinceReachedTenCandleStickTime_fifteenMinute_TimeFrame() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(155)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(165)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatLongSinceReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  public void testChatPatternSignalsThatLongSinceReachedTenCandleStickTime_findsZeroRowsTooNotJustNull() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(165)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    dao.setTenCandleStickTimePrice(chartPatternSignal, 0, 0);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatLongSinceReachedTenCandleStickTime();

    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_Hour_TimeFrame() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.HOUR)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(9)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.HOUR)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(10)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_Hour_TimeFrame_expiredWithinGracePeriod() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.HOUR)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(11)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.HOUR)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(10) - TimeUnit.MINUTES.toMillis(5)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatLongSinceReachedTenCandleStickTime_Hour_TimeFrame() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.HOUR)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(11)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.HOUR)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(10) - TimeUnit.MINUTES.toMillis(5)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatLongSinceReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("ETHUSDT");
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_FourHour_TimeFrame() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(39)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(40)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_FourHour_TimeFrame_GracePeriod() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(41)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(40) - TimeUnit.MINUTES.toMillis(5)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatLongSinceReachedTenCandleStickTime_FourHour_TimeFrame_GracePeriod() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(41)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(40) - TimeUnit.MINUTES.toMillis(5)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatLongSinceReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("ETHUSDT");
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_Daily_TimeFrame() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.DAY)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.DAYS.toMillis(9)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.DAY)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.DAYS.toMillis(10)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_Daily_TimeFrame_GracePeriod() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.DAY)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.DAYS.toMillis(11)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.DAY)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.DAYS.toMillis(10) - TimeUnit.MINUTES.toMillis(5)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatJustReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatLongSinceReachedTenCandleStickTime_Daily_TimeFrame_GracePeriod() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.DAY)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.DAYS.toMillis(11)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.DAY)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.DAYS.toMillis(10) - TimeUnit.MINUTES.toMillis(5)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatLongSinceReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("ETHUSDT");
  }

  @Test
  public void testSetTenCandleStickTimePrice() {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true).build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    ChartPatternSignal unrelatedChartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.HOUR)
        .setIsSignalOn(true)
        .build();
    dao.insertChartPatternSignal(unrelatedChartPatternSignal, volProfile);

    assertThat(dao.setTenCandleStickTimePrice(chartPatternSignal, 100, 10))
        .isTrue();

    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignal.priceAtTenCandlestickTime()).isEqualTo(100.0);
    assertThat(chartPatternSignal.profitPercentAtTenCandlestickTime()).isEqualTo(10.0);
  }

  private void assertChartPatternAgainstInsertedValues(ChartPatternSignal chartPatternSignal, VolumeProfile volProfile) {
    assertEquals("ETHUSDT", chartPatternSignal.coinPair());
    assertEquals(TimeFrame.FIFTEEN_MINUTES, chartPatternSignal.timeFrame());
    assertEquals("Resistance", chartPatternSignal.pattern());
    assertEquals(TradeType.BUY, chartPatternSignal.tradeType());
    assertThat(chartPatternSignal.priceAtTimeOfSignal()).isEqualTo(4000.0);
    assertEquals(dateFormat.format(new Date(currentTimeMillis)), dateFormat.format(chartPatternSignal.timeOfSignal()));
    assertThat(chartPatternSignal.priceTarget()).isEqualTo(6000.0);
    assertEquals(dateFormat.format(new Date(currentTimeMillis + 360000)),
        dateFormat.format(chartPatternSignal.priceTargetTime()));
    assertThat(chartPatternSignal.profitPotentialPercent()).isEqualTo(2.3);

    assertThat(chartPatternSignal.volumeAtSignalCandlestick()).isEqualTo(100);
    assertThat(chartPatternSignal.volumeAverage()).isEqualTo(volProfile.avgVol());
    assertThat(chartPatternSignal.isVolumeSurge()).isEqualTo(volProfile.isVolSurged());
  }

  private ChartPatternSignal.Builder getChartPatternSignal() {
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setTimeOfSignal(new Date(currentTimeMillis))
        .setTimeOfInsertion(new Date(currentTimeMillis))
        .setIsInsertedLate(false)
        .setPriceTarget(6000)
        .setPriceTargetTime(new Date(currentTimeMillis + 360000))
        .setProfitPotentialPercent(2.3)
        .setIsSignalOn(true);
  }

  @Test
  public void testIncrementNumTimesMissingInInput() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    dao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignalInDB));

    assertThat(dao.getChartPattern(chartPatternSignalInDB).numTimesMissingInInput()).isEqualTo(1);
  }

  @Test
  public void testResetNumTimesMissingInInput() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setNumTimesMissingInInput(1).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    dao.resetNumTimesMissingInInput(Lists.newArrayList(chartPatternSignalInDB));

    assertThat(dao.getChartPattern(chartPatternSignalInDB).numTimesMissingInInput()).isEqualTo(0);
  }

  @Test
  public void testSetFailedToGetPriceAtTenCandlestickTime() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    assertThat(dao.getChartPattern(chartPatternSignalInDB).failedToGetPriceAtTenCandlestickTime()).isFalse();

    dao.failedToGetPriceAtTenCandlestickTime(chartPatternSignalInDB);

    assertThat(dao.getChartPattern(chartPatternSignalInDB).failedToGetPriceAtTenCandlestickTime()).isTrue();
  }

  @Test
  public void testGetChartPatternSignalsToInvalidate() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setCoinPair("ETHUSDT").setNumTimesMissingInInput(1).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    chartPatternSignalInDB = getChartPatternSignal().setCoinPair("BTCUSDT").setNumTimesMissingInInput(5).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    List<ChartPatternSignal> patternsToInvalidate = dao.getChartPatternSignalsToInvalidate();

    assertThat(patternsToInvalidate).hasSize(2);
  }

  @Test
  public void testSetTenCandlestickTime_beforePriceTargetTime_doesntNullifyTenCandlestickPrice() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal()
        // After 150 minutes of 10 candlestick time.
        .setPriceTargetTime(new Date(currentTimeMillis + TimeUnit.MINUTES.toMillis(151)))
        .setPriceAtTenCandlestickTime(200)
        .build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setTenCandleSticktime(chartPatternSignalInDB)).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(dateFormat.format(chartPatternSignalInDB.tenCandlestickTime().getTime())).isEqualTo(
        dateFormat.format(currentTimeMillis + TimeUnit.MINUTES.toMillis(150)));
    assertThat(chartPatternSignalInDB.priceAtTenCandlestickTime()).isEqualTo(200.0);
  }

  @Test
  public void testSetTenCandlestickTime_afterPriceTargetTime_nullifiesTenCandlestickPrice() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal()
        // Before the 150 minutes of 10 candlestick time.
        .setPriceTargetTime(new Date(currentTimeMillis + TimeUnit.MINUTES.toMillis(149)))
        .setPriceAtTenCandlestickTime(200)
        .build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setTenCandleSticktime(chartPatternSignalInDB)).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(dateFormat.format(chartPatternSignalInDB.tenCandlestickTime().getTime())).isEqualTo(
        dateFormat.format(currentTimeMillis + TimeUnit.MINUTES.toMillis(149)));
    assertThat(chartPatternSignalInDB.priceAtTenCandlestickTime()).isZero();
  }

  @Test
  public void testUpdateEntryOrder() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 1.1, 2.2, OrderStatus.FILLED))).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.entryOrder().orderId()).isEqualTo(1);
    assertThat(chartPatternSignalInDB.entryOrder().executedQty()).isEqualTo(1.1);
    assertThat(chartPatternSignalInDB.entryOrder().avgPrice()).isEqualTo(2.2);
    assertThat(chartPatternSignalInDB.entryOrder().status()).isEqualTo(OrderStatus.FILLED);
  }

  @Test
  public void testUpdateExitStopLossOrder_orderJustPlaced() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.BUY).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED))).isTrue();
    assertThat(dao.setExitStopLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().executedQty()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().avgPrice()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().status()).isEqualTo(OrderStatus.NEW);
    assertThat(chartPatternSignalInDB.realized()).isZero();
    assertThat(chartPatternSignalInDB.realizedPercent()).isZero();
    assertThat(chartPatternSignalInDB.unRealized()).isZero();
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isZero();
    assertThat(chartPatternSignalInDB.isPositionExited()).isFalse();
  }

  @Test
  public void testUpdateExitStopLimitOrder_tradeTypeBUY_fullyExitsPosition() throws ParseException {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.BUY).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED))).isTrue();
    assertThat(dao.setExitStopLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))).isTrue();
    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    ChartPatternSignal.Order exitStopLimitOrderStatus = ChartPatternSignal.Order.create(2L, 100, 205, OrderStatus.FILLED);

    assertThat(dao.updateExitStopLimitOrder(chartPatternSignalInDB, exitStopLimitOrderStatus)).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().executedQty()).isEqualTo(100.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().avgPrice()).isEqualTo(205.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(500.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(2.5);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.isPositionExited()).isTrue();
    assertThat(chartPatternSignalInDB.isSignalOn()).isFalse();
    assertThat(chartPatternSignalInDB.tradeExitType()).isEqualTo(TradeExitType.STOP_LOSS);
  }

  @Test
  public void testUpdateExitStopLimitOrder_tradeTypeBUY_partiallyExitsPosition() throws ParseException {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.BUY).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED))).isTrue();
    assertThat(dao.setExitStopLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))).isTrue();
    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    ChartPatternSignal.Order exitStopLimitOrderStatus = ChartPatternSignal.Order.create(
        2L, 50, 205, OrderStatus.PARTIALLY_FILLED);

    assertThat(dao.updateExitStopLimitOrder(chartPatternSignalInDB, exitStopLimitOrderStatus)).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().executedQty()).isEqualTo(50.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().avgPrice()).isEqualTo(205.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(250.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(1.25);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(250.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(1.25);
    assertThat(chartPatternSignalInDB.isPositionExited()).isFalse();
    assertThat(chartPatternSignalInDB.isSignalOn()).isTrue();
    assertThat(chartPatternSignalInDB.tradeExitType()).isEqualTo(TradeExitType.STOP_LOSS);
  }

  @Test
  public void testUpdateExitStopLimitOrder_tradeTypeSELL_fullyExitsPosition() throws ParseException {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.SELL).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED))).isTrue();
    assertThat(dao.setExitStopLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))).isTrue();
    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    ChartPatternSignal.Order exitStopLimitOrderStatus = ChartPatternSignal.Order.create(2L, 100, 205, OrderStatus.FILLED);

    assertThat(dao.updateExitStopLimitOrder(chartPatternSignalInDB, exitStopLimitOrderStatus)).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().executedQty()).isEqualTo(100.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().avgPrice()).isEqualTo(205.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(-500.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(-2.5);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.isPositionExited()).isTrue();
    assertThat(chartPatternSignalInDB.isSignalOn()).isFalse();
    assertThat(chartPatternSignalInDB.tradeExitType()).isEqualTo(TradeExitType.STOP_LOSS);
  }

  @Test
  public void testUpdateExitStopLimitOrder_tradeTypeSELL_partiallyExitsPosition() throws ParseException {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.SELL).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED))).isTrue();
    assertThat(dao.setExitStopLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW))).isTrue();
    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    ChartPatternSignal.Order exitStopLimitOrderStatus = ChartPatternSignal.Order.create(2L, 50, 205, (OrderStatus.PARTIALLY_FILLED));

    assertThat(dao.updateExitStopLimitOrder(chartPatternSignalInDB, exitStopLimitOrderStatus)).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().executedQty()).isEqualTo(50.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().avgPrice()).isEqualTo(205.0);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(-250.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(-1.25);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(-250.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(-1.25);
    assertThat(chartPatternSignalInDB.isPositionExited()).isFalse();
    assertThat(chartPatternSignalInDB.isSignalOn()).isTrue();
    assertThat(chartPatternSignalInDB.tradeExitType()).isEqualTo(TradeExitType.STOP_LOSS);
  }

  @Test
  public void testUpdateExitMarketOrder_tradeTypeBUY_noPreExistingPnL() {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setTradeType(TradeType.BUY)
        // Fictious preexisting values for relaized and unrealized, not important how they got here for the test.
        .setRealized(0.0)
        .setRealizedPercent(0.0)
        .setUnRealized(0.0)
        .setUnRealizedPercent(0.0)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 100.0, 200.0, OrderStatus.FILLED))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    assertThat(dao.setExitMarketOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(2, 50, 205, OrderStatus.PARTIALLY_FILLED),
        TradeExitType.TARGET_TIME_PASSED)).isTrue();

    ChartPatternSignal chartPatternSignalInDB = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignalInDB.exitMarketOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitMarketOrder().executedQty()).isEqualTo(50.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().avgPrice()).isEqualTo(205.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(chartPatternSignalInDB.isSignalOn()).isTrue();
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(250.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(1.25);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.isPositionExited()).isFalse();
    assertThat(chartPatternSignalInDB.tradeExitType()).isEqualTo(TradeExitType.TARGET_TIME_PASSED);
  }

  @Test
  public void testUpdateExitMarketOrder_tradeTypeBUY_partialExit_previousPartiallyExecutedStopLimitOrder() {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setTradeType(TradeType.BUY)
        // Fictious preexisting values for relaized and unrealized, not important how they got here for the test.
        .setRealized(10.0)
        .setRealizedPercent(1.0)
        .setUnRealized(15.0)
        .setUnRealizedPercent(2.0)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 100.0, 200.0, OrderStatus.FILLED))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    assertThat(dao.setExitMarketOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(2, 50, 205, OrderStatus.PARTIALLY_FILLED),
        TradeExitType.TARGET_TIME_PASSED)).isTrue();

    ChartPatternSignal chartPatternSignalInDB = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignalInDB.exitMarketOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitMarketOrder().executedQty()).isEqualTo(50.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().avgPrice()).isEqualTo(205.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().status()).isEqualTo(OrderStatus.PARTIALLY_FILLED);
    assertThat(chartPatternSignalInDB.isSignalOn()).isTrue();
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(260.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(1.3);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.isPositionExited()).isFalse();
    assertThat(chartPatternSignalInDB.tradeExitType()).isEqualTo(TradeExitType.TARGET_TIME_PASSED);
  }

  @Test
  public void testUpdateExitMarketOrder_preExistingPnl_additive_tradeTypeSELL() {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setTradeType(TradeType.SELL)
        // Fictious preexisting values for relaized and unrealized, not important how they got here for the test.
        .setRealized(10.0)
        .setRealizedPercent(1.0)
        .setUnRealized(15.0)
        .setUnRealizedPercent(2.0)
        .setEntryOrder(ChartPatternSignal.Order.create(1, 100.0, 200.0, OrderStatus.FILLED))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);

    assertThat(dao.setExitMarketOrder(chartPatternSignal,
        ChartPatternSignal.Order.create(2, 50, 195, OrderStatus.FILLED), TradeExitType.TARGET_TIME_PASSED)).isTrue();

    ChartPatternSignal chartPatternSignalInDB = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignalInDB.exitMarketOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitMarketOrder().executedQty()).isEqualTo(50.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().avgPrice()).isEqualTo(195.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignalInDB.isSignalOn()).isFalse();
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(260.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(1.3);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.isPositionExited()).isTrue();
    assertThat(chartPatternSignalInDB.tradeExitType()).isEqualTo(TradeExitType.TARGET_TIME_PASSED);
  }

  @Test
  public void testGetActivePositions() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setTradeType(TradeType.BUY).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 1.1, 2.2, OrderStatus.FILLED));
    chartPatternSignalInDB = getChartPatternSignal().setTimeFrame(TimeFrame.HOUR)
        .setTradeType(TradeType.BUY).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 1.2, 2.2, OrderStatus.FILLED));

    List<ChartPatternSignal> activePositions = dao.getChartPatternsWithActiveTradePositions(TimeFrame.FIFTEEN_MINUTES, TradeType.BUY);

    assertThat(activePositions).hasSize(1);
    assertThat(activePositions.get(0).entryOrder().executedQty()).isEqualTo(1.1);
  }

  @Test
  public void testUpdateMaxLossAndTargetMetValues() {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    Date currDate = new Date(currentTimeMillis);

    ChartPatternSignal updatedChartPatternSignal = ChartPatternSignal.newBuilder()
        .copy(chartPatternSignal)
        .setMaxLoss(10.0)
        .setMaxLossPercent(1.0)
        .setMaxLossTime(currDate)
        .setIsPriceTargetMet(true)
        .setPriceTargetMetTime(DateUtils.addHours(currDate, 1))
        .build();

    dao.updateMaxLossAndTargetMetValues(updatedChartPatternSignal);

    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignal.maxLoss()).isEqualTo(10.0);
    assertThat(chartPatternSignal.maxLossPercent()).isEqualTo(1.0);
    assertThat(dateFormat.format(chartPatternSignal.maxLossTime().getTime())).isEqualTo(dateFormat.format(currentTimeMillis));
    assertThat(chartPatternSignal.isPriceTargetMet()).isTrue();
    assertThat(dateFormat.format(chartPatternSignal.priceTargetMetTime().getTime())).isEqualTo(
        dateFormat.format(currentTimeMillis + 3600000));
  }

  @Test
  public void testUpdateMaxLossAndTargetMetValues_emptyValues() {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    Date currDate = new Date(currentTimeMillis);

    dao.updateMaxLossAndTargetMetValues(chartPatternSignal);

    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignal.maxLoss()).isEqualTo(0.0);
    assertThat(chartPatternSignal.maxLossPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignal.maxLossTime()).isNull();
    assertThat(chartPatternSignal.isPriceTargetMet()).isNull();
    assertThat(chartPatternSignal.priceTargetMetTime()).isNull();
  }

  @Test
  public void testGetAllChartPatternsNeedingMaxLossCalculated() {
    ChartPatternSignal yesterdayChartPattern = getChartPatternSignal()
        .setCoinPair("ETHUSDT")
        .setPriceTargetTime(DateUtils.addMinutes(new Date(), -1))
        .build();
    ChartPatternSignal todayChartPattern = getChartPatternSignal()
        .setCoinPair("BTCUSDT")
        .setPriceTargetTime(DateUtils.addMinutes(new Date(), 1))
        .build();
    dao.insertChartPatternSignal(yesterdayChartPattern, volProfile);
    dao.insertChartPatternSignal(todayChartPattern, volProfile);

    List<ChartPatternSignal> patterns = dao.getAllChartPatternsNeedingMaxLossCalculated();

    assertThat(patterns).hasSize(1);
    assertThat(patterns.get(0).coinPair()).isEqualTo("ETHUSDT");
  }

  private final TimeFrame[] timeFrames = {TimeFrame.FIFTEEN_MINUTES, TimeFrame.HOUR, TimeFrame.FOUR_HOURS, TimeFrame.DAY};
  private final TradeType tradeTypes[] = {TradeType.BUY, TradeType.SELL};

  @Test
  public void testGetChartPatternSignalsToPlaceTrade_usesAltfinsSignalInvalidations_findsHighestAttemptCountsOnly() {
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 2; j++) {
        Date currTime = new Date();
        ChartPatternSignal chartPatternSignalWithEntryOrder = getChartPatternSignal()
            .setTimeFrame(timeFrames[i])
            .setTradeType(tradeTypes[j])
            .setTimeOfSignal(DateUtils.addMinutes(currTime, -1))
            .build();
        dao.insertChartPatternSignal(chartPatternSignalWithEntryOrder, volProfile);
        dao.setEntryOrder(chartPatternSignalWithEntryOrder, ChartPatternSignal.Order.create(1,1.0,2.0, OrderStatus.FILLED));
        ChartPatternSignal chartPatternSignalWithoutEntryOrder1 = getChartPatternSignal()
            .setTimeFrame(timeFrames[i])
            .setTradeType(tradeTypes[j])
            .setTimeOfSignal(DateUtils.addMinutes(currTime, -2))
            .setAttempt(1)
            .build();
        dao.insertChartPatternSignal(chartPatternSignalWithoutEntryOrder1, volProfile);
        ChartPatternSignal chartPatternSignalWithoutEntryOrder2 = getChartPatternSignal()
            .setTimeFrame(timeFrames[i])
            .setTradeType(tradeTypes[j])
            .setTimeOfSignal(DateUtils.addMinutes(currTime, -2))
            .setAttempt(2)
            .build();
        dao.insertChartPatternSignal(chartPatternSignalWithoutEntryOrder2, volProfile);
        ChartPatternSignal hypothetical = getChartPatternSignal()
            .setTimeFrame(timeFrames[i])
            .setTradeType(tradeTypes[j])
            .setTimeOfSignal(DateUtils.addMinutes(currTime, -121))
            .setAttempt(3)
            .build();
        dao.insertChartPatternSignal(hypothetical, volProfile);
      }
    }

    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 2; j++) {
        List<ChartPatternSignal> chartPatternSignalsToPlaceTrade = dao.getChartPatternSignalsToPlaceTrade(
            timeFrames[i], tradeTypes[j], true);
        assertThat(chartPatternSignalsToPlaceTrade).hasSize(1);
        assertThat(chartPatternSignalsToPlaceTrade.get(0).timeFrame()).isEqualTo(timeFrames[i]);
        assertThat(chartPatternSignalsToPlaceTrade.get(0).tradeType()).isEqualTo(tradeTypes[j]);
        assertThat(chartPatternSignalsToPlaceTrade.get(0).attempt()).isEqualTo(2);
        assertThat(chartPatternSignalsToPlaceTrade.get(0).entryOrder()).isNull();
      }
    }
  }

  @Test
  public void testGetChartPatternSignalsToPlaceTrade_doesntUseAltfinsSignalInvalidations_findsFirstAttemptsOnly() {
    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 2; j++) {
        Date currTime = new Date();
        ChartPatternSignal chartPatternSignalWithEntryOrder = getChartPatternSignal()
            .setTimeFrame(timeFrames[i])
            .setTradeType(tradeTypes[j])
            .setTimeOfSignal(DateUtils.addMinutes(currTime, -1))
            .build();
        dao.insertChartPatternSignal(chartPatternSignalWithEntryOrder, volProfile);
        dao.setEntryOrder(chartPatternSignalWithEntryOrder, ChartPatternSignal.Order.create(1,1.0,2.0, OrderStatus.FILLED));
        ChartPatternSignal chartPatternSignalWithoutEntryOrder1 = getChartPatternSignal()
            .setTimeFrame(timeFrames[i])
            .setTradeType(tradeTypes[j])
            .setTimeOfSignal(DateUtils.addMinutes(currTime, -2))
            .setAttempt(1)
            .build();
        dao.insertChartPatternSignal(chartPatternSignalWithoutEntryOrder1, volProfile);
        ChartPatternSignal chartPatternSignalWithoutEntryOrder2 = getChartPatternSignal()
            .setTimeFrame(timeFrames[i])
            .setTradeType(tradeTypes[j])
            .setTimeOfSignal(DateUtils.addMinutes(currTime, -2))
            .setAttempt(2)
            .build();
        dao.insertChartPatternSignal(chartPatternSignalWithoutEntryOrder2, volProfile);
        ChartPatternSignal hypothetical = getChartPatternSignal()
            .setTimeFrame(timeFrames[i])
            .setTradeType(tradeTypes[j])
            .setTimeOfSignal(DateUtils.addMinutes(currTime, -121))
            .setAttempt(3)
            .build();
        dao.insertChartPatternSignal(hypothetical, volProfile);
      }
    }

    for (int i = 0; i < 4; i++) {
      for (int j = 0; j < 2; j++) {
        List<ChartPatternSignal> chartPatternSignalsToPlaceTrade = dao.getChartPatternSignalsToPlaceTrade(
            timeFrames[i], tradeTypes[j], false);
        assertThat(chartPatternSignalsToPlaceTrade).hasSize(1);
        assertThat(chartPatternSignalsToPlaceTrade.get(0).timeFrame()).isEqualTo(timeFrames[i]);
        assertThat(chartPatternSignalsToPlaceTrade.get(0).tradeType()).isEqualTo(tradeTypes[j]);
        assertThat(chartPatternSignalsToPlaceTrade.get(0).attempt()).isEqualTo(1);
        assertThat(chartPatternSignalsToPlaceTrade.get(0).entryOrder()).isNull();
      }
    }
  }

  @Test
  public void testCancelStopLimitOrder() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.BUY).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED));
    dao.setExitStopLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 0, 0, OrderStatus.NEW));

    dao.cancelStopLimitOrder(chartPatternSignalInDB);

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitStopLimitOrder().status()).isEqualTo(OrderStatus.CANCELED);
  }

  @Test
  public void testInsertBitconPriceMonitoringTradeTypeOverdone() {
    Date time = new Date();
    dao.insertOverdoneTradeType(time, TimeFrame.FIFTEEN_MINUTES, TradeType.BUY);

    String ret = dao.jdbcTemplate.queryForObject(String.format("select TradeTypeOverdone from BitcoinPriceMonitoring " +
        "where Time='%s' " +
        "and TimeFrame='FIFTEEN_MINUTES'", dateFormat.format(time)), new Object[]{}, (rs, rowNum) -> {
      return rs.getString("TradeTypeOverdone");
    });
    assertThat(ret).isEqualTo("BUY");
  }

  @Test
  public void testUpdateBitconPriceMonitoringTradeTypeOverdone_whenRowAlreadyExists() {
    Date time = new Date();
    dao.insertOverdoneTradeType(time, TimeFrame.FIFTEEN_MINUTES, TradeType.BUY);

    dao.insertOverdoneTradeType(time, TimeFrame.FIFTEEN_MINUTES, TradeType.SELL);
    String ret = dao.jdbcTemplate.queryForObject(String.format("select TradeTypeOverdone from BitcoinPriceMonitoring " +
        "where Time='%s' " +
        "and TimeFrame='FIFTEEN_MINUTES'", dateFormat.format(time)), new Object[]{}, (rs, rowNum) -> {
      return rs.getString("TradeTypeOverdone");
    });
    assertThat(ret).isEqualTo("SELL");
  }

  @Test
  public void testInsertIntoBitconPriceMonitoring_priceRow() {
    Date time = new Date();

    dao.insertBitcoinPrice(TimeFrame.FIFTEEN_MINUTES, time.getTime(), 1.0, 2.0);
    Pair<Double, Double> prices = dao.jdbcTemplate.queryForObject(String.format("select CandleOpenPrice, CandleClosePrice " +
        "from BitcoinPriceMonitoring " +
        "where Time='%s' " +
        "and TimeFrame='FIFTEEN_MINUTES'", dateFormat.format(time)), new Object[]{}, (rs, rowNum) -> {
      return Pair.of(rs.getDouble("CandleOpenPrice"), rs.getDouble("CandleClosePrice"));
    });

    assertThat(prices.getFirst()).isEqualTo(1.0);
    assertThat(prices.getSecond()).isEqualTo(2.0);
  }

  @Test
  public void testInsertIntoBitconPriceMonitoring_priceRowAlreadyExists_IsUpdated() {
    Date time = new Date();

    dao.insertBitcoinPrice(TimeFrame.FIFTEEN_MINUTES, time.getTime(), 1.0, 2.0);
    dao.insertBitcoinPrice(TimeFrame.FIFTEEN_MINUTES, time.getTime(), 3.0, 4.0);
    Pair<Double, Double> prices = dao.jdbcTemplate.queryForObject(String.format("select CandleOpenPrice, CandleClosePrice " +
        "from BitcoinPriceMonitoring " +
        "where Time='%s' " +
        "and TimeFrame='FIFTEEN_MINUTES'", dateFormat.format(time)), new Object[]{}, (rs, rowNum) -> {
      return Pair.of(rs.getDouble("CandleOpenPrice"), rs.getDouble("CandleClosePrice"));
    });

    assertThat(prices.getFirst()).isEqualTo(3.0);
    assertThat(prices.getSecond()).isEqualTo(4.0);
  }

  @Test
  public void testTradeTypeOverdone_query() throws ParseException {
    Date date = dateFormat.parse("2022-01-02 11:15");
    dao.insertOverdoneTradeType(date, TimeFrame.FIFTEEN_MINUTES, TradeType.SELL);

    assertThat(dao.getOverdoneTradeType(date, TimeFrame.FIFTEEN_MINUTES)).isEqualTo(TradeType.SELL);
  }

  @Test
  public void testRoundFifteenMinute() throws ParseException {
    Date date = dateFormat.parse("2022-01-02 21:47");
    Date roundedDate = dao.getCandlestickStart(date, TimeFrame.FIFTEEN_MINUTES);
    assertThat(roundedDate).isEqualTo(dateFormat.parse("2022-01-02 21:45"));
  }

  @Test
  public void testRoundHour() throws ParseException {
    Date date = dateFormat.parse("2022-01-02 23:47");
    Date roundedDate = dao.getCandlestickStart(date, TimeFrame.HOUR);
    assertThat(roundedDate).isEqualTo(dateFormat.parse("2022-01-02 23:00"));
  }

  @Test
  public void testRoundFourHour() throws ParseException {
    Date date = dateFormat.parse("2022-01-02 23:17");
    Date roundedDate = dao.getCandlestickStart(date, TimeFrame.FOUR_HOURS);
    assertThat(roundedDate).isEqualTo(dateFormat.parse("2022-01-02 20:00"));
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

    dao.writeAccountBalanceToDB();

    Boolean result =
        dao.jdbcTemplate.query("select * from CrossMarginAccountBalanceHistory",
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