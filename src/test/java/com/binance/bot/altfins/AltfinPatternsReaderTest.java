package com.binance.bot.altfins;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeType;
import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class AltfinPatternsReaderTest extends TestCase {

  private static final String TEST_PATTERNS_FILE = "/test_data_patterns1.txt";
  private final AltfinPatternsReader altfinPatternsReader = new AltfinPatternsReader();
  private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  public void testReadPatterns() throws IOException {
    List<ChartPatternSignal> patterns = altfinPatternsReader.readPatterns(getPatternsFileContents());
    assertThat(patterns).hasSize(4);
    ChartPatternSignal pattern = patterns.get(0);
    assertThat(pattern.coinPair()).isEqualTo("ORNUSDT");
    assertThat(pattern.pattern()).isEqualTo("Triangle");
    assertThat(dateFormat.format(pattern.timeOfSignal())).isEqualTo("2022-01-09 08:45");
    assertThat(pattern.priceAtTimeOfSignal()).isEqualTo(4.8325);
    assertThat(pattern.priceTarget()).isEqualTo(4.9181);
    assertThat(pattern.profitPotentialPercent()).isEqualTo(1.77);
    assertThat(dateFormat.format(pattern.priceTargetTime())).isEqualTo("2022-01-09 14:11");

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
  }

  private String getPatternsFileContents() throws IOException {
    return new String(getClass().getResourceAsStream(TEST_PATTERNS_FILE).readAllBytes());
  }
}