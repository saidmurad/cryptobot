package com.binance.bot;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RecordChecker {

  //Executes each 500 ms
  @Scheduled(fixedRate = 1000)
  public void checkRecords() {
    System.out.println("Yes sir.");
  }
}
