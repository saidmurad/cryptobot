package com.binance.bot.sourcesignals;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.trading.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.*;
import com.binance.bot.trading.GetVolumeProfile;
import com.binance.bot.trading.SupportedSymbolsInfo;
import com.binance.bot.trading.VolumeProfile;
import com.gateiobot.GateIoClientFactory;
import com.gateiobot.db.MACDDataDao;
import com.google.common.collect.Lists;
import io.gate.gateapi.ApiException;
import io.gate.gateapi.api.SpotApi;
import junit.framework.TestCase;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.mail.MessagingException;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class SourceSignalsReaderTest extends TestCase {

  private static final String TEST_PATTERNS_FILE = "/test_data_patterns1.txt";
  private static final String TEST_PATTERNS_WITHOUT_PPP_FILE = "/data_patterns_without_ppp.txt";
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private SourceSignalsReader sourceSignalsReader;
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private VolumeProfile volumeProfile;
  private TickerPrice tickerPrice;
  @Mock
  private GetVolumeProfile mockGetVolumeProfile;
  @Mock private ChartPatternSignalDaoImpl mockDao;
  @Mock private GateIoClientFactory mockGateIoClientFactory;
  @Mock private SpotApi mockSpotApi;
  @Mock private SpotApi.APIlistCandlesticksRequest mockAPIlistCandlesticksRequest;
  @Mock private BinanceApiClientFactory mockApiClientFactory;
  @Mock private BinanceApiRestClient mockRestClient;
  @Mock private SupportedSymbolsInfo mockSupportedSymbolsInfo;
  @Mock private ExitPositionAtMarketPrice mockExitPositionAtMarketPrice;
  @Captor
  private ArgumentCaptor<ChartPatternSignal> patternArgCaptor;
  @Before
  public void setUp() throws BinanceApiException, ParseException, ApiException {
    MockitoAnnotations.openMocks(this);
    when(mockApiClientFactory.newRestClient()).thenReturn(mockRestClient);
    when(mockGateIoClientFactory.getSpotApi()).thenReturn(mockSpotApi);
    sourceSignalsReader = new SourceSignalsReader(mockApiClientFactory, mockGetVolumeProfile, mockDao, mockGateIoClientFactory, mockSupportedSymbolsInfo, mockExitPositionAtMarketPrice);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    when(mockSupportedSymbolsInfo.getSupportedSymbols()).thenReturn(Set.of("ORNUSDT", "ORNUSDTXYZ", "RSRUSDT", "BTCUSDT", "ETHUSDT"));
    Candlestick currentCandlestick = new Candlestick();
    currentCandlestick.setVolume("100");
    volumeProfile = VolumeProfile.newBuilder()
        .setCurrentCandlestick(currentCandlestick)
        .setMinVol(49)
        .setMaxVol(51)
        .setIsVolAtleastMaintained(true)
        .setAvgVol(50)
        .setIsVolSurged(true)
        .setRecentCandlesticks(Lists.newArrayList(currentCandlestick))
        .build();
    when(mockGetVolumeProfile.getVolumeProfile(any())).thenReturn(volumeProfile);
    tickerPrice = new TickerPrice();
    tickerPrice.setPrice("1,111.12");

    Date START_TIME = dateFormat.parse("2021-12-01 00:00");
    when(mockSpotApi.listCandlesticks(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.from(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.to(any())).thenReturn(mockAPIlistCandlesticksRequest);
    when(mockAPIlistCandlesticksRequest.interval(any())).thenReturn(mockAPIlistCandlesticksRequest);
    List<List<String>> candlesticks = new ArrayList<>();
    for (int i = 0; i < 2; i++) {
      candlesticks.add(Lists.newArrayList(
              Long.toString(DateUtils.addMinutes(START_TIME, 15 * i).getTime() / 1000),
              "volume", Double.toString(i + 1000), "highest", "lowest", "open"));
    }
    when(mockAPIlistCandlesticksRequest.execute()).thenReturn(candlesticks);
  }

  public void testReadPatterns_skipsNonUSDTNonBUSDQuoteCurrency() throws IOException {
    List<ChartPatternSignal> patterns = sourceSignalsReader.readPatterns(getPatternsFileContents());

    assertThat(patterns.stream().filter(pattern-> !pattern.coinPair().endsWith("USDT")).findFirst().isPresent()).isFalse();
    assertThat(patterns).hasSize(7);
  }

  public void testReadPatterns() throws IOException {
    List<ChartPatternSignal> patterns = sourceSignalsReader.readPatterns(getPatternsFileContents());
    ChartPatternSignal pattern = patterns.get(0);
    assertThat(pattern.coinPair()).isEqualTo("ORNUSDT");
    assertThat(pattern.pattern()).isEqualTo("Triangle");
    assertThat(dateFormat.format(pattern.timeOfSignal())).isEqualTo("2022-01-09 08:45");
    assertThat(pattern.priceAtTimeOfSignal()).isEqualTo(4.8325);
    assertThat(pattern.priceTarget()).isEqualTo(4.9181);
    assertThat(pattern.profitPotentialPercent()).isEqualTo(1.77);
    assertThat(dateFormat.format(pattern.priceTargetTime())).isEqualTo("2022-01-09 14:11");

    assertThat(patterns.get(1).profitPotentialPercent()).isEqualTo(-21999.0);

    assertThat(patterns.get(0).tradeType()).isEqualTo(TradeType.BUY);
    assertThat(patterns.get(1).tradeType()).isEqualTo(TradeType.SELL);

    assertThat(patterns.get(0).timeFrame()).isEqualTo(TimeFrame.FIFTEEN_MINUTES);
    assertThat(patterns.get(1).timeFrame()).isEqualTo(TimeFrame.HOUR);
    assertThat(patterns.get(2).timeFrame()).isEqualTo(TimeFrame.FOUR_HOURS);
    assertThat(patterns.get(3).timeFrame()).isEqualTo(TimeFrame.DAY);

    // Price target range, for sell and buy.
    assertThat(patterns.get(1).priceTarget()).isEqualTo(0.0254);
    assertThat(patterns.get(2).priceTarget()).isEqualTo(0.0254);
    assertThat(patterns.get(3).priceTarget()).isEqualTo(0.0248);

    // Comma in double value
    assertThat(patterns.get(4).priceAtTimeOfSignal()).isEqualTo(4000.0);
    assertThat(patterns.get(4).priceTarget()).isEqualTo(5000.0);
    // In range.
    assertThat(patterns.get(5).priceTarget()).isEqualTo(5000.0);
  }

  public void testReadPatterns_withoutPPP() throws IOException {
    List<ChartPatternSignal> patterns = sourceSignalsReader.readPatterns(getPatternsWithoutPPPFileContents());
    ChartPatternSignal pattern = patterns.get(0);
    assertThat(pattern.coinPair()).isEqualTo("WXTUSDT");
    assertThat(pattern.profitPotentialPercent()).isZero();
  }

  public void testReadPatterns_convertsBUSDToUSDT() throws IOException {
    List<ChartPatternSignal> patterns = sourceSignalsReader.readPatterns(getPatternsFileContents());

    assertThat(patterns.get(6).coinPair()).isEqualTo("XRPUSDT");
  }

  public void testFilterProcessPatterns_noNewPatterns() throws ParseException, MessagingException, BinanceApiException, ApiException {
    List<ChartPatternSignal> patterns = Lists.newArrayList(getChartPatternSignal().build());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patterns);

    sourceSignalsReader.processPaterns(patterns, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).insertChartPatternSignal(any(), any());
  }

  public void testFilterProcessPatterns_dedupesDuplicates() throws ParseException, MessagingException, BinanceApiException, ApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    List<ChartPatternSignal> patterns = Lists.newArrayList(chartPatternSignal, chartPatternSignal);
    when(mockRestClient.getPrice(chartPatternSignal.coinPair())).thenReturn(tickerPrice);
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList());
    when(mockDao.insertChartPatternSignal(chartPatternSignal, volumeProfile)).thenReturn(true);

    sourceSignalsReader.processPaterns(patterns, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(chartPatternSignal, volumeProfile);
  }

  public void testNegativeProfitPotential_neverInserted() throws ParseException, MessagingException, BinanceApiException, ApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setProfitPotentialPercent(-1.0)
        .build();
    List<ChartPatternSignal> patterns = Lists.newArrayList(chartPatternSignal, chartPatternSignal);
    when(mockRestClient.getPrice(chartPatternSignal.coinPair())).thenReturn(tickerPrice);
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList());

    sourceSignalsReader.processPaterns(patterns, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).insertChartPatternSignal(any(), any());
  }

  public void testFilterProcessPatterns_comebackChartPattern() throws ParseException, MessagingException, BinanceApiException, ApiException {
    ChartPatternSignal invalidatedChartPatternSignal = getChartPatternSignal()
        .setIsSignalOn(false)
        .setAttempt(1)
        .build();
    ChartPatternSignal patternFromAltfins = ChartPatternSignal.newBuilder().copy(invalidatedChartPatternSignal)
            .setIsSignalOn(true)
                .setAttempt(1)
                    .build();
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES))
        .thenReturn(Lists.newArrayList(invalidatedChartPatternSignal));
    when(mockDao.insertChartPatternSignal(patternArgCaptor.capture(), eq(volumeProfile))).thenReturn(true);

    sourceSignalsReader.processPaterns(Lists.newArrayList(patternFromAltfins), TimeFrame.FIFTEEN_MINUTES);

    assertThat(patternArgCaptor.getValue().coinPair()).isEqualTo(patternFromAltfins.coinPair());
    assertThat(patternArgCaptor.getValue().isSignalOn()).isTrue();
    assertThat(patternArgCaptor.getValue().attempt()).isEqualTo(2);
  }

  public void testFilterProcessPatterns_comebackChartPattern_secondComeback() throws ParseException, MessagingException, BinanceApiException, ApiException {
    ChartPatternSignal invalidatedChartPatternSignal1 = getChartPatternSignal()
        .setIsSignalOn(false)
        .setAttempt(1)
        .build();
    ChartPatternSignal invalidatedChartPatternSignal2 = ChartPatternSignal.newBuilder().copy(invalidatedChartPatternSignal1)
        .setIsSignalOn(false)
        .setAttempt(2)
        .build();
    ChartPatternSignal patternFromAltfins = ChartPatternSignal.newBuilder().copy(invalidatedChartPatternSignal1)
        .setIsSignalOn(true)
        .setAttempt(1)
        .build();
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES))
        .thenReturn(Lists.newArrayList(invalidatedChartPatternSignal1, invalidatedChartPatternSignal2));
    when(mockDao.insertChartPatternSignal(patternArgCaptor.capture(), eq(volumeProfile))).thenReturn(true);

    sourceSignalsReader.processPaterns(Lists.newArrayList(patternFromAltfins), TimeFrame.FIFTEEN_MINUTES);

    assertThat(patternArgCaptor.getValue().coinPair()).isEqualTo(patternFromAltfins.coinPair());
    assertThat(patternArgCaptor.getValue().attempt()).isEqualTo(3);
  }

  public void testFilterProcessPatterns_comebackTest_someSignal0_noCountingAsSecondComeback() throws ParseException, MessagingException, BinanceApiException, ApiException {
    ChartPatternSignal invalidatedChartPatternSignal1 = getChartPatternSignal()
        .setIsSignalOn(false)
        .setAttempt(1)
        .build();
    ChartPatternSignal invalidatedChartPatternSignal2 = ChartPatternSignal.newBuilder().copy(invalidatedChartPatternSignal1)
        .setIsSignalOn(true)
        .setAttempt(2)
        .build();
    ChartPatternSignal patternFromAltfins = ChartPatternSignal.newBuilder().copy(invalidatedChartPatternSignal1)
        .setIsSignalOn(true)
        .setAttempt(1)
        .build();
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES))
        .thenReturn(Lists.newArrayList(invalidatedChartPatternSignal1, invalidatedChartPatternSignal2));

    sourceSignalsReader.processPaterns(Lists.newArrayList(patternFromAltfins), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).insertChartPatternSignal(any(), any());
  }

  public void testFilterNewPatterns_nonPrimaryKeyChange_consideredIndistinct() throws IOException, ParseException, MessagingException, BinanceApiException, ApiException {
    List<ChartPatternSignal> patternsInDb = sourceSignalsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = sourceSignalsReader.readPatterns(getPatternsFileContents());
    // With changes in non-primary keys
    ChartPatternSignal modifiedChartPatternSignal = ChartPatternSignal.newBuilder()
        .setCoinPair(patternsFromAltfins.get(0).coinPair())
        .setTimeFrame(patternsFromAltfins.get(0).timeFrame())
        .setTradeType(patternsFromAltfins.get(0).tradeType())
        .setPattern(patternsFromAltfins.get(0).pattern())
        .setTimeOfSignal(patternsFromAltfins.get(0).timeOfSignal())
        .setPriceAtTimeOfSignal(0.001)
        .setPriceTarget(0.002)
        .setPriceTargetTime(new Date())
        .setProfitPotentialPercent(0.001)
        .build();
    patternsFromAltfins.set(0, modifiedChartPatternSignal);

    sourceSignalsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).insertChartPatternSignal(any(), any());
  }

  public void testFilterNewPatterns_primaryKeyChange_coinPair() throws IOException, ParseException, MessagingException, BinanceApiException, ApiException {
    List<ChartPatternSignal> patternsInDb = sourceSignalsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = sourceSignalsReader.readPatterns(getPatternsFileContents());
    String newCoinPair = patternsFromAltfins.get(0).coinPair() + "XYZ";
    ChartPatternSignal modifiedChartPatternSignal = ChartPatternSignal.newBuilder()
        .setCoinPair(newCoinPair)
        .setTimeFrame(patternsFromAltfins.get(0).timeFrame())
        .setTradeType(patternsFromAltfins.get(0).tradeType())
        .setPattern(patternsFromAltfins.get(0).pattern())
        .setTimeOfSignal(patternsFromAltfins.get(0).timeOfSignal())
        .setPriceAtTimeOfSignal(0.001)
        .setPriceTarget(0.002)
        .setPriceTargetTime(new Date())
        .setProfitPotentialPercent(0.001)
        .build();
    patternsFromAltfins.add(modifiedChartPatternSignal);
    when(mockRestClient.getPrice(newCoinPair)).thenReturn(tickerPrice);

    sourceSignalsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(modifiedChartPatternSignal, volumeProfile);
  }

  public void testFilterNewPatterns_primaryKeyChange_tradeType() throws IOException, ParseException, MessagingException, BinanceApiException, ApiException {
    List<ChartPatternSignal> patternsInDb = sourceSignalsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = sourceSignalsReader.readPatterns(getPatternsFileContents());
    ChartPatternSignal modifiedChartPatternSignal = ChartPatternSignal.newBuilder()
        .setCoinPair(patternsFromAltfins.get(0).coinPair())
        .setTimeFrame(patternsFromAltfins.get(0).timeFrame())
        .setTradeType(patternsFromAltfins.get(0).tradeType() == TradeType.BUY? TradeType.SELL : TradeType.BUY)
        .setPattern(patternsFromAltfins.get(0).pattern())
        .setTimeOfSignal(patternsFromAltfins.get(0).timeOfSignal())
        .setPriceAtTimeOfSignal(0.001)
        .setPriceTarget(0.002)
        .setPriceTargetTime(new Date())
        .setProfitPotentialPercent(0.001)
        .build();
    patternsFromAltfins.add(modifiedChartPatternSignal);
    when(mockRestClient.getPrice(patternsFromAltfins.get(0).coinPair())).thenReturn(tickerPrice);

    sourceSignalsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(modifiedChartPatternSignal, volumeProfile);
  }

  public void testFilterNewPatterns_primaryKeyChange_timeFrame() throws IOException, ParseException, MessagingException, BinanceApiException, ApiException {
    List<ChartPatternSignal> patternsInDb = sourceSignalsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = sourceSignalsReader.readPatterns(getPatternsFileContents());
    ChartPatternSignal modifiedChartPatternSignal = ChartPatternSignal.newBuilder()
        .setCoinPair(patternsFromAltfins.get(0).coinPair())
        .setTimeFrame(changeTimeFrame(patternsFromAltfins.get(0).timeFrame()))
        .setTradeType(patternsFromAltfins.get(0).tradeType() == TradeType.BUY? TradeType.SELL : TradeType.BUY)
        .setPattern(patternsFromAltfins.get(0).pattern())
        .setTimeOfSignal(patternsFromAltfins.get(0).timeOfSignal())
        .setPriceAtTimeOfSignal(0.001)
        .setPriceTarget(0.002)
        .setPriceTargetTime(new Date())
        .setProfitPotentialPercent(0.001)
        .build();
    patternsFromAltfins.add(modifiedChartPatternSignal);
    when(mockRestClient.getPrice(patternsFromAltfins.get(0).coinPair())).thenReturn(tickerPrice);

    sourceSignalsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(modifiedChartPatternSignal, volumeProfile);
  }

  public void testFilterNewPatterns_primaryKeyChange_timeOfSignal() throws IOException, ParseException, MessagingException, BinanceApiException, ApiException {
    List<ChartPatternSignal> patternsInDb = sourceSignalsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = sourceSignalsReader.readPatterns(getPatternsFileContents());
    ChartPatternSignal modifiedChartPatternSignal = ChartPatternSignal.newBuilder()
        .setCoinPair(patternsFromAltfins.get(0).coinPair())
        .setTimeFrame(patternsFromAltfins.get(0).timeFrame())
        .setTradeType(patternsFromAltfins.get(0).tradeType())
        .setPattern(patternsFromAltfins.get(0).pattern())
        .setTimeOfSignal(new Date())
        .setPriceAtTimeOfSignal(0.001)
        .setPriceTarget(0.002)
        .setPriceTargetTime(new Date())
        .setProfitPotentialPercent(0.001)
        .build();
    patternsFromAltfins.add(modifiedChartPatternSignal);
    when(mockRestClient.getPrice(patternsFromAltfins.get(0).coinPair())).thenReturn(tickerPrice);

    sourceSignalsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(modifiedChartPatternSignal, volumeProfile);
  }

  public void testFilterPatternsToInvalidate_allPatternsInDBStillOnAltfins_noneToInvalidate() throws IOException {
    List<ChartPatternSignal> patternsInDb = sourceSignalsReader.readPatterns(getPatternsFileContents());
    List<ChartPatternSignal> patternsFromAltfins = sourceSignalsReader.readPatterns(getPatternsFileContents());
    patternsFromAltfins.set(0, ChartPatternSignal.newBuilder()
        .copy(patternsFromAltfins.get(0))
        .setPriceTargetTime(new Date(System.currentTimeMillis() + 500000))
        .build());

    List<ChartPatternSignal> patternSignalsToInvalidate =
        sourceSignalsReader.getChartPatternSignalsToInvalidate(patternsFromAltfins, patternsInDb);

    assertThat(patternSignalsToInvalidate).hasSize(0);
  }

  public void testFilterPatternsToInvalidate_oneActivePatternStoppedComing_butWasAlreadyInvalidated_zeroResults() throws IOException {
    List<ChartPatternSignal> patternsInDb = sourceSignalsReader.readPatterns(getPatternsFileContents());
    List<ChartPatternSignal> patternsFromAltfins = sourceSignalsReader.readPatterns(getPatternsFileContents());
    patternsFromAltfins.remove(0);
    ChartPatternSignal patternAlreadyToInvalidate = ChartPatternSignal.newBuilder()
        .setCoinPair(patternsInDb.get(0).coinPair())
        .setTimeFrame(patternsInDb.get(0).timeFrame())
        .setTradeType(patternsInDb.get(0).tradeType())
        .setPattern(patternsInDb.get(0).pattern())
        .setTimeOfSignal(patternsInDb.get(0).timeOfSignal())
        .setPriceAtTimeOfSignal(patternsInDb.get(0).priceAtTimeOfSignal())
        .setPriceTarget(patternsInDb.get(0).priceTarget())
        .setPriceTargetTime(patternsInDb.get(0).priceTargetTime())
        .setProfitPotentialPercent(patternsInDb.get(0).profitPotentialPercent())
        .setIsSignalOn(false)
        .build();
    patternsInDb.set(0, patternAlreadyToInvalidate);
    List<ChartPatternSignal> patternSignalsToInvalidate = sourceSignalsReader.getChartPatternSignalsToInvalidate(patternsInDb, patternsInDb);

    assertThat(patternSignalsToInvalidate).hasSize(0);
  }

  public void testInsertChartPatternSignal_timeOfInsertion_tenCandlesticktime() throws IOException, ParseException, MessagingException, BinanceApiException, ApiException {
    ChartPatternSignal pattern = sourceSignalsReader.readPatterns(getPatternsFileContents()).get(0);

    when(mockRestClient.getPrice(pattern.coinPair())).thenReturn(tickerPrice);

    sourceSignalsReader.processPaterns(Lists.newArrayList(pattern), TimeFrame.FIFTEEN_MINUTES);

    ArgumentCaptor<ChartPatternSignal> patternArgCatcher = ArgumentCaptor.forClass(ChartPatternSignal.class);
//    verify(mockBinanceTradingBot).placeTrade(pattern);
    verify(mockDao).insertChartPatternSignal(patternArgCatcher.capture(), eq(volumeProfile));
    ChartPatternSignal insertedVal = patternArgCatcher.getValue();
    assertThat(insertedVal.coinPair()).isEqualTo(pattern.coinPair());
    assertThat(insertedVal.timeOfInsertion()).isNotNull();
    // First pattern in test file is a 15 min timeframe signal.
    assertThat((int) (insertedVal.tenCandlestickTime().getTime() - insertedVal.timeOfSignal().getTime())/60000).isEqualTo(150);
  }

  public void testInsertChartPatternSignal_notInsertedLate_FifteenMinutesTimeFrame() throws IOException, ParseException, MessagingException, BinanceApiException, ApiException {
    ChartPatternSignal pattern = getChartPatternSignal().build();

    when(mockRestClient.getPrice(pattern.coinPair())).thenReturn(tickerPrice);

    sourceSignalsReader.processPaterns(Lists.newArrayList(pattern), TimeFrame.FIFTEEN_MINUTES);

    ArgumentCaptor<ChartPatternSignal> patternArgCatcher = ArgumentCaptor.forClass(ChartPatternSignal.class);
//    verify(mockBinanceTradingBot).placeTrade(pattern);
    verify(mockDao).insertChartPatternSignal(patternArgCatcher.capture(), eq(volumeProfile));
    ChartPatternSignal insertedVal = patternArgCatcher.getValue();
    assertThat(insertedVal.isInsertedLate()).isFalse();
  }

  public void testFilterProcessPatterns_comebackChartPattern_neverMarkedInsertedLate() throws ParseException, MessagingException, BinanceApiException, ApiException {
    ChartPatternSignal invalidatedChartPatternSignal = getChartPatternSignal()
        .setIsSignalOn(false)
        .setTimeOfSignal(new Date(System.currentTimeMillis() - 16 * 60 * 1000))
        .build();
    ChartPatternSignal patternFromAltfins = ChartPatternSignal.newBuilder().copy(invalidatedChartPatternSignal)
        .setIsSignalOn(true)
        .setAttempt(1)
        .build();
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES))
        .thenReturn(Lists.newArrayList(invalidatedChartPatternSignal));
    when(mockDao.insertChartPatternSignal(patternArgCaptor.capture(), eq(volumeProfile))).thenReturn(true);

    sourceSignalsReader.processPaterns(Lists.newArrayList(patternFromAltfins), TimeFrame.FIFTEEN_MINUTES);

    assertThat(patternArgCaptor.getValue().coinPair()).isEqualTo(patternFromAltfins.coinPair());
    assertThat(patternArgCaptor.getValue().attempt()).isEqualTo(2);
    assertThat(patternArgCaptor.getValue().isInsertedLate()).isFalse();
  }

  public void testInsertChartPatternSignal_nullVolumeProfile() throws IOException, ParseException, MessagingException, BinanceApiException, ApiException {
    ChartPatternSignal pattern = sourceSignalsReader.readPatterns(getPatternsFileContents()).get(0);
    TickerPrice tickerPrice = new TickerPrice();
    tickerPrice.setPrice("1,111.12");
    when(mockRestClient.getPrice(pattern.coinPair())).thenReturn(tickerPrice);
    when(mockGetVolumeProfile.getVolumeProfile(pattern.coinPair())).thenReturn(null);

    sourceSignalsReader.processPaterns(Lists.newArrayList(pattern), TimeFrame.FIFTEEN_MINUTES);

    ArgumentCaptor<ChartPatternSignal> patternArgCatcher = ArgumentCaptor.forClass(ChartPatternSignal.class);
//    verify(mockBinanceTradingBot).placeTrade(pattern);
    verify(mockDao).insertChartPatternSignal(patternArgCatcher.capture(), eq(null));
    ChartPatternSignal insertedVal = patternArgCatcher.getValue();
    assertThat(insertedVal.coinPair()).isEqualTo(pattern.coinPair());
    assertThat(insertedVal.timeOfInsertion()).isNotNull();
    // First pattern in test file is a 15 min timeframe signal.
    assertThat((int) (insertedVal.tenCandlestickTime().getTime() - insertedVal.timeOfSignal().getTime())/60000).isEqualTo(150);
  }


  public void testInsertNewChartPatternSignal() throws ParseException, BinanceApiException, MessagingException, ApiException {
    long currentTimeMillis = System.currentTimeMillis();
    Date currentTime = new Date(currentTimeMillis);
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
            .setTimeOfSignal(currentTime)
            .setTimeOfInsertion(currentTime)
            .setPriceTargetTime(currentTime)
            .build();

    when(mockGetVolumeProfile.getVolumeProfile(any())).thenReturn(volumeProfile);

    sourceSignalsReader.insertNewChartPatternSignal(chartPatternSignal);

    ArgumentCaptor<ChartPatternSignal> chartPatternSignalArgumentCaptor = ArgumentCaptor.forClass(ChartPatternSignal.class);
    ArgumentCaptor<VolumeProfile> volumeProfileArgumentCaptor = ArgumentCaptor.forClass(VolumeProfile.class);

    verify(mockDao).insertChartPatternSignal(chartPatternSignalArgumentCaptor.capture(), volumeProfileArgumentCaptor.capture());
    assertThat(chartPatternSignalArgumentCaptor.getValue().coinPair()).isEqualTo("ETHUSDT");
    assertThat(chartPatternSignalArgumentCaptor.getValue().timeFrame()).isEqualTo(TimeFrame.FIFTEEN_MINUTES);
    assertThat(chartPatternSignalArgumentCaptor.getValue().pattern()).isEqualTo("Resistance");
    assertThat(chartPatternSignalArgumentCaptor.getValue().tradeType()).isEqualTo(TradeType.BUY);
    assertThat(chartPatternSignalArgumentCaptor.getValue().timeOfSignal()).isEqualTo(currentTime);
    assertThat(chartPatternSignalArgumentCaptor.getValue().isInsertedLate()).isEqualTo(false);
    assertThat(chartPatternSignalArgumentCaptor.getValue().priceTargetTime()).isEqualTo(currentTime);
    assertThat(chartPatternSignalArgumentCaptor.getValue().profitPotentialPercent()).isEqualTo(2.3);
    assertThat(chartPatternSignalArgumentCaptor.getValue().tenCandlestickTime()).isEqualTo(new Date(currentTimeMillis + 150*60*1000));
    assertThat(chartPatternSignalArgumentCaptor.getValue().preBreakoutCandlestickStopLossPrice()).isEqualTo(1000.0);

    assertThat(volumeProfileArgumentCaptor.getValue().minVol()).isEqualTo(49.0);
    assertThat(volumeProfileArgumentCaptor.getValue().maxVol()).isEqualTo(51.0);
    assertThat(volumeProfileArgumentCaptor.getValue().isVolAtleastMaintained()).isEqualTo(true);
    assertThat(volumeProfileArgumentCaptor.getValue().avgVol()).isEqualTo(50.0);
    assertThat(volumeProfileArgumentCaptor.getValue().isVolSurged()).isEqualTo(true);

  }

  private TimeFrame changeTimeFrame(TimeFrame timeFrame) {
    switch (timeFrame) {
      case FIFTEEN_MINUTES:
        return TimeFrame.HOUR;
      case HOUR:
        return TimeFrame.FOUR_HOURS;
      case FOUR_HOURS:
        return TimeFrame.DAY;
      default:
        return TimeFrame.FIFTEEN_MINUTES;
    }
  }

  private String getPatternsWithoutPPPFileContents() throws IOException {
    return new String(getClass().getResourceAsStream(TEST_PATTERNS_WITHOUT_PPP_FILE).readAllBytes());
  }

  private String getPatternsFileContents() throws IOException {
    return new String(getClass().getResourceAsStream(TEST_PATTERNS_FILE).readAllBytes());
  }

  public void testChartPatternSignalsToInvalidate_removedFromAltfins__priceIsObtained()
          throws ParseException, MessagingException, BinanceApiException, ApiException {
    Date currTime = new Date();
    Date newerOccurTimeForSignal = new Date(currTime.getTime() + 10000);
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeOfSignal(newerOccurTimeForSignal)
        .build();
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList(chartPatternSignal));
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal))).thenReturn(true);
    when(mockDao.getChartPatternSignalsToInvalidate()).thenReturn(Lists.newArrayList(chartPatternSignal));
    sourceSignalsReader.processPaterns(new ArrayList<>(), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal));
    verify(mockDao).invalidateChartPatternSignal(
        eq(chartPatternSignal), eq(numberFormat.parse(tickerPrice.getPrice()).doubleValue()),
        eq(ReasonForSignalInvalidation.REMOVED_FROM_SOURCESIGNALS));
  }

  public void testChartPatternSignalsToInvalidate_notUseAltfinsInvalidations()
          throws ParseException, MessagingException, BinanceApiException, ApiException {
    sourceSignalsReader.useAltfinsInvalidations = false;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList(chartPatternSignal));
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal))).thenReturn(true);
    when(mockDao.getChartPatternSignalsToInvalidate()).thenReturn(Lists.newArrayList(chartPatternSignal));

    sourceSignalsReader.processPaterns(new ArrayList<>(), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal));
    verify(mockDao).invalidateChartPatternSignal(
        eq(chartPatternSignal), eq(numberFormat.parse(tickerPrice.getPrice()).doubleValue()), any());
    verifyNoInteractions(mockExitPositionAtMarketPrice);
  }

  public void testChartPatternSignalsToInvalidate_useAltfinsInvalidations() throws ParseException, MessagingException, BinanceApiException, ApiException {
    sourceSignalsReader.useAltfinsInvalidations = true;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList(chartPatternSignal));
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal))).thenReturn(true);
    when(mockDao.getChartPatternSignalsToInvalidate()).thenReturn(Lists.newArrayList(chartPatternSignal));

    sourceSignalsReader.processPaterns(new ArrayList<>(), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal));
    double currPrice = numberFormat.parse(tickerPrice.getPrice()).doubleValue();
    verify(mockDao).invalidateChartPatternSignal(
        eq(chartPatternSignal), eq(currPrice), any());
    verify(mockExitPositionAtMarketPrice).exitPositionIfStillHeld(eq(chartPatternSignal), eq(TradeExitType.REMOVED_FROM_SOURCESIGNALS));
  }

  public void testChartPatternSignalsToInvalidate_comebackPatterns_marked_removedFromAltfins() throws ParseException, MessagingException, BinanceApiException, ApiException {
    Date currTime = new Date();
    Date olderOccurTimeForSignal = new Date(currTime.getTime() - 10000);
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeOfSignal(olderOccurTimeForSignal)
        .setAttempt(2)
        .build();
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList(chartPatternSignal));
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal))).thenReturn(true);
    when(mockDao.getChartPatternSignalsToInvalidate()).thenReturn(Lists.newArrayList(chartPatternSignal));
    sourceSignalsReader.processPaterns(new ArrayList<>(), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal));
    verify(mockDao).invalidateChartPatternSignal(
        eq(chartPatternSignal), eq(numberFormat.parse(tickerPrice.getPrice()).doubleValue()),
        eq(ReasonForSignalInvalidation.REMOVED_FROM_SOURCESIGNALS));

  }

  private ChartPatternSignal.Builder getChartPatternSignal() {
    long currentTimeMillis = System.currentTimeMillis();
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
        .setProfitPotentialPercent(2.3);
  }
}