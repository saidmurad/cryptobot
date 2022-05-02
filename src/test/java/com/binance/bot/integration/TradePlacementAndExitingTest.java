package com.binance.bot.integration;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiMarginRestClient;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.common.Mailer;
import com.binance.bot.common.Util;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.signalsuccessfailure.StopLimitOrderStatusChecker;
import com.binance.bot.trading.ExitPositionAtMarketPrice;
import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.tradesignals.TradeExitType;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.BinanceTradingBot;
import com.binance.bot.trading.VolumeProfile;
import com.binance.bot.util.CreateCryptobotDB;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.mail.MessagingException;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

import static com.google.common.truth.Truth.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@EnableConfigurationProperties
@TestPropertySource(locations = {
    "classpath:application.properties",
    "classpath:test-application.properties"},
    properties = "app.scheduling.enable=false")
/**
 * Disabled for frequent running becaause cross margin test doesn't have a testnet to use.
 */
public class TradePlacementAndExitingTest {
  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();
  @Mock private Mailer mockMailer;
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
  @Autowired
  StopLimitOrderStatusChecker stopLimitOrderStatusChecker;
  BinanceApiRestClient binanceApiRestClient;
  @Value("${run_integ_tests_with_real_trades}")
  boolean runIntegTestsWithRealTrades;
  private long timeOfSignal = System.currentTimeMillis();
  private NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
  private VolumeProfile volProfile;
  private double currPrice;
  private BinanceApiMarginRestClient binanceApiMarginRestClient;

  @Before
  public void setUp() throws SQLException, BinanceApiException, ParseException {
    CreateCryptobotDB.createCryptobotDB(jdbcTemplate.getDataSource());
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
    binanceApiMarginRestClient = binanceApiClientFactory.newMarginRestClient();
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
    currPrice = numberFormat.parse(binanceApiRestClient.getPrice("ETHUSDT").getPrice()).doubleValue();
    binanceTradingBot.mailer = mockMailer;
  }

  @Test
  public void buyAtMarket_and_exitAtMarket() throws ParseException, MessagingException, BinanceApiException, InterruptedException {
    if (!runIntegTestsWithRealTrades) {
      return;
    }
    binanceTradingBot.stopLossPercent = 5.0;
    binanceTradingBot.stopLimitPercent = 5.5;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    binanceTradingBot.perTradeAmountConfigured = 10;
    binanceTradingBot.placeTrade(chartPatternSignal, 0);
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);

    assertThat(chartPatternSignal.isSignalOn()).isTrue();
    assertThat(chartPatternSignal.entryOrder()).isNotNull();
    assertThat(chartPatternSignal.entryOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.FILLED);
    assertThat(Math.abs(chartPatternSignal.entryOrder().executedQty() * chartPatternSignal.entryOrder().avgPrice()
    -10)).isLessThan(2.0); // Min notional calculation factorsin.
    assertThat(chartPatternSignal.exitStopLimitOrder()).isNotNull();
    OrderStatusRequest stopLossOrderStatusReq =
        new OrderStatusRequest("ETHUSDT", chartPatternSignal.exitStopLimitOrder().orderId());
    Order stopLossOrderStatusResp = binanceApiMarginRestClient.getOrderStatus(stopLossOrderStatusReq);
    assertThat(stopLossOrderStatusResp.getOrigQty()).isEqualTo(
        Util.getTruncatedQuantity(chartPatternSignal.entryOrder().executedQty(), 4));

