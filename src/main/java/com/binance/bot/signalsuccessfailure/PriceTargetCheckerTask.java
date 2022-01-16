package com.binance.bot.signalsuccessfailure;

import org.springframework.scheduling.annotation.Scheduled;

public class PriceTargetCheckerTask {
  @Scheduled(fixedDelay = 60000)
  public void performPriceTargetChecks() {

  }
}
