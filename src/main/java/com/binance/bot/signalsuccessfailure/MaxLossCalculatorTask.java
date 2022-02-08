package com.binance.bot.signalsuccessfailure;

import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Date;
import java.util.List;

public class MaxLossCalculatorTask {
  @Autowired
  private ChartPatternSignalDaoImpl dao;

  //@Scheduled(fixedDelay = 600000)
  public void perform() {
    List<ChartPatternSignal> chartPatternSignals = dao.getAllChartPatternsNeedingMaxLossCalculated();
    for (ChartPatternSignal chartPatternSignal: chartPatternSignals) {
      Date signalTime = chartPatternSignal.timeOfSignal();
      Date signalTargetTime = chartPatternSignal.priceTargetTime();
      Date currWindowStart = signalTime;
      while (currWindowStart.before(signalTargetTime)) {

      }
    }
  }
}
