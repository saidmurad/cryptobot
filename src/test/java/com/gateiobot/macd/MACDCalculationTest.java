package com.gateiobot.macd;

import com.binance.bot.common.CandlestickUtil;
import com.binance.bot.common.Mailer;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.util.CreateCryptobotDB;
import com.binance.bot.util.CreateDatasource;
import com.gateiobot.GateIoClientFactory;
import com.gateiobot.db.HistogramTrendType;
import com.gateiobot.db.MACDData;
import com.gateiobot.db.MACDDataDao;
import com.gateiobot.db.TrendType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.api.SpotApi;
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
public class MACDCalculationTest {

  @Rule
  public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock
  private GateIoClientFactory mockGateIoClientFactory;
  private final Date currentTime = new Date();
  private Date START_TIME, FARAWAY_CURR_TIME;
  @Mock
  private SpotApi mockSpotApi;
  @Mock
  private SpotApi.APIlistCandlesticksRequest mockAPIlistCandlesticksRequest;
  @Mock
  private Clock mockClock;
  @Mock
  private Mailer mockMailer;
  private static String COINPAIR = "BTC_USDT";
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private MACDCalculation macdCalculation;
  private final Map<TimeFrame, String> timeFrameToStringMap;
  private final Date timeOfSignal;

  public MACDCalculationTest() throws ParseException {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    timeOfSignal = dateFormat.parse("2022-05-31 00:00:00");
    timeFrameToStringMap = new HashMap<>();
    timeFrameToStringMap.put(TimeFrame.FIFTEEN_MINUTES, "15m");
    timeFrameToStringMap.put(TimeFrame.HOUR, "1h");
    timeFrameToStringMap.put(TimeFrame.FOUR_HOURS, "4h");
    timeFrameToStringMap.put(TimeFrame.DAY, "1d");
  }

  @Before
  public void setUp() throws ApiException, IOException, ParseException {
    MACDCalculation.NUM_SHARDS = 1;
    START_TIME = dateFormat.parse("2021-12-01 00:00");
    FARAWAY_CURR_TIME = dateFormat.parse("2200-01-01 00:00");
    when(mockGateIoClientFactory.getSpotApi()).thenReturn(mockSpotApi);
    macdCalculation = new MACDCalculation(mockGateIoClientFactory, mockMailer);
    macdCalculation.isTest = true;
    when(mockClock.millis()).thenReturn(System.currentTimeMillis());
  }

