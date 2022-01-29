package com.binance.bot.signalsuccessfailure.onetimetasks;

import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SetTenCandlestickTime {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private ChartPatternSignalDaoImpl dao;

  public void perform() {
    List<ChartPatternSignal> allChartPatterns = dao.getAllChartPatterns();
    for (ChartPatternSignal pattern: allChartPatterns) {
      if (!dao.setTenCandleSticktime(pattern)) {
        logger.error("Failed updating for chart pattern " + pattern.toString());
      }
    }
  }
}
