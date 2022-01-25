package com.binance.bot.database;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.bot.altfins.AltfinPatternsReader;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.GetVolumeProfile;
import com.binance.bot.trading.VolumeProfile;
import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
      "    PriceAtTimeOfSignal REAL NOT NULL,\n" +
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
      "    PriceAtTenCandlestickTime REAL,\n" +
      "    FailedToGetPriceAtTenCandlestickTime INTEGER,\n" +
      "    ProfitPercentAtTenCandlestickTime REAL,\n" +
      "    PriceBestReached REAL,\n" +
      "    PriceCurrent REAL,\n" +
      "    CurrentTime TEXT" +
      ");";

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

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatReachedTenCandleStickTime();

    assertThat(ret).hasSize(0);
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_fifteenMinute_TimeFrame() {
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

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatReachedTenCandleStickTime();
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

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
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

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_Daily_TimeFrame() {
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

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
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

    assertThat(patternsToInvalidate).hasSize(1);
    assertThat(patternsToInvalidate.get(0)).isEqualTo(chartPatternSignalInDB);
  }
}