package com.binance.bot.database;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ChartPatternSignalDao {
  @Autowired
  private JdbcTemplate jdbcTemplate;

}
