package com.binance.bot.integration.trading;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.util.CreateCryptobotDB;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@EnableConfigurationProperties
@TestPropertySource(locations = {
    "classpath:application.properties",
    "classpath:test-application.properties"},
    properties = "app.scheduling.enable=false")
public class BinanceTradingBotIntegrationTest {
  @Autowired
  ChartPatternSignalDaoImpl dao;
  @Autowired
  private JdbcTemplate jdbcTemplate;
  @Autowired
  private BinanceApiClientFactory binanceApiClientFactory;
  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  @Before
  public void setUp() throws SQLException {
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    CreateCryptobotDB.createCryptobotDB(jdbcTemplate.getDataSource());
  }

  @Test
  public void placeBuyTrade() {

  }
}