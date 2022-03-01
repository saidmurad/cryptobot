package com.binance.bot.database;

import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.util.SetupDatasource;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class OutstandingTradesTest {

  private static final String CREATE_TABLE_STMT = "create table NumOutstandingTrades(\n" +
      "    TimeFrame TEXT NOT NULL,\n" +
      "    NumOutstandingTrades INTEGER,\n" +
      "    Constraint PK Primary Key(TimeFrame)\n" +
      ");";
  private final OutstandingTrades outstandingTrades = new OutstandingTrades();

  @Before
  public void setUp() {
    DataSource dataSource = SetupDatasource.getDataSource();
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    outstandingTrades.setJdbcTemplate(jdbcTemplate);
    jdbcTemplate.execute(CREATE_TABLE_STMT);
    jdbcTemplate.update("insert into NumOutstandingTrades values('FIFTEEN_MINUTES', 0)");
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