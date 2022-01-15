package com.binance.bot.database;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Repository
public class ChartPatternSignalDaoImpl {
  @Autowired
  private JdbcTemplate jdbcTemplate;
  final SimpleDateFormat df = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");

  void setDataSource(DataSource dataSource) {
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  public boolean insertChartPatternSignal(ChartPatternSignal chartPatternSignal) {
    String sql = "insert into ChartPatternSignal(CoinPair, TimeFrame, TradeType, Pattern, PriceAtTimeOfSignal, " +
        "PriceRelatedToPattern, TimeOfSignal, PriceTarget, PriceTargetTime, ProfitPotentialPercent, IsSignalOn)" +
        "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    Object params[] = new Object[]{chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(),
        chartPatternSignal.tradeType().name(),
        chartPatternSignal.pattern(),
        chartPatternSignal.priceAtTimeOfSignal(),
        chartPatternSignal.priceRelatedToPattern(),
        df.format(chartPatternSignal.timeOfSignal()),
        chartPatternSignal.priceTarget(),
        df.format(chartPatternSignal.priceTargetTime()),
        chartPatternSignal.profitPotentialPercent(),
        chartPatternSignal.isSignalOn()
    };

    return jdbcTemplate.update(sql, params) > 0;
  }

  public boolean invalidateChartPatternSignal(ChartPatternSignal chartPatternSignal, ReasonForSignalInvalidation reasonForSignalInvalidation) {
    String sql = "update ChartPatternSignal set IsSignalOn=0, TimeOfSignalInvalidation=?, ReasonForSignalInvalidation=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and TimeOfSignal=?";
    return jdbcTemplate.update(sql, df.format(new Date()), reasonForSignalInvalidation.name(), chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())) == 1;
  }

  public List<ChartPatternSignal> getActiveChartPatterns(TimeFrame timeFrame) {
    return jdbcTemplate.query("select * from ChartPatternSignal where IsSignalOn=1 and TimeFrame='" + timeFrame.name() + "'", new ChartPatternSignalMapper());
  }

  // intented for test use.
  public ChartPatternSignal getChartPattern(ChartPatternSignal chartPatternSignal) {
    String sql = "select * from ChartPatternSignal where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and TimeOfSignal=?";
    return jdbcTemplate.queryForObject(sql, new Object[]{chartPatternSignal.coinPair(), chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())}, new ChartPatternSignalMapper());
  }
}
