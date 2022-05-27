package com.gateiobot.macd;

import com.binance.bot.common.Mailer;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.util.CreateCryptobotDB;
import com.binance.bot.util.CreateDatasource;
import com.gateiobot.GateIoClientFactory;
import com.gateiobot.db.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.api.MarginApi;
import io.gate.gateapi.api.SpotApi;
import io.gate.gateapi.models.MarginCurrencyPair;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.mail.MessagingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.*;

import static com.binance.bot.common.Util.assertDecimalEquals;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

@RunWith(JUnit4.class)
// TODO: Integration test (is there in the MACD repository) is getting ruined becasue of the CommadnLineRunner run() getting auto-invoked.
public class MACDCalculationTest {

  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock
  private GateIoClientFactory mockGateIoClientFactory;
  private Date START_TIME, FARAWAY_CURR_TIME;
  @Mock
  private SpotApi mockSpotApi;
  @Mock
  private MarginApi mockMarginApi;
  @Mock
  private SpotApi.APIlistCandlesticksRequest mockAPIlistCandlesticksRequest, mockAPIlistCandlesticksRequest2,
      mockAPIlistCandlesticksRequest3;
  @Mock
  private Clock mockClock;
  @Mock
  private Mailer mockMailer;
  private JdbcTemplate jdbcTemplate;
  private static String COINPAIR = "BTC_USDT";
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private MACDCalculation macdCalculation;
  private final MACDDataDao macdDataDao = new MACDDataDao();
  private final Map<TimeFrame, String> timeFrameToStringMap;

  public MACDCalculationTest() {
    timeFrameToStringMap = new HashMap<>();
    timeFrameToStringMap.put(TimeFrame.FIFTEEN_MINUTES, "15m");
    timeFrameToStringMap.put(TimeFrame.HOUR, "1h");
    timeFrameToStringMap.put(TimeFrame.FOUR_HOURS, "4h");
    timeFrameToStringMap.put(TimeFrame.DAY, "1d");
  }

  @Before
  public void setUp() throws ApiException, IOException, ParseException {
    MACDCalculation.NUM_SHARDS = 1;
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    START_TIME = dateFormat.parse("2021-12-01 00:00");
    FARAWAY_CURR_TIME = dateFormat.parse("2200-01-01 00:00");
    DataSource dataSource = CreateDatasource.createDataSource();
    CreateCryptobotDB.createCryptobotDB(dataSource);
    macdDataDao.jdbcTemplate = new JdbcTemplate(dataSource);
    when(mockGateIoClientFactory.getSpotApi()).thenReturn(mockSpotApi);
    when(mockGateIoClientFactory.getMarginApi()).thenReturn(mockMarginApi);
    when(mockMarginApi.listMarginCurrencyPairs()).thenReturn(getMarginCurrencyPairs());
    macdCalculation = new MACDCalculation(mockGateIoClientFactory, macdDataDao);
    macdCalculation.setClock(mockClock);
    macdCalculation.setMockMailer(mockMailer);
    macdCalculation.isTest = true;
    when(mockClock.millis()).thenReturn(System.currentTimeMillis());
  }

  private List<MarginCurrencyPair> getMarginCurrencyPairs() {
    MarginCurrencyPair btcUsdtMarginPair = new MarginCurrencyPair();
    btcUsdtMarginPair.setId(COINPAIR);
    return Lists.newArrayList(btcUsdtMarginPair);
  }

