package com.binance.bot.signalsuccessfailure;

import com.binance.bot.database.ChartPatternSignalDaoImpl;
import com.binance.bot.tradesignals.ChartPatternSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

public class MaxLossCalculatorTask {
  @Autowired
  private ChartPatternSignalDaoImpl dao;

  @Scheduled(fixedDelay = 60000)
  public void perform() {
    List<ChartPatternSignal> chartPatternSignals = dao.getAllChartPatterns();

  }
}
