package com.binance.bot.database;

import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.util.CreateCryptobotDB;
import com.binance.bot.util.CreateDatasource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class OutstandingTradesTest {

  private final OutstandingTrades outstandingTrades = new OutstandingTrades();

  @Before
  public void setUp() {
    DataSource dataSource = CreateDatasource.createDataSource();
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    outstandingTrades.setJdbcTemplate(jdbcTemplate);
    CreateCryptobotDB.createCryptobotDB(dataSource);
  }

  @Test
  public void testincrementOutstandingTrades() {
    outstandingTrades.incrementNumOutstandingTrades(TimeFrame.FIFTEEN_MINUTES);
    assertThat(outstandingTrades.getNumOutstandingTrades(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(1);
  }

  @Test
  public void testDecrementOutstandingTrades() {
    outstandingTrades.decrementNumOutstandingTrades(TimeFrame.FIFTEEN_MINUTES);
    assertThat(outstandingTrades.getNumOutstandingTrades(TimeFrame.FIFTEEN_MINUTES)).isEqualTo(-1);
  }
}