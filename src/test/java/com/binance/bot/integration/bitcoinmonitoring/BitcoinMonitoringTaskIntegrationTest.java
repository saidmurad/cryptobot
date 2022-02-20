package com.binance.bot.integration.bitcoinmonitoring;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.bot.bitcoinmonitoring.BitcoinMonitoringTask;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.database.ChartPatternSignalDaoImplTest;
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

  @Before
  public void setUp() throws SQLException {
    assertThat(apiKey).startsWith("31");
    Statement stmt = jdbcTemplate.getDataSource().getConnection().createStatement();
    stmt.execute(ChartPatternSignalDaoImplTest.CREATE_TABLE_STMT_BITCOIN_MONITORING);
  }

  @Test
  public void testFifteenMinuteTimeFrame() throws ParseException {
    bitcoinMonitoringTask.performFifteenMinuteTimeFrame();
  }
}