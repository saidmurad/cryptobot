package com.binance.bot.database;

import com.binance.bot.tradesignals.ChartPatternSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.text.SimpleDateFormat;
import java.util.List;

@Repository
public class ChartPatternSignalDaoImpl {
  @Autowired
  private JdbcTemplate jdbcTemplate;
  private final SimpleDateFormat df = new SimpleDateFormat("YYYY-mm-dd HH:MM:SS");

  void setDataSource(DataSource dataSource) {
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  public void insertChartPatternSignal(ChartPatternSignal chartPatternSignal) {
    String sql = "insert into ChartPatternSignal(CoinPair, TimeFrame, TradeType, Pattern, PriceAtTimeOfSignal, " +
        "PriceRelatedToPattern, TimeOfSignal, PriceTarget, PriceTargetTime, ProfitPotentialPercent, IsSignalOn";
    Object params[] = new Object[]{chartPatternSignal.coinPair(), chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(), chartPatternSignal.priceAtSignalTargetTime(), chartPatternSignal.priceRelatedToPattern(), df.format(chartPatternSignal.timeOfSignal()),
        chartPatternSignal.priceTarget(), chartPatternSignal.};

    jdbcTemplate.update(sql, params);
  }
}
