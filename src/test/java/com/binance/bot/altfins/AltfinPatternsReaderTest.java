package com.binance.bot.altfins;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.signalsuccessfailure.specifictradeactions.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.*;
import com.binance.bot.trading.GetVolumeProfile;
import com.binance.bot.trading.SupportedSymbolsInfo;
import com.binance.bot.trading.VolumeProfile;
import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.junit.Before;
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

public class AltfinPatternsReaderTest extends TestCase {

  private static final String TEST_PATTERNS_FILE = "/test_data_patterns1.txt";
  private final NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private AltfinPatternsReader altfinPatternsReader;
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private VolumeProfile volumeProfile;
  private TickerPrice tickerPrice;
  @Mock
  private GetVolumeProfile mockGetVolumeProfile;
  @Mock private ChartPatternSignalDaoImpl mockDao;
  @Mock private BinanceApiClientFactory mockApiClientFactory;
  @Mock private BinanceApiRestClient mockRestClient;
  @Mock private SupportedSymbolsInfo mockSupportedSymbolsInfo;
  @Mock private ExitPositionAtMarketPrice mockExitPositionAtMarketPrice;
  @Captor
  private ArgumentCaptor<ChartPatternSignal> patternArgCaptor;

  @Before
  public void setUp() throws BinanceApiException {
    MockitoAnnotations.openMocks(this);
    when(mockApiClientFactory.newRestClient()).thenReturn(mockRestClient);
    altfinPatternsReader = new AltfinPatternsReader(mockApiClientFactory, mockGetVolumeProfile, mockDao, mockSupportedSymbolsInfo, mockExitPositionAtMarketPrice);
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
  }

  public void testReadPatterns_skipsNonUSDTNonBUSDQuoteCurrency() throws IOException {
    List<ChartPatternSignal> patterns = altfinPatternsReader.readPatterns(getPatternsFileContents());

    assertThat(patterns.stream().filter(pattern-> !pattern.coinPair().endsWith("USDT")).findFirst().isPresent()).isFalse();
    assertThat(patterns).hasSize(7);
  }

    public void testReadPatterns() throws IOException {
    List<ChartPatternSignal> patterns = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

  public void testReadPatterns_convertsBUSDToUSDT() throws IOException {
    List<ChartPatternSignal> patterns = altfinPatternsReader.readPatterns(getPatternsFileContents());

    assertThat(patterns.get(6).coinPair()).isEqualTo("XRPUSDT");
  }

  public void testFilterProcessPatterns_noNewPatterns() throws ParseException, MessagingException, BinanceApiException {
    List<ChartPatternSignal> patterns = Lists.newArrayList(getChartPatternSignal().build());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patterns);

    altfinPatternsReader.processPaterns(patterns, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).insertChartPatternSignal(any(), any());
  }