  @Test
  public void candlestickRequestParameters_fifteenMinuteTimeFrame() throws MessagingException, ApiException, InterruptedException, ParseException {
    candlestickRequestParameters(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void candlestickRequestParameters_hourlyTimeFrame() throws MessagingException, ApiException, InterruptedException, ParseException {
    candlestickRequestParameters(TimeFrame.HOUR);
  }

  @Test
  public void candlestickRequestParameters_fourHourlyTimeFrame() throws ApiException, MessagingException, InterruptedException, ParseException {
    candlestickRequestParameters(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void candlestickRequestParameters_dailyTimeFrame() throws ApiException, MessagingException, InterruptedException, ParseException {
    candlestickRequestParameters(TimeFrame.DAY);
  }

  // Tests for candlestick request formations.
  private void candlestickRequestParameters(TimeFrame timeFrame) throws MessagingException, ApiException, InterruptedException, ParseException {
    // Far future date from the hard coded start date.
    when(mockClock.millis()).thenReturn(currentTime.getTime());
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    Date startCandlestickTime = CandlestickUtil.getIthCandlestickTime(timeOfSignal, timeFrame, -999);
    when(mockAPIlistCandlesticksRequest.from(startCandlestickTime.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(timeOfSignal.getTime() / 1000)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(timeFrameToStringMap.get(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(ImmutableList.of());

    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR, timeOfSignal, timeFrame);

    verify(mockSpotApi).listCandlesticks(COINPAIR);
    verify(mockAPIlistCandlesticksRequest).from(startCandlestickTime.getTime() / 1000);
    verify(mockAPIlistCandlesticksRequest).to(timeOfSignal.getTime() / 1000);
    verify(mockAPIlistCandlesticksRequest).interval(timeFrameToStringMap.get(timeFrame));
  }

  // Tests for SMA calculations.
  @Test
  public void zeroPrices_shouldntCrash() throws MessagingException, ApiException, InterruptedException, ParseException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(START_TIME.getTime() / 1000), "volume", "0.0", "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR, timeOfSignal, TimeFrame.FIFTEEN_MINUTES);
    
    for (int i = 0; i < 30; i++) {
      assertThat(macdDataList.get(i).sma).isZero();
      assertThat(macdDataList.get(i).smaSlope).isZero();
    }
  }

  @Test
  public void fifteenMinutesTimeFrame_noRowsPreexisting_oneCandlestickInResponse() throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<String> candlestick = Lists.newArrayList(
        Long.toString(START_TIME.getTime() / 1000), "volume", "10.0", "highest", "lowest", "open");
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(ImmutableList.of(candlestick));

    when(mockClock.millis()).thenReturn(System.currentTimeMillis());
    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR,  timeOfSignal, TimeFrame.FIFTEEN_MINUTES);
    
    assertThat(macdDataList.get(0).coinPair).isEqualTo(COINPAIR);
    assertThat(macdDataList.get(0).time).isEqualTo(START_TIME);
    assertThat(macdDataList.get(0).timeFrame).isEqualTo(TimeFrame.FIFTEEN_MINUTES);
    assertThat(macdDataList.get(0).sma).isEqualTo(0.0);
    assertThat(macdDataList.get(0).smaSlope).isEqualTo(0.0);
    assertThat(macdDataList.get(0).trendType).isEqualTo(TrendType.NA);
  }

  @Test
  public void smaCalculation_windowSlidesAfter30SizeForAvgCalculation() throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(DateUtils.addMinutes(START_TIME, 15 * i).getTime() / 1000),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR,  timeOfSignal, TimeFrame.FIFTEEN_MINUTES);
    
    assertThat(macdDataList).hasSize(40);
    for (int i = 0; i < 30; i++) {
      double sum = (i + 1) * (i + 2) / 2;
      if (i >= 30) {
        sum -= i + 1 - 30;
      }
      if (i < 29) {
        assertThat(macdDataList.get(i).sma).isEqualTo(0.0);
      } else {
        double sma = sum / 30;
        assertThat(macdDataList.get(i).sma).isEqualTo(sma);
      }
      if (i >= 38) {
        assertDecimalEquals(macdDataList.get(i).smaSlope,
            (macdDataList.get(i).sma - macdDataList.get(i - 9).sma) / macdDataList.get(i - 9).sma * 100);
        assertThat(macdDataList.get(i).trendType).isEqualTo(TrendType.BULLISH);
      } else {
        assertThat(macdDataList.get(i).smaSlope).isEqualTo(0.0);
        assertThat(macdDataList.get(i).trendType).isEqualTo(TrendType.NA);
      }
    }
  }

  @Test
  public void bearishTrend() throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(DateUtils.addMinutes(START_TIME, 15 * i).getTime() / 1000),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);
    
    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR,  timeOfSignal, TimeFrame.FIFTEEN_MINUTES);

