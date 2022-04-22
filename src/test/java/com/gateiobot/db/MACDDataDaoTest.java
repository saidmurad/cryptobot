package com.gateiobot.db;

import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.util.SetupDatasource;
import junit.framework.TestCase;
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

import static com.google.common.truth.Truth.assertThat;
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
    DataSource dataSource = SetupDatasource.getDataSource();
    macdDataDao.jdbcTemplate = new JdbcTemplate(dataSource);
    String macdCreateStmt = new String(getClass().getResourceAsStream("/macd_schema.sql")
        .readAllBytes());
    macdDataDao.jdbcTemplate.execute(macdCreateStmt);
    macdDataDao.setMockClock(mockClock);
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
}