  @Test
  public void noPreExistingRowsInDB_usesHardcodedStartTime_fifteenMinuteTimeFrame() throws MessagingException, ApiException, InterruptedException {
    noPreExistingRowsInDB_usesHardcodedStartTime_endTime1000CandlesticksApart(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void noPreExistingRowsInDB_usesHardcodedStartTime_hourlyTimeFrame() throws MessagingException, ApiException, InterruptedException {
    noPreExistingRowsInDB_usesHardcodedStartTime_endTime1000CandlesticksApart(TimeFrame.HOUR);
  }

  @Test
  public void noPreExistingRowsInDB_usesHardcodedStartTime_fourHourlyTimeFrame() throws ApiException, MessagingException, InterruptedException {
    noPreExistingRowsInDB_usesHardcodedStartTime_endTime1000CandlesticksApart(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void noPreExistingRowsInDB_usesHardcodedStartTime_dailyTimeFrame() throws ApiException, MessagingException, InterruptedException {
    noPreExistingRowsInDB_usesHardcodedStartTime_endTime1000CandlesticksApart(TimeFrame.DAY);
  }

  // Tests for candlestick request formations.
  private void noPreExistingRowsInDB_usesHardcodedStartTime_endTime1000CandlesticksApart(TimeFrame timeFrame) throws MessagingException, ApiException, InterruptedException {
    // Far future date from the hard coded start date.
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    Date endCandlestick = getNthCandlestickTime(START_TIME, timeFrame, 1000);
    when(mockAPIlistCandlesticksRequest.to(endCandlestick.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(timeFrameToStringMap.get(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    verify(mockSpotApi).listCandlesticks(COINPAIR);
    verify(mockAPIlistCandlesticksRequest).from(START_TIME.getTime() / 1000);
    verify(mockAPIlistCandlesticksRequest).to(endCandlestick.getTime() / 1000);
    verify(mockAPIlistCandlesticksRequest).interval(timeFrameToStringMap.get(timeFrame));
  }

  @Test
  public void rowInDBAlreadyUptoLastCompletedCandlestick_noNewAPIRequest_fifteenMinuteTimeframe()
      throws MessagingException, ApiException, InterruptedException {
    rowInDBAlreadyUptoLastCompletedCandlestick_noNewAPIRequest(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void rowInDBAlreadyUptoLastCompletedCandlestick_noNewAPIRequest_hourlyTimeframe()
      throws MessagingException, ApiException, InterruptedException {
    rowInDBAlreadyUptoLastCompletedCandlestick_noNewAPIRequest(TimeFrame.HOUR);
  }

  @Test
  public void rowInDBAlreadyUptoLastCompletedCandlestick_noNewAPIRequest_fourHourlyTimeframe()
      throws MessagingException, ApiException, InterruptedException {
    rowInDBAlreadyUptoLastCompletedCandlestick_noNewAPIRequest(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void rowInDBAlreadyUptoLastCompletedCandlestick_noNewAPIRequest_DailyTimeframe()
      throws MessagingException, ApiException, InterruptedException {
    rowInDBAlreadyUptoLastCompletedCandlestick_noNewAPIRequest(TimeFrame.DAY);
  }

  private void rowInDBAlreadyUptoLastCompletedCandlestick_noNewAPIRequest(TimeFrame timeFrame) throws MessagingException, ApiException, InterruptedException {
    when(mockClock.millis()).thenReturn(getTimeAlmostAtCandlestickCloseButNotYet(START_TIME, timeFrame).getTime());
    MACDData preExistingRow = new MACDData();
    preExistingRow.coinPair = COINPAIR;
    preExistingRow.timeFrame = timeFrame;
    preExistingRow.trendType = TrendType.NA;
    preExistingRow.time = START_TIME;
    macdDataDao.insert(preExistingRow);

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    verify(mockSpotApi, never()).listCandlesticks(COINPAIR);
  }

  @Test
  public void rowInDB_nextAPIRequestStartAndEndCandlestickTimes_fifteenMinuteTimeFrame() throws MessagingException, ApiException, InterruptedException {
    rowInDB_nextAPIRequestStartAndEndCandlestickTimes(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void rowInDB_nextAPIRequestStartAndEndCandlestickTimes_hourlyTimeFrame() throws MessagingException, ApiException, InterruptedException {
    rowInDB_nextAPIRequestStartAndEndCandlestickTimes(TimeFrame.HOUR);
  }

  @Test
  public void rowInDB_nextAPIRequestStartAndEndCandlestickTimes_fourHourlyTimeFrame() throws MessagingException, ApiException, InterruptedException {
    rowInDB_nextAPIRequestStartAndEndCandlestickTimes(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void rowInDB_nextAPIRequestStartAndEndCandlestickTimes_dailyTimeFrame() throws MessagingException, ApiException, InterruptedException {
    rowInDB_nextAPIRequestStartAndEndCandlestickTimes(TimeFrame.DAY);
  }

  private void rowInDB_nextAPIRequestStartAndEndCandlestickTimes(TimeFrame timeFrame) throws MessagingException, ApiException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    MACDData preExistingRow = new MACDData();
    preExistingRow.coinPair = COINPAIR;
    preExistingRow.timeFrame = timeFrame;
    preExistingRow.trendType = TrendType.NA;
    preExistingRow.time = START_TIME;
    macdDataDao.insert(preExistingRow);
    Date nextCandlestickStartTime = getNextCandlestickStartTime(START_TIME, timeFrame);
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(nextCandlestickStartTime.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    Date endCandlestick = getNthCandlestickTime(nextCandlestickStartTime, timeFrame, 1000);
    when(mockAPIlistCandlesticksRequest.to(endCandlestick.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(timeFrameToStringMap.get(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    verify(mockSpotApi).listCandlesticks(COINPAIR);
    verify(mockAPIlistCandlesticksRequest).from(nextCandlestickStartTime.getTime() / 1000);
    verify(mockAPIlistCandlesticksRequest).to(endCandlestick.getTime() / 1000);
    verify(mockAPIlistCandlesticksRequest).interval(timeFrameToStringMap.get(timeFrame));
  }

  @Test
  public void rowInDB_nextAPIRequestEndCandlestickTimes_1000thCandlestickIsIncomplete_uses999thCandlestickInstead_fifteenMinuteTimeFrame() throws MessagingException, ApiException, InterruptedException {
    rowInDB_nextAPIRequestEndCandlestickTimes_1000thCandlestickIsIncomplete_uses999thCandlestickInstead(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void rowInDB_nextAPIRequestEndCandlestickTimes_1000thCandlestickIsIncomplete_uses999thCandlestickInstead_hourlyTimeFrame() throws MessagingException, ApiException, InterruptedException {
    rowInDB_nextAPIRequestEndCandlestickTimes_1000thCandlestickIsIncomplete_uses999thCandlestickInstead(TimeFrame.HOUR);
  }

  @Test
  public void rowInDB_nextAPIRequestEndCandlestickTimes_1000thCandlestickIsIncomplete_uses999thCandlestickInstead_fourHourlyTimeFrame() throws MessagingException, ApiException, InterruptedException {
    rowInDB_nextAPIRequestEndCandlestickTimes_1000thCandlestickIsIncomplete_uses999thCandlestickInstead(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void rowInDB_nextAPIRequestEndCandlestickTimes_1000thCandlestickIsIncomplete_uses999thCandlestickInstead_dailyTimeFrame() throws MessagingException, ApiException, InterruptedException {
    rowInDB_nextAPIRequestEndCandlestickTimes_1000thCandlestickIsIncomplete_uses999thCandlestickInstead(TimeFrame.DAY);
  }

  private void rowInDB_nextAPIRequestEndCandlestickTimes_1000thCandlestickIsIncomplete_uses999thCandlestickInstead(
      TimeFrame timeFrame) throws MessagingException, ApiException, InterruptedException {
    when(mockClock.millis()).thenReturn(getNthCandlestickTime(START_TIME, timeFrame, 1000).getTime());
    MACDData preExistingRow = new MACDData();
    preExistingRow.coinPair = COINPAIR;
    preExistingRow.timeFrame = timeFrame;
    preExistingRow.trendType = TrendType.NA;
    preExistingRow.time = START_TIME;
    macdDataDao.insert(preExistingRow);
    Date nextCandlestickStartTime = getNextCandlestickStartTime(START_TIME, timeFrame);
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(nextCandlestickStartTime.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    Date endCandlestick = getNthCandlestickTime(START_TIME, timeFrame, 999);
    when(mockAPIlistCandlesticksRequest.to(endCandlestick.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(timeFrameToStringMap.get(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    verify(mockSpotApi).listCandlesticks(COINPAIR);
    verify(mockAPIlistCandlesticksRequest).from(nextCandlestickStartTime.getTime() / 1000);
    verify(mockAPIlistCandlesticksRequest).to(endCandlestick.getTime() / 1000);
    verify(mockAPIlistCandlesticksRequest).interval(timeFrameToStringMap.get(timeFrame));
  }

  private Date getNextCandlestickStartTime(Date time, TimeFrame timeFrame) {
    return getNthCandlestickTime(time, timeFrame, 2);
  }

  private Date getTimeAlmostAtCandlestickCloseButNotYet(Date startTime, TimeFrame timeFrame) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return DateUtils.addMinutes(startTime, 14);
      case HOUR:
        return DateUtils.addMinutes(startTime, 59);
      case FOUR_HOURS:
        return DateUtils.addMinutes(startTime, 239);
      case DAY:
      default:
        return DateUtils.addMinutes(startTime, 24 * 60 - 1);
    }
  }

  private Date getNthCandlestickTime(Date beginCandlestick, TimeFrame timeFrame, int n) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return DateUtils.addMinutes(beginCandlestick, 15 * (n - 1));
      case HOUR:
        return DateUtils.addHours(beginCandlestick, (n - 1));
      case FOUR_HOURS:
        return DateUtils.addHours(beginCandlestick, 4 * (n - 1));
      case DAY:
      default:
        return DateUtils.addDays(beginCandlestick, (n - 1));
    }
  }

  // Tests for SMA calculations.
  @Test
  public void zeroPrices_shouldntCrash() throws MessagingException, ApiException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(getNthCandlestickTime(START_TIME, TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(START_TIME.getTime() / 1000), "volume", "0.0", "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);
    when(mockAPIlistCandlesticksRequest2.from(DateUtils.addMinutes(START_TIME, 15).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(getNthCandlestickTime(DateUtils.addMinutes(START_TIME, 15),
        TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    when(mockClock.millis()).thenReturn(System.currentTimeMillis());
    macdCalculation.fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 0, getMarginCurrencyPairs());

    List<MACDData> macdDataInDB = macdDataDao.getMACDDataList(COINPAIR, TimeFrame.FIFTEEN_MINUTES, 30);
    for (int i = 0; i < 30; i++) {
      assertThat(macdDataInDB.get(i).sma).isZero();
      assertThat(macdDataInDB.get(i).smaSlope).isZero();
    }
  }

  @Test
  public void fifteenMinutesTimeFrame_noRowsPreexisting_oneCandlestickInResponse() throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    final int invocationCount[] = new int[1];
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(getNthCandlestickTime(START_TIME, TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<String> candlestick = Lists.newArrayList(
        Long.toString(START_TIME.getTime() / 1000), "volume", "10.0", "highest", "lowest", "open");
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(ImmutableList.of(candlestick));
    when(mockAPIlistCandlesticksRequest2.from(DateUtils.addMinutes(START_TIME, 15).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(getNthCandlestickTime(DateUtils.addMinutes(START_TIME, 15),
        TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    when(mockClock.millis()).thenReturn(System.currentTimeMillis());
    macdCalculation.fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 0, getMarginCurrencyPairs());

    MACDData macdDataInDB = macdDataDao.getMACDDataList(COINPAIR, TimeFrame.FIFTEEN_MINUTES, 1).get(0);
    assertThat(macdDataInDB.coinPair).isEqualTo(COINPAIR);
    assertThat(macdDataInDB.time).isEqualTo(START_TIME);
    assertThat(macdDataInDB.timeFrame).isEqualTo(TimeFrame.FIFTEEN_MINUTES);
    assertThat(macdDataInDB.sma).isEqualTo(10.0);
    assertThat(macdDataInDB.smaSlope).isEqualTo(0.0);
    assertThat(macdDataInDB.trendType).isEqualTo(TrendType.NA);
  }

  @Test
  public void fifteenMinutesTimeFrame_oneRowPreexisting_oneCandlestickInResponse_avgSMA() throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    MACDData preExistingRowInDB = new MACDData();
    preExistingRowInDB.coinPair = COINPAIR;
    preExistingRowInDB.timeFrame = TimeFrame.FIFTEEN_MINUTES;
    preExistingRowInDB.time = START_TIME;
    preExistingRowInDB.candleClosingPrice = 10.0;
    preExistingRowInDB.trendType = TrendType.NA;
    macdDataDao.insert(preExistingRowInDB);

    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(DateUtils.addMinutes(START_TIME, 15).getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(
            DateUtils.addMinutes(START_TIME, 15), TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<String> candlestick = Lists.newArrayList(
        Long.toString(DateUtils.addMinutes(START_TIME, 15).getTime() / 1000), "volume", "20.0", "highest", "lowest", "open");
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(ImmutableList.of(candlestick));
    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(DateUtils.addMinutes(START_TIME, 30).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            DateUtils.addMinutes(START_TIME, 30), TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 0, getMarginCurrencyPairs());

    MACDData macdDataInDB = macdDataDao.getMACDDataList(COINPAIR, TimeFrame.FIFTEEN_MINUTES, 1).get(0);
    assertThat(macdDataInDB.coinPair).isEqualTo(COINPAIR);
    assertThat(macdDataInDB.time).isEqualTo(DateUtils.addMinutes(START_TIME, 15));
    assertThat(macdDataInDB.timeFrame).isEqualTo(TimeFrame.FIFTEEN_MINUTES);
    assertThat(macdDataInDB.sma).isEqualTo(15.0);
    assertThat(macdDataInDB.smaSlope).isEqualTo(0.0);
    assertThat(macdDataInDB.trendType).isEqualTo(TrendType.NA);
  }

  @Test
  public void smaCalculation_windowSlidesAfter30SizeForAvgCalculation() throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 31; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(DateUtils.addMinutes(START_TIME, 15 * i).getTime()),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(DateUtils.addMinutes(START_TIME, 15 * 31).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            DateUtils.addMinutes(START_TIME, 15 * 31), TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    when(mockClock.millis()).thenReturn(System.currentTimeMillis());
    macdCalculation.fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, TimeFrame.FIFTEEN_MINUTES, 31);
    assertThat(macdRowsInDB).hasSize(31);
    for (int i = 0; i <= 30; i++) {
      double sum = (i + 1) * (i + 2) / 2;
      if (i >= 30) {
        sum -= i + 1 - 30;
      }
      double sma = sum / ((i + 1) > 30 ? 30 : i + 1);
      assertThat(macdRowsInDB.get(i).sma).isEqualTo(sma);
      if (i >= 9) {
        assertDecimalEquals(macdRowsInDB.get(i).smaSlope,
            (macdRowsInDB.get(i).sma - macdRowsInDB.get(i - 9).sma) / macdRowsInDB.get(i - 9).sma * 100);
        assertThat(macdRowsInDB.get(i).trendType).isEqualTo(TrendType.BULLISH);
      } else {
        assertThat(macdRowsInDB.get(i).smaSlope).isEqualTo(0.0);
        assertThat(macdRowsInDB.get(i).trendType).isEqualTo(TrendType.NA);
      }
    }
  }

  @Test
  public void bearishTrend() throws ApiException, ParseException, MessagingException, InterruptedException {
    final int invocationCount[] = new int[1];
    doAnswer(inv -> {
      if (invocationCount[0]++ == 0) {
        return mockAPIlistCandlesticksRequest;
      }
      return mockAPIlistCandlesticksRequest2;
    }).when(mockSpotApi).listCandlesticks(COINPAIR);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 31; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(DateUtils.addMinutes(START_TIME, 15 * i).getTime()),
          "volume", Double.toString(100 - (i + 1)), "highest", "lowest", "open"));
    }

    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);
    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(DateUtils.addMinutes(START_TIME, 15 * 31).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            DateUtils.addMinutes(START_TIME, 15 * 31), TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    when(mockClock.millis()).thenReturn(System.currentTimeMillis());
    macdCalculation.fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, TimeFrame.FIFTEEN_MINUTES, 31);
    assertThat(macdRowsInDB).hasSize(31);
    for (int i = 0; i <= 30; i++) {
      if (i >= 9) {
        assertThat(macdRowsInDB.get(i).trendType).isEqualTo(TrendType.BEARISH);
      } else {
        assertThat(macdRowsInDB.get(i).smaSlope).isEqualTo(0.0);
        assertThat(macdRowsInDB.get(i).trendType).isEqualTo(TrendType.NA);
      }
    }
  }

  @Test
  public void rangingTrend() throws ApiException, ParseException, MessagingException, InterruptedException {
    final int invocationCount[] = new int[1];
    doAnswer(inv -> {
      if (invocationCount[0]++ == 0) {
        return mockAPIlistCandlesticksRequest;
      }
      return mockAPIlistCandlesticksRequest2;
    }).when(mockSpotApi).listCandlesticks(COINPAIR);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 31; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(DateUtils.addMinutes(START_TIME, 15 * i).getTime()),
          "volume", Double.toString(100), "highest", "lowest", "open"));
    }

    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);
    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(DateUtils.addMinutes(START_TIME, 15 * 31).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            DateUtils.addMinutes(START_TIME, 15 * 31), TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    when(mockClock.millis()).thenReturn(System.currentTimeMillis());
    macdCalculation.fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, TimeFrame.FIFTEEN_MINUTES, 31);
    assertThat(macdRowsInDB).hasSize(31);
    for (int i = 0; i <= 30; i++) {
      if (i >= 9) {
        assertThat(macdRowsInDB.get(i).trendType).isEqualTo(TrendType.RANGING);
      } else {
        assertThat(macdRowsInDB.get(i).smaSlope).isEqualTo(0.0);
        assertThat(macdRowsInDB.get(i).trendType).isEqualTo(TrendType.NA);
      }
    }
  }

  @Test
  public void smaCalculation_2Candlesticks_28FromDB() throws ApiException, ParseException, MessagingException, InterruptedException {
    for (int i = 0; i < 30; i++) {
      MACDData preExistingRowInDB = new MACDData();
      preExistingRowInDB.coinPair = COINPAIR;
      preExistingRowInDB.timeFrame = TimeFrame.FIFTEEN_MINUTES;
      preExistingRowInDB.time = DateUtils.addMinutes(START_TIME, i * 15);
      preExistingRowInDB.candleClosingPrice = i + 1;
      preExistingRowInDB.sma = ((i + 1.0) * (i + 2.0) / 2) / (i + 1);
      preExistingRowInDB.trendType = TrendType.NA;
      macdDataDao.insert(preExistingRowInDB);
    }

    final int invocationCount[] = new int[1];
    doAnswer(inv -> {
      if (invocationCount[0]++ == 0) {
        return mockAPIlistCandlesticksRequest;
      }
      return mockAPIlistCandlesticksRequest2;
    }).when(mockSpotApi).listCandlesticks(COINPAIR);
    Date currentTime = DateUtils.addMinutes(START_TIME, 30 * 15);
    when(mockAPIlistCandlesticksRequest.from(currentTime.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(currentTime, TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 30; i < 32; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(DateUtils.addMinutes(START_TIME, 15 * i).getTime() / 1000),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }

    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);
    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(DateUtils.addMinutes(currentTime, 2 * 15).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(DateUtils.addMinutes(currentTime, 2 * 15), TimeFrame.FIFTEEN_MINUTES, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    when(mockClock.millis()).thenReturn(System.currentTimeMillis());
    macdCalculation.fillMACDDataPartitioned(TimeFrame.FIFTEEN_MINUTES, 0, getMarginCurrencyPairs());

    // old rows: i: 0 - 29     -> candlestick values: 1 - 30
    // new rows: i: 30 - 31    -> candlestick values: 31 - 32
    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, TimeFrame.FIFTEEN_MINUTES, 2);
    assertThat(macdRowsInDB).hasSize(2);
    assertThat(macdRowsInDB.get(0).time).isEqualTo(currentTime);
    assertThat(macdRowsInDB.get(1).time).isEqualTo(DateUtils.addMinutes(currentTime, 15));
    // i=30: 2...31
    assertThat(macdRowsInDB.get(0).sma).isEqualTo((31.0 * 32 / 2 - 1) / 30);
    //int tenCandleSticksAgoIndex = 21; // 30 - 10 + 1
    double smaTenSticksAgo = (22.0 * 23 / 2) / 22;
    assertDecimalEquals(macdRowsInDB.get(0).smaSlope, (macdRowsInDB.get(0).sma - smaTenSticksAgo) / smaTenSticksAgo * 100);
    assertThat(macdRowsInDB.get(0).trendType).isEqualTo(TrendType.BULLISH);
    // i=31: 3..32
    assertThat(macdRowsInDB.get(1).sma).isEqualTo((32.0 * 33 / 2 - 3) / 30);
    smaTenSticksAgo = (23.0 * 24 / 2) / 23;
    assertDecimalEquals(macdRowsInDB.get(1).smaSlope, (macdRowsInDB.get(1).sma - smaTenSticksAgo) / smaTenSticksAgo * 100);
    assertThat(macdRowsInDB.get(1).trendType).isEqualTo(TrendType.BULLISH);
  }

  private String getTimeInterval(TimeFrame timeFrame) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return "15m";
      case HOUR:
        return "1h";
      case FOUR_HOURS:
        return "4h";
      case DAY:
      default:
        return "1d";
    }
  }

  private Date addNPeriodsFromSTART_TIME(int n, TimeFrame timeFrame) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return DateUtils.addMinutes(START_TIME, 15 * n);
      case HOUR:
        return DateUtils.addHours(START_TIME, n);
      case FOUR_HOURS:
        return DateUtils.addHours(START_TIME, 4 * n);
      default:
        return DateUtils.addDays(START_TIME, n);
    }
  }

  // EMA 12
  private void ema12_noPreExistingRows(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 14; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime()),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(addNPeriodsFromSTART_TIME(14, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(14, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval(getTimeInterval(timeFrame)))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, timeFrame, 14);
    assertThat(macdRowsInDB).hasSize(14);
    for (int i = 0; i < 12; i++) {
      double sum = (i + 1) * (i + 2) / 2;
      double sma = sum / (i + 1);
      assertThat(macdRowsInDB.get(i).sma).isEqualTo(sma);
      assertThat(macdRowsInDB.get(i).ema12).isEqualTo(0.0);
    }
    double multiplier = 2.0 / (12 + 1);
    assertThat(macdRowsInDB.get(12).ema12).isEqualTo((1 - multiplier) * macdRowsInDB.get(11).sma
        + multiplier * 13);
    assertThat(macdRowsInDB.get(13).ema12).isEqualTo((1 - multiplier) * macdRowsInDB.get(12).ema12
        + multiplier * 14);
  }

  @Test
  public void ema12_noPreExistingRows_fifteenMinuteTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema12_noPreExistingRows(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void ema12_noPreExistingRows_hourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema12_noPreExistingRows(TimeFrame.HOUR);
  }

  @Test
  public void ema12_noPreExistingRows_fourHourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema12_noPreExistingRows(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void ema12_noPreExistingRows_dailyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema12_noPreExistingRows(TimeFrame.DAY);
  }

  private void ema12_preExistingRows(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    for (int i = 0; i < 12; i++) {
      MACDData preExistingRowInDB = new MACDData();
      preExistingRowInDB.coinPair = COINPAIR;
      preExistingRowInDB.timeFrame = timeFrame;
      preExistingRowInDB.time = addNPeriodsFromSTART_TIME(i, timeFrame);
      preExistingRowInDB.candleClosingPrice = i + 1;
      preExistingRowInDB.sma = ((i + 1.0) * (i + 2.0) / 2) / (i + 1);
      preExistingRowInDB.trendType = TrendType.NA;
      macdDataDao.insert(preExistingRowInDB);
    }

    Date currentTime = addNPeriodsFromSTART_TIME(12, timeFrame);
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(currentTime.getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(currentTime, timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime()),
          "volume", Double.toString(i + 13), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(addNPeriodsFromSTART_TIME(2, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(2, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, timeFrame, 14);
    assertThat(macdRowsInDB).hasSize(14);
    double multiplier = 2.0 / (12 + 1);
    assertThat(macdRowsInDB.get(12).ema12).isEqualTo((1 - multiplier) * macdRowsInDB.get(11).sma
        + multiplier * 13);
    assertThat(macdRowsInDB.get(13).ema12).isEqualTo((1 - multiplier) * macdRowsInDB.get(12).ema12
        + multiplier * 14);
  }

  @Test
  public void ema12_preExistingRows_fifteenMinuteTimeframe() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema12_preExistingRows(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void ema12_preExistingRows_HourlyTimeframe() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema12_preExistingRows(TimeFrame.HOUR);
  }

  @Test
  public void ema12_preExistingRows_FourHourlyTimeframe() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema12_preExistingRows(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void ema12_preExistingRows_DailyTimeframe() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema12_preExistingRows(TimeFrame.DAY);
  }

  // EMA 26
  private void ema26_noPreExistingRows(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 28; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime()),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(addNPeriodsFromSTART_TIME(28, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(28, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval(getTimeInterval(timeFrame)))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, timeFrame, 28);
    assertThat(macdRowsInDB).hasSize(28);
    for (int i = 0; i < 26; i++) {
      double sum = (i + 1) * (i + 2) / 2;
      double sma = sum / (i + 1);
      assertThat(macdRowsInDB.get(i).sma).isEqualTo(sma);
      assertThat(macdRowsInDB.get(i).ema26).isEqualTo(0.0);
    }
    double multiplier = 2.0 / (26 + 1);
    assertThat(macdRowsInDB.get(26).ema26).isEqualTo((1 - multiplier) * macdRowsInDB.get(25).sma
        + multiplier * 27);
    assertThat(macdRowsInDB.get(27).ema26).isEqualTo((1 - multiplier) * macdRowsInDB.get(26).ema26
        + multiplier * 28);
    assertThat(macdRowsInDB.get(26).macd).isEqualTo(macdRowsInDB.get(26).ema12 - macdRowsInDB.get(26).ema26);
    assertThat(macdRowsInDB.get(27).macd).isEqualTo(macdRowsInDB.get(27).ema12 - macdRowsInDB.get(27).ema26);
  }

  @Test
  public void ema26_noPreExistingRows_fifteenMinuteTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema26_noPreExistingRows(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void ema26_noPreExistingRows_hourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema26_noPreExistingRows(TimeFrame.HOUR);
  }

  @Test
  public void ema26_noPreExistingRows_fourHourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema26_noPreExistingRows(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void ema26_noPreExistingRows_dailyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema26_noPreExistingRows(TimeFrame.DAY);
  }

  private void ema26_preExistingRows(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    for (int i = 0; i < 26; i++) {
      MACDData preExistingRowInDB = new MACDData();
      preExistingRowInDB.coinPair = COINPAIR;
      preExistingRowInDB.timeFrame = timeFrame;
      preExistingRowInDB.time = addNPeriodsFromSTART_TIME(i, timeFrame);
      preExistingRowInDB.candleClosingPrice = i + 1;
      preExistingRowInDB.sma = ((i + 1.0) * (i + 2.0) / 2) / (i + 1);
      preExistingRowInDB.trendType = TrendType.NA;
      macdDataDao.insert(preExistingRowInDB);
    }

    Date currentTime = addNPeriodsFromSTART_TIME(26, timeFrame);
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(currentTime.getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(currentTime, timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime()),
          "volume", Double.toString(i + 27), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(addNPeriodsFromSTART_TIME(2, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(2, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, timeFrame, 28);
    assertThat(macdRowsInDB).hasSize(28);
    double multiplier = 2.0 / (26 + 1);
    assertThat(macdRowsInDB.get(26).ema26).isEqualTo((1 - multiplier) * macdRowsInDB.get(25).sma
        + multiplier * 27);
    assertThat(macdRowsInDB.get(27).ema26).isEqualTo((1 - multiplier) * macdRowsInDB.get(26).ema26
        + multiplier * 28);
    assertThat(macdRowsInDB.get(26).macd).isEqualTo(macdRowsInDB.get(26).ema12 - macdRowsInDB.get(26).ema26);
    assertThat(macdRowsInDB.get(27).macd).isEqualTo(macdRowsInDB.get(27).ema12 - macdRowsInDB.get(27).ema26);
  }

  @Test
  public void ema26_preExistingRows_fifteenMinuteTimeframe() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema26_preExistingRows(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void ema26_preExistingRows_HourlyTimeframe() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema26_preExistingRows(TimeFrame.HOUR);
  }

  @Test
  public void ema26_preExistingRows_FourHourlyTimeframe() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema26_preExistingRows(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void ema26_preExistingRows_DailyTimeframe() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema26_preExistingRows(TimeFrame.DAY);
  }

  // MACD. starts after 35 rows (26 + 9).
  private void macd_noPreExistingRows(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 37; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime()),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(addNPeriodsFromSTART_TIME(37, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(37, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval(getTimeInterval(timeFrame)))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, timeFrame, 37);
    assertThat(macdRowsInDB).hasSize(37);
    for (int i = 0; i < 35; i++) {
      assertThat(macdRowsInDB.get(i).macdSignal).isEqualTo(0.0);
      assertThat(macdRowsInDB.get(i).histogram).isEqualTo(0.0);
    }
    double sumMACD = 0;
    for (int i = 26; i < 35; i++) {
      assertThat(macdRowsInDB.get(i).macd).isNonZero();
      sumMACD += macdRowsInDB.get(i).macd;
    }
    double multiplier = 2.0 / (9 + 1);
    assertThat(macdRowsInDB.get(35).macdSignal).isNonZero();
    assertThat(macdRowsInDB.get(35).macd).isNonZero();
    // TODO: Histogram is always zero coz ema 12 and ema26 are the same. Test for differentiated values.
    //assertThat(macdRowsInDB.get(35).histogram).isNonZero();
    assertThat(macdRowsInDB.get(36).macdSignal).isNonZero();
    assertThat(macdRowsInDB.get(36).macd).isNonZero();
    // assertThat(macdRowsInDB.get(36).histogram).isNonZero();
    assertDecimalEquals(macdRowsInDB.get(35).macdSignal,
        (1 - multiplier) * sumMACD / 9 + multiplier * macdRowsInDB.get(35).macd);
    assertDecimalEquals(macdRowsInDB.get(35).histogram, macdRowsInDB.get(35).macd - macdRowsInDB.get(35).macdSignal);
    assertDecimalEquals(macdRowsInDB.get(36).macdSignal,
        (1 - multiplier) * macdRowsInDB.get(35).macdSignal + multiplier * macdRowsInDB.get(36).macd);
    assertDecimalEquals(macdRowsInDB.get(36).histogram, macdRowsInDB.get(36).macd - macdRowsInDB.get(36).macdSignal);
  }

  @Test
  public void macd_noPreExistingRows_fifteenMinuteTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_noPreExistingRows(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void macd_noPreExistingRows_hourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_noPreExistingRows(TimeFrame.HOUR);
  }

  @Test
  public void macd_noPreExistingRows_fourHourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_noPreExistingRows(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void macd_noPreExistingRows_dailyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_noPreExistingRows(TimeFrame.DAY);
  }

  private void macd_preExistingRows_from_previousIteration(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2).thenReturn(mockAPIlistCandlesticksRequest3);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 35; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime() / 1000),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    // Second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(addNPeriodsFromSTART_TIME(35, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(35, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval(getTimeInterval(timeFrame)))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    candlesticks = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(35 + i, timeFrame).getTime() / 1000),
          "volume", Double.toString(35 + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(candlesticks);

    // Third fetch for candlesticks returns empty list.
    when(mockAPIlistCandlesticksRequest3.from(addNPeriodsFromSTART_TIME(37, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest3);
    when(mockAPIlistCandlesticksRequest3.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(37, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest3);
    when(mockAPIlistCandlesticksRequest3.interval(getTimeInterval(timeFrame)))
        .thenReturn(mockAPIlistCandlesticksRequest3);
    when(mockAPIlistCandlesticksRequest3.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, timeFrame, 37);
    assertThat(macdRowsInDB).hasSize(37);
    double sumMACD = 0;
    for (int i = 26; i < 35; i++) {
      sumMACD += macdRowsInDB.get(i).macd;
    }
    double multiplier = 2.0 / (9 + 1);
    assertDecimalEquals(macdRowsInDB.get(35).macdSignal,
        (1 - multiplier) * sumMACD / 9 + multiplier * macdRowsInDB.get(35).macd);
    assertDecimalEquals(macdRowsInDB.get(35).histogram, macdRowsInDB.get(35).macd - macdRowsInDB.get(35).macdSignal);
    assertDecimalEquals(macdRowsInDB.get(36).macdSignal,
        (1 - multiplier) * macdRowsInDB.get(35).macdSignal + multiplier * macdRowsInDB.get(36).macd);
    assertDecimalEquals(macdRowsInDB.get(36).histogram, macdRowsInDB.get(36).macd - macdRowsInDB.get(36).macdSignal);
  }

  @Test
  public void macd_preExistingRows_fifteenMinuteTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_preExistingRows_from_previousIteration(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void macd_preExistingRows_hourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_preExistingRows_from_previousIteration(TimeFrame.HOUR);
  }

  @Test
  public void macd_preExistingRows_fourHourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_preExistingRows_from_previousIteration(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void macd_preExistingRows_dailyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_preExistingRows_from_previousIteration(TimeFrame.DAY);
  }

  private void macd_histogramNonZeroness(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 37; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime()),
          "volume", Double.toString(Math.random()), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(addNPeriodsFromSTART_TIME(37, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(37, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval(getTimeInterval(timeFrame)))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, timeFrame, 37);
    for (int i = 0; i < 35; i++) {
      assertThat(macdRowsInDB.get(i).macdSignal).isEqualTo(0.0);
      assertThat(macdRowsInDB.get(i).histogram).isEqualTo(0.0);
    }
    double sumMACD = 0;
    for (int i = 26; i < 35; i++) {
      assertThat(macdRowsInDB.get(i).macd).isNonZero();
      sumMACD += macdRowsInDB.get(i).macd;
    }
    double multiplier = 2.0 / (9 + 1);
    assertThat(macdRowsInDB.get(35).macdSignal).isNonZero();
    assertThat(macdRowsInDB.get(35).macd).isNonZero();
    // TODO: Histogram is always zero coz ema 12 and ema26 are the same. Test for differentiated values.
    assertThat(macdRowsInDB.get(35).histogram).isNonZero();
    assertThat(macdRowsInDB.get(36).macdSignal).isNonZero();
    assertThat(macdRowsInDB.get(36).macd).isNonZero();
    assertThat(macdRowsInDB.get(36).histogram).isNonZero();
    assertDecimalEquals(macdRowsInDB.get(35).macdSignal,
        (1 - multiplier) * sumMACD / 9 + multiplier * macdRowsInDB.get(35).macd);
    assertDecimalEquals(macdRowsInDB.get(35).histogram, macdRowsInDB.get(35).macd - macdRowsInDB.get(35).macdSignal);
    assertDecimalEquals(macdRowsInDB.get(36).macdSignal,
        (1 - multiplier) * macdRowsInDB.get(35).macdSignal + multiplier * macdRowsInDB.get(36).macd);
    assertDecimalEquals(macdRowsInDB.get(36).histogram, macdRowsInDB.get(36).macd - macdRowsInDB.get(36).macdSignal);
  }


  @Test
  public void macd_histogramNonZeroness_fifteenMinuteTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_histogramNonZeroness(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void macd_histogramNonZeroness_hourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_histogramNonZeroness(TimeFrame.HOUR);
  }

  @Test
  public void macd_histogramNonZeroness_fourHourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_histogramNonZeroness(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void macd_histogramNonZeroness_dailyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_histogramNonZeroness(TimeFrame.DAY);
  }

  private void macd_histogramEMA_noPrevRowsInDB(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 42; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime() / 1000),
          "volume", Double.toString(Math.random()), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    // Make it return empty for the second fetch for candlesticks.
    when(mockAPIlistCandlesticksRequest2.from(addNPeriodsFromSTART_TIME(42, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(42, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval(getTimeInterval(timeFrame)))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, timeFrame, 42);
    assertThat(macdRowsInDB.get(39).histogramEMA).isZero();
    double sum = 0.0;
    for (int i = 35; i < 40; i++) {
      sum += macdRowsInDB.get(i).histogram;
    }
    double prevEMA = sum / 5;
    double multiplier = 2.0 / 6;
    assertDecimalEquals(macdRowsInDB.get(40).histogramEMA, (1 - multiplier) * prevEMA + multiplier * macdRowsInDB.get(40).histogram);
    HistogramTrendType trendType;
    if (macdRowsInDB.get(40).histogram - macdRowsInDB.get(40).histogramEMA > 0) {
      trendType = HistogramTrendType.ACCELERATING;
    } else {
      trendType = HistogramTrendType.DECELERATING;
    }
    assertThat(macdRowsInDB.get(40).histogramTrendType).isEqualTo(trendType);
    assertDecimalEquals(macdRowsInDB.get(41).histogramEMA, (1 - multiplier) * macdRowsInDB.get(40).histogramEMA + multiplier * macdRowsInDB.get(41).histogram);
    if (macdRowsInDB.get(41).histogram - macdRowsInDB.get(41).histogramEMA > 0) {
      trendType = HistogramTrendType.ACCELERATING;
    } else {
      trendType = HistogramTrendType.DECELERATING;
    }
    assertThat(macdRowsInDB.get(41).histogramTrendType).isEqualTo(trendType);
  }

  @Test
  public void macd_histogramEMA_noPrevRowsInDB_fifteenMinuteTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_histogramEMA_noPrevRowsInDB(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void macd_histogramEMA_noPrevRowsInDB_hourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_histogramEMA_noPrevRowsInDB(TimeFrame.HOUR);
  }

  @Test
  public void macd_histogramEMA_noPrevRowsInDB_fourHourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_histogramEMA_noPrevRowsInDB(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void macd_histogramEMA_noPrevRowsInDB_dailyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_histogramEMA_noPrevRowsInDB(TimeFrame.DAY);
  }

  private void macd_histogramEMA_prevRowsInDB(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest)
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest.from(START_TIME.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(
        getNthCandlestickTime(START_TIME, timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 42; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime() / 1000),
          "volume", Double.toString(Math.random()), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    when(mockAPIlistCandlesticksRequest2.from(addNPeriodsFromSTART_TIME(42, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(42, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    when(mockAPIlistCandlesticksRequest2.interval(getTimeInterval(timeFrame)))
        .thenReturn(mockAPIlistCandlesticksRequest2);
    candlesticks = new ArrayList<>();
    for (int i = 42; i < 43; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime()),
          "volume", Double.toString(Math.random()), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest2.execute()).thenReturn(candlesticks);

    when(mockAPIlistCandlesticksRequest3.from(addNPeriodsFromSTART_TIME(43, timeFrame).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest3);
    when(mockAPIlistCandlesticksRequest3.to(
        getNthCandlestickTime(
            addNPeriodsFromSTART_TIME(43, timeFrame), timeFrame, 1000).getTime() / 1000))
        .thenReturn(mockAPIlistCandlesticksRequest3);
    when(mockAPIlistCandlesticksRequest3.interval(getTimeInterval(timeFrame)))
        .thenReturn(mockAPIlistCandlesticksRequest3);
    when(mockAPIlistCandlesticksRequest3.execute()).thenReturn(ImmutableList.of());

    macdCalculation.fillMACDDataPartitioned(timeFrame, 0, getMarginCurrencyPairs());

    List<MACDData> macdRowsInDB = macdDataDao.getMACDDataList(COINPAIR, timeFrame, 43);
    assertThat(macdRowsInDB).hasSize(43);
    double multiplier = 2.0 / 6;
    assertDecimalEquals(macdRowsInDB.get(42).histogramEMA, (1 - multiplier) * macdRowsInDB.get(41).histogramEMA + multiplier * macdRowsInDB.get(42).histogram);
  }

  @Test
  public void macd_histogramEMA_prevRowsInDB_fifteenMinuteTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_histogramEMA_prevRowsInDB(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void macd_histogramEMA_prevRowsInDB_hourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    macd_histogramEMA_prevRowsInDB(TimeFrame.HOUR);
  }

  @Test
  public void macd_histogramEMA_prevRowsInDB_fourHourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_histogramEMA_prevRowsInDB(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void macd_histogramEMA_prevRowsInDB_dailyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    macd_histogramEMA_prevRowsInDB(TimeFrame.DAY);
  }


}