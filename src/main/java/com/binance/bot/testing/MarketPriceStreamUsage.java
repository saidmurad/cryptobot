package com.binance.bot.testing;

import com.binance.bot.signalsuccessfailure.MarketPriceStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class MarketPriceStreamUsage {
  @Autowired
  private MarketPriceStream marketPriceStream;
  private int counter = 0;

  public void perform() throws IOException {
    System.out.println("MarketPriceStreamUsage perform called for counter " + counter);
    switch (counter++ % 2) {
      case 0:
        marketPriceStream.removeSymbol("ETHUSDT");
        marketPriceStream.addSymbol("BTCUSDT");
        break;
      case 1:
        marketPriceStream.removeSymbol("BTCUSDT");
        marketPriceStream.addSymbol("ETHUSDT");
    }
  }
}
