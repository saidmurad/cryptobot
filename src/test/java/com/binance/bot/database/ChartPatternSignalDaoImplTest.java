package com.binance.bot.database;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
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
  String createTableStmt = "Create Table ChartPatternSignal(\n" +
      "    CoinPair TEXT NOT NULL,\n" +
      "    TimeFrame TEXT NOT NULL,\n" +
      "    TradeType TEXT NOT NULL,\n" +
      "    Pattern TEXT NOT NULL,\n" +
      "    PriceAtTimeOfSignal REAL NOT NULL,\n" +
      "    PriceRelatedToPattern REAL,\n" +
      "    TimeOfSignal TEXT NOT NULL,\n" +
      "    PriceTarget REAL NOT NULL,\n" +
      "    PriceTargetTime TEXT NOT NULL,\n" +
      "    ProfitPotentialPercent REAL NOT NULL,\n" +
      "    IsSignalOn INTEGER,\n" +
      "    VolumeAtSignalCandlestick INTEGER,\n" +
      "    VolumeAverage REAL,\n" +
      "    IsVolumeSurge INTEGER,\n" +
      "    TimeOfSignalInvalidation TEXT,\n" +
      "    ReasonForSignalInvalidation TEXT,\n" +
      "    PriceAtSignalTargetTime REAL,\n" +
      "    PriceAtTenCandlestickTime REAL,\n" +
      "    ProfitPercentAtTenCandlestickTime REAL,\n" +
      "    PriceBestReached REAL,\n" +
      "    PriceCurrent REAL,\n" +
      "    CurrentTime TEXT" +
      ");";

  @Before
  public void setUp() throws SQLException {
    new File("/home/kannanj/IdeaProjects/binance-java-api/testcryptobot.db").delete();
    SQLiteDataSource dataSource = new SQLiteDataSource();
    dataSource.setUrl("jdbc:sqlite:testcryptobot.db");
    dao = new ChartPatternSignalDaoImpl();
    dao.setDataSource(dataSource);
    Statement stmt = dataSource.getConnection().createStatement();
    stmt.execute(createTableStmt);
  }

  @Test
  public void testGetAllChartPatternSignals() {
    assertTrue(dao.insertChartPatternSignal(getChartPatternSignal().setIsSignalOn(true).build()));
    assertTrue(dao.insertChartPatternSignal(getChartPatternSignal().setCoinPair("Unrelated").setIsSignalOn(false).build()));
    List<ChartPatternSignal> chartPatternSignals = dao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES);
    assertTrue(chartPatternSignals.size() == 2);
    assertChartPatternAgainstInsertedValues(chartPatternSignals.get(0));
  }

  @Test
  public void testInvalidateChartPatternSignal() {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true).build();
    dao.insertChartPatternSignal(chartPatternSignal);
    ChartPatternSignal unrelatedChartPatternSignal = getChartPatternSignal()
        .setCoinPair("UNRELATED")
        .setIsSignalOn(true)
        .build();
    dao.insertChartPatternSignal(unrelatedChartPatternSignal);

    assertThat(dao.invalidateChartPatternSignal(chartPatternSignal, ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS))
        .isTrue();

    long currentTime = System.currentTimeMillis();
    ChartPatternSignal updatedChartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(updatedChartPatternSignal).isNotNull();
    assertThat(updatedChartPatternSignal.isSignalOn()).isFalse();
    assertThat(updatedChartPatternSignal.reasonForSignalInvalidation()).isEqualTo(ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS);
    assertThat(updatedChartPatternSignal.timeOfSignalInvalidation().getTime() - currentTime).isLessThan(5000L);
  }

  @Test
  public void testChatPatternSignalsThatReachedTenCandleStickTime_fifteenMinute_TimeFrame() {
    Date currentTime = new Date();
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("ETHUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(149)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.MINUTES.toMillis(150)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal);

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
    dao.insertChartPatternSignal(chartPatternSignal);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.HOUR)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(10)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal);

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
    dao.insertChartPatternSignal(chartPatternSignal);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(40)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal);

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
    dao.insertChartPatternSignal(chartPatternSignal);
    chartPatternSignal = getChartPatternSignal().setIsSignalOn(true)
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.FOUR_HOURS)
        .setTimeOfSignal(new Date(currentTime.getTime() - TimeUnit.HOURS.toMillis(40)))
        .build();
    dao.insertChartPatternSignal(chartPatternSignal);

    List<ChartPatternSignal> ret = dao.getChatPatternSignalsThatReachedTenCandleStickTime();
    assertThat(ret).hasSize(1);
    assertThat(ret.get(0).coinPair()).isEqualTo("BTCUSDT");
  }

  @Test
  public void testSetTenCandleStickTimePrice() {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().setIsSignalOn(true).build();
    dao.insertChartPatternSignal(chartPatternSignal);
    ChartPatternSignal unrelatedChartPatternSignal = getChartPatternSignal()
        .setTimeFrame(TimeFrame.HOUR)
        .setIsSignalOn(true)
        .build();
    dao.insertChartPatternSignal(unrelatedChartPatternSignal);

    assertThat(dao.setTenCandleStickTimePrice(chartPatternSignal, 100, 10))
        .isTrue();

    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignal.priceAtTenCandlestickTime()).isEqualTo(100.0);
    assertThat(chartPatternSignal.profitPercentAtTenCandlestickTime()).isEqualTo(10.0);
  }

  private void assertChartPatternAgainstInsertedValues(ChartPatternSignal chartPatternSignal) {
    assertEquals("ETHUSDT", chartPatternSignal.coinPair());
    assertEquals(TimeFrame.FIFTEEN_MINUTES, chartPatternSignal.timeFrame());
    assertEquals("Resistance", chartPatternSignal.pattern());
    assertEquals(TradeType.BUY, chartPatternSignal.tradeType());
    assertEquals(4000.0, chartPatternSignal.priceAtTimeOfSignal());
    assertEquals(new Date(currentTimeMillis), chartPatternSignal.timeOfSignal());
    assertEquals(6000.0, chartPatternSignal.priceTarget());
    assertEquals(new Date(currentTimeMillis + 360000), chartPatternSignal.priceTargetTime());
    assertEquals(2.3, chartPatternSignal.profitPotentialPercent());
  }

  private ChartPatternSignal.Builder getChartPatternSignal() {
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setTimeOfSignal(new Date(currentTimeMillis))
        .setPriceTarget(6000)
        .setPriceTargetTime(new Date(currentTimeMillis + 360000))
        .setProfitPotentialPercent(2.3);
  }
}