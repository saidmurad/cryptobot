package com.binance.bot.altfins;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import junit.framework.TestCase;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class AltfinPatternsReaderTest extends TestCase {

  private static final String TEST_PATTERNS_FILE = "/test_data_patterns1.txt";
  private final AltfinPatternsReader altfinPatternsReader = new AltfinPatternsReader();
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  public void testReadPatterns() throws IOException {
    List<ChartPatternSignal> patterns = altfinPatternsReader.readPatterns(getPatternsFileContents());
    assertThat(patterns).hasSize(6);
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

  public void testFilterNewPatterns() throws IOException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
    List<ChartPatternSignal> patternsFromAltfins = altfinPatternsReader.readPatterns(getPatternsFileContents());
    List<ChartPatternSignal> newPatterns = altfinPatternsReader.getChartPatternSignalsDelta(patternsInDb, patternsFromAltfins);
    // Without any change
    assertThat(newPatterns).hasSize(0);
  }

  public void testFilterNewPatterns_nonPrimaryKeyChange_consideredIndistinct() throws IOException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

    List<ChartPatternSignal> newPatterns = altfinPatternsReader.getChartPatternSignalsDelta(patternsInDb, patternsFromAltfins);

    assertThat(newPatterns).hasSize(0);
  }

  public void testFilterNewPatterns_primaryKeyChange_coinPair() throws IOException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
    List<ChartPatternSignal> patternsFromAltfins = altfinPatternsReader.readPatterns(getPatternsFileContents());
    ChartPatternSignal modifiedChartPatternSignal = ChartPatternSignal.newBuilder()
        .setCoinPair(patternsFromAltfins.get(0).coinPair() + "XYZ")
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

    List<ChartPatternSignal> newPatterns = altfinPatternsReader.getChartPatternSignalsDelta(patternsInDb, patternsFromAltfins);

    assertThat(newPatterns).hasSize(1);
    assertThat(newPatterns.get(0)).isEqualTo(modifiedChartPatternSignal);
  }

  public void testFilterNewPatterns_primaryKeyChange_tradeType() throws IOException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

    List<ChartPatternSignal> newPatterns = altfinPatternsReader.getChartPatternSignalsDelta(patternsInDb, patternsFromAltfins);

    assertThat(newPatterns).hasSize(1);
    assertThat(newPatterns.get(0)).isEqualTo(modifiedChartPatternSignal);
  }

  public void testFilterNewPatterns_primaryKeyChange_timeFrame() throws IOException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

    List<ChartPatternSignal> newPatterns = altfinPatternsReader.getChartPatternSignalsDelta(patternsInDb, patternsFromAltfins);

    assertThat(newPatterns).hasSize(1);
    assertThat(newPatterns.get(0)).isEqualTo(modifiedChartPatternSignal);
  }

  public void testFilterNewPatterns_primaryKeyChange_timeOfSignal() throws IOException {
    List<ChartPatternSignal> patternsInDb = altfinPatternsReader.readPatterns(getPatternsFileContents());
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

    List<ChartPatternSignal> newPatterns = altfinPatternsReader.getChartPatternSignalsDelta(patternsInDb, patternsFromAltfins);

    assertThat(newPatterns).hasSize(1);
    assertThat(newPatterns.get(0)).isEqualTo(modifiedChartPatternSignal);
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
}