  public void testFilterProcessPatterns_dedupesDuplicates() throws ParseException, MessagingException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    List<ChartPatternSignal> patterns = Lists.newArrayList(chartPatternSignal, chartPatternSignal);
    when(mockRestClient.getPrice(chartPatternSignal.coinPair())).thenReturn(tickerPrice);
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList());
    when(mockDao.insertChartPatternSignal(chartPatternSignal, volumeProfile)).thenReturn(true);

    altfinPatternsReader.processPaterns(patterns, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(chartPatternSignal, volumeProfile);
  }

  public void testNegativeProfitPotential_neverInserted() throws ParseException, MessagingException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setProfitPotentialPercent(-1.0)
        .build();
    List<ChartPatternSignal> patterns = Lists.newArrayList(chartPatternSignal, chartPatternSignal);
    when(mockRestClient.getPrice(chartPatternSignal.coinPair())).thenReturn(tickerPrice);
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList());

    altfinPatternsReader.processPaterns(patterns, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).insertChartPatternSignal(any(), any());
  }

  public void testFilterProcessPatterns_useAltfinInvalidationsIsFalse_comebackChartPatternIsIgnoredAndNotReInserted() throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = false;
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

    altfinPatternsReader.processPaterns(Lists.newArrayList(patternFromAltfins), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).insertChartPatternSignal(any(), any());
  }

  public void testFilterProcessPatterns_comebackChartPattern() throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = true;
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

    altfinPatternsReader.processPaterns(Lists.newArrayList(patternFromAltfins), TimeFrame.FIFTEEN_MINUTES);

    assertThat(patternArgCaptor.getValue().coinPair()).isEqualTo(patternFromAltfins.coinPair());
    assertThat(patternArgCaptor.getValue().isSignalOn()).isTrue();
    assertThat(patternArgCaptor.getValue().attempt()).isEqualTo(2);
  }

  public void testFilterProcessPatterns_comebackChartPattern_secondComeback() throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = true;
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

    altfinPatternsReader.processPaterns(Lists.newArrayList(patternFromAltfins), TimeFrame.FIFTEEN_MINUTES);

    assertThat(patternArgCaptor.getValue().coinPair()).isEqualTo(patternFromAltfins.coinPair());
    assertThat(patternArgCaptor.getValue().attempt()).isEqualTo(3);
  }

  public void testFilterProcessPatterns_comebackTest_someSignal0_noCountingAsSecondComeback() throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = true;
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

    altfinPatternsReader.processPaterns(Lists.newArrayList(patternFromAltfins), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).insertChartPatternSignal(any(), any());
  }

  public void testFilterNewPatterns_nonPrimaryKeyChange_consideredIndistinct() throws IOException, ParseException, MessagingException, BinanceApiException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

    altfinPatternsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).insertChartPatternSignal(any(), any());
  }

  public void testFilterNewPatterns_primaryKeyChange_coinPair() throws IOException, ParseException, MessagingException, BinanceApiException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

    altfinPatternsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(modifiedChartPatternSignal, volumeProfile);
  }

  public void testFilterNewPatterns_primaryKeyChange_tradeType() throws IOException, ParseException, MessagingException, BinanceApiException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

    altfinPatternsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(modifiedChartPatternSignal, volumeProfile);
  }

  public void testFilterNewPatterns_primaryKeyChange_timeFrame() throws IOException, ParseException, MessagingException, BinanceApiException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

    altfinPatternsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(modifiedChartPatternSignal, volumeProfile);
  }

  public void testFilterNewPatterns_primaryKeyChange_timeOfSignal() throws IOException, ParseException, MessagingException, BinanceApiException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(patternsInDb);
    List<ChartPatternSignal> patternsFromAltfins = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

    altfinPatternsReader.processPaterns(patternsFromAltfins, TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).insertChartPatternSignal(modifiedChartPatternSignal, volumeProfile);
  }

  public void testFilterPatternsToInvalidate_allPatternsInDBStillOnAltfins_noneToInvalidate() throws IOException {
    altfinPatternsReader.useAltfinInvalidations = true;
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
    List<ChartPatternSignal> patternsFromAltfins = altfinPatternsReader.readPatterns(getPatternsFileContents());
    patternsFromAltfins.set(0, ChartPatternSignal.newBuilder()
        .copy(patternsFromAltfins.get(0))
        .setPriceTargetTime(new Date(System.currentTimeMillis() + 500000))
        .build());

    List<ChartPatternSignal> patternSignalsToInvalidate = altfinPatternsReader.getChartPatternSignalsToInvalidate(patternsFromAltfins, patternsInDb);

    assertThat(patternSignalsToInvalidate).hasSize(0);
  }

  public void testFilterPatternsToInvalidate_oneActivePatternStoppedComing_butWasAlreadyInvalidated_zeroResults() throws IOException {
    altfinPatternsReader.useAltfinInvalidations = true;
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
    List<ChartPatternSignal> patternsFromAltfins = altfinPatternsReader.readPatterns(getPatternsFileContents());
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
    List<ChartPatternSignal> patternSignalsToInvalidate = altfinPatternsReader.getChartPatternSignalsToInvalidate(patternsInDb, patternsInDb);

    assertThat(patternSignalsToInvalidate).hasSize(0);
  }

  public void testInsertChartPatternSignal_timeOfInsertion_tenCandlesticktime() throws IOException, ParseException, MessagingException, BinanceApiException {
    ChartPatternSignal pattern = altfinPatternsReader.readPatterns(getPatternsFileContents()).get(0);

    when(mockRestClient.getPrice(pattern.coinPair())).thenReturn(tickerPrice);

    altfinPatternsReader.processPaterns(Lists.newArrayList(pattern), TimeFrame.FIFTEEN_MINUTES);

    ArgumentCaptor<ChartPatternSignal> patternArgCatcher = ArgumentCaptor.forClass(ChartPatternSignal.class);
//    verify(mockBinanceTradingBot).placeTrade(pattern);
    verify(mockDao).insertChartPatternSignal(patternArgCatcher.capture(), eq(volumeProfile));
    ChartPatternSignal insertedVal = patternArgCatcher.getValue();
    assertThat(insertedVal.coinPair()).isEqualTo(pattern.coinPair());
    assertThat(insertedVal.timeOfInsertion()).isNotNull();
    // First pattern in test file is a 15 min timeframe signal.
    assertThat((int) (insertedVal.tenCandlestickTime().getTime() - insertedVal.timeOfSignal().getTime())/60000).isEqualTo(150);
  }

  public void testInsertChartPatternSignal_notInsertedLate_FifteenMinutesTimeFrame() throws IOException, ParseException, MessagingException, BinanceApiException {
    ChartPatternSignal pattern = getChartPatternSignal().build();

    when(mockRestClient.getPrice(pattern.coinPair())).thenReturn(tickerPrice);

    altfinPatternsReader.processPaterns(Lists.newArrayList(pattern), TimeFrame.FIFTEEN_MINUTES);

    ArgumentCaptor<ChartPatternSignal> patternArgCatcher = ArgumentCaptor.forClass(ChartPatternSignal.class);
//    verify(mockBinanceTradingBot).placeTrade(pattern);
    verify(mockDao).insertChartPatternSignal(patternArgCatcher.capture(), eq(volumeProfile));
    ChartPatternSignal insertedVal = patternArgCatcher.getValue();
    assertThat(insertedVal.isInsertedLate()).isFalse();
  }

  public void testFilterProcessPatterns_comebackChartPattern_neverMarkedInsertedLate() throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = true;
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

    altfinPatternsReader.processPaterns(Lists.newArrayList(patternFromAltfins), TimeFrame.FIFTEEN_MINUTES);

    assertThat(patternArgCaptor.getValue().coinPair()).isEqualTo(patternFromAltfins.coinPair());
    assertThat(patternArgCaptor.getValue().attempt()).isEqualTo(2);
    assertThat(patternArgCaptor.getValue().isInsertedLate()).isFalse();
  }

  public void testInsertChartPatternSignal_nullVolumeProfile() throws IOException, ParseException, MessagingException, BinanceApiException {
    ChartPatternSignal pattern = altfinPatternsReader.readPatterns(getPatternsFileContents()).get(0);
    TickerPrice tickerPrice = new TickerPrice();
    tickerPrice.setPrice("1,111.12");
    when(mockRestClient.getPrice(pattern.coinPair())).thenReturn(tickerPrice);
    when(mockGetVolumeProfile.getVolumeProfile(pattern.coinPair())).thenReturn(null);

    altfinPatternsReader.processPaterns(Lists.newArrayList(pattern), TimeFrame.FIFTEEN_MINUTES);

    ArgumentCaptor<ChartPatternSignal> patternArgCatcher = ArgumentCaptor.forClass(ChartPatternSignal.class);
//    verify(mockBinanceTradingBot).placeTrade(pattern);
    verify(mockDao).insertChartPatternSignal(patternArgCatcher.capture(), eq(null));
    ChartPatternSignal insertedVal = patternArgCatcher.getValue();
    assertThat(insertedVal.coinPair()).isEqualTo(pattern.coinPair());
    assertThat(insertedVal.timeOfInsertion()).isNotNull();
    // First pattern in test file is a 15 min timeframe signal.
    assertThat((int) (insertedVal.tenCandlestickTime().getTime() - insertedVal.timeOfSignal().getTime())/60000).isEqualTo(150);
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

  private String getPatternsFileContents() throws IOException {
    return new String(getClass().getResourceAsStream(TEST_PATTERNS_FILE).readAllBytes());
  }

  public void testChartPatternSignalsToInvalidate_useAltfinInvalidationsIsFalse() throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = false;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList(chartPatternSignal));
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal))).thenReturn(true);
    when(mockDao.getChartPatternSignalsToInvalidate()).thenReturn(Lists.newArrayList(chartPatternSignal));

    altfinPatternsReader.processPaterns(new ArrayList<>(), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao, never()).incrementNumTimesMissingInInput(any());
  }

  public void testChartPatternSignalsToInvalidate() throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = true;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList(chartPatternSignal));
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal))).thenReturn(true);
    when(mockDao.getChartPatternSignalsToInvalidate()).thenReturn(Lists.newArrayList(chartPatternSignal));

    altfinPatternsReader.processPaterns(new ArrayList<>(), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal));
    verify(mockDao).invalidateChartPatternSignal(
        eq(chartPatternSignal), eq(numberFormat.parse(tickerPrice.getPrice()).doubleValue()), any());
  }

  public void testChartPatternSignalsToInvalidate_removedFromAltfins__priceIsObtained()
      throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = true;
    Date currTime = new Date();
    Date newerOccurTimeForSignal = new Date(currTime.getTime() + 10000);
    ChartPatternSignal chartPatternSignal = getChartPatternSignal()
        .setTimeOfSignal(newerOccurTimeForSignal)
        .build();
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList(chartPatternSignal));
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal))).thenReturn(true);
    when(mockDao.getChartPatternSignalsToInvalidate()).thenReturn(Lists.newArrayList(chartPatternSignal));
    altfinPatternsReader.processPaterns(new ArrayList<>(), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal));
    verify(mockDao).invalidateChartPatternSignal(
        eq(chartPatternSignal), eq(numberFormat.parse(tickerPrice.getPrice()).doubleValue()),
        eq(ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS));
  }

  public void testChartPatternSignalsToInvalidate_exitsTradesIfHeld() throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = true;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal().build();
    when(mockDao.getAllChartPatterns(TimeFrame.FIFTEEN_MINUTES)).thenReturn(Lists.newArrayList(chartPatternSignal));
    when(mockRestClient.getPrice(any())).thenReturn(tickerPrice);
    when(mockDao.incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal))).thenReturn(true);
    when(mockDao.getChartPatternSignalsToInvalidate()).thenReturn(Lists.newArrayList(chartPatternSignal));

    altfinPatternsReader.processPaterns(new ArrayList<>(), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal));
    double currPrice = numberFormat.parse(tickerPrice.getPrice()).doubleValue();
    verify(mockDao).invalidateChartPatternSignal(
        eq(chartPatternSignal), eq(currPrice), any());
    verify(mockExitPositionAtMarketPrice).exitPositionIfStillHeld(eq(chartPatternSignal), eq(TradeExitType.REMOVED_FROM_ALTFINS));
  }

  public void testChartPatternSignalsToInvalidate_comebackPatterns_marked_removedFromAltfins() throws ParseException, MessagingException, BinanceApiException {
    altfinPatternsReader.useAltfinInvalidations = true;
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
    altfinPatternsReader.processPaterns(new ArrayList<>(), TimeFrame.FIFTEEN_MINUTES);

    verify(mockDao).incrementNumTimesMissingInInput(Lists.newArrayList(chartPatternSignal));
    verify(mockDao).invalidateChartPatternSignal(
        eq(chartPatternSignal), eq(numberFormat.parse(tickerPrice.getPrice()).doubleValue()),
        eq(ReasonForSignalInvalidation.REMOVED_FROM_ALTFINS));

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