package com.binance.bot.tradesignals;

import com.binance.bot.common.MinNotionalTradeValueInUSDTCalculator;
import com.binance.bot.common.Util;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class MinNotionalTradeValueInUSDTCalculatorTest {

  private static final double MIN_NOTIONAL_USDT = 10.0;

  @Test
  // Step size of 1 in LOT_SIZE (eg ALGOUSDT) means the quantity is a round number with no decimal places.
  public void getMinNotionalTradeValueInUSDT_buyTrade_stepSizeNoDecimalPlaces() {
    doTestMinNotionalTradeValue_BuyTrade(0, 0.9754, 5);
  }

  @Test
  // Step size of 0.1 = 1 decimal place
  public void getMinNotionalTradeValueInUSDT_buyTrade_stepSizeOneDecimalPlaces() {
    doTestMinNotionalTradeValue_BuyTrade(1, 0.7306, 5);
  }

  @Test
  // Step size of 4 in LOT_SIZE means the quantity is a round number with no decimal places.
  public void getMinNotionalTradeValueInUSDT_buyTrade_stepSizeFourDecimalPlaces() {
    doTestMinNotionalTradeValue_BuyTrade(4, 2839.24, 5);
  }

  private void doTestMinNotionalTradeValue_BuyTrade(int stepSizeNumDecimalPlaces,
                                                    double entryPrice,
                                                    double stopLimitPercent) {
    double minTradeValueInUSDTNeeded = MinNotionalTradeValueInUSDTCalculator.getMinNotionalTradeValueInUSDTForBuyTrade(
        10, stepSizeNumDecimalPlaces, stopLimitPercent, entryPrice);
    assertThat(minTradeValueInUSDTNeeded >= MIN_NOTIONAL_USDT).isTrue();

    // Verify that stop loss order quantity can be placed also.
    double entryQty = Double.parseDouble(Util.getRoundedUpQuantity(minTradeValueInUSDTNeeded / entryPrice, stepSizeNumDecimalPlaces));
    double netQuantityAfterCommissions = entryQty * 0.999;
    // Quantity for stop loss when truncated for the num decimals.
    double stopLimitOrderQty = Double.parseDouble(Util.getTruncatedQuantity(netQuantityAfterCommissions, stepSizeNumDecimalPlaces));
    double stopLimitOrderPrice = entryPrice * (100 - stopLimitPercent) / 100;
    double stopLimitOrderValueInUSDt = stopLimitOrderQty * stopLimitOrderPrice;
    assertThat(stopLimitOrderValueInUSDt).isGreaterThan(MIN_NOTIONAL_USDT);
    assertThat(stopLimitOrderValueInUSDt).isLessThan(12.0);
  }

  @Test
  public void getMinNotionalTradeValueInUSDT_sellTrade_stepSizeNoDecimalPlaces() {
    doTestMinNotionalTradeValue_SellTrade(0, 0.9754, 5);
  }

  @Test
  // Step size of 0.1 = 1 decimal place
  public void getMinNotionalTradeValueInUSDT_sellTrade_stepSizeOneDecimalPlaces() {
    doTestMinNotionalTradeValue_SellTrade(1, 0.7306, 5);
  }

  private void doTestMinNotionalTradeValue_SellTrade(int stepSizeNumDecimalPlaces,
                                                    double entryPrice,
                                                    double stopLimitPercent) {
    double minTradeValueInUSDTNeeded = MinNotionalTradeValueInUSDTCalculator.getMinNotionalTradeValueInUSDTForBuyTrade(
        10, stepSizeNumDecimalPlaces, stopLimitPercent, entryPrice);
    assertThat(minTradeValueInUSDTNeeded >= MIN_NOTIONAL_USDT).isTrue();
    assertThat(minTradeValueInUSDTNeeded).isLessThan(12.0);
  }
}