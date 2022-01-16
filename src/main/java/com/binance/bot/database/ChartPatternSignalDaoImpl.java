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
import java.util.TimeZone;

@Repository
public class ChartPatternSignalDaoImpl {
  @Autowired
  private JdbcTemplate jdbcTemplate;
  final SimpleDateFormat df = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");

  ChartPatternSignalDaoImpl() {
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

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

  public List<ChartPatternSignal> getChatPatternSignalsThatReachedTenCandleStickTime() {
    String sql = "select * from ChartPatternSignal where IsSignalOn = 1\n" +
        "    and PriceAtTenCandlestickTime is null\n" +
        "    and ((TimeFrame = 'FIFTEEN_MINUTES' and DATETIME(TimeOfSignal, '+150 minute') <= DATETIME('now'))\n" +
        "    or (TimeFrame = 'HOUR' and DATETIME(TimeOfSignal, '+10 hour') <= DATETIME('now'))\n" +
        "    or (TimeFrame = 'FOUR_HOURS' and DATETIME(TimeOfSignal, '+40 hour') <= DATETIME('now'))\n" +
        "    or (TimeFrame = 'DAY' and DATETIME(TimeOfSignal, '+10 day') <= DATETIME('now')))";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }

  public boolean setTenCandleStickTimePrice(ChartPatternSignal chartPatternSignal,
                                            double tenCandleStickTimePrice,
                                            double tenCandleStickTimeProfitPercent) {
    String sql = "update ChartPatternSignal set PriceAtTenCandlestickTime=?, ProfitPercentAtTenCandlestickTime=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and TimeOfSignal=? and IsSignalOn=1";
    return jdbcTemplate.update(sql, tenCandleStickTimePrice, tenCandleStickTimeProfitPercent, chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())) == 1;
  }
}
