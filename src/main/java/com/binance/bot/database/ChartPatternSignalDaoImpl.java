package com.binance.bot.database;

import com.binance.bot.tradesignals.ChartPatternSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

@Repository
public class ChartPatternSignalDaoImpl {
  @Autowired
  private JdbcTemplate jdbcTemplate;

  void setDataSource(DataSource dataSource) {
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  public void insertChartPatternSignal(ChartPatternSignal chartPatternSignal) {
    String sql = "insert into ChartPatternSignal(CoinPair, TimeFrame, TradeType, Pattern, PriceAtTimeOfSignal, " +
        "PriceRelatedToPattern, TimeOfSignal, PriceTarget, PriceTargetTime, ProfitPotentialPercent, IsSignalOn";
    Object params[] = new Object[]{};

    jdbcTemplate.update(sql, params);
  }
}
