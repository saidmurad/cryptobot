package com.binance.bot.database;

import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.VolumeProfile;
import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

public class ChartPatternSignalDaoImplTest extends TestCase {

  private final long currentTimeMillis = 1642258800000L; // 2022-01-15 15:00
  ChartPatternSignalDaoImpl dao;
  private VolumeProfile volProfile;
  String createTableStmt = "Create Table ChartPatternSignal(\n" +
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
      "    ExitLimitOrderId INTEGER, \n" +
      "    ExitLimitOrderExecutedQty REAL, \n" +
      "    ExitLimitOrderAvgPrice REAL, \n" +
      "    ExitLimitOrderStatus TEXT, \n" +
      "    ExitMarketOrderId INTEGER, \n" +
      "    ExitMarketOrderExecutedQty REAL, \n" +
      "    ExitMarketOrderAvgPrice REAL, \n" +
      "    ExitMarketOrderStatus TEXT, \n" +
      "    Realized REAL, \n" +
      "    RealizedPercent REAL, \n" +
      "    UnRealized REAL, \n" +
      "    UnRealizedPercent REAL,\n" +
      "    IsPositionExited INTEGER \n" +
      ");";
/*
create table ChartPatternSignal2 as select * from ChartPatternSignal;
drop table ChartPatternSignal;
create table ChartPatternSignal as select * from ChartPatternSignal2;
 */
  /*
  create table ChartPatternSignalInvalidationEvents(CoinPair TEXT not null, TimeFrame text not null, TradeType text not null, Pattern Text not null, InvalidationEventTime TEXT not null, Event TEXT not null);
   */
  public ChartPatternSignalDaoImplTest() {
  }

