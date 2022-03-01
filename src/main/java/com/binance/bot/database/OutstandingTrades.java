package com.binance.bot.database;

import com.binance.bot.tradesignals.TimeFrame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutstandingTrades {
  @Autowired
  JdbcTemplate jdbcTemplate;

  void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public synchronized void incrementNumOutstandingTrades(TimeFrame timeFrame) {
    jdbcTemplate.update(String.format("update NumOutstandingTrades set NumOutstandingTrades=NumOutstandingTrades+1 where TimeFrame='%s'",
        timeFrame.name()));
  }

  public synchronized int getNumOutstandingTrades(TimeFrame timeFrame) {
    return jdbcTemplate.queryForObject(String.format("select NumOutstandingTrades from NumOutstandingTrades" +
        " where TimeFrame='%s'", timeFrame.name()), new Object[]{}, (rs, numRows) -> rs.getInt("NumOutstandingTrades"));
  }
}