    // Exit the trade now.
    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.REMOVED_FROM_SOURCESIGNALS);
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignal.isSignalOn()).isFalse();
    assertThat(chartPatternSignal.isPositionExited()).isTrue();
    assertThat(chartPatternSignal.exitStopLimitOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.CANCELED);
    assertThat(chartPatternSignal.exitOrder()).isNotNull();
    assertThat(chartPatternSignal.exitOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.FILLED);
    // Truncate the position rather than enter short for the small leftover.
    assertThat(Double.toString(chartPatternSignal.exitOrder().executedQty())).isEqualTo(
        Util.getTruncatedQuantity(chartPatternSignal.entryOrder().executedQty(), 4));
    assertThat(Math.abs(chartPatternSignal.exitOrder().avgPrice() - currPrice)).isLessThan(5.0);
    double realizedPercent = (chartPatternSignal.exitOrder().avgPrice() - chartPatternSignal.entryOrder().avgPrice())/chartPatternSignal.entryOrder().avgPrice();
    double realized = chartPatternSignal.entryOrder().executedQty() * realizedPercent / 100;
    assertThat(isCloseEnough(chartPatternSignal.realized(), realized)).isTrue();
    assertThat(isCloseEnough(chartPatternSignal.realizedPercent(), realizedPercent)).isTrue();
    assertThat(chartPatternSignal.unRealized()).isZero();
    assertThat(chartPatternSignal.unRealizedPercent()).isZero();
  }

  @Test
  public void buyTrade_stopLoss() throws ParseException, MessagingException, BinanceApiException, InterruptedException {
    if (!runIntegTestsWithRealTrades) {
      return;
    }
    binanceTradingBot.stopLossPercent = 0;
    binanceTradingBot.stopLimitPercent = 0.5;
    ChartPatternSignal chartPatternSignal = getChartPatternSignal();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    binanceTradingBot.perTradeAmountConfigured = 10;
    binanceTradingBot.placeTrade(chartPatternSignal, 0);
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);

    assertThat(chartPatternSignal.isSignalOn()).isTrue();
    assertThat(chartPatternSignal.entryOrder()).isNotNull();
    assertThat(chartPatternSignal.entryOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.FILLED);
    System.out.println(String.format("Executed entry order: %s.", chartPatternSignal.entryOrder()));
    assertThat(Math.abs(chartPatternSignal.entryOrder().executedQty() * chartPatternSignal.entryOrder().avgPrice()
        -10)).isLessThan(2.0); // Min notional calculation factors in.
    assertThat(chartPatternSignal.exitStopLimitOrder()).isNotNull();
    System.out.println(String.format("Placed stop limit order: %s.", chartPatternSignal.exitStopLimitOrder()));
    Order stopLossOrderStatusResp;
    do {
      OrderStatusRequest stopLossOrderStatusReq =
          new OrderStatusRequest("ETHUSDT", chartPatternSignal.exitStopLimitOrder().orderId());
      stopLossOrderStatusResp = binanceApiMarginRestClient.getOrderStatus(stopLossOrderStatusReq);
      Thread.sleep(1000);
    } while (stopLossOrderStatusResp.getStatus() != OrderStatus.FILLED);
    assertThat(stopLossOrderStatusResp.getOrigQty()).isEqualTo(
        Util.getTruncatedQuantity(chartPatternSignal.entryOrder().executedQty(), 4));
    assertThat(stopLossOrderStatusResp.getExecutedQty()).isEqualTo(
        Util.getTruncatedQuantity(chartPatternSignal.entryOrder().executedQty(), 4));

    // Exit the trade now.
    stopLimitOrderStatusChecker.perform();
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignal.isSignalOn()).isFalse();
    assertThat(chartPatternSignal.isPositionExited()).isTrue();
    assertThat(chartPatternSignal.exitStopLimitOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.FILLED);
    // This is because commissions on the exit order is not on the base asset but on USDT.
    assertThat(Double.toString(chartPatternSignal.exitStopLimitOrder().executedQty()))
        .isEqualTo(Util.getTruncatedQuantity(chartPatternSignal.entryOrder().executedQty(), 4));
    assertThat(Math.abs(chartPatternSignal.exitStopLimitOrder().avgPrice() - currPrice)).isLessThan(5.0);
    double realizedPercent = (chartPatternSignal.exitStopLimitOrder().avgPrice() - chartPatternSignal.entryOrder().avgPrice())/chartPatternSignal.entryOrder().avgPrice();
    double realized = chartPatternSignal.entryOrder().executedQty() * realizedPercent / 100;
    assertThat(isCloseEnough(chartPatternSignal.realized(), realized)).isTrue();
    assertThat(isCloseEnough(chartPatternSignal.realizedPercent(), realizedPercent)).isTrue();
    assertThat(chartPatternSignal.unRealized()).isZero();
    assertThat(chartPatternSignal.unRealizedPercent()).isZero();
  }

  @Test
  public void sellAtMarket_and_exitAtMarket() throws ParseException, MessagingException, BinanceApiException, InterruptedException {
    if (!runIntegTestsWithRealTrades) {
      return;
    }
    binanceTradingBot.stopLossPercent = 5;
    binanceTradingBot.stopLimitPercent = 5.5;
    ChartPatternSignal chartPatternSignal = getSellChartPatternSignal();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    binanceTradingBot.perTradeAmountConfigured = 10;
    binanceTradingBot.placeTrade(chartPatternSignal, 0);
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);

    assertThat(chartPatternSignal.isSignalOn()).isTrue();
    assertThat(chartPatternSignal.entryOrder()).isNotNull();
    assertThat(chartPatternSignal.entryOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.FILLED);
    System.out.println(String.format("Executed entry order: %s.", chartPatternSignal.entryOrder()));
    assertThat(Math.abs(chartPatternSignal.entryOrder().executedQty() * chartPatternSignal.entryOrder().avgPrice()
        -10)).isLessThan(2.0); // Min notional calculation factorsin.
    assertThat(chartPatternSignal.exitStopLimitOrder()).isNotNull();
    System.out.println(String.format("Placed stop limit order: %s.", chartPatternSignal.exitStopLimitOrder()));
    OrderStatusRequest stopLossOrderStatusReq =
        new OrderStatusRequest("ETHUSDT", chartPatternSignal.exitStopLimitOrder().orderId());
    Order stopLossOrderStatusResp = binanceApiMarginRestClient.getOrderStatus(stopLossOrderStatusReq);
    assertThat(stopLossOrderStatusResp.getOrigQty()).isEqualTo(
        Util.getRoundedUpQuantity(chartPatternSignal.entryOrder().executedQty() / 0.999, 4));

    // Exit the trade now.
    exitPositionAtMarketPrice.exitPositionIfStillHeld(chartPatternSignal, TradeExitType.REMOVED_FROM_SOURCESIGNALS);
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignal.isSignalOn()).isFalse();
    assertThat(chartPatternSignal.isPositionExited()).isTrue();
    assertThat(chartPatternSignal.exitStopLimitOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.CANCELED);
    assertThat(chartPatternSignal.exitOrder()).isNotNull();
    assertThat(chartPatternSignal.exitOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.FILLED);
    // The former value below will be in high precision after deducting the ETH commission., hence the rounding for it as well.
    assertThat(Util.getRoundedUpQuantity(chartPatternSignal.exitOrder().executedQty(), 4)).isEqualTo(
        Util.getRoundedUpQuantity(chartPatternSignal.entryOrder().executedQty() / 0.999, 4));
    assertThat(Math.abs(chartPatternSignal.exitOrder().avgPrice() - currPrice)).isLessThan(5.0);
    double realizedPercent = (chartPatternSignal.exitOrder().avgPrice() - chartPatternSignal.entryOrder().avgPrice())/chartPatternSignal.entryOrder().avgPrice();
    double realized = chartPatternSignal.entryOrder().executedQty() * realizedPercent / 100;
    assertThat(isCloseEnough(chartPatternSignal.realized(), realized)).isTrue();
    assertThat(isCloseEnough(chartPatternSignal.realizedPercent(), realizedPercent)).isTrue();
    assertThat(chartPatternSignal.unRealized()).isZero();
    assertThat(chartPatternSignal.unRealizedPercent()).isZero();
  }

  @Test
  public void sellTrade_stopLoss() throws ParseException, MessagingException, BinanceApiException, InterruptedException {
    if (!runIntegTestsWithRealTrades) {
      return;
    }
    binanceTradingBot.stopLossPercent = 0;
    binanceTradingBot.stopLimitPercent = 0.5;
    ChartPatternSignal chartPatternSignal = getSellChartPatternSignal();
    dao.insertChartPatternSignal(chartPatternSignal, volProfile);
    binanceTradingBot.perTradeAmountConfigured = 10;
    binanceTradingBot.placeTrade(chartPatternSignal, 0);
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);

    assertThat(chartPatternSignal.isSignalOn()).isTrue();
    assertThat(chartPatternSignal.entryOrder()).isNotNull();
    assertThat(chartPatternSignal.entryOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.FILLED);
    System.out.println(String.format("Executed entry order: %s.", chartPatternSignal.entryOrder()));
    assertThat(Math.abs(chartPatternSignal.entryOrder().executedQty() * chartPatternSignal.entryOrder().avgPrice()
        -10)).isLessThan(2.0); // Min notional calculation factorsin.
    assertThat(chartPatternSignal.exitStopLimitOrder()).isNotNull();
    Order stopLossOrderStatusResp;
    do {
      OrderStatusRequest stopLossOrderStatusReq =
          new OrderStatusRequest("ETHUSDT", chartPatternSignal.exitStopLimitOrder().orderId());
      stopLossOrderStatusResp = binanceApiMarginRestClient.getOrderStatus(stopLossOrderStatusReq);
      Thread.sleep(1000);
    } while (stopLossOrderStatusResp.getStatus() != OrderStatus.FILLED);
    // OrigQty (= executedQty) below is before deducting commissions
    assertThat(stopLossOrderStatusResp.getOrigQty()).isEqualTo(
        Util.getRoundedUpQuantity(chartPatternSignal.entryOrder().executedQty() / 0.999, 4));
    assertThat(stopLossOrderStatusResp.getExecutedQty()).isEqualTo(
        Util.getRoundedUpQuantity(chartPatternSignal.entryOrder().executedQty() / 0.999, 4));

    // Exit the trade now.
    stopLimitOrderStatusChecker.perform();
    chartPatternSignal = dao.getChartPattern(chartPatternSignal);
    assertThat(chartPatternSignal.isSignalOn()).isFalse();
    assertThat(chartPatternSignal.isPositionExited()).isTrue();
    assertThat(chartPatternSignal.exitStopLimitOrder().status()).isEqualTo(ChartPatternSignal.Order.OrderStatusInt.FILLED);
    // Below doesn't include commission, (trade fill is unavailable anyway for stop loss orders).
    assertThat(chartPatternSignal.exitStopLimitOrder().executedQty() / 0.999)
        .isEqualTo(Double.parseDouble(Util.getRoundedUpQuantity(chartPatternSignal.entryOrder().executedQty() / 0.999, 4)));
    assertThat(Math.abs(chartPatternSignal.exitStopLimitOrder().avgPrice() - currPrice)).isLessThan(5.0);
    double realizedPercent = (chartPatternSignal.exitStopLimitOrder().avgPrice() - chartPatternSignal.entryOrder().avgPrice())/chartPatternSignal.entryOrder().avgPrice();
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

  private ChartPatternSignal getSellChartPatternSignal() {
    return ChartPatternSignal.newBuilder()
        .setCoinPair("ETHUSDT")
        .setTimeFrame(TimeFrame.FIFTEEN_MINUTES)
        .setPattern("Resistance")
        .setTradeType(TradeType.SELL)
        .setPriceAtTimeOfSignal(3000)
        .setTimeOfSignal(new Date(timeOfSignal))
        .setTimeOfInsertion(new Date(timeOfSignal))
        .setIsInsertedLate(false)
        .setPriceTarget(2000)
        .setPriceTargetTime(new Date(timeOfSignal + 200 * 60000))
        .setProfitPotentialPercent(2.3)
        .setIsSignalOn(true)
        .build();
  }
}