  @Before
  public void setUp() throws SQLException {
    new File("/home/kannanj/IdeaProjects/binance-java-api/testcryptobot.db").delete();
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:testcryptobot.db");
    dao = new ChartPatternSignalDaoImpl();
    dao.setDataSource(dataSource);
    Statement stmt = dataSource.getConnection().createStatement();
    stmt.execute(createTableStmt);
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

    final double priceAtTimeOfInvalidation = 100.01;
    assertThat(dao.invalidateChartPatternSignal(chartPatternSignal, priceAtTimeOfInvalidation, ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS))
        .isTrue();

    long currentTime = System.currentTimeMillis();
    ChartPatternSignal updatedChartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(updatedChartPatternSignal).isNotNull();
    assertThat(updatedChartPatternSignal.isSignalOn()).isFalse();
    assertThat(updatedChartPatternSignal.reasonForSignalInvalidation()).isEqualTo(ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS);
    assertThat(updatedChartPatternSignal.timeOfSignalInvalidation().getTime() - currentTime).isLessThan(5000L);
    assertThat(updatedChartPatternSignal.priceAtTimeOfSignalInvalidation()).isEqualTo(priceAtTimeOfInvalidation);
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
    assertEquals(4000.0, chartPatternSignal.priceAtTimeOfSignal());
    assertEquals(new Date(currentTimeMillis), chartPatternSignal.timeOfSignal());
    assertEquals(6000.0, chartPatternSignal.priceTarget());
    assertEquals(new Date(currentTimeMillis + 360000), chartPatternSignal.priceTargetTime());
    assertEquals(2.3, chartPatternSignal.profitPotentialPercent());

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

  public void testIncrementNumTimesMissingInInput() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    dao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignalInDB));

    assertThat(dao.getChartPattern(chartPatternSignalInDB).numTimesMissingInInput()).isEqualTo(1);
  }

  public void testResetNumTimesMissingInInput() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setNumTimesMissingInInput(1).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    dao.resetNumTimesMissingInInput(Lists.newArrayList(chartPatternSignalInDB));

    assertThat(dao.getChartPattern(chartPatternSignalInDB).numTimesMissingInInput()).isEqualTo(0);
  }

  public void testSetFailedToGetPriceAtTenCandlestickTime() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    assertThat(dao.getChartPattern(chartPatternSignalInDB).failedToGetPriceAtTenCandlestickTime()).isFalse();

    dao.failedToGetPriceAtTenCandlestickTime(chartPatternSignalInDB);

    assertThat(dao.getChartPattern(chartPatternSignalInDB).failedToGetPriceAtTenCandlestickTime()).isTrue();
  }

  public void testGetChartPatternSignalsToInvalidate() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setCoinPair("ETHUSDT").setNumTimesMissingInInput(1).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);
    chartPatternSignalInDB = getChartPatternSignal().setCoinPair("BTCUSDT").setNumTimesMissingInInput(5).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    List<ChartPatternSignal> patternsToInvalidate = dao.getChartPatternSignalsToInvalidate();

    assertThat(patternsToInvalidate).hasSize(2);
  }

  public void testSetTenCandlestickTime_beforePriceTargetTime_doesntNullifyTenCandlestickPrice() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal()
        // After 150 minutes of 10 candlestick time.
        .setPriceTargetTime(new Date(currentTimeMillis + TimeUnit.MINUTES.toMillis(151)))
        .setPriceAtTenCandlestickTime(200)
        .build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setTenCandleSticktime(chartPatternSignalInDB)).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.tenCandlestickTime().getTime()).isEqualTo(currentTimeMillis + TimeUnit.MINUTES.toMillis(150));
    assertThat(chartPatternSignalInDB.priceAtTenCandlestickTime()).isEqualTo(200.0);
  }

  public void testSetTenCandlestickTime_afterPriceTargetTime_nullifiesTenCandlestickPrice() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal()
        // Before the 150 minutes of 10 candlestick time.
        .setPriceTargetTime(new Date(currentTimeMillis + TimeUnit.MINUTES.toMillis(149)))
        .setPriceAtTenCandlestickTime(200)
        .build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setTenCandleSticktime(chartPatternSignalInDB)).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.tenCandlestickTime().getTime()).isEqualTo(currentTimeMillis + TimeUnit.MINUTES.toMillis(149));
    assertThat(chartPatternSignalInDB.priceAtTenCandlestickTime()).isZero();
  }

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

  public void testUpdateExitLimitOrder_tradeTypeBUY_fullyExitsPosition() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.BUY).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED))).isTrue();
    assertThat(dao.setExitLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 100, 205, OrderStatus.FILLED))).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitLimitOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitLimitOrder().executedQty()).isEqualTo(100.0);
    assertThat(chartPatternSignalInDB.exitLimitOrder().avgPrice()).isEqualTo(205.0);
    assertThat(chartPatternSignalInDB.exitLimitOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(500.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(2.5);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.isPositionExited()).isTrue();
  }

  public void testUpdateExitLimitOrder_tradeTypeBUY_partiallyExitsPosition() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.BUY).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED))).isTrue();
    assertThat(dao.setExitLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 50, 205, OrderStatus.FILLED))).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitLimitOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitLimitOrder().executedQty()).isEqualTo(50.0);
    assertThat(chartPatternSignalInDB.exitLimitOrder().avgPrice()).isEqualTo(205.0);
    assertThat(chartPatternSignalInDB.exitLimitOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(250.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(1.25);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(250.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(1.25);
    assertThat(chartPatternSignalInDB.isPositionExited()).isFalse();
  }

  public void testUpdateExitLimitOrder_tradeTypeSELL_fullyExitsPosition() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.SELL).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED))).isTrue();
    assertThat(dao.setExitLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 100, 195, OrderStatus.FILLED))).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitLimitOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitLimitOrder().executedQty()).isEqualTo(100.0);
    assertThat(chartPatternSignalInDB.exitLimitOrder().avgPrice()).isEqualTo(195.0);
    assertThat(chartPatternSignalInDB.exitLimitOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(500.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(2.5);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.isPositionExited()).isTrue();
  }

  public void testUpdateExitLimitOrder_tradeTypeSELL_partiallyExitsPosition() {
    ChartPatternSignal chartPatternSignalInDB = getChartPatternSignal().setTradeType(TradeType.SELL).build();
    dao.insertChartPatternSignal(chartPatternSignalInDB, volProfile);

    assertThat(dao.setEntryOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(1, 100, 200, OrderStatus.FILLED))).isTrue();
    assertThat(dao.setExitLimitOrder(chartPatternSignalInDB,
        ChartPatternSignal.Order.create(2, 50, 195, OrderStatus.FILLED))).isTrue();

    chartPatternSignalInDB = dao.getChartPattern(chartPatternSignalInDB);
    assertThat(chartPatternSignalInDB.exitLimitOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitLimitOrder().executedQty()).isEqualTo(50.0);
    assertThat(chartPatternSignalInDB.exitLimitOrder().avgPrice()).isEqualTo(195.0);
    assertThat(chartPatternSignalInDB.exitLimitOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(250.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(1.25);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(250.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(1.25);
    assertThat(chartPatternSignalInDB.isPositionExited()).isFalse();
  }

  public void testUpdateExitMarketOrder_tradeTypeBUY() {
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
        ChartPatternSignal.Order.create(2, 50, 205, OrderStatus.FILLED))).isTrue();

    ChartPatternSignal chartPatternSignalInDB = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignalInDB.exitMarketOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitMarketOrder().executedQty()).isEqualTo(50.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().avgPrice()).isEqualTo(205.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(260.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(2.25);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.isPositionExited()).isTrue();
  }

  public void testUpdateExitMarketOrder_tradeTypeSELL() {
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
        ChartPatternSignal.Order.create(2, 50, 195, OrderStatus.FILLED))).isTrue();

    ChartPatternSignal chartPatternSignalInDB = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignalInDB.exitMarketOrder().orderId()).isEqualTo(2);
    assertThat(chartPatternSignalInDB.exitMarketOrder().executedQty()).isEqualTo(50.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().avgPrice()).isEqualTo(195.0);
    assertThat(chartPatternSignalInDB.exitMarketOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignalInDB.realized()).isEqualTo(260.0);
    assertThat(chartPatternSignalInDB.realizedPercent()).isEqualTo(2.25);
    assertThat(chartPatternSignalInDB.unRealized()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.unRealizedPercent()).isEqualTo(0.0);
    assertThat(chartPatternSignalInDB.isPositionExited()).isTrue();
  }

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
  }