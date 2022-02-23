package com.binance.bot.integration.bitcoinmonitoring;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import com.binance.bot.bitcoinmonitoring.BitcoinMonitoringTask;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.ChartPatternSignalDaoImplTest;
import com.binance.bot.tradesignals.TradeType;
import com.binance.bot.trading.BinanceTradingBot;
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

import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import static com.binance.bot.database.ChartPatternSignalDaoImplTest.createTableStmt;
import static com.google.common.truth.Truth.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@EnableConfigurationProperties
@TestPropertySource(locations = {
    "classpath:application.properties",
    "classpath:test-application.properties" },
    properties = "app.scheduling.enable=false")
public class BitcoinMonitoringTaskIntegrationTest {
  @Autowired
  private BitcoinMonitoringTask bitcoinMonitoringTask;
  @Autowired
  ChartPatternSignalDaoImpl dao;
  @Autowired
  private JdbcTemplate jdbcTemplate;
  @Value("${api_key}")
  String apiKey;
  private long timeOfSignal = System.currentTimeMillis();
  private BinanceApiRestClient binanceApiRestClient;
  @Autowired
  private BinanceApiClientFactory binanceApiClientFactory;
  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  @Before
  public void setUp() throws SQLException {
    assertThat(apiKey).startsWith("31");
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    binanceApiRestClient = binanceApiClientFactory.newRestClient();
    Statement stmt = jdbcTemplate.getDataSource().getConnection().createStatement();
    try {
      stmt.execute("drop table BitcoinPriceMonitoring");
    } catch (SQLiteException ignore) {}
    stmt.execute(ChartPatternSignalDaoImplTest.CREATE_TABLE_STMT_BITCOIN_MONITORING);
  }

  @Test
  public void testFourHourTimeFrame() throws ParseException, BinanceApiException {
    // setting status for 18-02-2022 midnight.
    List<Candlestick> candlesticks = binanceApiRestClient.getCandlestickBars("BTCUSDT", CandlestickInterval.FOUR_HOURLY, 10,
        dateFormat.parse("2022-02-16 08:00").getTime(), null);
    TradeType tradeTypeOverdone = bitcoinMonitoringTask.getOverdoneTradeType(candlesticks, 5.5);
    assertThat(tradeTypeOverdone).isEqualTo(TradeType.SELL);
  }
}