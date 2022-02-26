package com.binance.bot.integration;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.signalsuccessfailure.specifictradeactions.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.BinanceTradingBot;
import com.binance.bot.trading.VolumeProfile;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.sqlite.SQLiteException;

import javax.mail.MessagingException;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

import static com.binance.bot.database.ChartPatternSignalDaoImplTest.createTableStmt;
import static com.google.common.truth.Truth.assertThat;

/*@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@EnableConfigurationProperties
@TestPropertySource(locations = {
    "classpath:application.properties"},
    properties = "app.scheduling.enable=false")*/
/**
 * Disabled for frequent running becaause cross margin test doesn't have a testnet to use.
 */
public class TradePlacementAndExitingTest {
  @Autowired
  private BinanceTradingBot binanceTradingBot;
  @Autowired
  ChartPatternSignalDaoImpl dao;
  @Autowired
  private JdbcTemplate jdbcTemplate;
  @Autowired
  ExitPositionAtMarketPrice exitPositionAtMarketPrice;
  @Autowired
  BinanceApiClientFactory binanceApiClientFactory;
  BinanceApiRestClient binanceApiRestClient;
  @Value("${api_key}") String apiKey;
  private long timeOfSignal = System.currentTimeMillis();
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private VolumeProfile volProfile;

  //@Before
  public void setUp() throws SQLException {
    assertThat(apiKey).startsWith("31");
    Statement stmt = jdbcTemplate.getDataSource().getConnection().createStatement();
    try {
      stmt.execute("drop table ChartPatternSignal");
    } catch (SQLiteException ignore) {

    }
    stmt.execute(createTableStmt);
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
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

  //@Test
  public void buyAtMarket_and_exitAtMarket() throws ParseException, MessagingException, BinanceApiException {
    ChartPatternSignal chartPatternSignal = getChartPatternSignal();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    binanceTradingBot.perTradeAmountConfigured = 10;
    binanceTradingBot.placeTrade(chartPatternSignal);
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);

    assertThat(chartPatternSignal.isSignalOn()).isTrue();
    assertThat(chartPatternSignal.entryOrder()).isNotNull();
    assertThat(chartPatternSignal.entryOrder().status()).isEqualTo(OrderStatus.FILLED);
    System.out.println(String.format("Executed entry order: %s.", chartPatternSignal.entryOrder()));
    assertThat(Math.abs(chartPatternSignal.entryOrder().executedQty() * chartPatternSignal.entryOrder().avgPrice()
    -10)).isLessThan(0.5);
    assertThat(chartPatternSignal.exitStopLimitOrder()).isNotNull();
    System.out.println(String.format("Placed stop limit order: %s.", chartPatternSignal.exitStopLimitOrder()));

    // Exit the trade now.
    double currPrice = numberFormat.parse(binanceApiRestClient.getPrice(chartPatternSignal.coinPair()).getPrice()).doubleValue();
    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.REMOVED_FROM_ALTFINS);
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignal.isSignalOn()).isFalse();
    assertThat(chartPatternSignal.isPositionExited()).isTrue();
    assertThat(chartPatternSignal.exitStopLimitOrder().status()).isEqualTo(OrderStatus.CANCELED);
    assertThat(chartPatternSignal.exitMarketOrder()).isNotNull();
    assertThat(chartPatternSignal.exitMarketOrder().status()).isEqualTo(OrderStatus.FILLED);
    assertThat(chartPatternSignal.exitMarketOrder().executedQty()).isEqualTo(chartPatternSignal.exitMarketOrder().executedQty());
    assertThat(Math.abs(chartPatternSignal.exitMarketOrder().avgPrice() - currPrice)).isLessThan(5.0);
    double realizedPercent = (chartPatternSignal.exitMarketOrder().avgPrice() - chartPatternSignal.entryOrder().avgPrice())/chartPatternSignal.entryOrder().avgPrice();
    double realized = chartPatternSignal.entryOrder().executedQty() * realizedPercent / 100;
    assertThat(isCloseEnough(chartPatternSignal.realized(), realized)).isTrue();
    assertThat(isCloseEnough(chartPatternSignal.realizedPercent(), realizedPercent)).isTrue();
    assertThat(chartPatternSignal.unRealized()).isZero();
    assertThat(chartPatternSignal.unRealizedPercent()).isZero();
  }

  private boolean isCloseEnough(double val1, double val2) {
    return Math.abs(val1 - val2) < 0.1;
  }
  private ChartPatternSignal getChartPatternSignal() {
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.BUY)
        .setPriceAtTimeOfSignal(4000)
        .setTimeOfSignal(new Date(timeOfSignal))
        .setTimeOfInsertion(new Date(timeOfSignal))
        .setIsInsertedLate(false)
        .setPriceTarget(6000)
        .setPriceTargetTime(new Date(timeOfSignal + 200 * 60000))
        .setProfitPotentialPercent(2.3)
        .setIsSignalOn(true)
        .build();
  }
}
