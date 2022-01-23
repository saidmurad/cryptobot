package com.binance.bot.database;

import com.binance.bot.tradesignals.ChartPatternSignal;
import com.binance.bot.tradesignals.ReasonForSignalInvalidation;
import com.binance.bot.tradesignals.TimeFrame;
import com.binance.bot.trading.VolumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  private final Logger logger = LoggerFactory.getLogger(getClass());
  ChartPatternSignalDaoImpl() {
    df.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  void setDataSource(DataSource dataSource) {
    jdbcTemplate = new JdbcTemplate(dataSource);
  }

  public boolean insertChartPatternSignal(ChartPatternSignal chartPatternSignal, VolumeProfile volProfile) {
    String sql = "insert into ChartPatternSignal(CoinPair, TimeFrame, TradeType, Pattern, PriceAtTimeOfSignal, " +
        "PriceRelatedToPattern, TimeOfSignal, TimeOfInsertion, IsInsertedLate, NumTimesMissingInInput, VolumeAtSignalCandlestick, VolumeAverage, IsVolumeSurge, PriceTarget, PriceTargetTime, ProfitPotentialPercent, IsSignalOn)" +
        "values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    Object params[] = new Object[]{chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(),
        chartPatternSignal.tradeType().name(),
        chartPatternSignal.pattern(),
        chartPatternSignal.priceAtTimeOfSignal(),
        chartPatternSignal.priceRelatedToPattern(),
        df.format(chartPatternSignal.timeOfSignal()),
        df.format(chartPatternSignal.timeOfInsertion()),
        chartPatternSignal.isInsertedLate(),
        chartPatternSignal.numTimesMissingInInput(),
        (long) Double.parseDouble(volProfile.currentCandlestick().getVolume()),
        volProfile.avgVol(),
        volProfile.isVolSurged()? 1:0,
        chartPatternSignal.priceTarget(),
        df.format(chartPatternSignal.priceTargetTime()),
        chartPatternSignal.profitPotentialPercent(),
        chartPatternSignal.isSignalOn()
    };

    return jdbcTemplate.update(sql, params) > 0;
  }

  public boolean invalidateChartPatternSignal(ChartPatternSignal chartPatternSignal, double priceAtTimeOfInvalidation, ReasonForSignalInvalidation reasonForSignalInvalidation) {
    String sql = "update ChartPatternSignal set IsSignalOn=0, TimeOfSignalInvalidation=?, " +
        "PriceAtTimeOfSignalInvalidation=?, ReasonForSignalInvalidation=? where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    return jdbcTemplate.update(sql, df.format(new Date()), Double.toString(priceAtTimeOfInvalidation), reasonForSignalInvalidation.name(), chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())) == 1;
  }

  // TODO: Think of a way how we can trim the data considered.
  public List<ChartPatternSignal> getAllChartPatterns(TimeFrame timeFrame) {
    return jdbcTemplate.query("select * from ChartPatternSignal where TimeFrame='" + timeFrame.name() + "'", new ChartPatternSignalMapper());
  }

  // intented for test use.
  public ChartPatternSignal getChartPattern(ChartPatternSignal chartPatternSignal) {
    String sql = "select * from ChartPatternSignal where CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
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
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?) and IsSignalOn=1";
    return jdbcTemplate.update(sql, tenCandleStickTimePrice, tenCandleStickTimeProfitPercent, chartPatternSignal.coinPair(),
        chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
        df.format(chartPatternSignal.timeOfSignal())) == 1;
  }

  public void incrementNumTimesMissingInInput(List<ChartPatternSignal> chartPatternsMissingInInput) {
    String sql = "update ChartPatternSignal set NumTimesMissingInInput = NumTimesMissingInInput + 1 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    for (ChartPatternSignal chartPatternSignal: chartPatternsMissingInInput) {
      int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()));
      if (ret == 1) {
        logger.info("Updated chart pattern signal missing count to " + (chartPatternSignal.numTimesMissingInInput() + 1) + " for chart pattern signal: " + chartPatternSignal.toString());
      } else {
        logger.error("Failed to increment numTimesMissingInInput for chart pattern signal: " + chartPatternSignal.toString());
      }
    }
  }

  public void resetNumTimesMissingInInput(List<ChartPatternSignal> chartPatternSignalsReappearedInTime) {
    String sql = "update ChartPatternSignal set NumTimesMissingInInput = 0 where " +
        "CoinPair=? and TimeFrame=? and TradeType=? and Pattern=? and DATETIME(TimeOfSignal)=DATETIME(?)";
    for (ChartPatternSignal chartPatternSignal: chartPatternSignalsReappearedInTime) {
      int ret = jdbcTemplate.update(sql, chartPatternSignal.coinPair(),
          chartPatternSignal.timeFrame().name(), chartPatternSignal.tradeType().name(), chartPatternSignal.pattern(),
          df.format(chartPatternSignal.timeOfSignal()));
      if (ret == 1) {
        logger.info("Updated chart pattern signal missing count to 0 for chart pattern signal: " + chartPatternSignal.toString());
      } else {
        logger.error("Failed to make numTimesMissingInInput 0 for chart pattern signal: " + chartPatternSignal.toString());
      }
    }
  }

  public List<ChartPatternSignal> getChartPatternSignalsToInvalidate() {
    String sql = "select * from ChartPatternSignal where IsSignalOn=1 and NumTimesMissingInInput >= 5";
    return jdbcTemplate.query(sql, new ChartPatternSignalMapper());
  }
}
