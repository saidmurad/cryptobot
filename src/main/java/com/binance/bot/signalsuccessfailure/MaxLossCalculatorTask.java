package com.binance.bot.signalsuccessfailure;

import com.binance.bot.database.ChartPatternSignalDaoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

public class MaxLossCalculatorTask {
  @Autowired
  private ChartPatternSignalDaoImpl dao;

  @Scheduled(fixedDelay = 60000)
  public void perform() {

  }
}
