package com.binance.bot.signalsuccessfailure.onetimetasks;

import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.bot.trading.SupportedSymbolsInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CheckSymbolInfoCorrectness {
  @Autowired
  private SupportedSymbolsInfo supportedSymbolsInfo;
  String[] symbolsToCheck = {"KAVAUSDT", "OGUSDT", "YFIUSDT", "ARDRUSDT", "IOSTUSDT", "ONGUSDT", "MDXUSDT"};
  public void checkSymbols() {
    for (int i = 0; i < symbolsToCheck.length; i++) {
      if (supportedSymbolsInfo.getSupportedSymbols().containsKey(symbolsToCheck[i])) {
        System.out.println("Symbol " + symbolsToCheck[i] + " is supported.");
      } else {
        System.out.println("Symbol " + symbolsToCheck[i] + " is not supported.");
      }
    }
  }
}