    assertThat(macdDataList).hasSize(40);
    for (int i = 0; i < 30; i++) {
      double sum = (i + 1) * (i + 2) / 2;
      if (i >= 30) {
        sum -= i + 1 - 30;
      }
      if (i < 29) {
        assertThat(macdDataList.get(i).sma).isEqualTo(0.0);
      } else {
        double sma = sum / 30;
        assertThat(macdDataList.get(i).sma).isEqualTo(sma);
      }
      if (i >= 38) {
        assertDecimalEquals(macdDataList.get(i).smaSlope,
            (macdDataList.get(i).sma - macdDataList.get(i - 9).sma) / macdDataList.get(i - 9).sma * 100);
        assertThat(macdDataList.get(i).trendType).isEqualTo(TrendType.BEARISH);
      } else {
        assertThat(macdDataList.get(i).smaSlope).isEqualTo(0.0);
        assertThat(macdDataList.get(i).trendType).isEqualTo(TrendType.NA);
      }
    }
  }

  @Test
  public void rangingTrend() throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval("15m")).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 31; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(DateUtils.addMinutes(START_TIME, 15 * i).getTime() / 1000),
          "volume", Double.toString(100), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR,  timeOfSignal, TimeFrame.FIFTEEN_MINUTES);

    assertThat(macdDataList).hasSize(31);
    for (int i = 0; i <= 28; i++) {
      assertThat(macdDataList.get(i).sma).isEqualTo(0.0);
      assertThat(macdDataList.get(i).smaSlope).isEqualTo(0.0);
      assertThat(macdDataList.get(i).trendType).isEqualTo(TrendType.NA);
    }
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
  private void ema12(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 14; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(DateUtils.addMinutes(START_TIME, 15 * i).getTime() / 1000),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR, timeOfSignal, timeFrame);

    assertThat(macdDataList).hasSize(14);

    double sum = 0;
    for (int i = 0; i < 12; i++) {
      sum += macdDataList.get(i).candleClosingPrice;
      assertThat(macdDataList.get(i).sma).isEqualTo(0.0);
      assertThat(macdDataList.get(i).ema12).isEqualTo(0.0);
    }
    double firstEMA12 = sum / 12;
    double multiplier = 2.0 / (12 + 1);
    assertThat(macdDataList.get(12).ema12).isEqualTo((1 - multiplier) * firstEMA12
        + multiplier * 13);
    assertThat(macdDataList.get(13).ema12).isEqualTo((1 - multiplier) * macdDataList.get(12).ema12
        + multiplier * 14);
  }

  @Test
  public void ema12_fifteenMinuteTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema12(TimeFrame.FIFTEEN_MINUTES);
  }

  @Test
  public void ema12_hourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    ema12(TimeFrame.HOUR);
  }

  @Test
  public void ema12_fourHourlyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema12(TimeFrame.FOUR_HOURS);
  }

  @Test
  public void ema12_dailyTimeFrame() throws ApiException, MessagingException, ParseException, InterruptedException {
    when(mockClock.millis()).thenReturn(FARAWAY_CURR_TIME.getTime());
    ema12(TimeFrame.DAY);
  }

  // EMA 26
  private void ema26_noPreExistingRows(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 28; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime() / 1000),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR, timeOfSignal, timeFrame);

    assertThat(macdDataList).hasSize(28);
    double sum = 0;
    for (int i = 0; i < 26; i++) {
      sum += macdDataList.get(i).candleClosingPrice;
      assertThat(macdDataList.get(i).sma).isEqualTo(0.0);
      assertThat(macdDataList.get(i).ema26).isEqualTo(0.0);
    }
    double firstEMA26 = sum / 26;
    double multiplier = 2.0 / (26 + 1);
    assertThat(macdDataList.get(26).ema26).isEqualTo((1 - multiplier) * firstEMA26
        + multiplier * 27);
    assertThat(macdDataList.get(27).ema26).isEqualTo((1 - multiplier) * macdDataList.get(26).ema26
        + multiplier * 28);
    assertThat(macdDataList.get(26).macd).isEqualTo(macdDataList.get(26).ema12 - macdDataList.get(26).ema26);
    assertThat(macdDataList.get(27).macd).isEqualTo(macdDataList.get(27).ema12 - macdDataList.get(27).ema26);
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

  // MACD. starts after 35 rows (26 + 9).
  private void macd_noPreExistingRows(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 37; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime() / 1000),
          "volume", Double.toString(i + 1), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR, timeOfSignal, timeFrame);

    assertThat(macdDataList).hasSize(37);
    for (int i = 0; i < 35; i++) {
      assertThat(macdDataList.get(i).macdSignal).isEqualTo(0.0);
      assertThat(macdDataList.get(i).histogram).isEqualTo(0.0);
    }
    double sumMACD = 0;
    for (int i = 26; i < 35; i++) {
      assertThat(macdDataList.get(i).macd).isNonZero();
      sumMACD += macdDataList.get(i).macd;
    }
    double multiplier = 2.0 / (9 + 1);
    assertThat(macdDataList.get(35).macdSignal).isNonZero();
    assertThat(macdDataList.get(35).macd).isNonZero();
    // TODO: Histogram is always zero coz ema 12 and ema26 are the same. Test for differentiated values.
    //assertThat(macdDataList.get(35).histogram).isNonZero();
    assertThat(macdDataList.get(36).macdSignal).isNonZero();
    assertThat(macdDataList.get(36).macd).isNonZero();
    // assertThat(macdDataList.get(36).histogram).isNonZero();
    assertDecimalEquals(macdDataList.get(35).macdSignal,
        (1 - multiplier) * sumMACD / 9 + multiplier * macdDataList.get(35).macd);
    assertDecimalEquals(macdDataList.get(35).histogram, macdDataList.get(35).macd - macdDataList.get(35).macdSignal);
    assertDecimalEquals(macdDataList.get(36).macdSignal,
        (1 - multiplier) * macdDataList.get(35).macdSignal + multiplier * macdDataList.get(36).macd);
    assertDecimalEquals(macdDataList.get(36).histogram, macdDataList.get(36).macd - macdDataList.get(36).macdSignal);
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

  private void macd_histogramNonZeroness(TimeFrame timeFrame) throws ApiException, ParseException, MessagingException, InterruptedException {
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 37; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime() / 1000),
          "volume", Double.toString(Math.random()), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR, timeOfSignal, timeFrame);

    for (int i = 0; i < 35; i++) {
      assertThat(macdDataList.get(i).macdSignal).isEqualTo(0.0);
      assertThat(macdDataList.get(i).histogram).isEqualTo(0.0);
    }
    double sumMACD = 0;
    for (int i = 26; i < 35; i++) {
      assertThat(macdDataList.get(i).macd).isNonZero();
      sumMACD += macdDataList.get(i).macd;
    }
    double multiplier = 2.0 / (9 + 1);
    assertThat(macdDataList.get(35).macdSignal).isNonZero();
    assertThat(macdDataList.get(35).macd).isNonZero();
    // TODO: Histogram is always zero coz ema 12 and ema26 are the same. Test for differentiated values.
    assertThat(macdDataList.get(35).histogram).isNonZero();
    assertThat(macdDataList.get(36).macdSignal).isNonZero();
    assertThat(macdDataList.get(36).macd).isNonZero();
    assertThat(macdDataList.get(36).histogram).isNonZero();
    assertDecimalEquals(macdDataList.get(35).macdSignal,
        (1 - multiplier) * sumMACD / 9 + multiplier * macdDataList.get(35).macd);
    assertDecimalEquals(macdDataList.get(35).histogram, macdDataList.get(35).macd - macdDataList.get(35).macdSignal);
    assertDecimalEquals(macdDataList.get(36).macdSignal,
        (1 - multiplier) * macdDataList.get(35).macdSignal + multiplier * macdDataList.get(36).macd);
    assertDecimalEquals(macdDataList.get(36).histogram, macdDataList.get(36).macd - macdDataList.get(36).macdSignal);
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
    when(mockSpotApi.listCandlesticks(COINPAIR)).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(getTimeInterval(timeFrame))).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 42; i++) {
      candlesticks.add(Lists.newArrayList(
          Long.toString(addNPeriodsFromSTART_TIME(i, timeFrame).getTime() / 1000),
          "volume", Double.toString(Math.random()), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);

    List<MACDData> macdDataList = macdCalculation.getMACDData(COINPAIR, timeOfSignal, timeFrame);

    assertThat(macdDataList.get(39).histogramEMA).isZero();
    double sum = 0.0;
    for (int i = 35; i < 40; i++) {
      sum += macdDataList.get(i).histogram;
    }
    double prevEMA = sum / 5;
    double multiplier = 2.0 / 6;
    assertDecimalEquals(macdDataList.get(40).histogramEMA, (1 - multiplier) * prevEMA + multiplier * macdDataList.get(40).histogram);
    HistogramTrendType trendType;
    if (macdDataList.get(40).histogram - macdDataList.get(40).histogramEMA > 0) {
      trendType = HistogramTrendType.ACCELERATING;
    } else {
      trendType = HistogramTrendType.DECELERATING;
    }
    assertThat(macdDataList.get(40).histogramTrendType).isEqualTo(trendType);
    assertDecimalEquals(macdDataList.get(41).histogramEMA, (1 - multiplier) * macdDataList.get(40).histogramEMA + multiplier * macdDataList.get(41).histogram);
    if (macdDataList.get(41).histogram - macdDataList.get(41).histogramEMA > 0) {
      trendType = HistogramTrendType.ACCELERATING;
    } else {
      trendType = HistogramTrendType.DECELERATING;
    }
    assertThat(macdDataList.get(41).histogramTrendType).isEqualTo(trendType);
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
}