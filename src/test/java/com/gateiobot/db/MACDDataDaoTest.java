package com.gateiobot.db;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.util.CreateCryptobotDB;
import com.binance.bot.util.CreateDatasource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class MACDDataDaoTest {
  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Mock private Clock mockClock;
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final MACDDataDao macdDataDao = new MACDDataDao();

  @Before
  public void setUp() throws IOException {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    DataSource dataSource = CreateDatasource.createDataSource();
    macdDataDao.jdbcTemplate = new JdbcTemplate(dataSource);
    CreateCryptobotDB.createCryptobotDB(dataSource);
    macdDataDao.setMockClock(mockClock);
  }

  @Test
  public void getMACDDataBetweenTimes() throws ParseException {
    MACDData macd1 = new MACDData();
    macd1.coinPair = "BTC_USDT";
    macd1.timeFrame = TimeFrame.FIFTEEN_MINUTES;
    macd1.time = dateFormat.parse("2022-4-22 16:45");
    macd1.trendType = TrendType.NA;
    macd1.histogramTrendType = HistogramTrendType.NA;
    macdDataDao.insert(macd1);
    MACDData macd2 = new MACDData();
    macd2.coinPair = "BTC_USDT";
    macd2.timeFrame = TimeFrame.FIFTEEN_MINUTES;
    macd2.time = dateFormat.parse("2022-4-22 17:15");
    macd2.trendType = TrendType.NA;
    macd2.histogramTrendType = HistogramTrendType.NA;
    macdDataDao.insert(macd2);
    MACDData macd3 = new MACDData();
    macd3.coinPair = "BTC_USDT";
    macd3.timeFrame = TimeFrame.FIFTEEN_MINUTES;
    macd3.time = dateFormat.parse("2022-04-22 17:45");
    macd3.trendType = TrendType.NA;
    macd3.histogramTrendType = HistogramTrendType.NA;
    macdDataDao.insert(macd3);
    Date signalTime = dateFormat.parse("2022-04-22 17:00");
    Date targetTime = dateFormat.parse("2022-04-22 17:30");

    List<MACDData> macdDatas = macdDataDao.getMACDDataBetweenTimes("BTC_USDT", TimeFrame.FIFTEEN_MINUTES, signalTime, targetTime );

    assertTrue(macdDatas.size() == 1);
    assertThat(macdDatas.get(0).time).isEqualTo(macd2.time);
  }
  @Test
  public void lastCandlestickMACD_queryingWithinImmediateNextCandle_isReturned() throws ParseException {
    MACDData macd = new MACDData();
    macd.coinPair = "BTC_USDT";
    macd.timeFrame = TimeFrame.FIFTEEN_MINUTES;
    macd.time = dateFormat.parse("2022-04-22 18:00");
    macd.trendType = TrendType.NA;
    macd.histogramTrendType = HistogramTrendType.NA;
    macdDataDao.insert(macd);
    Date currTime = dateFormat.parse("2022-04-22 18:29");
    when(mockClock.millis()).thenReturn(currTime.getTime());

    MACDData lastMacdData = macdDataDao.getLastMACDData("BTC_USDT", TimeFrame.FIFTEEN_MINUTES);

    assertThat(lastMacdData.time).isEqualTo(macd.time);
  }

  @Test
  public void lastCandlestickMACD_queryingAfterImmediateNextCandle_isNotReturned() throws ParseException {
    MACDData macd = new MACDData();
    macd.coinPair = "BTC_USDT";
    macd.timeFrame = TimeFrame.FIFTEEN_MINUTES;
    macd.time = dateFormat.parse("2022-04-22 18:00");
    macd.trendType = TrendType.NA;
    macd.histogramTrendType = HistogramTrendType.NA;
    macdDataDao.insert(macd);
    Date currTime = dateFormat.parse("2022-04-22 18:30");
    when(mockClock.millis()).thenReturn(currTime.getTime());

    MACDData lastMacdData = macdDataDao.getLastMACDData("BTC_USDT", TimeFrame.FIFTEEN_MINUTES);

    assertThat(lastMacdData).isNull();
  }

  @Test
  public void getStopLossLevelBasedOnBreakoutCandlestick() throws ParseException {
    MACDData macd = new MACDData();
    macd.coinPair = "BTC_USDT";
    macd.timeFrame = TimeFrame.FIFTEEN_MINUTES;
    macd.time = dateFormat.parse("2022-04-22 18:00");
    macd.candleClosingPrice = 100;
    macd.trendType = TrendType.NA;
    macd.histogramTrendType = HistogramTrendType.NA;
    macdDataDao.insert(macd);

    double stopLossLevel = macdDataDao.getStopLossLevelBasedOnBreakoutCandlestick(
        getChartPatternSignal(dateFormat.parse("2022-04-22 18:30")));

    assertThat(stopLossLevel).isEqualTo(100.0);
  }

  private ChartPatternSignal getChartPatternSignal(Date timeOfSignal) {
    return ChartPatternSignal.newBuilder()
        .setCoinPair("BTCUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setTimeOfSignal(timeOfSignal)
        .setTimeOfInsertion(timeOfSignal)
        .setIsInsertedLate(false)
        .setPriceTarget(6000)
        .setPriceTargetTime(new Date(System.currentTimeMillis() + 360000))
        .setProfitPotentialPercent(2.3)
        .setIsSignalOn(true)
        .build();
  }
}